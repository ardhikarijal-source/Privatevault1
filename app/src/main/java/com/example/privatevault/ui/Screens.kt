package com.example.privatevault.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.privatevault.data.*
import java.io.File

/* ---------- LOCK ---------- */
@Composable
fun LockScreen(pm: PinManager, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    val setup = !pm.hasPin
    val title = when { !setup -> "Masukkan PIN"; first == null -> "Buat PIN baru (4 digit)"; else -> "Ulangi PIN" }

    fun bio() {
        val a = ctx as? FragmentActivity ?: return
        val p = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) = onUnlocked()
        })
        p.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Buka Private Vault").setNegativeButtonText("Gunakan PIN").build())
    }
    LaunchedEffect(Unit) { if (!setup && pm.biometric) bio() }

    fun press(k: String) {
        error = ""
        if (k == "⌫") pin = pin.dropLast(1) else if (pin.length < 4) pin += k
        if (pin.length == 4) {
            when {
                !setup -> if (pm.verify(pin)) onUnlocked() else { error = "PIN salah, coba lagi"; pin = "" }
                first == null -> { first = pin; pin = "" }
                first == pin -> { pm.set(pin); onUnlocked() }
                else -> { error = "PIN tidak sama"; first = null; pin = "" }
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Bg).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(Accent), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(12.dp)); Text("Private Vault", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(title, color = Muted)
        Row(Modifier.padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { Box(Modifier.size(14.dp).clip(CircleShape).border(2.dp, Accent, CircleShape).background(if (it < pin.length) Accent else androidx.compose.ui.graphics.Color.Transparent)) }
        }
        Text(error, color = androidx.compose.ui.graphics.Color(0xFFFF6B73), fontSize = 13.sp, modifier = Modifier.height(20.dp))
        listOf("123", "456", "789", " 0⌫").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                row.forEach { ch -> if (ch == ' ') Spacer(Modifier.size(64.dp)) else
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Card).clickable { press(ch.toString()) }, contentAlignment = Alignment.Center) { Text(ch.toString(), fontSize = 22.sp) } }
            }
        }
        if (!setup && pm.biometric) TextButton(onClick = ::bio) { Icon(Icons.Default.Fingerprint, null); Text(" Gunakan biometrik") }
    }
}

/* ---------- THUMBNAIL (didekripsi dari thumbnail terenkripsi) ---------- */
@Composable
fun Thumb(repo: VaultRepository, f: VaultFile, modifier: Modifier = Modifier, selected: Boolean? = null, onToggle: () -> Unit = {}, onClick: () -> Unit) {
    val bmp by produceState<android.graphics.Bitmap?>(null, f.file.absolutePath) { value = withContext(Dispatchers.IO) { repo.thumb(f) } }
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Card2).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (bmp == null && f.type == MediaType.COMIC) Icon(Icons.Default.MenuBook, null, tint = Accent, modifier = Modifier.size(36.dp))
        if (f.type == MediaType.VIDEO) Icon(Icons.Default.PlayCircle, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(34.dp))
        if (selected != null) Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape)
            .background(if (selected) Accent else androidx.compose.ui.graphics.Color(0x66000000)).border(2.dp, androidx.compose.ui.graphics.Color.White, CircleShape).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center) { if (selected) Icon(Icons.Default.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp)) }
    }
}

