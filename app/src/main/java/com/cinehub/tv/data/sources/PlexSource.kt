package com.cinehub.tv.data.sources

import com.cinehub.tv.data.Http
import com.cinehub.tv.data.arr
import com.cinehub.tv.data.db.SourceConfig
import com.cinehub.tv.data.dbl
import com.cinehub.tv.data.int
import com.cinehub.tv.data.long
import com.cinehub.tv.data.obj
import com.cinehub.tv.data.str
import com.cinehub.tv.data.trimSlash
import com.cinehub.tv.data.urlEncode
import com.cinehub.tv.model.MediaCache
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.model.MediaRow
import com.cinehub.tv.model.SourceType
import kotlinx.serialization.json.JsonObject

/** Plex Media Server. Нужен адрес сервера (http://IP:32400) и X-Plex-Token. */
class PlexSource(
    private val cfg: SourceConfig,
    private val deviceId: String,
) : VideoSource {
    override val id get() = cfg.id
    override val name get() = cfg.name
    override val type = SourceType.PLEX

    private val base get() = cfg.url.trimSlash()
    private val token get() = cfg.token

    private fun headers() = mapOf(
        "X-Plex-Token" to token,
        "X-Plex-Client-Identifier" to deviceId,
        "X-Plex-Product" to "CineHub",
        "X-Plex-Platform" to "Android",
    )

    private suspend fun container(path: String): JsonObject {
        val sep = if ('?' in path) '&' else '?'
        return Http.getJson("$base$path${sep}X-Plex-Token=$token", headers()).obj("MediaContainer")
            ?: JsonObject(emptyMap())
    }

    private fun img(path: String?): String? = path?.let {
        if (it.startsWith("http")) it else "$base$it?X-Plex-Token=$token"
    }

    private fun parse(o: JsonObject): MediaItem? {
        val rk = o.str("ratingKey") ?: return null
        val kind = when (o.str("type")) {
            "movie" -> MediaKind.MOVIE
            "show" -> MediaKind.SHOW
            "season" -> MediaKind.SEASON
            "episode" -> MediaKind.EPISODE
            "clip" -> MediaKind.VIDEO
            else -> return null
        }
        val partKey = o.arr("Media").firstOrNull()?.arr("Part")?.firstOrNull()?.str("key")
        val subtitle = when (kind) {
            MediaKind.EPISODE -> listOfNotNull(
                o.str("grandparentTitle"),
                o.int("parentIndex")?.let { "S${it}E${o.int("index") ?: "?"}" },
            ).joinToString(" · ")
            MediaKind.SEASON -> o.str("parentTitle")
            else -> null
        }
        return MediaItem(
            key = "plex:${cfg.id}:$rk",
            sourceType = SourceType.PLEX,
            sourceId = cfg.id,
            kind = kind,
            title = o.str("title") ?: "Без названия",
            subtitle = subtitle,
            overview = o.str("summary"),
            year = o.int("year"),
            poster = img(
                if (kind == MediaKind.EPISODE) o.str("thumb") ?: o.str("grandparentThumb")
                else o.str("thumb") ?: o.str("parentThumb"),
            ),
            backdrop = img(o.str("art") ?: o.str("grandparentArt")),
            rating = o.dbl("audienceRating") ?: o.dbl("rating"),
            streamUrl = partKey?.let { "$base$it?X-Plex-Token=$token" },
            durationMs = o.long("duration"),
            remoteId = rk,
            serverPositionMs = o.long("viewOffset"),
            landscape = kind == MediaKind.EPISODE,
        ).also(MediaCache::put)
    }

    override suspend fun homeRows(): List<MediaRow> {
        val rows = mutableListOf<MediaRow>()
        runCatching { container("/library/onDeck").arr("Metadata").mapNotNull(::parse) }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let { rows += MediaRow("$name: продолжить", it, landscape = true) }
        runCatching {
            container("/library/recentlyAdded?X-Plex-Container-Start=0&X-Plex-Container-Size=30")
                .arr("Metadata").mapNotNull(::parse)
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { rows += MediaRow("$name: новое", it) }
        return rows
    }

    override suspend fun browse(parent: MediaItem?): List<MediaItem> {
        if (parent == null) {
            return container("/library/sections").arr("Directory").mapNotNull { d ->
                val k = d.str("key") ?: return@mapNotNull null
                MediaItem(
                    key = "plex:${cfg.id}:section:$k",
                    sourceType = SourceType.PLEX,
                    sourceId = cfg.id,
                    kind = MediaKind.FOLDER,
                    title = d.str("title") ?: "Раздел",
                    subtitle = d.str("type"),
                    remoteId = "section:$k",
                    poster = img(d.str("thumb")),
                ).also(MediaCache::put)
            }
        }
        val rid = parent.remoteId ?: return emptyList()
        val path = if (rid.startsWith("section:")) "/library/sections/${rid.removePrefix("section:")}/all"
        else "/library/metadata/$rid/children"
        return container(path).arr("Metadata").mapNotNull(::parse)
    }

    override suspend fun search(query: String): List<MediaItem> =
        container("/hubs/search?query=${query.urlEncode()}&limit=30").arr("Hub")
            .flatMap { it.arr("Metadata") }
            .mapNotNull(::parse)
            .filter { it.kind in setOf(MediaKind.MOVIE, MediaKind.SHOW, MediaKind.EPISODE) }

    override suspend fun reportProgress(item: MediaItem, positionMs: Long, finished: Boolean) {
        val rk = item.remoteId ?: return
        runCatching {
            if (finished) {
                Http.getText("$base/:/scrobble?key=$rk&identifier=com.plexapp.plugins.library&X-Plex-Token=$token", headers())
            } else {
                Http.getText(
                    "$base/:/timeline?ratingKey=$rk&key=${"/library/metadata/$rk".urlEncode()}" +
                        "&state=stopped&time=$positionMs&duration=${item.durationMs ?: 0}&X-Plex-Token=$token",
                    headers(),
                )
            }
        }
    }
}
