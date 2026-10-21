package com.loveadvisor.core.diagnostics

import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.CountDownLatch
import org.junit.Assert.*
import org.junit.Test

class DiagnosticJournalTest {
    /** Instruments real retained-row traversals without adding test counters to production. */
    private class VisitDeque<E>(values:Collection<E>):ArrayDeque<E>(values) {
        var visits=0
        override fun iterator():MutableIterator<E> {
            val delegate=super.iterator()
            return object:MutableIterator<E> {
                override fun hasNext()=delegate.hasNext()
                override fun next():E { visits++;return delegate.next() }
                override fun remove()=delegate.remove()
            }
        }
    }
    private fun instrumentRows(journal:DiagnosticJournal):VisitDeque<Any> {
        val field=DiagnosticJournal::class.java.getDeclaredField("rows").apply { isAccessible=true }
        @Suppress("UNCHECKED_CAST")
        val tracked=VisitDeque(field.get(journal) as Collection<Any>)
        field.set(journal,tracked)
        return tracked
    }
    private class Worker:Executor {
        val tasks=ConcurrentLinkedQueue<Runnable>()
        override fun execute(command:Runnable) { tasks.add(command) }
        fun runOne() { tasks.poll()?.run() }
        fun drain() { var count=0;while(tasks.isNotEmpty()) { check(count++<1000);runOne() } }
    }
    private class Fixture {
        val dir=Files.createTempDirectory("diagnostic-journal-").toFile()
        val file=File(dir,"journal.json")
        var time=1_000_000L
        val worker=Worker()
        fun journal(events:Int=10000,bytes:Int=4194304,retention:Long=2592000000,version:String="V1.4.11",location:()->File={file},delay:((()->Unit)->Unit)?=null)=DiagnosticJournal(location,worker,version,41,32,{time},delay,events,bytes,retention)
        fun close() { dir.deleteRecursively() }
    }
    private inline fun fixture(block:(Fixture)->Unit) { val f=Fixture();try { block(f) } finally { f.close() } }

    @Test fun appendingIsImmediateWhileHistoryAndDiskAccessRemainOnWorker()=fixture { f ->
        var accessed=false
        val journal=f.journal(location={accessed=true;f.file})
        journal.event(LogStep.PROCESS_START)
        assertEquals(1,journal.snapshot().events.size);assertEquals(LogHistoryState.LOADING,journal.snapshot().historyState)
        assertFalse(accessed);assertFalse(f.file.exists())
        f.worker.drain();assertTrue(accessed);assertTrue(f.file.isFile)
    }

    @Test fun persistsOperationPhasesAndFailureAcrossNewJournal()=fixture { f ->
        val first=f.journal();val read=first.begin(LogStep.READ_TAP)
        first.event(LogStep.READ_NATIVE,LogState.OK,operation=read,metrics=mapOf(LogMetric.NODE_COUNT to 17))
        f.time+=12;first.end(read,LogState.FAIL,LogReason.NO_CHAT)
        first.flush();f.worker.drain()
        val second=f.journal();f.worker.drain();val rows=second.snapshot().events
        assertEquals(3,rows.size);assertEquals(listOf(LogState.START,LogState.OK,LogState.FAIL),rows.map { it.state })
        assertEquals(1,rows.map { it.operationId }.distinct().size);assertNotNull(rows.first().operationId)
        assertEquals(12L,rows.last().metrics[LogMetric.DURATION_MS]);assertEquals(LogReason.NO_CHAT,rows.last().reason)
        assertEquals("V1.4.11",rows.first().version);assertEquals(41,rows.first().build);assertEquals(32,rows.first().pid)
        assertEquals(LogHistoryState.LOADED,second.snapshot().historyState)
    }

    @Test fun historyLoadMergesNewOperationsBeforeInitialReadCompletes()=fixture { f ->
        val old=f.journal();old.event(LogStep.APP_CREATE);f.worker.drain()
        f.time++
        val fresh=f.journal();val op=fresh.begin(LogStep.CONFIG_SAVE);fresh.end(op,LogState.OK);fresh.flush()
        f.worker.drain()
        assertEquals(listOf(LogStep.APP_CREATE,LogStep.CONFIG_SAVE,LogStep.CONFIG_SAVE),fresh.snapshot().events.map { it.step })
        val reloaded=f.journal();f.worker.drain();assertEquals(3,reloaded.snapshot().events.size)
    }

    @Test fun childOperationsKeepParentAndTerminalResultOnlyOnce()=fixture { f ->
        val journal=f.journal();val parent=journal.begin(LogStep.READ_TAP);val child=journal.begin(LogStep.VISION_REQUEST,parent)
        journal.end(child,LogState.CANCELLED,LogReason.USER_CANCELLED);journal.end(child,LogState.OK);journal.end(parent,LogState.OK)
        val rows=journal.snapshot().events
        assertEquals(4,rows.size);assertEquals(rows[0].operationId,rows[1].parentOperationId)
        assertEquals(LogState.CANCELLED,rows[2].state);assertNotEquals(rows[0].operationId,rows[1].operationId)
    }

