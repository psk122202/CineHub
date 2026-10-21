package com.cinehub.tv.data

import android.content.Context
import com.cinehub.tv.data.db.AppDatabase
import com.cinehub.tv.data.db.LibraryEntry
import com.cinehub.tv.data.db.Profile
import com.cinehub.tv.data.db.SourceConfig
import com.cinehub.tv.data.db.UserLink
import com.cinehub.tv.data.db.WatchProgress
import com.cinehub.tv.data.sources.JellyfinSource
import com.cinehub.tv.data.sources.LinksSource
import com.cinehub.tv.data.sources.LocalSource
import com.cinehub.tv.data.sources.M3uSource
import com.cinehub.tv.data.sources.PlexSource
import com.cinehub.tv.data.sources.SmbSource
import com.cinehub.tv.data.sources.VideoSource
import com.cinehub.tv.data.sources.normalizeTitle
import com.cinehub.tv.data.sources.sourceType
import com.cinehub.tv.data.tmdb.TmdbClient
import com.cinehub.tv.model.MediaCache
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.model.MediaRow
import com.cinehub.tv.model.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class SearchGroup(val title: String, val items: List<MediaItem>)

/** Всё приложение в одном месте: БД, настройки, TMDB и источники. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.create(context)
    val settings = SettingsStore(context)
    val tmdb = TmdbClient { settings.current() }

    val local = LocalSource(context)
    val links = LinksSource(db.links())

    val settingsFlow: StateFlow<AppSettings?> = settings.flow.stateIn(scope, SharingStarted.Eagerly, null)
    val profileId: Flow<Long> = settings.flow.map { it.profileId }.distinctUntilChanged()

    // ---------- Профили ----------

    val profiles: Flow<List<Profile>> = db.profiles().observeAll()

    suspend fun ensureProfile(): Long {
        val all = db.profiles().all()
        val current = settings.current().profileId
        if (all.any { it.id == current }) return current
        val id = all.firstOrNull()?.id ?: db.profiles().insert(Profile(name = "Я", color = 0xFFE5A00D))
        settings.setProfile(id)
        return id
    }

    suspend fun addProfile(name: String, color: Long): Long = db.profiles().insert(Profile(name = name, color = color))

    suspend fun deleteProfile(id: Long) {
        db.profiles().delete(id)
        db.library().deleteProfile(id)
        db.progress().clear(id)
        ensureProfile()
    }

    suspend fun selectProfile(id: Long) = settings.setProfile(id)

    private suspend fun pid() = settings.current().profileId

    // ---------- Избранное / Буду смотреть ----------

    fun observeList(list: String): Flow<List<MediaItem>> = profileId.flatMapLatest { p ->
        db.library().observe(p, list).map { rows -> rows.mapNotNull { decode(it.itemJson) } }
    }

    fun observeInList(list: String, key: String): Flow<Boolean> = profileId.flatMapLatest { p ->
        db.library().observeContains(p, list, key).map { it > 0 }
    }

    suspend fun toggleList(list: String, item: MediaItem, add: Boolean) {
        val p = pid()
        if (add) db.library().upsert(LibraryEntry(p, list, item.key, encode(item), System.currentTimeMillis()))
        else db.library().delete(p, list, item.key)
    }

    // ---------- Прогресс ----------

    fun observeContinue(): Flow<List<Pair<MediaItem, WatchProgress>>> = profileId.flatMapLatest { p ->
        db.progress().observeContinue(p).map { rows -> rows.mapNotNull { r -> decode(r.itemJson)?.let { it to r } } }
    }

    fun observeHistory(): Flow<List<Pair<MediaItem, WatchProgress>>> = profileId.flatMapLatest { p ->
        db.progress().observeHistory(p).map { rows -> rows.mapNotNull { r -> decode(r.itemJson)?.let { it to r } } }
    }

    fun observeProgress(key: String): Flow<WatchProgress?> = profileId.flatMapLatest { p -> db.progress().observe(p, key) }

    suspend fun getProgress(key: String): WatchProgress? = db.progress().get(pid(), key)

    suspend fun saveProgress(item: MediaItem, positionMs: Long, durationMs: Long) {
        if (item.kind == MediaKind.CHANNEL) return
        val finished = durationMs > 0 && positionMs >= durationMs * 0.92
        db.progress().upsert(
            WatchProgress(
                profileId = pid(),
                key = item.key,
                itemJson = encode(item),
                positionMs = if (finished) 0 else positionMs,
                durationMs = durationMs,
                finished = finished,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeProgress(key: String) = db.progress().delete(pid(), key)
    suspend fun clearHistory() = db.progress().clear(pid())

    // ---------- Источники ----------

    val sourceConfigs: Flow<List<SourceConfig>> = db.sources().observeAll()
    private val sourceCache = mutableMapOf<Long, Pair<SourceConfig, VideoSource>>()
    private val sourceMutex = Mutex()

    private suspend fun build(cfg: SourceConfig, persistAuth: Boolean = true): VideoSource {
        val deviceId = settings.current().deviceId
        return when (cfg.sourceType()) {
            SourceType.JELLYFIN -> JellyfinSource(cfg, deviceId) { updated ->
                if (persistAuth && updated.id > 0) db.sources().upsert(updated)
            }
            SourceType.PLEX -> PlexSource(cfg, deviceId)
            SourceType.M3U -> M3uSource(cfg)
            SourceType.SMB -> SmbSource(cfg)
            else -> M3uSource(cfg)
        }
    }

    /** Все активные источники, включая встроенные (устройство и ссылки). */
    suspend fun sources(): List<VideoSource> = sourceMutex.withLock {
        val configs = db.sources().all().filter { it.enabled }
        val list = configs.map { cfg ->
            val cached = sourceCache[cfg.id]
            // Jellyfin сам обновляет токен в БД — не пересоздаём из-за этого
            if (cached != null && cached.first.copy(token = "", userId = "") == cfg.copy(token = "", userId = "")) cached.second
            else build(cfg).also { sourceCache[cfg.id] = cfg to it }
        }
        list + links + local
    }

    suspend fun source(id: Long): VideoSource? = sources().firstOrNull { it.id == id }

    suspend fun saveSource(cfg: SourceConfig): Long {
        val id = db.sources().upsert(cfg)
        sourceMutex.withLock { sourceCache.remove(id) }
        return id
    }

    suspend fun deleteSource(id: Long) {
        db.sources().delete(id)
        sourceMutex.withLock { sourceCache.remove(id) }
    }

    suspend fun testSource(cfg: SourceConfig) {
        build(cfg, persistAuth = false).test()
    }

    suspend fun addLink(title: String, url: String) = db.links().insert(UserLink(title = title, url = url))
    suspend fun deleteLink(id: Long) = db.links().delete(id)
    val linksFlow = db.links().observeAll()

    // ---------- Главная и поиск ----------

    suspend fun sourceRows(): List<MediaRow> = coroutineScope {
        sources().map { s ->
            async { withTimeoutOrNull(15_000) { runCatching { s.homeRows() }.getOrNull() } ?: emptyList() }
        }.awaitAll().flatten()
    }

    suspend fun searchAll(query: String): List<SearchGroup> = coroutineScope {
        val tmdbJob = async {
            runCatching { tmdb.search(query) }.getOrNull()?.let { SearchGroup("Каталог TMDB", it) }
        }
        val srcJobs = sources().map { s ->
            async {
                withTimeoutOrNull(15_000) { runCatching { s.search(query) }.getOrNull() }
                    ?.takeIf { it.isNotEmpty() }?.let { SearchGroup(s.name, it) }
            }
        }
        (listOf(tmdbJob) + srcJobs).awaitAll().filterNotNull().filter { it.items.isNotEmpty() }
    }

    /** Ищет фильм/сериал из TMDB во всех подключённых источниках. */
    suspend fun findInSources(title: String, originalTitle: String?, year: Int?): List<MediaItem> = coroutineScope {
        val queries = listOfNotNull(title, originalTitle?.takeIf { it != title })
        val names = queries.map(::normalizeTitle).toSet()
        val found = sources().flatMap { s ->
            queries.map { q -> async { withTimeoutOrNull(15_000) { runCatching { s.search(q) }.getOrNull() } ?: emptyList() } }
        }.awaitAll().flatten().distinctBy { it.key }
        found.sortedByDescending { item ->
            val n = normalizeTitle(item.title)
            var score = 0
            if (n in names) score += 10
            if (names.any { n.startsWith(it) }) score += 3
            if (year != null && item.year == year) score += 5
            if (item.kind == MediaKind.MOVIE || item.kind == MediaKind.SHOW) score += 2
            score
        }
    }

    /** Найти элемент по ключу: кэш → избранное/история. */
    suspend fun resolve(key: String): MediaItem? {
        MediaCache.get(key)?.let { return it }
        val p = pid()
        db.progress().get(p, key)?.let { decode(it.itemJson) }?.let { MediaCache.put(it); return it }
        return null
    }

    fun encode(item: MediaItem): String = AppJson.encodeToString(MediaItem.serializer(), item)
    fun decode(json: String): MediaItem? = runCatching { AppJson.decodeFromString(MediaItem.serializer(), json) }
        .getOrNull()?.also(MediaCache::put)
}
