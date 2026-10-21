package com.cinehub.tv.model

import kotlinx.serialization.Serializable

/** Тип источника, из которого пришёл элемент. */
@Serializable
enum class SourceType(val title: String) {
    TMDB("Каталог TMDB"),
    JELLYFIN("Jellyfin / Emby"),
    PLEX("Plex"),
    M3U("Плейлист M3U"),
    SMB("Сетевая папка (SMB)"),
    LOCAL("Это устройство"),
    LINKS("Мои ссылки"),
}

@Serializable
enum class MediaKind { MOVIE, SHOW, SEASON, EPISODE, FOLDER, VIDEO, CHANNEL }

/**
 * Единая модель фильма/сериала/папки/видео для всех источников.
 * key — глобально уникальный ключ, например "tmdb:movie:550", "jf:3:abc", "plex:4:1234".
 */
@Serializable
data class MediaItem(
    val key: String,
    val sourceType: SourceType,
    val sourceId: Long = 0,
    val kind: MediaKind,
    val title: String,
    val subtitle: String? = null,
    val overview: String? = null,
    val year: Int? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val rating: Double? = null,
    val streamUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val durationMs: Long? = null,
    /** Идентификатор в источнике (id Jellyfin, ratingKey Plex, путь SMB, id TMDB). */
    val remoteId: String? = null,
    /** Позиция просмотра, которую сообщил сам сервер (Jellyfin/Plex). */
    val serverPositionMs: Long? = null,
    val landscape: Boolean = false,
) {
    val isPlayable: Boolean
        get() = streamUrl != null && kind in setOf(MediaKind.MOVIE, MediaKind.EPISODE, MediaKind.VIDEO, MediaKind.CHANNEL)

    val isContainer: Boolean
        get() = kind in setOf(MediaKind.FOLDER, MediaKind.SHOW, MediaKind.SEASON)

    val isTmdb: Boolean get() = sourceType == SourceType.TMDB
}

data class MediaRow(
    val title: String,
    val items: List<MediaItem>,
    val landscape: Boolean = false,
)

/** Кэш элементов в памяти: экраны передают друг другу только key. */
object MediaCache {
    private val map = java.util.concurrent.ConcurrentHashMap<String, MediaItem>()
    fun put(item: MediaItem) { map[item.key] = item }
    fun putAll(items: Collection<MediaItem>) = items.forEach(::put)
    fun get(key: String): MediaItem? = map[key]
}
