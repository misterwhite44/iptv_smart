package com.misterwhite.iptvsmart.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.misterwhite.iptvsmart.data.CredentialsStore
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
) {
    val canSubmit get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !isLoading
}

class LoginViewModel(
    private val client: XtreamClient,
    private val store: CredentialsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
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
                client.authenticate(credentials)
                store.save(credentials)
                _state.update { it.copy(isLoading = false, loggedIn = true) }
            } catch (e: XtreamException) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Erreur inattendue : ${e.message}") }
            }
        }
    }
}
