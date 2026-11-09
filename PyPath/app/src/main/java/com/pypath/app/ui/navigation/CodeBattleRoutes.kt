package com.pypath.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pypath.app.audio.LocalSounds
import com.pypath.app.audio.Sfx
import com.pypath.app.premium.PremiumFeature
import com.pypath.app.ui.AppViewModel
import com.pypath.app.ui.components.AppTopBar
import com.pypath.app.ui.screens.practical.PracticalViewModel
import com.pypath.app.ui.screens.premium.BattleScreen
import com.pypath.app.ui.screens.premium.BattleTestingState
import com.pypath.app.ui.screens.premium.BattleWorkspace
import com.pypath.app.ui.screens.premium.CodeBattleScreen
import com.pypath.app.ui.screens.premium.CodeBattleViewModel
import com.pypath.app.ui.screens.premium.PremiumLockedNotice
import com.pypath.app.ui.theme.AppTheme

/** Premium Code Battle destinations. Both check the Phase 1 entitlement before showing anything. */
object CodeBattleRoutes {
    const val HOME = "code_battle"
    const val BATTLE = "code_battle/{id}"
    fun battle(id: String) = "code_battle/$id"
}

@Composable
internal fun CodeBattleLocked(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
        AppTopBar(title = "Code Battle", onBack = onBack)
        PremiumLockedNotice(
            "Code Battle is a Premium feature",
            "Timed Python challenges with hidden tests, records and trophies. Turn on Premium from Home → ⋮ menu → Premium. " +
                "Everything in the Free version keeps working as before.",
        )
    }
}

internal fun NavGraphBuilder.codeBattleRoutes(
    nav: NavHostController,
    vm: AppViewModel,
    practicalVm: PracticalViewModel,
    activityOwner: ViewModelStoreOwner,
) {
    composable(CodeBattleRoutes.HOME) {
        val premium by vm.entitlements.collectAsStateWithLifecycle()
        if (!premium.has(PremiumFeature.CODE_BATTLE)) { LockedOffer(premium, PremiumFeature.CODE_BATTLE) { nav.popBackStack() }; return@composable }
        val bvm: CodeBattleViewModel = viewModel(activityOwner)
        LaunchedEffect(Unit) { bvm.load() }
        val data by bvm.data.collectAsStateWithLifecycle()
        CodeBattleScreen(
            catalog = bvm.catalog, data = data, remainingMs = bvm.remainingMs(), loadError = bvm.loadError,
            onBack = { nav.popBackStack() },
            onOpen = { nav.navigate(CodeBattleRoutes.battle(it)) { launchSingleTop = true } },
        )
    }

    composable(CodeBattleRoutes.BATTLE) { entry ->
        val premium by vm.entitlements.collectAsStateWithLifecycle()
        if (!premium.has(PremiumFeature.CODE_BATTLE)) { LockedOffer(premium, PremiumFeature.CODE_BATTLE) { nav.popBackStack() }; return@composable }
        val bvm: CodeBattleViewModel = viewModel(activityOwner)
        LaunchedEffect(Unit) { bvm.load() }
        val dataOrNull by bvm.data.collectAsStateWithLifecycle()
        val catalog = bvm.catalog
        val data = dataOrNull
        if (catalog == null || data == null) {
            Box(Modifier.fillMaxSize().background(AppTheme.colors.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppTheme.colors.brand)
            }
            return@composable
        }
        val ch = catalog.challenge(entry.arguments?.getString("id").orEmpty())
        if (ch == null) { CodeBattleLocked { nav.popBackStack() }; return@composable }
        val task = ch.task()
        val active = data.active?.takeIf { it.challengeId == ch.id }
        val check = practicalVm.checkState?.takeIf { it.taskId == task.id }
        BattleScreen(
            ch = ch, data = data, remainingMs = bvm.remainingMs(), outcome = bvm.outcome,
            testing = if (active?.submitted == true) BattleTestingState(
                current = ((check?.report?.results?.size ?: 0) + 1).coerceAtMost(task.tests.size),
                total = task.tests.size, problem = bvm.testProblem,
            ) else null,
            onBack = { nav.popBackStack() },
            onStart = { bvm.start(ch) },
            onSubmit = { bvm.submit(practicalVm.code(task).text) },
            onGiveUp = { bvm.giveUp() },
            onRetryTests = { bvm.retryTests() },
            onOpenOther = { id -> nav.navigate(CodeBattleRoutes.battle(id)) { popUpTo(CodeBattleRoutes.BATTLE) { inclusive = true } } },
            onDoneResult = { bvm.consumeOutcome() },
            otherTitle = { id -> catalog.challenge(id)?.title },
            workspace = { editable ->
                active?.let { a -> BattleWorkspace(practicalVm, bvm, task, a.startedAt, a.code, a.submittedCode, editable) }
            },
        )
        // Runs the hidden tests after Submit (also after a timeout or when coming back to a submitted battle).
        LaunchedEffect(active?.submittedAt, practicalVm.phase, practicalVm.checkState, bvm.testProblem) {
            bvm.drive(practicalVm, ch)
        }
        val sounds = LocalSounds.current
        LaunchedEffect(practicalVm.successSoundPending) {
            if (practicalVm.successSoundPending) { sounds.play(Sfx.CORRECT); practicalVm.consumeSuccessSound() }
        }
    }
}