    @Test fun nonTerminalEndDoesNotConsumeHandleAndForeignHandlesAreIgnored()=fixture { f ->
        val one=f.journal();val two=f.journal();val op=one.begin(LogStep.FILL_TAP)
        one.end(op,LogState.STEP);two.end(op,LogState.OK);one.end(op,LogState.FAIL,LogReason.FILL_TIMEOUT)
        assertEquals(2,one.snapshot().events.size);assertTrue(two.snapshot().events.isEmpty())
        assertEquals(LogState.FAIL,one.snapshot().events.last().state)
    }

    @Test fun incompletePriorOperationIsEvidenceWithoutInventingCrash()=fixture { f ->
        val first=f.journal();first.begin(LogStep.READ_TAP);f.worker.drain()
        val next=f.journal();f.worker.drain()
        assertEquals(1,next.snapshot().incompletePrevious);assertEquals(1,next.snapshot().events.size)
        assertTrue(next.text().contains("INCOMPLETE_PREVIOUS"));assertTrue(next.text().contains("不能据此确定崩溃"))
        assertFalse(next.snapshot().events.any { it.state==LogState.CRASH })
    }

    @Test fun concurrentEndsAndOperationsDoNotLoseOrDuplicateEvents()=fixture { f ->
        val journal=f.journal();val shared=journal.begin(LogStep.READ_TAP)
        val start=CountDownLatch(1)
        val threads=(1..12).map { Thread { start.await();repeat(20) { val op=journal.begin(LogStep.LOCAL_TASK);journal.end(op,LogState.OK) };journal.end(shared,LogState.CANCELLED) }.apply { start() } }
        start.countDown();threads.forEach { it.join(5000);assertFalse(it.isAlive) }
        f.worker.drain();val rows=journal.snapshot().events
        assertEquals(482,rows.size);assertEquals(rows.size,rows.map { it.id }.distinct().size)
        assertEquals(241,rows.count { it.state==LogState.START });assertEquals(1,rows.count { it.state==LogState.CANCELLED })
        val reload=f.journal();f.worker.drain();assertEquals(482,reload.snapshot().events.size)
    }

    @Test fun corruptHistoryFailsClosedAndStillPersistsNewEvents()=fixture { f ->
        f.file.writeText("{private-secret broken")
        val journal=f.journal();journal.event(LogStep.PROCESS_START);f.worker.drain()
        assertEquals(LogHistoryState.CORRUPT,journal.snapshot().historyState);assertEquals(1L,journal.snapshot().historyFailures)
        assertEquals(1,journal.snapshot().events.size);assertFalse(journal.text().contains("private-secret"))
        assertFalse(f.file.readText().contains("private-secret"))
    }

    @Test fun oversizedHistoryIsNotReadIntoMemoryOrExported()=fixture { f ->
        f.file.writeText("private-secret".repeat(1000));val journal=f.journal(bytes=2048)
        journal.event(LogStep.STARTUP);f.worker.drain()
        assertEquals(LogHistoryState.OVERSIZED,journal.snapshot().historyState);assertEquals(1L,journal.snapshot().historyFailures)
        assertEquals(1,journal.snapshot().events.size);assertFalse(journal.text().contains("private-secret"))
        assertTrue(f.file.length()<=2048)
    }

    @Test fun malformedRowsAreRejectedWhileValidEvidenceSurvives()=fixture { f ->
        val first=f.journal();first.event(LogStep.STARTUP);first.event(LogStep.PAGE_OPEN);f.worker.drain()
        assertTrue("journal file must be persisted",f.file.isFile)
        val root=JsonParser.parseString(f.file.readText()).asJsonObject
        root.getAsJsonArray("events")[0].asJsonObject.addProperty("step","private-secret")
        f.file.writeText(root.toString());val next=f.journal();f.worker.drain()
        assertEquals(1,next.snapshot().events.size);assertEquals(LogStep.PAGE_OPEN,next.snapshot().events.single().step)
        assertEquals(1L,next.snapshot().rejected);assertFalse(next.text().contains("private-secret"))
    }

