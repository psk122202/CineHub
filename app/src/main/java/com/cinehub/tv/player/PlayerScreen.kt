package com.cinehub.tv.player

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.ui.LocalApp
import com.cinehub.tv.ui.LocalNav
import com.cinehub.tv.ui.Palette
import com.cinehub.tv.ui.TvButton
import com.cinehub.tv.ui.formatTime
import com.cinehub.tv.ui.tryFocus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem as ExoItem

@Composable
fun PlayerScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val ctx = LocalContext.current

    val queue = remember { PlayQueue.items }
    val startIndex = remember { PlayQueue.startIndex }
    val fromStart = remember { PlayQueue.fromStart }

    var error by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf(queue.getOrNull(startIndex)) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val retryFocus = remember { FocusRequester() }

    if (queue.isEmpty()) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }

    val player = remember {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(buildDataSourceFactory(ctx)))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    /** Сохраняет позицию текущего элемента в БД. */
    suspend fun saveCurrent(item: MediaItem?, report: Boolean) {
        item ?: return
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (item.durationMs ?: 0L)
        if (pos < 5_000 && dur > 0) return
        app.saveProgress(item, pos, dur)
        if (report) {
            val finished = dur > 0 && pos >= dur * 0.92
            runCatching { app.source(item.sourceId)?.reportProgress(item, pos, finished) }
        }
    }

    // Подготовка плейлиста и продолжение с места остановки
    LaunchedEffect(Unit) {
        // Язык дорожек — как в настройках описаний
        val lang = app.settings.current().language.take(2)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(lang, "ru", "uk", "en")
            .setPreferredTextLanguages(lang, "ru", "uk", "en")
            .build()

        queue.forEach { StreamHeaders.put(it.streamUrl ?: "", it.headers) }
        val exoItems = queue.map { it.toExoItem() }
        val startItem = queue[startIndex]
        val resumeMs = if (fromStart || startItem.kind == MediaKind.CHANNEL) 0L else {
            val local = app.getProgress(startItem.key)?.takeIf { !it.finished }?.positionMs ?: 0L
            maxOf(local, startItem.serverPositionMs ?: 0L)
        }
        player.setMediaItems(exoItems, startIndex, resumeMs)
        player.prepare()
        player.playWhenReady = true
        if (resumeMs > 30_000) {
            Toast.makeText(ctx, "Продолжение с ${formatTime(resumeMs)}", Toast.LENGTH_SHORT).show()
        }
    }

    // Слушатель: смена серии, ошибки
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: ExoItem?, reason: Int) {
                val prev = current
                val next = queue.getOrNull(player.currentMediaItemIndex)
                if (prev != null && prev.key != next?.key) {
                    // предыдущую серию досмотрели — помечаем
                    nav.scope.launch {
                        val dur = prev.durationMs ?: 0L
                        if (dur > 0) app.saveProgress(prev, dur, dur)
                        runCatching { app.source(prev.sourceId)?.reportProgress(prev, dur, true) }
                    }
                }
                current = next
                error = null
            }

            override fun onPlayerError(e: PlaybackException) {
                error = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Нет соединения с источником"
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Сервер отказал в доступе (HTTP-ошибка)"
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Файл не найден"
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Телевизор не поддерживает формат этого видео"
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Неподдерживаемый формат файла"
                    else -> "Ошибка воспроизведения: ${e.errorCodeName}"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            val item = current
            val pos = player.currentPosition
            val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (item?.durationMs ?: 0L)
            player.release()
            if (item != null && (pos >= 5_000 || dur <= 0)) {
                // сохраняем вне жизненного цикла экрана
                nav.scope.launch {
                    withContext(NonCancellable) {
                        if (pos > 0) app.saveProgress(item, pos, dur)
                        val finished = dur > 0 && pos >= dur * 0.92
                        runCatching { app.source(item.sourceId)?.reportProgress(item, pos, finished) }
                    }
                }
            }
        }
    }

    // Периодическое сохранение позиции
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            if (player.isPlaying) saveCurrent(current, report = false)
        }
    }

    BackHandler {
        val v = playerView
        if (v != null && v.isControllerFullyVisible) v.hideController() else nav.back()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShowNextButton(queue.size > 1)
                    setShowPreviousButton(queue.size > 1)
                    setShowSubtitleButton(true)
                    controllerShowTimeoutMs = 4_000
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    post { requestFocus() }
                    playerView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        error?.let { msg ->
            LaunchedEffect(msg) { delay(100); retryFocus.tryFocus() }
            Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(current?.title ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(msg, fontSize = 17.sp, color = Palette.Error, modifier = Modifier.width(700.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton("Повторить", {
                            error = null
                            player.prepare()
                            player.playWhenReady = true
                        }, primary = true, modifier = Modifier.focusRequester(retryFocus))
                        if (queue.size > 1 && player.hasNextMediaItem()) {
                            TvButton("Следующее", { error = null; player.seekToNextMediaItem(); player.prepare() })
                        }
                        TvButton("Назад", { nav.back() })
                    }
                }
            }
        }
    }
}

private fun MediaItem.toExoItem(): ExoItem {
    val url = streamUrl ?: ""
    val lower = url.lowercase()
    val mime = when {
        lower.contains(".m3u8") || lower.contains("format=m3u8") -> MimeTypes.APPLICATION_M3U8
        lower.contains(".mpd") -> MimeTypes.APPLICATION_MPD
        else -> null
    }
    return ExoItem.Builder()
        .setUri(url)
        .setMediaId(key)
        .apply { if (mime != null) setMimeType(mime) }
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .build(),
        )
        .build()
}
