package com.loveadvisor.android.input

import android.accessibilityservice.InputMethod
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.core.input.ImeFillPolicy
import com.loveadvisor.core.input.ImeConnectionWait
import com.loveadvisor.core.input.ImeConnectionStep
import com.loveadvisor.core.input.FillFeedback
import com.loveadvisor.core.model.SessionToken
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Official Android editing channel for WeChat builds which expose no editable nodes. */
class ImeReplyFiller(private val service:ChatCaptureService) {
    private val main=Handler(Looper.getMainLooper())
    fun fill(token:SessionToken,text:String,onConfirm:((String,()->Unit)->Unit)?,parentActive:()->Boolean={true},callback:(Boolean,String)->Unit) {
        val generation=service.captureGeneration();val finished=AtomicBoolean(false)
        val boundEditor=AtomicLong(-1)
        fun active()=!finished.get() && parentActive() && (boundEditor.get()<0 || boundEditor.get()==service.editorGeneration()) && ChatCaptureService.instance===service && service.overlayAllowed() && service.captureGeneration()==generation && service.graph.tracker.sameConversation(token)
        fun done(success:Boolean,message:String) { if(finished.compareAndSet(false,true))callback(success,message) }
        fun fail(message:String) { val copied=active();if(copied)ClipboardController.copy(service,text,notify=false);done(false,FillFeedback.failure(message,copied)) }
        fun connection():InputMethod.AccessibilityInputConnection? {
            if(Build.VERSION.SDK_INT<33 || !active())return null
            val method=service.inputMethod ?: return null
            val e=method.currentInputEditorInfo ?: return null
            return method.currentInputConnection.takeIf { e.packageName==token.packageName && ImeFillPolicy.editorAllowed(e.packageName,e.inputType,e.imeOptions) }
        }
        fun draft(next:(InputMethod.AccessibilityInputConnection,String)->Unit) {
            val epoch=service.editorGeneration()
            val c=connection() ?: run { fail("尚未连接微信聊天输入框，请点输入框后重试");return }
            boundEditor.compareAndSet(-1,epoch)
            if(!active() || epoch!=service.editorGeneration()) { done(false,"输入连接已变化，请重新选择回复");return }
            worker.execute {
                val surrounding=runCatching { c.getSurroundingText(4096,4096,0) }.getOrNull()
                main.post {
                    if(finished.get())return@post
                    if(!active()) { done(false,"聊天已变化，请重新读取");return@post }
                    if(surrounding==null || !ImeFillPolicy.completeDraft(surrounding.text.toString(),surrounding.offset,surrounding.selectionStart,surrounding.selectionEnd))fail("微信未返回完整草稿，已保留原稿")
                    else next(c,surrounding.text.toString())
                }
            }
        }
        fun verify(attempt:Int) {
            main.postDelayed({ if(!finished.get())draft { _,actual ->
                if(actual==text)done(true,"已填入微信草稿，请自行发送")
                else if(attempt<3)verify(attempt+1) else fail("输入接口未确认写入，请核对草稿")
            } },if(attempt==0)80L else 180L)
        }
        fun commitSelected(c:InputMethod.AccessibilityInputConnection,expected:String) {
            // Final identity check stays after the preparatory IPC and before every text mutation.
            service.verifyVisualFill(token) finalTitle@{ stillCurrent ->
                if(!stillCurrent || !active()) { done(false,"当前聊天或输入连接已变化，已保留原稿");return@finalTitle }
                worker.execute {
                    val finalDraft=runCatching { c.getSurroundingText(4096,4096,0) }.getOrNull()
                    val unchanged=finalDraft!=null && ImeFillPolicy.completeDraft(finalDraft.text.toString(),finalDraft.offset,finalDraft.selectionStart,finalDraft.selectionEnd) && finalDraft.text.toString()==expected && minOf(finalDraft.selectionStart,finalDraft.selectionEnd)==0 && maxOf(finalDraft.selectionStart,finalDraft.selectionEnd)==expected.length
                    main.post {
                        if(finished.get())return@post
                        val committed=runCatching { if(unchanged && active() && connection()!=null) { c.commitText(text,1,null);true } else false }.getOrDefault(false)
                        if(committed)verify(0) else done(false,"草稿或输入连接已变化，已保留原稿")
                    }
                }
            }
        }
        fun write(expected:String) {
            if(expected.isEmpty()) {
                // An empty draft has no text to select. Only read before the final guarded commit.
                draft { c,actual -> if(actual.isEmpty())commitSelected(c,expected) else done(false,"草稿已变化，已保留原稿") }
                return
            }
            service.verifyVisualFill(token) { verified ->
                if(!verified || !active()) { done(false,"无法确认仍为当前聊天，请重新读取");return@verifyVisualFill }
                draft fresh@{ c,actual ->
                    if(actual!=expected) { done(false,"草稿已变化，已保留原稿");return@fresh }
                    worker.execute {
                        val result=runCatching { if(active()) { c.setSelection(0,expected.length);true } else false }.getOrDefault(false)
                        main.post {
                            if(!result || !active()) { done(false,"聊天或输入连接已变化");return@post }
                            // Read back both selection and draft before replacing an existing draft.
                            worker.execute {
                                val selected=runCatching { c.getSurroundingText(4096,4096,0) }.getOrNull()
                                val safe=selected!=null && ImeFillPolicy.completeDraft(selected.text.toString(),selected.offset,selected.selectionStart,selected.selectionEnd) && selected.text.toString()==expected && minOf(selected.selectionStart,selected.selectionEnd)==0 && maxOf(selected.selectionStart,selected.selectionEnd)==expected.length
                                main.post selectedPost@{ if(!finished.get()) {
                                    if(!safe || !active()) { done(false,"草稿或输入连接已变化，已保留原稿");return@selectedPost }
                                    commitSelected(c,expected)
                                } }
                            }
                        }
                    }
                }
            }
        }
        fun begin() {
            draft { _,old ->
                if(old==text)service.verifyVisualFill(token) { if(it && active())done(true,"聊天草稿已是这条回复") else done(false,"当前聊天无法确认，请重新读取") }
                else if(old.isNotBlank()) {
                    if(onConfirm==null)fail("微信已有草稿，未覆盖")
                    else service.verifyVisualFill(token) { verified ->
                        if(verified && active())onConfirm(old) { if(!finished.get())write(old) }
                        else done(false,"当前聊天无法确认，请重新读取")
                    }
                } else write(old)
            }
        }
        main.postDelayed({ if(!finished.get())fail("填入超时，请重试") },15000)
        if(Build.VERSION.SDK_INT<33) { fail("当前系统不支持备用输入接口");return }
        val waiting=ImeConnectionWait()
        fun awaitConnection() {
            if(finished.get())return
            if(!active()) { done(false,"聊天或输入连接已变化，已保留原稿");return }
            val imeVisible=service.windows.any { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            when(waiting.next(connection()!=null,imeVisible)) {
                ImeConnectionStep.READY -> begin()
                ImeConnectionStep.FOCUS -> service.focusVisualComposer(token) { if(!finished.get())main.postDelayed({ awaitConnection() },150) }
                ImeConnectionStep.WAIT -> main.postDelayed({ awaitConnection() },150)
                ImeConnectionStep.FAIL -> fail("聊天输入框未就绪，请点输入框后重试")
            }
        }
        awaitConnection()
    }
    companion object { private val worker=Executors.newSingleThreadExecutor { r -> Thread(r,"wechat-official-input") } }
}
