package com.loveadvisor.android.diagnostics
import com.loveadvisor.core.api.*
import com.loveadvisor.core.diagnostics.*

/** Logs outcome, character count and numeric usage only; never retains content or credentials. */
class DiagnosticTransport(private val delegate:HttpTransport=JdkHttpTransport(),private val step:LogStep=LogStep.LOCAL_TASK):HttpTransport {
    override fun execute(request:ApiRequest,cancel:Cancellation):ApiResponse {
        val op=DiagnosticLogs.begin(step,DiagnosticLogs.parent(),metrics=mapOf(LogMetric.REQUEST_CHARS to (request.body?.length ?: 0).toLong()))
        try {
            val response=delegate.execute(request,cancel)
            val ok=response.status in 200..299
            val reason=when(response.status) { 401,403->LogReason.AUTH_ERROR;429->LogReason.RATE_LIMIT;in 500..599->LogReason.REMOTE_ERROR;else->if(ok)LogReason.NONE else LogReason.HTTP_ERROR }
            DiagnosticLogs.end(op,if(ok)LogState.OK else LogState.FAIL,reason,metrics=mapOf(LogMetric.HTTP_STATUS to response.status.toLong())+TokenUsage.counters(response.body))
            return response
        } catch(error:java.util.concurrent.CancellationException) { DiagnosticLogs.end(op,LogState.CANCELLED,LogReason.USER_CANCELLED);throw error }
        catch(error:Exception) { val fault=LogFault.capture(error);DiagnosticLogs.end(op,LogState.FAIL,fault.reason,fault);throw error }
    }
}
