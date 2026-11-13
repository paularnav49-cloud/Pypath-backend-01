package com.pypath.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the real Practical tab UI end to end: type code, Run, input(), errors, Stop. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PracticalTabUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val long = 120_000L

    private fun consoleContains(s: String) = SemanticsMatcher("console contains $s") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Console output") == true &&
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(s) } == true
    }

    private fun openPractical() {
        rule.waitUntil(long) {
            rule.onAllNodes(hasText("Skip")).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(hasContentDescription("Practical tab")).fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodes(hasText("Skip")).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Skip").performClick()
            rule.waitUntilAtLeastOneExists(hasText("Start learning"), long)
            rule.onNodeWithText("Start learning").performClick()
        }
        rule.waitUntilAtLeastOneExists(hasContentDescription("Practical tab"), long)
        rule.onNodeWithContentDescription("Practical tab").performClick()
        rule.waitUntilAtLeastOneExists(hasContentDescription("Python code editor"), long)
    }

    private fun typeAndRun(code: String) {
        rule.onNodeWithContentDescription("Python code editor").performScrollTo().performTextReplacement(code)
        rule.onNodeWithContentDescription("Run code").performScrollTo().assertIsEnabled().performClick()
    }

    @Test fun writeRunInputErrorAndStop() {
        openPractical()

        // Real execution of code typed into the editor.
        typeAndRun("x = 25\ny = 15\nprint(x + y)")
        rule.waitUntilAtLeastOneExists(consoleContains("40\n"), long)
        rule.waitUntilAtLeastOneExists(hasContentDescription("Status: Done", substring = true), long)

        typeAndRun("x = 100\ny = 200\nprint(x * y)")
        rule.waitUntilAtLeastOneExists(consoleContains("20000"), long)

        // input(): prompt shows, the learner answers inside the app, execution continues.
        typeAndRun("name = input(\"Enter your name: \")\nprint(\"Hello\", name)")
        rule.waitUntilAtLeastOneExists(hasContentDescription("Program input"), long)
        rule.waitUntilAtLeastOneExists(consoleContains("Enter your name: "), long)
        rule.onNodeWithContentDescription("Program input").performTextInput("Arnav")
        rule.onNodeWithContentDescription("Program input").performImeAction()
        rule.waitUntilAtLeastOneExists(consoleContains("Hello Arnav"), long)

        // Real traceback + error card.
        typeAndRun("print(undefined_variable)")
        rule.waitUntilAtLeastOneExists(consoleContains("NameError: name 'undefined_variable' is not defined"), long)
        rule.waitUntilAtLeastOneExists(hasText("NameError"), long)
        rule.waitUntilAtLeastOneExists(hasText("Go to line 1"), long)

        // An endless loop hits the execution limit; the app stays responsive.
        // (The Stop button itself is covered by PythonRunnerTest.test7 and manual testing: while
        // code runs the status timer keeps Compose busy, so the test framework can't click mid-run.)
        typeAndRun("n = 0\nwhile True:\n    n += 1")
        rule.waitUntilAtLeastOneExists(consoleContains("Execution timed out after 10 seconds"), long)
        rule.waitUntilAtLeastOneExists(hasText("Execution timed out"), long)

        // Fresh interpreter afterwards.
        typeAndRun("print('back', sum(range(101)))")
        rule.waitUntilAtLeastOneExists(consoleContains("back 5050"), long)

        // Clear output.
        rule.onNodeWithContentDescription("Clear output").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Output will appear here", substring = true), long)
    }
}
