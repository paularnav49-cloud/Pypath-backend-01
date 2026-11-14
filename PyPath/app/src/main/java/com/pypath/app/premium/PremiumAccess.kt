package com.pypath.app.premium

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Premium Phase 1: modular feature gating.
 *
 * Screens never ask "is this user premium?" directly; they ask whether one [PremiumFeature] is
 * unlocked ([Entitlements.has]). Free users keep every existing feature exactly as before; the
 * gated parts below are extra on top.
 *
 * SECURITY NOTE: the only implementation in this phase is [LocalDevPremiumRepository], a local
 * developer/testing switch stored on the phone. It is NOT payment verification and is NOT secure:
 * anyone with access to the device could turn it on. A future `BackendPremiumRepository` (purchase
 * + server verification) can replace it in [com.pypath.app.AppContainer] without touching screens.
 */
enum class PremiumFeature(val title: String, val description: String) {
    ADVANCED_IDE("Advanced Python IDE", "Save multiple programs, folders, autosave and a snippets library"),
    IMAGE_CENTER("Image Center", "Two downloadable PyPath images saved to your gallery"),
    /** Premium Phase 2. */
    PROJECT_LIBRARY("Premium Project Library", "Advanced and real-world projects plus challenges checked by automatic tests"),
    /** Premium Phase 2. */
    QUIZ_ARENA("Advanced Quiz Arena", "Timed quizzes, difficult questions and full-length mock tests"),
    /** Premium Code Battle. */
    CODE_BATTLE("Code Battle", "Timed Python challenges checked by hidden tests, with records and trophies"),
}

/** Where the current access comes from. Only [LOCAL_DEV] exists today. */
enum class PremiumSource { NONE, LOCAL_DEV }

/** The set of unlocked Premium features. [FREE] unlocks nothing (every existing feature still works). */
data class Entitlements(
    val features: Set<PremiumFeature> = emptySet(),
    val source: PremiumSource = PremiumSource.NONE,
) {
    fun has(feature: PremiumFeature): Boolean = feature in features
    val isPremium: Boolean get() = features.isNotEmpty()

    companion object {
        val FREE = Entitlements()
        fun all(source: PremiumSource) = Entitlements(PremiumFeature.entries.toSet(), source)
    }
}

interface PremiumRepository {
    val entitlements: Flow<Entitlements>
    /** True only for the local development/testing implementation. */
    val isDevToggle: Boolean
    /** Development/testing only: turns local Premium access on or off. */
    suspend fun setDevPremium(enabled: Boolean)
}

// Its own DataStore file, so "Reset progress" never changes Premium access and vice versa.
private val Context.premiumStore: DataStore<Preferences> by preferencesDataStore(name = "pypath_premium")

/** Local developer/testing switch. NOT secure and NOT a purchase (see the note at the top of this file). */
class LocalDevPremiumRepository(private val context: Context) : PremiumRepository {
    private val key = booleanPreferencesKey("dev_premium_enabled")

    override val isDevToggle: Boolean = true

    override val entitlements: Flow<Entitlements> = context.premiumStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { if (it[key] == true) Entitlements.all(PremiumSource.LOCAL_DEV) else Entitlements.FREE }

    override suspend fun setDevPremium(enabled: Boolean) {
        context.premiumStore.edit { it[key] = enabled }
    }
}

/** In-memory version for previews and unit tests. */
class FakePremiumRepository(initial: Boolean = false) : PremiumRepository {
    private val state = MutableStateFlow(initial)
    override val isDevToggle: Boolean = true
    override val entitlements: Flow<Entitlements> =
        state.map { if (it) Entitlements.all(PremiumSource.LOCAL_DEV) else Entitlements.FREE }
    override suspend fun setDevPremium(enabled: Boolean) { state.value = enabled }
}
