package com.cinehub.tv.data.sources

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import com.cinehub.tv.model.MediaCache
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.model.MediaRow
import com.cinehub.tv.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Видео на самом устройстве и подключённых флешках (через MediaStore). */
class LocalSource(private val context: Context) : VideoSource {
    override val id = LOCAL_ID
    override val name = "Это устройство"
    override val type = SourceType.LOCAL

    private data class Row(val item: MediaItem, val bucket: String)

    fun hasPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun loadAll(): List<Row> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val out = mutableListOf<Row>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
        )
        context.contentResolver.query(
            MediaStore.Video.Media.getContentUri("external"),
            projection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val vid = c.getLong(iId)
                val fileName = c.getString(iName) ?: "video"
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.getContentUri("external"), vid)
                val (title, year) = cleanFileTitle(fileName)
                val bucket = c.getString(iBucket) ?: "Видео"
                out += Row(
                    MediaItem(
                        key = "local:$vid",
                        sourceType = SourceType.LOCAL,
                        sourceId = LOCAL_ID,
                        kind = MediaKind.VIDEO,
                        title = title,
                        subtitle = bucket,
                        year = year,
                        poster = uri.toString(),
                        streamUrl = uri.toString(),
                        durationMs = c.getLong(iDur).takeIf { it > 0 },
                        remoteId = bucket,
                        landscape = true,
                    ),
                    bucket,
                )
            }
        }
        MediaCache.putAll(out.map { it.item })
        out
    }

    override suspend fun homeRows(): List<MediaRow> {
        val items = loadAll().take(30).map { it.item }
        return if (items.isEmpty()) emptyList() else listOf(MediaRow("На этом устройстве", items, landscape = true))
    }

    override suspend fun browse(parent: MediaItem?): List<MediaItem> {
        val rows = loadAll()
        if (parent == null) {
            return rows.groupBy { it.bucket }.map { (bucket, list) ->
                MediaItem(
                    key = "local:folder:$bucket",
                    sourceType = SourceType.LOCAL,
                    sourceId = LOCAL_ID,
                    kind = MediaKind.FOLDER,
                    title = bucket,
                    subtitle = "${list.size} видео",
                    poster = list.first().item.poster,
                    remoteId = bucket,
                    landscape = true,
                ).also(MediaCache::put)
            }
        }
        return rows.filter { it.bucket == parent.remoteId }.map { it.item }
    }

    override suspend fun search(query: String): List<MediaItem> {
        val q = normalizeTitle(query)
        return loadAll().map { it.item }.filter { normalizeTitle(it.title + " " + it.subtitle).contains(q) }.take(40)
    }

    override suspend fun test() {}

    companion object { const val LOCAL_ID = -1L }
}
