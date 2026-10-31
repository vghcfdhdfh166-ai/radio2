package com.example.radio

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp

data class Station(val name: String, val genre: String, val url: String, val c1: Color, val c2: Color)

val STATIONS = listOf(
    Station("Европа Плюс", "Поп · Хиты", "https://ep128.hostingradio.ru:8030/ep128", Color(0xFFFF512F), Color(0xFFF09819)),
    Station("Русское радио", "Русская поп-музыка", "https://rusradio.hostingradio.ru/rusradio128.mp3", Color(0xFFEE0979), Color(0xFFFF6A00)),
    Station("Ретро FM", "Хиты 70-х, 80-х, 90-х", "https://retroserver.streamr.ru:8043/retro128", Color(0xFF11998E), Color(0xFF38EF7D)),
    Station("Радио Рекорд", "Dance · Electronic", "https://radio-srv1.11one.ru/record192k.mp3", Color(0xFF8E2DE2), Color(0xFF4A00E0)),
    Station("DFM", "Клубная музыка", "https://dfm.hostingradio.ru/dfm128.mp3", Color(0xFF4568DC), Color(0xFFB06AB3)),
    Station("Наше Радио", "Русский рок", "https://nashe1.hostingradio.ru/nashe-128.mp3", Color(0xFF136A8A), Color(0xFF267871)),
    Station("Maximum", "Рок · Поп", "https://maximum.hostingradio.ru/maximum128.mp3", Color(0xFFFF416C), Color(0xFFFF4B2B)),
    Station("Радио Шансон", "Шансон", "https://chanson.hostingradio.ru:8041/chanson128.mp3", Color(0xFF603813), Color(0xFFB29F94)),
    Station("Jazz FM", "Джаз", "https://nashe1.hostingradio.ru/jazz-128.mp3", Color(0xFF1D2B64), Color(0xFFF8CDDA)),
    Station("Record Chill-Out", "Чилаут", "https://radiorecord.hostingradio.ru/chil96.aacp", Color(0xFF2193B0), Color(0xFF6DD5ED)),
    Station("Record Rock", "Рок", "https://radiorecord.hostingradio.ru/rock96.aacp", Color(0xFF200122), Color(0xFF6F0000)),
    Station("Record Гоп FM", "Гоп-музыка", "https://radiorecord.hostingradio.ru/gop96.aacp", Color(0xFFF7971E), Color(0xFFFFD200)),
    Station("Record Нафталин FM", "Ретро", "https://radiorecord.hostingradio.ru/naft96.aacp", Color(0xFF834D9B), Color(0xFFD04ED6)),
    Station("Record 80-х", "Хиты 80-х", "https://radiorecord.hostingradio.ru/198096.aacp", Color(0xFFDA22FF), Color(0xFF9733EE)),
    Station("Record Megamix", "Мегамикс", "https://radiorecord.hostingradio.ru/mix96.aacp", Color(0xFF00B4DB), Color(0xFF0083B0)),
    Station("Record Медляк FM", "Медленные хиты", "https://radiorecord.hostingradio.ru/mdl96.aacp", Color(0xFFCB356B), Color(0xFFBD3F32))
)

private val Bg = Color(0xFF0B0B14)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Color.White, background = Bg, surface = Bg),
                typography = Typography().withFont(FontFamily.Serif)
            ) {
                RadioApp()
            }
        }
    }
}

