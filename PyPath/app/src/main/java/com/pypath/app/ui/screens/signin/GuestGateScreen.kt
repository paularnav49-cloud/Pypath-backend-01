package com.pypath.app.ui.screens.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pypath.app.data.auth.GoogleAccount
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.screens.onboarding.Logo
import com.pypath.app.ui.theme.AppTheme

/**
 * Shown to guests instead of anything past the first lesson: at the end of the first lesson
 * ([firstLessonDone] = true) and whenever a locked lesson, project or feature is opened in any way.
 */
@Composable
fun GuestGateScreen(
    firstLessonDone: Boolean,
    onSignedIn: (GoogleAccount) -> Unit,
    onNotNow: () -> Unit,
    notNowLabel: String = "Not now",
) {
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Logo(size = 64)
        Spacer(Modifier.height(24.dp))
        Text(
            if (firstLessonDone) "You've completed your first lesson!" else "Sign in to continue learning",
            style = MaterialTheme.typography.headlineSmall, color = c.text, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in or create an account to continue learning with PyPath.",
            style = MaterialTheme.typography.bodyLarge, color = c.textMuted, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (!firstLessonDone) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Without an account, only the first lesson is available.",
                style = MaterialTheme.typography.bodyMedium, color = c.textFaint, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GoogleAuthButtons(
                onSignedIn = onSignedIn, modifier = Modifier.fillMaxWidth(),
                signUpLabel = "Sign in with Google to Continue",
                logInLabel = "Login with Google to Continue",
            )
            AppButton(notNowLabel, onClick = onNotNow, kind = ButtonKind.Ghost, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Your progress from the first lesson stays on this phone and is added to your account when you sign in.",
            style = MaterialTheme.typography.bodySmall, color = c.textFaint, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}
