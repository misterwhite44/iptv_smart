package com.misterwhite.iptvsmart.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.misterwhite.iptvsmart.data.xtream.Category
import com.misterwhite.iptvsmart.ui.theme.IptvColors

private val CardShape = RoundedCornerShape(8.dp)

/** Carte qui rétrécit légèrement quand on appuie dessus, comme sur les plateformes de streaming. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "cardScale")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CardShape)
            .background(IptvColors.Surface)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CardShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) { content() }
}

/** Image distante avec un texte de secours quand elle manque ou ne charge pas. */
@Composable
fun RemoteImage(
    url: String?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val fallback = @Composable {
        Box(
            Modifier
                .fillMaxSize()
                .background(IptvColors.SurfaceHigh)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                fallbackText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (url.isNullOrBlank()) {
        Box(modifier) { fallback() }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = fallbackText,
            contentScale = contentScale,
            loading = { Box(Modifier.fillMaxSize().background(IptvColors.SurfaceHigh)) },
            error = { fallback() },
            modifier = modifier,
        )
    }
}

@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 112.dp,
    progress: Float? = null,
) {
    PressableCard(
        onClick = onClick,
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .aspectRatio(2f / 3f),
    ) {
        RemoteImage(imageUrl, title, Modifier.fillMaxSize())
        if (progress != null) ProgressBar(progress, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun ChannelCard(
    name: String,
    logoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 160.dp,
) {
    Column(modifier = if (width != null) modifier.width(width) else modifier.fillMaxWidth()) {
        PressableCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            RemoteImage(
                url = logoUrl,
                fallbackText = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = IptvColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Carte large 16:9 avec barre de progression. */
@Composable
fun WideCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(220.dp)) {
        PressableCard(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            RemoteImage(imageUrl, title, Modifier.fillMaxSize())
            if (progress != null) ProgressBar(progress, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = IptvColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.Black.copy(alpha = 0.5f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .background(IptvColors.Accent),
        )
    }
}

/** Une rangée horizontale avec titre, comme sur les accueils de streaming. */
@Composable
fun <T> ContentRow(
    title: String,
    entries: List<T>,
    key: (T) -> Any,
    itemContent: @Composable (T) -> Unit,
) {
    if (entries.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries, key = key) { itemContent(it) }
        }
    }
}

@Composable
fun CategoryChips(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    allLabel: String = "Tout",
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "__all") {
            Chip(allLabel, selectedId == null) { onSelect(null) }
        }
        items(categories, key = { it.id }) { category ->
            Chip(category.name, selectedId == category.id) { onSelect(category.id) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = IptvColors.Surface,
            labelColor = IptvColors.TextSecondary,
            selectedContainerColor = IptvColors.TextPrimary,
            selectedLabelColor = IptvColors.Background,
        ),
        border = null,
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, message: String? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = IptvColors.Accent)
            if (message != null) {
                Spacer(Modifier.height(16.dp))
                Text(message, color = IptvColors.TextSecondary)
            }
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Réessayer") }
        }
    }
}

@Composable
fun EmptyBox(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = IptvColors.TextSecondary, textAlign = TextAlign.Center)
    }
}
