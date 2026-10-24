package com.misterwhite.iptvsmart.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misterwhite.iptvsmart.ui.login.LoginField
import com.misterwhite.iptvsmart.ui.theme.IptvColors

/** Connexion ou création du compte Zynora, avant de saisir son fournisseur. */
@Composable
fun AccountScreen(viewModel: AccountViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val submit = {
        focusManager.clearFocus()
        viewModel.submit()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IptvColors.screenGradient)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = IptvColors.Accent, modifier = Modifier.size(64.dp))
            Text("Zynora", style = MaterialTheme.typography.headlineLarge, color = IptvColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (state.mode) {
                    AccountMode.SIGN_IN -> "Connecte-toi à ton compte Zynora"
                    AccountMode.SIGN_UP -> "Crée ton compte Zynora"
                    AccountMode.RESET -> "Entre ton adresse : tu recevras un lien pour changer ton mot de passe"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = IptvColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            if (state.mode != AccountMode.RESET) {
                OutlinedButton(
                    onClick = { viewModel.signInWithGoogle(context) },
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, IptvColors.SurfaceHigh),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text("G", color = IptvColors.TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.size(12.dp))
                    Text("Continuer avec Google", color = IptvColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = IptvColors.SurfaceHigh)
                    Text("ou", color = IptvColors.TextSecondary, modifier = Modifier.padding(horizontal = 12.dp))
                    HorizontalDivider(Modifier.weight(1f), color = IptvColors.SurfaceHigh)
                }
                Spacer(Modifier.height(20.dp))
            }

            LoginField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "Adresse email",
                keyboardType = KeyboardType.Email,
                imeAction = if (state.mode == AccountMode.RESET) ImeAction.Done else ImeAction.Next,
                leading = { Icon(Icons.Filled.Email, contentDescription = null) },
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = submit,
            )
            if (state.mode != AccountMode.RESET) {
                Spacer(Modifier.height(14.dp))
                LoginField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = if (state.mode == AccountMode.SIGN_UP) "Mot de passe (6 caractères minimum)" else "Mot de passe",
                    leading = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Masquer" else "Afficher", color = IptvColors.TextSecondary)
                        }
                    },
                    onDone = submit,
                )
            }

            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            state.info?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = IptvColors.AccentBright, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = submit,
                enabled = state.canSubmit,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IptvColors.Accent),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = IptvColors.TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Text(
                        when (state.mode) {
                            AccountMode.SIGN_IN -> "SE CONNECTER"
                            AccountMode.SIGN_UP -> "CRÉER MON COMPTE"
                            AccountMode.RESET -> "ENVOYER LE LIEN"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            when (state.mode) {
                AccountMode.SIGN_IN -> {
                    TextButton(onClick = { viewModel.setMode(AccountMode.RESET) }) {
                        Text("Mot de passe oublié ?", color = IptvColors.TextSecondary)
                    }
                    TextButton(onClick = { viewModel.setMode(AccountMode.SIGN_UP) }) {
                        Text("Pas encore de compte ? Créer un compte", color = IptvColors.AccentBright)
                    }
                }
                AccountMode.SIGN_UP, AccountMode.RESET -> TextButton(onClick = { viewModel.setMode(AccountMode.SIGN_IN) }) {
                    Text("J'ai déjà un compte : me connecter", color = IptvColors.AccentBright)
                }
            }
        }
    }
}
