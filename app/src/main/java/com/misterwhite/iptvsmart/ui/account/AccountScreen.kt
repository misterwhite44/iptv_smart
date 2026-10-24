package com.misterwhite.iptvsmart.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misterwhite.iptvsmart.data.xtream.AccountInfo
import com.misterwhite.iptvsmart.ui.theme.IptvColors
import java.text.DateFormat
import java.util.Date

/** Infos de l'abonnement et déconnexion. */
@Composable
fun AccountScreen(viewModel: AccountViewModel, onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IptvColors.screenGradient)
            .systemBarsPadding(),
    ) {
        when (val s = state) {
            AccountUiState.Loading -> CircularProgressIndicator(
                color = IptvColors.Accent,
                modifier = Modifier.align(Alignment.Center),
            )
            is AccountUiState.Error -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = viewModel::refresh) { Text("Réessayer") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.logout(); onLoggedOut() }) { Text("Se déconnecter") }
            }
            is AccountUiState.Ready -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                    Text("Mon compte", style = MaterialTheme.typography.headlineLarge)
                }
                AccountCard(s.account)
                Text("Catalogue", style = MaterialTheme.typography.titleMedium)
                CountCard("Chaînes en direct", s.liveCategories)
                CountCard("Films", s.movieCategories)
                CountCard("Séries", s.seriesCategories)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.logout(); onLoggedOut() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Se déconnecter") }
            }
        }
    }
}

@Composable
private fun AccountCard(account: AccountInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IptvColors.Surface, RoundedCornerShape(10.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Mon abonnement", style = MaterialTheme.typography.titleMedium)
        InfoRow("Statut", if (account.isTrial) "${account.status} (essai)" else account.status)
        InfoRow(
            "Expire le",
            account.expiresAtMillis?.let { DateFormat.getDateInstance(DateFormat.LONG).format(Date(it)) } ?: "Illimité",
        )
        account.maxConnections?.let { InfoRow("Connexions max", it.toString()) }
    }
}

@Composable
private fun CountCard(title: String, categories: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IptvColors.Surface, RoundedCornerShape(10.dp))
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text("$categories catégories", color = IptvColors.TextSecondary)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = IptvColors.TextSecondary)
        Text(value)
    }
}
