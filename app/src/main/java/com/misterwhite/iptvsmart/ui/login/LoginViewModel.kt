package com.misterwhite.iptvsmart.ui.login

import com.misterwhite.iptvsmart.BuildConfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.misterwhite.iptvsmart.data.CredentialsStore
import com.misterwhite.iptvsmart.data.account.AccountSync
import com.misterwhite.iptvsmart.data.xtream.DemoCatalog
import com.misterwhite.iptvsmart.data.xtream.XtreamClient
import com.misterwhite.iptvsmart.data.xtream.XtreamCredentials
import com.misterwhite.iptvsmart.data.xtream.XtreamException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
    /** Null sans compte Zynora ; sinon, garder ce service sur le compte. */
    val keepOnAccount: Boolean? = null,
) {
    val canSubmit get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !isLoading
}

class LoginViewModel(
    private val client: XtreamClient,
    private val store: CredentialsStore,
    private val accountSync: AccountSync,
    hasAccount: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState(keepOnAccount = if (hasAccount) true else null))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onServerUrlChange(value: String) {
        // Un lien M3U complet contient déjà l'identifiant et le mot de passe.
        val fromLink = XtreamClient.credentialsFromLink(value)
        _state.update {
            if (fromLink != null && it.username.isBlank() && it.password.isBlank()) {
                it.copy(
                    serverUrl = XtreamClient.normalizeServerUrl(value),
                    username = fromLink.first,
                    password = fromLink.second,
                    error = null,
                )
            } else {
                it.copy(serverUrl = value, error = null)
            }
        }
    }

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onKeepOnAccountChange(value: Boolean) = _state.update { it.copy(keepOnAccount = value) }

    /** Ouvre le catalogue de démonstration intégré, sans le garder sur le compte Zynora. */
    fun tryDemo() {
        if (!BuildConfig.DEMO || _state.value.isLoading) return
        store.save(DemoCatalog.credentials)
        store.saveLiveFormat(DemoCatalog.account.allowedOutputFormats)
        store.setKeepOnAccount(false)
        _state.update { it.copy(error = null, loggedIn = true) }
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val credentials = XtreamCredentials(
            serverUrl = XtreamClient.normalizeServerUrl(current.serverUrl),
            username = current.username.trim(),
            password = current.password.trim(),
        )
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val account = client.authenticate(credentials)
                store.save(credentials)
                store.saveLiveFormat(account.allowedOutputFormats)
                current.keepOnAccount?.let(accountSync::onCredentialsSaved)
                _state.update { it.copy(isLoading = false, loggedIn = true) }
            } catch (e: XtreamException) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Erreur inattendue : ${e.message}") }
            }
        }
    }
}
