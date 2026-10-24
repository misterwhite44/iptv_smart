package com.misterwhite.iptvsmart.ui.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.misterwhite.iptvsmart.data.account.AccountException
import com.misterwhite.iptvsmart.data.account.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AccountMode { SIGN_IN, SIGN_UP, RESET }

data class AccountUiState(
    val mode: AccountMode = AccountMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val canSubmit: Boolean
        get() = !isLoading && email.contains('@') && (mode == AccountMode.RESET || password.length >= 6)
}

class AccountViewModel(private val account: AccountRepository) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun setMode(mode: AccountMode) = _state.update { it.copy(mode = mode, error = null, info = null) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        launchAction {
            when (s.mode) {
                AccountMode.SIGN_IN -> account.signIn(s.email, s.password)
                AccountMode.SIGN_UP -> account.signUp(s.email, s.password)
                AccountMode.RESET -> {
                    account.sendPasswordReset(s.email)
                    _state.update {
                        it.copy(mode = AccountMode.SIGN_IN, info = "Email envoyé : suis le lien pour choisir un nouveau mot de passe.")
                    }
                }
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) = launchAction { account.signInWithGoogle(activityContext) }

    private fun launchAction(block: suspend () -> Unit) {
        _state.update { it.copy(isLoading = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(isLoading = false) }
            } catch (e: AccountException) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
