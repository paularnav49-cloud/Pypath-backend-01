package com.pypath.app

import android.app.Application
import com.pypath.app.audio.SoundManager
import com.pypath.app.data.activity.ActivityRepository
import com.pypath.app.data.activity.LocalActivityRepository
import com.pypath.app.data.auth.AuthRepository
import com.pypath.app.data.auth.LocalAuthRepository
import com.pypath.app.data.content.AssetCourseRepository
import com.pypath.app.data.content.CourseRepository
import com.pypath.app.data.progress.LocalProgressRepository
import com.pypath.app.data.progress.ProgressRepository
import com.pypath.app.data.settings.LocalSettingsRepository
import com.pypath.app.data.settings.SettingsRepository
import com.pypath.app.data.programs.FileProgramRepository
import com.pypath.app.data.programs.ProgramRepository
import com.pypath.app.premium.LocalDevPremiumRepository
import com.pypath.app.premium.PremiumRepository
import com.pypath.app.premium.FilePremiumStudyRepository
import com.pypath.app.premium.PremiumStudyStore
import com.pypath.app.premium.BattleStore
import com.pypath.app.premium.FileBattleRepository
import com.pypath.app.cloud.AccountCheckApi
import com.pypath.app.cloud.AccountDeletionApi
import com.pypath.app.cloud.CloudStateApi
import com.pypath.app.cloud.ConsentApi
import com.pypath.app.cloud.LocalConsentStore
import com.pypath.app.cloud.CloudSync
import com.pypath.app.cloud.KeystoreSessionStore
import com.pypath.app.cloud.LocalSyncMetaStore
import com.pypath.app.cloud.RepositoryCloudData
import com.pypath.app.cloud.SupabaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Simple manual DI container. Swap implementations here (e.g. remote content). */
class AppContainer(app: Application) {
    val courseRepository: CourseRepository = AssetCourseRepository(app)
    val progressRepository: ProgressRepository = LocalProgressRepository(app)
    val settingsRepository: SettingsRepository = LocalSettingsRepository(app)
    /** Batch 3: question history, XP per day and streak days (separate DataStore file). */
    val activityRepository: ActivityRepository = LocalActivityRepository(app)
    /** Google Sign-In: the signed-in account (separate DataStore file, kept on the phone only). */
    val authRepository: AuthRepository = LocalAuthRepository(app)
    /** Premium Phase 1: local development/testing Premium switch (NOT secure; replace with a backend check later). */
    val premiumRepository: PremiumRepository = LocalDevPremiumRepository(app)
    /** Premium Phase 1: saved programs of the Advanced IDE (files/programs/programs.json, opened on first use). */
    val programRepository: ProgramRepository by lazy { FileProgramRepository(File(app.filesDir, "programs")) }
    /** Premium Phase 2: challenge passes, project drafts and Quiz Arena results (files/premium/study.json, opened on first use). */
    val premiumStudyStore: PremiumStudyStore by lazy { PremiumStudyStore(FilePremiumStudyRepository(File(app.filesDir, "premium"))) }
    /** Premium Code Battle: battle records, history and trophies (files/premium/battle.json, opened on first use). */
    val battleStore: BattleStore by lazy { BattleStore(FileBattleRepository(File(app.filesDir, "premium"))) }
    /**
     * Cloud backup (Supabase) for signed-in learners. Main process only: the `:python` runner process
     * never creates the container, and its network access is locked by pypath_netlock.
     */
    val cloudSync: CloudSync by lazy {
        CloudSync(
            auth = SupabaseAuth(),
            api = CloudStateApi(),
            sessions = KeystoreSessionStore(app),
            local = RepositoryCloudData(progressRepository, activityRepository, programRepository, settingsRepository, File(app.filesDir, "cloud")),
            meta = LocalSyncMetaStore(app),
            accounts = authRepository,
            accountCheck = AccountCheckApi(),
            consentApi = ConsentApi(),
            consents = LocalConsentStore(app),
            deletion = AccountDeletionApi(),
        )
    }
    /** Lives as long as the app process; used for background cloud sync. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Sound effects for the Learn side only. Loaded on first use by MainActivity. */
    val sounds: SoundManager by lazy { SoundManager(app) }
}

class PyPathApp : Application() {
    /**
     * Created on first use. The `:python` process (Practical tab runner) also instantiates this
     * Application class but never touches the container, so it never opens the progress store
     * and never loads sounds.
     */
    val container: AppContainer by lazy { AppContainer(this) }
}
