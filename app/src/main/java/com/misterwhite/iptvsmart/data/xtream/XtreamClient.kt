package com.misterwhite.iptvsmart.data.xtream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Erreur affichable telle quelle à l'utilisateur. */
class XtreamException(message: String, cause: Throwable? = null) : Exception(message, cause)

class XtreamClient(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun authenticate(credentials: XtreamCredentials): AccountInfo {
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

    suspend fun liveChannels(credentials: XtreamCredentials): List<LiveChannel> =
        request(credentials, "get_live_streams").objects().mapNotNull { o ->
            LiveChannel(
                id = o.str("stream_id") ?: return@mapNotNull null,
                name = o.str("name") ?: "Chaîne",
                logo = o.str("stream_icon"),
                categoryId = o.str("category_id"),
                epgChannelId = o.str("epg_channel_id"),
            )
        }

    suspend fun movies(credentials: XtreamCredentials): List<Movie> =
        request(credentials, "get_vod_streams").objects().mapNotNull { it.toMovie() }

    suspend fun series(credentials: XtreamCredentials): List<Series> =
        request(credentials, "get_series").objects().mapNotNull { o ->
            Series(
                id = o.str("series_id") ?: return@mapNotNull null,
                name = o.str("name") ?: "Série",
                cover = o.str("cover"),
                backdrop = o.firstImage("backdrop_path"),
                plot = o.str("plot"),
                genre = o.str("genre"),
                year = o.str("releaseDate")?.take(4) ?: o.str("release_date")?.take(4),
                rating = o.str("rating")?.toDoubleOrNull()?.takeIf { it > 0 },
                updatedAt = o.long("last_modified") ?: 0L,
                categoryId = o.str("category_id"),
            )
        }

    suspend fun movieDetails(credentials: XtreamCredentials, movie: Movie): MovieDetails {
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
        )
    }

    suspend fun seriesDetails(credentials: XtreamCredentials, series: Series): SeriesDetails {
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
                seasons[season] = (seasons[season].orEmpty() + eps).sortedBy { it.number }
            }
        }
        return SeriesDetails(
            series = series.copy(
                cover = series.cover ?: info?.str("cover"),
                backdrop = series.backdrop ?: info?.firstImage("backdrop_path"),
                plot = series.plot ?: info?.str("plot"),
                genre = series.genre ?: info?.str("genre"),
            ),
            cast = info?.str("cast"),
            seasons = seasons.toSortedMap(),
        )
    }

    private fun JsonObject.toMovie(): Movie? = Movie(
        id = str("stream_id") ?: return null,
        name = str("name") ?: "Film",
        poster = str("stream_icon"),
        rating = str("rating")?.toDoubleOrNull()?.takeIf { it > 0 },
        addedAt = long("added") ?: 0L,
        categoryId = str("category_id"),
        extension = str("container_extension") ?: "mp4",
    )

    suspend fun categories(credentials: XtreamCredentials, type: ContentType): List<Category> {
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
    ): JsonElement = withContext(Dispatchers.IO) {
        val url = apiUrl(credentials, action, params)
        val call = http.newCall(Request.Builder().url(url).get().build())
        try {
            call.execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 ->
                        throw XtreamException(BAD_CREDENTIALS)
                    !response.isSuccessful ->
                        throw XtreamException("Le serveur a répondu avec une erreur (${response.code}).")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) throw XtreamException(UNEXPECTED)
                try {
                    json.parseToJsonElement(body)
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
