package com.loveadvisor.android.settings

import android.os.Bundle
import android.text.InputType
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.ui.BaseActivity
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.overlay.BubbleIdlePolicy

class OverlaySettingsActivity:BaseActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        page("悬浮窗设置","减少遮挡，需要时轻点唤出。")
        val card=ViewStyles.card(this);body.addView(card)
        card.addView(ViewStyles.text(this,"自动隐藏等待时间",19f,bold=true))
        card.addView(ViewStyles.text(this,"面板收起且无操作后，悬浮球滑到最近的屏幕边缘，只露出约四分之一。点一下完整露出，再点一下展开面板。",14f,ViewStyles.MUTED))
        val seconds=ViewStyles.field(this,"等待秒数（1–3600）",graph.settings.load().bubbleAutoHideSeconds.toString()).apply {
            inputType=InputType.TYPE_CLASS_NUMBER
            contentDescription="自动隐藏等待秒数"
        }
        card.addView(seconds)
        card.addView(ViewStyles.text(this,"默认 15 秒；展开面板或拖动时不会自动隐藏。",13f,ViewStyles.MUTED))
        card.addView(ViewStyles.button(this,"保存等待时间",true) {
            val value=seconds.text.toString().trim().toIntOrNull()
            if(value==null || value !in 1..BubbleIdlePolicy.MAX_SECONDS) {
                seconds.error="请输入 1–3600 的整数秒数";return@button
            }
            graph.settings.update { it.copy(bubbleAutoHideSeconds=value) }
            ChatCaptureService.instance?.settingsChanged()
            toast("已保存：${value} 秒无操作后隐藏")
        })
    }
}
