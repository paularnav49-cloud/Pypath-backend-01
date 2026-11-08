package com.pypath.app.domain

/** Everything printed on the course certificate. Built on the phone; nothing is uploaded. */
data class CertificateInfo(
    val name: String,
    val courseTitle: String,
    /** When the last sub-level was finished (epoch millis). */
    val completedAt: Long,
    val levels: Int,
    val lessons: Int,
    val projects: Int,
    val mcqAverage: Int?,
    val badgeLevels: List<Int>,
    val id: String,
)

object Certificates {
    const val MAX_NAME = 40

    /** Trims, collapses spaces and limits the name to [MAX_NAME] characters. */
    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).trim()

    /** null until the whole course is complete. */
    fun info(snapshot: CourseSnapshot, rawName: String, now: Long = System.currentTimeMillis()): CertificateInfo? {
        if (!snapshot.certificateUnlocked) return null
        val subs = snapshot.coreSubLevels // Batch 1: advanced extension levels are not part of the certificate
        val completedAt = subs.mapNotNull { it.progress.completedAt }.maxOrNull() ?: now
        val scores = subs.mapNotNull { it.progress.quiz?.takeIf { q -> q.total > 0 } }
        val avg = if (scores.isEmpty()) null else scores.sumOf { it.bestPercent } / scores.size
        val name = cleanName(rawName)
        return CertificateInfo(
            name = name,
            courseTitle = snapshot.course.title,
            completedAt = completedAt,
            levels = snapshot.levels.count { it.level.available && !it.level.advanced },
            lessons = subs.count { !it.subLevel.isProject },
            projects = snapshot.allProjects.count { it.status == NodeStatus.COMPLETED },
            mcqAverage = avg,
            badgeLevels = snapshot.levels.filter { !it.level.advanced && (it.badgeEarned || it.status == NodeStatus.COMPLETED) }.map { it.level.number },
            id = certificateId(name, completedAt),
        )
    }

    /** Stable, readable id such as "PP-3F9A-C21B" (FNV-1a hash of the name and completion time). */
    fun certificateId(name: String, completedAt: Long): String {
        var h = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
        for (b in "${name.lowercase()}|$completedAt".toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xff)
            h *= 0x100000001b3L
        }
        val hex = java.lang.Long.toHexString(h).uppercase().padStart(16, '0')
        return "PP-${hex.substring(0, 4)}-${hex.substring(4, 8)}"
    }
}
