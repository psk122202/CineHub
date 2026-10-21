package com.cinehub.tv.data.sources

import com.cinehub.tv.data.db.LinkDao
import com.cinehub.tv.data.db.UserLink
import com.cinehub.tv.model.MediaCache
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.model.MediaRow
import com.cinehub.tv.model.SourceType

/** Прямые ссылки на видео (mp4, m3u8, mpd…), добавленные вручную. */
class LinksSource(private val dao: LinkDao) : VideoSource {
    override val id = LINKS_ID
    override val name = "Мои ссылки"
    override val type = SourceType.LINKS

    private fun UserLink.toItem(): MediaItem {
        val lower = url.lowercase()
        val live = lower.contains(".m3u8") && !lower.contains("vod")
        return MediaItem(
            key = "link:$id",
            sourceType = SourceType.LINKS,
            sourceId = LINKS_ID,
            kind = if (live) MediaKind.CHANNEL else MediaKind.VIDEO,
            title = title,
            subtitle = url.substringAfter("://").substringBefore('/'),
            poster = poster.ifBlank { null },
            streamUrl = url,
            remoteId = id.toString(),
            landscape = true,
        ).also(MediaCache::put)
    }

    override suspend fun homeRows(): List<MediaRow> {
        val items = dao.all().map { it.toItem() }
        return if (items.isEmpty()) emptyList() else listOf(MediaRow(name, items, landscape = true))
    }

    override suspend fun browse(parent: MediaItem?): List<MediaItem> = dao.all().map { it.toItem() }

    override suspend fun search(query: String): List<MediaItem> {
        val q = normalizeTitle(query)
        return dao.all().filter { normalizeTitle(it.title).contains(q) }.map { it.toItem() }
    }

    override suspend fun test() {}

    companion object { const val LINKS_ID = -2L }
}
