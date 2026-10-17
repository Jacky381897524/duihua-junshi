package com.loveadvisor.core.storage

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CancellationException

data class CacheUsage(val bytes: Long, val failedEntries: Int)
data class CacheClearResult(val freedBytes: Long, val remainingBytes: Long, val failedEntries: Int)

/** Receives only OS-provided cache roots. Never follows links, deletes roots or opens file contents. */
class CacheCleaner(directories: List<File>) {
    private val roots = directories.map { it.toPath().toAbsolutePath().normalize() }.distinct()

    @Synchronized fun usage(checkCancelled: () -> Unit = {}): CacheUsage {
        val errors = mutableSetOf<Path>()
        return CacheUsage(walk(false, errors, checkCancelled), errors.size)
    }

    @Synchronized fun clear(checkCancelled: () -> Unit = {}): CacheClearResult {
        val errors = mutableSetOf<Path>()
        val freed = walk(true, errors, checkCancelled)
        val remaining = walk(false, errors, checkCancelled)
        return CacheClearResult(freed, remaining, errors.size)
    }

    private fun walk(remove: Boolean, errors: MutableSet<Path>, checkCancelled: () -> Unit): Long {
        var bytes = 0L
        fun checkpoint() {
            checkCancelled()
            if (Thread.currentThread().isInterrupted) throw CancellationException()
        }
        fun failed(path: Path, error: Exception) {
            // A vanished temporary file is already gone; inaccessible files are reported.
            if (error !is NoSuchFileException) errors.add(path)
        }
        roots.forEach { root ->
            checkpoint()
            try {
                val rootInfo = Files.readAttributes(root, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                if (root.parent == null || !rootInfo.isDirectory || rootInfo.isSymbolicLink) {
                    errors.add(root)
                    return@forEach
                }
                // walkFileTree's default is NOFOLLOW_LINKS, including directory links.
                Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        checkpoint()
                        return FileVisitResult.CONTINUE
                    }
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        checkpoint()
                        val size = if (attrs.isRegularFile) attrs.size() else 0L
                        if (!remove) bytes += size
                        else try {
                            // Deleting a symlink unlinks the entry, never its target.
                            if (Files.deleteIfExists(file)) bytes += size
                        } catch (e: IOException) { failed(file, e) }
                        catch (e: SecurityException) { failed(file, e) }
                        return FileVisitResult.CONTINUE
                    }
                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                        checkpoint()
                        failed(file, exc)
                        return FileVisitResult.CONTINUE
                    }
                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        checkpoint()
                        if (exc != null) failed(dir, exc)
                        if (remove && dir != root && exc == null) try {
                            Files.deleteIfExists(dir)
                        } catch (e: IOException) { failed(dir, e) }
                        catch (e: SecurityException) { failed(dir, e) }
                        return FileVisitResult.CONTINUE
                    }
                })
            } catch (e: IOException) { failed(root, e) }
            catch (e: SecurityException) { failed(root, e) }
        }
        return bytes
    }
}
