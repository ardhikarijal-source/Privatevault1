package com.example.privatevault.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.privatevault.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(repo: VaultRepository, f: VaultFile, onBack: () -> Unit, onChanged: () -> Unit) {
    var fav by remember { mutableStateOf(repo.isFav(f)) }
    Scaffold(containerColor = Color.Black,
        topBar = { TopAppBar(title = { Text(f.name, maxLines = 1) }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0F1A))) },
        bottomBar = { BottomAppBar(containerColor = Color(0xFF0A0F1A)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton({ fav = !fav; repo.setFav(f, fav); onChanged() }) { Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (fav) Color.Red else Color.White) }
                IconButton({ repo.delete(f); onChanged(); onBack() }) { Icon(Icons.Default.Delete, null) }
            } } }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) {
            // Dekripsi sementara; file sementara dihapus saat layar ditutup
            val plain = produceState<File?>(null, f.file.absolutePath) { value = withContext(Dispatchers.IO) { repo.open(f) } }
            DisposableEffect(f.file.absolutePath) { onDispose { plain.value?.delete() } }
            plain.value?.let { p ->
                when (f.type) {
                    MediaType.PHOTO -> ZoomImage(p)
                    MediaType.VIDEO -> VideoPlayer(p)
                    MediaType.COMIC -> CbzReader(p)
                    null -> {}
                }
            } ?: CircularProgressIndicator()
        }
    }
}

@Composable
fun ZoomImage(file: File) {
    var scale by remember { mutableStateOf(1f) }; var off by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    AsyncImage(file, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().clipToBounds()
        .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); off = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else off + pan } }
        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = off.x, translationY = off.y))
}

@Composable
fun VideoPlayer(file: File) {
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file))); prepare(); playWhenReady = true } }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView({ PlayerView(it).apply { this.player = player; setShowNextButton(false); setShowPreviousButton(false) } }, Modifier.fillMaxSize())
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CbzReader(file: File) {
    val names = remember(file) { ZipFile(file).use { z -> z.entries().asSequence().filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp") }.map { it.name }.sortedWith(compareBy { it.lowercase() }).toList() } }
    if (names.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("CBZ tidak berisi gambar") }; return }
    val pager = rememberPagerState { names.size }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val bmp by produceState<Bitmap?>(null, page) {
                value = withContext(Dispatchers.IO) { ZipFile(file).use { z ->
                    val e = z.getEntry(names[page]); val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                    z.getInputStream(e).use { BitmapFactory.decodeStream(it, null, o) } } }
            }
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().background(Color.White), contentScale = ContentScale.Fit) } ?: Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        }
        Text("${pager.currentPage + 1} / ${names.size}", Modifier.align(Alignment.BottomCenter).padding(12.dp).background(Color(0xAA000000)).padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White)
    }
}