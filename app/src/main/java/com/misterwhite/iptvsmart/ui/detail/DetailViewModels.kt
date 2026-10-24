package com.misterwhite.iptvsmart.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.misterwhite.iptvsmart.data.CatalogRepository
import com.misterwhite.iptvsmart.data.xtream.MovieDetails
import com.misterwhite.iptvsmart.data.xtream.SeriesDetails
import com.misterwhite.iptvsmart.data.xtream.XtreamException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailState<out T> {
    data object Loading : DetailState<Nothing>
    data class Ready<T>(val details: T) : DetailState<T>
    data class Error(val message: String) : DetailState<Nothing>
}

/** Charge une fiche (film ou série) et gère les erreurs de la même façon. */
abstract class DetailViewModel<T>(private val loader: suspend () -> T) : ViewModel() {
    private val _state = MutableStateFlow<DetailState<T>>(DetailState.Loading)
    val state: StateFlow<DetailState<T>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = DetailState.Loading
        viewModelScope.launch {
            _state.value = try {
                DetailState.Ready(loader())
            } catch (e: XtreamException) {
                DetailState.Error(e.message ?: "Erreur inconnue")
            } catch (e: Exception) {
                DetailState.Error("Erreur inattendue : ${e.message}")
            }
        }
    }
}

class MovieDetailViewModel(catalog: CatalogRepository, movieId: String) :
    DetailViewModel<MovieDetails>({ catalog.movieDetails(movieId) })

class SeriesDetailViewModel(catalog: CatalogRepository, seriesId: String) :
    DetailViewModel<SeriesDetails>({ catalog.seriesDetails(seriesId) })
