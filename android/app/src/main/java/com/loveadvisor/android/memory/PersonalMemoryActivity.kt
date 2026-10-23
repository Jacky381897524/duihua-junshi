package com.loveadvisor.android.memory

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import com.loveadvisor.android.ui.*
import com.loveadvisor.android.diagnostics.DiagnosticLogs as Logs
import com.loveadvisor.core.capture.ChatApps
import com.loveadvisor.core.diagnostics.*
import com.loveadvisor.core.memory.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** All profile evidence can be inspected and corrected; a generated sentence is never treated as a sent message. */
class PersonalMemoryActivity:BaseActivity() {
    private val repository get()=graph.personalMemory.repository
    private var selected:String?=null
    private var limit=40
    private var importField:EditText?=null
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);reload() }
    private fun reload() {
        page("真人感与记忆","正在读取本地资料…")
        work("memory",{ repository.state() },{ state -> if(selected==null)overview(state) else details(state,selected!!) })
    }
    private fun date(at:Long?)=if(at==null || at<=0)"时间未知" else SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(Date(at))
    private fun title(contact:PersonalContact)=contact.nickname+(if(contact.groupRoom)" · 群聊" else "")+contact.group?.takeUnless { contact.groupRoom }?.let { " · 群：$it" }.orEmpty()+" · "+ChatApps.label(contact.platform)
    private fun perform(step:LogStep=LogStep.MEMORY_EDIT,action:()->Unit) {
        graph.personalMemory.cancelLearning()
        work("memory",{ action();Logs.event(step,LogState.OK) },{ reload() })
    }
    private fun overview(s:RealityMemoryState) {
        page("真人感与记忆","保存在本机的加密持久资料，清除缓存会保留。只有手动读取、导入及确认修正才提供学习材料。")
        body.addView(ReplyRealitySwitch(this,graph.settings))
        body.addView(ViewStyles.switch(this,"学习新读到的聊天",s.learningEnabled) { enabled -> perform { repository.configure(enabled,repository.state().paused) } })
        body.addView(ViewStyles.switch(this,"暂停学习（仍可使用已有记忆）",s.paused) { paused -> perform { repository.configure(repository.state().learningEnabled,paused) } })
        body.addView(ViewStyles.text(this,"${s.data.contacts.size}个对象/群成员 · ${s.data.evidence.size}条去重原话 · ${s.data.memories.size}条资料\n${graph.personalMemory.status}",13f,ViewStyles.MUTED))
        body.addView(ViewStyles.text(this,"本地先整理原话。候选完成后，用所选回复模型对新材料做一次后台分类，可能产生少量用量；刷新不重复学习。资料更充分能改善依据，并不改变模型权重。",13f,ViewStyles.MUTED))
        body.addView(ViewStyles.button(this,"本人表达、习惯与修正") { selected=PersonalIdentity.SELF;limit=40;reload() })
        body.addView(ViewStyles.button(this,"导入聊天记录") { importDialog() })
        body.addView(ViewStyles.button(this,"重试未完成的后台分类") { graph.personalMemory.retry();toast("已安排后台分类，生成候选时会让出") })
        if(s.undo!=null)body.addView(ViewStyles.button(this,"撤销最近一次资料变更") { perform { repository.undo() } })
        s.data.contacts.forEach { contact ->
            body.addView(ViewStyles.button(this,title(contact)+"\n关系："+relation(contact,s)) { selected=contact.id;limit=40;reload() })
        }
        body.addView(ViewStyles.button(this,"清空真人感的全部资料") {
            confirm("清空真人感资料","删除原话、记忆、修正、关系和互动记录，也清除撤销副本。已删除的原话不会因再次读取自动恢复；此操作不可撤销。") { perform(LogStep.MEMORY_DELETE) { repository.clear() } }
        })
        body.addView(ViewStyles.text(this,"按平台与昵称匹配，群成员按所在群隔离。同名者可能混淆，请检查来源并手动关联或纠正；不会自动按相似昵称合并。",12f,ViewStyles.MUTED))
    }
    private fun relation(c:PersonalContact,s:RealityMemoryState):String {
        if(c.relationshipConfirmed)return c.relationship+"（已确认）"
        val item=s.data.memories.lastOrNull { it.ownerId==c.id && it.kind==PersonalKind.RELATIONSHIP }
        return item?.let { PersonalRelations.context(c,s.data.memories).relationship+" · ${it.text.take(35)}" } ?: "未知"
    }
    private fun details(s:RealityMemoryState,id:String) {
        val self=id==PersonalIdentity.SELF
        val contact=s.data.contacts.find { it.id==id }
        if(!self && contact==null) { selected=null;overview(s);return }
        page(if(self)"本人表达与记忆" else contact!!.nickname,if(self)"从你实际说过的话与确认的修正中提炼口吻，按对象保留使用场景。" else title(contact!!))
        body.addView(ViewStyles.button(this,"返回全部对象") { selected=null;reload() })
        if(!self) {
            body.addView(ViewStyles.text(this,"关系：${relation(contact!!,s)}",16f,bold=true))
            body.addView(ViewStyles.button(this,"确认或更正关系") { textDialog("与你的实际关系",contact.relationship.takeUnless { it=="未知" }.orEmpty(),"例如：同事、朋友、家人、恋人；以你确认为准") { label -> perform { repository.relationship(id,label) } } })
            val signals=s.data.signals.filter { it.contactId==id }
            val estimate=if(signals.isEmpty())"暂不足以估计" else "${"%.1f".format(Locale.ROOT,InteractionTrend.score(signals))}/100 · ${signals.size}条互动依据 · 最近${date(signals.maxOfOrNull { it.at })}"
            body.addView(ViewStyles.text(this,"好感度（互动趋势估计）：$estimate\n仅根据明确互动变化；不是对方真实内心。重复证据不计分，同类互动递减，每日正向增幅最多3。",13f,ViewStyles.MUTED))
            if(signals.isNotEmpty())body.addView(ViewStyles.button(this,"查看 / 纠正好感变化依据") {
                val rows=signals.takeLast(50).asReversed()
                AlertDialog.Builder(this).setTitle("互动依据 · 点击可纠正").setItems(rows.map { signal -> "${date(signal.at)} · ${"%+.2f".format(Locale.ROOT,signal.delta)} · ${s.data.evidence.find { it.id==signal.sourceId }?.text?.take(35).orEmpty()}" }.toTypedArray()) { _,i ->
                    val signal=rows[i]
                    AlertDialog.Builder(this).setTitle("本条互动估计").setMessage(s.data.evidence.find { it.id==signal.sourceId }?.text ?: "来源已删除").setNegativeButton("保留",null).setPositiveButton("忽略本条判断") { _,_->perform { repository.dismissSignal(signal.sourceId) } }.show()
                }.setNegativeButton("关闭",null).show()
            })
            body.addView(ViewStyles.button(this,"关联误识别昵称 / 已有对象") { bindDialog(contact,s) })
            body.addView(ViewStyles.button(this,"记录我对候选的实际修改") { correctionDialog(id) })
        }
        body.addView(ViewStyles.button(this,"添加已确认资料") { add(id) })
        val items=s.data.memories.filter { it.ownerId==id }
        body.addView(ViewStyles.text(this,"已整理资料 · ${items.size}条",18f,bold=true))
        items.sortedByDescending { it.updatedAt }.take(limit).forEach { item ->
            val card=ViewStyles.card(this)
            val source=s.data.evidence.find { it.id==item.sourceId }
            card.addView(ViewStyles.text(this,"${kind(item.kind)} · ${when(item.status) { EvidenceStatus.CONFIRMED->"你已确认";EvidenceStatus.TENTATIVE->"待确认观察";else->"原话陈述" }}",12f,ViewStyles.MUTED))
            card.addView(ViewStyles.text(this,item.text,15f))
            card.addView(ViewStyles.text(this,source?.let { "原文时间：${it.time?.label ?: "未显示"} · ${it.time?.epochMillis?.let(::date) ?: "时间未确定"}\n首次读取：${date(it.readAt)}" } ?: "手动资料 · ${date(item.updatedAt)}",11f,ViewStyles.MUTED))
            card.addView(ViewStyles.button(this,"查看来源 / 编辑") {
                val panel=ViewStyles.column(this,14)
                panel.addView(ViewStyles.text(this,"原始证据：${source?.text ?: "手动添加"}",13f,ViewStyles.MUTED))
                val field=ViewStyles.field(this,"确认后记为你的修正",item.text,true);panel.addView(field)
                AlertDialog.Builder(this).setTitle("${kind(item.kind)} · 纠正资料").setView(panel).setNegativeButton("取消",null).setPositiveButton("确认保存") { _,_->perform { repository.edit(item,field.text.toString()) } }.setNeutralButton("删除") { _,_->perform(LogStep.MEMORY_DELETE) { repository.deleteMemory(item.id) } }.show()
            });body.addView(card)
        }
        if(items.size>limit)body.addView(ViewStyles.button(this,"再显示40条资料") { limit+=40;details(s,id) })
        val evidence=s.data.evidence.filter { it.ownerId==id }
        body.addView(ViewStyles.button(this,"查看已读取原话 · ${evidence.size}条") { evidenceDialog(evidence,0) })
        if(self && s.data.corrections.isNotEmpty())body.addView(ViewStyles.button(this,"查看我确认的候选修改") { showText("实际修改",s.data.corrections.takeLast(50).joinToString("\n\n") { "${date(it.at)}\n原候选：${it.original}\n我改成：${it.actual}" }) })
        body.addView(ViewStyles.button(this,"清除此处记忆与原话") { confirm("删除资料","删除该对象（本人页则为本人）的已读原话、资料、相关互动与修正。保留昵称入口；可以撤销。") { perform(LogStep.MEMORY_DELETE) { repository.forget(id) } } })
    }
    private fun kind(value:PersonalKind)=when(value) { PersonalKind.LIKE->"喜好";PersonalKind.DISLIKE->"不喜欢";PersonalKind.HABIT->"习惯";PersonalKind.TRAIT->"性格观察";PersonalKind.BOUNDARY->"边界";PersonalKind.RELATIONSHIP->"关系依据";PersonalKind.EVENT->"事件";PersonalKind.FACT->"事实陈述";PersonalKind.VOICE->"口吻" }
    private fun evidenceDialog(rows:List<PersonalEvidence>,offset:Int) {
        val scroll=ScrollView(this);scroll.addView(ViewStyles.text(this,rows.asReversed().drop(offset).take(30).joinToString("\n\n") { "${it.time?.label ?: "消息时间未显示"} · 读取${date(it.readAt)}\n${it.text}" }.ifBlank { "暂无原话" },14f))
        val dialog=AlertDialog.Builder(this).setTitle("已读原话 ${offset+1}–${minOf(offset+30,rows.size)}").setView(scroll).setNegativeButton("关闭",null)
        if(offset+30<rows.size)dialog.setPositiveButton("更早30条") { _,_->evidenceDialog(rows,offset+30) }
        dialog.show()
    }
    private fun showText(title:String,text:String) { val scroll=ScrollView(this);scroll.addView(ViewStyles.text(this,text,14f));AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("关闭",null).show() }
    private fun textDialog(title:String,initial:String,hint:String,save:(String)->Unit) {
        val field=ViewStyles.field(this,hint,initial,true)
        AlertDialog.Builder(this).setTitle(title).setView(field).setNegativeButton("取消",null).setPositiveButton("保存") { _,_->save(field.text.toString()) }.show()
    }
    private fun add(id:String) {
        val panel=ViewStyles.column(this,14);val kinds=PersonalKind.entries.filter { it!=PersonalKind.VOICE }
        val spinner=Spinner(this).apply { adapter=ArrayAdapter(this@PersonalMemoryActivity,android.R.layout.simple_spinner_dropdown_item,kinds.map(::kind)) }
        val text=ViewStyles.field(this,"你确认的资料，最多300字",multiline=true);panel.addView(spinner);panel.addView(text)
        AlertDialog.Builder(this).setTitle("添加资料").setView(panel).setNegativeButton("取消",null).setPositiveButton("保存") { _,_->perform { repository.edit(PersonalMemory(UUID.randomUUID().toString(),id,kinds[spinner.selectedItemPosition],"",null),text.text.toString()) } }.show()
    }
    private fun bindDialog(contact:PersonalContact,s:RealityMemoryState) {
        val choices=s.data.contacts.filter { it.id!=contact.id && it.platform==contact.platform && it.group==contact.group && it.groupRoom==contact.groupRoom && !it.groupRoom }
        if(choices.isEmpty()) { toast("暂无同平台和群范围的其他对象。先读取正确昵称的对话，再关联；也可手动添加资料。");return }
        AlertDialog.Builder(this).setTitle("确认是同一个人后关联").setItems(choices.map(::title).toTypedArray()) { _,i ->
            confirm("关联对象","将 ${contact.nickname} 的资料关联至 ${choices[i].nickname}。同名不是身份证明，请先核对；可以撤销。") { perform { repository.bind(contact.id,choices[i].id) } }
        }.setNegativeButton("取消",null).show()
    }
    private fun correctionDialog(id:String) {
        val panel=ViewStyles.column(this,14);val original=ViewStyles.field(this,"原候选（可空）",multiline=true);val actual=ViewStyles.field(this,"你实际选择的说法",multiline=true)
        panel.addView(original);panel.addView(actual)
        AlertDialog.Builder(this).setTitle("学习你自己的修改").setView(panel).setNegativeButton("取消",null).setPositiveButton("确认记录") { _,_->perform { repository.correction(id,original.text.toString(),actual.text.toString()) } }.show()
    }
    private fun importDialog() {
        val panel=ViewStyles.column(this,14);val platforms=ChatApps.packages.toList()
        val platform=Spinner(this).apply { adapter=ArrayAdapter(this@PersonalMemoryActivity,android.R.layout.simple_spinner_dropdown_item,platforms.map(ChatApps::label)) }
        val name=ViewStyles.field(this,"准确的对象昵称 / 群名")
        val group=CheckBox(this).apply { text="这是群聊（每行使用具体成员昵称）" }
        val text=ViewStyles.field(this,"时间单独一行，例如2026年10月24日 18:30\n我：今天怎么样\n对方：我喜欢咖啡",multiline=true);text.minLines=5
        panel.addView(platform);panel.addView(name);panel.addView(group);panel.addView(text)
        panel.addView(ViewStyles.text(this,"仅导入你有权使用的记录。内容留在本机；真人感开启时会将选中的新原文发给已配置模型分类。昵称、平台和说话方确认后保存。",12f,ViewStyles.MUTED))
        panel.addView(ViewStyles.button(this,"选择UTF-8文本文件") { importField=text;startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE),81) })
        val scroll=ScrollView(this);scroll.addView(panel)
        val dialog=AlertDialog.Builder(this).setTitle("导入并学习聊天").setView(scroll).setNegativeButton("取消",null).setPositiveButton("确认导入",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value=text.text.toString();val nickname=name.text.toString();val pkg=platforms[platform.selectedItemPosition];val isGroup=group.isChecked
            work("memory",{ val snapshot=PersonalImport.parse(value,pkg,nickname,isGroup);val result=repository.ingest(snapshot);if(result.added>0 && graph.settings.load().realisticRepliesEnabled)graph.personalMemory.retry();result },{ receipt -> dialog.dismiss();toast("已保存${receipt.added}条新原话，重复内容已跳过");reload() })
        } };dialog.setOnDismissListener { importField=null };dialog.show()
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        val uri=data?.data ?: return
        if(requestCode!=81 || resultCode!=RESULT_OK)return
        val target=importField ?: return
        work("memory",{ cancel ->
            val bytes=contentResolver.openInputStream(uri)?.use { stream -> val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true) { cancel.check();val n=stream.read(buffer);if(n<0)break;require(out.size()+n<=1024*1024) { "单次最多1MiB" };out.write(buffer,0,n) };out.toByteArray() } ?: error("无法读取文件")
            Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        },{ if(importField===target)target.setText(it) })
    }
}
