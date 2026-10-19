package com.loveadvisor.core.startup

import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class StartupLoaderTest {
    private class Queue:Executor {
        val tasks=ArrayDeque<Runnable>()
        override fun execute(command:Runnable) { tasks.addLast(command) }
        fun drain() { while(tasks.isNotEmpty())tasks.removeFirst().run() }
    }
    @Test fun initializationNeverRunsOnTheCallerAndOneLoadServesHomeAndService() {
        val worker=Queue();val ui=Queue();var loads=0
        val loader=StartupLoader(worker,ui) { loads++;"settings-ready" }
        val home=mutableListOf<String>();val service=mutableListOf<String>()
        loader.observe { if(it.phase==StartupPhase.READY)home.add(it.value!!) }
        loader.observe { if(it.phase==StartupPhase.READY)service.add(it.value!!) }
        loader.start();loader.start();ui.drain()
        assertEquals(0,loads);assertEquals(1,worker.tasks.size);assertTrue(home.isEmpty())
        worker.drain();assertEquals(1,loads);assertTrue(home.isEmpty())
        ui.drain();assertEquals(listOf("settings-ready"),home);assertEquals(listOf("settings-ready"),service)
    }
    @Test fun slowInitializationReportsAStatusThenRecoversWithoutASecondLoad() {
        val worker=Queue();val ui=Queue();var loads=0
        val loader=StartupLoader(worker,ui) { loads++;42 }
        val phases=mutableListOf<StartupPhase>();loader.observe { phases.add(it.phase) }
        loader.start();val attempt=loader.state.attempt;loader.markSlow(attempt);ui.drain()
        assertEquals(StartupPhase.SLOW,phases.last())
        loader.start();assertEquals(1,worker.tasks.size)
        worker.drain();ui.drain();assertEquals(StartupPhase.READY,phases.last());assertEquals(42,loader.state.value);assertEquals(1,loads)
    }
    @Test fun detachedActivityNeverReceivesLateResultAndRetryPreservesExistingData() {
        val worker=Queue();val ui=Queue();var calls=0;val existing=mutableMapOf("style" to "简短","key" to "saved")
        val loader=StartupLoader(worker,ui) { if(calls++==0)throw IllegalStateException("private contents");existing.getValue("style") }
        var deliveries=0;val close=loader.observe { deliveries++ }
        loader.start();worker.drain();assertEquals(StartupPhase.FAILED,loader.state.phase)
        close();ui.drain();assertEquals(0,deliveries)
        val result=mutableListOf<String>();loader.observe { if(it.phase==StartupPhase.READY)result.add(it.value!!) }
        loader.start();worker.drain();ui.drain()
        assertEquals(listOf("简短"),result);assertEquals(mapOf("style" to "简短","key" to "saved"),existing)
    }
    @Test fun timeoutFromAnOlderAttemptCannotReplaceSuccessOrANewRetry() {
        val worker=Queue();val ui=Queue();var calls=0
        val loader=StartupLoader(worker,ui) { if(calls++==0)throw IllegalArgumentException();"ok" }
        loader.start();val old=loader.state.attempt;worker.drain()
        loader.start();loader.markSlow(old);assertEquals(StartupPhase.LOADING,loader.state.phase)
        worker.drain();loader.markSlow(loader.state.attempt);assertEquals(StartupPhase.READY,loader.state.phase)
    }
}
