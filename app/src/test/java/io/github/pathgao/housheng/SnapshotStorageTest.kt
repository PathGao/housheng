package io.github.pathgao.housheng

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class SnapshotStorageTest {
    private val directory = Files.createTempDirectory(File("build/test-snapshots").apply { mkdirs() }.toPath(), "case-").toFile()
    private val target = File(directory, "capture.png")
    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun successReplacesLastSnapshot() {
        target.writeText("previous")
        assertTrue(saveSnapshot(target) { it.write("new".toByteArray()); true })
        assertEquals("new", target.readText())
        assertEquals(listOf("capture.png"), directory.list()!!.toList())
    }
    @Test fun encoderFailureKeepsPreviousSnapshot() {
        target.writeText("previous")
        assertFalse(saveSnapshot(target) { it.write("partial".toByteArray()); false })
        assertEquals("previous", target.readText())
        assertEquals(listOf("capture.png"), directory.list()!!.toList())
    }
    @Test fun ioFailureKeepsPreviousSnapshot() {
        target.writeText("previous")
        assertFalse(saveSnapshot(target) { it.write("partial".toByteArray()); throw IOException("test disk full") })
        assertEquals("previous", target.readText())
        assertEquals(listOf("capture.png"), directory.list()!!.toList())
    }
    @Test fun unwritableDestinationDoesNotCrashOrLeavePartialFile() {
        target.mkdir()
        assertFalse(saveSnapshot(target) { it.write(42); true })
        assertTrue(target.isDirectory)
        assertEquals(listOf("capture.png"), directory.list()!!.toList())
    }
}
