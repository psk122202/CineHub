package com.cinehub.tv.data.tmdb

import com.cinehub.tv.data.AppSettings
import com.cinehub.tv.data.Http
import com.cinehub.tv.data.arr
import com.cinehub.tv.data.dbl
import com.cinehub.tv.data.int
import com.cinehub.tv.data.obj
import com.cinehub.tv.data.str
import com.cinehub.tv.data.urlEncode
import com.cinehub.tv.model.MediaCache
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.model.MediaRow
import com.cinehub.tv.model.SourceType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class Provider(val id: Int, val name: String, val logo: String?, val kind: String)
data class CastMember(val name: String, val character: String?, val photo: String?)
data class SeasonInfo(val number: Int, val name: String, val episodes: Int, val poster: String?, val year: Int?)
data class Episode(
    val number: Int,
    val season: Int,
    val name: String,
    val overview: String?,
    val still: String?,
    val airDate: String?,
    val runtime: Int?,
)

data class TmdbDetails(
    val item: MediaItem,
    val originalTitle: String?,
    val genres: List<String>,
    val runtimeMin: Int?,
    val tagline: String?,
    val seasons: List<SeasonInfo>,
    val cast: List<CastMember>,
    val providers: List<Provider>,
    val providersLink: String?,
    val trailerYoutubeKey: String?,
    val recommendations: List<MediaItem>,
)

class TmdbMissingKey : Exception("Не указан ключ TMDB. Откройте Настройки и вставьте API-ключ.")