/* ---------- HOME ---------- */
@Composable
fun HomeScreen(repo: VaultRepository, version: Int, onFolder: (File) -> Unit, onFile: (VaultFile) -> Unit, onFav: () -> Unit, onSettings: () -> Unit, onChanged: () -> Unit) {
    val all = remember(version) { repo.allFiles() }
    var q by remember { mutableStateOf("") }
    var newDialog by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var target by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        val f = target ?: repo.folders().firstOrNull() ?: return@rememberLauncherForActivityResult
        repo.import(f, uris); onChanged()
    }
    Scaffold(containerColor = Bg,
        floatingActionButton = { FloatingActionButton(onClick = { if (repo.folders().isEmpty()) newDialog = true else picker.launch(arrayOf("image/*", "video/mp4", "application/zip", "application/vnd.comicbook+zip", "application/x-cbz", "application/octet-stream")) }, containerColor = Accent, shape = CircleShape) { Icon(Icons.Default.Add, null) } },
        bottomBar = { NavigationBar(containerColor = Color0d) {
            NavigationBarItem(true, {}, { Icon(Icons.Default.Home, null) }, label = { Text("Beranda") })
            NavigationBarItem(false, {}, { Icon(Icons.Default.Folder, null) }, label = { Text("Folder") })
            NavigationBarItem(false, onFav, { Icon(Icons.Default.FavoriteBorder, null) }, label = { Text("Favorit") })
            NavigationBarItem(false, onSettings, { Icon(Icons.Default.Settings, null) }, label = { Text("Pengaturan") })
        } }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Private Vault", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp)) }
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).padding(vertical = 14.dp)) {
                    listOf("Foto" to MediaType.PHOTO, "Video" to MediaType.VIDEO, "Komik" to MediaType.COMIC).forEach { (n, t) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("${all.count { it.type == t }}", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(n, color = Muted) }
                    }
                }
            }
            item { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Cari file…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp)) }
            item { Text("Folder Saya", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(repo.folders()) { f ->
                        Column(Modifier.width(78.dp).clip(RoundedCornerShape(14.dp)).background(Card).clickable { onFolder(f) }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Folder, null, tint = Accent, modifier = Modifier.size(34.dp)); Text(f.name, fontSize = 12.sp, maxLines = 1); Text("${repo.files(f).size} item", fontSize = 11.sp, color = Muted)
                        }
                    }
                    item { Column(Modifier.width(78.dp).clip(RoundedCornerShape(14.dp)).background(Card).clickable { newDialog = true }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Add, null, modifier = Modifier.size(34.dp)); Text("Folder Baru", fontSize = 12.sp) } }
                }
            }
            item { Text("Media Terbaru", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            items(all.filter { it.name.contains(q, true) }.take(16).chunked(4)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { Thumb(repo, it, Modifier.weight(1f)) { onFile(it) } }; repeat(4 - row.size) { Spacer(Modifier.weight(1f)) } }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (newDialog) NameDialog("Folder Baru", "") { n -> newDialog = false; if (n != null) { repo.createFolder(n); onChanged() } }
}
private val Color0d = androidx.compose.ui.graphics.Color(0xFF0D1527)

@Composable
fun NameDialog(title: String, init: String, done: (String?) -> Unit) {
    var t by remember { mutableStateOf(init) }
    AlertDialog(onDismissRequest = { done(null) }, title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = true, placeholder = { Text("Nama folder") }) },
        confirmButton = { TextButton({ done(t.ifBlank { null }) }) { Text("Simpan") } },
        dismissButton = { TextButton({ done(null) }) { Text("Batal") } })
}

