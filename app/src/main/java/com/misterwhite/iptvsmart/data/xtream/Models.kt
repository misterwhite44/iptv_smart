package com.misterwhite.iptvsmart.data.xtream

data class XtreamCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
)

data class AccountInfo(
    val status: String,
    /** Date d'expiration en millisecondes, null si illimité. */
    val expiresAtMillis: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val isTrial: Boolean,
)

data class Category(
    val id: String,
    val name: String,
)

enum class ContentType { LIVE, MOVIE, SERIES }
