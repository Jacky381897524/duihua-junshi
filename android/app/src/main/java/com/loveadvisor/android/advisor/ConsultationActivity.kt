package com.loveadvisor.android.advisor
import android.os.Bundle
import android.content.Intent
import android.widget.*
import com.loveadvisor.android.ui.*
import com.loveadvisor.android.overlay.ReplyCardView
import com.loveadvisor.android.input.ClipboardController
import com.loveadvisor.android.knowledge.KnowledgeActivity
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.model.*
import java.util.UUID
class ConsultationActivity:BaseActivity() {
    private lateinit var result:LinearLayout; private lateinit var status:TextView
    private var analyzed:ConsultationRequest?=null
    private val listener:(BatchState)->Unit={ state -> if(::result.isInitialized) render(state) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); page("完整咨询","结合对话、你的目标与资料，选择不同策略的六个方案。")
        val text=ViewStyles.field(this,"聊天原文，例如：\n我：你好\n对方：今天有点累",multiline=true); val goal=ViewStyles.field(this,"目标：自然接话、邀约、澄清、修复…",graph.currentGoal); val background=ViewStyles.field(this,"补充背景（可空）",multiline=true); val draft=ViewStyles.field(this,"已有回复草稿（可空，填写后按润色处理）",multiline=true)
        val objects=graph.profiles.load().objects; val target=Spinner(this); target.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("不关联对象资料")+objects.map { it.nickname })
        body.addView(text); body.addView(goal); body.addView(background); body.addView(draft); body.addView(target)
        body.addView(ViewStyles.button(this,"分析 / 润色为六条",true) {
            try {
                val imported=ImportParser().parseUtf8(text.text.toString())
                if(!imported.speakerMappingConfirmed) { toast("请用“我：”“对方：”标明说话方，或通过导入页面确认"); return@button }
                val identity=ConversationIdentity(UUID.randomUUID().toString(),"完整咨询",imported.kind,true)
                val snapshot=ChatSnapshot(packageName,-1,identity,imported.messages,source=CaptureSource.PASTE)
                val selected=if(target.selectedItemPosition>0) objects[target.selectedItemPosition-1].id else null
                val request=ConsultationRequest(snapshot,goal.text.toString(),background.text.toString(),draft.text.toString(),selected); analyzed=request
                graph.currentObjectId=selected; graph.currentGoal=request.goal; graph.controller.invalidate(); graph.controller.analyze(snapshot,request.goal,request.combinedBackground(),selected)
            } catch(e:Exception) { toast(graph.safeError(e)) }
        })
        body.addView(ViewStyles.button(this,"刷新换一批（沿用上次目标与草稿）") { if(!graph.controller.refreshIndependent()) toast("请先分析这段对话") })
        status=ViewStyles.text(this,"",14f,ViewStyles.MUTED); result=ViewStyles.column(this); body.addView(status); body.addView(result); graph.controller.listen(listener)
    }
    private fun render(state:BatchState) {
        status.text=state.error ?: if(state.loading) "正在生成六条候选，已有结果会保留…" else ""
        val batch=state.current?.batch ?: return; result.removeAllViews()
        val a=batch.analysis; result.addView(ViewStyles.text(this,"${a.intent}\n下一步：${a.action}",20f,bold=true)); result.addView(ViewStyles.text(this,"事实：${a.facts.joinToString("；")}\n推测：${a.inferences.joinToString("；")}\n未知：${a.unknowns.joinToString("；")}",15f))
        batch.replies.forEachIndexed { i,plan -> result.addView(ReplyCardView(this,i,plan,{ ClipboardController.copy(this,plan.text) },null)) }
        if(batch.knowledgeIds.isNotEmpty()) {
            result.addView(ViewStyles.text(this,"本次命中的知识",18f,bold=true))
            for(id in batch.knowledgeIds) { val doc=(graph.knowledge.documents+graph.profiles.load().notes).find { it.id==id } ?: continue; result.addView(ViewStyles.button(this,doc.title) { startActivity(Intent(this,KnowledgeActivity::class.java).putExtra("id",id)) }) }
        }
    }
    override fun onDestroy() { graph.controller.unlisten(listener); super.onDestroy() }
}
