package com.pypath.app.data.programs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Premium Phase 1 (Advanced IDE): one saved Python program. Programs are stored once, in a single
 * small JSON file in the app's private storage (one copy; signed-in learners also get a private
 * cloud backup through [com.pypath.app.cloud.CloudSync]).
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
    /**
     * Cloud sync: ids of deleted programs and when they were deleted, so a program deleted on one
     * phone is not brought back from another phone or from the cloud copy.
     */
    val deleted: Map<String, Long> = emptyMap(),
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

    fun delete(lib: ProgramLibrary, id: String, now: Long = System.currentTimeMillis()): ProgramLibrary {
        if (lib.find(id) == null) return lib
        val rest = lib.programs.filter { it.id != id }
        val last = if (lib.lastOpenedId == id) rest.maxByOrNull { it.updatedAt }?.id else lib.lastOpenedId
        return lib.copy(programs = rest, lastOpenedId = last, deleted = lib.deleted + (id to now))
    }

    fun open(lib: ProgramLibrary, id: String): ProgramLibrary = if (lib.find(id) != null) lib.copy(lastOpenedId = id) else lib
}

interface ProgramRepository {
    suspend fun load(): ProgramLibrary
    suspend fun save(library: ProgramLibrary)

    /**
     * Cloud sync: read-modify-write in one step (so a save from the editor is never lost). The
     * result is announced on [syncedChanges] so an open editor can pick it up.
     */
    suspend fun applySynced(replaced: Boolean = false, transform: (ProgramLibrary) -> ProgramLibrary): ProgramLibrary {
        val next = transform(load())
        save(next)
        return next
    }

    /** Libraries written by cloud sync (not by the editor itself). */
    val syncedChanges: Flow<SyncedPrograms> get() = emptyFlow()
}

/**
 * A library written by cloud sync. [replaced] is true when the phone switched to a different
 * account: the editor must then drop what it holds instead of merging it in.
 */
data class SyncedPrograms(val library: ProgramLibrary, val replaced: Boolean)

/**
 * Stores the whole library in `files/programs/programs.json`. Writes go to a temporary file that
 * then replaces the old one, so a crash mid-write never loses saved programs. A file that cannot
 * be read is kept as `programs.json.bak` instead of being overwritten.
 */
class FileProgramRepository(private val dir: File) : ProgramRepository {
    private val file get() = File(dir, "programs.json")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val synced = MutableSharedFlow<SyncedPrograms>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val syncedChanges: Flow<SyncedPrograms> get() = synced

    override suspend fun load(): ProgramLibrary = withContext(Dispatchers.IO) { mutex.withLock { read() } }

    private fun read(): ProgramLibrary {
        val f = file
        if (!f.exists()) return ProgramLibrary()
        return runCatching { json.decodeFromString(ProgramLibrary.serializer(), f.readText()) }.getOrElse {
            runCatching { f.copyTo(File(dir, "programs.json.bak"), overwrite = true) }
            ProgramLibrary()
        }
    }

    override suspend fun applySynced(replaced: Boolean, transform: (ProgramLibrary) -> ProgramLibrary): ProgramLibrary {
        val next = withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = read()
                val next = transform(current)
                if (next != current) write(next)
                next
            }
        }
        synced.tryEmit(SyncedPrograms(next, replaced))
        return next
    }

    override suspend fun save(library: ProgramLibrary) = withContext(Dispatchers.IO) { mutex.withLock { write(library) } }

    private fun write(library: ProgramLibrary) {
        run {
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
