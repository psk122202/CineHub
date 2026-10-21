package com.cinehub.tv.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.cinehub.tv.data.AppContainer
import com.cinehub.tv.model.MediaItem
import com.cinehub.tv.model.MediaKind
import com.cinehub.tv.model.MediaRow
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon as M3Icon

val LocalApp = compositionLocalOf<AppContainer> { error("AppContainer не задан") }
val LocalNav = compositionLocalOf<Navigator> { error("Navigator не задан") }

@Composable
fun Ico(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = 22.dp) {
    M3Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

private val CardShape = RoundedCornerShape(10.dp)

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Palette.Accent.copy(alpha = 0.9f) else Palette.SurfaceHi,
            contentColor = if (primary) Palette.OnAccent else Palette.Text,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Ico(icon, size = 20.dp)
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

fun kindIcon(kind: MediaKind): ImageVector = when (kind) {
    MediaKind.MOVIE -> Icons.Filled.Movie
    MediaKind.SHOW, MediaKind.SEASON, MediaKind.EPISODE -> Icons.Filled.Tv
    MediaKind.FOLDER -> Icons.Filled.Folder
    MediaKind.CHANNEL -> Icons.Filled.LiveTv
    MediaKind.VIDEO -> Icons.Filled.PlayCircle
}

/** Карточка фильма/сериала/видео. */
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = item.landscape,
    progress: Float? = null,
    onFocused: (MediaItem) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val width = if (landscape) 240.dp else 140.dp
    val height = if (landscape) 135.dp else 210.dp
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .width(width)
                .height(height)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused(item)
                },
            shape = ClickableSurfaceDefaults.shape(CardShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Palette.Surface,
                focusedContainerColor = Palette.Surface,
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Palette.Accent), shape = CardShape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        ) {
            Box(Modifier.fillMaxSize()) {
                val img = if (landscape) item.backdrop ?: item.poster else item.poster ?: item.backdrop
                Placeholder(item)
                if (img != null) {
                    AsyncImage(
                        model = img,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (landscape || img == null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xDD000000))),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                        Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        item.subtitle?.let { Text(it, fontSize = 11.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                item.rating?.let { r ->
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(Icons.Filled.Star, tint = Palette.Accent, size = 12.dp)
                        Text(String.format("%.1f", r), fontSize = 11.sp, color = Color.White)
                    }
                }
                if (progress != null && progress > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color(0x66FFFFFF)),
                    ) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(Palette.Accent))
                    }
                }
            }
        }
        if (!landscape) {
            Spacer(Modifier.height(6.dp))
            Text(
                item.title,
                fontSize = 13.sp,
                color = if (focused) Palette.Text else Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BoxScope.Placeholder(item: MediaItem) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Palette.SurfaceHi, Palette.Surface))),
        contentAlignment = Alignment.Center,
    ) {
        Ico(kindIcon(item.kind), tint = Palette.TextDim.copy(alpha = 0.6f), size = 40.dp)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 48.dp, bottom = 10.dp, top = 6.dp),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = Palette.Text,
    )
}

/** Горизонтальный ряд карточек. */
@Composable
fun MediaRowView(
    row: MediaRow,
    onFocused: (MediaItem) -> Unit = {},
    progressOf: (MediaItem) -> Float? = { null },
    firstItemFocus: FocusRequester? = null,
    onClick: ((Int, MediaItem) -> Unit)? = null,
) {
    val nav = LocalNav.current
    val app = LocalApp.current
    val ctx = LocalContext.current
    Column(Modifier.padding(bottom = 18.dp)) {
        SectionTitle(row.title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(row.items, key = { i, it -> it.key + "#" + i }) { i, item ->
                MediaCard(
                    item = item,
                    landscape = row.landscape,
                    progress = progressOf(item),
                    onFocused = onFocused,
                    modifier = if (i == 0 && firstItemFocus != null) Modifier.focusRequester(firstItemFocus) else Modifier,
                    onClick = { if (onClick != null) onClick(i, item) else nav.open(item, row.items) },
                    onLongClick = {
                        nav.scope.launch {
                            app.toggleList("fav", item, true)
                            Toast.makeText(ctx, "«${item.title}» добавлено в избранное", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier, text: String? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Palette.Accent)
            if (text != null) {
                Spacer(Modifier.height(12.dp))
                Text(text, color = Palette.TextDim)
            }
        }
    }
}

@Composable
fun MessageBox(title: String, text: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Box(modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
            if (text != null) Text(text, fontSize = 15.sp, color = Palette.TextDim)
            action?.invoke()
        }
    }
}

/** Фоновая картинка с затемнением слева и снизу. */
@Composable
fun Backdrop(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(0f to Palette.Bg, 0.55f to Palette.Bg.copy(alpha = 0.6f), 1f to Color.Transparent)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Palette.Bg)),
        )
    }
}

fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

/** Безопасный запрос фокуса (если элемент ещё не на экране — не падаем). */
fun FocusRequester.tryFocus() { runCatching { requestFocus() } }

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 48.dp, top = 28.dp, bottom = 16.dp),
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}
