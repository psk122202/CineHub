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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.cinehub.tv.BuildConfig
import com.cinehub.tv.ui.LocalApp
import com.cinehub.tv.ui.LocalNav
import com.cinehub.tv.ui.Palette
import com.cinehub.tv.ui.Routes
import com.cinehub.tv.ui.ScreenTitle
import com.cinehub.tv.ui.SectionTitle
import com.cinehub.tv.ui.TvButton
import com.cinehub.tv.ui.tryFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val languages = listOf("ru-RU" to "Русский", "uk-UA" to "Українська", "en-US" to "English")
private val regions = listOf(
    "UA" to "Украина", "PL" to "Польша", "DE" to "Германия", "US" to "США", "GB" to "Великобритания",
    "KZ" to "Казахстан", "RU" to "Россия", "CZ" to "Чехия", "LT" to "Литва",
)

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val settings by app.settingsFlow.collectAsState()
    var key by remember { mutableStateOf("") }
    var keyStatus by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        key = app.settings.current().tmdbKey
        delay(150)
        first.tryFocus()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
        item { ScreenTitle("Настройки") }

        item { Column {
            SectionTitle("Каталог TMDB")
            Column(Modifier.padding(horizontal = 48.dp).width(760.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Бесплатный ключ: зарегистрируйтесь на themoviedb.org → Настройки → API → «Create» (тип Developer). " +
                        "Подойдёт «API Key» (32 символа) или «API Read Access Token».",
                    fontSize = 14.sp, color = Palette.TextDim,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    label = { androidx.compose.material3.Text("API-ключ TMDB") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.Accent,
                        unfocusedBorderColor = Palette.SurfaceHi,
                        focusedTextColor = Palette.Text,
                        unfocusedTextColor = Palette.Text,
                        focusedLabelColor = Palette.Accent,
                        unfocusedLabelColor = Palette.TextDim,
                    ),
                    modifier = Modifier.width(760.dp).focusRequester(first),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Сохранить и проверить", {
                        scope.launch {
                            app.settings.setTmdbKey(key)
                            keyStatus = "Проверяю…" to true
                            keyStatus = runCatching { app.tmdb.ping() }.fold(
                                { "Ключ работает" to true },
                                { "Ошибка: ${it.message}" to false },
                            )
                            HomeCache.invalidate()
                        }
                    }, icon = Icons.Filled.Check, primary = true)
                }
                keyStatus?.let { (t, ok) -> Text(t, color = if (ok) Palette.Good else Palette.Error, fontSize = 14.sp) }
            }
            Spacer(Modifier.height(20.dp))
        } }

        item { Column {
            SectionTitle("Язык описаний")
            LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(languages) { (code, title) ->
                    TvButton(title, {
                        scope.launch { app.settings.setLanguage(code); HomeCache.invalidate() }
                    }, primary = settings?.language == code)
                }
            }
            Spacer(Modifier.height(16.dp))
        } }

        item { Column {
            SectionTitle("Страна (для «Где смотреть»)")
            LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(regions) { (code, title) ->
                    TvButton(title, {
                        scope.launch { app.settings.setRegion(code); HomeCache.invalidate() }
                    }, primary = settings?.region == code)
                }
            }
            Spacer(Modifier.height(16.dp))
        } }

        item { Column {
            SectionTitle("Профили и история")
            Row(Modifier.padding(horizontal = 48.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton("Управление профилями", { nav.go(Routes.PROFILES) }, icon = Icons.Filled.Group)
                TvButton("Очистить историю текущего профиля", { scope.launch { app.clearHistory() } })
            }
            Spacer(Modifier.height(24.dp))
        } }

        item { Column {
            SectionTitle("О приложении")
            Column(Modifier.padding(horizontal = 48.dp).width(760.dp)) {
                Text("CineHub ${BuildConfig.VERSION_NAME}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                Text(
                    "Библиотека фильмов и сериалов: каталог TMDB, Jellyfin/Emby, Plex, сетевые папки, флешки, M3U и прямые ссылки. " +
                        "Этот продукт использует TMDB API, но не одобрен и не сертифицирован TMDB.",
                    fontSize = 13.sp, color = Palette.TextDim, modifier = Modifier.padding(top = 4.dp),
                )
            }
        } }
    }
}