/* ---------- FOLDER / FAVORIT ---------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(repo: VaultRepository, folder: File?, version: Int, onBack: () -> Unit, onFile: (VaultFile) -> Unit, onChanged: () -> Unit) {
    var tab by remember { mutableStateOf(MediaType.PHOTO) }
    val sel = remember { mutableStateListOf<VaultFile>() }
    var menu by remember { mutableStateOf(false) }; var rename by remember { mutableStateOf(false) }; var moveDlg by remember { mutableStateOf(false) }
    val items = remember(version, tab) { (if (folder == null) repo.favorites() else repo.files(folder)).filter { it.type == tab } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> folder?.let { repo.import(it, uris); onChanged() } }
    Scaffold(containerColor = Bg,
        topBar = { TopAppBar(title = { Text(folder?.name ?: "Favorit") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = { if (folder != null) Box { IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Ubah Nama") }, { menu = false; rename = true })
                    DropdownMenuItem({ Text("Hapus Folder") }, { menu = false; repo.deleteFolder(folder); onChanged(); onBack() })
                } } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)) },
        floatingActionButton = { if (folder != null) FloatingActionButton({ picker.launch(arrayOf("image/*", "video/mp4", "application/zip", "application/vnd.comicbook+zip", "application/octet-stream")) }, containerColor = Accent) { Icon(Icons.Default.Add, null) } },
        bottomBar = { if (folder != null) BottomAppBar(containerColor = Color0d) {
            listOf(Triple("Pindahkan", Icons.Default.DriveFileMove) { if (sel.isNotEmpty()) moveDlg = true },
                Triple("Salin", Icons.Default.ContentCopy) { sel.forEach(repo::copy); sel.clear(); onChanged() },
                Triple("Hapus", Icons.Default.Delete) { sel.forEach(repo::delete); sel.clear(); onChanged() }).forEach { (l, ic, act) ->
                TextButton(act, Modifier.weight(1f)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(ic, null); Text(l, fontSize = 11.sp) } } } } }) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(tab.ordinal, containerColor = Bg) { listOf("Foto", "Video", "Komik").forEachIndexed { i, n -> Tab(tab.ordinal == i, { tab = MediaType.values()[i]; sel.clear() }) { Text(n, Modifier.padding(12.dp)) } } }
            if (items.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Belum ada file di sini.\nTekan + untuk menambah.", color = Muted) }
            LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items, key = { it.file.absolutePath }) { f ->
                    Thumb(repo, f, selected = if (folder != null) sel.contains(f) else null, onToggle = { if (sel.contains(f)) sel.remove(f) else sel.add(f) }) { if (sel.isNotEmpty()) { if (sel.contains(f)) sel.remove(f) else sel.add(f) } else onFile(f) }
                }
            }
        }
    }
    if (rename && folder != null) NameDialog("Ubah Nama", folder.name) { n -> rename = false; if (n != null) { repo.renameFolder(folder, n); onChanged(); onBack() } }
    if (moveDlg) AlertDialog({ moveDlg = false }, title = { Text("Pindahkan ke") }, confirmButton = {},
        text = { Column { repo.folders().filter { it != folder }.forEach { d -> TextButton({ sel.forEach { repo.move(it, d) }; sel.clear(); moveDlg = false; onChanged() }) { Text(d.name) } } } })
}

/* ---------- PENGATURAN ---------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(pm: PinManager, onBack: () -> Unit) {
    var bio by remember { mutableStateOf(pm.biometric) }
    var auto by remember { mutableStateOf(pm.autoLockMs) }
    var pinDlg by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Scaffold(containerColor = Bg, topBar = { TopAppBar(title = { Text("Pengaturan") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Keamanan", color = Muted, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).clickable { pinDlg = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = Accent); Text("  Ubah PIN", Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Fingerprint, null, tint = Accent); Text("  Aktifkan Biometrik", Modifier.weight(1f)); Switch(bio, { bio = it; pm.biometric = it })
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).padding(16.dp)) {
                Text("Kunci Otomatis"); Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Langsung" to 0L, "1 menit" to 60_000L, "5 menit" to 300_000L).forEach { (l, v) ->
                        FilterChip(auto == v, { auto = v; pm.autoLockMs = v }, { Text(l) }) }
                }
            }
            Text("File", color = Muted, fontWeight = FontWeight.SemiBold)
            Text("Semua file & thumbnail dienkripsi AES-256 di penyimpanan privat aplikasi.", color = Muted, fontSize = 13.sp)
            Text("Tentang", color = Muted, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).padding(16.dp)) { Text("Versi Aplikasi", Modifier.weight(1f)); Text("1.0.0", color = Muted) }
        }
    }
    if (pinDlg) {
        var old by remember { mutableStateOf("") }; var new by remember { mutableStateOf("") }
        val kb = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
        val hide = androidx.compose.ui.text.input.PasswordVisualTransformation()
        AlertDialog({ pinDlg = false }, title = { Text("Ubah PIN") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(old, { if (it.length <= 4) old = it }, label = { Text("PIN lama") }, singleLine = true, keyboardOptions = kb, visualTransformation = hide)
                OutlinedTextField(new, { if (it.length <= 4) new = it }, label = { Text("PIN baru (4 digit)") }, singleLine = true, keyboardOptions = kb, visualTransformation = hide) } },
            confirmButton = { TextButton({
                val ok = pm.changePin(old, new)
                android.widget.Toast.makeText(ctx, if (ok) "PIN diubah" else "PIN lama salah / PIN baru tidak valid", android.widget.Toast.LENGTH_SHORT).show()
                if (ok) pinDlg = false }) { Text("Simpan") } },
            dismissButton = { TextButton({ pinDlg = false }) { Text("Batal") } })
    }
}