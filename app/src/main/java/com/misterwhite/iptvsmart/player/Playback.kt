package com.misterwhite.iptvsmart.player

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.misterwhite.iptvsmart.AppContainer
import com.misterwhite.iptvsmart.data.LibraryRepository
import com.misterwhite.iptvsmart.data.MediaKind
import com.misterwhite.iptvsmart.data.WatchEntry
import com.misterwhite.iptvsmart.data.xtream.Episode
import com.misterwhite.iptvsmart.data.xtream.MovieDetails
import com.misterwhite.iptvsmart.data.xtream.SeriesDetails
import com.misterwhite.iptvsmart.data.xtream.XtreamCredentials

/** Prépare la file de lecture et ouvre le lecteur. */
class Playback(private val context: Context, private val container: AppContainer) {

    private val library get() = container.library

    private fun credentials(): XtreamCredentials? = container.credentialsStore.load()

    /** Ouvre une chaîne ; les autres chaînes de sa catégorie suivent pour zapper. */
    fun playChannel(channelId: String) {
        val c = credentials() ?: return
        val live = container.catalog.cachedContent()?.live ?: return toast("Catalogue pas encore chargé.")
        val channel = live.items.firstOrNull { it.id == channelId } ?: return toast("Chaîne introuvable.")
        val categoryName = live.categories.firstOrNull { it.id == channel.categoryId }?.name
        val group = live.items.filter { it.categoryId == channel.categoryId }.ifEmpty { listOf(channel) }
        val format = container.credentialsStore.liveFormat()
        val queue = group.map { ch ->
            PlayItem(
                key = LibraryRepository.liveKey(ch.id),
                kind = MediaKind.LIVE,
                contentId = ch.id,
                seriesId = null,
                title = ch.name,
                subtitle = categoryName,
                image = ch.logo,
                url = c.liveUrl(ch.id, format),
                fallbackUrl = if (format == "m3u8") c.liveUrl(ch.id, "ts") else null,
                extension = format,
            )
        }
        start(queue, group.indexOfFirst { it.id == channel.id }.coerceAtLeast(0))
    }

    fun playMovie(details: MovieDetails) {
        val c = credentials() ?: return
        val movie = details.movie
        val key = LibraryRepository.movieKey(movie.id)
        val resume = library.entry(key)?.takeIf { it.isResumable }?.positionMs ?: 0
        start(
            listOf(
                PlayItem(
                    key = key,
                    kind = MediaKind.MOVIE,
                    contentId = movie.id,
                    seriesId = null,
                    title = movie.name,
                    subtitle = listOfNotNull(details.releaseDate?.take(4), details.genre).joinToString(" • ").ifBlank { null },
                    image = details.backdrop ?: movie.poster,
                    url = c.movieUrl(movie.id, movie.extension),
                    extension = movie.extension,
                    startPositionMs = resume,
                )
            ),
            0,
        )
    }

    /** Lit un épisode, suivi des épisodes suivants de la série. */
    fun playEpisode(details: SeriesDetails, episode: Episode) {
        val c = credentials() ?: return
        val all = details.seasons.values.flatten()
        val startAt = all.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
        val queue = all.drop(startAt).take(MAX_EPISODES).map { ep ->
            val key = LibraryRepository.episodeKey(ep.id)
            PlayItem(
                key = key,
                kind = MediaKind.EPISODE,
                contentId = ep.id,
                seriesId = details.series.id,
                title = details.series.name,
                subtitle = "S${ep.season} É${ep.number} • ${ep.title}",
                image = ep.image ?: details.series.backdrop ?: details.series.cover,
                url = c.episodeUrl(ep.id, ep.extension),
                extension = ep.extension,
                startPositionMs = if (ep.id == episode.id) {
                    library.entry(key)?.takeIf { it.isResumable }?.positionMs ?: 0
                } else 0,
            )
        }
        start(queue, 0)
    }

    /** Reprend un film depuis l'historique. */
    fun resumeMovie(entry: WatchEntry) {
        val c = credentials() ?: return
        val extension = entry.extension ?: "mp4"
        start(
            listOf(
                PlayItem(
                    key = entry.key,
                    kind = MediaKind.MOVIE,
                    contentId = entry.contentId,
                    seriesId = null,
                    title = entry.title,
                    subtitle = entry.subtitle,
                    image = entry.image,
                    url = c.movieUrl(entry.contentId, extension),
                    extension = extension,
                    startPositionMs = entry.positionMs,
                )
            ),
            0,
        )
    }

    private fun start(queue: List<PlayItem>, index: Int) {
        if (queue.isEmpty()) return
        PlayQueue.set(queue, index)
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val MAX_EPISODES = 60
    }
}
