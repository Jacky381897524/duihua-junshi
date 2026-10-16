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
        page("悬浮窗与左上角胶囊","选择聊天时的入口；只有点读取信息才会收集和分析。")
        val config=graph.settings.load()
        val appearance=ViewStyles.card(this);body.addView(appearance)
        appearance.addView(ViewStyles.text(this,"候选面板外观",19f,bold=true))
        appearance.addView(ViewStyles.switch(this,"使用轻毛玻璃",config.frostedPanelEnabled) { enabled ->
            graph.settings.update { it.copy(frostedPanelEnabled=enabled) }
            ChatCaptureService.instance?.settingsChanged()
            toast(if(enabled)"已选择轻毛玻璃，返回聊天后查看" else "已恢复普通面板")
        })
        appearance.addView(ViewStyles.text(this,"背景柔和模糊，文字和按钮保持清晰。省电模式或系统不支持模糊时，自动使用清晰底色。关闭开关即可恢复普通面板。",14f,ViewStyles.MUTED))
        val capsule=ViewStyles.card(this);body.addView(capsule)
        capsule.addView(ViewStyles.text(this,"左上角胶囊入口",19f,bold=true))
        capsule.addView(ViewStyles.text(this,"默认在时间右侧、摄像头左侧。仅微信、QQ、抖音前台显示，不依赖通知。点按打开候选，长按后拖动微调，位置自动保存。",14f,ViewStyles.MUTED))
        capsule.addView(ViewStyles.switch(this,"使用左上角胶囊",config.topCapsuleEnabled) { enabled ->
            graph.settings.update { it.copy(topCapsuleEnabled=enabled,nativeIslandEnabled=false,nativeIslandConfirmedBuild=null) }
            ChatCaptureService.instance?.settingsChanged()
            toast(if(enabled)"已开启，返回聊天应用查看左上角" else "已恢复普通悬浮球")
        })
        capsule.addView(ViewStyles.text(this,"开启后隐藏普通悬浮球；关闭后恢复。六条候选与手动读取保持不变，面板只通过顶部三点拖动。",13f,ViewStyles.MUTED))
        capsule.addView(ViewStyles.button(this,"重置胶囊位置") {
            graph.settings.update { it.copy(capsuleOffsetXdp=0f,capsuleOffsetYdp=0f) }
            ChatCaptureService.instance?.settingsChanged();toast("已重置为左上角时间右侧")
        })
        val card=ViewStyles.card(this);body.addView(card)
        card.addView(ViewStyles.text(this,"普通悬浮球自动隐藏",19f,bold=true))
        card.addView(ViewStyles.text(this,"普通球在面板收起且无操作后滑到最近的屏幕边缘，露出约三分之一。点一下完整露出，再点一下展开面板。胶囊入口不使用这个计时。",14f,ViewStyles.MUTED))
        val seconds=ViewStyles.field(this,"等待秒数（1–3600）",config.bubbleAutoHideSeconds.toString()).apply {
            inputType=InputType.TYPE_CLASS_NUMBER;contentDescription="自动隐藏等待秒数"
        }
        card.addView(seconds)
        card.addView(ViewStyles.text(this,"默认15秒；展开面板或拖动时不会自动隐藏。",13f,ViewStyles.MUTED))
        card.addView(ViewStyles.button(this,"保存等待时间",true) {
            val value=seconds.text.toString().trim().toIntOrNull()
            if(value==null || value !in 1..BubbleIdlePolicy.MAX_SECONDS) { seconds.error="请输入1–3600的整数秒数";return@button }
            graph.settings.update { it.copy(bubbleAutoHideSeconds=value) };ChatCaptureService.instance?.settingsChanged()
            toast("已保存：${value}秒无操作后隐藏")
        })
    }
}
