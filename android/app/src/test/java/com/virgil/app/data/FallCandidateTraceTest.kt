package com.virgil.app.data

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FallCandidateTraceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `appends lines in order`() {
        val file = tmp.newFile()
        FallCandidateTrace.appendToFile(file, "first")
        FallCandidateTrace.appendToFile(file, "second")
        assertEquals("first\nsecond", FallCandidateTrace.tail(file, 10))
    }

    @Test
    fun `tail returns only the last lines`() {
        val file = tmp.newFile()
        repeat(10) { FallCandidateTrace.appendToFile(file, "line$it") }
        assertEquals("line8\nline9", FallCandidateTrace.tail(file, 2))
    }

    @Test
    fun `tail of a missing file is null`() {
        assertNull(FallCandidateTrace.tail(tmp.root.resolve("absent.log"), 10))
    }

    @Test
    fun `tail of an empty file is null`() {
        assertNull(FallCandidateTrace.tail(tmp.newFile(), 10))
    }

    @Test
    fun `file is trimmed once it outgrows the cap and keeps the newest lines`() {
        val file = tmp.newFile()
        val line = "x".repeat(100)
        repeat(400) { FallCandidateTrace.appendToFile(file, "$it $line") }
        assertTrue(file.length() < 16 * 1024L)
        val tail = FallCandidateTrace.tail(file, 1)
        assertEquals("399 $line", tail)
        // Trimming cuts at a line boundary — every surviving line is intact.
        file.readText().trim().lines().forEach { assertTrue(it.endsWith(line)) }
    }
}
