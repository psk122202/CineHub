package com.cinehub.tv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.ui.LocalApp
import com.cinehub.tv.ui.LocalNav
import com.cinehub.tv.ui.MediaCard
import com.cinehub.tv.ui.MessageBox
import com.cinehub.tv.ui.Palette
import com.cinehub.tv.ui.ScreenTitle
import com.cinehub.tv.ui.TvButton
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val cont by remember { app.observeContinue() }.collectAsState(initial = emptyList())
    val fav by remember { app.observeList("fav") }.collectAsState(initial = emptyList())
    val watch by remember { app.observeList("watchlist") }.collectAsState(initial = emptyList())
    val history by remember { app.observeHistory() }.collectAsState(initial = emptyList())

    val tabs = listOf("Продолжить", "Избранное", "Буду смотреть", "История")
    val progressMap = (cont + history).associate { (i, p) ->
        i.key to if (p.durationMs > 0 && !p.finished) p.positionMs.toFloat() / p.durationMs else null
    }
    val items: List<MediaItem> = when (tab) {
        0 -> cont.map { it.first }
        1 -> fav
        2 -> watch
        else -> history.map { it.first }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Моя библиотека")
        Row(Modifier.padding(start = 48.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tabs.forEachIndexed { i, t -> TvButton(t, { tab = i }, primary = tab == i) }
            if (tab == 3 && history.isNotEmpty()) {
                TvButton("Очистить", { scope.launch { app.clearHistory() } }, icon = Icons.Filled.DeleteSweep)
            }
        }
        if (items.isEmpty()) {
            MessageBox(
                "Пока пусто",
                when (tab) {
                    1 -> "Удерживайте ОК на карточке или нажмите «В избранное» в описании фильма"
                    2 -> "Нажмите «Буду смотреть» в описании фильма"
                    else -> "Здесь появится то, что вы смотрите"
                },
            )
        } else {
            Text(
                "Удерживайте ОК, чтобы удалить из списка",
                fontSize = 12.sp, color = Palette.TextDim, modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(250.dp),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                itemsIndexed(items, key = { i, it -> it.key + "#" + i }) { _, item ->
                    MediaCard(
                        item = item,
                        landscape = true,
                        progress = progressMap[item.key],
                        onClick = { nav.open(item, items) },
                        onLongClick = {
                            scope.launch {
                                when (tab) {
                                    1 -> app.toggleList("fav", item, false)
                                    2 -> app.toggleList("watchlist", item, false)
                                    else -> app.removeProgress(item.key)
                                }
                                Toast.makeText(ctx, "Удалено", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }
}
