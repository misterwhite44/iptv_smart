package com.misterwhite.iptvsmart.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misterwhite.iptvsmart.ui.components.ZynoraLogo
import com.misterwhite.iptvsmart.ui.theme.IptvColors

@Composable
fun LoginScreen(viewModel: LoginViewModel, onLoggedIn: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var showPassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
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
            ZynoraLogo(72.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Zynora",
                style = MaterialTheme.typography.headlineLarge,
                color = IptvColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Connecte-toi avec les identifiants Xtream de ton fournisseur",
                style = MaterialTheme.typography.bodyMedium,
                color = IptvColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            LoginField(
                value = state.serverUrl,
                onValueChange = viewModel::onServerUrlChange,
                label = "URL du serveur",
                placeholder = "http://exemple.com:8080",
                keyboardType = KeyboardType.Uri,
                onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
            )
            Spacer(Modifier.height(14.dp))
            LoginField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = "Identifiant",
                leading = { Icon(Icons.Filled.Person, contentDescription = null) },
                onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
            )
            Spacer(Modifier.height(14.dp))
            LoginField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Mot de passe",
                leading = { Icon(Icons.Filled.Lock, contentDescription = null) },
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Masquer" else "Afficher", color = IptvColors.TextSecondary)
                    }
                },
                onDone = {
                    focusManager.clearFocus()
                    viewModel.submit()
                },
            )

            if (state.error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.submit()
                },
                enabled = state.canSubmit,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IptvColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = IptvColors.TextPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text("SE CONNECTER", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Astuce : tu peux aussi coller ton lien M3U complet dans le champ URL.",
                style = MaterialTheme.typography.bodyMedium,
                color = IptvColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Zynora ne fournit aucune chaîne ni aucun contenu. Utilise uniquement un service auquel tu as légalement accès.",
                style = MaterialTheme.typography.bodySmall,
                color = IptvColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onNext: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        leadingIcon = leading,
        trailingIcon = trailing,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
        shape = RoundedCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = IptvColors.Accent,
            unfocusedBorderColor = IptvColors.SurfaceHigh,
            focusedContainerColor = IptvColors.Surface,
            unfocusedContainerColor = IptvColors.Surface,
            focusedLabelColor = IptvColors.AccentBright,
            cursorColor = IptvColors.AccentBright,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
