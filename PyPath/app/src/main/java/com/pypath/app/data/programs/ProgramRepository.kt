package com.pypath.app.data.programs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Premium Phase 1 (Advanced IDE): one saved Python program. Programs are stored once, in a single
 * small JSON file in the app's private storage (no database, no cloud, no duplicate copies).
 */
@Serializable
data class SavedProgram(
    val id: String,
    val name: String,
    /** Folder / category; empty means "No folder". */
    val folder: String = "",
    val code: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val lineCount: Int get() = code.count { it == '\n' } + 1
}

@Serializable
data class ProgramLibrary(
    val version: Int = 1,
    val programs: List<SavedProgram> = emptyList(),
    /** Program shown when the IDE opens again. */
    val lastOpenedId: String? = null,
) {
    fun find(id: String?): SavedProgram? = programs.firstOrNull { it.id == id }

    /** Folder names in use, sorted (without the empty "No folder"). */
    val folders: List<String> get() = programs.map { it.folder }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }

    /** Newest first. */
    val sorted: List<SavedProgram> get() = programs.sortedByDescending { it.updatedAt }
}

/** Pure, testable operations on a [ProgramLibrary]. Each returns a new library. */
object ProgramBook {
    const val MAX_NAME = 40
    const val MAX_FOLDER = 24
    /** Generous safety cap (about 200 KB of code per program). */
    const val MAX_CODE = 200_000

    const val STARTER_CODE = "# My program\nname = input(\"What's your name? \")\nprint(f\"Hello, {name}! Welcome to PyPath.\")\n"
    const val NEW_PROGRAM_CODE = "# Write your Python program here\n\n"

    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).trim()
    fun cleanFolder(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_FOLDER).trim()

    /** "Untitled", "Untitled 2", … — names are unique (case-insensitive) so programs are easy to tell apart. */
    fun uniqueName(lib: ProgramLibrary, wanted: String, exceptId: String? = null): String {
        val base = cleanName(wanted).ifEmpty { "Untitled" }
        val taken = lib.programs.filter { it.id != exceptId }.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        var n = 2
        while (true) {
            val suffix = " $n"
            val candidate = base.take(MAX_NAME - suffix.length).trimEnd() + suffix
            if (candidate.lowercase() !in taken) return candidate
            n++
        }
    }

    fun create(
        lib: ProgramLibrary, name: String, folder: String = "", code: String = NEW_PROGRAM_CODE,
        now: Long = System.currentTimeMillis(), id: String = UUID.randomUUID().toString(),
    ): Pair<ProgramLibrary, SavedProgram> {
        val p = SavedProgram(id, uniqueName(lib, name), cleanFolder(folder), code.take(MAX_CODE), now, now)
        return lib.copy(programs = lib.programs + p, lastOpenedId = p.id) to p
    }

    fun rename(lib: ProgramLibrary, id: String, name: String, folder: String, now: Long = System.currentTimeMillis()): ProgramLibrary =
        lib.copy(programs = lib.programs.map {
            if (it.id == id) it.copy(name = uniqueName(lib, name, exceptId = id), folder = cleanFolder(folder), updatedAt = now) else it
        })

    fun updateCode(lib: ProgramLibrary, id: String, code: String, now: Long = System.currentTimeMillis()): ProgramLibrary =
        lib.copy(programs = lib.programs.map {
            if (it.id == id && it.code != code) it.copy(code = code.take(MAX_CODE), updatedAt = now) else it
        })

    fun delete(lib: ProgramLibrary, id: String): ProgramLibrary {
        val rest = lib.programs.filter { it.id != id }
        val last = if (lib.lastOpenedId == id) rest.maxByOrNull { it.updatedAt }?.id else lib.lastOpenedId
        return lib.copy(programs = rest, lastOpenedId = last)
    }

    fun open(lib: ProgramLibrary, id: String): ProgramLibrary = if (lib.find(id) != null) lib.copy(lastOpenedId = id) else lib
}

interface ProgramRepository {
    suspend fun load(): ProgramLibrary
    suspend fun save(library: ProgramLibrary)
}

/**
 * Stores the whole library in `files/programs/programs.json`. Writes go to a temporary file that
 * then replaces the old one, so a crash mid-write never loses saved programs. A file that cannot
 * be read is kept as `programs.json.bak` instead of being overwritten.
 */
class FileProgramRepository(private val dir: File) : ProgramRepository {
    private val file get() = File(dir, "programs.json")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun load(): ProgramLibrary = withContext(Dispatchers.IO) {
        mutex.withLock {
            val f = file
            if (!f.exists()) return@withLock ProgramLibrary()
            runCatching { json.decodeFromString(ProgramLibrary.serializer(), f.readText()) }.getOrElse {
                runCatching { f.copyTo(File(dir, "programs.json.bak"), overwrite = true) }
                ProgramLibrary()
            }
        }
    }

    override suspend fun save(library: ProgramLibrary) = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.mkdirs()
            val tmp = File(dir, "programs.json.tmp")
            tmp.writeText(json.encodeToString(ProgramLibrary.serializer(), library))
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
            }
        }
    }
}

/** In-memory version for previews and tests. */
class MemoryProgramRepository(var stored: ProgramLibrary = ProgramLibrary()) : ProgramRepository {
    override suspend fun load(): ProgramLibrary = stored
    override suspend fun save(library: ProgramLibrary) { stored = library }
}
