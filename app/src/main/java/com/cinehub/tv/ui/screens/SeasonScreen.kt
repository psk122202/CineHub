package com.cinehub.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.cinehub.tv.data.tmdb.Episode
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaRow
import com.cinehub.tv.ui.LocalApp
import com.cinehub.tv.ui.Loading
import com.cinehub.tv.ui.MediaRowView
import com.cinehub.tv.ui.MessageBox
import com.cinehub.tv.ui.Palette
import com.cinehub.tv.ui.ScreenTitle
import com.cinehub.tv.ui.tryFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Список серий сезона (из TMDB) + поиск сериала в своих источниках. */
@Composable
fun SeasonScreen(tvId: String, number: Int, showTitle: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var episodes by remember { mutableStateOf<List<Episode>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<List<MediaItem>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    LaunchedEffect(tvId, number) {
        runCatching { app.tmdb.season(tvId, number) }
            .onSuccess { episodes = it }
            .onFailure { error = it.message }
        delay(150)
        first.tryFocus()
    }

    fun findShow() {
        searching = true
        scope.launch {
            matches = runCatching { app.findInSources(showTitle, null, null) }.getOrDefault(emptyList())
            searching = false
        }
    }

    val list = episodes
    when {
        error != null -> MessageBox("Не удалось загрузить сезон", error)
        list == null -> Loading()
        else -> LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
            item { ScreenTitle("$showTitle — сезон $number") }
            item {
                val m = matches
                when {
                    searching -> Text("Ищу сериал в ваших источниках…", color = Palette.TextDim, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
                    m != null && m.isNotEmpty() -> MediaRowView(MediaRow("Сериал в ваших источниках", m, landscape = true))
                    m != null -> Text("В подключённых источниках не найдено", color = Palette.TextDim, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
                }
            }
            itemsIndexed(list, key = { _, e -> e.number }) { i, e ->
                Surface(
                    onClick = { findShow() },
                    modifier = Modifier
                        .padding(horizontal = 48.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Palette.Surface, focusedContainerColor = Palette.SurfaceHi),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.width(200.dp).height(112.dp)) {
                            if (e.still != null) AsyncImage(
                                model = e.still, contentDescription = e.name,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Column {
                            Text("${e.number}. ${e.name}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                            Text(
                                listOfNotNull(e.airDate, e.runtime?.let { "$it мин" }).joinToString(" · "),
                                fontSize = 13.sp, color = Palette.Accent, modifier = Modifier.padding(vertical = 4.dp),
                            )
                            e.overview?.let { Text(it, fontSize = 14.sp, color = Palette.TextDim, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}
