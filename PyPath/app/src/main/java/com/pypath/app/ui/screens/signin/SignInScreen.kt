package com.pypath.app.ui.screens.signin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pypath.app.auth.GoogleSignIn
import com.pypath.app.auth.PendingGoogleSignUp
import com.pypath.app.cloud.AuthMode
import com.pypath.app.auth.SignInResult
import com.pypath.app.data.auth.GoogleAccount
import com.pypath.app.ui.components.AppButton
import com.pypath.app.ui.components.ButtonKind
import com.pypath.app.ui.screens.onboarding.Logo
import com.pypath.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * Account screen with exactly three choices:
 *  1. "Sign in with Google" (primary): creates the PyPath account, or opens it if it already exists.
 *  2. "Login with Google" (secondary): opens an existing account only; never creates one.
 *  3. "Continue without sign in": guest mode (the first lesson only).
 * After 1 or 2, the Privacy Policy must be accepted before the dashboard opens.
 */
@Composable
fun SignInScreen(
    onSignedIn: (GoogleAccount) -> Unit,
    onSkip: () -> Unit,
) {
    val c = AppTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Logo(size = 64)
        Spacer(Modifier.height(24.dp))
        Text(
            "Welcome to PyPath", style = MaterialTheme.typography.headlineSmall, color = c.text, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Create your PyPath account with Google, or log in to the account you already have.",
            style = MaterialTheme.typography.bodyLarge, color = c.textMuted, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(32.dp))
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GoogleAuthButtons(onSignedIn = onSignedIn, modifier = Modifier.fillMaxWidth())
            AppButton("Continue without sign in", onClick = onSkip, kind = ButtonKind.Ghost, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Without an account you can try the first lesson. Sign in to unlock the full course; your progress " +
                "stays on this phone and is also backed up privately to your PyPath account.",
            style = MaterialTheme.typography.bodySmall, color = c.textFaint, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "PyPath is designed for learners aged 12 and up. If you are under 18, please ask a parent or " +
                "guardian before creating an account.",
            style = MaterialTheme.typography.bodySmall, color = c.textFaint, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}

/**
 * "Sign in with Google" (primary, creates or opens the account) and "Login with Google" (secondary,
 * existing accounts only). When Login finds no account, shows "Account not found." with a visible
 * "Sign in with Google" option that creates the account for the same Google identity.
 * Reused by the sign-in screen, the guest gate and the account dialog.
 */
@Composable
fun GoogleAuthButtons(
    onSignedIn: (GoogleAccount) -> Unit,
    modifier: Modifier = Modifier,
    signUpLabel: String = "Sign in with Google",
    logInLabel: String = "Login with Google",
) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<AuthMode?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // In memory only: the Google identity picked during a Login that found no account.
    var notFound by remember { mutableStateOf<PendingGoogleSignUp?>(null) }

    fun handle(r: SignInResult) {
        when (r) {
            is SignInResult.Success -> { notFound = null; onSignedIn(r.account) }
            SignInResult.Cancelled -> Unit
            is SignInResult.Failure -> error = r.message
            is SignInResult.AccountNotFound -> notFound = r.pending
        }
    }

    fun run(mode: AuthMode, block: suspend () -> SignInResult) {
        if (busy != null) return
        error = null
        busy = mode
        scope.launch {
            try { handle(block()) } finally { busy = null }
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GoogleStyledButton(
            label = signUpLabel, busy = busy == AuthMode.SIGN_UP, enabled = busy == null,
            onClick = { notFound = null; run(AuthMode.SIGN_UP) { GoogleSignIn.signIn(context, AuthMode.SIGN_UP) } },
            modifier = Modifier.fillMaxWidth(),
        )
        AppButton(
            if (busy == AuthMode.LOG_IN) "Checking your account..." else logInLabel,
            onClick = { notFound = null; run(AuthMode.LOG_IN) { GoogleSignIn.signIn(context, AuthMode.LOG_IN) } },
            kind = ButtonKind.Secondary, enabled = busy == null, modifier = Modifier.fillMaxWidth(),
        )
        val pending = notFound
        if (pending != null) {
            AccountNotFoundCard(
                email = pending.email, busy = busy != null,
                onSignUp = { run(AuthMode.SIGN_UP) { GoogleSignIn.signUpWith(context, pending) } },
            )
        }
        val message = error
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = c.error, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun AccountNotFoundCard(email: String, busy: Boolean, onSignUp: () -> Unit) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.surfaceAlt).border(1.dp, c.error, shape).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Account not found.", style = MaterialTheme.typography.titleMedium, color = c.text,
            modifier = Modifier.semantics { heading() },
        )
        if (email.isNotBlank()) {
            Text(
                "There is no PyPath account for $email.", style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted, textAlign = TextAlign.Center,
            )
        }
        Text("Please use Sign in with Google to create an account.", style = MaterialTheme.typography.bodyMedium, color = c.text, textAlign = TextAlign.Center)
        GoogleStyledButton(label = "Sign in with Google", busy = busy, enabled = !busy, onClick = onSignUp, modifier = Modifier.fillMaxWidth())
    }
}

