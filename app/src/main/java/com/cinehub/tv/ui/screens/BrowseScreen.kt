package com.cinehub.tv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.ui.LocalApp
import com.cinehub.tv.ui.LocalNav
import com.cinehub.tv.ui.Loading
import com.cinehub.tv.ui.MediaCard
import com.cinehub.tv.ui.MessageBox
import com.cinehub.tv.ui.ScreenTitle
import com.cinehub.tv.ui.tryFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Содержимое источника / папки / сериала / сезона. */
@Composable
fun BrowseScreen(sourceId: Long, parentKey: String?) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }

    LaunchedEffect(sourceId, parentKey) {
        val source = app.source(sourceId)
        if (source == null) { error = "Источник не найден"; return@LaunchedEffect }
        val parent = parentKey?.let { app.resolve(it) }
        title = parent?.let { p -> listOfNotNull(p.subtitle?.takeIf { p.kind == MediaKind.SEASON }, p.title).joinToString(" — ") }
            ?: source.name
        runCatching { source.browse(parent) }
            .onSuccess { items = it }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        delay(150)
        first.tryFocus()
    }

    val list = items
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(title)
        when {
            error != null -> MessageBox("Ошибка", error)
            list == null -> Loading()
            list.isEmpty() -> MessageBox("Здесь пусто", "В этой папке нет видео")
            else -> {
                val landscape = list.count { it.landscape } > list.size / 2
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (landscape) 250.dp else 150.dp),
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(list, key = { i, it -> it.key + "#" + i }) { i, item ->
                        MediaCard(
                            item = item,
                            landscape = landscape,
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            onClick = {
                                when {
                                    item.isContainer -> nav.open(item)
                                    item.kind == MediaKind.MOVIE -> nav.open(item)
                                    item.isPlayable -> {
                                        val playable = list.filter { it.isPlayable }
                                        nav.play(playable, playable.indexOfFirst { it.key == item.key }.coerceAtLeast(0))
                                    }
                                    else -> nav.open(item, list)
                                }
                            },
                            onLongClick = {
                                scope.launch {
                                    app.toggleList("fav", item, true)
                                    Toast.makeText(ctx, "«${item.title}» — в избранном", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
