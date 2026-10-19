package com.loveadvisor.android.analysis
import android.os.Handler
import android.os.Looper
import com.loveadvisor.android.AppGraph
import com.loveadvisor.android.settings.SettingsWorkScope
import com.loveadvisor.core.analysis.*
import com.loveadvisor.core.model.ChatSnapshot
import com.google.gson.Gson
import java.security.MessageDigest
class MessageAnalysisController(private val graph:AppGraph) {
    private val gate=JudgmentRequestGate()
    private val scope=SettingsWorkScope(graph)
    private val cache=LinkedHashMap<JudgmentKey,MessageJudgment>()
    fun revision():String=MessageDigest.getInstance("SHA-256").digest(Gson().toJson(graph.settings.load().judgeProfile()).toByteArray()).joinToString("") { "%02x".format(it) }
    fun analyze(snapshot:ChatSnapshot,targetId:String,onResult:(MessageJudgment?,String?)->Unit):()->Unit {
        val session=graph.tracker.currentToken ?: run { onResult(null,"请先读取信息，才能判断分析");return {} }
        if(graph.tracker.lastSnapshot!=snapshot) { onResult(null,"对话已变化，请重新读取");return {} }
        val context=try { MessageJudgmentService.context(snapshot,targetId) } catch(e:Exception) { onResult(null,e.message);return {} }
        val profile=graph.settings.load().judgeProfile()
        val key=JudgmentKey(session,targetId,revision());val ticket=gate.begin(key)
        fun valid()=gate.accept(key,ticket) && graph.tracker.isCurrent(session) && revision()==key.judgeRevision
        val hit=cache[key]
        if(hit!=null)Handler(Looper.getMainLooper()).post { if(valid())onResult(hit,null) }
        else scope.work("judgment",{ cancel -> graph.judge.inspect(profile,context,"",cancel) },{ result ->
            if(valid()) { cache[key]=result;while(cache.size>50)cache.remove(cache.keys.first());onResult(result,null) }
            else if(gate.accept(key,ticket))onResult(null,"对话或判断模型已变化，请重新选择")
        },{ error -> if(gate.accept(key,ticket))onResult(null,if(valid())error else "对话或模型已变化，请重新读取") })
        return { gate.invalidate();scope.cancel("judgment") }
    }
    fun close() { gate.invalidate();cache.clear();scope.close() }
}