    @Test fun unknownFieldsAndUnsafeHydratedMetadataAreDroppedRatherThanEchoed()=fixture { f ->
        val first=f.journal();repeat(3) { first.event(LogStep.STARTUP) };f.worker.drain()
        assertTrue("journal file must be persisted",f.file.isFile)
        val root=JsonParser.parseString(f.file.readText()).asJsonObject
        val rows=root.getAsJsonArray("events")
        rows[0].asJsonObject.addProperty("chat","private-secret")
        rows[1].asJsonObject.addProperty("version","https://private-secret")
        rows[2].asJsonObject.addProperty("state","not-a-state")
        f.file.writeText(root.toString());val next=f.journal();next.event(LogStep.LOG_OPEN);f.worker.drain()
        assertEquals(1,next.snapshot().events.size);assertEquals(3L,next.snapshot().rejected)
        assertFalse(next.text().contains("private-secret"));assertFalse(f.file.readText().contains("private-secret"))
    }

    @Test fun eventCountRetentionAndByteBoundsEvictOldestWithDurableCounters()=fixture { f ->
        val journal=f.journal(events=3)
        repeat(8) { journal.event(LogStep.LOCAL_TASK,metrics=mapOf(LogMetric.COUNT to it.toLong()));f.time++ };f.worker.drain()
        assertEquals(listOf(5L,6L,7L),journal.snapshot().events.map { it.metrics[LogMetric.COUNT] })
        assertEquals(5L,journal.snapshot().evicted)
        val reload=f.journal(events=3);f.worker.drain();assertEquals(5L,reload.snapshot().evicted)
        val small=f.journal(bytes=2048);repeat(50) { small.event(LogStep.PAGE_OPEN) };f.worker.drain()
        assertTrue(f.file.length()<=2048);assertTrue(small.snapshot().evicted>0)
    }

    @Test fun ageRetentionRunsWhenLoadingAndAppending()=fixture { f ->
        val journal=f.journal(retention=100);journal.event(LogStep.STARTUP);f.worker.drain();f.time+=101
        val next=f.journal(retention=100);f.worker.drain();assertTrue(next.snapshot().events.isEmpty());assertEquals(1L,next.snapshot().evicted)
        next.event(LogStep.PAGE_OPEN);f.time+=101;next.event(LogStep.LOG_OPEN)
        assertEquals(LogStep.LOG_OPEN,next.snapshot().events.single().step);assertEquals(2L,next.snapshot().evicted)
    }

    @Test fun expiredEvidenceIsRemovedBeforeReadingEvenWithoutAnotherAppend()=fixture { f ->
        val journal=f.journal(retention=100);journal.event(LogStep.STARTUP);f.worker.drain();f.time+=101
        assertTrue(journal.snapshot().events.isEmpty());assertEquals(1L,journal.snapshot().evicted)
        f.worker.drain()
        assertEquals(0,JsonParser.parseString(f.file.readText()).asJsonObject.getAsJsonArray("events").size())
    }

    @Test fun unexpiredAppendsAndCapacityEvictionsDoNotTraverseRetainedRows()=fixture { f ->
        val journal=f.journal(events=32,retention=1000);f.worker.runOne()
        repeat(32) { journal.event(LogStep.LOCAL_TASK);f.time++ }
        val retained=instrumentRows(journal)
        repeat(100) { journal.event(LogStep.LOCAL_TASK);f.time++ }
        assertEquals("appends must inspect only timestamp candidates and evicted rows",0,retained.visits)
        assertEquals(32,journal.snapshot().events.size);assertEquals(100L,journal.snapshot().evicted)
    }

    @Test fun unexpiredSnapshotVisitsEachRowOnlyToProduceTheSnapshot()=fixture { f ->
        val journal=f.journal();f.worker.runOne();repeat(200) { journal.event(LogStep.LOCAL_TASK) }
        val retained=instrumentRows(journal)
        assertEquals(200,journal.snapshot().events.size)
        assertEquals("retention must not add a second scan",200,retained.visits)
    }

    @Test fun expirationAfterClockRollbackPreservesLaterExpiryOfEarlierRows()=fixture { f ->
        val journal=f.journal(retention=1000);f.worker.runOne()
        journal.event(LogStep.STARTUP);f.time+=100;journal.event(LogStep.PAGE_OPEN)
        f.time-=600;journal.event(LogStep.UI_ACTION)
        f.time=1_000_501
        assertEquals(listOf(LogStep.STARTUP,LogStep.PAGE_OPEN),journal.snapshot().events.map { it.step })
        f.time=1_001_001
        assertEquals(listOf(LogStep.PAGE_OPEN),journal.snapshot().events.map { it.step })
        assertEquals(2L,journal.snapshot().evicted)
    }

    @Test fun clockRollbackDoesNotTurnAnObservedCompletionIntoIncompleteEvidence()=fixture { f ->
        val journal=f.journal();val op=journal.begin(LogStep.READ_TAP);f.time-=100
        journal.end(op,LogState.OK);f.worker.drain()
        val next=f.journal();f.worker.drain()
        assertEquals(0,next.snapshot().incompletePrevious)
        assertEquals(0L,next.snapshot().events.first { it.state==LogState.OK }.metrics[LogMetric.DURATION_MS])
    }

