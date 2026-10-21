package com.cinehub.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinehub.tv.ui.Ico
import com.cinehub.tv.ui.LocalApp
import com.cinehub.tv.ui.LocalNav
import com.cinehub.tv.ui.Palette
import com.cinehub.tv.ui.Routes
import com.cinehub.tv.ui.TvButton
import com.cinehub.tv.ui.tryFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfilesScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val profiles by remember { app.profiles }.collectAsState(initial = emptyList())
    var deleteMode by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }

    LaunchedEffect(profiles.isNotEmpty()) {
        if (profiles.isNotEmpty()) { delay(150); first.tryFocus() }
    }

    fun select(id: Long) = scope.launch {
        app.selectProfile(id)
        HomeCache.invalidate()
        nav.nav.navigate(Routes.HOME) {
            popUpTo(nav.nav.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (deleteMode) "Какой профиль удалить?" else "Кто смотрит?",
                fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Palette.Text,
            )
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                profiles.forEachIndexed { i, p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = {
                                if (deleteMode) {
                                    if (profiles.size > 1) scope.launch { app.deleteProfile(p.id); deleteMode = false }
                                } else select(p.id)
                            },
                            modifier = Modifier.size(120.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color(p.color), focusedContainerColor = Color(p.color)),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(androidx.compose.foundation.BorderStroke(4.dp, Color.White), shape = CircleShape),
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (deleteMode) Ico(Icons.Filled.Delete, tint = Color.Black, size = 44.dp)
                                else Text(p.name.take(1).uppercase(), fontSize = 52.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(p.name, fontSize = 18.sp, color = Palette.Text)
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
            if (adding) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { androidx.compose.material3.Text("Имя профиля") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Palette.Accent,
                            focusedTextColor = Palette.Text,
                            unfocusedTextColor = Palette.Text,
                        ),
                        modifier = Modifier.width(320.dp),
                    )
                    TvButton("Создать", {
                        val n = newName.trim()
                        if (n.isNotEmpty()) scope.launch {
                            val color = Palette.ProfileColors[profiles.size % Palette.ProfileColors.size]
                            app.addProfile(n, color)
                            newName = ""; adding = false
                        }
                    }, primary = true)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Добавить профиль", { adding = true; deleteMode = false }, icon = Icons.Filled.Add)
                    if (profiles.size > 1) {
                        TvButton(if (deleteMode) "Готово" else "Удалить профиль", { deleteMode = !deleteMode }, icon = Icons.Filled.Delete)
                    }
                }
            }
        }
    }
}
