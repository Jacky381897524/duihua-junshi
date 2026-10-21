package com.loveadvisor.core.diagnostics
import org.junit.Assert.*
import org.junit.Test

class DiagnosticCrashHandlerTest {
    @Test fun forwardsSameThreadAndErrorAfterReceipt() {
        val steps=mutableListOf<String>();val thread=Thread.currentThread();val error=OutOfMemoryError("private message")
        val handler=DiagnosticCrashHandler({ t,e->assertSame(thread,t);assertSame(error,e);steps.add("delegate") },{ e->assertSame(error,e);steps.add("receipt") })
        handler.uncaughtException(thread,error)
        assertEquals(listOf("receipt","delegate"),steps)
    }
    @Test fun receiptFailureCannotSwallowOriginalFatalError() {
        var original:Throwable?=null;val error=LinkageError("private")
        DiagnosticCrashHandler({ _,e->original=e },{ throw OutOfMemoryError() }).uncaughtException(Thread.currentThread(),error)
        assertSame(error,original)
    }
    @Test fun originalHandlerFailurePropagates() {
        val expected=IllegalStateException()
        try { DiagnosticCrashHandler({ _,_->throw expected },{}).uncaughtException(Thread.currentThread(),Error());fail() } catch(actual:IllegalStateException) { assertSame(expected,actual) }
    }
}
