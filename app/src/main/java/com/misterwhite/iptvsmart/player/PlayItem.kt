package com.misterwhite.iptvsmart.player

import com.misterwhite.iptvsmart.data.MediaKind

/** Un élément à lire, avec ce qu'il faut pour l'historique. */
data class PlayItem(
    val key: String,
    val kind: MediaKind,
    val contentId: String,
    val seriesId: String?,
    val title: String,
    val subtitle: String?,
    val image: String?,
    val url: String,
    /** URL de secours (.ts) si le flux HLS d'une chaîne ne passe pas. */
    val fallbackUrl: String? = null,
    val extension: String?,
    val startPositionMs: Long = 0,
    /** De quoi chercher des sous-titres externes (films et épisodes). */
    val subtitleSearch: SubtitleSearch? = null,
)

/** Ce qu'on sait du contenu pour chercher ses sous-titres sur OpenSubtitles. */
data class SubtitleSearch(
    val title: String,
    val year: String? = null,
    /** TMDB du film, ou de la série pour un épisode. */
    val tmdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

/**
 * File de lecture transmise au lecteur. Elle reste en mémoire plutôt que dans
 * l'Intent, car la liste des chaînes d'une catégorie peut être longue.
 */
object PlayQueue {
    @Volatile
    var items: List<PlayItem> = emptyList()
        private set

    @Volatile
    var startIndex: Int = 0
        private set

    fun set(items: List<PlayItem>, startIndex: Int) {
        this.items = items
        this.startIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }
}
