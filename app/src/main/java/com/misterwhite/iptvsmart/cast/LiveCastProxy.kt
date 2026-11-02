package com.misterwhite.iptvsmart.cast

import android.content.Context
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Relais des chaînes en direct pour la Chromecast.
 *
 * Le lecteur de la Chromecast lit le HLS avec des requêtes web, qui exigent
 * des en-têtes CORS que les serveurs IPTV n'envoient presque jamais : la
 * télé reste alors sur le logo Cast. Les films passent car un fichier MP4
 * n'en a pas besoin.
 *
 * Le téléphone sert donc de relais sur le Wi-Fi : la Chromecast lui demande
 * la playlist et les segments, il va les chercher chez le fournisseur (une
 * seule connexion, comme avant), réécrit les liens de la playlist vers
 * lui-même et ajoute les en-têtes CORS.
 */
object LiveCastProxy {

    private val streams = ConcurrentHashMap<String, String>()
    private val workers = Executors.newCachedThreadPool { r -> Thread(r, "live-cast").apply { isDaemon = true } }
    @Volatile
    private var socket: ServerSocket? = null
    private lateinit var http: OkHttpClient

    /** Dernière requête de la Chromecast, pour savoir si le relais sert encore. */
    @Volatile
    var lastRequestAt = 0L
        private set

    /** Lien du relais pour cette chaîne, ou null sans Wi-Fi (on envoie alors le lien direct). */
    fun urlFor(context: Context, client: OkHttpClient, streamUrl: String): String? {
        http = client
        val host = SubtitleServer.localAddress(context) ?: return null
        val port = ensureStarted() ?: return null
        // Un seul direct à la fois sur la Chromecast : on oublie les anciens liens.
        streams.clear()
        val id = UUID.randomUUID().toString()
        streams[id] = streamUrl
        lastRequestAt = System.currentTimeMillis()
        LiveCastService.start(context)
        return "http://$host:$port/l/$id/live.m3u8"
    }

    /** La Chromecast lit un direct relayé (elle a demandé quelque chose récemment). */
    fun isActive(idleMs: Long): Boolean =
        streams.isNotEmpty() && System.currentTimeMillis() - lastRequestAt < idleMs

    fun stop() {
        streams.clear()
    }

    @Synchronized
    private fun ensureStarted(): Int? {
        socket?.takeIf { !it.isClosed }?.let { return it.localPort }
        val server = runCatching { ServerSocket(0) }.getOrNull() ?: return null
        socket = server
        Thread({
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                workers.execute { handle(client) }
            }
        }, "live-cast-server").apply { isDaemon = true }.start()
        return server.localPort
    }

    private fun handle(client: Socket) = client.use { socket ->
        runCatching {
            socket.soTimeout = 30_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return@runCatching
            var range: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("range:", ignoreCase = true)) range = line.substringAfter(':').trim()
            }
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0).orEmpty()
            val path = parts.getOrNull(1).orEmpty().substringBefore('?')
            val out = socket.getOutputStream()
            if (method == "OPTIONS") return@runCatching writeHead(out, 204, "No Content", length = 0)

            val match = PATH.find(path) ?: return@runCatching writeHead(out, 404, "Not Found", length = 0)
            val id = match.groupValues[1]
            val base = streams[id] ?: return@runCatching writeHead(out, 404, "Not Found", length = 0)
            lastRequestAt = System.currentTimeMillis()
            val target = when (val rest = match.groupValues[2]) {
                "live.m3u8" -> base
                else -> decode(rest.substringAfter("s/").substringBeforeLast('.')) ?: return@runCatching writeHead(out, 400, "Bad Request", length = 0)
            }
            relay(out, id, target, isPlaylist = path.endsWith(".m3u8"), range = range, headOnly = method == "HEAD")
        }
    }

    private fun relay(out: OutputStream, id: String, target: String, isPlaylist: Boolean, range: String?, headOnly: Boolean) {
        val request = Request.Builder().url(target).apply { range?.let { header("Range", it) } }.build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // On rend l'erreur du fournisseur (souvent 429) : le téléphone réessaie ensuite.
                return writeHead(out, response.code, response.message.ifBlank { "Error" }, length = 0)
            }
            val body = response.body ?: return writeHead(out, 502, "Bad Gateway", length = 0)
            val type = body.contentType()?.toString()
            if (isPlaylist || type?.contains("mpegurl", ignoreCase = true) == true) {
                val finalUrl = response.request.url.toString()
                val text = rewrite(id, body.string(), finalUrl).toByteArray(Charsets.UTF_8)
                writeHead(out, 200, "OK", length = text.size.toLong(), type = "application/vnd.apple.mpegurl")
                if (!headOnly) out.write(text)
            } else {
                val length = body.contentLength()
                writeHead(
                    out,
                    response.code,
                    response.message.ifBlank { "OK" },
                    length = length.takeIf { it >= 0 },
                    type = type ?: "video/mp2t",
                    contentRange = response.header("Content-Range"),
                )
                if (!headOnly) body.byteStream().copyTo(out, 64 * 1024)
            }
            out.flush()
        }
    }

    /** Réécrit chaque lien de la playlist (segments, sous-playlists, clés) vers le relais. */
    private fun rewrite(id: String, playlist: String, baseUrl: String): String {
        val base = baseUrl.toHttpUrlOrNull() ?: return playlist
        fun proxied(uri: String): String {
            val absolute = base.resolve(uri.trim())?.toString() ?: return uri
            val ext = absolute.toHttpUrlOrNull()?.encodedPath?.substringAfterLast('/')
                ?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
                ?: "ts"
            return "/l/$id/s/${encode(absolute)}.$ext"
        }
        return playlist.lineSequence().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.startsWith("#") -> URI_ATTR.replace(line) { "URI=\"${proxied(it.groupValues[1])}\"" }
                else -> proxied(line)
            }
        }
    }

    private fun encode(url: String): String =
        Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun decode(value: String): String? =
        runCatching { String(Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    private fun writeHead(
        out: OutputStream,
        code: Int,
        status: String,
        length: Long?,
        type: String? = null,
        contentRange: String? = null,
    ) {
        val head = buildString {
            append("HTTP/1.1 $code $status\r\n")
            type?.let { append("Content-Type: $it\r\n") }
            length?.let { append("Content-Length: $it\r\n") }
            contentRange?.let { append("Content-Range: $it\r\n") }
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: *\r\n")
            append("Access-Control-Expose-Headers: Content-Length, Content-Range\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private val PATH = Regex("^/l/([0-9a-f-]{36})/(live\\.m3u8|s/[A-Za-z0-9_-]+\\.[a-z0-9]{1,5})$")
    private val URI_ATTR = Regex("URI=\"([^\"]+)\"")
}
