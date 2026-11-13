package com.pypath.app.practical

import android.content.Context
import com.pypath.app.domain.CourseSnapshot
import com.pypath.app.domain.NodeStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Hands-on coding tasks for the Practical tab, loaded from `assets/practical/tasks.json`.
 *
 * Each task names the sub-level that teaches its last required concept ([afterSubLevel]).
 * A task unlocks only once that sub-level is completed, so tasks never require concepts the
 * learner hasn't been taught yet. A task with no [afterSubLevel] (the playground) is always open.
 *
 * Tasks with [tests] can be checked automatically (Check button, see TaskChecks.kt). Tasks without
 * tests keep working exactly as before: Run only, nothing is graded. All new fields have defaults,
 * so older tasks.json files still parse.
 */
@Serializable
data class PracticalTask(
    val id: String,
    val title: String,
    val afterSubLevel: String? = null,
    val concepts: List<String> = emptyList(),
    val instructions: String,
    val requirements: List<String> = emptyList(),
    /** Example interaction shown to explain the goal. Not used to judge the learner's output. */
    val example: String? = null,
    val hint: String? = null,
    val starterCode: String = "",
    /** "easy", "medium" or "hard". Display only. */
    val difficulty: String? = null,
    /** How [tests] compare output: "exact" (default), "contains" or "regex". See [CheckMode]. */
    val checkMode: String = "exact",
    /** For checkMode "contains": strings every test's output must include (a test may override). */
    val mustContain: List<String> = emptyList(),
    /** For checkMode "regex": pattern searched for in every test's output (a test may override). */
    val pattern: String? = null,
    val tests: List<TaskTest> = emptyList(),
) {
    /** True when the task has a Check button. */
    val checkable: Boolean get() = tests.isNotEmpty()
    val mode: CheckMode get() = CheckMode.from(checkMode)
}

/**
 * One automatic test: the program is run once with [stdin] as the answers to its input() calls.
 * [expectedOutput] is what the console shows for a correct program (prompts + echoed answers +
 * printed text). For "contains" and "regex" tasks it is an example of a correct output.
 */
@Serializable
data class TaskTest(
    val stdin: List<String> = emptyList(),
    val expectedOutput: String,
    val description: String? = null,
    /** A hidden test never reveals its input or output; a failure only says "Hidden test failed". */
    val hidden: Boolean = false,
    val mustContain: List<String>? = null,
    val pattern: String? = null,
)

@Serializable
data class PracticalCatalog(val schemaVersion: Int = 1, val tasks: List<PracticalTask>)

object PracticalCatalogParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): PracticalCatalog = json.decodeFromString(PracticalCatalog.serializer(), text)
}

fun loadPracticalCatalog(context: Context, path: String = "practical/tasks.json"): PracticalCatalog =
    context.assets.open(path).bufferedReader().use { PracticalCatalogParser.parse(it.readText()) }

/** Lock state of a task for the current learner. */
data class TaskAvailability(
    val task: PracticalTask,
    val unlocked: Boolean,
    /** e.g. "1.4 Input and Output" — the sub-level that unlocks it. */
    val requirementLabel: String?,
    /** True when the unlocking sub-level belongs to a level that isn't released yet. */
    val comingSoon: Boolean,
)

fun availability(tasks: List<PracticalTask>, snapshot: CourseSnapshot): List<TaskAvailability> = tasks.map { t ->
    val req = t.afterSubLevel?.let { snapshot.subLevel(it) }
    TaskAvailability(
        task = t,
        unlocked = t.afterSubLevel == null || req?.status == NodeStatus.COMPLETED,
        requirementLabel = req?.let { "${it.subLevel.code} ${it.subLevel.title}" },
        comingSoon = req?.status == NodeStatus.COMING_SOON,
    )
}
