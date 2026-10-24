package com.misterwhite.iptvsmart.ui.premium

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misterwhite.iptvsmart.cast.CastButton
import com.misterwhite.iptvsmart.data.billing.PremiumPlan
import com.misterwhite.iptvsmart.data.billing.PremiumRepository
import com.misterwhite.iptvsmart.data.billing.PremiumSource
import com.misterwhite.iptvsmart.ui.theme.IptvColors

/** Accès Premium pour tous les écrans ; null dans les aperçus (tout est alors débloqué). */
val LocalPremium = staticCompositionLocalOf<PremiumRepository?> { null }

@Composable
fun rememberIsPremium(): Boolean {
    val premium = LocalPremium.current ?: return true
    val source by premium.source.collectAsStateWithLifecycle()
    return source != PremiumSource.NONE
}

/** Bouton Chromecast réservé à Premium : en gratuit, il ouvre l'offre. */
@Composable
fun PremiumCastButton(modifier: Modifier = Modifier) {
    if (rememberIsPremium()) {
        CastButton(modifier)
        return
    }
    var showOffer by remember { mutableStateOf(false) }
    IconButton(onClick = { showOffer = true }, modifier = modifier) {
        Icon(Icons.Filled.Cast, contentDescription = "Caster (Premium)", tint = IptvColors.TextPrimary)
    }
    if (showOffer) PremiumDialog(onDismiss = { showOffer = false }, reason = "Le cast sur Chromecast fait partie de Premium.")
}

/** L'offre Premium : 4 € par mois ou 30 € à vie, achetés via Google Play. */
@Composable
fun PremiumDialog(onDismiss: () -> Unit, reason: String? = null) {
    val premium = LocalPremium.current ?: return
    val context = LocalContext.current
    val offers by premium.billing.offers.collectAsStateWithLifecycle()
    val error by premium.billing.error.collectAsStateWithLifecycle()
    val source by premium.source.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { premium.billing.refresh() }
    LaunchedEffect(source) { if (source != PremiumSource.NONE) onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = IptvColors.Surface,
        icon = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = IptvColors.AccentBright, modifier = Modifier.size(40.dp)) },
        title = { Text("Zynora Premium", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reason?.let { Text(it, color = IptvColors.TextSecondary) }
                Benefit("Cast sur ta Chromecast")
                Benefit("Toutes les prochaines fonctions Premium")
                Benefit("Soutenir le développement de Zynora")
                Spacer(Modifier.height(8.dp))
                if (offers.isEmpty()) {
                    Text("Les offres ne sont pas encore disponibles. Réessaie plus tard.", color = IptvColors.TextSecondary)
                }
                offers.forEach { offer ->
                    Button(
                        onClick = { context.findActivity()?.let { premium.billing.launchPurchase(it, offer) } },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IptvColors.Accent),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(
                            when (offer.plan) {
                                PremiumPlan.MONTHLY -> "${offer.price} par mois"
                                PremiumPlan.LIFETIME -> "${offer.price} à vie (une seule fois)"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (offers.any { it.plan == PremiumPlan.MONTHLY }) {
                    Text(
                        "L'abonnement se renouvelle chaque mois. Résiliable à tout moment dans Google Play.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IptvColors.TextSecondary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Plus tard") } },
        dismissButton = { TextButton(onClick = { premium.billing.refresh() }) { Text("Restaurer mes achats") } },
    )
}

@Composable
private fun Benefit(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = IptvColors.AccentBright, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text)
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
