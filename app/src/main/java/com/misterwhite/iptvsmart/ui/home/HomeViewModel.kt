package com.misterwhite.iptvsmart.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.misterwhite.iptvsmart.data.CredentialsStore
import com.misterwhite.iptvsmart.data.xtream.AccountInfo
import com.misterwhite.iptvsmart.data.xtream.ContentType
import com.misterwhite.iptvsmart.data.xtream.XtreamClient
import com.misterwhite.iptvsmart.data.xtream.XtreamException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val account: AccountInfo,
        val liveCategories: Int,
        val movieCategories: Int,
        val seriesCategories: Int,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val client: XtreamClient,
    private val store: CredentialsStore,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val credentials = store.load() ?: return
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                coroutineScope {
                    val account = async { client.authenticate(credentials) }
                    val live = async { client.categories(credentials, ContentType.LIVE) }
                    val movies = async { client.categories(credentials, ContentType.MOVIE) }
                    val series = async { client.categories(credentials, ContentType.SERIES) }
                    HomeUiState.Ready(
                        account = account.await(),
                        liveCategories = live.await().size,
                        movieCategories = movies.await().size,
                        seriesCategories = series.await().size,
                    )
                }
            } catch (e: XtreamException) {
                HomeUiState.Error(e.message ?: "Erreur inconnue")
            } catch (e: Exception) {
                HomeUiState.Error("Erreur inattendue : ${e.message}")
            }
        }
    }

    fun logout() {
        store.clear()
    }
}
