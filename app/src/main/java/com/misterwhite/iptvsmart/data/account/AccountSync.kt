package com.misterwhite.iptvsmart.data.account

import android.util.Log
import com.google.android.gms.tasks.Task
import com.misterwhite.iptvsmart.data.CredentialsStore
import com.misterwhite.iptvsmart.data.FavoriteEntry
import com.misterwhite.iptvsmart.data.LibraryRepository
import com.misterwhite.iptvsmart.data.MediaKind
import com.misterwhite.iptvsmart.data.WatchEntry
import com.misterwhite.iptvsmart.data.xtream.XtreamCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Garde sur le compte Zynora les favoris, l'historique (avec les positions de
 * reprise « En cours ») et le service IPTV : tout revient après une déconnexion,
 * une réinstallation ou sur un autre téléphone.
 */
class AccountSync(
    private val account: AccountRepository,
    private val library: LibraryRepository,
    private val credentials: CredentialsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var historyLoadedFor: String? = null
    private var lastSavedHistory: List<WatchEntry>? = null
    private var job: Job? = null
    private var syncedUid: String? = null

    init {
        scope.launch {
            account.user.collect { user -> if (user == null) stop() else start(user.uid) }
        }
    }

    @OptIn(FlowPreview::class)
    private fun start(uid: String) {
        if (syncedUid == uid) return
        stop()
        syncedUid = uid
        job = scope.launch {
            launch {
                try {
                    account.loadFavorites(uid)?.mapNotNull { it.toFavorite() }?.let(library::mergeFavorites)
                } catch (e: Exception) {
                    // Hors ligne : on garde les favoris du téléphone, ils partiront au prochain changement.
                    Log.w(TAG, "Lecture des favoris du compte impossible", e)
                }
                library.favorites
                    .debounce(1_000)
                    .distinctUntilChanged()
                    .collect { favorites ->
                        try {
                            account.saveFavorites(uid, favorites.map { it.toMap() })
                        } catch (e: Exception) {
                            Log.w(TAG, "Sauvegarde des favoris impossible", e)
                        }
                    }
            }
            launch {
                loadHistory(uid)
                // Le lecteur note la position toutes les 5 s : au plus une écriture par minute
                // pendant la lecture, et flush() à la sortie du lecteur pour la position finale.
                // StateFlow ne garde que la dernière valeur pendant le delay.
                library.history.collect {
                    // Hors ligne au départ : on relit le compte avant d'écrire, sinon
                    // l'historique du téléphone écraserait celui du compte.
                    if (historyLoadedFor != uid) loadHistory(uid)
                    saveHistory(uid)
                    delay(HISTORY_SAVE_INTERVAL_MS)
                }
            }
            launch { uploadCredentialsIfMissing(uid) }
        }
    }

    /** Envoie tout de suite l'historique, par exemple quand on quitte le lecteur. */
    fun flush() {
        val uid = syncedUid ?: return
        saveHistory(uid)
    }

    /** Envoie les dernières positions, puis arrête : à appeler avant de se déconnecter. */
    suspend fun flushAndStop() {
        val uid = syncedUid
        job?.cancel()
        val write = uid?.let(::saveHistory)
        stop()
        if (write != null) withTimeoutOrNull(FLUSH_TIMEOUT_MS) { runCatching { write.await() } }
    }

    /** Recharge depuis le compte, par exemple après avoir vidé le téléphone. */
    fun restart() {
        stop()
        account.user.value?.let { start(it.uid) }
    }

    /** À appeler avant d'effacer la bibliothèque locale, pour ne pas vider celle du compte. */
    fun stop() {
        job?.cancel()
        job = null
        syncedUid = null
        historyLoadedFor = null
        lastSavedHistory = null
    }

    /**
     * Remet sur le téléphone le service IPTV enregistré sur le compte, s'il n'y en a
     * pas déjà un. Renvoie vrai si le téléphone a maintenant un service.
     */
    suspend fun restoreCredentials(): Boolean {
        if (credentials.load() != null) return true
        val uid = account.user.value?.uid ?: return false
        val data = try {
            withTimeoutOrNull(RESTORE_TIMEOUT_MS) { account.loadXtream(uid) }
        } catch (e: Exception) {
            Log.w(TAG, "Lecture du service IPTV du compte impossible", e)
            null
        } ?: return false
        val server = data["server"] as? String ?: return false
        val username = data["username"] as? String ?: return false
        val password = data["password"] as? String ?: return false
        credentials.save(XtreamCredentials(server, username, password))
        credentials.setKeepOnAccount(true)
        (data["liveFormat"] as? String)?.let(credentials::restoreLiveFormat)
        return true
    }

    /** Après une connexion réussie au fournisseur : l'enregistre sur le compte, ou l'en retire. */
    fun onCredentialsSaved(keepOnAccount: Boolean) {
        credentials.setKeepOnAccount(keepOnAccount)
        val uid = account.user.value?.uid ?: return
        scope.launch {
            try {
                if (keepOnAccount) uploadCredentials(uid) else account.deleteXtream(uid)
            } catch (e: Exception) {
                Log.w(TAG, "Sauvegarde du service IPTV impossible", e)
            }
        }
    }

    /**
     * « Changer de fournisseur » : l'ancien service et son historique quittent aussi
     * le compte, puis on recharge ce qui reste (les favoris). À appeler après stop().
     */
    fun forgetProviderAndRestart() {
        val uid = account.user.value?.uid ?: return
        scope.launch {
            try {
                withTimeoutOrNull(FLUSH_TIMEOUT_MS) {
                    account.deleteXtream(uid)
                    account.deleteHistory(uid)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Effacement du service IPTV du compte impossible", e)
            }
            restart()
        }
    }

    private suspend fun loadHistory(uid: String) {
        try {
            val remote = account.loadHistory(uid)?.mapNotNull { it.toWatchEntry() }.orEmpty()
            library.mergeHistory(remote)
            if (library.history.value == remote) lastSavedHistory = remote
            historyLoadedFor = uid
        } catch (e: Exception) {
            Log.w(TAG, "Lecture de l'historique du compte impossible", e)
        }
    }

    // Sans attendre le serveur : Firestore garde l'écriture hors ligne et envoie
    // les écritures dans l'ordre, donc la plus récente gagne toujours.
    private fun saveHistory(uid: String): Task<Void>? {
        if (historyLoadedFor != uid) return null
        val history = library.history.value
        if (history == lastSavedHistory) return null
        lastSavedHistory = history
        return account.saveHistory(uid, history.map { it.toMap() })
            .addOnFailureListener { Log.w(TAG, "Sauvegarde de l'historique impossible", it) }
    }

    // Comptes créés avant cette version : le service n'est encore que sur le téléphone.
    private suspend fun uploadCredentialsIfMissing(uid: String) {
        if (credentials.load() == null || !credentials.keepOnAccount()) return
        try {
            if (account.loadXtream(uid) == null) uploadCredentials(uid)
        } catch (e: Exception) {
            Log.w(TAG, "Vérification du service IPTV du compte impossible", e)
        }
    }

    private suspend fun uploadCredentials(uid: String) {
        val c = credentials.load() ?: return
        account.saveXtream(
            uid,
            mapOf(
                "server" to c.serverUrl,
                "username" to c.username,
                "password" to c.password,
                "liveFormat" to credentials.liveFormat(),
            ),
        )
    }

    private fun FavoriteEntry.toMap(): Map<String, Any?> = mapOf(
        "key" to key,
        "kind" to kind.name,
        "contentId" to contentId,
        "title" to title,
        "image" to image,
        "addedAt" to addedAt,
    )

    private fun Map<String, Any?>.toFavorite(): FavoriteEntry? = try {
        FavoriteEntry(
            key = get("key") as String,
            kind = MediaKind.valueOf(get("kind") as String),
            contentId = get("contentId") as String,
            title = get("title") as String,
            image = get("image") as String?,
            addedAt = (get("addedAt") as? Number)?.toLong() ?: 0L,
        )
    } catch (e: Exception) {
        null
    }

    private fun WatchEntry.toMap(): Map<String, Any?> = mapOf(
        "key" to key,
        "kind" to kind.name,
        "contentId" to contentId,
        "seriesId" to seriesId,
        "title" to title,
        "subtitle" to subtitle,
        "image" to image,
        "extension" to extension,
        "positionMs" to positionMs,
        "durationMs" to durationMs,
        "updatedAt" to updatedAt,
    )

    private fun Map<String, Any?>.toWatchEntry(): WatchEntry? = try {
        WatchEntry(
            key = get("key") as String,
            kind = MediaKind.valueOf(get("kind") as String),
            contentId = get("contentId") as String,
            seriesId = get("seriesId") as String?,
            title = get("title") as String,
            subtitle = get("subtitle") as String?,
            image = get("image") as String?,
            extension = get("extension") as String?,
            positionMs = (get("positionMs") as? Number)?.toLong() ?: 0L,
            durationMs = (get("durationMs") as? Number)?.toLong() ?: 0L,
            updatedAt = (get("updatedAt") as? Number)?.toLong() ?: 0L,
        )
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TAG = "AccountSync"
        const val HISTORY_SAVE_INTERVAL_MS = 60_000L
        const val FLUSH_TIMEOUT_MS = 3_000L
        const val RESTORE_TIMEOUT_MS = 8_000L
    }
}
