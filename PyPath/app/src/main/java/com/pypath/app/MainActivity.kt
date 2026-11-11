package com.pypath.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pypath.app.audio.LocalSounds
import com.pypath.app.ui.AppState
import com.pypath.app.ui.AppViewModel
import com.pypath.app.ui.navigation.PyPathNavHost
import com.pypath.app.ui.theme.PyPathTheme

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash until saved progress (and the Google account) is loaded, so we know whether
        // to show onboarding or the sign-in screen.
        splash.setKeepOnScreenCondition { appViewModel.state.value is AppState.Loading || appViewModel.account.value == null }
        enableEdgeToEdge()
        val container = (application as PyPathApp).container
        val sounds = container.sounds.also { it.ensureLoaded() }
        // Cloud backup for signed-in learners (does nothing for guests).
        container.cloudSync.start(container.appScope)
        setContent {
            PyPathTheme {
                CompositionLocalProvider(LocalSounds provides sounds) {
                    PyPathNavHost(appViewModel)
                }
            }
        }
    }

    override fun onStop() {
        // Leaving the app: send any unsynced change now (kept on the phone if offline).
        (application as PyPathApp).container.cloudSync.requestSync(immediate = true)
        super.onStop()
    }

    override fun onDestroy() {
        // Free the SoundPool when the user leaves the app (not on rotation).
        if (isFinishing) (application as PyPathApp).container.sounds.release()
        super.onDestroy()
    }
}
