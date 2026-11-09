package com.pypath.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.pypath.app.data.programs.FileProgramRepository
import com.pypath.app.data.programs.MemoryProgramRepository
import com.pypath.app.data.programs.ProgramBook
import com.pypath.app.data.programs.ProgramLibrary
import com.pypath.app.domain.PythonSnippets
import com.pypath.app.premium.Entitlements
import com.pypath.app.premium.FakePremiumRepository
import com.pypath.app.premium.PremiumFeature
import com.pypath.app.premium.PremiumSource
import com.pypath.app.ui.screens.certificate.PremiumImage
import com.pypath.app.ui.screens.practical.insertSnippet
import com.pypath.app.ui.screens.practical.programFileName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Premium Phase 1: gating, saved programs, snippets and Image Center definitions. */
class PremiumPhase1Test {

    // ───────────── Gating ─────────────

    @Test fun freeUnlocksNothing() {
        assertFalse(Entitlements.FREE.isPremium)
        PremiumFeature.entries.forEach { assertFalse(Entitlements.FREE.has(it)) }
    }

    @Test fun premiumUnlocksBothFeatures() {
        val p = Entitlements.all(PremiumSource.LOCAL_DEV)
        assertTrue(p.isPremium)
        assertTrue(p.has(PremiumFeature.ADVANCED_IDE))
        assertTrue(p.has(PremiumFeature.IMAGE_CENTER))
    }

    @Test fun singleFeatureGateIsIndependent() {
        val onlyIde = Entitlements(setOf(PremiumFeature.ADVANCED_IDE), PremiumSource.LOCAL_DEV)
        assertTrue(onlyIde.has(PremiumFeature.ADVANCED_IDE))
        assertFalse(onlyIde.has(PremiumFeature.IMAGE_CENTER))
    }

    @Test fun devToggleSwitchesBetweenFreeAndPremium() = runBlocking {
        val repo = FakePremiumRepository()
        assertEquals(Entitlements.FREE, repo.entitlements.first())
        repo.setDevPremium(true)
        assertTrue(repo.entitlements.first().has(PremiumFeature.IMAGE_CENTER))
        repo.setDevPremium(false)
        assertFalse(repo.entitlements.first().isPremium)
    }

    // ───────────── Saved programs ─────────────

    @Test fun createRenameEditDelete() {
        var lib = ProgramLibrary()
        val (l1, a) = ProgramBook.create(lib, "Calculator", "School", "print(1)", now = 10, id = "a")
        val (l2, b) = ProgramBook.create(l1, "Game", "", "print(2)", now = 20, id = "b")
        lib = l2
        assertEquals(2, lib.programs.size)
        assertEquals("b", lib.lastOpenedId)
        assertEquals(listOf("School"), lib.folders)

        lib = ProgramBook.rename(lib, a.id, "  My   calculator ", "Maths", now = 30)
        assertEquals("My calculator", lib.find("a")!!.name)
        assertEquals("Maths", lib.find("a")!!.folder)

        lib = ProgramBook.updateCode(lib, b.id, "print(3)", now = 40)
        assertEquals("print(3)", lib.find("b")!!.code)
        assertEquals(40, lib.find("b")!!.updatedAt)
        assertEquals("print(1)", lib.find("a")!!.code) // other programs are independent

        lib = ProgramBook.delete(lib, "b")
        assertNull(lib.find("b"))
        assertEquals("a", lib.lastOpenedId)
        lib = ProgramBook.delete(lib, "a")
        assertTrue(lib.programs.isEmpty())
        assertNull(lib.lastOpenedId)
    }

    @Test fun namesAreUniqueAndCleaned() {
        var lib = ProgramLibrary()
        lib = ProgramBook.create(lib, "Untitled", id = "1").first
        lib = ProgramBook.create(lib, "untitled", id = "2").first
        lib = ProgramBook.create(lib, "", id = "3").first
        assertEquals(listOf("Untitled", "untitled 2", "Untitled 3"), lib.programs.map { it.name })
        // Renaming to its own name keeps it.
        assertEquals("Untitled", ProgramBook.rename(lib, "1", "Untitled", "").find("1")!!.name)
        assertEquals(ProgramBook.MAX_NAME, ProgramBook.cleanName("x".repeat(100)).length)
    }

