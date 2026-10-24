package com.misterwhite.iptvsmart.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.misterwhite.iptvsmart.data.xtream.XtreamCredentials

/** Garde l'URL, l'identifiant et le mot de passe chiffrés sur le téléphone. */
class CredentialsStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            create()
        } catch (e: Exception) {
            // Clé du Keystore perdue (ex. restauration) : on repart de zéro.
            context.deleteSharedPreferences(FILE_NAME)
            create()
        }
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): XtreamCredentials? {
        val server = prefs.getString(KEY_SERVER, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return XtreamCredentials(server, username, password)
    }

    fun save(credentials: XtreamCredentials) {
        prefs.edit()
            .putString(KEY_SERVER, credentials.serverUrl)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .apply()
    }

    /** Format des chaînes en direct : m3u8 (HLS) si le serveur le propose, sinon ts. */
    fun liveFormat(): String = prefs.getString(KEY_LIVE_FORMAT, null) ?: "m3u8"

    fun saveLiveFormat(allowedFormats: List<String>) {
        val format = if (allowedFormats.isEmpty() || allowedFormats.any { it.equals("m3u8", true) }) "m3u8" else "ts"
        prefs.edit().putString(KEY_LIVE_FORMAT, format).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "xtream_credentials"
        const val KEY_SERVER = "server"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_LIVE_FORMAT = "live_format"
    }
}
