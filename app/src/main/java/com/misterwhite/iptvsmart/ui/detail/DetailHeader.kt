package com.misterwhite.iptvsmart.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.misterwhite.iptvsmart.ui.components.RemoteImage
import com.misterwhite.iptvsmart.ui.theme.IptvColors

/** Grande image en fond avec le titre par-dessus, comme une fiche Disney+. */
@Composable
fun DetailHeader(
    title: String,
    backdrop: String?,
    poster: String?,
    meta: String?,
    onBack: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(380.dp),
    ) {
        RemoteImage(backdrop ?: poster, title, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.4f),
                        0.35f to Color.Transparent,
                        1f to IptvColors.Background,
                    )
                ),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (backdrop != null && poster != null) {
                RemoteImage(
                    poster,
                    title,
                    Modifier
                        .width(96.dp)
                        .height(144.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(14.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 3)
                if (!meta.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(meta, color = IptvColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, IptvColors.TextSecondary),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Icon(
            if (isFavorite) Icons.Filled.Check else Icons.Filled.Add,
            contentDescription = null,
            tint = IptvColors.TextPrimary,
        )
        Spacer(Modifier.size(8.dp))
        Text(if (isFavorite) "DANS MA LISTE" else "AJOUTER À MA LISTE", color = IptvColors.TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PlayButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = IptvColors.TextPrimary,
            contentColor = IptvColors.Background,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label : ", color = IptvColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatRating(rating: Double?): String? = rating?.let { "★ " + String.format(java.util.Locale.FRANCE, "%.1f", it) }
