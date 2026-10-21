package com.loveadvisor.android.analysis
import android.os.Handler
import android.os.Looper
import com.loveadvisor.android.AppGraph
import com.loveadvisor.android.settings.SettingsWorkScope
import com.loveadvisor.core.analysis.*
import com.loveadvisor.core.model.ChatSnapshot
import com.google.gson.Gson
import java.security.MessageDigest
import com.loveadvisor.android.diagnostics.DiagnosticLogs as Logs
import com.loveadvisor.core.diagnostics.*
class MessageAnalysisController(private val graph:AppGraph) {
    private val gate=JudgmentRequestGate()
    private val scope=SettingsWorkScope(graph)
    private val cache=JudgmentCache<MessageJudgment>()
    private var operation:LogOperation?=null
    fun revision():String=MessageDigest.getInstance("SHA-256").digest(Gson().toJson(graph.settings.load().judgeProfile()).toByteArray()).joinToString("") { "%02x".format(it) }
    fun analyze(snapshot:ChatSnapshot,targetId:String,onResult:(MessageJudgment?,String?)->Unit):()->Unit {
        Logs.event(LogStep.JUDGMENT_TAP)
        val session=graph.tracker.currentToken ?: run { Logs.event(LogStep.JUDGMENT_TAP,LogState.FAIL,LogReason.NO_READ_CONTEXT);onResult(null,"请先读取信息，才能判断分析");return {} }
        if(graph.tracker.lastSnapshot!=snapshot) { Logs.event(LogStep.JUDGMENT_TAP,LogState.FAIL,LogReason.CONTEXT_CHANGED);onResult(null,"对话已变化，请重新读取");return {} }
        val context=try { MessageJudgmentService.context(snapshot,targetId) } catch(e:Exception) { Logs.failed(LogStep.JUDGMENT_TAP,e);onResult(null,e.message);return {} }
        Logs.end(operation,LogState.CANCELLED,LogReason.USER_CANCELLED)
        val log=Logs.begin(LogStep.JUDGMENT_REQUEST,metrics=mapOf(LogMetric.MESSAGE_COUNT to snapshot.messages.size.toLong()));operation=log
        val profile=graph.settings.load().judgeProfile()
        val key=JudgmentKey(session,targetId,revision());val ticket=gate.begin(key)
        fun valid()=gate.accept(key,ticket) && graph.tracker.isCurrent(session) && revision()==key.judgeRevision
        val hit=cache.get(key)
        Logs.event(LogStep.JUDGMENT_CACHE,LogState.OK,operation=log,metrics=mapOf(LogMetric.CACHED to if(hit!=null)1L else 0L))
        if(hit!=null)Handler(Looper.getMainLooper()).post { if(valid()) { Logs.end(log,LogState.OK);onResult(hit,null) } else Logs.end(log,LogState.CANCELLED,LogReason.RESULT_STALE) }
        else scope.work("judgment",{ cancel -> Logs.within(log) { graph.judge.inspect(profile,context,"",cancel,targetId) } },{ result ->
            if(valid()) { Logs.end(log,LogState.OK);cache.put(key,result);onResult(result,null) }
            else { Logs.end(log,LogState.CANCELLED,LogReason.RESULT_STALE);if(gate.accept(key,ticket))onResult(null,"对话或判断模型已变化，请重新选择") }
        },{ error -> Logs.end(log,LogState.FAIL,LogReason.STEP_FAILED);if(gate.accept(key,ticket))onResult(null,if(valid())error else "对话或模型已变化，请重新读取") })
        return { Logs.end(log,LogState.CANCELLED,LogReason.USER_CANCELLED);gate.invalidate();scope.cancel("judgment") }
    }
    fun cancel() { Logs.end(operation,LogState.CANCELLED,LogReason.USER_CANCELLED);operation=null;gate.invalidate();scope.cancel("judgment") }
    fun invalidate() { cancel();cache.clear() }
    fun close() { invalidate();scope.close() }
}
