package io.github.pathgao.housheng

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun saveSnapshot(target: File, encode: (OutputStream) -> Boolean): Boolean {
    val pending = File(target.parentFile, "${target.name}.pending")
    return try {
        if (!pending.outputStream().use { encode(it) }) return false
        Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        true
    } catch (_: Exception) {
        false
    } finally {
        pending.delete()
    }
}
