package com.cinehub.tv.data.sources

import com.cinehub.tv.data.Http
import com.cinehub.tv.data.db.SourceConfig
import com.cinehub.tv.model.MediaCache
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.model.MediaRow
import com.cinehub.tv.model.SourceType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class M3uEntry(
    val title: String,
    val url: String,
    val group: String?,
    val logo: String?,
    val headers: Map<String, String>,
)

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<M3uEntry> {
        val out = mutableListOf<M3uEntry>()
        var title: String? = null
        var group: String? = null
        var logo: String? = null
        val headers = mutableMapOf<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    group = attrs["group-title"]
                    logo = attrs["tvg-logo"]
                    // название — после последней запятой вне кавычек
                    title = line.substringAfterLast("\",", "").ifBlank { line.substringAfterLast(',') }.trim()
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substringAfter(':').trim()
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    val k = opt.substringBefore('=').lowercase()
                    val v = opt.substringAfter('=')
                    when (k) {
                        "http-user-agent" -> headers["User-Agent"] = v
                        "http-referrer", "http-referer" -> headers["Referer"] = v
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    out += M3uEntry(
                        title = title?.takeIf { it.isNotBlank() } ?: line.substringAfterLast('/').substringBefore('?'),
                        url = line,
                        group = group?.takeIf { it.isNotBlank() },
                        logo = logo?.takeIf { it.isNotBlank() },
                        headers = headers.toMap(),
                    )
                    title = null; logo = null; headers.clear()
                }
            }
        }
        return out
    }
}

class M3uSource(private val cfg: SourceConfig) : VideoSource {
    override val id get() = cfg.id
    override val name get() = cfg.name
    override val type = SourceType.M3U

    private val mutex = Mutex()
    private var cache: List<MediaItem>? = null

    private suspend fun load(force: Boolean = false): List<MediaItem> = mutex.withLock {
        cache?.takeIf { !force }?.let { return@withLock it }
        val entries = M3uParser.parse(Http.getText(cfg.url))
        val items = entries.mapIndexed { i, e ->
            val isLive = !isVideoFile(e.url.substringBefore('?')) || e.url.contains(".m3u8")
            MediaItem(
                key = "m3u:${cfg.id}:$i",
                sourceType = SourceType.M3U,
                sourceId = cfg.id,
                kind = if (isLive) MediaKind.CHANNEL else MediaKind.VIDEO,
                title = e.title,
                subtitle = e.group,
                poster = e.logo,
                streamUrl = e.url,
                headers = e.headers,
                remoteId = e.group ?: "",
                landscape = true,
            )
        }
        MediaCache.putAll(items)
        cache = items
        items
    }

    override suspend fun homeRows(): List<MediaRow> =
        listOf(MediaRow(name, load().take(40), landscape = true)).filter { it.items.isNotEmpty() }

    override suspend fun browse(parent: MediaItem?): List<MediaItem> {
        val all = load(force = parent == null)
        val groups = all.mapNotNull { it.subtitle }.distinct()
        if (parent == null) {
            if (groups.size <= 1) return all
            val folders = groups.map { g ->
                MediaItem(
                    key = "m3u:${cfg.id}:group:$g",
                    sourceType = SourceType.M3U,
                    sourceId = cfg.id,
                    kind = MediaKind.FOLDER,
                    title = g,
                    subtitle = "${all.count { it.subtitle == g }} шт.",
                    remoteId = "group:$g",
                ).also(MediaCache::put)
            }
            return folders + all.filter { it.subtitle == null }
        }
        val g = parent.remoteId?.removePrefix("group:")
        return all.filter { it.subtitle == g }
    }

    override suspend fun search(query: String): List<MediaItem> {
        val q = normalizeTitle(query)
        return load().filter { normalizeTitle(it.title).contains(q) }.take(60)
    }
}
