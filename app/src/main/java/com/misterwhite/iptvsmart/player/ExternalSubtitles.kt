package com.misterwhite.iptvsmart.player

import android.content.Context
import com.misterwhite.iptvsmart.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Sous-titres français téléchargés sur OpenSubtitles, pour les contenus dont
 * le fichier n'en contient pas, et surtout pour la Chromecast, qui ne lit pas
 * les sous-titres intégrés aux fichiers MKV ou MP4.
 */
object ExternalSubtitles {

    sealed interface Result {
        data class Found(val vtt: String) : Result
        data object NotFound : Result
        data object QuotaExceeded : Result
        data class Failed(val message: String) : Result
    }

    val isAvailable: Boolean get() = BuildConfig.OPENSUBTITLES_API_KEY.isNotBlank()

    private const val API = "https://api.opensubtitles.com/api/v1"
    private val json = Json { ignoreUnknownKeys = true }
    private val memory = ConcurrentHashMap<String, String>()
    @Volatile
    private var client: OkHttpClient? = null

    /**
     * Le client de l'appli impose son propre User-Agent ; OpenSubtitles exige
     * celui de l'appli enregistrée (« Nom vX.Y »).
     */
    fun init(httpClient: OkHttpClient) {
        if (client == null) client = httpClient.newBuilder().apply { interceptors().clear() }.build()
    }

    /** Message à afficher quand la recherche n'aboutit pas. */
    fun message(result: Result): String? = when (result) {
        is Result.Found -> null
        Result.NotFound -> "Pas de sous-titres français trouvés pour ce titre."
        Result.QuotaExceeded -> "Limite de téléchargements de sous-titres atteinte pour aujourd'hui. Réessaie demain."
        is Result.Failed -> result.message
    }

    suspend fun french(context: Context, cacheKey: String, search: SubtitleSearch): Result =
        withContext(Dispatchers.IO) { frenchBlocking(context, cacheKey, search) }

