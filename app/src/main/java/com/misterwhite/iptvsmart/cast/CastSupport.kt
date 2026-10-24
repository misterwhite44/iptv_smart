package com.misterwhite.iptvsmart.cast

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

object CastSupport {
    /** null si les services Google Play ne sont pas disponibles sur le téléphone. */
    fun castContext(context: Context): CastContext? = try {
        @Suppress("DEPRECATION")
        CastContext.getSharedInstance(context.applicationContext)
    } catch (e: Exception) {
        null
    }
}

/** Bouton Chromecast standard : il apparaît dès qu'une Chromecast est sur le Wi-Fi. */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            MediaRouteButton(ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat)).also {
                if (CastSupport.castContext(context) != null) {
                    CastButtonFactory.setUpMediaRouteButton(context.applicationContext, it)
                }
            }
        },
        modifier = modifier,
    )
}
