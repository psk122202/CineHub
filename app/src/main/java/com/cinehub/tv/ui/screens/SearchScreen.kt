package com.cinehub.tv.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.cinehub.tv.data.SearchGroup
import com.cinehub.tv.model.MediaRow
import com.cinehub.tv.ui.Ico
import com.cinehub.tv.ui.LocalApp
import com.cinehub.tv.ui.MediaRowView
import com.cinehub.tv.ui.Palette
import com.cinehub.tv.ui.ScreenTitle
import com.cinehub.tv.ui.tryFocus
import kotlinx.coroutines.delay

private object SearchState {
    var lastQuery = ""
    var lastResults: List<SearchGroup> = emptyList()
}

@Composable
fun SearchScreen() {
    val app = LocalApp.current
    var query by rememberSaveable { mutableStateOf(SearchState.lastQuery) }
    var results by remember { mutableStateOf(SearchState.lastResults) }
    var loading by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }

    LaunchedEffect(Unit) { delay(150); field.tryFocus() }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); return@LaunchedEffect }
        if (q == SearchState.lastQuery && SearchState.lastResults.isNotEmpty()) { results = SearchState.lastResults; return@LaunchedEffect }
        delay(700) // ждём, пока пользователь допечатает
        loading = true
        results = app.searchAll(q)
        loading = false
        SearchState.lastQuery = q
        SearchState.lastResults = results
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item { ScreenTitle("Поиск") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { androidx.compose.material3.Text("Название фильма или сериала") },
                leadingIcon = { Ico(Icons.Filled.Search, tint = Palette.TextDim) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Accent,
                    unfocusedBorderColor = Palette.SurfaceHi,
                    focusedTextColor = Palette.Text,
                    unfocusedTextColor = Palette.Text,
                    cursorColor = Palette.Accent,
                ),
                modifier = Modifier
                    .padding(start = 48.dp, bottom = 20.dp)
                    .width(640.dp)
                    .focusRequester(field),
            )
        }
        if (loading) item { Text("Ищу во всех источниках…", color = Palette.TextDim, modifier = Modifier.padding(start = 48.dp)) }
        if (!loading && query.trim().length >= 2 && results.isEmpty()) {
            item { Text("Ничего не найдено", color = Palette.TextDim, fontSize = 16.sp, modifier = Modifier.padding(start = 48.dp)) }
        }
        items(results) { g ->
            MediaRowView(MediaRow(g.title, g.items, landscape = g.items.count { it.landscape } > g.items.size / 2))
        }
    }
}
