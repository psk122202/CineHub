package com.cinehub.tv.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.cinehub.tv.data.tmdb.TmdbMissingKey
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.model.MediaRow
import com.cinehub.tv.ui.Backdrop
import com.cinehub.tv.ui.LocalApp
import com.cinehub.tv.ui.LocalNav
import com.cinehub.tv.ui.MediaRowView
import com.cinehub.tv.ui.MessageBox
import com.cinehub.tv.ui.Palette
import com.cinehub.tv.ui.Routes
import com.cinehub.tv.ui.TvButton
import com.cinehub.tv.ui.tryFocus
import kotlinx.coroutines.delay

/** Кэш рядов главной между переходами (чтобы не грузить заново при возврате). */
internal object HomeCache {
    var tmdbRows: List<MediaRow>? = null
    var sourceRows: List<MediaRow>? = null
    var tmdbError: String? = null
    var loadedAt = 0L

    /** Вызывается после изменения источников или настроек. */
    fun invalidate() {
        tmdbRows = null; sourceRows = null; tmdbError = null; loadedAt = 0L
    }
}

@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current

    val continueList by remember { app.observeContinue() }.collectAsState(initial = emptyList())
    val favorites by remember { app.observeList("fav") }.collectAsState(initial = emptyList())
    val watchlist by remember { app.observeList("watchlist") }.collectAsState(initial = emptyList())

    var tmdbRows by remember { mutableStateOf(HomeCache.tmdbRows) }
    var sourceRows by remember { mutableStateOf(HomeCache.sourceRows) }
    var tmdbError by remember { mutableStateOf(HomeCache.tmdbError) }
    var focused by remember { mutableStateOf<MediaItem?>(null) }
    val firstFocus = remember { FocusRequester() }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val fresh = System.currentTimeMillis() - HomeCache.loadedAt < 10 * 60_000
        if (!fresh || HomeCache.tmdbRows == null) {
            val t = runCatching { app.tmdb.homeRows() }
            tmdbRows = t.getOrDefault(emptyList())
            tmdbError = t.exceptionOrNull()?.let { if (it is TmdbMissingKey) it.message else "TMDB недоступен: ${it.message}" }
            HomeCache.tmdbRows = tmdbRows; HomeCache.tmdbError = tmdbError
        }
        if (!fresh || HomeCache.sourceRows == null) {
            sourceRows = app.sourceRows()
            HomeCache.sourceRows = sourceRows
        }
        HomeCache.loadedAt = System.currentTimeMillis()
    }

    val rows = buildList {
        if (continueList.isNotEmpty()) add(MediaRow("Продолжить просмотр", continueList.map { it.first }, landscape = true))
        sourceRows?.filter { it.title.endsWith("продолжить") || it.title.endsWith("следующие серии") }?.let { addAll(it) }
        if (favorites.isNotEmpty()) add(MediaRow("Избранное", favorites))
        if (watchlist.isNotEmpty()) add(MediaRow("Буду смотреть", watchlist))
        sourceRows?.filterNot { it.title.endsWith("продолжить") || it.title.endsWith("следующие серии") }?.let { addAll(it) }
        tmdbRows?.let { addAll(it) }
    }
    val progressMap = continueList.associate { (item, p) ->
        item.key to (if (p.durationMs > 0) p.positionMs.toFloat() / p.durationMs else 0f)
    }

    LaunchedEffect(rows.isNotEmpty()) {
        if (rows.isNotEmpty() && !focusedOnce) {
            delay(150)
            firstFocus.tryFocus()
            focusedOnce = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = focused?.backdrop, label = "backdrop") { url -> Backdrop(url) }

        val loading = tmdbRows == null || sourceRows == null
        if (rows.isEmpty() && !loading) {
            MessageBox(
                title = "Добро пожаловать в CineHub",
                text = tmdbError ?: "Добавьте источники или ключ TMDB, чтобы здесь появились фильмы.",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton("Настройки", { nav.goTab(Routes.SETTINGS) }, icon = Icons.Filled.Settings, primary = true)
                        TvButton("Источники", { nav.goTab(Routes.SOURCES) }, icon = Icons.Filled.Storage)
                    }
                },
            )
            return@Box
        }

        Column(Modifier.fillMaxSize()) {
            // Описание выделенного элемента
            Hero(focused, Modifier.fillMaxWidth().fillMaxHeight(0.38f))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
                if (tmdbError != null && tmdbRows.isNullOrEmpty()) {
                    item {
                        Text(
                            tmdbError ?: "",
                            color = Palette.Error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                        )
                    }
                }
                items(rows.size, key = { rows[it].title + it }) { i ->
                    val row = rows[i]
                    MediaRowView(
                        row = row,
                        onFocused = { focused = it },
                        progressOf = { progressMap[it.key] },
                        firstItemFocus = if (i == 0) firstFocus else null,
                    )
                }
                if (loading) item { Text("Загрузка…", color = Palette.TextDim, modifier = Modifier.padding(start = 48.dp)) }
            }
        }
    }
}

@Composable
private fun Hero(item: MediaItem?, modifier: Modifier) {
    Column(modifier.padding(start = 48.dp, top = 36.dp, end = 48.dp)) {
        if (item == null) {
            Text("CineHub", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            Text("Все фильмы и сериалы в одном месте", fontSize = 16.sp, color = Palette.TextDim)
            return@Column
        }
        Text(
            item.title,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(
            item.year?.toString(),
            when (item.kind) {
                MediaKind.MOVIE -> "Фильм"
                MediaKind.SHOW -> "Сериал"
                MediaKind.EPISODE -> item.subtitle
                MediaKind.CHANNEL -> "Трансляция"
                else -> item.subtitle
            },
            item.rating?.let { "★ " + String.format("%.1f", it) },
            item.sourceType.title,
        ).joinToString("  ·  ")
        Text(meta, fontSize = 14.sp, color = Palette.Accent, modifier = Modifier.padding(vertical = 6.dp))
        item.overview?.let {
            Text(
                it,
                fontSize = 15.sp,
                color = Palette.TextDim,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(620.dp),
            )
        }
    }
}
