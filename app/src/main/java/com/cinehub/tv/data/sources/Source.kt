package com.cinehub.tv.data.sources

import com.cinehub.tv.data.db.SourceConfig
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaRow
import com.cinehub.tv.model.SourceType

/** Общий интерфейс для всех источников видео. */
interface VideoSource {
    val id: Long
    val name: String
    val type: SourceType

    /** Ряды для главного экрана (новинки, продолжить и т.п.). */
    suspend fun homeRows(): List<MediaRow> = emptyList()

    /** Содержимое папки/сериала/сезона; parent == null — корень источника. */
    suspend fun browse(parent: MediaItem?): List<MediaItem>

    suspend fun search(query: String): List<MediaItem> = emptyList()

    /** Проверка подключения. Бросает исключение с понятным текстом. */
    suspend fun test() { browse(null) }

    /** Отчёт о просмотре на сервер (если поддерживается). */
    suspend fun reportProgress(item: MediaItem, positionMs: Long, finished: Boolean) {}
}

fun SourceConfig.sourceType(): SourceType = runCatching { SourceType.valueOf(type) }.getOrDefault(SourceType.LINKS)

val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "m4v", "webm", "ts", "m2ts", "mts", "wmv", "flv", "3gp", "mpg", "mpeg", "vob", "m3u8", "mpd",
)

fun isVideoFile(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

private val yearRegex = Regex("""[(\[. ](19\d{2}|20\d{2})[)\]. ]""")

/** «Matrix.1999.1080p.BluRay.mkv» → «Matrix», 1999 */
fun cleanFileTitle(fileName: String): Pair<String, Int?> {
    var base = fileName.substringBeforeLast('.')
    val year = yearRegex.find(" $base ")?.groupValues?.get(1)?.toIntOrNull()
    val cut = listOf(
        Regex("""[(\[. ](19|20)\d{2}[)\]. ].*"""),
        Regex("""(?i)[. ](720p|1080p|2160p|4k|bdrip|webrip|web-dl|hdrip|bluray|x264|x265|hevc).*"""),
    )
    cut.forEach { r -> base = (" $base ").replace(r, "").trim().ifBlank { base } }
    base = base.replace('.', ' ').replace('_', ' ').replace(Regex("\\s+"), " ").trim()
    return base to year
}

/** Нормализация названия для сравнения TMDB ↔ источники. */
fun normalizeTitle(s: String): String =
    s.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
