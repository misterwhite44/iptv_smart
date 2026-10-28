package com.misterwhite.iptvsmart.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks

/** Réglage « sous-titres français automatiques », gardé sur le téléphone. */
object SubtitlePrefs {
    private const val FILE = "player"
    private const val KEY_AUTO_FRENCH = "auto_french_subtitles"

    fun autoFrench(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_FRENCH, true)

    fun setAutoFrench(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_FRENCH, enabled).apply()
    }
}

/** Choix des pistes de sous-titres intégrées au fichier vidéo. */
object Subtitles {

    /**
     * Les fichiers IPTV indiquent la langue de façon très variable : code
     * (« fr », « fre », « fra », « fr-FR ») ou seulement un nom (« Français »,
     * « French », « VFF »).
     */
    fun isFrench(format: Format): Boolean {
        val language = format.language?.lowercase()
        if (language != null && (language == "fr" || language.startsWith("fr-") || language == "fre" || language == "fra")) {
            return true
        }
        val label = format.label?.lowercase() ?: return false
        return "fran" in label || "french" in label || Regex("\\b(fr|vf|vff|vfq|vfi)\\b").containsMatchIn(label)
    }

    /** Langue audio en cours de lecture, ou null si les pistes ne sont pas encore connues. */
    fun selectedAudio(tracks: Tracks): Format? {
        val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected } ?: return null
        return (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let(group::getTrackFormat)
    }

    /**
     * Meilleure piste de sous-titres en français : une piste complète plutôt
     * que « forcés » (qui ne traduit que quelques passages), et sans les
     * descriptions pour malentendants si possible.
     */
    fun frenchTrack(tracks: Tracks): Pair<Tracks.Group, Int>? =
        tracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { group -> (0 until group.length).filter(group::isTrackSupported).map { group to it } }
            .filter { (group, index) -> isFrench(group.getTrackFormat(index)) }
            .minByOrNull { (group, index) -> penalty(group.getTrackFormat(index)) }

    private fun penalty(format: Format): Int {
        val label = format.label?.lowercase().orEmpty()
        val forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0 || "forc" in label
        val sdh = "sdh" in label || "malentendant" in label || "cc" == label
        return (if (forced) 2 else 0) + (if (sdh) 1 else 0)
    }
}
