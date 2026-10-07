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
        page("连接聊天应用","开启权限后，在微信、QQ或抖音点击图标，再点读取信息。仅支持一对一私聊，发送由你操作。")
        body.addView(ViewStyles.text(this,"悬浮窗：${if(Settings.canDrawOverlays(this)) "已允许" else "未允许"}\n无障碍服务：${if(ChatCaptureService.instance!=null) "已连接" else "未连接"}",18f,bold=true))
        body.addView(ViewStyles.button(this,"允许悬浮窗",true) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) })
        body.addView(ViewStyles.button(this,"打开无障碍设置") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        body.addView(ViewStyles.button(this,"开启聊天悬浮球") { val service=ChatCaptureService.instance; if(service==null) toast("无障碍服务尚未连接，请在系统设置重新开启对话军师") else { service.showFloating(); toast("已开启，进入微信、QQ或抖音后自动显示") } })
        body.addView(ViewStyles.text(this,"图标在微信、QQ和抖音前台显示；切到其他 App 自动隐藏。读取只在点击读取信息时发生。",13f,ViewStyles.MUTED))
        ChatApps.packages.forEach { pkg -> body.addView(ViewStyles.button(this,"打开${ChatApps.label(pkg)}") { val launch=packageManager.getLaunchIntentForPackage(pkg); if(launch!=null) startActivity(launch) else toast("未找到已安装的${ChatApps.label(pkg)}") }) }
        body.addView(ViewStyles.text(this,"在系统列表中启用“对话军师”。聊天文字会发送给你配置的 AI 服务；原生读取不可靠时，聊天窗口截图会发送给你选择的视觉服务。最近 50 条上下文加密保存在本机。",14f,ViewStyles.MUTED))
    }
}
