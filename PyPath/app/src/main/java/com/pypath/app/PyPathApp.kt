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
import java.io.File

/** Simple manual DI container. Swap implementations here (e.g. remote content, cloud sync). */
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
