package com.loveadvisor.android.capture
import android.os.Bundle
import android.content.Intent
import android.widget.*
import com.loveadvisor.android.ui.*
import com.loveadvisor.android.advisor.ConsultationActivity
import com.loveadvisor.core.capture.*
import com.loveadvisor.core.model.*
import java.util.UUID
class CapturePreviewActivity:BaseActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); val draft=graph.pendingDraft ?: run { toast("没有待确认内容，请重新读取"); finish(); return }
        page("检查识别","确认当前会话、我／对方和群成员；可删掉误识别文字。只分析下方确认的内容。")
        val title=ViewStyles.field(this,"当前聊天名称",draft.title.orEmpty()); body.addView(title)
        val kind=Spinner(this); kind.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("单聊","群聊")); kind.setSelection(if(draft.kind==ChatKind.GROUP) 1 else 0); body.addView(kind)
        val profiles=graph.profiles.load().objects; val target=Spinner(this); target.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("不关联对象资料")+profiles.map { it.nickname }); body.addView(target)
        body.addView(ViewStyles.text(this,"会话昵称不能证明联系人身份，档案由你明确选择。",13f,ViewStyles.MUTED))
        val goal=ViewStyles.field(this,"这次希望达到什么目标？",graph.currentGoal); body.addView(goal)
        if(draft.messages.size>80) body.addView(ViewStyles.text(this,"仅预览最近 80 条，请分段分析更早的内容。",13f,ViewStyles.MUTED))
        data class Row(val keep:CheckBox,val speaker:Spinner,val sender:EditText,val text:EditText)
        val rows=mutableListOf<Row>()
        for(message in draft.messages.takeLast(80)) {
            val card=ViewStyles.card(this); val keep=CheckBox(this).apply { text="保留这一条"; isChecked=true }; val speaker=Spinner(this).apply { adapter=ArrayAdapter(this@CapturePreviewActivity,android.R.layout.simple_spinner_dropdown_item,listOf("请确认说话方","我","对方")); setSelection(when(message.speaker) { Speaker.ME -> 1; Speaker.OTHER -> 2; Speaker.UNKNOWN -> 0 }) }
            val sender=ViewStyles.field(this,"群成员昵称（可空）",message.sender.orEmpty()); val text=ViewStyles.field(this,"消息内容",message.text,true); card.addView(keep); card.addView(speaker); card.addView(sender); card.addView(text); body.addView(card); rows.add(Row(keep,speaker,sender,text))
        }
        body.addView(ViewStyles.button(this,"确认并分析六条方案",true) {
            val selected=rows.filter { it.keep.isChecked && it.text.text.isNotBlank() }
            if(title.text.isBlank() || selected.isEmpty() || selected.any { it.speaker.selectedItemPosition==0 }) { toast("请填写会话名称、保留消息，并确认每条说话方"); return@button }
            val identity=ConversationIdentity(UUID.randomUUID().toString(),title.text.toString(),if(kind.selectedItemPosition==1) ChatKind.GROUP else ChatKind.DIRECT,true)
            val snapshot=ChatSnapshot(draft.packageName,draft.windowId,identity,selected.mapIndexed { i,row -> ChatMessage("confirmed-$i",if(row.speaker.selectedItemPosition==1) Speaker.ME else Speaker.OTHER,row.sender.text.toString().ifBlank { null },row.text.text.toString().take(6000)) },source=draft.source)
            graph.currentObjectId=if(target.selectedItemPosition>0) profiles[target.selectedItemPosition-1].id else null; graph.currentGoal=goal.text.toString(); graph.pendingDraft=null
            val service=ChatCaptureService.instance
            val edited=snapshot.messages.map { Triple(it.speaker,it.sender,it.text) }!=draft.messages.map { Triple(it.speaker,it.sender,it.text) } || (draft.source==CaptureSource.NODES && (draft.title==null || title.text.toString()!=draft.title))
            if(draft.packageName in CapturePolicy.supportedPackages && service!=null && !edited && draft.source==CaptureSource.NODES) { service.confirmDraft(snapshot); packageManager.getLaunchIntentForPackage(draft.packageName)?.let { startActivity(it) }; toast("返回聊天后开始分析，旧窗口变化会要求重新读取"); finish() }
            else { val imported=PreviewBindingPolicy.independent(snapshot,packageName); graph.tracker.update(imported); graph.controller.analyze(imported); if(draft.source==CaptureSource.OCR) toast("分析已确认的截图内容；仅可复制，填入需重新读取并确认当前聊天节点"); startActivity(Intent(this,ConsultationActivity::class.java).putExtra("showResult",true)); finish() }
        })
    }
}
