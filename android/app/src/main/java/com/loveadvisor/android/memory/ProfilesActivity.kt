package com.loveadvisor.android.memory
import android.os.Bundle
import android.app.AlertDialog
import android.widget.*
import com.loveadvisor.android.ui.*
import com.loveadvisor.core.memory.RelationshipProfile
import java.util.UUID
class ProfilesActivity:BaseActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); render() }
    private fun render() {
        page("关系资料","每个对象使用独立编号；读取聊天时由你确认关联。")
        body.addView(ViewStyles.button(this,"新增对象",true) { edit(null) })
        body.addView(ViewStyles.button(this,"记忆与聊天历史") { open(MemoryActivity::class.java) })
        val data=graph.profiles.load()
        for(profile in listOf(data.self)+data.objects) {
            val card=ViewStyles.card(this); card.addView(ViewStyles.text(this,if(profile.id=="self") "本人资料" else profile.nickname,20f,bold=true)); card.addView(ViewStyles.text(this,"${profile.stage} · ${profile.goal.ifBlank { "未填写目标" }}",14f,ViewStyles.MUTED)); card.addView(ViewStyles.button(this,"查看与编辑") { edit(profile) }); body.addView(card)
        }
    }
    private fun edit(existing:RelationshipProfile?) {
        val panel=ViewStyles.column(this,16); val scroll=ScrollView(this); scroll.addView(panel)
        val nick=ViewStyles.field(this,"昵称",existing?.nickname.orEmpty()); val stage=ViewStyles.field(this,"关系阶段",existing?.stage.orEmpty()); val details=ViewStyles.field(this,"已知事实与背景",existing?.details.orEmpty(),true); val goal=ViewStyles.field(this,"你的目标",existing?.goal.orEmpty()); val tone=ViewStyles.field(this,"希望的口吻",existing?.tone.orEmpty()); val boundaries=ViewStyles.field(this,"你的边界",existing?.boundaries.orEmpty()); val mbti=ViewStyles.field(this,"MBTI（可空，只作描述）",existing?.mbti.orEmpty()); val score=ViewStyles.field(this,"你的主观评分 0–100（可空）",existing?.subjectiveScore?.toString().orEmpty()).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER }
        listOf(nick,stage,details,goal,tone,boundaries,mbti,score).forEach { panel.addView(it) }
        val dialog=AlertDialog.Builder(this).setTitle(if(existing==null) "新增对象" else "编辑资料").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val rating=score.text.toString().toIntOrNull()
            if(nick.text.isBlank() || details.text.length>6000 || (score.text.isNotBlank() && rating !in 0..100)) { toast("请填写昵称；背景最多 6000 字，主观评分为 0–100"); return@setOnClickListener }
            val profile=RelationshipProfile(existing?.id ?: UUID.randomUUID().toString(),nick.text.toString().take(80),details.text.toString(),mbti.text.toString().ifBlank { null },rating,goal.text.toString().take(1000),tone.text.toString().take(500),boundaries.text.toString().take(1000),stage.text.toString().ifBlank { "未知" }.take(100))
            graph.profiles.put(profile); graph.controller.invalidate("资料变化"); dialog.dismiss(); render()
        } }; dialog.show()
        if(existing!=null && existing.id!="self") {
            panel.addView(ViewStyles.button(this,"删除对象及关联记忆、历史") { confirm("删除对象","将删除 ${existing.nickname} 的资料、记忆和聊天历史。") { graph.controller.invalidate("对象删除"); if(graph.currentObjectId==existing.id) graph.currentObjectId=null; graph.profiles.delete(existing.id); graph.memory.forgetObject(existing.id); dialog.dismiss(); render() } })
        }
    }
}
