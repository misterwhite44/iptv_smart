package com.misterwhite.iptvsmart.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.misterwhite.iptvsmart.data.CatalogRepository
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

sealed interface AccountUiState {
    data object Loading : AccountUiState
    data class Ready(
        val account: AccountInfo,
        val liveCategories: Int,
        val movieCategories: Int,
        val seriesCategories: Int,
    ) : AccountUiState
    data class Error(val message: String) : AccountUiState
}

class AccountViewModel(
    private val client: XtreamClient,
    private val store: CredentialsStore,
    private val catalog: CatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AccountUiState>(AccountUiState.Loading)
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val credentials = store.load() ?: return
        _state.value = AccountUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                coroutineScope {
                    val account = async { client.authenticate(credentials) }
                    val live = async { client.categories(credentials, ContentType.LIVE) }
                    val movies = async { client.categories(credentials, ContentType.MOVIE) }
                    val series = async { client.categories(credentials, ContentType.SERIES) }
                    AccountUiState.Ready(
                        account = account.await(),
                        liveCategories = live.await().size,
                        movieCategories = movies.await().size,
                        seriesCategories = series.await().size,
                    )
                }
            } catch (e: XtreamException) {
                AccountUiState.Error(e.message ?: "Erreur inconnue")
            } catch (e: Exception) {
                AccountUiState.Error("Erreur inattendue : ${e.message}")
            }
        }
    }

    fun logout() {
        store.clear()
        catalog.clear()
    }
}
