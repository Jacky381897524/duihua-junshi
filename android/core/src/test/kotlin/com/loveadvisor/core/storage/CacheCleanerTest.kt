package com.loveadvisor.core.storage

import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.concurrent.CancellationException

class CacheCleanerTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun clearsOnlyCacheAndKeepsSettingsHistoryAndTheCacheRoot() {
        val cache = folder.newFolder("cache")
        val files = folder.newFolder("private-data")
        val settings = files.resolve("settings.enc").apply { writeText("credentials") }
        val history = files.resolve("automatic-recent.enc").apply { writeText("conversation") }
        cache.resolve("nested").mkdir()
        cache.resolve("nested/preview.tmp").writeBytes(ByteArray(7))
        cache.resolve("request.tmp").writeBytes(ByteArray(5))
        val cleaner = CacheCleaner(listOf(cache))
        assertEquals(12L, cleaner.usage().bytes)

        val result = cleaner.clear()

        assertEquals(12L, result.freedBytes)
        assertEquals(0L, result.remainingBytes)
        assertEquals(0, result.failedEntries)
        assertTrue(cache.isDirectory)
        assertTrue(cache.listFiles()!!.isEmpty())
        assertEquals("credentials", settings.readText())
        assertEquals("conversation", history.readText())
    }

    @Test fun measuresBothVolumesAndDoesNotDoubleCountTheSameRoot() {
        val internal = folder.newFolder("cache")
        val external = folder.newFolder("external-cache")
        internal.resolve("a").writeBytes(ByteArray(3))
        external.resolve("b").writeBytes(ByteArray(8))
        val cleaner = CacheCleaner(listOf(internal, external, internal.resolve(".")))
        assertEquals(11L, cleaner.usage().bytes)
        assertEquals(11L, cleaner.clear().freedBytes)
    }

    @Test fun missingOrEmptyCachesNeedNoRepairAndClearingTwiceIsSafe() {
        val cache = folder.newFolder("empty-cache")
        val cleaner = CacheCleaner(listOf(cache, folder.root.resolve("missing")))
        assertEquals(CacheUsage(0L, 0), cleaner.usage())
        assertEquals(CacheClearResult(0L, 0L, 0), cleaner.clear())
        assertEquals(CacheClearResult(0L, 0L, 0), cleaner.clear())
        assertFalse(folder.root.resolve("missing").exists())
    }

    @Test fun symbolicLinksNeverExposeOrDeletePersistentDataOutsideCache() {
        val cache = folder.newFolder("cache")
        val privateData = folder.newFolder("private-data")
        val key = privateData.resolve("settings.enc").apply { writeText("keep-this-key") }
        val link = cache.resolve("linked-private-data").toPath()
        try { Files.createSymbolicLink(link, privateData.toPath()) }
        catch (e: Exception) { assumeNoException("Platform cannot create symbolic links", e) }
        cache.resolve("temp").writeBytes(ByteArray(4))
        val cleaner = CacheCleaner(listOf(cache))
        assertEquals(4L, cleaner.usage().bytes)
        assertEquals(4L, cleaner.clear().freedBytes)
        assertEquals("keep-this-key", key.readText())
        assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS))
    }

    @Test fun aLinkedCacheRootIsRejectedInsteadOfCleaningItsTarget() {
        val data = folder.newFolder("private-data")
        val key = data.resolve("settings.enc").apply { writeText("keep") }
        val link = folder.root.resolve("cache-link").toPath()
        try { Files.createSymbolicLink(link, data.toPath()) }
        catch (e: Exception) { assumeNoException("Platform cannot create symbolic links", e) }
        val result = CacheCleaner(listOf(link.toFile())).clear()
        assertEquals(0L, result.freedBytes)
        assertTrue(result.failedEntries > 0)
        assertEquals("keep", key.readText())
    }

    @Test fun aNonDirectoryRootReportsFailureWithoutDeletingIt() {
        val invalid = folder.newFile("not-a-cache").apply { writeText("keep") }
        val result = CacheCleaner(listOf(invalid)).clear()
        assertEquals(0L, result.freedBytes)
        assertEquals(1, result.failedEntries)
        assertEquals("keep", invalid.readText())
    }

    @Test fun cancellationBeforeCleaningDoesNotDeleteAnyFile() {
        val cache = folder.newFolder("cache")
        val pending = cache.resolve("pending").apply { writeText("pending") }
        try {
            CacheCleaner(listOf(cache)).clear { throw CancellationException() }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals("pending", pending.readText())
    }
}
