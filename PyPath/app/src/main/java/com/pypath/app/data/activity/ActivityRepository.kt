package com.pypath.app.data.activity

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Separate file from "pypath_progress", so question history never slows down progress saves. */
private val Context.activityStore: DataStore<Preferences> by preferencesDataStore(name = "pypath_activity")

/** Learning activity (question history, XP per day, streak days). Local only. */
interface ActivityRepository {
    val activity: Flow<ActivityData>
    /** Applies [transform] in one transaction. */
    suspend fun update(transform: (ActivityData) -> ActivityData)
    /** "Reset progress" clears all activity too. */
    suspend fun reset()
}

class LocalActivityRepository(private val context: Context) : ActivityRepository {

    private val key = stringPreferencesKey("activity_json")

    override val activity: Flow<ActivityData> =
        context.activityStore.data.map { prefs -> ActivityUpdates.migrate(ActivityUpdates.decode(prefs[key])) }

    override suspend fun update(transform: (ActivityData) -> ActivityData) {
        context.activityStore.edit { prefs ->
            val current = ActivityUpdates.migrate(ActivityUpdates.decode(prefs[key]))
            val next = transform(current)
            if (next != current || prefs[key] == null) prefs[key] = ActivityUpdates.encode(next)
        }
    }

    override suspend fun reset() {
        context.activityStore.edit { it.clear() }
    }
}
