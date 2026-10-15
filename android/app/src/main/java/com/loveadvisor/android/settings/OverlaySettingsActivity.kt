package com.loveadvisor.android.settings

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import android.content.Intent
import android.widget.TextView
import android.text.InputType
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.ui.BaseActivity
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.overlay.BubbleIdlePolicy

class OverlaySettingsActivity:BaseActivity() {
    private lateinit var islandStatus:TextView
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        page("悬浮窗设置","减少遮挡，需要时轻点唤出。")
        val card=ViewStyles.card(this);body.addView(card)
        card.addView(ViewStyles.text(this,"自动隐藏等待时间",19f,bold=true))
        card.addView(ViewStyles.text(this,"面板收起且无操作后，悬浮球滑到最近的屏幕边缘，只露出约三分之一。点一下完整露出，再点一下展开面板。",14f,ViewStyles.MUTED))
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
        val island=ViewStyles.card(this);body.addView(island)
        island.addView(ViewStyles.text(this,"vivo 原生灵动岛 · 测试",19f,bold=true))
        island.addView(ViewStyles.text(this,"仅在微信、QQ、抖音前台显示。测试时保留球；从系统原生岛点击并成功打开面板后，才隐藏球。普通通知点击不算验证。",14f,ViewStyles.MUTED))
        island.addView(ViewStyles.switch(this,"使用 vivo 原生灵动岛",graph.settings.load().nativeIslandEnabled) { enabled ->
            graph.settings.update { it.copy(nativeIslandEnabled=enabled,nativeIslandConfirmedBuild=null) }
            if(enabled && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),14014)
            }
            ChatCaptureService.instance?.settingsChanged();updateStatus()
        })
        islandStatus=ViewStyles.text(this,"",13f,ViewStyles.MUTED);island.addView(islandStatus)
        island.addView(ViewStyles.button(this,"重新测试原生岛") {
            if(!graph.settings.load().nativeIslandEnabled) { toast("请先开启原生灵动岛开关");return@button }
            ChatCaptureService.instance?.startNativeIslandTest()
            toast("请返回聊天应用，在60秒内点击系统顶部的原生岛；未出现时球仍可使用")
            updateStatus()
        })
        island.addView(ViewStyles.button(this,"通知权限与原生岛通知通道") {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
        })
        island.addView(ViewStyles.text(this,"当前属于兼容性测试：系统可能忽略扩展字段，正式场景接入可能需要厂商授权。开关不会自动读取聊天或调用模型。",13f,ViewStyles.MUTED))
        updateStatus()
    }
    private fun updateStatus() { if(::islandStatus.isInitialized)islandStatus.text=ChatCaptureService.instance?.islandDiagnostic ?: "无障碍服务未连接，暂时不能测试原生入口" }
    override fun onResume() { super.onResume();ChatCaptureService.instance?.settingsChanged();updateStatus() }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==14014) { ChatCaptureService.instance?.settingsChanged();updateStatus();toast(if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)"已授权，请返回聊天应用测试" else "通知未授权，继续使用悬浮球") }
    }
}
