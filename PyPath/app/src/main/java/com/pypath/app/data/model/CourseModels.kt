package com.pypath.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Course content model.
 *
 * All learning content is data-driven (loaded from assets/course/course.json) so new
 * levels, sub-levels, lessons and questions can be added without touching UI code.
 *
 * Hierarchy:  Course → Level → SubLevel → Steps (Learn, Quiz, [Practice], ...)
 */
@Serializable
data class Course(
    val id: String,
    val title: String,
    val schemaVersion: Int = 1,
    val levels: List<Level> = emptyList(),
    /**
     * Since schema 2 the course index (course.json) lists one JSON file per level, relative to
     * the index (e.g. "levels/l1.json"). The loader reads them and fills [levels].
     */
    val levelFiles: List<String> = emptyList(),
    /** Since v0.4.0: glossary file relative to the index (e.g. "glossary.json"). The loader fills [glossary]. */
    val glossaryFile: String? = null,
    val glossary: List<GlossaryTerm> = emptyList(),
)

@Serializable
data class Level(
    val id: String,
    val number: Int,
    val title: String,
    val description: String,
    /** When false the level is shown as "Coming soon" and can never be unlocked. */
    val available: Boolean = true,
    val subLevels: List<SubLevel>,
    /**
     * Reserved for future level-final projects / assessments. Kept nullable so
     * existing content files stay valid when the feature ships.
     */
    val finalAssessment: AssessmentRef? = null,
    /**
     * Optional guided projects for this level ("level projects"). They unlock when the level is
     * finished and never block the main path, badges or overall progress.
     */
    val projects: List<SubLevel> = emptyList(),
    /** One-page summary of the level, unlocked when the level is finished. */
    val cheatSheet: CheatSheet? = null,
    /**
     * Batch 1: optional advanced extension placed after the Level 7 final project. Advanced levels unlock
     * in order like every other level, but the course certificate only requires the core (non-advanced) levels.
     */
    val advanced: Boolean = false,
)

@Serializable
data class AssessmentRef(
    val id: String,
    val type: String,
)

@Serializable
data class SubLevel(
    val id: String,
    /** Display code such as "1.2". */
    val code: String,
    val title: String,
    val summary: String,
    val estimatedMinutes: Int = 5,
    /**
     * Ordered list of steps the learner must finish to complete the sub-level.
     * Defaults to LEARN → QUIZ. Future content can add PRACTICE, PROJECT, etc.
     */
    val steps: List<StepType> = listOf(StepType.LEARN, StepType.QUIZ),
    val lesson: Lesson? = null,
    val quiz: Quiz? = null,
    /** "lesson" (Learn + MCQs) or "project" (guided build, see [briefing] and [stages]). */
    val type: String = TYPE_LESSON,
    val briefing: ProjectBriefing? = null,
    /** Project only: the finished program, one entry per line. */
    val finalCode: List<String> = emptyList(),
    val stages: List<ProjectStage> = emptyList(),
    /** Project only: short "How it works" notes shown at the end. */
    val walkthrough: List<String> = emptyList(),
) {
    val isProject: Boolean get() = type == TYPE_PROJECT

    companion object {
        const val TYPE_LESSON = "lesson"
        const val TYPE_PROJECT = "project"
    }
}

@Serializable
enum class StepType {
    @SerialName("learn") LEARN,
    @SerialName("quiz") QUIZ,
    @SerialName("practice") PRACTICE,
    @SerialName("project") PROJECT;

    /** Steps the current build can actually run. Others render as "Coming soon". */
    val isSupported: Boolean get() = this == LEARN || this == QUIZ || this == PROJECT

    val label: String
        get() = when (this) {
            LEARN -> "Learn"
            QUIZ -> "MCQs"
            PRACTICE -> "Practice"
            PROJECT -> "Project"
        }
}

// ─────────────────────────── Lesson ───────────────────────────

/** A lesson is split into short pages so learners never face a wall of text. */
@Serializable
data class Lesson(
    val pages: List<LessonPage>,
)

@Serializable
data class LessonPage(
    val title: String,
    val blocks: List<LessonBlock>,
)

@Serializable
sealed interface LessonBlock {
    @Serializable @SerialName("text")
    data class Text(val text: String) : LessonBlock

    @Serializable @SerialName("bullets")
    data class Bullets(val items: List<String>) : LessonBlock

    @Serializable @SerialName("code")
    data class Code(
        val code: String,
        val output: String? = null,
        /** Line-by-line or overall plain-language explanation of the code. */
        val explanation: List<String> = emptyList(),
        val caption: String? = null,
    ) : LessonBlock

    @Serializable @SerialName("tip")
    data class Tip(val title: String = "Tip", val text: String) : LessonBlock

    @Serializable @SerialName("keypoint")
    data class KeyPoint(val text: String) : LessonBlock
}

// ─────────────────────────── Quiz / MCQ ───────────────────────────

@Serializable
data class Quiz(
    val questions: List<Question>,
    /** Minimum percentage of correct answers required to complete the sub-level. */
    val passPercent: Int = 60,
)

