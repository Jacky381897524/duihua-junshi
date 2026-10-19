package com.loveadvisor.android.ui
import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.core.capture.ChatApps
class PermissionsActivity:BaseActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() { super.onResume(); ChatCaptureService.instance?.ensureOverlay(); render() }
    private fun render() {
        page("连接聊天应用","开启权限后，在微信、QQ或抖音点击图标，再点读取信息。支持单聊与群聊，发送由你操作。")
        body.addView(ViewStyles.text(this,"悬浮窗：${if(Settings.canDrawOverlays(this)) "已允许" else "未允许"}\n无障碍服务：${if(ChatCaptureService.instance!=null) "已连接" else "未连接"}",18f,bold=true))
        val settings=SystemSettingsNavigator(this)
        body.addView(ViewStyles.button(this,"允许悬浮窗",true) { if(!settings.overlay())toast("无法打开系统设置，请在手机设置中查找“悬浮窗”或“显示在其他应用上层”") })
        body.addView(ViewStyles.button(this,"打开无障碍设置") { if(!settings.accessibility())toast("无法打开系统设置，请在手机设置中查找“无障碍”并启用对话军师") })
        body.addView(ViewStyles.button(this,"打开应用系统设置") { if(!settings.appDetails())toast("无法打开应用设置，请长按对话军师图标进入应用信息") })
        body.addView(ViewStyles.text(this,"不同手机可能称为“悬浮窗”“显示在其他应用上层”或“在其他应用上层显示”。如服务经常断开，可在应用系统设置中允许后台运行；具体选项由手机系统提供。",13f,ViewStyles.MUTED))
        body.addView(ViewStyles.text(this,"图标在微信、QQ和抖音前台显示；切到其他 App 自动隐藏。读取只在点击读取信息时发生。",13f,ViewStyles.MUTED))
        ChatApps.packages.forEach { pkg -> body.addView(ViewStyles.button(this,"打开${ChatApps.label(pkg)}") { val launch=packageManager.getLaunchIntentForPackage(pkg); if(launch!=null) startActivity(launch) else toast("未找到已安装的${ChatApps.label(pkg)}") }) }
        body.addView(ViewStyles.text(this,"在系统列表中启用“对话军师”。聊天文字会发送给你配置的 AI 服务；原生读取不可靠时，聊天窗口截图会发送给你选择的视觉服务。私聊最近50条、群聊默认100条已读上下文加密保存在本机。",14f,ViewStyles.MUTED))
    }
}
