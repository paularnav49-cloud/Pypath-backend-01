package com.pypath.app.privacy

import android.content.Context

/**
 * The PyPath Privacy Policy shown on the mandatory consent screen.
 *
 * Text: app/src/main/assets/legal/privacy_policy.txt (a copy for publishing on the web is in
 * docs/privacy-policy.md). Versions are dates (yyyy-MM-dd, optionally ".n"), so they sort as text.
 *
 * Changing the policy:
 *  - Edit the text and set [VERSION] to the new date.
 *  - If learners must agree again (a material change), also set [RENEWAL_REQUIRED_SINCE] to the new
 *    date. Every signed-in learner then sees the policy again before reaching the dashboard.
 *  - For small wording fixes keep [RENEWAL_REQUIRED_SINCE]: earlier consent stays valid.
 */
object PrivacyPolicy {
    const val VERSION = "2026-11-13.2"
    const val RENEWAL_REQUIRED_SINCE = "2026-11-13.2"
    const val ASSET = "legal/privacy_policy.txt"

    /** Consent to [version] is still valid for the current policy. */
    fun isConsentValid(version: String?): Boolean =
        !version.isNullOrBlank() && version >= RENEWAL_REQUIRED_SINCE

    fun load(context: Context): String =
        runCatching { context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrDefault("")

    /** One rendered piece of the policy. */
    sealed interface Block {
        data class Title(val text: String) : Block
        data class Heading(val text: String) : Block
        data class SubHeading(val text: String) : Block
        data class Paragraph(val text: String) : Block
        data class Bullet(val text: String) : Block
    }

    /**
     * Tiny markup: "# " title, "## " heading, "### " sub-heading, "- " bullet; other non-blank lines
     * are paragraphs (consecutive lines are joined). "{VERSION}" is replaced by [VERSION].
     */
    fun parse(text: String): List<Block> {
        val out = mutableListOf<Block>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotEmpty()) { out += Block.Paragraph(para.toString().trim()); para.clear() }
        }
        text.replace("{VERSION}", VERSION).lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> flush()
                line.startsWith("### ") -> { flush(); out += Block.SubHeading(line.removePrefix("### ").trim()) }
                line.startsWith("## ") -> { flush(); out += Block.Heading(line.removePrefix("## ").trim()) }
                line.startsWith("# ") -> { flush(); out += Block.Title(line.removePrefix("# ").trim()) }
                line.startsWith("- ") -> { flush(); out += Block.Bullet(line.removePrefix("- ").trim()) }
                else -> { if (para.isNotEmpty()) para.append(' '); para.append(line.trim()) }
            }
        }
        flush()
        return out
    }
}
