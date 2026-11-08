package com.pypath.app.data.content

import android.content.Context
import com.pypath.app.data.model.Course
import com.pypath.app.data.model.Glossary
import com.pypath.app.data.model.Level
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Source of course content. Today it reads bundled JSON files; later it could be remote. */
interface CourseRepository {
    suspend fun loadCourse(): Course
}

/**
 * Reads assets/course/course.json. Since schema 2 that file is an index listing one file per
 * level (assets/course/levels/l1.json, ...); older single-file courses with inline levels still load.
 */
class AssetCourseRepository(
    private val context: Context,
    private val assetPath: String = "course/course.json",
) : CourseRepository {

    @Volatile private var cached: Course? = null

    override suspend fun loadCourse(): Course = cached ?: withContext(Dispatchers.IO) {
        val dir = assetPath.substringBeforeLast('/', "")
        CourseParser.parseWithLevels(read(assetPath)) { file -> read(if (dir.isEmpty()) file else "$dir/$file") }
            .also { cached = it }
    }

    private fun read(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }
}

/** Shared JSON configuration for course content (also used by tests and future remote loaders). */
object CourseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun parse(text: String): Course = json.decodeFromString(Course.serializer(), text)

    fun parseLevel(text: String): Level = json.decodeFromString(Level.serializer(), text)

    fun parseGlossary(text: String): Glossary = json.decodeFromString(Glossary.serializer(), text)

    /** Parses a course index, appends every level listed in levelFiles (in order) and loads the glossary. */
    fun parseWithLevels(indexText: String, readFile: (String) -> String): Course {
        val index = parse(indexText)
        val withLevels = if (index.levelFiles.isEmpty()) index
        else index.copy(levels = index.levels + index.levelFiles.map { parseLevel(readFile(it)) })
        val glossaryFile = withLevels.glossaryFile ?: return withLevels
        // A missing or broken glossary must never stop the course from loading.
        val terms = runCatching { parseGlossary(readFile(glossaryFile)).terms }.getOrDefault(emptyList())
        return withLevels.copy(glossary = terms)
    }
}
