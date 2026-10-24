package com.misterwhite.iptvsmart.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.misterwhite.iptvsmart.data.CatalogRepository
import com.misterwhite.iptvsmart.ui.components.ChannelCard
import com.misterwhite.iptvsmart.ui.components.ContentRow
import com.misterwhite.iptvsmart.ui.components.EmptyBox
import com.misterwhite.iptvsmart.ui.components.PosterCard
import com.misterwhite.iptvsmart.ui.theme.IptvColors
import java.text.Normalizer
import androidx.compose.runtime.LaunchedEffect
import com.misterwhite.iptvsmart.data.xtream.LiveChannel
import com.misterwhite.iptvsmart.data.xtream.Movie
import com.misterwhite.iptvsmart.data.xtream.Series
import com.misterwhite.iptvsmart.ui.components.LoadingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val DIACRITICS = Regex("\\p{Mn}+")

private fun String.normalized(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

private data class SearchResults(
    val movies: List<Movie>,
    val series: List<Series>,
    val channels: List<LiveChannel>,
) {
    val isEmpty get() = movies.isEmpty() && series.isEmpty() && channels.isEmpty()
}

/**
 * Noms déjà mis en minuscules et sans accents, calculés une seule fois :
 * les catalogues comptent souvent des dizaines de milliers de titres.
 */
private class SearchIndex(private val content: CatalogRepository.Content) {
    private val movieNames by lazy { content.movies.items.map { it.name.normalized() } }
    private val seriesNames by lazy { content.series.items.map { it.name.normalized() } }
    private val channelNames by lazy { content.live.items.map { it.name.normalized() } }

    fun search(query: String): SearchResults = SearchResults(
        movies = matches(content.movies.items, movieNames, query),
        series = matches(content.series.items, seriesNames, query),
        channels = matches(content.live.items, channelNames, query),
    )

    private fun <T> matches(items: List<T>, names: List<String>, query: String): List<T> {
        val found = ArrayList<T>()
        for (i in names.indices) {
            if (names[i].contains(query)) {
                found.add(items[i])
                if (found.size >= MAX_RESULTS) break
            }
        }
        return found
    }

    private companion object {
        const val MAX_RESULTS = 60
    }
}

@Composable
fun SearchTab(content: CatalogRepository.Content, actions: MainActions) {
    var query by rememberSaveable { mutableStateOf("") }
    val index = remember(content) { SearchIndex(content) }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var searching by remember { mutableStateOf(false) }

    // La recherche tourne en arrière-plan, un court instant après la dernière
    // lettre tapée, pour que le clavier reste fluide.
    LaunchedEffect(index, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = null
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(250)
        results = withContext(Dispatchers.Default) { index.search(q.normalized()) }
        searching = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Films, séries, chaînes…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = IptvColors.Surface,
                unfocusedContainerColor = IptvColors.Surface,
                focusedIndicatorColor = IptvColors.Surface,
                unfocusedIndicatorColor = IptvColors.Surface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        val current = results
        when {
            query.isBlank() -> EmptyBox("Cherche un film, une série ou une chaîne.")
            current == null -> LoadingBox()
            current.isEmpty && !searching -> EmptyBox("Aucun résultat pour « ${query.trim()} ».")
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    ContentRow("Films", current.movies, key = { it.id }) { movie ->
                        PosterCard(movie.name, movie.poster, onClick = { actions.openMovie(movie.id) })
                    }
                }
                item {
                    ContentRow("Séries", current.series, key = { it.id }) { series ->
                        PosterCard(series.name, series.cover, onClick = { actions.openSeries(series.id) })
                    }
                }
                item {
                    ContentRow("Chaînes", current.channels, key = { it.id }) { channel ->
                        ChannelCard(channel.name, channel.logo, onClick = { actions.playChannel(channel.id) })
                    }
                }
            }
        }
    }
}
