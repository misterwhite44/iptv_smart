package com.misterwhite.iptvsmart.data

import com.misterwhite.iptvsmart.data.xtream.Catalog
import com.misterwhite.iptvsmart.data.xtream.ContentType
import com.misterwhite.iptvsmart.data.xtream.LiveChannel
import com.misterwhite.iptvsmart.data.xtream.Movie
import com.misterwhite.iptvsmart.data.xtream.MovieDetails
import com.misterwhite.iptvsmart.data.xtream.Series
import com.misterwhite.iptvsmart.data.xtream.SeriesDetails
import com.misterwhite.iptvsmart.data.xtream.XtreamClient
import com.misterwhite.iptvsmart.data.xtream.XtreamCredentials
import com.misterwhite.iptvsmart.data.xtream.XtreamException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Charge le catalogue Xtream une fois par session et le garde en mémoire,
 * pour que la navigation entre les onglets reste instantanée.
 */
class CatalogRepository(
    private val client: XtreamClient,
    private val store: CredentialsStore,
) {
    data class Content(
        val live: Catalog<LiveChannel>,
        val movies: Catalog<Movie>,
        val series: Catalog<Series>,
    )

    private val mutex = Mutex()
    private var cached: Content? = null
    private val movieDetailsCache = mutableMapOf<String, MovieDetails>()
    private val seriesDetailsCache = mutableMapOf<String, SeriesDetails>()

    fun credentials(): XtreamCredentials =
        store.load() ?: throw XtreamException("Session expirée, reconnecte-toi.")

    suspend fun content(forceRefresh: Boolean = false): Content = try {
        loadContent(forceRefresh)
    } catch (e: OutOfMemoryError) {
        throw XtreamException("Le catalogue est trop gros pour la mémoire du téléphone.", e)
    }

    private suspend fun loadContent(forceRefresh: Boolean): Content = mutex.withLock {
        if (!forceRefresh) cached?.let { return@withLock it }
        val c = credentials()
        coroutineScope {
            val liveCats = async { client.categories(c, ContentType.LIVE) }
            val live = async { client.liveChannels(c) }
            val movieCats = async { client.categories(c, ContentType.MOVIE) }
            val movies = async { client.movies(c) }
            val seriesCats = async { client.categories(c, ContentType.SERIES) }
            val series = async { client.series(c) }
            Content(
                // Certains panneaux listent un même élément plusieurs fois.
                live = Catalog(liveCats.await().distinctBy { it.id }, live.await().distinctBy { it.id }),
                movies = Catalog(movieCats.await().distinctBy { it.id }, movies.await().distinctBy { it.id }),
                series = Catalog(seriesCats.await().distinctBy { it.id }, series.await().distinctBy { it.id }),
            )
        }.also { cached = it }
    }

    fun cachedContent(): Content? = cached

    fun findMovie(id: String): Movie? = cached?.movies?.items?.firstOrNull { it.id == id }

    fun findSeries(id: String): Series? = cached?.series?.items?.firstOrNull { it.id == id }

    fun findChannel(id: String): LiveChannel? = cached?.live?.items?.firstOrNull { it.id == id }

    suspend fun movieDetails(id: String): MovieDetails {
        movieDetailsCache[id]?.let { return it }
        val movie = findMovie(id) ?: content().movies.items.firstOrNull { it.id == id }
            ?: throw XtreamException("Film introuvable.")
        return client.movieDetails(credentials(), movie).also { movieDetailsCache[id] = it }
    }

    suspend fun seriesDetails(id: String): SeriesDetails {
        seriesDetailsCache[id]?.let { return it }
        val series = findSeries(id) ?: content().series.items.firstOrNull { it.id == id }
            ?: throw XtreamException("Série introuvable.")
        return client.seriesDetails(credentials(), series).also { seriesDetailsCache[id] = it }
    }

    fun clear() {
        cached = null
        movieDetailsCache.clear()
        seriesDetailsCache.clear()
    }
}
