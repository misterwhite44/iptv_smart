package com.misterwhite.iptvsmart.data.xtream

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Les panneaux Xtream renvoient les mêmes champs tantôt en texte, tantôt en
// nombre, parfois null ou vides : on lit tout de façon tolérante.

internal fun JsonObject.str(key: String): String? {
    val value = this[key] as? JsonPrimitive ?: return null
    if (value is JsonNull) return null
    return value.content.trim().takeIf { it.isNotEmpty() && it != "null" }
}

internal fun JsonObject.int(key: String): Int? {
    val raw = str(key) ?: return null
    return raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt()
}

internal fun JsonObject.long(key: String): Long? {
    val raw = str(key) ?: return null
    return raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong()
}

internal fun JsonObject.bool(key: String): Boolean {
    val raw = str(key)?.lowercase() ?: return false
    return raw == "1" || raw == "true" || raw == "yes"
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonElement.objects(): List<JsonObject> = when (this) {
    is JsonArray -> filterIsInstance<JsonObject>()
    // Certains serveurs renvoient un objet indexé {"0": {...}, "1": {...}}.
    is JsonObject -> values.filterIsInstance<JsonObject>()
    else -> emptyList()
}
