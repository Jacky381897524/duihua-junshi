package com.loveadvisor.android.input

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.capture.NodeSnapshotReader
import com.loveadvisor.core.input.*
import com.loveadvisor.core.model.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Binder reads run off the UI thread; every asynchronous step rechecks the chat token. */
class ReplyFiller(private val service:ChatCaptureService) {
    private val main=Handler(Looper.getMainLooper())
    fun fill(token:SessionToken,text:String,callback:(Boolean,String)->Unit)=begin(token,text,null,callback)
    fun fill(token:SessionToken,text:String,onConfirm:(String,()->Unit,()->Unit)->Unit,callback:(Boolean,String)->Unit)=begin(token,text,onConfirm,callback)
    private fun begin(token:SessionToken,text:String,onConfirm:((String,()->Unit,()->Unit)->Unit)?,callback:(Boolean,String)->Unit):()->Unit {
        val guard=FillGuard(service.graph.tracker,allowOlderRevision=true)
        val generation=service.captureGeneration()
        val finished=AtomicBoolean(false)
        val started=android.os.SystemClock.elapsedRealtime()
        fun active()=!finished.get() && ChatCaptureService.instance===service && service.overlayAllowed() && generation==service.captureGeneration() && guard.isCurrent(token)
        fun done(success:Boolean,message:String) { if(!finished.compareAndSet(false,true))return;val result=if(success || message.startsWith("填入失败："))message else FillFeedback.failure(message,false);service.fillDiagnostic(result);service.graph.record("fill",android.os.SystemClock.elapsedRealtime()-started,null,if(success)null else "failure");callback(success,result) }
        service.fillDiagnostic("正在检查当前聊天与输入框",reset=true)
        main.postDelayed({ if(!finished.get())done(false,"填入超时，请在连接诊断查看最近填入") },25000)
        fun fallback(message:String) { if(!active())done(false,"聊天已变化，请重新读取") else { ClipboardController.copy(service,text,notify=false);done(false,FillFeedback.failure(message,true)) } }
        fun read(callbackTarget:(FillTarget?)->Unit) {
            if(!active()) { done(false,"聊天已变化，请重新读取");return }
            worker.execute {
                val read=runCatching { service.readFillWindow() }.getOrNull()
                main.post {
                    if(finished.get())return@post
                    if(!active()) { done(false,"聊天已变化，请重新读取");return@post }
                    val snapshot=read?.let { service.currentForFill(it.window) }
                    val candidate=read?.editables?.filter { val r=it.first; r.bounds.top>read.window.viewport.top+read.window.viewport.height*.55 && !r.text.orEmpty().contains("搜索") }?.maxByOrNull { it.first.bounds.top }
                    val target=if(read!=null && snapshot!=null && candidate!=null) FillTarget(snapshot,read.window,candidate.first,candidate.second) else null
                    callbackTarget(target?.takeIf { guard.verify(token,it.snapshot,it.window,it.editable).allowed })
                }
            }
        }
        fun action(target:FillTarget,command:Int,bundle:Bundle?=null,next:(Boolean)->Unit) {
            if(!active()) { done(false,"聊天已变化，请重新读取");return }
            worker.execute { val result=runCatching { if(active()) (target.handle as AccessibilityNodeInfo).performAction(command,bundle) else false }.getOrDefault(false);main.post { if(!finished.get()) { if(active())next(result) else done(false,"聊天已变化，请重新读取") } } }
        }
        fun verify(attempt:Int) {
            main.postDelayed({ read { observed ->
                if(observed?.editable?.text==text) done(true,"已填入微信草稿，请自行发送")
                else if(attempt<3) verify(attempt+1) else fallback("无法确认写入结果，请核对草稿")
            } },if(attempt==0)120L else 180L)
        }
        fun write(expectedDraft:String) {
            read freshRead@{ fresh ->
                if(fresh==null) { fallback("微信未提供可操作的聊天输入框");return@freshRead }
                if(fresh.editable.text.orEmpty()!=expectedDraft) { done(false,"草稿已变化，已保留原稿，请重新选择回复");return@freshRead }
                action(fresh,AccessibilityNodeInfo.ACTION_CLICK) { action(fresh,AccessibilityNodeInfo.ACTION_FOCUS) {
                    // IME appears asynchronously. Wait, reread the target, then write.
                    main.postDelayed({ read focusedRead@{ focused ->
                        if(focused==null) { fallback("无法确认聚焦后的聊天输入框");return@focusedRead }
                        if(focused.editable.text.orEmpty()!=expectedDraft) { done(false,"草稿已变化，已保留原稿");return@focusedRead }
                        action(focused,AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text) }) { set ->
                            if(set) verify(0) else {
                                ClipboardController.copy(service,text,notify=false)
                                read pasteRead@{ paste ->
                                    if(paste==null || paste.editable.text.orEmpty()!=expectedDraft) { done(false,"输入框或草稿已变化，已复制回复");return@pasteRead }
                                    if(expectedDraft.isEmpty()) action(paste,AccessibilityNodeInfo.ACTION_PASTE) { verify(0) }
                                    else action(paste,AccessibilityNodeInfo.ACTION_SET_SELECTION,Bundle().apply {
                                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,0)
                                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,expectedDraft.length)
                                    }) { selected ->
                                        if(!selected) fallback("系统不支持替换草稿，已保留原稿")
                                        else read selectionRead@{ selectedTarget ->
                                            if(selectedTarget==null || selectedTarget.editable.text.orEmpty()!=expectedDraft) { done(false,"草稿已变化，已复制回复");return@selectionRead }
                                            action(selectedTarget,AccessibilityNodeInfo.ACTION_PASTE) { verify(0) }
                                        }
                                    }
                                }
                            }
                        }
                    } },140)
                } }
            }
        }
        read { first ->
            if(first==null) {
                service.fillDiagnostic("原生输入框不可用，尝试系统输入连接")
                service.fillStage("native-unavailable")
                val confirm=onConfirm?.let { present -> { draft:String,write:()->Unit -> present(draft,write,{ done(false,"已保留微信原稿") }) } }
                ImeReplyFiller(service).fill(token,text,confirm,{ active() }) { success,message -> done(success,message) }
                return@read
            }
            val draft=first.editable.text.orEmpty()
            if(draft.isNotBlank() && draft!=text) {
                if(onConfirm==null) fallback("输入框已有草稿，未覆盖")
                else onConfirm(draft,{ if(!finished.get())write(draft) },{ done(false,"已保留微信原稿") })
            } else write(draft)
        }
        return { if(Looper.myLooper()==Looper.getMainLooper())done(false,"已取消填入，保留原稿") else main.post { done(false,"已取消填入，保留原稿") } }
    }
    companion object { private val worker=Executors.newSingleThreadExecutor { r->Thread(r,"verified-wechat-fill") } }
}