/** "Sign in with Google" for older call sites: the account-creating option only. */
@Composable
fun GoogleSignInButton(onSignedIn: (GoogleAccount) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        GoogleStyledButton(
            label = "Sign in with Google", busy = busy, enabled = !busy, modifier = Modifier.fillMaxWidth(),
            onClick = {
                error = null
                busy = true
                scope.launch {
                    try {
                        when (val r = GoogleSignIn.signIn(context, AuthMode.SIGN_UP)) {
                            is SignInResult.Success -> onSignedIn(r.account)
                            SignInResult.Cancelled -> Unit
                            is SignInResult.Failure -> error = r.message
                            is SignInResult.AccountNotFound -> Unit // never returned for SIGN_UP
                        }
                    } finally { busy = false }
                }
            },
        )
        val message = error
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = c.error, textAlign = TextAlign.Center)
        }
    }
}

/** Google-branded white (or dark) button with the "G". */
@Composable
fun GoogleStyledButton(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.heightIn(min = 54.dp).clip(shape)
            .background(if (c.isDark) Color(0xFF131314) else Color.White)
            .border(1.dp, if (c.isDark) Color(0xFF8E918F) else Color(0xFF747775), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.brand)
        } else {
            GoogleG(20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label, style = MaterialTheme.typography.labelLarge,
            color = if (c.isDark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F),
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

/** The four-colour Google "G", drawn with arcs (no image asset, no network). */
@Composable
fun GoogleG(size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.22f
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(inset, inset)
        val st = Stroke(width = stroke, cap = StrokeCap.Butt)
        drawArc(Color(0xFFEA4335), startAngle = -150f, sweepAngle = 105f, useCenter = false, topLeft = topLeft, size = arcSize, style = st)
        drawArc(Color(0xFFFBBC05), startAngle = 150f, sweepAngle = 60f, useCenter = false, topLeft = topLeft, size = arcSize, style = st)
        drawArc(Color(0xFF34A853), startAngle = 45f, sweepAngle = 105f, useCenter = false, topLeft = topLeft, size = arcSize, style = st)
        drawArc(Color(0xFF4285F4), startAngle = 0f, sweepAngle = 45f, useCenter = false, topLeft = topLeft, size = arcSize, style = st)
        // The bar of the G.
        val cy = this.size.height / 2
        drawRect(
            Color(0xFF4285F4),
            topLeft = Offset(this.size.width / 2, cy - stroke / 2),
            size = Size(this.size.width / 2 - inset + stroke / 2, stroke),
        )
    }
}

/** Round avatar with the account's initials (no profile photo download, the app stays offline). */
@Composable
fun AccountAvatar(account: GoogleAccount, size: Dp = 40.dp) {
    val c = AppTheme.colors
    Box(Modifier.size(size).clip(CircleShape).background(c.brand), contentAlignment = Alignment.Center) {
        Text(account.initials, color = c.onBrand, style = MaterialTheme.typography.labelLarge)
    }
}
