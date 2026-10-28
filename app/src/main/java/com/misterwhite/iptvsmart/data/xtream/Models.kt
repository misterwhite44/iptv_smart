package com.misterwhite.iptvsmart.data.xtream

data class XtreamCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
) {
    fun liveUrl(streamId: String, extension: String = "m3u8") =
        if (DemoCatalog.isDemo(this)) DemoCatalog.streamUrl(streamId)
        else "$serverUrl/live/$username/$password/$streamId.$extension"

    fun movieUrl(streamId: String, extension: String) =
        if (DemoCatalog.isDemo(this)) DemoCatalog.streamUrl(streamId)
        else "$serverUrl/movie/$username/$password/$streamId.$extension"

    fun episodeUrl(episodeId: String, extension: String) =
        if (DemoCatalog.isDemo(this)) DemoCatalog.streamUrl(episodeId)
        else "$serverUrl/series/$username/$password/$episodeId.$extension"
}

data class AccountInfo(
    val status: String,
    /** Date d'expiration en millisecondes, null si illimité. */
    val expiresAtMillis: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val isTrial: Boolean,
    /** Formats proposés par le serveur pour le direct (m3u8, ts…). */
    val allowedOutputFormats: List<String> = emptyList(),
)

data class Category(
    val id: String,
    val name: String,
)

enum class ContentType { LIVE, MOVIE, SERIES }

data class LiveChannel(
    val id: String,
    val name: String,
    val logo: String?,
    val categoryId: String?,
    val epgChannelId: String?,
)

data class Movie(
    val id: String,
    val name: String,
    val poster: String?,
    val rating: Double?,
    val addedAt: Long,
    val categoryId: String?,
    val extension: String,
)

data class Series(
    val id: String,
    val name: String,
    val cover: String?,
    val backdrop: String?,
    val plot: String?,
    val genre: String?,
    val year: String?,
    val rating: Double?,
    val updatedAt: Long,
    val categoryId: String?,
)

data class MovieDetails(
    val movie: Movie,
    val backdrop: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val duration: String?,
    /** Identifiant TMDB, quand le serveur le donne (sert à trouver des sous-titres). */
    val tmdbId: String? = null,
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val extension: String,
    val image: String?,
    val plot: String?,
    val durationSecs: Int?,
)

data class SeriesDetails(
    val series: Series,
    val cast: String?,
    val seasons: Map<Int, List<Episode>>,
    /** Identifiant TMDB de la série, quand le serveur le donne. */
    val tmdbId: String? = null,
)

/** Un type de contenu complet : catégories et éléments. */
data class Catalog<T>(
    val categories: List<Category>,
    val items: List<T>,
) {
    companion object {
        fun <T> empty() = Catalog<T>(emptyList(), emptyList())
    }
}
