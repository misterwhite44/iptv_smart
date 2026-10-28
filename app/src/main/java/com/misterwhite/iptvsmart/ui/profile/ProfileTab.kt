package com.misterwhite.iptvsmart.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misterwhite.iptvsmart.player.SubtitlePrefs
import com.misterwhite.iptvsmart.BuildConfig
import com.misterwhite.iptvsmart.data.CatalogRepository
import com.misterwhite.iptvsmart.data.MediaKind
import com.misterwhite.iptvsmart.data.WatchEntry
import com.misterwhite.iptvsmart.data.account.Legal
import com.misterwhite.iptvsmart.data.billing.PremiumPlan
import com.misterwhite.iptvsmart.data.billing.PremiumSource
import com.misterwhite.iptvsmart.data.xtream.AccountInfo
import com.misterwhite.iptvsmart.ui.components.ContentRow
import com.misterwhite.iptvsmart.ui.components.PosterCard
import com.misterwhite.iptvsmart.ui.components.ProgressBar
import com.misterwhite.iptvsmart.ui.components.RemoteImage
import com.misterwhite.iptvsmart.ui.main.LibraryState
import com.misterwhite.iptvsmart.ui.main.MainActions
import com.misterwhite.iptvsmart.ui.premium.LocalPremium
import com.misterwhite.iptvsmart.ui.premium.PremiumDialog
import com.misterwhite.iptvsmart.ui.theme.IptvColors
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun ProfileTab(
    viewModel: ProfileViewModel,
    content: CatalogRepository.Content,
    library: LibraryState,
    actions: MainActions,
    onRefreshCatalog: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadAccountOnce() }
    var confirmLogout by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val premiumSource = LocalPremium.current?.source?.collectAsStateWithLifecycle()?.value
    var showPremium by remember { mutableStateOf(false) }
    val activePurchase = LocalPremium.current?.billing?.activePurchase?.collectAsStateWithLifecycle()?.value
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val accountBusy by viewModel.accountBusy.collectAsStateWithLifecycle()
    val accountError by viewModel.accountError.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val watched = library.history.filter { it.kind != MediaKind.LIVE }
    val myList = library.favorites.filter { it.kind != MediaKind.LIVE }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") { Header(viewModel.username, viewModel.server) }

        item(key = "account") {
            Section("Mon abonnement") {
                when (val s = account) {
                    AccountState.Loading -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = IptvColors.Accent, modifier = Modifier.size(28.dp))
                    }
                    is AccountState.Error -> Column {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::refreshAccount) { Text("Réessayer") }
                    }
                    is AccountState.Ready -> AccountDetails(s.account)
                }
            }
        }

        item(key = "stats") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile("Films vus", library.history.count { it.kind == MediaKind.MOVIE }, Modifier.weight(1f))
                StatTile("Épisodes", library.history.count { it.kind == MediaKind.EPISODE }, Modifier.weight(1f))
                StatTile("Chaînes", library.history.count { it.kind == MediaKind.LIVE }, Modifier.weight(1f))
            }
        }

        item(key = "myList") {
            if (myList.isEmpty()) {
                Section("Ma liste") {
                    Text(
                        "Ajoute des films et des séries avec « Ajouter à ma liste » sur leur fiche.",
                        color = IptvColors.TextSecondary,
                    )
                }
            } else {
                ContentRow("Ma liste (${myList.size})", myList, key = { it.key }) { fav ->
                    PosterCard(fav.title, fav.image, onClick = {
                        if (fav.kind == MediaKind.SERIES) actions.openSeries(fav.contentId) else actions.openMovie(fav.contentId)
                    })
                }
            }
        }

        item(key = "historyTitle") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Historique", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (library.history.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Effacer")
                    }
                }
            }
        }
        if (watched.isEmpty()) {
            item(key = "historyEmpty") {
                Text(
                    "Les films et épisodes que tu regardes apparaîtront ici.",
                    color = IptvColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            items(watched.take(15), key = { "h" + it.key }) { entry ->
                HistoryRow(entry, onClick = { actions.resume(entry) })
            }
        }

        item(key = "playback") {
            val context = LocalContext.current
            var autoFrench by remember { mutableStateOf(SubtitlePrefs.autoFrench(context)) }
            Section("Lecture") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sous-titres français automatiques", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Sur les films et séries en VO, active les sous-titres français du fichier quand il en a.",
                            color = IptvColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = autoFrench,
                        onCheckedChange = {
                            autoFrench = it
                            SubtitlePrefs.setAutoFrench(context, it)
                        },
                    )
                }
            }
        }

        item(key = "catalog") {
            Section("Catalogue") {
                InfoRow("Chaînes en direct", content.live.items.size.toString())
                InfoRow("Films", content.movies.items.size.toString())
                InfoRow("Séries", content.series.items.size.toString())
                Spacer(Modifier.height(4.dp))
                ActionRow(Icons.Filled.Refresh, "Actualiser le catalogue", onRefreshCatalog)
            }
        }

        item(key = "logout") {
            Section(null) {
                ActionRow(Icons.AutoMirrored.Filled.Logout, "Changer de fournisseur", { confirmLogout = true })
            }
        }

        item(key = "premium") {
            Section("Zynora Premium") {
                InfoRow(
                    "Statut",
                    when (premiumSource) {
                        PremiumSource.VIP -> "Actif (offert)"
                        PremiumSource.PLAY -> "Actif"
                        PremiumSource.NONE, null -> "Gratuit"
                    },
                )
                if (premiumSource == PremiumSource.PLAY) {
                    activePurchase?.let { purchase ->
                        InfoRow("Formule", if (purchase.plan == PremiumPlan.LIFETIME) "À vie" else "Mensuelle")
                        if (purchase.plan == PremiumPlan.MONTHLY) {
                            InfoRow(
                                "Renouvellement",
                                if (purchase.autoRenewing) "Automatique chaque mois" else "Résilié : actif jusqu'à la fin du mois payé",
                            )
                        }
                        InfoRow("Depuis le", DateFormat.getDateInstance(DateFormat.LONG).format(Date(purchase.purchaseTime)))
                        if (purchase.plan == PremiumPlan.MONTHLY) {
                            Spacer(Modifier.height(4.dp))
                            ActionRow(Icons.Filled.Settings, "Gérer mon abonnement", {
                                uriHandler.openUri(
                                    "https://play.google.com/store/account/subscriptions" +
                                        "?sku=${PremiumPlan.MONTHLY.productId}&package=${BuildConfig.APPLICATION_ID}"
                                )
                            })
                        }
                    }
                }
                if (premiumSource == PremiumSource.NONE) {
                    Spacer(Modifier.height(4.dp))
                    ActionRow(Icons.Filled.WorkspacePremium, "Passer à Premium", { showPremium = true }, IptvColors.AccentBright)
                }
            }
        }

        viewModel.accountEmail?.let { email ->
            item(key = "zynoraAccount") {
                Section("Mon compte Zynora") {
                    InfoRow("Compte", email)
                    Spacer(Modifier.height(4.dp))
                    ActionRow(Icons.AutoMirrored.Filled.Logout, "Se déconnecter", { confirmSignOut = true }, IptvColors.Error)
                    if (accountBusy) {
                        CircularProgressIndicator(color = IptvColors.Accent, modifier = Modifier.size(28.dp).padding(4.dp))
                    } else {
                        ActionRow(Icons.Filled.DeleteOutline, "Supprimer mon compte", { confirmDelete = true }, IptvColors.Error)
                    }
                    accountError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        item(key = "legal") {
            Section(null) {
                ActionRow(Icons.Filled.Policy, "Politique de confidentialité", { uriHandler.openUri(Legal.PRIVACY_POLICY_URL) })
            }
        }

        item(key = "version") {
            Text(
                "Zynora ${BuildConfig.VERSION_NAME}",
                color = IptvColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Changer de fournisseur ?",
            text = "Ton service et ton historique seront effacés de ce téléphone et de ton compte. Ta liste est conservée.",
            confirm = "Continuer",
            onConfirm = {
                confirmLogout = false
                viewModel.changeProvider()
                onLoggedOut()
            },
            onDismiss = { confirmLogout = false },
        )
    }
    if (showPremium) PremiumDialog(onDismiss = { showPremium = false })
    if (confirmSignOut) {
        ConfirmDialog(
            title = "Se déconnecter ?",
            text = "Tout sera effacé de ce téléphone. Ton service, ton historique et ta liste restent sur ton compte et reviendront à la prochaine connexion.",
            confirm = "Se déconnecter",
            onConfirm = {
                confirmSignOut = false
                viewModel.signOutAccount(onDone = onLoggedOut)
            },
            onDismiss = { confirmSignOut = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Supprimer ton compte ?",
            text = "Ton compte Zynora et toutes ses données seront supprimés définitivement.",
            confirm = "Supprimer",
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAccount(onDeleted = onLoggedOut)
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Effacer l'historique ?",
            text = "« En cours » sera vidé. Ta liste est conservée.",
            confirm = "Effacer",
            onConfirm = {
                confirmClear = false
                viewModel.clearHistory()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun Header(username: String, server: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(IptvColors.BackgroundTop, IptvColors.Background)))
            .statusBarsPadding()
            .padding(top = 24.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(IptvColors.Accent, IptvColors.AccentBright))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    username.firstOrNull()?.uppercase() ?: "Z",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(username.ifBlank { "Mon profil" }, style = MaterialTheme.typography.headlineSmall)
            Text(
                server.removePrefix("http://").removePrefix("https://"),
                color = IptvColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AccountDetails(account: AccountInfo) {
    val active = account.status.equals("Active", ignoreCase = true)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Statut", color = IptvColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(
            when {
                active && account.isTrial -> "Actif (essai)"
                active -> "Actif"
                else -> account.status
            },
            color = if (active) Color(0xFF4ADE80) else IptvColors.Error,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background((if (active) Color(0xFF4ADE80) else IptvColors.Error).copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
    Spacer(Modifier.height(6.dp))
    val expiry = account.expiresAtMillis
    InfoRow(
        "Expire le",
        expiry?.let { DateFormat.getDateInstance(DateFormat.LONG).format(Date(it)) } ?: "Illimité",
    )
    if (expiry != null) {
        val days = TimeUnit.MILLISECONDS.toDays(expiry - System.currentTimeMillis())
        InfoRow("Jours restants", if (days >= 0) days.toString() else "Expiré")
    }
    if (account.maxConnections != null) {
        InfoRow(
            "Connexions",
            account.activeConnections?.let { "$it / ${account.maxConnections}" } ?: "${account.maxConnections} max",
        )
    }
}

@Composable
private fun Section(title: String?, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(IptvColors.Surface, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(IptvColors.Surface, RoundedCornerShape(12.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = IptvColors.AccentBright)
        Text(label, color = IptvColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HistoryRow(entry: WatchEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            RemoteImage(entry.image, entry.title, Modifier.fillMaxSize())
            entry.progress?.let { ProgressBar(it, Modifier.align(Alignment.BottomCenter)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.subtitle?.let {
                Text(it, color = IptvColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                if (entry.isFinished) "Terminé" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.updatedAt)),
                color = IptvColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = IptvColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit, color: Color = IptvColors.TextPrimary) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(12.dp))
        Text(label, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        containerColor = IptvColors.Surface,
    )
}
