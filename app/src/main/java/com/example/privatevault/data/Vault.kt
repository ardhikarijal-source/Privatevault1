package com.example.privatevault.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import androidx.core.content.edit
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipFile
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class MediaType { PHOTO, VIDEO, COMIC }

data class VaultFile(val file: File) {
    val name: String get() = file.name
    val type: MediaType? get() = when (file.extension.lowercase()) {
        "jpg", "jpeg", "png", "webp" -> MediaType.PHOTO
        "mp4" -> MediaType.VIDEO
        "cbz" -> MediaType.COMIC
        else -> null
    }
}

/**
 * Semua file & thumbnail dienkripsi (AES-256-GCM via Jetpack Security, kunci di Android Keystore)
 * dan disimpan di filesDir/vault. Untuk dilihat, file didekripsi sementara ke cacheDir lalu dihapus.
 * Catatan: nama file (bukan isinya) tetap terbaca di dalam penyimpanan privat aplikasi.
 */
class VaultRepository(private val ctx: Context) {
    private val root = File(ctx.filesDir, "vault").apply { mkdirs(); File(this, ".nomedia").createNewFile() }
    private val cache = File(ctx.cacheDir, "view").apply { mkdirs() }
    private val prefs = ctx.getSharedPreferences("vault_meta", Context.MODE_PRIVATE)
    private val master by lazy { MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build() }
    private val thumbMem = LruCache<String, Bitmap>(80)

    private fun enc(f: File) = EncryptedFile.Builder(ctx, f, master, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
    private fun encryptFrom(plain: File, dest: File) { enc(dest).openFileOutput().use { o -> plain.inputStream().use { it.copyTo(o) } } }
    private fun decryptTo(src: File, dest: File) { enc(src).openFileInput().use { i -> dest.outputStream().use { i.copyTo(it) } } }
    private fun thumbFile(f: File) = File(File(f.parentFile, ".thumbs").apply { mkdirs() }, f.name + ".jpg")

    /* ---- folder ---- */
    fun folders(): List<File> = root.listFiles { f -> f.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()
    fun createFolder(name: String) = name.isNotBlank() && File(root, name.trim().replace(Regex("[/\\\\]"), "_")).mkdirs()
    fun renameFolder(f: File, name: String) = f.renameTo(File(root, name.trim()))
    fun deleteFolder(f: File) = f.deleteRecursively()

    /* ---- file ---- */
    fun files(folder: File): List<VaultFile> =
        folder.listFiles { f -> f.isFile }?.map(::VaultFile)?.filter { it.type != null }?.sortedByDescending { it.file.lastModified() } ?: emptyList()
    fun allFiles(): List<VaultFile> = folders().flatMap { files(it) }.sortedByDescending { it.file.lastModified() }

    /** Enkripsi file plain ke folder tujuan + buat thumbnail terenkripsi. */
    private fun store(plain: File, folder: File, name: String): File {
        val dest = unique(folder, name)
        runCatching { makeThumb(plain)?.let { bmp ->
            val t = File(cache, "t_${System.nanoTime()}.jpg")
            t.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            encryptFrom(t, thumbFile(dest)); t.delete() } }
        encryptFrom(plain, dest)
        return dest
    }

    fun import(folder: File, uris: List<Uri>): Int {
        var n = 0
        uris.forEach { uri ->
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            } ?: "file_${System.currentTimeMillis()}"
            if (VaultFile(File(name)).type == null) return@forEach
            val tmp = File(cache, "imp_${System.nanoTime()}.${name.substringAfterLast('.')}")
            ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
            store(tmp, folder, name); tmp.delete(); n++
        }
        return n
    }
    fun move(f: VaultFile, to: File) { val p = open(f); store(p, to, f.name); p.delete(); delete(f) }
    fun copy(f: VaultFile) { val p = open(f); store(p, f.file.parentFile!!, f.name); p.delete() }
    fun delete(f: VaultFile) { f.file.delete(); thumbFile(f.file).delete(); setFav(f, false); thumbMem.remove(f.file.absolutePath) }

    /** Dekripsi sementara untuk dilihat/diputar. Hapus file hasilnya setelah selesai. */
    fun open(f: VaultFile): File {
        val out = File(cache, "${System.nanoTime()}_${f.name}"); decryptTo(f.file, out); return out
    }
    fun clearCache() { cache.listFiles()?.forEach { it.delete() }; thumbMem.evictAll() }

    fun thumb(f: VaultFile): Bitmap? = thumbMem.get(f.file.absolutePath) ?: runCatching {
        val bytes = enc(thumbFile(f.file)).openFileInput().use { it.readBytes() }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { thumbMem.put(f.file.absolutePath, it) }
    }.getOrNull()

    private fun sampled(open: () -> InputStream?): Bitmap? {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, b) }
        var s = 1; while (b.outWidth / (s * 2) >= 300 && b.outHeight / (s * 2) >= 300) s *= 2
        return open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    }
    private fun makeThumb(plain: File): Bitmap? = when (VaultFile(plain).type) {
        MediaType.PHOTO -> sampled { plain.inputStream() }
        MediaType.VIDEO -> MediaMetadataRetriever().run { try { setDataSource(plain.path); getFrameAtTime(0) } finally { release() } }
        MediaType.COMIC -> ZipFile(plain).use { z ->
            val e = z.entries().asSequence().filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp") }.minByOrNull { it.name.lowercase() }
            e?.let { sampled { z.getInputStream(it) } } }
        null -> null
    }

    /* ---- favorit ---- */
    fun isFav(f: VaultFile) = prefs.getStringSet("fav", emptySet())!!.contains(f.file.absolutePath)
    fun setFav(f: VaultFile, on: Boolean) = prefs.edit {
        val s = prefs.getStringSet("fav", emptySet())!!.toMutableSet()
        if (on) s.add(f.file.absolutePath) else s.remove(f.file.absolutePath); putStringSet("fav", s)
    }
    fun favorites() = allFiles().filter(::isFav)

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name); var i = 1
        while (f.exists()) { f = File(dir, "${name.substringBeforeLast('.')}_${i++}.${name.substringAfterLast('.')}") }
        return f
    }
}

/** PIN disimpan sebagai hash PBKDF2 + salt di EncryptedSharedPreferences. */
class PinManager(ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx, "vault_secure", MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    val hasPin get() = prefs.contains("hash")
    var biometric: Boolean
        get() = prefs.getBoolean("bio", true)
        set(v) = prefs.edit { putBoolean("bio", v) }
    /** Jeda sebelum terkunci setelah aplikasi ke background (ms). 0 = langsung. */
    var autoLockMs: Long
        get() = prefs.getLong("autolock", 60_000L)
        set(v) = prefs.edit { putLong("autolock", v) }

    fun set(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit { putString("salt", Base64.encodeToString(salt, 0)); putString("hash", hash(pin, salt)) }
    }
    fun changePin(old: String, new: String): Boolean {
        if (new.length != 4 || !new.all { it.isDigit() } || !verify(old)) return false
        set(new); return true
    }
    fun verify(pin: String): Boolean {
        val salt = Base64.decode(prefs.getString("salt", "") ?: return false, 0)
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), (prefs.getString("hash", "") ?: "").toByteArray())
    }
    private fun hash(pin: String, salt: ByteArray) = Base64.encodeToString(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded, 0)
}