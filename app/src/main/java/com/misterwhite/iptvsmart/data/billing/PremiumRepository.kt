package com.misterwhite.iptvsmart.data.billing

import android.content.Context
import com.misterwhite.iptvsmart.data.account.AccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** D'où vient l'accès Premium. */
enum class PremiumSource { NONE, PLAY, VIP }

/**
 * Premium = un achat Google Play actif, ou le drapeau « vip » posé à la main
 * sur la fiche du compte dans la console Firebase (accès offert, à vie).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PremiumRepository(
    context: Context,
    account: AccountRepository,
    val billing: BillingRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences("premium", Context.MODE_PRIVATE)

    private val vip = account.user.flatMapLatest { user ->
        if (user == null) flowOf(false) else account.observeVip(user.uid)
    }

    /** Dernier état connu, pour ne pas bloquer Premium hors ligne ou pendant que Google Play répond. */
    private val cached = runCatching { PremiumSource.valueOf(prefs.getString(KEY, null).orEmpty()) }.getOrDefault(PremiumSource.NONE)

    val source: StateFlow<PremiumSource> = combine(billing.ownsPremium, vip) { play, isVip ->
        when {
            isVip -> PremiumSource.VIP
            play == true || (play == null && cached == PremiumSource.PLAY) -> PremiumSource.PLAY
            else -> PremiumSource.NONE
        }
    }.stateIn(scope, SharingStarted.Eagerly, cached)

    init {
        source.onEach { prefs.edit().putString(KEY, it.name).apply() }.launchIn(scope)
        billing.refresh()
    }

    val isPremium: Boolean get() = source.value != PremiumSource.NONE

    private companion object {
        const val KEY = "source"
    }
}
