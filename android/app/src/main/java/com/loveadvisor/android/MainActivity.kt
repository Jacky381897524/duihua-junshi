package com.loveadvisor.android

import android.os.Bundle
import android.content.Intent
import com.loveadvisor.android.ui.*
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.settings.SettingsActivity
import com.loveadvisor.android.settings.ReplyStyleActivity
import com.loveadvisor.android.settings.OverlaySettingsActivity
import com.loveadvisor.android.knowledge.KnowledgeActivity
import com.loveadvisor.android.memory.ProfilesActivity
import com.loveadvisor.android.advisor.*

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        page("对话军师","读懂当下，再决定怎么回应。")
        val hero=ViewStyles.card(this); body.addView(hero)
        hero.addView(ViewStyles.text(this,"一个对话，六种思路。",26f,bold=true)); hero.addView(ViewStyles.text(this,"结合聊天副驾、狗头军师与恋爱大师的知识，给你不同策略的候选回复。",15f,ViewStyles.MUTED))
        hero.addView(ViewStyles.button(this,"连接聊天悬浮窗",true) { open(PermissionsActivity::class.java) })
        hero.addView(ViewStyles.button(this,"完整咨询 / 润色草稿") { open(ConsultationActivity::class.java) })
        val config=graph.settings.load(); val model=ViewStyles.card(this); body.addView(model); model.addView(ViewStyles.text(this,"你的模型",19f,bold=true)); model.addView(ViewStyles.text(this,config.profile.selectedModelId ?: "填写服务地址与密钥，刷新选择模型",14f,ViewStyles.MUTED)); model.addView(ViewStyles.button(this,"模型与连接设置") { open(SettingsActivity::class.java) })
        body.addView(ViewStyles.button(this,"回复风格设置") { open(ReplyStyleActivity::class.java) })
        body.addView(ViewStyles.switch(this,"启用聊天悬浮窗",config.overlayEnabled) { enabled -> graph.settings.update { it.copy(overlayEnabled=enabled) }; if(!enabled && com.loveadvisor.core.capture.CapturePolicy.invalidateForDisabledCapture(graph.tracker.currentToken)) graph.controller.invalidate(); ChatCaptureService.instance?.settingsChanged() })
        body.addView(ViewStyles.button(this,"悬浮窗设置 · ${config.bubbleAutoHideSeconds} 秒后隐藏") { open(OverlaySettingsActivity::class.java) })
        body.addView(ViewStyles.text(this,"微信 · QQ · 抖音单聊按需读取 · 最近 50 条上下文\n点击悬浮图标展开，再点读取信息；刷新使用已读取上下文，复制后收起，填入后由你发送。",14f,ViewStyles.MUTED))
        body.addView(ViewStyles.switch(this,"减少动效",config.reducedMotion) { enabled -> graph.settings.update { it.copy(reducedMotion=enabled) } })
        body.addView(ViewStyles.button(this,"融合知识库与我的笔记") { open(KnowledgeActivity::class.java) })
        body.addView(ViewStyles.button(this,"关系资料、记忆与历史") { open(ProfilesActivity::class.java) })
        body.addView(ViewStyles.button(this,"逐轮模拟练习") { open(PracticeActivity::class.java) })
        body.addView(ViewStyles.button(this,"导入聊天 / 本地截图识别") { open(ImportActivity::class.java) })
        body.addView(ViewStyles.button(this,"多对象独立分析与比较") { open(ComparisonActivity::class.java) })
        body.addView(ViewStyles.button(this,"连接诊断") { open(DiagnosticsActivity::class.java) }); body.addView(ViewStyles.button(this,"关于与许可") { open(AboutActivity::class.java) })
        val author=ViewStyles.card(this);body.addView(author)
        val authorRow=ViewStyles.row(this);author.addView(authorRow)
        val avatarSize=ViewStyles.dp(this,48f)
        val avatar=android.widget.ImageView(this).apply {
            setImageResource(R.drawable.author_avatar);scaleType=android.widget.ImageView.ScaleType.CENTER_CROP
            background=ViewStyles.shape(this@MainActivity,0xFFFFE500.toInt(),24f);clipToOutline=true
            contentDescription="作者海街寺庙头像"
        }
        authorRow.addView(avatar,android.widget.LinearLayout.LayoutParams(avatarSize,avatarSize).apply { rightMargin=ViewStyles.dp(this@MainActivity,12f) })
        val authorName=ViewStyles.column(this)
        authorName.addView(ViewStyles.text(this,"作者",12f,ViewStyles.MUTED))
        authorName.addView(ViewStyles.text(this,"海街寺庙",17f,bold=true))
        authorRow.addView(authorName,android.widget.LinearLayout.LayoutParams(0,-2,1f))
        authorRow.addView(ViewStyles.text(this,"V1.2.14",12f,ViewStyles.MUTED))
    }
}
