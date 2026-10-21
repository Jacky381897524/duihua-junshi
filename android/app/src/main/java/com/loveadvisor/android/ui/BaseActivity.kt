package com.loveadvisor.android.ui
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import android.content.Intent
import com.loveadvisor.android.AppGraph
import com.loveadvisor.core.api.Cancellation
import java.util.concurrent.Executors
import com.loveadvisor.android.diagnostics.DiagnosticLogs as Logs
import com.loveadvisor.core.diagnostics.*
open class BaseActivity:Activity() {
    protected val graph get()=AppGraph.get(this)
    protected lateinit var body:LinearLayout
    private val tasks=Executors.newSingleThreadExecutor()
    private val cancellations=mutableListOf<Cancellation>()
    private val logTasks=mutableMapOf<Cancellation,LogOperation?>()
    private val pageLog by lazy { Logs.begin(LogStep.PAGE_OPEN) }
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);Logs.event(LogStep.PAGE_OPEN,reason=Logs.page(javaClass.simpleName),operation=pageLog);window.decorView.setBackgroundColor(ViewStyles.themed(this,ViewStyles.BG)) }
    protected fun page(title:String,subtitle:String?=null) {
        val scroll=ScrollView(this); body=ViewStyles.column(this,22); scroll.addView(body); setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view,insets -> val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime()); view.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets }
        body.addView(ViewStyles.text(this,title,30f,bold=true)); subtitle?.let { body.addView(ViewStyles.text(this,it,14f,ViewStyles.MUTED)) }
    }
    protected fun <T> work(stage:String,operation:(Cancellation)->T,success:(T)->Unit,failure:((String)->Unit)?=null) {
        val cancel=Cancellation(); cancellations.add(cancel)
        val log=Logs.begin(Logs.step(stage),pageLog);logTasks[cancel]=log
        tasks.execute {
            try { val value=Logs.within(log) { operation(cancel) }; cancel.check();runOnUiThread {
                if(!isDestroyed && !cancel.cancelled) { try { success(value);Logs.end(log,LogState.OK) } catch(error:Exception) { val fault=LogFault.capture(error);Logs.end(log,LogState.FAIL,fault.reason,fault);throw error } }
                else Logs.end(log,LogState.CANCELLED,LogReason.RESULT_STALE)
            } }
            catch(_:java.util.concurrent.CancellationException) { Logs.end(log,LogState.CANCELLED,LogReason.USER_CANCELLED) }
            catch(e:Exception) { val fault=LogFault.capture(e);Logs.end(log,LogState.FAIL,fault.reason,fault);val message=graph.safeError(e); runOnUiThread { if(!isDestroyed && !cancel.cancelled) { if(failure!=null) failure(message) else toast(message) } } }
            finally { runOnUiThread { cancellations.remove(cancel);logTasks.remove(cancel) } }
        }
    }
    protected fun toast(text:String) { if(text.contains("失败") || text.contains("无效") || text.contains("无法"))Logs.feedback(text);Toast.makeText(this,text,Toast.LENGTH_LONG).show() }
    protected fun open(type:Class<out Activity>)=startActivity(Intent(this,type))
    protected fun confirm(title:String,message:String,action:()->Unit) { AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消",null).setPositiveButton("确认") { _,_ -> action() }.show() }
    override fun onDestroy() { cancellations.toList().forEach { it.cancel();Logs.end(logTasks[it],LogState.CANCELLED,LogReason.USER_CANCELLED) }; tasks.shutdownNow();Logs.event(LogStep.PAGE_CLOSE,operation=pageLog);Logs.end(pageLog,LogState.OK);Logs.flush();super.onDestroy() }
}
