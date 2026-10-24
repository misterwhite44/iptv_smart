package com.misterwhite.iptvsmart.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.misterwhite.iptvsmart.data.CatalogRepository
import com.misterwhite.iptvsmart.data.FavoriteEntry
import com.misterwhite.iptvsmart.data.LibraryRepository
import com.misterwhite.iptvsmart.data.MediaKind
import com.misterwhite.iptvsmart.data.PreferredChannels
import com.misterwhite.iptvsmart.data.xtream.MovieDetails
import com.misterwhite.iptvsmart.data.xtream.XtreamException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val content: CatalogRepository.Content) : CatalogState
    data class Error(val message: String) : CatalogState
}

class MainViewModel(
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    /** Fiches des films mis en avant (résumé, durée, image large). */
    private val _movieDetails = MutableStateFlow<Map<String, MovieDetails>>(emptyMap())
    val movieDetails: StateFlow<Map<String, MovieDetails>> = _movieDetails.asStateFlow()
    private val requested = mutableSetOf<String>()

    fun loadMovieDetails(ids: List<String>) {
        ids.filter { requested.add(it) }.forEach { id ->
            viewModelScope.launch {
                try {
                    val details = catalog.movieDetails(id)
                    _movieDetails.value = _movieDetails.value + (id to details)
                } catch (e: Exception) {
                    // Pas grave : l'affiche reste sans résumé.
                }
            }
        }
    }

    private fun seedPreferredChannels(content: CatalogRepository.Content) {
        val now = System.currentTimeMillis()
        val channels = PreferredChannels.find(content.live).take(MAX_SEEDED)
        library.seedFavoritesOnce(
            "seeded_preferred_live_v1",
            channels.mapIndexed { i, ch ->
                FavoriteEntry(LibraryRepository.liveKey(ch.id), MediaKind.LIVE, ch.id, ch.name, ch.logo, now - i)
            },
        )
    }


    private val _state = MutableStateFlow<CatalogState>(
        catalog.cachedContent()?.let { CatalogState.Ready(it) } ?: CatalogState.Loading
    )
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    init {
        if (_state.value !is CatalogState.Ready) load(force = false)
    }

    fun load(force: Boolean) {
        if (_state.value !is CatalogState.Ready) _state.value = CatalogState.Loading
        viewModelScope.launch {
            _state.value = try {
                CatalogState.Ready(catalog.content(forceRefresh = force).also { seedPreferredChannels(it) })
            } catch (e: XtreamException) {
                CatalogState.Error(e.message ?: "Erreur inconnue")
            } catch (e: Exception) {
                CatalogState.Error("Erreur inattendue : ${e.message}")
            }
        }
    }

    private companion object {
        const val MAX_SEEDED = 40
    }
}
