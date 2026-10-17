package com.loveadvisor.android.ui
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import android.content.Intent
import com.loveadvisor.android.AppGraph
import com.loveadvisor.core.api.Cancellation
import java.util.concurrent.Executors
open class BaseActivity:Activity() {
    protected val graph get()=AppGraph.get(this)
    protected lateinit var body:LinearLayout
    private val tasks=Executors.newSingleThreadExecutor()
    private val cancellations=mutableListOf<Cancellation>()
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); window.decorView.setBackgroundColor(ViewStyles.themed(this,ViewStyles.BG)) }
    protected fun page(title:String,subtitle:String?=null) {
        val scroll=ScrollView(this); body=ViewStyles.column(this,22); scroll.addView(body); setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view,insets -> val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime()); view.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets }
        body.addView(ViewStyles.text(this,title,30f,bold=true)); subtitle?.let { body.addView(ViewStyles.text(this,it,14f,ViewStyles.MUTED)) }
    }
    protected fun <T> work(stage:String,operation:(Cancellation)->T,success:(T)->Unit,failure:((String)->Unit)?=null) {
        val cancel=Cancellation(); cancellations.add(cancel); val began=System.currentTimeMillis()
        tasks.execute {
            try { val value=operation(cancel); cancel.check(); graph.record(stage,System.currentTimeMillis()-began,null,null); runOnUiThread { if(!isDestroyed && !cancel.cancelled) success(value) } }
            catch(_:java.util.concurrent.CancellationException) { }
            catch(e:Exception) { graph.record(stage,System.currentTimeMillis()-began,(e as? com.loveadvisor.core.api.ApiException)?.status,"failure"); val message=graph.safeError(e); runOnUiThread { if(!isDestroyed && !cancel.cancelled) { if(failure!=null) failure(message) else toast(message) } } }
            finally { runOnUiThread { cancellations.remove(cancel) } }
        }
    }
    protected fun toast(text:String)=Toast.makeText(this,text,Toast.LENGTH_LONG).show()
    protected fun open(type:Class<out Activity>)=startActivity(Intent(this,type))
    protected fun confirm(title:String,message:String,action:()->Unit) { AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消",null).setPositiveButton("确认") { _,_ -> action() }.show() }
    override fun onDestroy() { cancellations.toList().forEach { it.cancel() }; tasks.shutdownNow(); super.onDestroy() }
}
