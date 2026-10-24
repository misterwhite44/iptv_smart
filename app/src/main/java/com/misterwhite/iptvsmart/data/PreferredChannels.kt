package com.misterwhite.iptvsmart.data

import com.misterwhite.iptvsmart.data.xtream.Catalog
import com.misterwhite.iptvsmart.data.xtream.LiveChannel
import java.text.Normalizer

/**
 * Chaînes mises d'office en favoris et en tête du Direct :
 * Canal+ en français, puis Eurosport en français.
 */
object PreferredChannels {
    private val DIACRITICS = Regex("\\p{Mn}+")
    private val FRENCH = Regex("(^|[^a-z])(fr|fra|france|french|francais|vf|vff)([^a-z]|$)")
    private val CANAL = Regex("canal ?(\\+|plus)")

    private fun normalize(text: String) =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    /** 0 pour Canal+, 1 pour Eurosport, null pour les autres chaînes. */
    fun rank(channel: LiveChannel, categoryName: String?, allowUnmarkedCanal: Boolean = false): Int? {
        val name = normalize(channel.name)
        val isCanal = CANAL.containsMatchIn(name)
        val isEurosport = "eurosport" in name
        if (!isCanal && !isEurosport) return null
        val french = FRENCH.containsMatchIn(name) || FRENCH.containsMatchIn(normalize(categoryName.orEmpty()))
        return when {
            isCanal && (french || allowUnmarkedCanal) -> 0
            isEurosport && french -> 1
            else -> null
        }
    }

    /** Les chaînes préférées du catalogue, Canal+ d'abord, triées par nom. */
    fun find(live: Catalog<LiveChannel>): List<LiveChannel> {
        val categoryNames = live.categories.associate { it.id to it.name }
        fun ranked(allowUnmarked: Boolean) = live.items.mapNotNull { ch ->
            rank(ch, categoryNames[ch.categoryId], allowUnmarked)?.let { ch to it }
        }
        // Si le fournisseur ne marque pas la langue, on garde au moins Canal+.
        val found = ranked(false).ifEmpty { ranked(true) }
        return found
            .sortedWith(compareBy<Pair<LiveChannel, Int>> { it.second }.thenBy(NaturalOrder) { normalize(it.first.name) })
            .map { it.first }
    }

    /** Ordre « humain » : Canal+ 2 avant Canal+ 10. */
    private object NaturalOrder : Comparator<String> {
        private val chunk = Regex("\\d+|\\D+")
        override fun compare(a: String, b: String): Int {
            val pa = chunk.findAll(a).map { it.value }.toList()
            val pb = chunk.findAll(b).map { it.value }.toList()
            for (i in 0 until minOf(pa.size, pb.size)) {
                val x = pa[i]
                val y = pb[i]
                val c = if (x[0].isDigit() && y[0].isDigit()) {
                    (x.toBigIntegerOrNull() ?: 0.toBigInteger()).compareTo(y.toBigIntegerOrNull() ?: 0.toBigInteger())
                } else x.compareTo(y)
                if (c != 0) return c
            }
            return pa.size - pb.size
        }
    }
}
