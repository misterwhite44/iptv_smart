package com.misterwhite.iptvsmart

import android.app.Application
import com.misterwhite.iptvsmart.data.CredentialsStore
import com.misterwhite.iptvsmart.data.xtream.XtreamClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class IptvApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Dépendances partagées par toute l'appli. */
class AppContainer(app: Application) {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
            )
        }
        .build()

    val credentialsStore = CredentialsStore(app)
    val xtreamClient = XtreamClient(httpClient)

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) IPTVSmart/1.0"
    }
}
