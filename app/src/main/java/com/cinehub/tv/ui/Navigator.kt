package com.cinehub.tv.ui

import android.net.Uri
import androidx.navigation.NavHostController
import com.cinehub.tv.model.MediaCache
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.player.PlayQueue
import kotlinx.coroutines.CoroutineScope

object Routes {
    const val PROFILES = "profiles"
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SOURCES = "sources"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val DETAILS = "details/{key}"
    const val BROWSE = "browse/{sourceId}?parent={parent}"
    const val SEASON = "season/{tvId}/{number}?show={show}"
    const val ADD_SOURCE = "addsource/{type}?id={id}"
    const val GENRES = "genres/{type}"
    const val DISCOVER = "discover/{type}/{genreId}?name={name}"

    fun details(key: String) = "details/${Uri.encode(key)}"
    fun browse(sourceId: Long, parentKey: String? = null) =
        "browse/$sourceId" + (parentKey?.let { "?parent=${Uri.encode(it)}" } ?: "")
    fun season(tvId: String, n: Int, show: String) = "season/$tvId/$n?show=${Uri.encode(show)}"
    fun addSource(type: String, id: Long? = null) = "addsource/$type" + (id?.let { "?id=$it" } ?: "")
    fun genres(type: String) = "genres/$type"
    fun discover(type: String, genreId: Int, name: String) = "discover/$type/$genreId?name=${Uri.encode(name)}"

    /** Экраны, где боковое меню скрыто. */
    val fullScreen = setOf(PROFILES, PLAYER)
}

class Navigator(val nav: NavHostController, val scope: CoroutineScope) {

    fun go(route: String) = nav.navigate(route) { launchSingleTop = true }

    /** Переход на один из разделов бокового меню. */
    fun goTab(route: String) = nav.navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun back() { nav.popBackStack() }

    /** Открыть элемент: карточка, папка или сразу воспроизведение. */
    fun open(item: MediaItem, siblings: List<MediaItem> = listOf(item)) {
        MediaCache.put(item)
        when {
            item.isTmdb -> go(Routes.details(item.key))
            item.isContainer -> go(Routes.browse(item.sourceId, item.key))
            item.kind == MediaKind.MOVIE -> go(Routes.details(item.key))
            item.kind == MediaKind.EPISODE -> go(Routes.details(item.key))
            item.isPlayable -> {
                val playable = siblings.filter { it.isPlayable }
                play(playable, playable.indexOfFirst { it.key == item.key }.coerceAtLeast(0))
            }
            else -> go(Routes.details(item.key))
        }
    }

    fun play(items: List<MediaItem>, index: Int = 0, fromStart: Boolean = false) {
        if (items.isEmpty()) return
        PlayQueue.set(items, index, fromStart)
        go(Routes.PLAYER)
    }
}
