package com.loveadvisor.core.startup

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class StartupTraceTest {
    private class Queue:Executor {
        val tasks=ArrayDeque<Runnable>()
        override fun execute(command:Runnable) { tasks.addLast(command) }
        fun drain() { while(tasks.isNotEmpty())tasks.removeFirst().run() }
    }
    private fun inDirectory(test:(File)->Unit) {
        val directory=Files.createTempDirectory("startup-trace-test").toFile()
        try { test(File(directory,"startup.json")) } finally { directory.deleteRecursively() }
    }

    // Resolving filesDir and performing a first write on the caller would regress launch responsiveness.
    @Test fun traceIsImmediatelyReadableWhileAllStorageWaitsForTheWorker()=inDirectory { file ->
        val worker=Queue();var paths=0;var now=100L
        val trace=StartupTrace({ paths++;file },worker,"V1.4.9",7,1234L) { now }
        now=140L;trace.mark(StartupStage.MAIN_CREATE_ENTERED)
        assertEquals(0,paths);assertFalse(file.exists())
        assertEquals(listOf(StartupStage.PROCESS_ATTACHED,StartupStage.MAIN_CREATE_ENTERED),trace.snapshot.current.events.map { it.stage })
        assertEquals(40L,trace.snapshot.current.events.last().elapsedMs)
        worker.drain();assertTrue(file.isFile);assertTrue(trace.snapshot.historyLoaded)
    }

    // Dropping or overwriting the old current attempt would conceal the failure after the next launch.
    @Test fun aNewProcessRetainsExactlyThePriorProcessWithItsLastBoundary()=inDirectory { file ->
        val firstWorker=Queue()
        val first=StartupTrace({ file },firstWorker,"V1.4.8",11,1000L) { 100L }
        first.mark(StartupStage.SETTINGS_READ_BEGIN);firstWorker.drain()
        val secondWorker=Queue()
        val second=StartupTrace({ file },secondWorker,"V1.4.9",22,2000L) { 200L }
        second.mark(StartupStage.FIRST_DRAW);secondWorker.drain()
        assertEquals(11,second.snapshot.previous!!.pid)
        assertEquals(StartupStage.SETTINGS_READ_BEGIN,second.snapshot.previous!!.events.last().stage)
        assertEquals(22,second.snapshot.current.pid)
        val thirdWorker=Queue()
        val third=StartupTrace({ file },thirdWorker,"V1.4.9",33,3000L) { 300L }
        thirdWorker.drain()
        assertEquals(22,third.snapshot.previous!!.pid)
        assertFalse(file.readText().contains("V1.4.8"))
    }

    // Repeated resumes must not create unbounded files or replace first-start timing evidence.
    @Test fun repeatedStagesRemainBoundedAndKeepTheFirstElapsedTime()=inDirectory { file ->
        val worker=Queue();var now=100L
        val trace=StartupTrace({ file },worker,"V1.4.9",7,1234L) { now }
        now=105L;trace.mark(StartupStage.MAIN_RESUMED)
        repeat(1000) { now++;trace.mark(StartupStage.MAIN_RESUMED) }
        worker.drain()
        assertEquals(2,trace.snapshot.current.events.size)
        assertEquals(5L,trace.snapshot.current.events.last().elapsedMs)
        assertTrue(file.length()<8192L)
    }

    // Unknown JSON fields cannot become diagnostic output or survive a rewrite of the fixed metadata schema.
    @Test fun unknownPersistedContentAndInvalidVersionsNeverEnterTheTrace()=inDirectory { file ->
        file.writeText("""{"current":{"version":"V1.4.8","pid":11,"startedAtMillis":1000,"events":[{"stage":"PROCESS_ATTACHED","elapsedMs":0}],"apiKey":"private-key","chat":"private-chat"},"intentExtras":"private-intent"}""")
        val worker=Queue()
        val trace=StartupTrace({ file },worker,"https://private-key.example",7,1234L) { 100L }
        worker.drain()
        assertEquals("unknown",trace.snapshot.current.version)
        val text=file.readText()
        listOf("private-key","private-chat","private-intent","apiKey","intentExtras").forEach { assertFalse(text.contains(it)) }
        assertEquals("V1.4.8",trace.snapshot.previous!!.version)
    }

    // Corrupt, oversized, or future stage data must not break the new process or be echoed as diagnostics.
    @Test fun invalidHistoryIsDiscardedWithoutBlockingCurrentMarkers()=inDirectory { file ->
        listOf("not-json","x".repeat(9000),"""{"current":{"version":"V1.4.8","pid":11,"startedAtMillis":1000,"events":[{"stage":"private-chat","elapsedMs":0}]}}""").forEach { contents ->
            file.writeText(contents)
            val worker=Queue();val trace=StartupTrace({ file },worker,"V1.4.9",7,1234L) { 100L }
            trace.mark(StartupStage.MAIN_CREATE_ENTERED);worker.drain()
            assertNull(trace.snapshot.previous);assertTrue(trace.snapshot.historyLoaded)
            assertEquals(StartupStage.MAIN_CREATE_ENTERED,trace.snapshot.current.events.last().stage)
            assertFalse(file.readText().contains("private-chat"))
        }
    }

    // A storage permission/directory failure must remain diagnostic-only and never stop app startup.
    @Test fun storageFailureLeavesTheInMemoryBoundariesUsable()=inDirectory { file ->
        file.writeText("occupied")
        val worker=Queue()
        val trace=StartupTrace({ File(file,"cannot-write.json") },worker,"V1.4.9",7,1234L) { 100L }
        trace.mark(StartupStage.STARTUP_READY);worker.drain()
        assertTrue(trace.snapshot.historyLoaded);assertEquals(false,trace.snapshot.persistenceAvailable)
        assertEquals(StartupStage.STARTUP_READY,trace.snapshot.current.events.last().stage)
        assertEquals("occupied",file.readText())
    }
}
