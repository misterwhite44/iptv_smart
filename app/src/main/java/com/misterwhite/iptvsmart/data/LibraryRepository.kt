package com.misterwhite.iptvsmart.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class MediaKind { LIVE, MOVIE, EPISODE, SERIES }

/** Une lecture en cours ou passée, pour l'historique et « Reprendre la lecture ». */
data class WatchEntry(
    val key: String,
    val kind: MediaKind,
    val contentId: String,
    val seriesId: String?,
    val title: String,
    val subtitle: String?,
    val image: String?,
    val extension: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val progress: Float?
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null

    val isFinished: Boolean
        get() = durationMs > 0 && positionMs >= durationMs * 0.95

    /** Assez avancé pour être proposé à la reprise. */
    val isResumable: Boolean
        get() = kind != MediaKind.LIVE && !isFinished && positionMs > 15_000
}

data class FavoriteEntry(
    val key: String,
    val kind: MediaKind,
    val contentId: String,
    val title: String,
    val image: String?,
    val addedAt: Long,
)

/** Historique et favoris, gardés sur le téléphone. */
class LibraryRepository(context: Context) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(readHistory())
    val history: StateFlow<List<WatchEntry>> = _history.asStateFlow()

    private val _favorites = MutableStateFlow(readFavorites())
    val favorites: StateFlow<List<FavoriteEntry>> = _favorites.asStateFlow()

    fun entry(key: String): WatchEntry? = _history.value.firstOrNull { it.key == key }

    @Synchronized
    fun record(entry: WatchEntry) {
        val updated = (listOf(entry) + _history.value.filter { it.key != entry.key }).take(MAX_HISTORY)
        _history.value = updated
        prefs.edit().putString(KEY_HISTORY, JSONArray(updated.map { it.toJson() }).toString()).apply()
    }

    @Synchronized
    fun removeFromHistory(key: String) {
        val updated = _history.value.filter { it.key != key }
        _history.value = updated
        prefs.edit().putString(KEY_HISTORY, JSONArray(updated.map { it.toJson() }).toString()).apply()
    }

    fun isFavorite(key: String): Boolean = _favorites.value.any { it.key == key }

    /** Ajoute ou retire un favori. Renvoie true s'il est maintenant en favori. */
    @Synchronized
    fun toggleFavorite(entry: FavoriteEntry): Boolean {
        val exists = isFavorite(entry.key)
        val updated = if (exists) _favorites.value.filter { it.key != entry.key } else listOf(entry) + _favorites.value
        _favorites.value = updated
        prefs.edit().putString(KEY_FAVORITES, JSONArray(updated.map { it.toJson() }).toString()).apply()
        return !exists
    }

    @Synchronized
    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    fun clear() {
        _history.value = emptyList()
        _favorites.value = emptyList()
        prefs.edit().clear().apply()
    }

    private fun readHistory(): List<WatchEntry> = readArray(KEY_HISTORY) { o ->
        WatchEntry(
            key = o.getString("key"),
            kind = MediaKind.valueOf(o.getString("kind")),
            contentId = o.getString("contentId"),
            seriesId = o.optStringOrNull("seriesId"),
            title = o.getString("title"),
            subtitle = o.optStringOrNull("subtitle"),
            image = o.optStringOrNull("image"),
            extension = o.optStringOrNull("extension"),
            positionMs = o.optLong("positionMs"),
            durationMs = o.optLong("durationMs"),
            updatedAt = o.optLong("updatedAt"),
        )
    }

    private fun readFavorites(): List<FavoriteEntry> = readArray(KEY_FAVORITES) { o ->
        FavoriteEntry(
            key = o.getString("key"),
            kind = MediaKind.valueOf(o.getString("kind")),
            contentId = o.getString("contentId"),
            title = o.getString("title"),
            image = o.optStringOrNull("image"),
            addedAt = o.optLong("addedAt"),
        )
    }

    private fun <T> readArray(key: String, parse: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                try {
                    parse(array.getJSONObject(i))
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun WatchEntry.toJson() = JSONObject()
        .put("key", key).put("kind", kind.name).put("contentId", contentId)
        .putOpt("seriesId", seriesId).put("title", title).putOpt("subtitle", subtitle)
        .putOpt("image", image).putOpt("extension", extension)
        .put("positionMs", positionMs).put("durationMs", durationMs).put("updatedAt", updatedAt)

    private fun FavoriteEntry.toJson() = JSONObject()
        .put("key", key).put("kind", kind.name).put("contentId", contentId)
        .put("title", title).putOpt("image", image).put("addedAt", addedAt)

    companion object {
        private const val KEY_HISTORY = "history"
        private const val KEY_FAVORITES = "favorites"
        private const val MAX_HISTORY = 200

        fun movieKey(id: String) = "movie:$id"
        fun episodeKey(id: String) = "episode:$id"
        fun liveKey(id: String) = "live:$id"
        fun seriesKey(id: String) = "series:$id"
    }
}

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
