package com.loveadvisor.android.debug

import android.content.*
import android.graphics.Rect
import android.os.*
import android.view.*
import com.google.gson.Gson
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.capture.ocr.*
import com.loveadvisor.android.input.*
import com.loveadvisor.core.api.Cancellation
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.capture.AutomaticConversation
import com.loveadvisor.core.model.*
import java.io.File
import java.util.concurrent.Executors

/** USB-only debug probe, protected by the shell's DUMP permission. No model requests or sending. */
class DeviceProbeReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val pending=goAsync()
        val s=ChatCaptureService.instance
        fun report(data:Map<String,Any?>) { File(context.filesDir,"device-probe.json").writeText(Gson().toJson(data));pending.finish() }
        if(s==null) { report(mapOf("connected" to false));return }
        if(Build.VERSION.SDK_INT<33) { report(mapOf("inputApi" to false));return }
        val mode=intent.getStringExtra("mode") ?: "inspect"
        fun field(name:String)=s.javaClass.getDeclaredField(name).apply { isAccessible=true }
        val main=Handler(Looper.getMainLooper())
        if(mode=="fill" || mode=="fixture") {
            worker.execute {
                val before=runCatching { s.inputMethod?.currentInputConnection?.getSurroundingText(4096,4096,0)?.text?.toString() }.getOrNull()
                val window=s.reader.foreground()
                main.post {
                    if(before!="" || window?.packageName!="com.tencent.mm") { report(mapOf("safe" to false,"draftEmpty" to (before=="")));return@post }
                    val cancellation=Cancellation()
                    WindowCapture(s).capture(window.windowId,cancellation) { image ->
                        if(image !is CaptureImageResult.Success) { report(mapOf("capture" to false));return@capture }
                        val stamp=VisualFillReference.stamp(image.bitmap)
                        val footer=VisualFillReference.footerStamp(image.bitmap);image.bitmap.recycle()
                        if(stamp==null) { report(mapOf("capture" to false));return@capture }
                        // Fixture seeds only manual-read state; the actual production fill transaction is exercised.
                        val id=ConversationIdentity("usb-fill-fixture","文件传输助手",ChatKind.DIRECT,true)
                        val snapshot=ChatSnapshot(window.packageName,window.windowId,id,listOf(ChatMessage("fixture",Speaker.OTHER,null,"USB草稿测试上下文")),source=CaptureSource.OCR)
                        field("identity").set(s,id)
                        (field("conversation").get(s) as AutomaticConversation).restore(snapshot)
                        field("visualFill").set(s,VisualFillReference(window.windowId,s.captureGeneration(),stamp,null,footer,window.viewport))
                        // The native tree is empty on the target phone; the native route must fall back to the IME.
                        field("visualSignature").set(s,"")
                        s.graph.controller.observe(snapshot)
                        if(mode=="fixture") {
                            val request=s.graph.coordinator.begin(snapshot)!!
                            val strategies=listOf(Strategy.ACKNOWLEDGE,Strategy.HUMOR,Strategy.APPRECIATION,Strategy.TEASING,Strategy.INVITATION,Strategy.CALM)
                            val replies=strategies.mapIndexed { index,strategy -> ReplyPlan("usb-$index",strategy,if(index==0)MARKER else "USB界面测试候选${index+1}","仅供本地界面验证","USB调试","不发送",ReplyBranches("保持草稿","保持草稿","保持草稿"),.9-index*.05) }
                            val batch=AdvisorBatch(AdvisorAnalysis(emptyList(),emptyList(),emptyList(),"USB本地界面测试",1,"只写入草稿，完成后清除"),replies,compact=true)
                            val accepted=s.graph.coordinator.complete(request.requestTicket,batch)
                            s.graph.controller.publish()
                            val overlay=field("overlay").get(s);overlay.javaClass.getMethod("show").invoke(overlay)
                            report(mapOf("fixture" to accepted,"sent" to false));return@capture
                        }
                        val token=s.graph.tracker.currentToken!!
                        ReplyFiller(s).fill(token,MARKER) { success,message -> report(mapOf("productionFill" to success,"message" to message,"fixture" to true,"sent" to false)) }
                    }
                }
            };return
        }
        val method=if(Build.VERSION.SDK_INT>=33)s.inputMethod else null
        val editor=method?.currentInputEditorInfo
        val connection=method?.currentInputConnection
        worker.execute {
            val draft=runCatching { connection?.getSurroundingText(4096,4096,0) }.getOrNull()
            if(mode=="clear" && draft?.text?.toString()==MARKER && editor?.packageName=="com.tencent.mm") {
                main.post {
                    val token=s.graph.tracker.currentToken
                    if(token?.conversationId!="usb-fill-fixture") { report(mapOf("cleared" to false,"reason" to "fixture no longer current"));return@post }
                    ReplyFiller(s).fill(token,"",{ actual,confirm,cancel -> if(actual==MARKER)confirm() else cancel() }) { success,message ->
                        if(success) { (field("conversation").get(s) as AutomaticConversation).clear();field("identity").set(s,null);field("visualFill").set(s,null);s.graph.controller.invalidate("USB测试已结束") }
                        report(mapOf("cleared" to success,"message" to message,"sent" to false))
                    }
                };return@execute
            }
            main.post {
                val overlay=field("overlay").get(s)
                fun viewBounds(name:String):List<Int>? {
                    val f=overlay.javaClass.getDeclaredField(name).apply { isAccessible=true }
                    val v=f.get(overlay) as? View ?: return null
                    val p=IntArray(2);v.getLocationOnScreen(p);return listOf(p[0],p[1],p[0]+v.width,p[1]+v.height)
                }
                val bubbleField=overlay.javaClass.getDeclaredField("bubble").apply { isAccessible=true }
                val bubble=bubbleField.get(overlay) as? View
                report(mapOf("connected" to true,"inputPackage" to editor?.packageName,"inputType" to editor?.inputType,"imeOptions" to editor?.imeOptions,"inputConnection" to (connection!=null),"surrounding" to (draft!=null),"draftLength" to draft?.text?.length,"editorGeneration" to s.editorGeneration(),"tokenValid" to (s.graph.tracker.currentToken!=null),"generation" to s.captureGeneration(),"readStatus" to s.lastWechatDiagnosticStatus,"fillStatus" to s.lastFillDiagnosticStatus,"bubble" to viewBounds("bubble"),"panel" to viewBounds("panel"),"imeInset" to bubble?.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom,"imeWindows" to s.windows.filter { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }.map { val r=Rect();it.getBoundsInScreen(r);listOf(r.left,r.top,r.right,r.bottom) }))
            }
        }
    }
    companion object { private val worker=Executors.newSingleThreadExecutor();private const val MARKER="军师填入测试（未发送）" }
}