    /** Version bloquante, pour le petit serveur qui sert les sous-titres à la Chromecast. */
    @Synchronized
    fun frenchBlocking(context: Context, cacheKey: String, search: SubtitleSearch): Result {
        memory[cacheKey]?.let { return Result.Found(it) }
        val file = cacheFile(context, cacheKey)
        if (file.exists()) {
            runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                memory[cacheKey] = it
                return Result.Found(it)
            }
        }
        val http = client ?: return Result.Failed("Sous-titres indisponibles.")
        if (!isAvailable) return Result.Failed("Sous-titres externes non configurés.")
        return try {
            val fileId = findFile(http, search) ?: return Result.NotFound
            when (val link = downloadLink(http, fileId)) {
                null -> Result.QuotaExceeded
                else -> {
                    val vtt = toVtt(get(http, link.toHttpUrl()))
                    memory[cacheKey] = vtt
                    runCatching { file.parentFile?.mkdirs(); file.writeText(vtt) }
                    Result.Found(vtt)
                }
            }
        } catch (e: ApiException) {
            Result.Failed(e.message ?: "Erreur OpenSubtitles.")
        } catch (e: Exception) {
            Result.Failed("Impossible de joindre OpenSubtitles. Vérifie ta connexion.")
        }
    }

    /** Sous-titres déjà téléchargés pour ce contenu, sans rien demander au réseau. */
    fun cached(context: Context, cacheKey: String): String? =
        memory[cacheKey] ?: cacheFile(context, cacheKey).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    private fun cacheFile(context: Context, cacheKey: String) =
        File(File(context.cacheDir, "subtitles"), cacheKey.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".vtt")

    /** Meilleur fichier en français : d'abord par identifiant TMDB, sinon par titre. */
    private fun findFile(http: OkHttpClient, search: SubtitleSearch): Long? {
        val title = cleanTitle(search.title)
        val year = search.year?.takeIf { it.length == 4 } ?: Regex("\\((\\d{4})\\)").find(search.title)?.groupValues?.get(1)
        val season = search.season
        val episode = search.episode
        val tmdbId = search.tmdbId
        val isEpisode = season != null && episode != null
        val attempts = buildList<Map<String, String>> {
            if (tmdbId != null) {
                add(
                    if (isEpisode) {
                        mapOf(
                            "episode_number" to episode.toString(),
                            "parent_tmdb_id" to tmdbId,
                            "season_number" to season.toString(),
                        )
                    } else {
                        mapOf("tmdb_id" to tmdbId)
                    }
                )
            }
            if (title.isNotBlank()) {
                add(
                    buildMap<String, String> {
                        put("query", title.lowercase())
                        if (isEpisode) {
                            put("episode_number", episode.toString())
                            put("season_number", season.toString())
                        } else if (year != null) {
                            put("year", year)
                        }
                    }
                )
            }
        }
        for (params in attempts) {
            best(http, params)?.let { return it }
        }
        return null
    }

    private fun best(http: OkHttpClient, params: Map<String, String>): Long? {
        // OpenSubtitles demande les paramètres triés par ordre alphabétique.
        val url = "$API/subtitles".toHttpUrl().newBuilder().apply {
            (params + ("languages" to "fr")).toSortedMap().forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val root = json.parseToJsonElement(get(http, url, api = true)) as? JsonObject ?: return null
        val data = root["data"] as? JsonArray ?: return null
        return data.mapNotNull { it as? JsonObject }
            .mapNotNull { it["attributes"] as? JsonObject }
            .filter { (it["language"] as? JsonPrimitive)?.content?.lowercase()?.startsWith("fr") == true }
            .sortedWith(
                compareBy<JsonObject> { a ->
                    // Les traductions automatiques et les versions pour malentendants passent après.
                    (if (a.flag("machine_translated") || a.flag("ai_translated")) 2 else 0) + (if (a.flag("hearing_impaired")) 1 else 0)
                }.thenByDescending { (it["download_count"] as? JsonPrimitive)?.longOrNull ?: 0L }
            )
            .firstNotNullOfOrNull { a ->
                ((a["files"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("file_id")?.let { (it as? JsonPrimitive)?.longOrNull }
            }
    }

    private fun JsonObject.flag(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull == true

    /** Lien de téléchargement, ou null si le quota du jour est épuisé. */
    private fun downloadLink(http: OkHttpClient, fileId: Long): String? {
        val body = """{"file_id":$fileId,"sub_format":"webvtt"}""".toRequestBody("application/json".toMediaType())
        val request = apiRequest("$API/download".toHttpUrl()).post(body).build()
        http.newCall(request).execute().use { response ->
            if (response.code == 406 || response.code == 429) return null
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(errorMessage(response.code))
            val root = json.parseToJsonElement(text) as? JsonObject ?: throw ApiException("Réponse OpenSubtitles illisible.")
            val remaining = (root["remaining"] as? JsonPrimitive)?.intOrNull
            val link = (root["link"] as? JsonPrimitive)?.content?.takeIf { it.startsWith("http") }
            if (link == null && remaining != null && remaining <= 0) return null
            return link ?: throw ApiException("OpenSubtitles n'a pas donné de lien de téléchargement.")
        }
    }

    private fun apiRequest(url: HttpUrl) = Request.Builder()
        .url(url)
        .header("Api-Key", BuildConfig.OPENSUBTITLES_API_KEY)
        .header("User-Agent", "Zynora v${BuildConfig.VERSION_NAME}")
        .header("Accept", "application/json")

    private fun get(http: OkHttpClient, url: HttpUrl, api: Boolean = false): String {
        val request = if (api) apiRequest(url).build() else Request.Builder().url(url).header("User-Agent", "Zynora v${BuildConfig.VERSION_NAME}").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiException(errorMessage(response.code))
            return response.body?.string().orEmpty()
        }
    }

    private fun errorMessage(code: Int) = when (code) {
        401, 403 -> "La clé OpenSubtitles est refusée."
        429 -> "OpenSubtitles est saturé, réessaie dans un moment."
        else -> "Erreur OpenSubtitles ($code)."
    }

    private class ApiException(message: String) : Exception(message)

    /**
     * Les noms IPTV contiennent souvent des préfixes et des mentions de
     * qualité (« FR - », « |EN| », « [VOSTFR] », « 4K ») qui gênent la recherche.
     */
    fun cleanTitle(raw: String): String {
        var t = raw
        t = t.replace(Regex("^\\s*(\\|[^|]{1,8}\\|\\s*|\\[[^\\]]{1,10}\\]\\s*|[A-Z]{2,4}\\s*[-:|]\\s+)+"), "")
        t = t.replace(Regex("\\[[^\\]]*\\]|\\(\\d{4}\\)"), " ")
        t = t.replace(
            Regex("\\b(VOSTFR|VOST|MULTI|TRUEFRENCH|FRENCH|VFF|VFQ|VFI|VF|VO|4K|UHD|FHD|HD|SD|HDR|1080p|720p|2160p)\\b", RegexOption.IGNORE_CASE),
            " ",
        )
        t = t.replace(Regex("[-|:]\\s*\\d{4}\\s*$"), " ")
        return t.replace(Regex("\\s+"), " ").trim(' ', '-', '|', ':', '.')
    }

    /** Le format demandé est WebVTT ; au cas où on recevrait du SRT, on le convertit. */
    fun toVtt(text: String): String {
        val clean = text.removePrefix("﻿").replace("\r\n", "\n")
        if (clean.trimStart().startsWith("WEBVTT")) return clean
        val body = clean.replace(Regex("(\\d{2}:\\d{2}:\\d{2}),(\\d{3})"), "$1.$2")
        return "WEBVTT\n\n$body"
    }
}
