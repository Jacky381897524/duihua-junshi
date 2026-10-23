package com.loveadvisor.core.storage
import java.nio.file.Files
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test
class CipherDocumentStoreTest {
    @Test fun personalMemorySurvivesCacheClearEncryptedAndCanUseItsOwnCapacity() {
        val root=Files.createTempDirectory("personal-data").toFile();val secret=key()
        try {
            val files=root.resolve("files/private-data");val cache=root.resolve("cache").apply { mkdirs() };cache.resolve("temporary").writeText("discard")
            val store=CipherDocumentStore(files,limits=mapOf("personal-memory-v1" to 5*1024*1024)) { secret }
            val value="本人原话与对方偏好"+"x".repeat(3*1024*1024)
            store.write("personal-memory-v1",value)
            CacheCleaner(listOf(cache)).clear()
            assertEquals(value,CipherDocumentStore(files,limits=mapOf("personal-memory-v1" to 5*1024*1024)) { secret }.read("personal-memory-v1"))
            assertFalse(files.resolve("personal-memory-v1.enc").readBytes().toString(Charsets.UTF_8).contains("对方偏好"))
        } finally { root.deleteRecursively() }
    }
    private fun key()=KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @Test fun diskHasNoKeyOrSensitiveMemoryAndCiphertextsDiffer() {
        val dir=Files.createTempDirectory("cipher-test").toFile(); val k=key(); val store=CipherDocumentStore(dir) { k }
        try {
            store.write("config","FAKE_SECRET 私密记忆"); assertEquals("FAKE_SECRET 私密记忆",store.read("config")); val first=dir.resolve("config.enc").readBytes()
            assertFalse(first.toString(Charsets.UTF_8).contains("FAKE_SECRET")); assertFalse(first.toString(Charsets.UTF_8).contains("私密记忆"))
            store.write("config","FAKE_SECRET 私密记忆"); assertFalse(first.contentEquals(dir.resolve("config.enc").readBytes()))
        } finally { dir.deleteRecursively() }
    }
    @Test fun interruptedPendingWriteDoesNotDestroyPriorDocument() {
        val dir=Files.createTempDirectory("cipher-test").toFile(); val k=key(); val store=CipherDocumentStore(dir) { k }
        try { store.write("memory","原有资料"); dir.resolve("memory.pending").writeText("broken"); assertEquals("原有资料",CipherDocumentStore(dir) { k }.read("memory")) } finally { dir.deleteRecursively() }
    }
    @Test fun tamperingAndWrongKeyAreExplicitFailures() {
        val dir=Files.createTempDirectory("cipher-test").toFile(); val k=key(); val store=CipherDocumentStore(dir) { k }
        try { store.write("memory","资料"); try { CipherDocumentStore(dir) { key() }.read("memory"); fail() } catch(_:SecurityException) {} } finally { dir.deleteRecursively() }
    }
}
