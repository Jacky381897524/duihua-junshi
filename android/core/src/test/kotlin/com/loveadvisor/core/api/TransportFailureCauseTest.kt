package com.loveadvisor.core.api
import java.net.ServerSocket
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test

class TransportFailureCauseTest {
    @Test fun networkTimeoutKeepsTypedCauseForMetadataDiagnostics() {
        val server=ServerSocket(0)
        val responder=Thread { try { server.accept().use { Thread.sleep(1500) } } catch(_:Exception) {} }.apply { isDaemon=true;start() }
        try {
            JdkHttpTransport(1000,100).execute(ApiRequest("GET","http://127.0.0.1:${server.localPort}/",emptyMap(),allowLanHttp=true),Cancellation())
            fail("expected timeout")
        } catch(error:ApiException) { assertTrue("typed timeout cause was lost",error.cause is SocketTimeoutException) }
        finally { server.close();responder.interrupt();responder.join(2000) }
    }
}