@Composable
fun RadioApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("radio", Context.MODE_PRIVATE) }
    val client = remember { OkHttpClient() }
    val dir = remember { ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir }

    var tab by remember { mutableStateOf(0) }
    var current by remember { mutableStateOf(STATIONS[0]) }
    var playing by remember { mutableStateOf(false) }
    var playingUri by remember { mutableStateOf("") }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    val favs = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("favs", emptySet())!!) } }
    val records = remember { mutableStateListOf<File>() }
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }
    var call by remember { mutableStateOf<Call?>(null) }

    var isAdmin by remember { mutableStateOf(prefs.getBoolean("is_admin", false)) }
    fun setAdmin(v: Boolean) {
        isAdmin = v
        prefs.edit().putBoolean("is_admin", v).apply()
    }
    var showLogin by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var photoTarget by remember { mutableStateOf("") }
    var newPhoto by remember { mutableStateOf<String?>(null) }
    val custom = remember { mutableStateListOf<Station>().apply { addAll(loadCustom(prefs)) } }
    val images = remember {
        mutableStateMapOf<String, String>().apply {
            prefs.all.forEach { (k, v) -> if (k.startsWith("img_") && v is String) put(k.removePrefix("img_"), v) }
        }
    }
    val all = STATIONS + custom

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val f = File(ctx.filesDir, "photo_${System.currentTimeMillis()}.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            if (photoTarget == "__new__") newPhoto = f.absolutePath
            else {
                images[photoTarget] = f.absolutePath
                prefs.edit().putString("img_$photoTarget", f.absolutePath).apply()
            }
        }
    }

    fun refreshRecords() {
        records.clear()
        records.addAll(dir.listFiles()?.filter { it.extension in listOf("mp3", "aac") }?.sortedByDescending { it.lastModified() } ?: emptyList())
    }

    // Разрешение на уведомления (Android 13+)
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        refreshRecords()
    }

    // Подключение к сервису воспроизведения
    DisposableEffect(Unit) {
        val token = SessionToken(ctx, ComponentName(ctx, RadioService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            fun sync() {
                playing = c.playWhenReady &&
                        c.playbackState != Player.STATE_IDLE &&
                        c.playbackState != Player.STATE_ENDED
            }
            sync()
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) { sync() }
                override fun onPlayerError(error: PlaybackException) {
                    c.stop()
                    playing = false
                }
            })
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { MediaController.releaseFuture(future) }
    }

    LaunchedEffect(recording) {
        if (recording) {
            seconds = 0
            while (true) { delay(1000); seconds++ }
        }
    }

    fun playUri(uri: String, title: String, subtitle: String) {
        playingUri = uri
        controller?.run {
            setMediaItem(
                MediaItem.Builder().setUri(uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(subtitle).build())
                    .build()
            )
            prepare()
            play()
        }
    }

    fun playStation(s: Station) {
        current = s
        playUri(s.url, s.name, s.genre)
    }

    fun toggleFav(s: Station) {
        if (!favs.remove(s.name)) favs.add(s.name)
        prefs.edit().putStringSet("favs", favs.toSet()).apply()
    }

    fun startRecording() {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val ext = if (current.url.contains("aac")) "aac" else "mp3"
        val file = File(dir, "${current.name.replace(" ", "_")}_$stamp.$ext")
        val c = client.newCall(Request.Builder().url(current.url).build())
        call = c
        recording = true
        scope.launch(Dispatchers.IO) {
            try {
                c.execute().use { resp ->
                    resp.body?.byteStream()?.use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(8192)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // отмена вызова кнопкой «стоп» тоже попадает сюда — это нормально
            } finally {
                withContext(Dispatchers.Main) {
                    recording = false
                    if (file.length() < 1024) file.delete()
                    refreshRecords()
                }
            }
        }
    }

    if (showLogin) LoginDialog(prefs, onSuccess = { prefs.edit().putBoolean("unlocked", true).apply(); setAdmin(true); showLogin = false }, onDismiss = { showLogin = false })
    if (showAdd) AddStationDialog(
        photo = newPhoto,
        onPickPhoto = { photoTarget = "__new__"; photoLauncher.launch("image/*") },
        onAdd = { n, g, u ->
            val (c1, c2) = paletteAt(custom.size)
            custom.add(Station(n, g, u, c1, c2))
            saveCustom(prefs, custom)
            newPhoto?.let { images[n] = it; prefs.edit().putString("img_$n", it).apply() }
            newPhoto = null
            showAdd = false
        },
        onDismiss = { newPhoto = null; showAdd = false }
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(current.c1.copy(alpha = 0.35f), Bg, Bg)))
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(44.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Radio", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (isAdmin) "Режим администратора" else "Слушай и записывай эфир",
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = {
                    if (isAdmin) setAdmin(false)
                    else if (prefs.getBoolean("unlocked", false)) setAdmin(true)
                    else showLogin = true
                }) {
                    Icon(if (isAdmin) Icons.Filled.Lock else Icons.Filled.Person, null, tint = Color.White)
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---------- Карточка «Сейчас играет» (компактная) ----------
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(current.c1, current.c2)))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StationCover(current, images[current.name], 56.dp, 14.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                        Text(
                            if (recording) "REC  %02d:%02d".format(seconds / 60, seconds % 60) else current.genre,
                            fontSize = 13.sp,
                            fontWeight = if (recording) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
                    Equalizer(active = playing, height = 32.dp)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RecordButton(recording, 42.dp) { if (recording) call?.cancel() else startRecording() }

                    Box(
                        Modifier.size(54.dp).clip(CircleShape).background(Color.White).clickable {
                            if (playing) controller?.stop() else playStation(current)
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        if (playing) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                repeat(2) { Box(Modifier.size(6.dp, 20.dp).clip(RoundedCornerShape(3.dp)).background(Bg)) }
                            }
                        } else {
                            Icon(Icons.Filled.PlayArrow, null, tint = Bg, modifier = Modifier.size(34.dp))
                        }
                    }

                    IconButton(onClick = { toggleFav(current) }) {
                        Icon(
                            if (current.name in favs) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            null, tint = Color.White, modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- Вкладки ----------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Станции", "Избранное", "Записи").forEachIndexed { i, label ->
                    FilterChip(
                        selected = tab == i,
                        onClick = { tab = i },
                        label = { Text(label) },
                        shape = RoundedCornerShape(50)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // ---------- Списки ----------
            if (tab < 2) {
                if (isAdmin && tab == 0) {
                    OutlinedButton(onClick = { newPhoto = null; showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Добавить станцию", color = Color.White)
                    }
                }
                val list = if (tab == 0) all else all.filter { it.name in favs }
                if (list.isEmpty()) EmptyHint("Нажми ♥ у станции, чтобы добавить её сюда")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(list) { s ->
                        val active = s == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = if (active) 0.12f else 0.06f))
                                .clickable { playStation(s) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StationCover(s, images[s.name], 52.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(s.genre, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                            }
                            if (isAdmin) {
                                IconButton(
                                    onClick = { photoTarget = s.name; photoLauncher.launch("image/*") },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, null, tint = Color.White.copy(alpha = 0.8f))
                                }
                                if (images[s.name] != null) IconButton(
                                    onClick = {
                                        images.remove(s.name)?.let { File(it).delete() }
                                        prefs.edit().remove("img_${s.name}").apply()
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Filled.Close, null, tint = Color(0xFFFFB347))
                                }
                                if (s in custom) IconButton(onClick = {
                                    custom.remove(s); saveCustom(prefs, custom)
                                    images.remove(s.name)?.let { File(it).delete() }
                                    prefs.edit().remove("img_${s.name}").apply()
                                    if (current == s) current = STATIONS[0]
                                }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF6B6B)) }
                            }
                            IconButton(onClick = { toggleFav(s) }) {
                                Icon(
                                    if (s.name in favs) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    null, tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            } else {
                if (records.isEmpty()) EmptyHint("Записей пока нет. Нажми красную кнопку во время эфира")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(records.toList()) { f ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.06f)).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(f.nameWithoutExtension, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("%.1f МБ".format(f.length() / 1048576.0), color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                            }
                            val fUri = Uri.fromFile(f).toString()
                            val isThis = playing && playingUri == fUri
                            IconButton(onClick = {
                                when {
                                    isThis -> controller?.pause()
                                    playingUri == fUri -> controller?.run { prepare(); play() }
                                    else -> playUri(fUri, f.nameWithoutExtension, "Запись")
                                }
                            }) {
                                if (isThis) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        repeat(2) { Box(Modifier.size(5.dp, 18.dp).clip(RoundedCornerShape(2.dp)).background(Color.White)) }
                                    }
                                } else {
                                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                                }
                            }
                            IconButton(onClick = { f.delete(); refreshRecords() }) {
                                Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF6B6B))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 24.dp))
}

@Composable
fun RecordButton(recording: Boolean, size: Dp = 54.dp, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "rec")
    val a by pulse.animateFloat(
        1f, 0.3f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a"
    )
    Box(
        Modifier.size(size).clip(CircleShape).border(BorderStroke(2.dp, Color.White), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(size * 0.4f)
                .alpha(if (recording) a else 1f)
                .clip(if (recording) RoundedCornerShape(5.dp) else CircleShape)
                .background(Color(0xFFFF3B3B))
        )
    }
}

@Composable
fun Equalizer(active: Boolean, height: Dp = 54.dp) {
    val t = rememberInfiniteTransition(label = "eq")
    Row(
        Modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(9) { i ->
            val h by t.animateFloat(
                0.2f, 1f,
                infiniteRepeatable(tween(380 + i * 70, easing = LinearEasing), RepeatMode.Reverse),
                label = "bar$i"
            )
            Box(
                Modifier
                    .width(7.dp)
                    .fillMaxHeight(if (active) h else 0.12f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.9f))
            )
        }
    }
}

// Шрифт со засечками (как Times New Roman) для всего приложения
fun Typography.withFont(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)