@Serializable
data class Question(
    val id: String,
    val prompt: String,
    /** Optional code shown with the question (e.g. "What does this print?"). */
    val code: String? = null,
    /** MCQ only. Empty for the Batch 3 question types. */
    val options: List<String> = emptyList(),
    /** MCQ only. */
    val correctIndex: Int = 0,
    val explanation: String,
    // ── Batch 1 (optional, safe defaults; older content without these fields still loads) ──
    /** One-sentence nudge shown behind a "Hint" button before answering. Never reveals the answer. */
    val hint: String? = null,
    /**
     * One message per option, aligned with [options] by index. After a wrong answer the message for
     * the chosen option is shown above [explanation]. Ignored unless it has exactly one entry per option.
     */
    val optionFeedback: List<String>? = null,
    // ── Batch 3: question types (optional, default "mcq", so every existing question stays valid) ──
    /** One of [QuestionType]: "mcq", "predict", "fill" or "order". Unknown values are treated as "mcq". */
    val type: String = QuestionType.MCQ,
    /** predict: the exact output of [code]. Compared line by line, ignoring extra spaces and blank lines. */
    val answer: String? = null,
    /** predict: other outputs that are also accepted (compared the same way). */
    val alternatives: List<String> = emptyList(),
    /** predict: when false, capital letters don't matter. */
    val caseSensitive: Boolean = true,
    /** fill: the correct token for each blank, in order. [code] marks blank n as {{n}} (1-based). */
    val blanks: List<String> = emptyList(),
    /** fill: wrong tokens mixed into the bank (1 or 2). */
    val distractors: List<String> = emptyList(),
    /** order: the lines in the correct order, each with its leading spaces (indentation). */
    val lines: List<String> = emptyList(),
    /** order: other line orders (as indexes into [lines]) that are also correct. */
    val alternativeOrders: List<List<Int>> = emptyList(),
    /** fill and order: what the finished program prints, shown as the goal. */
    val output: String? = null,
) {
    /** The type this app knows how to show; anything unknown falls back to MCQ. */
    val kind: String get() = if (type in QuestionType.ALL) type else QuestionType.MCQ
    val isMcq: Boolean get() = kind == QuestionType.MCQ

    /** Feedback written for [option], or null when the question has none (UI then shows only [explanation]). */
    fun feedbackFor(option: Int?): String? {
        if (!isMcq) return null
        val list = optionFeedback ?: return null
        if (option == null || list.size != options.size) return null
        return list.getOrNull(option)?.takeIf { it.isNotBlank() }
    }
}

/** Batch 3: question types. */
object QuestionType {
    const val MCQ = "mcq"
    const val PREDICT = "predict"
    const val FILL = "fill"
    const val ORDER = "order"
    val ALL = setOf(MCQ, PREDICT, FILL, ORDER)
}

// ─────────────────────────── Project (guided build) ───────────────────────────

@Serializable
data class ProjectBriefing(
    val title: String,
    /** Short paragraphs explaining what the learner will build. */
    val text: List<String> = emptyList(),
    /** Why the project is useful. */
    val why: List<String> = emptyList(),
    val concepts: List<ConceptRef> = emptyList(),
    /** Exact console output of the finished program. */
    val sampleOutput: String = "",
    val usesInput: Boolean = false,
    /** File name shown above the code, e.g. "expense_tracker.py". */
    val fileName: String = "main.py",
    /** For programs that use input(): the answers typed in [sampleOutput]. */
    val sampleInputs: List<String> = emptyList(),
)

/** Link from the project briefing back to the sub-level that taught a concept. */
@Serializable
data class ConceptRef(val subLevelId: String, val label: String)

@Serializable
data class ProjectStage(
    val id: String,
    val title: String,
    val instruction: String,
    /** 1-based line numbers of [SubLevel.finalCode] that appear when this stage is answered correctly. */
    val revealsLines: List<Int>,
    /** Render options in the code font. */
    val optionsAreCode: Boolean = true,
    val question: Question,
    /** One message per option: why it is right (correct option) or why it is wrong. */
    val optionFeedback: List<String> = emptyList(),
)

// ─────────────────────────── Cheat sheets ───────────────────────────

@Serializable
data class CheatSheet(
    val title: String,
    val sections: List<CheatSection> = emptyList(),
    /** Short "remember" points shown at the end. */
    val remember: List<String> = emptyList(),
)

@Serializable
data class CheatSection(val heading: String, val items: List<CheatItem> = emptyList())

@Serializable
data class CheatItem(val code: String, val note: String, val output: String? = null)

// ─────────────────────────── Glossary ───────────────────────────

@Serializable
data class Glossary(val terms: List<GlossaryTerm> = emptyList())

@Serializable
data class GlossaryTerm(
    val term: String,
    /** Sub-level that teaches the term (e.g. "l1s2"). */
    val subLevelId: String,
    val level: Int,
    val definition: String,
    val example: String? = null,
    val output: String? = null,
    /** Other words that should find this term in search. */
    val aliases: List<String> = emptyList(),
)
