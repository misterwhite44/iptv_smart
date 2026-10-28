package com.misterwhite.iptvsmart.cast

import android.content.Context
import android.net.ConnectivityManager
import com.misterwhite.iptvsmart.player.ExternalSubtitles
import com.misterwhite.iptvsmart.player.SubtitleSearch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Tout petit serveur web sur le téléphone : la Chromecast vient y chercher le
 * fichier de sous-titres (WebVTT) du contenu qu'elle lit, sur le Wi-Fi de la
 * maison. Il ne sert que les sous-titres enregistrés ici, sous un lien
 * aléatoire.
 */
object SubtitleServer {

    private class Entry(val cacheKey: String, val search: SubtitleSearch)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val idsByKey = ConcurrentHashMap<String, String>()
    private val workers = Executors.newCachedThreadPool { r -> Thread(r, "subtitles").apply { isDaemon = true } }
    @Volatile
    private var socket: ServerSocket? = null
    private lateinit var appContext: Context

    /** Lien des sous-titres de ce contenu pour la Chromecast, ou null sans réseau local. */
    fun urlFor(context: Context, cacheKey: String, search: SubtitleSearch): String? {
        appContext = context.applicationContext
        val host = localAddress(context) ?: return null
        val port = ensureStarted() ?: return null
        val id = idsByKey.getOrPut(cacheKey) { UUID.randomUUID().toString() }
        entries[id] = Entry(cacheKey, search)
        return "http://$host:$port/s/$id.vtt"
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
        }, "subtitles-server").apply { isDaemon = true }.start()
        return server.localPort
    }

    private fun handle(client: Socket) = client.use { socket ->
        runCatching {
            socket.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return@runCatching
            // On lit les en-têtes sans s'en servir.
            while (reader.readLine()?.isNotEmpty() == true) Unit
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0).orEmpty()
            val path = parts.getOrNull(1).orEmpty()
            val out = socket.getOutputStream()
            if (method == "OPTIONS") {
                out.write(header(204, "No Content", 0).toByteArray(Charsets.ISO_8859_1))
                out.flush()
                return@runCatching
            }
            val id = Regex("^/s/([0-9a-f-]{36})\\.vtt$").find(path)?.groupValues?.get(1)
            val entry = id?.let { entries[it] }
            val vtt = entry?.let {
                (ExternalSubtitles.frenchBlocking(appContext, it.cacheKey, it.search) as? ExternalSubtitles.Result.Found)?.vtt
            }
            if (vtt == null) {
                out.write(header(404, "Not Found", 0).toByteArray(Charsets.ISO_8859_1))
            } else {
                val body = vtt.toByteArray(Charsets.UTF_8)
                out.write(header(200, "OK", body.size, "text/vtt; charset=utf-8").toByteArray(Charsets.ISO_8859_1))
                if (method != "HEAD") out.write(body)
            }
            out.flush()
        }
    }

    /** La Chromecast refuse les sous-titres sans en-têtes CORS. */
    private fun header(code: Int, status: String, length: Int, type: String? = null) = buildString {
        append("HTTP/1.1 $code $status\r\n")
        type?.let { append("Content-Type: $it\r\n") }
        append("Content-Length: $length\r\n")
        append("Access-Control-Allow-Origin: *\r\n")
        append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
        append("Access-Control-Allow-Headers: *\r\n")
        append("Cache-Control: no-cache\r\n")
        append("Connection: close\r\n\r\n")
    }

    /** Adresse du téléphone sur le Wi-Fi (IPv4), là où la Chromecast peut le joindre. */
    private fun localAddress(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }
}
