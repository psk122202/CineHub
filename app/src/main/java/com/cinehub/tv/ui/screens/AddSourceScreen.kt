package com.cinehub.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.cinehub.tv.data.db.SourceConfig
import com.cinehub.tv.model.SourceType
import com.cinehub.tv.ui.LocalApp
import com.cinehub.tv.ui.LocalNav
import com.cinehub.tv.ui.Palette
import com.cinehub.tv.ui.ScreenTitle
import com.cinehub.tv.ui.TvButton
import com.cinehub.tv.ui.tryFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AddSourceScreen(typeName: String, editId: Long) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val type = runCatching { SourceType.valueOf(typeName) }.getOrDefault(SourceType.LINKS)

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    LaunchedEffect(editId) {
        if (editId > 0) app.db.sources().get(editId)?.let { c ->
            name = c.name; url = c.url; user = c.username; pass = c.password; token = c.token
        }
        delay(150)
        first.tryFocus()
    }

    val (title, hint) = when (type) {
        SourceType.JELLYFIN -> "Jellyfin / Emby" to
            "Адрес сервера, например http://192.168.1.10:8096 (для Emby — :8096 или :8920). Логин и пароль — как в веб-интерфейсе."
        SourceType.PLEX -> "Plex" to
            "Адрес сервера, например http://192.168.1.10:32400 и X-Plex-Token. Токен: откройте любой фильм в веб-Plex → ⋯ → «Получить информацию» → «Просмотреть XML», в ссылке будет X-Plex-Token=…"
        SourceType.SMB -> "Сетевая папка (SMB)" to
            "Путь вида smb://192.168.1.10/Movies или \\\\NAS\\Video. Для общего доступа без пароля оставьте логин пустым. Домен можно указать как ДОМЕН\\логин."
        SourceType.M3U -> "Плейлист M3U" to
            "Ссылка на файл .m3u / .m3u8 со списком видео или каналов."
        else -> "Ссылка на видео" to
            "Прямая ссылка на видеофайл или поток: .mp4, .mkv, .m3u8 (HLS), .mpd (DASH)."
    }

    fun save() {
        if (url.isBlank()) { status = "Укажите адрес"; isError = true; return }
        busy = true; isError = false; status = "Проверяю подключение…"
        scope.launch {
            try {
                if (type == SourceType.LINKS) {
                    app.addLink(name.ifBlank { url.substringAfterLast('/').substringBefore('?') }, url.trim())
                } else {
                    val cfg = SourceConfig(
                        id = if (editId > 0) editId else 0,
                        type = type.name,
                        name = name.ifBlank { title },
                        url = url.trim(),
                        username = user.trim(),
                        password = pass,
                        token = if (type == SourceType.JELLYFIN) "" else token.trim(),
                    )
                    app.testSource(cfg)
                    // для Jellyfin токен получит сам источник при первом запросе
                    app.saveSource(cfg)
                }
                HomeCache.invalidate()
                status = "Готово!"
                nav.back()
            } catch (e: Throwable) {
                isError = true
                status = "Ошибка: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item { ScreenTitle((if (editId > 0) "Изменить: " else "Добавить: ") + title) }
        item {
            Column(Modifier.padding(horizontal = 48.dp).width(680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(hint, fontSize = 14.sp, color = Palette.TextDim)
                Field("Название", name, { name = it }, Modifier.focusRequester(first))
                Field(if (type == SourceType.SMB) "Путь к папке" else "Адрес", url, { url = it }, keyboard = KeyboardType.Uri)
                if (type == SourceType.JELLYFIN || type == SourceType.SMB) {
                    Field("Логин", user, { user = it })
                    Field("Пароль", pass, { pass = it }, password = true)
                }
                if (type == SourceType.PLEX) Field("X-Plex-Token", token, { token = it })
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(if (busy) "Подождите…" else "Сохранить", { if (!busy) save() }, icon = Icons.Filled.Check, primary = true)
                    TvButton("Отмена", { nav.back() })
                }
                status?.let { Text(it, color = if (isError) Palette.Error else Palette.Good, fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { androidx.compose.material3.Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Accent,
            unfocusedBorderColor = Palette.SurfaceHi,
            focusedTextColor = Palette.Text,
            unfocusedTextColor = Palette.Text,
            focusedLabelColor = Palette.Accent,
            unfocusedLabelColor = Palette.TextDim,
            cursorColor = Palette.Accent,
        ),
        modifier = modifier.width(680.dp),
    )
}
