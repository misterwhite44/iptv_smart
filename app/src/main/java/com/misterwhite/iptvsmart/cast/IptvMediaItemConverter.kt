package com.misterwhite.iptvsmart.cast

import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.HlsSegmentFormat
import com.google.android.gms.cast.HlsVideoSegmentFormat
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaTrack

/**
 * Précise à la Chromecast comment lire les flux IPTV : les chaînes sont du
 * direct (pas de durée), et leurs flux HLS contiennent des segments MPEG-TS.
 * Sans ces indications, le lecteur Chromecast standard reste sur le logo Cast.
 */
@UnstableApi
class IptvMediaItemConverter : MediaItemConverter {
    private val default = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val base = default.toMediaQueueItem(mediaItem)
        val info = base.media ?: return base
        val mimeType = mediaItem.localConfiguration?.mimeType
        val isLive = mediaItem.mediaId.startsWith(LIVE_PREFIX)
        val url = info.contentUrl ?: mediaItem.localConfiguration?.uri?.toString() ?: info.contentId ?: return base
        val builder = MediaInfo.Builder(info.contentId ?: url)
            .setContentUrl(url)
            .setContentType(mimeType ?: info.contentType ?: MimeTypes.VIDEO_MP4)
            .setStreamType(if (isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(info.metadata)
            .setCustomData(info.customData)
        // Sous-titres servis par le téléphone : la Chromecast ne les charge
        // que quand on les active depuis la télécommande.
        val tracks = mediaItem.localConfiguration?.subtitleConfigurations.orEmpty().mapIndexed { i, sub ->
            MediaTrack.Builder(SUBTITLE_TRACK_ID + i, MediaTrack.TYPE_TEXT)
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(sub.uri.toString())
                .setContentType(sub.mimeType ?: MimeTypes.TEXT_VTT)
                .setLanguage(sub.language ?: "fr")
                .setName(sub.label ?: "Français")
                .build()
        }
        if (tracks.isNotEmpty()) builder.setMediaTracks(tracks)
        if (mimeType == MimeTypes.APPLICATION_M3U8) {
            builder.setHlsSegmentFormat(HlsSegmentFormat.TS)
            builder.setHlsVideoSegmentFormat(HlsVideoSegmentFormat.MPEG2_TS)
        }
        return MediaQueueItem.Builder(builder.build()).build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = default.toMediaItem(mediaQueueItem)

    companion object {
        const val LIVE_PREFIX = "live:"
        /** Identifiant de la piste de sous-titres français envoyée à la Chromecast. */
        const val SUBTITLE_TRACK_ID = 100L
    }
}