class TmdbClient(private val settingsProvider: suspend () -> AppSettings) {

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val s = settingsProvider()
        val key = s.tmdbKey
        if (key.isBlank()) throw TmdbMissingKey()
        val isBearer = key.length > 40 // токен v4 длинный, ключ v3 — 32 символа
        val q = buildMap {
            put("language", s.language)
            putAll(params)
            if (!isBearer) put("api_key", key)
        }.entries.joinToString("&") { "${it.key}=${it.value.urlEncode()}" }
        val headers = if (isBearer) mapOf("Authorization" to "Bearer $key") else emptyMap()
        return Http.getJson("$BASE$path?$q", headers)
    }

    private fun parseItem(o: JsonObject, forcedType: String? = null): MediaItem? {
        val type = forcedType ?: o.str("media_type") ?: return null
        if (type != "movie" && type != "tv") return null
        val id = o.int("id") ?: return null
        val title = o.str("title") ?: o.str("name") ?: return null
        val date = o.str("release_date") ?: o.str("first_air_date")
        return MediaItem(
            key = "tmdb:$type:$id",
            sourceType = SourceType.TMDB,
            kind = if (type == "movie") MediaKind.MOVIE else MediaKind.SHOW,
            title = title,
            overview = o.str("overview"),
            year = date?.take(4)?.toIntOrNull(),
            poster = o.str("poster_path")?.let { "$IMG/w342$it" },
            backdrop = o.str("backdrop_path")?.let { "$IMG/w1280$it" },
            rating = o.dbl("vote_average")?.takeIf { it > 0 },
            remoteId = id.toString(),
        ).also(MediaCache::put)
    }

    private suspend fun list(path: String, type: String?, params: Map<String, String> = emptyMap()) =
        get(path, params).arr("results").mapNotNull { parseItem(it, type) }

    suspend fun homeRows(): List<MediaRow> = coroutineScope {
        val region = settingsProvider().region
        val jobs = listOf(
            "В тренде: фильмы" to async { runCatching { list("/trending/movie/week", "movie") }.getOrDefault(emptyList()) },
            "В тренде: сериалы" to async { runCatching { list("/trending/tv/week", "tv") }.getOrDefault(emptyList()) },
            "Сейчас в кино" to async { runCatching { list("/movie/now_playing", "movie", mapOf("region" to region)) }.getOrDefault(emptyList()) },
            "Популярные фильмы" to async { runCatching { list("/movie/popular", "movie") }.getOrDefault(emptyList()) },
            "Популярные сериалы" to async { runCatching { list("/tv/popular", "tv") }.getOrDefault(emptyList()) },
            "Лучшие фильмы" to async { runCatching { list("/movie/top_rated", "movie") }.getOrDefault(emptyList()) },
            "Лучшие сериалы" to async { runCatching { list("/tv/top_rated", "tv") }.getOrDefault(emptyList()) },
        )
        jobs.map { (title, d) -> MediaRow(title, d.await()) }.filter { it.items.isNotEmpty() }
    }

    /** Проверка ключа: бросает исключение, если что-то не так. */
    suspend fun ping() { get("/configuration") }

    suspend fun search(query: String): List<MediaItem> =
        list("/search/multi", null, mapOf("query" to query, "include_adult" to "false"))

    suspend fun genres(type: String): List<Pair<Int, String>> =
        get("/genre/$type/list").arr("genres").mapNotNull { g ->
            val id = g.int("id") ?: return@mapNotNull null
            id to (g.str("name") ?: return@mapNotNull null)
        }

    suspend fun discover(type: String, genreId: Int, page: Int = 1): List<MediaItem> =
        list("/discover/$type", type, mapOf("with_genres" to genreId.toString(), "sort_by" to "popularity.desc", "page" to page.toString()))

    suspend fun details(type: String, id: String): TmdbDetails {
        val s = settingsProvider()
        val o = get(
            "/$type/$id",
            mapOf(
                "append_to_response" to "videos,credits,recommendations,watch/providers,external_ids",
                "include_video_language" to "${s.language.take(2)},en,null",
            ),
        )
        val item = parseItem(o, type)!!
        val genres = o.arr("genres").mapNotNull { it.str("name") }
        val runtime = o.int("runtime")
            ?: (o["episode_run_time"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.intOrNull
        val seasons = o.arr("seasons").mapNotNull { se ->
            val n = se.int("season_number") ?: return@mapNotNull null
            SeasonInfo(
                number = n,
                name = se.str("name") ?: "Сезон $n",
                episodes = se.int("episode_count") ?: 0,
                poster = se.str("poster_path")?.let { "$IMG/w342$it" },
                year = se.str("air_date")?.take(4)?.toIntOrNull(),
            )
        }.sortedBy { if (it.number == 0) Int.MAX_VALUE else it.number }
        val cast = o.obj("credits")?.arr("cast")?.take(20)?.mapNotNull { c ->
            CastMember(
                name = c.str("name") ?: return@mapNotNull null,
                character = c.str("character"),
                photo = c.str("profile_path")?.let { "$IMG/w185$it" },
            )
        } ?: emptyList()

        val regionBlock = o.obj("watch/providers")?.obj("results")?.obj(s.region)
        val providers = buildList<Provider> {
            listOf("flatrate" to "Подписка", "free" to "Бесплатно", "ads" to "С рекламой", "rent" to "Аренда", "buy" to "Покупка")
                .forEach { (k, label) ->
                    regionBlock?.arr(k)?.forEach { p ->
                        val pid = p.int("provider_id") ?: return@forEach
                        if (none { it.id == pid }) add(
                            Provider(pid, p.str("provider_name") ?: "?", p.str("logo_path")?.let { "$IMG/w154$it" }, label),
                        )
                    }
                }
        }
        val videos = o.obj("videos")?.arr("results").orEmpty().filter { it.str("site") == "YouTube" }
        val trailer = (videos.firstOrNull { it.str("type") == "Trailer" } ?: videos.firstOrNull())?.str("key")
        val recs = o.obj("recommendations")?.arr("results").orEmpty().mapNotNull { parseItem(it, type) }

        return TmdbDetails(
            item = item,
            originalTitle = o.str("original_title") ?: o.str("original_name"),
            genres = genres,
            runtimeMin = runtime,
            tagline = o.str("tagline"),
            seasons = seasons,
            cast = cast,
            providers = providers,
            providersLink = regionBlock?.str("link"),
            trailerYoutubeKey = trailer,
            recommendations = recs,
        )
    }

    suspend fun season(tvId: String, number: Int): List<Episode> =
        get("/tv/$tvId/season/$number").arr("episodes").mapNotNull { e ->
            Episode(
                number = e.int("episode_number") ?: return@mapNotNull null,
                season = number,
                name = e.str("name") ?: "Серия",
                overview = e.str("overview"),
                still = e.str("still_path")?.let { "$IMG/w300$it" },
                airDate = e.str("air_date"),
                runtime = e.int("runtime"),
            )
        }

    companion object {
        const val BASE = "https://api.themoviedb.org/3"
        const val IMG = "https://image.tmdb.org/t/p"
    }
}
