package com.cinehub.tv.player

import com.cinehub.tv.model.MediaItem

/** Очередь воспроизведения, которую заполняют экраны перед открытием плеера. */
object PlayQueue {
    var items: List<MediaItem> = emptyList()
        private set
    var startIndex: Int = 0
        private set
    var fromStart: Boolean = false
        private set

    fun set(items: List<MediaItem>, index: Int, fromStart: Boolean) {
        this.items = items
        this.startIndex = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        this.fromStart = fromStart
    }
}
