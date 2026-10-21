package com.cinehub.tv.data.sources

import com.cinehub.tv.BuildConfig
import com.cinehub.tv.data.Http
import com.cinehub.tv.data.arr
import com.cinehub.tv.data.bool
import com.cinehub.tv.data.db.SourceConfig
import com.cinehub.tv.data.dbl
import com.cinehub.tv.data.int
import com.cinehub.tv.data.long
import com.cinehub.tv.data.obj
import com.cinehub.tv.data.str
import com.cinehub.tv.data.strList
import com.cinehub.tv.data.trimSlash
import com.cinehub.tv.data.urlEncode
import com.cinehub.tv.model.MediaCache
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.model.MediaRow
import com.cinehub.tv.model.SourceType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Jellyfin и Emby (у них общий API). */
class JellyfinSource(
    private var cfg: SourceConfig,
    private val deviceId: String,
    private val onAuthUpdated: suspend (SourceConfig) -> Unit,
) : VideoSource {
    override val id get() = cfg.id
    override val name get() = cfg.name
    override val type = SourceType.JELLYFIN

    private val base get() = cfg.url.trimSlash()
    private val authMutex = Mutex()

    private fun authHeader(token: String?) = buildString {
        append("MediaBrowser Client=\"CineHub\", Device=\"Android TV\", DeviceId=\"$deviceId\", Version=\"${BuildConfig.VERSION_NAME}\"")
        if (!token.isNullOrBlank()) append(", Token=\"$token\"")
    }

    private fun headers(): Map<String, String> = mapOf(
        "X-Emby-Authorization" to authHeader(cfg.token),
        "Authorization" to authHeader(cfg.token),
        "X-Emby-Token" to cfg.token,
    )

    private suspend fun ensureAuth() = authMutex.withLock {
        if (cfg.token.isNotBlank() && cfg.userId.isNotBlank()) return@withLock
        val body = buildJsonObject {
            put("Username", cfg.username)
            put("Pw", cfg.password)
        }.toString()
        val res = Http.postJson(
            "$base/Users/AuthenticateByName", body,
            mapOf("X-Emby-Authorization" to authHeader(null), "Authorization" to authHeader(null)),
        )
        val token = res.str("AccessToken") ?: error("Сервер не вернул токен")
        val userId = res.obj("User")?.str("Id") ?: error("Сервер не вернул пользователя")
        cfg = cfg.copy(token = token, userId = userId)
        onAuthUpdated(cfg)
    }

    private suspend fun call(pathAndQuery: String): JsonObject {
        ensureAuth()
        return try {
            Http.getJson("$base$pathAndQuery", headers())
        } catch (e: com.cinehub.tv.data.HttpException) {
            if (e.code == 401 && cfg.password.isNotEmpty()) {
                cfg = cfg.copy(token = "", userId = "")
                ensureAuth()
                Http.getJson("$base$pathAndQuery", headers())
            } else throw e
        }
    }

    private suspend fun callArray(pathAndQuery: String): List<JsonObject> {
        ensureAuth()
        val el = Http.getJsonAny("$base$pathAndQuery", headers())
        return when (el) {
            is JsonArray -> el.mapNotNull { it as? JsonObject }
            is JsonObject -> el.arr("Items")
            else -> emptyList()
        }
    }

    private val fields = "Fields=Overview,PrimaryImageAspectRatio,ProductionYear&EnableImageTypes=Primary,Backdrop,Thumb"

    private fun parse(o: JsonObject): MediaItem? {
        val itemId = o.str("Id") ?: return null
        val t = o.str("Type") ?: ""
        val kind = when (t) {
            "Movie" -> MediaKind.MOVIE
            "Series" -> MediaKind.SHOW
            "Season" -> MediaKind.SEASON
            "Episode" -> MediaKind.EPISODE
            "TvChannel" -> MediaKind.CHANNEL
            "Video", "MusicVideo", "Trailer" -> MediaKind.VIDEO
            else -> if (o.bool("IsFolder") == true) MediaKind.FOLDER else MediaKind.VIDEO
        }
        val primaryTag = o.obj("ImageTags")?.str("Primary")
        val poster = when {
            primaryTag != null -> "$base/Items/$itemId/Images/Primary?maxHeight=480&tag=$primaryTag"
            kind == MediaKind.SEASON || kind == MediaKind.EPISODE ->
                o.str("SeriesId")?.let { "$base/Items/$it/Images/Primary?maxHeight=480" }
            else -> null
        }
        val backdrop = when {
            o.strList("BackdropImageTags").isNotEmpty() -> "$base/Items/$itemId/Images/Backdrop/0?maxWidth=1280"
            o.str("ParentBackdropItemId") != null -> "$base/Items/${o.str("ParentBackdropItemId")}/Images/Backdrop/0?maxWidth=1280"
            else -> null
        }
        val subtitle = when (kind) {
            MediaKind.EPISODE -> listOfNotNull(
                o.str("SeriesName"),
                o.int("ParentIndexNumber")?.let { s -> "S${s}E${o.int("IndexNumber") ?: "?"}" },
            ).joinToString(" · ")
            MediaKind.SEASON -> o.str("SeriesName")
            else -> null
        }
        val playable = kind in setOf(MediaKind.MOVIE, MediaKind.EPISODE, MediaKind.VIDEO)
        val ticksPos = o.obj("UserData")?.long("PlaybackPositionTicks") ?: 0L
        return MediaItem(
            key = "jf:${cfg.id}:$itemId",
            sourceType = SourceType.JELLYFIN,
            sourceId = cfg.id,
            kind = kind,
            title = o.str("Name") ?: "Без названия",
            subtitle = subtitle,
            overview = o.str("Overview"),
            year = o.int("ProductionYear"),
            poster = poster,
            backdrop = backdrop,
            rating = o.dbl("CommunityRating"),
            streamUrl = if (playable) "$base/Videos/$itemId/stream?static=true&api_key=${cfg.token}&DeviceId=$deviceId" else null,
            durationMs = o.long("RunTimeTicks")?.div(10_000),
            remoteId = itemId,
            serverPositionMs = (ticksPos / 10_000).takeIf { it > 0 },
            landscape = kind == MediaKind.EPISODE,
        ).also(MediaCache::put)
    }

    override suspend fun homeRows(): List<MediaRow> {
        ensureAuth()
        val uid = cfg.userId
        val rows = mutableListOf<MediaRow>()
        runCatching { callArray("/Users/$uid/Items/Resume?Limit=20&MediaTypes=Video&$fields").mapNotNull(::parse) }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let { rows += MediaRow("$name: продолжить", it, landscape = true) }
        runCatching { callArray("/Shows/NextUp?UserId=$uid&Limit=20&$fields").mapNotNull(::parse) }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let { rows += MediaRow("$name: следующие серии", it, landscape = true) }
        runCatching { callArray("/Users/$uid/Items/Latest?Limit=30&$fields").mapNotNull(::parse) }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let { rows += MediaRow("$name: новое", it) }
        return rows
    }

    override suspend fun browse(parent: MediaItem?): List<MediaItem> {
        ensureAuth()
        val uid = cfg.userId
        if (parent == null) return call("/Users/$uid/Views").arr("Items").mapNotNull(::parse)
            .map { it.copy(kind = MediaKind.FOLDER) }
        val sort = if (parent.kind == MediaKind.SHOW || parent.kind == MediaKind.SEASON)
            "SortBy=ParentIndexNumber,IndexNumber,SortName" else "SortBy=SortName"
        return call("/Users/$uid/Items?ParentId=${parent.remoteId}&$sort&Limit=1000&$fields")
            .arr("Items").mapNotNull(::parse)
    }

    override suspend fun search(query: String): List<MediaItem> {
        ensureAuth()
        return call(
            "/Users/${cfg.userId}/Items?searchTerm=${query.urlEncode()}&Recursive=true" +
                "&IncludeItemTypes=Movie,Series,Episode,Video&Limit=60&$fields",
        ).arr("Items").mapNotNull(::parse)
    }

    override suspend fun reportProgress(item: MediaItem, positionMs: Long, finished: Boolean) {
        val itemId = item.remoteId ?: return
        runCatching {
            if (finished) {
                Http.postJson("$base/Users/${cfg.userId}/PlayedItems/$itemId", "{}", headers())
            } else {
                val body = buildJsonObject {
                    put("ItemId", itemId)
                    put("PositionTicks", positionMs * 10_000)
                }.toString()
                Http.postJson("$base/Sessions/Playing/Stopped", body, headers())
            }
        }
    }

    override suspend fun test() {
        ensureAuth()
        call("/Users/${cfg.userId}/Views")
    }
}
