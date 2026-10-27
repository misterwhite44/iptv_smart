package com.misterwhite.iptvsmart.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.misterwhite.iptvsmart.data.CatalogRepository
import com.misterwhite.iptvsmart.data.CredentialsStore
import com.misterwhite.iptvsmart.data.LibraryRepository
import com.misterwhite.iptvsmart.data.account.AccountException
import com.misterwhite.iptvsmart.data.account.AccountRepository
import com.misterwhite.iptvsmart.data.account.AccountSync
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
    private val accountSync: AccountSync,
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

    private var accountRequested = false

    /** Vérifie l'abonnement à la première ouverture du Profil, pas au lancement de l'appli. */
    fun loadAccountOnce() {
        if (accountRequested) return
        accountRequested = true
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

    /**
     * Oublie le fournisseur IPTV, ici et sur le compte, avec son historique ;
     * les favoris enregistrés sur le compte reviennent ensuite.
     */
    fun changeProvider() {
        accountSync.stop()
        clearDevice()
        accountSync.forgetProviderAndRestart()
    }

    /**
     * Déconnecte le compte Zynora et efface tout de ce téléphone. Favoris, historique
     * et service IPTV restent sur le compte et reviennent à la prochaine connexion.
     */
    fun signOutAccount(onDone: () -> Unit) {
        _accountBusy.value = true
        viewModelScope.launch {
            accountSync.flushAndStop()
            zynoraAccount.signOut()
            clearDevice()
            _accountBusy.value = false
            onDone()
        }
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        _accountBusy.value = true
        _accountError.value = null
        viewModelScope.launch {
            try {
                accountSync.stop()
                zynoraAccount.deleteAccount()
                clearDevice()
                onDeleted()
            } catch (e: AccountException) {
                _accountError.value = e.message
                accountSync.restart()
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
