package com.cinehub.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.cinehub.tv.data.tmdb.TmdbDetails
import com.cinehub.tv.model.MediaCache
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.model.MediaRow
import com.cinehub.tv.ui.Backdrop
import com.cinehub.tv.ui.External
import com.cinehub.tv.ui.LocalApp
import com.cinehub.tv.ui.LocalNav
import com.cinehub.tv.ui.Loading
import com.cinehub.tv.ui.MediaRowView
import com.cinehub.tv.ui.MessageBox
import com.cinehub.tv.ui.Palette
import com.cinehub.tv.ui.Routes
import com.cinehub.tv.ui.SectionTitle
import com.cinehub.tv.ui.TvButton
import com.cinehub.tv.ui.formatTime
import com.cinehub.tv.ui.tryFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DetailsScreen(key: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var item by remember(key) { mutableStateOf(MediaCache.get(key)) }
    var details by remember(key) { mutableStateOf<TmdbDetails?>(null) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var matches by remember(key) { mutableStateOf<List<MediaItem>?>(null) }
    var searching by remember(key) { mutableStateOf(false) }

    val progress by remember(key) { app.observeProgress(key) }.collectAsState(initial = null)
    val isFav by remember(key) { app.observeInList("fav", key) }.collectAsState(initial = false)
    val inWatchlist by remember(key) { app.observeInList("watchlist", key) }.collectAsState(initial = false)
    val firstButton = remember { FocusRequester() }

    fun searchSources() {
        val it = item ?: return
        searching = true
        scope.launch {
            matches = runCatching {
                app.findInSources(it.title, details?.originalTitle, it.year)
            }.getOrDefault(emptyList())
            searching = false
        }
    }

    LaunchedEffect(key) {
        if (item == null) item = app.resolve(key)
        if (key.startsWith("tmdb:")) {
            val parts = key.split(":")
            runCatching { app.tmdb.details(parts[1], parts[2]) }
                .onSuccess { d ->
                    details = d
                    item = d.item
                    searchSources() // сразу ищем в своих источниках
                }
                .onFailure { error = it.message }
        }
        delay(200)
        firstButton.tryFocus()
    }

    val current = item
    if (current == null) {
        if (error != null) MessageBox("Не удалось загрузить", error) else Loading()
        return
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(current.backdrop)
        LazyColumn(contentPadding = PaddingValues(top = 48.dp, bottom = 48.dp)) {
            item { Column {
                Column(Modifier.padding(horizontal = 48.dp).width(720.dp)) {
                    Text(current.title, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 2)
                    details?.originalTitle?.takeIf { it != current.title }?.let {
                        Text(it, fontSize = 16.sp, color = Palette.TextDim)
                    }
                    val meta = listOfNotNull(
                        current.year?.toString(),
                        current.subtitle,
                        details?.genres?.take(3)?.joinToString(", ")?.takeIf { it.isNotBlank() },
                        (details?.runtimeMin ?: current.durationMs?.let { (it / 60000).toInt() })?.takeIf { it > 0 }?.let { "$it мин" },
                        current.rating?.let { "★ " + String.format("%.1f", it) },
                        current.sourceType.title,
                    ).joinToString("  ·  ")
                    Text(meta, fontSize = 15.sp, color = Palette.Accent, modifier = Modifier.padding(vertical = 10.dp))
                    details?.tagline?.let { Text("«$it»", fontSize = 15.sp, color = Palette.Text, modifier = Modifier.padding(bottom = 6.dp)) }
                    current.overview?.let {
                        Text(it, fontSize = 16.sp, color = Palette.TextDim, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(22.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (current.isPlayable) {
                            val resumeAt = progress?.takeIf { !it.finished && it.positionMs > 30_000 }?.positionMs
                                ?: current.serverPositionMs?.takeIf { it > 30_000 }
                            TvButton(
                                if (resumeAt != null) "Продолжить с ${formatTime(resumeAt)}" else "Смотреть",
                                { nav.play(listOf(current)) },
                                icon = Icons.Filled.PlayArrow,
                                primary = true,
                                modifier = Modifier.focusRequester(firstButton),
                            )
                            if (resumeAt != null) {
                                TvButton("Сначала", { nav.play(listOf(current), fromStart = true) }, icon = Icons.Filled.Replay)
                            }
                        } else if (current.isTmdb) {
                            TvButton(
                                if (searching) "Ищу в источниках…" else "Найти в моих источниках",
                                { searchSources() },
                                icon = Icons.Filled.Search,
                                primary = true,
                                modifier = Modifier.focusRequester(firstButton),
                            )
                        }
                        details?.trailerYoutubeKey?.let { yt ->
                            TvButton("Трейлер", { External.openYoutube(ctx, yt) }, icon = Icons.Filled.Theaters)
                        }
                        TvButton(
                            if (isFav) "В избранном" else "В избранное",
                            { scope.launch { app.toggleList("fav", current, !isFav) } },
                            icon = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            modifier = if (!current.isPlayable && !current.isTmdb) Modifier.focusRequester(firstButton) else Modifier,
                        )
                        TvButton(
                            if (inWatchlist) "В списке" else "Буду смотреть",
                            { scope.launch { app.toggleList("watchlist", current, !inWatchlist) } },
                            icon = if (inWatchlist) Icons.Filled.BookmarkAdded else Icons.Filled.BookmarkAdd,
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                }
            } }

            // Найдено в своих источниках
            if (current.isTmdb) {
                item { Column {
                    val m = matches
                    when {
                        searching -> Text("Ищу «${current.title}» в ваших источниках…", color = Palette.TextDim, modifier = Modifier.padding(start = 48.dp, bottom = 18.dp))
                        m != null && m.isNotEmpty() -> MediaRowView(MediaRow("Есть в ваших источниках", m, landscape = true))
                        m != null -> Text(
                            "В подключённых источниках не найдено." +
                                if (details?.providers?.isNotEmpty() == true) " Посмотрите, где он доступен легально ↓" else "",
                            color = Palette.TextDim,
                            modifier = Modifier.padding(start = 48.dp, bottom = 18.dp),
                        )
                    }
                } }
            }

            val d = details
            if (d != null) {
                if (d.providers.isNotEmpty()) {
                    item { Column {
                        SectionTitle("Где смотреть")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(d.providers, key = { it.id }) { p ->
                                Surface(
                                    onClick = { External.openProvider(ctx, p.name) },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                    colors = ClickableSurfaceDefaults.colors(containerColor = Palette.Surface, focusedContainerColor = Palette.SurfaceHi),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                                ) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = p.logo, contentDescription = p.name,
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                                        )
                                        Column(Modifier.padding(start = 10.dp, end = 6.dp)) {
                                            Text(p.name, fontSize = 14.sp, color = Palette.Text, maxLines = 1)
                                            Text(p.kind, fontSize = 12.sp, color = Palette.TextDim)
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            "Данные о сервисах: JustWatch через TMDB",
                            fontSize = 11.sp, color = Palette.TextDim,
                            modifier = Modifier.padding(start = 48.dp, top = 4.dp, bottom = 18.dp),
                        )
                    } }
                }

                if (d.seasons.isNotEmpty()) {
                    item { Column {
                        SectionTitle("Сезоны")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(d.seasons, key = { it.number }) { s ->
                                val seasonItem = MediaItem(
                                    key = "tmdb:season:${current.remoteId}:${s.number}",
                                    sourceType = current.sourceType,
                                    kind = MediaKind.SEASON,
                                    title = s.name,
                                    subtitle = "${s.episodes} серий" + (s.year?.let { " · $it" } ?: ""),
                                    poster = s.poster ?: current.poster,
                                )
                                com.cinehub.tv.ui.MediaCard(
                                    item = seasonItem,
                                    landscape = false,
                                    onClick = { nav.go(Routes.season(current.remoteId ?: "", s.number, current.title)) },
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    } }
                }

                if (d.cast.isNotEmpty()) {
                    item { Column {
                        SectionTitle("В ролях")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(d.cast) { c ->
                                Surface(
                                    onClick = {},
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                    colors = ClickableSurfaceDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent, focusedContainerColor = Palette.Surface),
                                ) {
                                    Column(Modifier.width(110.dp).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.size(84.dp).clip(CircleShape).background(Palette.SurfaceHi)) {
                                            if (c.photo != null) AsyncImage(
                                                model = c.photo, contentDescription = c.name,
                                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                        Text(c.name, fontSize = 13.sp, color = Palette.Text, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
                                        c.character?.let { Text(it, fontSize = 11.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    } }
                }

                if (d.recommendations.isNotEmpty()) {
                    item { MediaRowView(MediaRow("Похожие", d.recommendations)) }
                }
            }
        }
    }
}
