package com.misterwhite.iptvsmart.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.misterwhite.iptvsmart.data.CatalogRepository
import com.misterwhite.iptvsmart.data.CredentialsStore
import com.misterwhite.iptvsmart.data.LibraryRepository
import com.misterwhite.iptvsmart.data.account.AccountException
import com.misterwhite.iptvsmart.data.account.AccountRepository
import com.misterwhite.iptvsmart.data.account.FavoritesSync
import com.misterwhite.iptvsmart.data.xtream.AccountInfo
import com.misterwhite.iptvsmart.data.xtream.XtreamClient
import com.misterwhite.iptvsmart.data.xtream.XtreamException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AccountState {
    data object Loading : AccountState
    data class Ready(val account: AccountInfo) : AccountState
    data class Error(val message: String) : AccountState
}

class ProfileViewModel(
    private val client: XtreamClient,
    private val store: CredentialsStore,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val zynoraAccount: AccountRepository,
    private val favoritesSync: FavoritesSync,
) : ViewModel() {

    /** Adresse du compte Zynora, ou null si les comptes ne sont pas configurés. */
    val accountEmail: String? = zynoraAccount.user.value?.let { it.email ?: "Compte Google" }

    private val _accountBusy = MutableStateFlow(false)
    val accountBusy: StateFlow<Boolean> = _accountBusy.asStateFlow()
    private val _accountError = MutableStateFlow<String?>(null)
    val accountError: StateFlow<String?> = _accountError.asStateFlow()

    private val credentials = store.load()
    val username: String = credentials?.username.orEmpty()
    val server: String = credentials?.serverUrl.orEmpty()

    private val _account = MutableStateFlow<AccountState>(AccountState.Loading)
    val account: StateFlow<AccountState> = _account.asStateFlow()

    init {
        refreshAccount()
    }

    fun refreshAccount() {
        val c = store.load() ?: return
        _account.value = AccountState.Loading
        viewModelScope.launch {
            _account.value = try {
                val info = client.authenticate(c)
                store.saveLiveFormat(info.allowedOutputFormats)
                AccountState.Ready(info)
            } catch (e: XtreamException) {
                AccountState.Error(e.message ?: "Erreur inconnue")
            } catch (e: Exception) {
                AccountState.Error("Erreur inattendue : ${e.message}")
            }
        }
    }

    fun clearHistory() = library.clearHistory()

    /** Oublie le fournisseur IPTV ; les favoris enregistrés sur le compte reviennent ensuite. */
    fun changeProvider() {
        favoritesSync.stop()
        clearDevice()
        favoritesSync.restart()
    }

    /** Déconnecte le compte Zynora et efface tout de ce téléphone (les favoris restent sur le compte). */
    fun signOutAccount() {
        favoritesSync.stop()
        zynoraAccount.signOut()
        clearDevice()
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        _accountBusy.value = true
        _accountError.value = null
        viewModelScope.launch {
            try {
                favoritesSync.stop()
                zynoraAccount.deleteAccount()
                clearDevice()
                onDeleted()
            } catch (e: AccountException) {
                _accountError.value = e.message
                favoritesSync.restart()
            } finally {
                _accountBusy.value = false
            }
        }
    }

    private fun clearDevice() {
        store.clear()
        catalog.clear()
        library.clear()
    }
}
