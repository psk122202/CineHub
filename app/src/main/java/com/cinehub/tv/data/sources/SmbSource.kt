package com.cinehub.tv.data.sources

import com.cinehub.tv.data.db.SourceConfig
import com.cinehub.tv.model.MediaCache
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.model.SourceType
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/** Хранит контексты авторизации SMB по префиксу адреса, чтобы плеер мог открыть файл. */
object SmbRegistry {
    private val contexts = ConcurrentHashMap<String, CIFSContext>()

    fun register(prefix: String, ctx: CIFSContext) { contexts[prefix.lowercase()] = ctx }

    fun contextFor(url: String): CIFSContext {
        val u = url.lowercase()
        return contexts.entries.filter { u.startsWith(it.key) }.maxByOrNull { it.key.length }?.value
            ?: anonymous
    }

    private val baseContext: BaseContext by lazy {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB1")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.resolveOrder", "DNS,BCAST")
        }
        BaseContext(PropertyConfiguration(props))
    }

    val anonymous: CIFSContext by lazy { baseContext.withAnonymousCredentials() }

    fun create(user: String, password: String): CIFSContext {
        if (user.isBlank()) return baseContext.withCredentials(NtlmPasswordAuthenticator("", "guest", ""))
        val domain = if ('\\' in user) user.substringBefore('\\') else ""
        val name = user.substringAfter('\\')
        return baseContext.withCredentials(NtlmPasswordAuthenticator(domain, name, password))
    }
}

/** Сетевая папка Windows / NAS. url вида smb://192.168.1.10/Movies/ */
class SmbSource(private val cfg: SourceConfig) : VideoSource {
    override val id get() = cfg.id
    override val name get() = cfg.name
    override val type = SourceType.SMB

    private val root: String = cfg.url.trim().let {
        val withScheme = if (it.startsWith("smb://", ignoreCase = true)) it else "smb://" + it.trimStart('/', '\\').replace('\\', '/')
        if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
    private val ctx: CIFSContext = SmbRegistry.create(cfg.username, cfg.password).also { SmbRegistry.register(root, it) }

    override suspend fun browse(parent: MediaItem?): List<MediaItem> = withContext(Dispatchers.IO) {
        val dirUrl = parent?.remoteId ?: root
        val files = SmbFile(dirUrl, ctx).listFiles()
            .filter { !it.isHidden && !it.name.startsWith(".") }
        val dirs = files.filter { it.isDirectory }.sortedBy { it.name.lowercase() }.map { f ->
            MediaItem(
                key = "smb:${cfg.id}:${f.url}",
                sourceType = SourceType.SMB,
                sourceId = cfg.id,
                kind = MediaKind.FOLDER,
                title = f.name.trimEnd('/'),
                remoteId = f.url.toString(),
            )
        }
        val videos = files.filter { !it.isDirectory && isVideoFile(it.name) }.sortedBy { it.name.lowercase() }.map { f ->
            val (title, year) = cleanFileTitle(f.name)
            MediaItem(
                key = "smb:${cfg.id}:${f.url}",
                sourceType = SourceType.SMB,
                sourceId = cfg.id,
                kind = MediaKind.VIDEO,
                title = title,
                subtitle = f.name,
                year = year,
                streamUrl = f.url.toString(),
                remoteId = f.url.toString(),
                landscape = true,
            )
        }
        (dirs + videos).also(MediaCache::putAll)
    }

    /** Поиск по имени файла — рекурсивно, но с ограничением глубины. */
    override suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val q = normalizeTitle(query)
        val out = mutableListOf<MediaItem>()
        fun walk(url: String, depth: Int) {
            if (depth > 3 || out.size >= 40) return
            val list = runCatching { SmbFile(url, ctx).listFiles() }.getOrNull() ?: return
            for (f in list) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) walk(f.url.toString(), depth + 1)
                else if (isVideoFile(f.name) && normalizeTitle(f.name).contains(q)) {
                    val (title, year) = cleanFileTitle(f.name)
                    out += MediaItem(
                        key = "smb:${cfg.id}:${f.url}",
                        sourceType = SourceType.SMB,
                        sourceId = cfg.id,
                        kind = MediaKind.VIDEO,
                        title = title,
                        subtitle = f.name,
                        year = year,
                        streamUrl = f.url.toString(),
                        remoteId = f.url.toString(),
                        landscape = true,
                    ).also(MediaCache::put)
                }
            }
        }
        walk(root, 0)
        out
    }
}
