package com.loveadvisor.android.storage

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AppCacheDeviceTest {
    @Test fun androidAdapterCleansCachesButPreservesPrivateDataAndCompiledCode() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val sandbox=File(context.cacheDir,"cache-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val internal=File(sandbox,"cache").apply { mkdirs() }
        val external=File(sandbox,"external-cache").apply { mkdirs() }
        val persistent=File(sandbox,"files").apply { mkdirs() }
        val compiled=File(sandbox,"code-cache").apply { mkdirs() }
        val bound=object:ContextWrapper(context) {
            override fun getCacheDir()=internal
            override fun getExternalCacheDirs():Array<File> = arrayOf(external)
            override fun getFilesDir()=persistent
            override fun getCodeCacheDir()=compiled
        }
        try {
            File(internal,"a.tmp").writeBytes(ByteArray(4))
            File(external,"b.tmp").writeBytes(ByteArray(6))
            val settings=File(persistent,"settings.enc").apply { writeText("keep settings") }
            val history=File(persistent,"automatic-recent.enc").apply { writeText("keep history") }
            val code=File(compiled,"compiled").apply { writeText("keep compiled code") }
            val cache=AppCache(bound)
            assertEquals(10L,cache.usage().bytes)
            val result=cache.clear()
            assertEquals(10L,result.freedBytes);assertEquals(0L,result.remainingBytes)
            assertEquals("keep settings",settings.readText());assertEquals("keep history",history.readText())
            assertEquals("keep compiled code",code.readText())
        } finally { sandbox.deleteRecursively() }
    }
}
