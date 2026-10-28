package com.misterwhite.iptvsmart

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.misterwhite.iptvsmart.data.CatalogRepository
import com.misterwhite.iptvsmart.data.CredentialsStore
import com.misterwhite.iptvsmart.data.LibraryRepository
import com.misterwhite.iptvsmart.data.account.AccountRepository
import com.misterwhite.iptvsmart.data.account.AccountSync
import com.misterwhite.iptvsmart.data.billing.BillingRepository
import com.misterwhite.iptvsmart.data.billing.PremiumRepository
import com.misterwhite.iptvsmart.data.xtream.XtreamClient
import com.misterwhite.iptvsmart.player.ExternalSubtitles
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class IptvApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(container.httpClient)
        .crossfade(true)
        .build()
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
    val catalog = CatalogRepository(xtreamClient, credentialsStore)
    val library = LibraryRepository(app)
    val account = AccountRepository(app)
    val accountSync = AccountSync(account, library, credentialsStore)
    val premium = PremiumRepository(app, account, BillingRepository(app))

    init {
        ExternalSubtitles.init(httpClient)
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) Zynora/1.0"
    }
}
