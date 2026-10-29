package com.loveadvisor.android.memory
import android.os.Bundle
import android.app.AlertDialog
import android.widget.*
import com.loveadvisor.android.ui.*
import com.loveadvisor.core.memory.*
import java.util.UUID
class MemoryActivity:BaseActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); render() }
    private fun render() {
        page("手工记忆与历史","此处保留旧版手工资料。V1.6按对象自动整理、口吻学习和互动趋势请进入真实与记忆。")
        val state=graph.memory.state(); val data=graph.profiles.load()
        body.addView(ViewStyles.button(this,"进入真实与记忆") { open(PersonalMemoryActivity::class.java) })
        body.addView(ViewStyles.switch(this,"保存关联对象的聊天历史",graph.settings.load().historyEnabled) { graph.settings.update { d -> d.copy(historyEnabled=it) } })
        body.addView(ViewStyles.text(this,"${state.items.size}/200 条记忆 · ${data.history.size} 段历史；每对象最多 20 个事件、5 个假设。",13f,ViewStyles.MUTED))
        body.addView(ViewStyles.button(this,"手动添加记忆") { edit(null) })
        state.undo.lastOrNull()?.let { change -> body.addView(ViewStyles.button(this,"撤销最近一次：${change.summary}") { if(graph.memory.undo(change.id)) render() }) }
        body.addView(ViewStyles.button(this,"清空全部记忆") { confirm("清空记忆","清空所有对象的记忆与撤销记录？") { graph.controller.invalidate(); graph.memory.clearAll(); render() } })
        body.addView(ViewStyles.button(this,"删除全部聊天历史") { confirm("删除历史","删除已保存的聊天历史？") { graph.profiles.save(graph.profiles.load().copy(history=emptyList())); render() } })
        for(profile in data.objects) {
            val card=ViewStyles.card(this); card.addView(ViewStyles.text(this,profile.nickname,20f,bold=true))
            for(item in state.items.filter { it.objectId==profile.id }) {
                card.addView(ViewStyles.text(this,"${when(item.kind) { MemoryKind.FACT -> "事实"; MemoryKind.EVENT -> "事件"; MemoryKind.HYPOTHESIS -> "暂定假设" }} · ${item.text}",15f))
                card.addView(ViewStyles.button(this,"编辑这一条") { edit(item) })
            }
            card.addView(ViewStyles.button(this,"查看已保存历史") {
                val text=data.history.filter { it.objectId==profile.id }.joinToString("\n\n") { saved -> saved.messages.joinToString("\n") { "${it.sender ?: it.speaker.name}：${it.text}" } }.ifBlank { "没有已保存历史" }
                val scroll=ScrollView(this); scroll.addView(ViewStyles.text(this,text,15f)); AlertDialog.Builder(this).setTitle(profile.nickname).setView(scroll).setPositiveButton("关闭",null).show()
            })
            card.addView(ViewStyles.button(this,"清除此对象记忆") { confirm("清除记忆","清除 ${profile.nickname} 的记忆？") { graph.controller.invalidate(); graph.memory.forgetObject(profile.id); render() } }); body.addView(card)
        }
    }
    private fun edit(item:MemoryItem?) {
        val objects=graph.profiles.load().objects; if(objects.isEmpty()) { toast("请先新增对象资料"); return }
        val panel=ViewStyles.column(this,16); val target=Spinner(this); target.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,objects.map { it.nickname }); target.setSelection(objects.indexOfFirst { it.id==item?.objectId }.coerceAtLeast(0)); if(item!=null) target.isEnabled=false
        val kind=Spinner(this); kind.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("事实（你已确认）","事件（你已确认）","暂定假设")); kind.setSelection(item?.kind?.ordinal ?: 0)
        val text=ViewStyles.field(this,"每条最多 200 字",item?.text.orEmpty(),true); panel.addView(target); panel.addView(kind); panel.addView(text)
        val dialog=AlertDialog.Builder(this).setTitle("手动记忆").setView(panel).setNegativeButton("取消",null).setPositiveButton("保存",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val objectId=objects[target.selectedItemPosition].id; val selectedKind=MemoryKind.entries[kind.selectedItemPosition]
            val proposed=MemoryItem(item?.id ?: UUID.randomUUID().toString(),objectId,selectedKind,text.text.toString(),selectedKind==MemoryKind.HYPOTHESIS)
            val update=MemoryUpdate(objectId,listOf(proposed),false); val valid=MemoryPolicy().validate(graph.memory.state(),update)
            if(!valid.accepted) toast(valid.message.orEmpty()) else { graph.memory.apply(update); graph.controller.invalidate(); dialog.dismiss(); render() }
        } }; dialog.show()
    }
}
