package com.loveadvisor.core.diagnostics

import com.loveadvisor.core.api.ApiException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.*
import org.junit.Test

class DiagnosticLogTest {
    @Test fun classifiesTypedFailuresWithoutInspectingTheirMessages() {
        val rows=listOf(SocketTimeoutException("sk-secret") to LogReason.NETWORK_TIMEOUT,UnknownHostException("https://secret") to LogReason.NETWORK_DNS,ConnectException("draft") to LogReason.NETWORK_CONNECTION,SSLException("nickname") to LogReason.NETWORK_TLS,IllegalStateException("timeout 401 unauthorized") to LogReason.UNEXPECTED)
        for((error,expected) in rows) assertEquals(expected,LogFault.capture(error).reason)
    }

    @Test fun apiStatusesAreRetainedWithoutResponseBodies() {
        for((status,expected) in listOf(401 to LogReason.AUTH_ERROR,403 to LogReason.AUTH_ERROR,429 to LogReason.RATE_LIMIT,503 to LogReason.REMOTE_ERROR,400 to LogReason.HTTP_ERROR)) {
            val fault=LogFault.capture(ApiException(status,"sk-secret https://private chat draft"))
            assertEquals(expected,fault.reason);assertEquals(status,fault.httpStatus)
            assertFalse(fault.toString().contains("sk-secret"));assertFalse(fault.toString().contains("https://"))
        }
    }

    @Test fun boundsCauseDepthAndPrefersOwnedCodeFramesWithoutFilenames() {
        var error:Throwable=IllegalArgumentException("chat-secret")
        repeat(7) { error=IllegalStateException("token-secret",error) }
        error.stackTrace=(1..12).map { StackTraceElement("java.util.Helper","call","private-secret.kt",it) }.plus(StackTraceElement("com.loveadvisor.core.capture.Reader","read","draft-secret.kt",77)).toTypedArray()
        val fault=LogFault.capture(error)
        assertEquals(4,fault.types.size);assertEquals(8,fault.frames.size)
        assertTrue(fault.frames.first().contains("com.loveadvisor.core.capture.Reader.read:77"))
        assertFalse(fault.toString().contains("secret"));assertFalse(fault.toString().contains(".kt"))
    }

    @Test fun handlesCyclicCausesAndRejectsInjectedStackText() {
        val error=object:RuntimeException("sk-secret") { override val cause:Throwable get()=this }
        error.stackTrace=arrayOf(StackTraceElement("https://private","nickname\nBearer secret","draft-secret",9))
        val fault=LogFault.capture(error)
        assertEquals(1,fault.types.size);assertTrue(fault.frames.isEmpty());assertFalse(fault.toString().contains("secret"))
    }

    @Test fun discoversTypedCauseWithoutGuessingFromWrapperText() {
        val fault=LogFault.capture(RuntimeException("HTTP 403 draft secret",SocketTimeoutException("private endpoint")))
        assertEquals(LogReason.NETWORK_TIMEOUT,fault.reason);assertEquals(2,fault.types.size)
        assertFalse(fault.toString().contains("secret"));assertFalse(fault.toString().contains("endpoint"))
    }
}
