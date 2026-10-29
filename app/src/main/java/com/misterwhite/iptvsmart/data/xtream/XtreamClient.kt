package com.misterwhite.iptvsmart.data.xtream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import android.util.JsonReader
import android.util.JsonToken
import android.util.MalformedJsonException
import java.io.InputStreamReader
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Erreur affichable telle quelle à l'utilisateur. */
class XtreamException(message: String, cause: Throwable? = null) : Exception(message, cause)

class XtreamClient(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun authenticate(credentials: XtreamCredentials): AccountInfo {
        if (DemoCatalog.isDemo(credentials)) return DemoCatalog.account
        val root = request(credentials, action = null) as? JsonObject
            ?: throw XtreamException(UNEXPECTED)
        val user = root.obj("user_info") ?: throw XtreamException(UNEXPECTED)
        if (!user.bool("auth")) throw XtreamException(BAD_CREDENTIALS)
        val status = user.str("status") ?: "Active"
        if (!status.equals("Active", ignoreCase = true)) {
            throw XtreamException(
                when (status.lowercase()) {
                    "expired" -> "Ton abonnement a expiré."
                    "banned" -> "Ce compte a été bloqué par le fournisseur."
                    "disabled" -> "Ce compte est désactivé."
                    else -> "Compte inactif (statut : $status)."
                }
            )
        }
        return AccountInfo(
            status = status,
            expiresAtMillis = user.long("exp_date")?.takeIf { it > 0 }?.times(1000),
            maxConnections = user.int("max_connections"),
            activeConnections = user.int("active_cons"),
            isTrial = user.bool("is_trial"),
            allowedOutputFormats = (user["allowed_output_formats"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                .orEmpty(),
        )
    }

    // Les listes complètes peuvent peser des dizaines de Mo : on les lit en flux,
    // élément par élément, sans jamais charger toute la réponse en mémoire.

    suspend fun liveChannels(credentials: XtreamCredentials): List<LiveChannel> =
        if (DemoCatalog.isDemo(credentials)) DemoCatalog.liveChannels
        else streamList(credentials, "get_live_streams", LIVE_FIELDS) { f ->
            LiveChannel(
                id = f["stream_id"] ?: return@streamList null,
                name = f["name"] ?: "Chaîne",
                logo = f["stream_icon"],
                categoryId = f["category_id"],
                epgChannelId = f["epg_channel_id"],
            )
        }

    suspend fun movies(credentials: XtreamCredentials): List<Movie> =
        if (DemoCatalog.isDemo(credentials)) DemoCatalog.movies
        else streamList(credentials, "get_vod_streams", MOVIE_FIELDS) { f ->
            Movie(
                id = f["stream_id"] ?: return@streamList null,
                name = f["name"] ?: "Film",
                poster = f["stream_icon"],
                rating = f["rating"]?.toDoubleOrNull()?.takeIf { it > 0 },
                addedAt = f["added"]?.toLongOrNull() ?: 0L,
                categoryId = f["category_id"],
                extension = f["container_extension"] ?: "mp4",
            )
        }

    suspend fun series(credentials: XtreamCredentials): List<Series> =
        if (DemoCatalog.isDemo(credentials)) DemoCatalog.series
        else streamList(credentials, "get_series", SERIES_FIELDS) { f ->
            Series(
                id = f["series_id"] ?: return@streamList null,
                name = f["name"] ?: "Série",
                cover = f["cover"],
                backdrop = f["backdrop_path"],
                plot = f["plot"],
                genre = f["genre"],
                year = (f["releaseDate"] ?: f["release_date"])?.take(4),
                rating = f["rating"]?.toDoubleOrNull()?.takeIf { it > 0 },
                updatedAt = f["last_modified"]?.toLongOrNull() ?: 0L,
                categoryId = f["category_id"],
            )
        }

    suspend fun movieDetails(credentials: XtreamCredentials, movie: Movie): MovieDetails {
        if (DemoCatalog.isDemo(credentials)) return DemoCatalog.movieDetails(movie)
        val root = request(credentials, "get_vod_info", mapOf("vod_id" to movie.id)) as? JsonObject
        val info = root?.obj("info")
        val data = root?.obj("movie_data")
        return MovieDetails(
            movie = movie.copy(
                poster = movie.poster ?: info?.str("movie_image") ?: info?.str("cover_big"),
                extension = data?.str("container_extension") ?: movie.extension,
            ),
            backdrop = info?.firstImage("backdrop_path"),
            plot = info?.str("plot") ?: info?.str("description"),
            cast = info?.str("cast") ?: info?.str("actors"),
            director = info?.str("director"),
            genre = info?.str("genre"),
            releaseDate = info?.str("releasedate") ?: info?.str("release_date"),
            duration = info?.str("duration"),
            tmdbId = (info?.str("tmdb_id") ?: info?.str("tmdb"))?.takeIf { it.all(Char::isDigit) && it != "0" },
        )
    }

    suspend fun seriesDetails(credentials: XtreamCredentials, series: Series): SeriesDetails {
        if (DemoCatalog.isDemo(credentials)) return DemoCatalog.seriesDetails(series)
        val root = request(credentials, "get_series_info", mapOf("series_id" to series.id)) as? JsonObject
        val info = root?.obj("info")
        val seasons = mutableMapOf<Int, List<Episode>>()
        val episodes = root?.get("episodes")
        val groups: List<Pair<String?, JsonElement>> = when (episodes) {
            is JsonObject -> episodes.entries.map { it.key to it.value }
            is JsonArray -> episodes.map { null to it }
            else -> emptyList()
        }
        for ((key, value) in groups) {
            val list = value.objects().mapNotNull { e ->
                val epInfo = e.obj("info")
                Episode(
                    id = e.str("id") ?: return@mapNotNull null,
                    season = e.int("season") ?: key?.toIntOrNull() ?: 1,
                    number = e.int("episode_num") ?: 0,
                    title = e.str("title") ?: "Épisode",
                    extension = e.str("container_extension") ?: "mp4",
                    image = epInfo?.str("movie_image") ?: epInfo?.str("cover_big"),
                    plot = epInfo?.str("plot"),
                    durationSecs = epInfo?.int("duration_secs"),
                )
            }
            list.groupBy { it.season }.forEach { (season, eps) ->
                seasons[season] = seasons[season].orEmpty() + eps
            }
        }
        seasons.replaceAll { _, eps -> dedupeEpisodes(eps) }
        return SeriesDetails(
            series = series.copy(
                cover = series.cover ?: info?.str("cover"),
                backdrop = series.backdrop ?: info?.firstImage("backdrop_path"),
                plot = series.plot ?: info?.str("plot"),
                genre = series.genre ?: info?.str("genre"),
            ),
            cast = info?.str("cast"),
            seasons = seasons.toSortedMap(),
            tmdbId = (info?.str("tmdb_id") ?: info?.str("tmdb"))?.takeIf { it.all(Char::isDigit) && it != "0" },
        )
    }

    /**
     * Certains serveurs listent le même épisode plusieurs fois (même id, ou plusieurs
     * copies avec le même numéro). On n'en garde qu'un par numéro : celui qui a le plus d'infos.
     */
    private fun dedupeEpisodes(eps: List<Episode>): List<Episode> {
        val unique = eps.distinctBy { it.id }
        val numbered = unique.filter { it.number > 0 }
            .groupBy { it.number }
            .map { (_, copies) -> copies.maxBy { it.richness() } }
        return (numbered + unique.filter { it.number <= 0 }).sortedBy { it.number }
    }

    private fun Episode.richness() =
        (if (image != null) 1 else 0) + (if (!plot.isNullOrBlank()) 1 else 0) + (if ((durationSecs ?: 0) > 0) 1 else 0)

    suspend fun categories(credentials: XtreamCredentials, type: ContentType): List<Category> {
        if (DemoCatalog.isDemo(credentials)) return DemoCatalog.categories(type)
        val action = when (type) {
            ContentType.LIVE -> "get_live_categories"
            ContentType.MOVIE -> "get_vod_categories"
            ContentType.SERIES -> "get_series_categories"
        }
        return request(credentials, action).objects().mapNotNull { obj ->
            val id = obj.str("category_id") ?: return@mapNotNull null
            Category(id = id, name = obj.str("category_name") ?: "Sans nom")
        }
    }

    private suspend fun request(
        credentials: XtreamCredentials,
        action: String?,
        params: Map<String, String> = emptyMap(),
    ): JsonElement = execute(credentials, action, params) { body ->
        val text = body.string()
        if (text.isBlank()) throw XtreamException(UNEXPECTED)
        json.parseToJsonElement(text)
    }

    /**
     * Lit une liste d'objets JSON en flux. Seuls les champs de [fields] sont
     * gardés, en texte ; pour un champ en liste, on garde le premier élément.
     */
    private suspend fun <T : Any> streamList(
        credentials: XtreamCredentials,
        action: String,
        fields: Set<String>,
        build: (Map<String, String>) -> T?,
    ): List<T> = execute(credentials, action, emptyMap()) { body ->
        val result = ArrayList<T>()
        JsonReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
            reader.isLenient = true
            val readItem = {
                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                    build(readFields(reader, fields))?.let { result.add(it) }
                } else {
                    reader.skipValue()
                }
            }
            when (reader.peek()) {
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    while (reader.hasNext()) readItem()
                    reader.endArray()
                }
                // Certains serveurs renvoient {"0": {...}, "1": {...}}.
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        reader.nextName()
                        readItem()
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        result
    }

    private fun readFields(reader: JsonReader, fields: Set<String>): Map<String, String> {
        val values = HashMap<String, String>(fields.size * 2)
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name !in fields) {
                reader.skipValue()
                continue
            }
            readScalar(reader)?.let { values[name] = it }
        }
        reader.endObject()
        return values
    }

    /** Valeur en texte ; pour une liste, son premier élément texte. */
    private fun readScalar(reader: JsonReader): String? = when (reader.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> reader.nextString().trim().takeIf { it.isNotEmpty() && it != "null" }
        JsonToken.BOOLEAN -> reader.nextBoolean().toString()
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }
        JsonToken.BEGIN_ARRAY -> {
            reader.beginArray()
            var first: String? = null
            while (reader.hasNext()) {
                val value = if (reader.peek() == JsonToken.STRING) reader.nextString() else {
                    reader.skipValue()
                    null
                }
                if (first == null && !value.isNullOrBlank()) first = value
            }
            reader.endArray()
            first
        }
        else -> {
            reader.skipValue()
            null
        }
    }

    private suspend fun <T> execute(
        credentials: XtreamCredentials,
        action: String?,
        params: Map<String, String>,
        parse: (ResponseBody) -> T,
    ): T = withContext(Dispatchers.IO) {
        val url = apiUrl(credentials, action, params)
        // Les serveurs Xtream limitent souvent les requêtes simultanées et
        // répondent 429 au lancement si l'appli demande tout d'un coup.
        var attempt = 0
        while (true) {
            when (val result = requestSlots.withPermit { executeOnce(url, parse) }) {
                is Attempt.Done -> return@withContext result.value
                is Attempt.Busy -> {
                    attempt++
                    if (attempt > MAX_BUSY_RETRIES) {
                        throw XtreamException("Le serveur est surchargé (erreur 429). Réessaie dans quelques secondes.")
                    }
                    delay(result.retryAfterMs ?: (BUSY_BASE_DELAY_MS shl (attempt - 1)))
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException("unreachable")
    }

    private sealed interface Attempt<out T> {
        class Done<T>(val value: T) : Attempt<T>
        class Busy(val retryAfterMs: Long?) : Attempt<Nothing>
    }

    private fun <T> executeOnce(url: HttpUrl, parse: (ResponseBody) -> T): Attempt<T> {
        val call = http.newCall(Request.Builder().url(url).get().build())
        return try {
            call.execute().use { response ->
                if (response.code == 429 || response.code == 503) {
                    val seconds = response.header("Retry-After")?.trim()?.toLongOrNull()
                    return Attempt.Busy(seconds?.coerceIn(1, 30)?.times(1000))
                }
                when {
                    response.code == 401 || response.code == 403 ->
                        throw XtreamException(BAD_CREDENTIALS)
                    !response.isSuccessful ->
                        throw XtreamException("Le serveur a répondu avec une erreur (${response.code}).")
                }
                val body = response.body ?: throw XtreamException(UNEXPECTED)
                try {
                    Attempt.Done(parse(body))
                } catch (e: XtreamException) {
                    throw e
                } catch (e: MalformedJsonException) {
                    throw XtreamException(UNEXPECTED, e)
                } catch (e: IOException) {
                    throw e
                } catch (e: OutOfMemoryError) {
                    throw XtreamException("Le catalogue est trop gros pour la mémoire du téléphone.", e)
                } catch (e: Exception) {
                    throw XtreamException(UNEXPECTED, e)
                }
            }
        } catch (e: XtreamException) {
            throw e
        } catch (e: UnknownHostException) {
            throw XtreamException("Serveur introuvable. Vérifie l'URL et ta connexion.", e)
        } catch (e: SocketTimeoutException) {
            throw XtreamException("Le serveur ne répond pas. Réessaie dans un instant.", e)
        } catch (e: SSLException) {
            throw XtreamException("Connexion sécurisée impossible. Essaie avec http:// au lieu de https://.", e)
        } catch (e: IOException) {
            throw XtreamException("Impossible de joindre le serveur. Vérifie l'URL et ta connexion.", e)
        }
    }

    private fun apiUrl(
        credentials: XtreamCredentials,
        action: String?,
        params: Map<String, String>,
    ): HttpUrl {
        val base = credentials.serverUrl.toHttpUrlOrNull()
            ?: throw XtreamException("L'URL du serveur n'est pas valide.")
        return base.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
            .apply {
                if (action != null) addQueryParameter("action", action)
                params.forEach { (key, value) -> addQueryParameter(key, value) }
            }
            .build()
    }

    companion object {
        /** Requêtes envoyées en même temps au serveur, au plus. */
        private val requestSlots = Semaphore(2)
        private const val MAX_BUSY_RETRIES = 4
        private const val BUSY_BASE_DELAY_MS = 1_500L

        private val LIVE_FIELDS = setOf("stream_id", "name", "stream_icon", "category_id", "epg_channel_id")
        private val MOVIE_FIELDS = setOf(
            "stream_id", "name", "stream_icon", "rating", "added", "category_id", "container_extension",
        )
        private val SERIES_FIELDS = setOf(
            "series_id", "name", "cover", "backdrop_path", "plot", "genre", "releaseDate", "release_date",
            "rating", "last_modified", "category_id",
        )

        private const val BAD_CREDENTIALS = "Identifiant ou mot de passe incorrect."
        private const val UNEXPECTED =
            "Réponse inattendue du serveur. Vérifie que l'URL est bien celle de ton fournisseur Xtream."

        /**
         * Nettoie l'URL saisie : ajoute http:// si besoin et retire ce qui suit
         * l'adresse du serveur (player_api.php, get.php, barre finale…).
         */
        fun normalizeServerUrl(input: String): String {
            var url = input.trim()
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                url = "http://$url"
            }
            val parsed = url.toHttpUrlOrNull() ?: return url.trimEnd('/')
            val segments = parsed.pathSegments
                .filter { it.isNotEmpty() }
                .takeWhile { !it.endsWith(".php") }
            return parsed.newBuilder()
                .encodedPath("/")
                .query(null)
                .fragment(null)
                .apply { segments.forEach { addPathSegment(it) } }
                .build()
                .toString()
                .trimEnd('/')
        }

        /** Extrait identifiant et mot de passe d'un lien M3U collé dans le champ URL. */
        fun credentialsFromLink(input: String): Pair<String, String>? {
            val url = input.trim().let {
                if (it.startsWith("http", true)) it else "http://$it"
            }.toHttpUrlOrNull() ?: return null
            val user = url.queryParameter("username") ?: return null
            val pass = url.queryParameter("password") ?: return null
            return user to pass
        }
    }
}
