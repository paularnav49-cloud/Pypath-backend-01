package com.pypath.app.cloud

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pypath.app.data.activity.ActivityData
import com.pypath.app.data.activity.ActivityRepository
import com.pypath.app.data.programs.ProgramLibrary
import com.pypath.app.data.programs.ProgramRepository
import com.pypath.app.data.progress.ProgressRepository
import com.pypath.app.data.progress.UserProgress
import com.pypath.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Sync bookkeeping kept on the phone (no secrets; the session itself is in [SessionStore]). */
data class SyncMeta(
    /** Supabase user id whose data is on this phone (null until the first successful sync). */
    val owner: String? = null,
    /** Last "Reset progress" on this phone, epoch ms (0 = never). */
    val resetAt: Long = 0,
    val revision: Long = 0,
    /** Fingerprint of the data last confirmed in the cloud. */
    val fingerprint: String = "",
    val lastSyncedAt: Long = 0,
)

interface SyncMetaStore {
    suspend fun load(): SyncMeta
    suspend fun update(transform: (SyncMeta) -> SyncMeta)
}

private val Context.cloudSyncStore: DataStore<Preferences> by preferencesDataStore(name = "pypath_cloud_sync")

class LocalSyncMetaStore(private val context: Context) : SyncMetaStore {
    private val owner = stringPreferencesKey("owner")
    private val resetAt = longPreferencesKey("reset_at")
    private val revision = longPreferencesKey("revision")
    private val fingerprint = stringPreferencesKey("fingerprint")
    private val lastSynced = longPreferencesKey("last_synced_at")

    private fun read(p: Preferences) = SyncMeta(p[owner], p[resetAt] ?: 0, p[revision] ?: 0, p[fingerprint].orEmpty(), p[lastSynced] ?: 0)

    override suspend fun load(): SyncMeta = read(context.cloudSyncStore.data.first())

    override suspend fun update(transform: (SyncMeta) -> SyncMeta) {
        context.cloudSyncStore.edit { p ->
            val m = transform(read(p))
            if (m.owner != null) p[owner] = m.owner else p.remove(owner)
            p[resetAt] = m.resetAt
            p[revision] = m.revision
            p[fingerprint] = m.fingerprint
            p[lastSynced] = m.lastSyncedAt
        }
    }
}

class MemorySyncMetaStore(var meta: SyncMeta = SyncMeta()) : SyncMetaStore {
    override suspend fun load() = meta
    override suspend fun update(transform: (SyncMeta) -> SyncMeta) { meta = transform(meta) }
}

/** The phone's copy of everything that is synced. */
interface CloudLocalData {
    suspend fun read(meta: SyncMeta): CloudSnapshot
    /** Merges [remote] into the local stores (each store in its own transaction). */
    suspend fun mergeIn(remote: CloudSnapshot, localResetAt: Long)
    /** Replaces local data with [remote] (another account's data is on this phone). */
    suspend fun replaceWith(remote: CloudSnapshot?)
    /** Emits after local learning data, programs or settings change. */
    val changes: Flow<Unit>
    /** Keeps another account's unsynced data aside on this phone (never uploaded to a different account). */
    suspend fun saveBackup(owner: String, snapshot: CloudSnapshot)
    suspend fun takeBackup(owner: String): CloudSnapshot?
    suspend fun deleteBackup(owner: String)
}

class RepositoryCloudData(
    private val progress: ProgressRepository,
    private val activity: ActivityRepository,
    private val programs: ProgramRepository,
    private val settings: SettingsRepository,
    private val backupDir: File?,
) : CloudLocalData {

    override suspend fun read(meta: SyncMeta): CloudSnapshot = CloudSnapshot(
        progress = ProgressEnvelope(resetAt = meta.resetAt, data = progress.progress.first()),
        activity = ActivityEnvelope(resetAt = meta.resetAt, data = activity.activity.first()),
        programs = ProgramsEnvelope(data = programs.load()),
        settings = SettingsEnvelope(data = settings.cloudSettings.first()),
    )

    override suspend fun mergeIn(remote: CloudSnapshot, localResetAt: Long) {
        val remoteReset = maxOf(remote.progress.resetAt, remote.activity.resetAt)
        if (remoteReset > localResetAt) {
            // Another phone pressed "Reset progress" after this phone did: follow it.
            progress.applySynced { cur -> remote.progress.data.copy(onboardingCompleted = remote.progress.data.onboardingCompleted || cur.onboardingCompleted) }
            activity.update { remote.activity.data }
        } else if (remoteReset == localResetAt) {
            progress.applySynced { cur -> CloudMerge.progress(cur, remote.progress.data) }
            activity.update { cur -> CloudMerge.activity(cur, remote.activity.data) }
        } // else: this phone reset more recently, so the cloud copy is replaced by the local one on upload.
        programs.applySynced { cur -> CloudMerge.programs(cur, remote.programs.data) }
        val cur = settings.cloudSettings.first()
        val merged = CloudMerge.settings(cur, remote.settings.data)
        if (merged != cur) settings.applyCloudSettings(merged)
    }

    override suspend fun replaceWith(remote: CloudSnapshot?) {
        progress.applySynced { cur ->
            val p = remote?.progress?.data ?: UserProgress()
            p.copy(onboardingCompleted = p.onboardingCompleted || cur.onboardingCompleted)
        }
        activity.update { remote?.activity?.data ?: ActivityData() }
        programs.applySynced(replaced = true) { remote?.programs?.data ?: ProgramLibrary() }
        // Settings: keep this phone's values, but let the account's own choices win.
        val cur = settings.cloudSettings.first().copy(stamps = emptyMap())
        settings.applyCloudSettings(if (remote != null) CloudMerge.settings(cur, remote.settings.data) else cur)
    }

    override val changes: Flow<Unit> = merge(
        progress.progress.drop(1).map { },
        activity.activity.drop(1).map { },
        settings.cloudSettings.drop(1).map { },
        programs.syncedChanges.map { },
    )

    private fun backupFile(owner: String): File? {
        val safe = owner.filter { it.isLetterOrDigit() || it == '-' }.take(64)
        if (backupDir == null || safe.isEmpty()) return null
        return File(backupDir, "unsynced-$safe.json")
    }

    override suspend fun saveBackup(owner: String, snapshot: CloudSnapshot) = withContext(Dispatchers.IO) {
        val f = backupFile(owner) ?: return@withContext
        val previous = readBackup(f)
        val combined = if (previous != null) CloudMerge.snapshot(snapshot, previous) else snapshot
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText("[" + CloudCodec.rowJson(combined, 0, null, "") + "]")
        if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
    }

    override suspend fun takeBackup(owner: String): CloudSnapshot? = withContext(Dispatchers.IO) {
        val f = backupFile(owner) ?: return@withContext null
        readBackup(f)
    }

    /** Called once the backup was merged and uploaded to its own account. */
    override suspend fun deleteBackup(owner: String) = withContext(Dispatchers.IO) { backupFile(owner)?.delete(); Unit }

    private fun readBackup(f: File): CloudSnapshot? =
        if (!f.exists()) null else runCatching { CloudCodec.parseRows(f.readText()).firstOrNull()?.snapshot }.getOrNull()
}
