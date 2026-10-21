package com.cinehub.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.ui.LocalApp
import com.cinehub.tv.ui.LocalNav
import com.cinehub.tv.ui.Loading
import com.cinehub.tv.ui.MediaCard
import com.cinehub.tv.ui.MessageBox
import com.cinehub.tv.ui.Palette
import com.cinehub.tv.ui.Routes
import com.cinehub.tv.ui.ScreenTitle
import com.cinehub.tv.ui.TvButton
import com.cinehub.tv.ui.tryFocus
import kotlinx.coroutines.delay

/** Список жанров TMDB для фильмов или сериалов. */
@Composable
fun GenresScreen(type: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    var genres by remember(type) { mutableStateOf<List<Pair<Int, String>>?>(null) }
    var error by remember(type) { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }

    LaunchedEffect(type) {
        runCatching { app.tmdb.genres(type) }.onSuccess { genres = it }.onFailure { error = it.message }
        delay(150); first.tryFocus()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Жанры")
        Row(Modifier.padding(start = 48.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Фильмы", { if (type != "movie") nav.nav.navigate(Routes.genres("movie")) { popUpTo(Routes.GENRES) { inclusive = true } } }, primary = type == "movie")
            TvButton("Сериалы", { if (type != "tv") nav.nav.navigate(Routes.genres("tv")) { popUpTo(Routes.GENRES) { inclusive = true } } }, primary = type == "tv")
        }
        val list = genres
        when {
            error != null -> MessageBox("TMDB недоступен", error)
            list == null -> Loading()
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(list, key = { _, g -> g.first }) { i, (id, name) ->
                    Surface(
                        onClick = { nav.go(Routes.discover(type, id, name)) },
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                            .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Palette.Surface,
                            focusedContainerColor = Palette.Accent,
                            focusedContentColor = Palette.OnAccent,
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name.replaceFirstChar { it.uppercase() }, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** Популярное в выбранном жанре, с подгрузкой страниц. */
@Composable
fun DiscoverScreen(type: String, genreId: Int, name: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val state = rememberLazyGridState()
    val first = remember { FocusRequester() }

    LaunchedEffect(page) {
        loading = true
        runCatching { app.tmdb.discover(type, genreId, page) }
            .onSuccess { new -> items = (items + new).distinctBy { it.key } }
            .onFailure { error = it.message }
        loading = false
        if (page == 1) { delay(150); first.tryFocus() }
    }

    val nearEnd by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            items.isNotEmpty() && last >= items.size - 6
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd && !loading && page < 20) page++ }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(name.replaceFirstChar { it.uppercase() } + if (type == "tv") " — сериалы" else " — фильмы")
        when {
            error != null && items.isEmpty() -> MessageBox("Ошибка", error)
            items.isEmpty() -> Loading()
            else -> LazyVerticalGrid(
                state = state,
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.key }) { i, item ->
                    MediaCard(
                        item = item,
                        landscape = false,
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        onClick = { nav.open(item) },
                    )
                }
            }
        }
    }
}