    @Test fun invalidVersionAndMetricsAreSanitizedBeforeMemoryAndDisk()=fixture { f ->
        val journal=f.journal(version="https://private-secret")
        val input=mutableMapOf(LogMetric.COUNT to Long.MAX_VALUE,LogMetric.DURATION_MS to -50L,LogMetric.HAS_DRAFT to 17L)
        journal.event(LogStep.UI_ACTION,metrics=input);input[LogMetric.COUNT]=7;f.worker.drain()
        assertEquals(1,journal.snapshot().events.size)
        val row=journal.snapshot().events.single();assertEquals("unknown",row.version)
        assertEquals(1_000_000_000L,row.metrics[LogMetric.COUNT]);assertEquals(0L,row.metrics[LogMetric.DURATION_MS]);assertEquals(1L,row.metrics[LogMetric.HAS_DRAFT])
        assertFalse(journal.text().contains("private-secret"));assertFalse(f.file.readText().contains("private-secret"))
    }

    @Test fun writeFailuresKeepMemoryEvidenceAndRecoverAtNextFlush()=fixture { f ->
        val blocker=File(f.dir,"parent");blocker.writeText("existing")
        var target=File(blocker,"journal.json")
        val journal=f.journal(location={target});journal.event(LogStep.MODEL_TEST,LogState.FAIL,LogReason.NETWORK_TIMEOUT);f.worker.drain()
        assertEquals(1,journal.snapshot().events.size);assertTrue(journal.snapshot().writeFailures>0);assertFalse(journal.snapshot().persisted)
        assertTrue(journal.text().contains("写入失败"));assertFalse(journal.text().contains("正常运行"))
        target=f.file;journal.flush();f.worker.drain();assertTrue(journal.snapshot().persisted)
        val reload=f.journal();f.worker.drain();assertEquals(LogReason.NETWORK_TIMEOUT,reload.snapshot().events.single().reason)
        assertTrue(reload.snapshot().writeFailures>0)
    }

    @Test fun queuedWritesAreBatchedAndFlushDoesNotWaitForDisk()=fixture { f ->
        val delayed=mutableListOf<()->Unit>();val journal=f.journal(delay={ delayed.add(it) });f.worker.drain()
        repeat(40) { journal.event(LogStep.UI_ACTION) }
        assertEquals(1,delayed.size);assertFalse(f.file.exists())
        journal.flush();assertFalse(f.file.exists());f.worker.drain();assertTrue(f.file.isFile)
        assertEquals(40,JsonParser.parseString(f.file.readText()).asJsonObject.getAsJsonArray("events").size())
    }

    @Test fun exportUsesFixedLabelsLimitsRowsAndExplainsUnknownEvidence()=fixture { f ->
        val journal=f.journal();repeat(4) { journal.event(LogStep.UI_ACTION) }
        val loading=journal.text(2);assertTrue(loading.contains("加载中"));assertEquals(2,Regex("UI_ACTION").findAll(loading).count())
        f.worker.drain();val text=journal.text(2)
        assertTrue(text.contains("V1.4.11"));assertTrue(text.contains("保留"));assertTrue(text.contains("淘汰"));assertTrue(text.contains("未知"))
        assertTrue(journal.text(0).isNotBlank());assertFalse(journal.text(0).contains("UI_ACTION"))
    }

    @Test fun exceptionMessagesAndInjectedFaultFieldsNeverReachDisk()=fixture { f ->
        val journal=f.journal();val error=IllegalStateException("chat nickname draft https://private-secret sk-token")
        journal.event(LogStep.EXCEPTION,LogState.FAIL,fault=LogFault.capture(error))
        journal.event(LogStep.EXCEPTION,LogState.FAIL,fault=LogFault(LogReason.UNEXPECTED,listOf("https://private-secret"),listOf("Bearer sk-token")))
        f.worker.drain();assertTrue("journal file must be persisted",f.file.isFile)
        val text=journal.text()+f.file.readText()
        for(secret in listOf("nickname","draft","private-secret","sk-token","Bearer")) assertFalse(secret,text.contains(secret))
        assertTrue(text.contains("java.lang.IllegalStateException"))
    }

    @Test fun executorRejectionDoesNotEscapeAndMemoryStillReportsFailure()=fixture { f ->
        val rejected=Executor { throw java.util.concurrent.RejectedExecutionException("private-secret") }
        val journal=DiagnosticJournal({f.file},rejected,"V1.4.11",41,32,{f.time})
        journal.event(LogStep.STARTUP);journal.flush()
        assertEquals(1,journal.snapshot().events.size);assertTrue(journal.snapshot().historyFailures>0);assertTrue(journal.snapshot().writeFailures>0)
        assertFalse(journal.text().contains("private-secret"))
    }
}
