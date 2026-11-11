package com.misterwhite.iptvsmart.cast

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.misterwhite.iptvsmart.data.LibraryRepository
import com.misterwhite.iptvsmart.data.MediaKind
import com.misterwhite.iptvsmart.data.WatchEntry
import com.misterwhite.iptvsmart.data.account.AccountSync
import com.misterwhite.iptvsmart.data.optStringOrNull
import com.misterwhite.iptvsmart.player.PlayItem
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Note dans l'historique et « En cours » ce que la Chromecast lit, comme pour
 * une lecture sur le téléphone. Il suit la Chromecast pour toute l'appli :
 * la position continue d'être enregistrée quand on quitte le lecteur, qu'on
 * verrouille le téléphone ou qu'on passe par le mini-lecteur.
 */
class CastWatchTracker(
    private val context: Context,
    private val library: LibraryRepository,
    private val accountSync: AccountSync,
) {
    private var client: RemoteMediaClient? = null
    private var lastSavedAt = 0L
    private var lastKey: String? = null
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L

    fun start() {
        val cast = CastSupport.castContext(context) ?: return
        runCatching {
            cast.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
            // Session reprise au lancement de l'appli (la télé lisait déjà).
            cast.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let(::attach)
        }.onFailure { Log.w(TAG, "Suivi de la Chromecast impossible", it) }
    }

    private fun attach(session: CastSession) {
        val next = session.remoteMediaClient ?: return
        if (next === client) return
        detach()
        client = next
        next.registerCallback(mediaCallback)
        next.addProgressListener(progressListener, PROGRESS_INTERVAL_MS)
    }

    private fun detach() {
        client?.let {
            it.unregisterCallback(mediaCallback)
            it.removeProgressListener(progressListener)
        }
        client = null
    }

    private val progressListener = RemoteMediaClient.ProgressListener { progressMs, durationMs ->
        save(progressMs, durationMs, force = false)
    }

    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val c = client ?: return
            val status = c.mediaStatus
            when {
                // Fin du film ou de l'épisode : il sort de « En cours ».
                status?.playerState == MediaStatus.PLAYER_STATE_IDLE &&
                    status?.idleReason == MediaStatus.IDLE_REASON_FINISHED -> markFinished()
                // Pause : on garde la position tout de suite.
                c.isPaused -> save(c.approximateStreamPosition, c.streamDuration, force = true)
            }
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = attach(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attach(session)
        override fun onSessionEnding(session: CastSession) {
            client?.let { save(it.approximateStreamPosition, it.streamDuration, force = true) }
            detach()
            accountSync.flush()
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            detach()
            accountSync.flush()
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            accountSync.flush()
        }
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
    }

    /** Le film ou l'épisode que lit la Chromecast, s'il vient de Zynora. */
    private fun currentTemplate(): WatchEntry? {
        val c = client ?: return null
        val info = c.mediaInfo ?: return null
        if (info.streamType == MediaInfo.STREAM_TYPE_LIVE) return null
        val json = info.customData?.optJSONObject(CUSTOM_DATA_KEY)
            ?: info.contentId?.let { known[it] }
            ?: info.contentUrl?.let { known[it] }
            ?: return null
        return runCatching { fromJson(json) }.getOrNull()
    }

    private fun save(positionMs: Long, durationMs: Long, force: Boolean) {
        val c = client ?: return
        // Pendant le chargement, la position vaut souvent 0 : on ne veut pas
        // écraser la position de reprise avec ça.
        if (!(c.isPlaying || c.isPaused) || positionMs <= 0) return
        val template = currentTemplate() ?: return
        val now = System.currentTimeMillis()
        // Durée pas encore annoncée : on garde celle déjà connue.
        val duration = durationMs.takeIf { it > 0 } ?: library.entry(template.key)?.durationMs ?: 0L
        val sameItem = template.key == lastKey
        lastKey = template.key
        lastPositionMs = positionMs
        lastDurationMs = duration
        if (!force && sameItem && now - lastSavedAt < SAVE_INTERVAL_MS) return
        library.record(template.copy(positionMs = positionMs, durationMs = duration, updatedAt = now))
        lastSavedAt = now
    }

    private fun markFinished() {
        val key = lastKey ?: return
        val entry = library.entry(key) ?: return
        val duration = entry.durationMs.takeIf { it > 0 } ?: lastDurationMs
        // Seulement si on était vraiment près de la fin (pas un arrêt en plein film).
        if (duration <= 0 || lastPositionMs < duration * 0.9) return
        library.record(entry.copy(positionMs = duration, durationMs = duration, updatedAt = System.currentTimeMillis()))
        lastKey = null
        accountSync.flush()
    }

    companion object {
        private const val TAG = "CastWatchTracker"
        private const val PROGRESS_INTERVAL_MS = 5_000L
        private const val SAVE_INTERVAL_MS = 10_000L
        const val CUSTOM_DATA_KEY = "zynora_watch"
        /** Extra du MediaItem qui porte la description du contenu pour l'historique. */
        const val EXTRA_WATCH = "zynora_watch"

        /** Contenus envoyés à la Chromecast, par lien, au cas où la télé ne renvoie pas customData. */
        private val known = ConcurrentHashMap<String, JSONObject>()

        fun remember(url: String, json: JSONObject) {
            known[url] = json
        }

        /** Ce qu'il faut pour noter cet élément dans l'historique. */
        fun describe(item: PlayItem): String = JSONObject()
            .put("key", item.key).put("kind", item.kind.name).put("contentId", item.contentId)
            .putOpt("seriesId", item.seriesId).put("title", item.title).putOpt("subtitle", item.subtitle)
            .putOpt("image", item.image).putOpt("extension", item.extension)
            .toString()

        private fun fromJson(o: JSONObject) = WatchEntry(
            key = o.getString("key"),
            kind = MediaKind.valueOf(o.getString("kind")),
            contentId = o.getString("contentId"),
            seriesId = o.optStringOrNull("seriesId"),
            title = o.getString("title"),
            subtitle = o.optStringOrNull("subtitle"),
            image = o.optStringOrNull("image"),
            extension = o.optStringOrNull("extension"),
            positionMs = 0,
            durationMs = 0,
            updatedAt = 0,
        )
    }
}
