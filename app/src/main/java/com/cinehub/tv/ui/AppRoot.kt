package com.cinehub.tv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinehub.tv.data.AppContainer
import com.cinehub.tv.player.PlayerScreen
import com.cinehub.tv.ui.screens.AddSourceScreen
import com.cinehub.tv.ui.screens.BrowseScreen
import com.cinehub.tv.ui.screens.DetailsScreen
import com.cinehub.tv.ui.screens.DiscoverScreen
import com.cinehub.tv.ui.screens.GenresScreen
import com.cinehub.tv.ui.screens.HomeScreen
import com.cinehub.tv.ui.screens.LibraryScreen
import com.cinehub.tv.ui.screens.ProfilesScreen
import com.cinehub.tv.ui.screens.SearchScreen
import com.cinehub.tv.ui.screens.SeasonScreen
import com.cinehub.tv.ui.screens.SettingsScreen
import com.cinehub.tv.ui.screens.SourcesScreen

@Composable
fun AppRoot(app: AppContainer) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val navigator = remember { Navigator(navController, scope) }
    var startRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        app.ensureProfile()
        startRoute = if (app.db.profiles().all().size > 1) Routes.PROFILES else Routes.HOME
    }

    CompositionLocalProvider(LocalApp provides app, LocalNav provides navigator) {
        Box(Modifier.fillMaxSize().background(Palette.Bg)) {
            val start = startRoute
            if (start == null) {
                Loading()
            } else {
                val entry by navController.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                Row(Modifier.fillMaxSize()) {
                    if (route != null && route !in Routes.fullScreen) NavRail(route)
                    NavHost(
                        navController = navController,
                        startDestination = start,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        composable(Routes.PROFILES) { ProfilesScreen() }
                        composable(Routes.HOME) { HomeScreen() }
                        composable(Routes.SEARCH) { SearchScreen() }
                        composable(Routes.LIBRARY) { LibraryScreen() }
                        composable(Routes.SOURCES) { SourcesScreen() }
                        composable(Routes.SETTINGS) { SettingsScreen() }
                        composable(Routes.PLAYER) { PlayerScreen() }
                        composable(
                            Routes.DETAILS,
                            arguments = listOf(navArgument("key") { type = NavType.StringType }),
                        ) { e -> DetailsScreen(e.arguments?.getString("key").orEmpty()) }
                        composable(
                            Routes.BROWSE,
                            arguments = listOf(
                                navArgument("sourceId") { type = NavType.LongType },
                                navArgument("parent") { type = NavType.StringType; nullable = true; defaultValue = null },
                            ),
                        ) { e ->
                            BrowseScreen(e.arguments?.getLong("sourceId") ?: 0L, e.arguments?.getString("parent"))
                        }
                        composable(
                            Routes.SEASON,
                            arguments = listOf(
                                navArgument("tvId") { type = NavType.StringType },
                                navArgument("number") { type = NavType.IntType },
                                navArgument("show") { type = NavType.StringType; defaultValue = "" },
                            ),
                        ) { e ->
                            SeasonScreen(
                                tvId = e.arguments?.getString("tvId").orEmpty(),
                                number = e.arguments?.getInt("number") ?: 1,
                                showTitle = e.arguments?.getString("show").orEmpty(),
                            )
                        }
                        composable(
                            Routes.ADD_SOURCE,
                            arguments = listOf(
                                navArgument("type") { type = NavType.StringType },
                                navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                            ),
                        ) { e ->
                            AddSourceScreen(e.arguments?.getString("type").orEmpty(), e.arguments?.getLong("id") ?: 0L)
                        }
                        composable(
                            Routes.GENRES,
                            arguments = listOf(navArgument("type") { type = NavType.StringType }),
                        ) { e -> GenresScreen(e.arguments?.getString("type") ?: "movie") }
                        composable(
                            Routes.DISCOVER,
                            arguments = listOf(
                                navArgument("type") { type = NavType.StringType },
                                navArgument("genreId") { type = NavType.IntType },
                                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                            ),
                        ) { e ->
                            DiscoverScreen(
                                type = e.arguments?.getString("type") ?: "movie",
                                genreId = e.arguments?.getInt("genreId") ?: 0,
                                name = e.arguments?.getString("name").orEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class RailItem(val route: String, val title: String, val icon: ImageVector)

private val railItems = listOf(
    RailItem(Routes.SEARCH, "Поиск", Icons.Filled.Search),
    RailItem(Routes.HOME, "Главная", Icons.Filled.Home),
    RailItem(Routes.LIBRARY, "Моя библиотека", Icons.Filled.VideoLibrary),
    RailItem(Routes.genres("movie"), "Жанры", Icons.Filled.Category),
    RailItem(Routes.SOURCES, "Источники", Icons.Filled.Storage),
    RailItem(Routes.SETTINGS, "Настройки", Icons.Filled.Settings),
)

@Composable
private fun NavRail(currentRoute: String) {
    val nav = LocalNav.current
    val app = LocalApp.current
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 220.dp else 76.dp, label = "rail")
    val profiles by app.profiles.collectAsState(initial = emptyList())
    val settings by app.settingsFlow.collectAsState()
    val profile = profiles.firstOrNull { it.id == settings?.profileId }

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(if (expanded) Palette.Surface else Color(0xFF0B0E12))
            .onFocusChanged { expanded = it.hasFocus }
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Профиль
        RailButton(
            selected = false,
            expanded = expanded,
            title = profile?.name ?: "Профиль",
            onClick = { nav.go(Routes.PROFILES) },
            leading = {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(profile?.color ?: 0xFFE5A00D)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (profile?.name ?: "?").take(1).uppercase(),
                        color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    )
                }
            },
        )
        Spacer(Modifier.height(18.dp))
        railItems.forEach { item ->
            val selected = currentRoute == item.route ||
                (item.route.startsWith("genres") && (currentRoute.startsWith("genres") || currentRoute.startsWith("discover")))
            RailButton(
                selected = selected,
                expanded = expanded,
                title = item.title,
                onClick = { nav.goTab(item.route) },
                leading = { Ico(item.icon, size = 24.dp) },
            )
        }
    }
}

@Composable
private fun RailButton(
    selected: Boolean,
    expanded: Boolean,
    title: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.SurfaceHi else Color.Transparent,
            contentColor = if (selected) Palette.Accent else Palette.TextDim,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            leading()
            if (expanded) Text(title, fontSize = 15.sp, maxLines = 1)
        }
    }
}
