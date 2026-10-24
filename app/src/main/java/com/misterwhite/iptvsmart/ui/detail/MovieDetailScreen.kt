package com.misterwhite.iptvsmart.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misterwhite.iptvsmart.data.xtream.MovieDetails
import com.misterwhite.iptvsmart.ui.components.ErrorBox
import com.misterwhite.iptvsmart.ui.components.LoadingBox
import com.misterwhite.iptvsmart.ui.components.ProgressBar
import com.misterwhite.iptvsmart.ui.theme.IptvColors

@Composable
fun MovieDetailScreen(
    viewModel: MovieDetailViewModel,
    onBack: () -> Unit,
    onPlay: (MovieDetails) -> Unit,
    /** Avancement déjà regardé, entre 0 et 1, s'il y en a un. */
    progress: Float? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (MovieDetails) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(IptvColors.Background)) {
        when (val s = state) {
            DetailState.Loading -> LoadingBox()
            is DetailState.Error -> ErrorBox(s.message, onRetry = viewModel::load)
            is DetailState.Ready -> {
                val d = s.details
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding(),
                ) {
                    DetailHeader(
                        title = d.movie.name,
                        backdrop = d.backdrop,
                        poster = d.movie.poster,
                        meta = listOfNotNull(
                            d.releaseDate?.take(4),
                            d.duration,
                            formatRating(d.movie.rating),
                        ).joinToString(" • "),
                        onBack = onBack,
                    )
                    Column(Modifier.padding(16.dp)) {
                        PlayButton(
                            if (progress != null && progress > 0.02f) "REPRENDRE" else "LECTURE",
                            onClick = { onPlay(d) },
                        )
                        if (progress != null && progress > 0.02f) {
                            Spacer(Modifier.height(8.dp))
                            ProgressBar(progress)
                        }
                        Spacer(Modifier.height(10.dp))
                        FavoriteButton(isFavorite, onClick = { onToggleFavorite(d) })
                        Spacer(Modifier.height(18.dp))
                        if (!d.plot.isNullOrBlank()) {
                            Text(d.plot, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(14.dp))
                        }
                        InfoLine("Genre", d.genre)
                        InfoLine("Réalisation", d.director)
                        InfoLine("Avec", d.cast)
                        InfoLine("Sortie", d.releaseDate)
                    }
                }
            }
        }
    }
}
