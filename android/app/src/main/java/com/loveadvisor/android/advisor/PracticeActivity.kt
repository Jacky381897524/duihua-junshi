package com.loveadvisor.android.advisor
import android.os.Bundle
import com.loveadvisor.android.ui.*
import com.loveadvisor.core.advisor.*
class PracticeActivity:BaseActivity() {
    private val session=PracticeSession()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); page("逐轮练习","这是模拟对话。每轮只演对方一句，等你作答后继续。")
        val scenario=ViewStyles.field(this,"练习情境与对方特点",multiline=true); val answer=ViewStyles.field(this,"你这一轮怎么说？",multiline=true); val turns=ViewStyles.column(this)
        body.addView(scenario); body.addView(ViewStyles.button(this,"开始新练习") { try { session.start(PracticeEngine(graph.chat,graph.settings.load().profile).start(scenario.text.toString())); answer.isEnabled=true; answer.setText(""); turns.removeAllViews(); toast("请主动开场，填写这一轮回答") } catch(e:Exception) { toast(graph.safeError(e)) } }); body.addView(turns); body.addView(answer)
        body.addView(ViewStyles.button(this,"提交这一轮",true) {
            if(session.loading) return@button
            val request=session.begin() ?: run { toast("请先开始练习"); return@button }; val userText=answer.text.toString(); val engine=PracticeEngine(graph.chat,graph.settings.load().profile); answer.isEnabled=false
            work("practice",{ _ -> engine.respond(request.state,userText,request.cancel) },{ turn -> if(session.complete(request,turn)) { answer.isEnabled=true; turns.addView(ViewStyles.text(this,"我：$userText",17f)); turns.addView(ViewStyles.text(this,"模拟对方：${turn.simulatedReplies.single()}",17f,ViewStyles.BLUE)); turns.addView(ViewStyles.text(this,"教练建议：${turn.feedback}",14f,ViewStyles.MUTED)); answer.setText("") } },{ error -> if(session.fail(request)) { answer.isEnabled=true; toast(error) } })
        })
    }
    override fun onDestroy() { session.cancel(); super.onDestroy() }
}
