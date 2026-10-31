package com.example.radio

import android.content.SharedPreferences
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

private val PALETTE = listOf(
    Color(0xFF00C6FF) to Color(0xFF0072FF),
    Color(0xFFF7971E) to Color(0xFFFFD200),
    Color(0xFFDA22FF) to Color(0xFF9733EE),
    Color(0xFF56AB2F) to Color(0xFFA8E063)
)

fun paletteAt(i: Int) = PALETTE[i % PALETTE.size]

// SHA-256 пароля администратора (сам пароль в коде не хранится)
const val ADMIN_HASH = "79e5ce37a461fe58e1bf37e55cb65612ee13c069bcaf0a79cff657773263daa5"

// Пароль нигде не хранится в открытом виде, только его хэш
fun sha(s: String): String =
    MessageDigest.getInstance("SHA-256").digest("radio_salt_$s".toByteArray())
        .joinToString("") { "%02x".format(it) }

fun loadCustom(p: SharedPreferences): List<Station> {
    val arr = JSONArray(p.getString("custom", "[]"))
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Station(o.getString("name"), o.getString("genre"), o.getString("url"), Color(o.getLong("c1")), Color(o.getLong("c2")))
    }
}

fun saveCustom(p: SharedPreferences, list: List<Station>) {
    val arr = JSONArray()
    list.forEach { s ->
        arr.put(
            JSONObject().put("name", s.name).put("genre", s.genre).put("url", s.url)
                .put("c1", s.c1.toArgb().toLong() and 0xFFFFFFFFL)
                .put("c2", s.c2.toArgb().toLong() and 0xFFFFFFFFL)
        )
    }
    p.edit().putString("custom", arr.toString()).apply()
}

@Composable
fun StationCover(s: Station, path: String?, size: Dp, radius: Dp = 14.dp) {
    val bmp = remember(path) {
        path?.let { File(it) }?.takeIf { it.exists() }?.let {
            BitmapFactory.decodeFile(it.path, BitmapFactory.Options().apply { inSampleSize = 4 })
        }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(s.c1, s.c2)))
    ) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun LoginDialog(prefs: SharedPreferences, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    val saved: String? = ADMIN_HASH // пароль задан в коде (хранится только его хэш)
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var fails by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (saved == null) "Создай пароль админа" else "Вход администратора") },
        text = {
            Column {
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, singleLine = true,
                    label = { Text("Пароль") }, visualTransformation = PasswordVisualTransformation()
                )
                if (error.isNotEmpty()) Text(error, color = Color(0xFFFF6B6B))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val now = System.currentTimeMillis()
                val lock = prefs.getLong("lock_until", 0L)
                if (now < lock) {
                    error = "Подожди ${(lock - now) / 1000 + 1} сек"
                } else if (saved == null) {
                    if (pass.length < 4) error = "Минимум 4 символа"
                    else { prefs.edit().putString("admin_hash", sha(pass)).apply(); onSuccess() }
                } else if (sha(pass.trim()) == saved) {
                    onSuccess()
                } else {
                    fails++
                    pass = ""
                    if (fails >= 3) {
                        prefs.edit().putLong("lock_until", now + 30_000).apply()
                        fails = 0
                        error = "Слишком много попыток. Подожди 30 сек"
                    } else error = "Неверный пароль"
                }
            }) { Text(if (saved == null) "Создать" else "Войти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun AddStationDialog(
    photo: String?,
    onPickPhoto: () -> Unit,
    onAdd: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая станция") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(genre, { genre = it }, label = { Text("Жанр") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Ссылка на поток (https://...)") }, singleLine = true)
                OutlinedButton(onClick = onPickPhoto) { Text(if (photo == null) "Выбрать фото" else "Фото выбрано ✓") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.trim().startsWith("http"),
                onClick = { onAdd(name.trim(), genre.trim(), url.trim()) }
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}