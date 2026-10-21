package com.cinehub.tv.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.cinehub.tv.data.Http
import com.cinehub.tv.data.sources.SmbRegistry
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Заголовки (User-Agent, Referer) для конкретных ссылок из плейлистов. */
object StreamHeaders {
    // по хосту — чтобы заголовки применялись и к сегментам HLS
    private val byHost = ConcurrentHashMap<String, Map<String, String>>()
    private fun host(url: String) = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
    fun put(url: String, headers: Map<String, String>) { if (headers.isNotEmpty()) byHost[host(url)] = headers }
    fun get(url: String): Map<String, String> = byHost[host(url)] ?: emptyMap()
}

/** Чтение файлов по SMB для ExoPlayer с поддержкой перемотки. */
class SmbDataSource : BaseDataSource(/* isNetwork = */ true) {
    private var file: SmbRandomAccessFile? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        try {
            val url = dataSpec.uri.toString()
            val smbFile = SmbFile(url, SmbRegistry.contextFor(url))
            val raf = SmbRandomAccessFile(smbFile, "r")
            val length = raf.length()
            raf.seek(dataSpec.position)
            file = raf
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length
            else length - dataSpec.position
            if (bytesRemaining < 0) throw IOException("Неверная позиция")
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = minOf(bytesRemaining, length.toLong()).toInt()
        val read = file?.read(buffer, offset, toRead) ?: -1
        if (read < 0) return C.RESULT_END_OF_INPUT
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try { file?.close() } catch (_: Exception) {}
        file = null
        if (opened) { opened = false; transferEnded() }
    }

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource()
    }
}

/** Выбирает SMB или обычный (http/file/content) источник по схеме ссылки. */
class RoutingDataSource(
    private val default: DataSource,
    private val smb: DataSource,
) : DataSource {
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        default.addTransferListener(transferListener)
        smb.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val ds = if (dataSpec.uri.scheme.equals("smb", ignoreCase = true)) smb else default
        current = ds
        return ds.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        current?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        current?.close()
        current = null
    }

    class Factory(private val default: DataSource.Factory, private val smb: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = RoutingDataSource(default.createDataSource(), smb.createDataSource())
    }
}

fun buildDataSourceFactory(context: Context): DataSource.Factory {
    val http = OkHttpDataSource.Factory(Http.client)
        .setUserAgent("Mozilla/5.0 (Linux; Android 12; Android TV) CineHub/1.0")
    // Подставляем заголовки, заданные в плейлисте для конкретной ссылки
    val withHeaders = ResolvingDataSource.Factory(http) { spec ->
        val h = StreamHeaders.get(spec.uri.toString())
        if (h.isEmpty()) spec else spec.withAdditionalHeaders(h)
    }
    val default = DefaultDataSource.Factory(context, withHeaders)
    return RoutingDataSource.Factory(default, SmbDataSource.Factory())
}
