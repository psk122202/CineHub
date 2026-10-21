package com.cinehub.tv.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinehub.tv.data.sources.LinksSource
import com.cinehub.tv.data.sources.LocalSource
import com.cinehub.tv.data.sources.sourceType
import com.cinehub.tv.model.SourceType
import com.cinehub.tv.ui.Ico
import com.cinehub.tv.ui.LocalApp
import com.cinehub.tv.ui.LocalNav
import com.cinehub.tv.ui.Palette
import com.cinehub.tv.ui.Routes
import com.cinehub.tv.ui.ScreenTitle
import com.cinehub.tv.ui.SectionTitle
import com.cinehub.tv.ui.TvButton
import com.cinehub.tv.ui.tryFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun iconFor(type: SourceType): ImageVector = when (type) {
    SourceType.JELLYFIN, SourceType.PLEX -> Icons.Filled.Dns
    SourceType.M3U -> Icons.Filled.List
    SourceType.SMB -> Icons.Filled.FolderShared
    SourceType.LOCAL -> Icons.Filled.PhoneAndroid
    SourceType.LINKS -> Icons.Filled.Link
    SourceType.TMDB -> Icons.Filled.PlayCircle
}

@Composable
fun SourcesScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val configs by remember { app.sourceConfigs }.collectAsState(initial = emptyList())
    val links by remember { app.linksFlow }.collectAsState(initial = emptyList())
    var hasLocalPerm by remember { mutableStateOf(app.local.hasPermission()) }
    val first = remember { FocusRequester() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocalPerm = granted
        HomeCache.invalidate()
        if (granted) nav.go(Routes.browse(LocalSource.LOCAL_ID))
    }

    LaunchedEffect(Unit) { delay(150); first.tryFocus() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item { ScreenTitle("Источники") }
        item { Column {
            SectionTitle("Добавить")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Column {
                    TvButton("Jellyfin / Emby", { nav.go(Routes.addSource(SourceType.JELLYFIN.name)) },
                        icon = Icons.Filled.Add, primary = true, modifier = Modifier.focusRequester(first))
                } }
                item { TvButton("Plex", { nav.go(Routes.addSource(SourceType.PLEX.name)) }, icon = Icons.Filled.Add) }
                item { TvButton("Сетевая папка", { nav.go(Routes.addSource(SourceType.SMB.name)) }, icon = Icons.Filled.Add) }
                item { TvButton("Плейлист M3U", { nav.go(Routes.addSource(SourceType.M3U.name)) }, icon = Icons.Filled.Add) }
                item { TvButton("Ссылка на видео", { nav.go(Routes.addSource(SourceType.LINKS.name)) }, icon = Icons.Filled.Add) }
            }
            Spacer(Modifier.height(16.dp))
        } }

        item { SectionTitle("Подключено") }
        item { Column {
            SourceRow(
                icon = Icons.Filled.PhoneAndroid,
                title = "Это устройство и флешки",
                subtitle = if (hasLocalPerm) "Видео из памяти ТВ и USB-накопителей" else "Нужно разрешение на доступ к видео",
                onOpen = {
                    if (hasLocalPerm) nav.go(Routes.browse(LocalSource.LOCAL_ID))
                    else permLauncher.launch(
                        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
                        else Manifest.permission.READ_EXTERNAL_STORAGE,
                    )
                },
            )
        } }
        item { Column {
            SourceRow(
                icon = Icons.Filled.Link,
                title = "Мои ссылки",
                subtitle = "${links.size} шт.",
                onOpen = { nav.go(Routes.browse(LinksSource.LINKS_ID)) },
            )
        } }
        items(configs, key = { it.id }) { cfg ->
            val t = cfg.sourceType()
            SourceRow(
                icon = iconFor(t),
                title = cfg.name,
                subtitle = "${t.title} · ${cfg.url}",
                onOpen = { nav.go(Routes.browse(cfg.id)) },
                onEdit = { nav.go(Routes.addSource(t.name, cfg.id)) },
                onDelete = { scope.launch { app.deleteSource(cfg.id); HomeCache.invalidate() } },
            )
        }
        if (links.isNotEmpty()) {
            item { Column { Spacer(Modifier.height(16.dp)); SectionTitle("Ссылки") } }
            items(links, key = { "link" + it.id }) { l ->
                SourceRow(
                    icon = Icons.Filled.Link,
                    title = l.title,
                    subtitle = l.url,
                    onOpen = { nav.go(Routes.browse(LinksSource.LINKS_ID)) },
                    onDelete = { scope.launch { app.deleteLink(l.id); HomeCache.invalidate() } },
                )
            }
        }
        item { Column {
            Text(
                "Jellyfin, Plex и SMB-папки работают в домашней сети. Плейлисты M3U и ссылки — " +
                    "любые прямые ссылки на видео (mp4, mkv, m3u8, mpd), на которые у вас есть права.",
                fontSize = 13.sp, color = Palette.TextDim,
                modifier = Modifier.padding(start = 48.dp, end = 48.dp, top = 20.dp).width(800.dp),
            )
        } }
    }
}

@Composable
private fun SourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onOpen: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        Modifier.padding(horizontal = 48.dp, vertical = 5.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            onClick = onOpen,
            modifier = Modifier.weight(1f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Palette.Surface, focusedContainerColor = Palette.SurfaceHi),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(icon, tint = Palette.Accent, size = 28.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 1)
                    Text(subtitle, fontSize = 13.sp, color = Palette.TextDim, maxLines = 1)
                }
            }
        }
        if (onEdit != null) TvButton("Изменить", onEdit, icon = Icons.Filled.Edit)
        if (onDelete != null) {
            TvButton(
                if (confirmDelete) "Точно удалить?" else "Удалить",
                { if (confirmDelete) onDelete() else confirmDelete = true },
                icon = Icons.Filled.Delete,
            )
        }
    }
}
