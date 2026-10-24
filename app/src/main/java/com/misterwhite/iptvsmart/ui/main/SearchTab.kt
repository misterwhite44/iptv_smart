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

private fun String.normalized(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

@Composable
fun SearchTab(content: CatalogRepository.Content, actions: MainActions) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().normalized()
    val results = remember(content, q) {
        if (q.length < 2) null else Triple(
            content.movies.items.filter { it.name.normalized().contains(q) }.take(60),
            content.series.items.filter { it.name.normalized().contains(q) }.take(60),
            content.live.items.filter { it.name.normalized().contains(q) }.take(60),
        )
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
        when {
            results == null -> EmptyBox("Tape au moins deux lettres pour chercher dans ton catalogue.")
            results.first.isEmpty() && results.second.isEmpty() && results.third.isEmpty() ->
                EmptyBox("Aucun résultat pour « ${query.trim()} ».")
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    ContentRow("Films", results.first, key = { it.id }) { movie ->
                        PosterCard(movie.name, movie.poster, onClick = { actions.openMovie(movie.id) })
                    }
                }
                item {
                    ContentRow("Séries", results.second, key = { it.id }) { series ->
                        PosterCard(series.name, series.cover, onClick = { actions.openSeries(series.id) })
                    }
                }
                item {
                    ContentRow("Chaînes", results.third, key = { it.id }) { channel ->
                        ChannelCard(channel.name, channel.logo, onClick = { actions.playChannel(channel.id) })
                    }
                }
            }
        }
    }
}
