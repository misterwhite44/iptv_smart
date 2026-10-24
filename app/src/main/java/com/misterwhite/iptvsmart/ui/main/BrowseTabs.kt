package com.misterwhite.iptvsmart.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.misterwhite.iptvsmart.data.xtream.Catalog
import com.misterwhite.iptvsmart.data.xtream.Category
import com.misterwhite.iptvsmart.data.xtream.LiveChannel
import com.misterwhite.iptvsmart.data.xtream.Movie
import com.misterwhite.iptvsmart.data.xtream.Series
import com.misterwhite.iptvsmart.ui.components.CategoryChips
import com.misterwhite.iptvsmart.ui.components.ChannelCard
import com.misterwhite.iptvsmart.ui.components.EmptyBox
import com.misterwhite.iptvsmart.ui.components.PosterCard

@Composable
fun LiveTab(catalog: Catalog<LiveChannel>, actions: MainActions) {
    BrowseLayout(
        title = "Direct",
        catalog = catalog,
        categoryOf = { it.categoryId },
        minCellWidth = 150,
    ) { list ->
        items(list, key = { it.id }) { channel ->
            ChannelCard(channel.name, channel.logo, onClick = { actions.playChannel(channel.id) }, width = null)
        }
    }
}

@Composable
fun MoviesTab(catalog: Catalog<Movie>, actions: MainActions) {
    BrowseLayout(
        title = "Films",
        catalog = catalog,
        categoryOf = { it.categoryId },
        minCellWidth = 105,
    ) { list ->
        items(list, key = { it.id }) { movie ->
            PosterCard(movie.name, movie.poster, onClick = { actions.openMovie(movie.id) }, width = null)
        }
    }
}

@Composable
fun SeriesTab(catalog: Catalog<Series>, actions: MainActions) {
    BrowseLayout(
        title = "Séries",
        catalog = catalog,
        categoryOf = { it.categoryId },
        minCellWidth = 105,
    ) { list ->
        items(list, key = { it.id }) { series ->
            PosterCard(series.name, series.cover, onClick = { actions.openSeries(series.id) }, width = null)
        }
    }
}

/** Titre, filtres par catégorie et grille : la mise en page commune des onglets. */
@Composable
private fun <T> BrowseLayout(
    title: String,
    catalog: Catalog<T>,
    categoryOf: (T) -> String?,
    minCellWidth: Int,
    grid: androidx.compose.foundation.lazy.grid.LazyGridScope.(List<T>) -> Unit,
) {
    var selected by rememberSaveable(title) { mutableStateOf<String?>(null) }
    // On n'affiche que les catégories qui contiennent quelque chose.
    val categories: List<Category> = remember(catalog) {
        val used = catalog.items.mapNotNullTo(HashSet()) { categoryOf(it) }
        catalog.categories.filter { it.id in used }
    }
    val items = remember(catalog, selected) {
        if (selected == null) catalog.items else catalog.items.filter { categoryOf(it) == selected }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        )
        if (categories.isNotEmpty()) {
            CategoryChips(categories, selected, onSelect = { selected = it })
            Spacer(Modifier.height(8.dp))
        }
        if (items.isEmpty()) {
            EmptyBox("Rien à afficher dans cette catégorie.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minCellWidth.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                grid(items)
            }
        }
    }
}
