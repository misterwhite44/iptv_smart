package com.misterwhite.iptvsmart.data.account

import android.util.Log
import com.misterwhite.iptvsmart.data.FavoriteEntry
import com.misterwhite.iptvsmart.data.LibraryRepository
import com.misterwhite.iptvsmart.data.MediaKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Garde les favoris sur le compte : ils reviennent après une déconnexion,
 * une réinstallation ou sur un autre téléphone.
 */
class FavoritesSync(
    private val account: AccountRepository,
    private val library: LibraryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
    }

    /** Recharge les favoris du compte, par exemple après avoir vidé ceux du téléphone. */
    fun restart() {
        stop()
        account.user.value?.let { start(it.uid) }
    }

    /** À appeler avant d'effacer la bibliothèque locale, pour ne pas vider celle du compte. */
    fun stop() {
        job?.cancel()
        job = null
        syncedUid = null
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

    private companion object {
        const val TAG = "FavoritesSync"
    }
}