    @Test fun unchangedCodeDoesNotTouchUpdatedAt() {
        val lib = ProgramBook.create(ProgramLibrary(), "A", code = "x", now = 5, id = "a").first
        assertEquals(5, ProgramBook.updateCode(lib, "a", "x", now = 99).find("a")!!.updatedAt)
    }

    @Test fun programsSurviveRestart() = runBlocking {
        val dir = Files.createTempDirectory("programs").toFile()
        try {
            var lib = ProgramBook.create(ProgramLibrary(), "Hello", "Practice", "print('hi')\n", id = "h").first
            lib = ProgramBook.create(lib, "Loop", "", "for i in range(3):\n    print(i)\n", id = "l").first
            FileProgramRepository(dir).save(lib)
            // A new repository instance = the app was closed and opened again.
            val reloaded = FileProgramRepository(dir).load()
            assertEquals(lib, reloaded)
            assertEquals("for i in range(3):\n    print(i)\n", reloaded.find("l")!!.code)
            // Exactly one file: no duplicate copies.
            assertEquals(listOf("programs.json"), dir.listFiles()!!.map { it.name })
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptFileIsBackedUpNotLost() = runBlocking {
        val dir = Files.createTempDirectory("programs").toFile()
        try {
            File(dir, "programs.json").writeText("{not json")
            val lib = FileProgramRepository(dir).load()
            assertTrue(lib.programs.isEmpty())
            assertEquals("{not json", File(dir, "programs.json.bak").readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun memoryRepositoryRoundTrip() = runBlocking {
        val repo = MemoryProgramRepository()
        val lib = ProgramBook.create(ProgramLibrary(), "A", id = "a").first
        repo.save(lib)
        assertEquals(lib, repo.load())
    }

    @Test fun programFileNames() {
        assertEquals("my_first_program.py", programFileName("My first program"))
        assertEquals("program.py", programFileName("!!!"))
    }

    // ───────────── Snippets ─────────────

    @Test fun snippetLibraryIsWellFormed() {
        val ids = PythonSnippets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(PythonSnippets.all.size >= 20)
        assertTrue(PythonSnippets.categories.size >= 8)
        PythonSnippets.all.forEach { assertTrue(it.code.isNotBlank()); assertFalse(it.code.contains('\t')) }
    }

    @Test fun snippetInsertsAtCursorOnEmptyLine() {
        val v = TextFieldValue("a = 1\n\nb = 2", TextRange(6))
        val r = insertSnippet(v, "print(a)\nprint(b)")
        assertEquals("a = 1\nprint(a)\nprint(b)\nb = 2", r.text)
        assertEquals(6 + "print(a)\nprint(b)".length, r.selection.start)
    }

    @Test fun snippetKeepsIndentation() {
        val v = TextFieldValue("def f():\n    ", TextRange(13))
        val r = insertSnippet(v, "if x:\n    y()")
        assertEquals("def f():\n    if x:\n        y()", r.text)
    }

    @Test fun snippetAfterCodeGoesOnNextLine() {
        val v = TextFieldValue("    print(1)\nend", TextRange(8))
        val r = insertSnippet(v, "x = 2\ny = 3")
        assertEquals("    print(1)\n    x = 2\n    y = 3\nend", r.text)
    }

    @Test fun snippetReplacesSelectionAndHandlesEmptyEditor() {
        assertEquals("print(1)", insertSnippet(TextFieldValue("", TextRange(0)), "print(1)\n").text)
        val v = TextFieldValue("old\n", TextRange(0, 3))
        assertEquals("new\n", insertSnippet(v, "new").text)
    }

    // ───────────── Image Center ─────────────

    @Test fun imageCenterHasExactlyTwoImages() {
        assertEquals(2, PremiumImage.entries.size)
        PremiumImage.entries.forEach {
            assertTrue(it.width in 720..1440)
            assertTrue(it.height in 720..2560)
            assertTrue(it.fileName.startsWith("PyPath-"))
        }
        assertEquals(PremiumImage.entries.size, PremiumImage.entries.map { it.fileName }.toSet().size)
    }
}
