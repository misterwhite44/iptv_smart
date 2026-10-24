package com.misterwhite.iptvsmart.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.misterwhite.iptvsmart.data.CatalogRepository
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

class MainViewModel(private val catalog: CatalogRepository) : ViewModel() {

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
                CatalogState.Ready(catalog.content(forceRefresh = force))
            } catch (e: XtreamException) {
                CatalogState.Error(e.message ?: "Erreur inconnue")
            } catch (e: Exception) {
                CatalogState.Error("Erreur inattendue : ${e.message}")
            }
        }
    }
}
