package com.pypath.app.premium

import android.content.Context
import com.pypath.app.data.model.Question
import com.pypath.app.practical.PracticalTask
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Premium Phase 2 content, bundled in the APK (assets/premium/…, written and validated by
 * content/premium_content.py). Nothing is downloaded; every model reuses an existing one:
 * challenges are [PracticalTask]s (same tests, same checker) and difficult questions are [Question]s
 * (same quiz engine and question cards).
 */
object PremiumIds {
    /** Every Premium challenge id starts with this. Their completion never goes into course progress. */
    const val CHALLENGE_PREFIX = "premium-ch-"
    /** Workspace id of a project's free-build editor (no tests, Run only). */
    const val PROJECT_TASK_PREFIX = "premium-pr-"

    /** Premium Code Battle: every battle challenge id starts with this. Results go to the battle file only. */
    const val BATTLE_PREFIX = "premium-cb-"

    fun isChallenge(taskId: String): Boolean = taskId.startsWith(CHALLENGE_PREFIX)
    fun isPremiumTask(taskId: String): Boolean = isChallenge(taskId) || taskId.startsWith(PROJECT_TASK_PREFIX)
    fun isBattle(taskId: String): Boolean = taskId.startsWith(BATTLE_PREFIX)
}

// ─────────────────────────── Feature 1: Project Library ───────────────────────────

@Serializable
data class ProjectStep(val title: String, val text: String)

@Serializable
data class PremiumProject(
    val id: String,
    /** [COLLECTION_ADVANCED] or [COLLECTION_REAL_WORLD]. */
    val collection: String,
    val order: Int = 0,
    /** "easy", "medium" or "hard". */
    val difficulty: String = "easy",
    val title: String,
    val summary: String,
    val concepts: List<String> = emptyList(),
    val objectives: List<String> = emptyList(),
    val steps: List<ProjectStep> = emptyList(),
    val starterCode: String = "",
    val fileName: String = "main.py",
    /** Sub-level after which the project is a good fit (shown as advice, never a lock). */
    val recommendedAfter: String? = null,
    /** Console transcript of a finished version, run with sample answers. */
    val sampleRun: String = "",
    val sampleNote: String? = null,
    val challengeIds: List<String> = emptyList(),
) {
    /** The project's editor, wrapped as a test-less task so the existing editor, Run and console are reused. */
    fun workspaceTask(): PracticalTask = PracticalTask(
        id = PremiumIds.PROJECT_TASK_PREFIX + id, title = title, instructions = summary, starterCode = starterCode,
    )

    companion object {
        const val COLLECTION_ADVANCED = "advanced"
        const val COLLECTION_REAL_WORLD = "realworld"
    }
}

@Serializable
data class PremiumProjectCatalog(
    val schemaVersion: Int = 1,
    val projects: List<PremiumProject> = emptyList(),
    val challenges: List<PracticalTask> = emptyList(),
) {
    val advanced: List<PremiumProject> get() = projects.filter { it.collection == PremiumProject.COLLECTION_ADVANCED }.sortedBy { it.order }
    val realWorld: List<PremiumProject> get() = projects.filter { it.collection == PremiumProject.COLLECTION_REAL_WORLD }.sortedBy { it.order }
    fun project(id: String): PremiumProject? = projects.firstOrNull { it.id == id }
    fun challenge(id: String): PracticalTask? = challenges.firstOrNull { it.id == id }
    fun projectOf(challengeId: String): PremiumProject? = projects.firstOrNull { challengeId in it.challengeIds }
}

// ─────────────────────────── Feature 2: Quiz Arena ───────────────────────────

@Serializable
data class TimedConfig(
    val questionCount: Int = 10,
    val minuteOptions: List<Int> = listOf(3, 5, 10),
    val defaultMinutes: Int = 5,
)

@Serializable
data class HardSet(
    val id: String,
    val title: String,
    val description: String = "",
    /** Unlocks when this sub-level is completed, so every question uses concepts already taught. */
    val afterSubLevel: String? = null,
    val questions: List<Question> = emptyList(),
)

@Serializable
data class MockDef(
    val id: String,
    val title: String,
    val description: String = "",
    val levels: List<Int> = emptyList(),
    val perLevel: Int = 3,
    val minutes: Int = 30,
    val seed: Int = 1,
) {
    val questionCount: Int get() = levels.size * perLevel
}

@Serializable
data class ArenaCatalog(
    val schemaVersion: Int = 1,
    val timed: TimedConfig = TimedConfig(),
    val hardSets: List<HardSet> = emptyList(),
    val mocks: List<MockDef> = emptyList(),
)

object PremiumContentParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun projects(text: String): PremiumProjectCatalog = json.decodeFromString(PremiumProjectCatalog.serializer(), text)
    fun arena(text: String): ArenaCatalog = json.decodeFromString(ArenaCatalog.serializer(), text)
}

fun loadPremiumProjects(context: Context): PremiumProjectCatalog =
    context.assets.open("premium/projects.json").bufferedReader().use { PremiumContentParser.projects(it.readText()) }

fun loadArenaCatalog(context: Context): ArenaCatalog =
    context.assets.open("premium/arena.json").bufferedReader().use { PremiumContentParser.arena(it.readText()) }
