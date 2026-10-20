package com.loveadvisor.android.ui
import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.provider.Settings
import com.loveadvisor.android.capture.ChatCaptureService
class DiagnosticsActivity:BaseActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); page("连接诊断","仅记录阶段、状态与耗时；不导出密钥、聊天正文或截图。")
        val wechat=runCatching { @Suppress("DEPRECATION") val p=packageManager.getPackageInfo("com.tencent.mm",0); "${p.versionName} / ${p.longVersionCode}" }.getOrDefault("未读取到")
        val config=graph.settings.load(); val text="设备：${Build.MANUFACTURER} ${Build.MODEL}\nAndroid：${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）\n微信：$wechat\n悬浮窗：${Settings.canDrawOverlays(this)}\n服务连接：${ChatCaptureService.instance!=null}\n选定模型：${config.profile.selectedModelId ?: "未选择"}\n\n脱敏事件（最近100条）：\n${graph.diagnosticText()}"
        body.addView(ViewStyles.text(this,text,15f).apply { setTextIsSelectable(true) })
        body.addView(ViewStyles.text(this,"本次启动：${com.loveadvisor.android.AppStartup.description(this)}",13f,ViewStyles.MUTED))
        val service=ChatCaptureService.instance
        body.addView(ViewStyles.text(this,"入口：${if(config.topCapsuleEnabled) "左上角胶囊（无需通知）" else "普通悬浮球"}",14f))
        body.addView(ViewStyles.button(this,"悬浮窗与左上角胶囊设置") { open(com.loveadvisor.android.settings.OverlaySettingsActivity::class.java) })
        val captureStatus="回复模型：${config.profile.selectedModelId ?: "未选择"}\n视觉模型：${config.vision.selectedModelId ?: "未选择"}\n最近读取：${service?.diagnosticStatus ?: "无障碍服务未连接"}\n最近微信读取：${service?.lastWechatDiagnosticStatus ?: "无障碍服务未连接"}"
        val fillStatus="最近填入：${service?.lastFillDiagnosticStatus ?: "无障碍服务未连接"}\n填入阶段：${service?.lastFillStages ?: "无障碍服务未连接"}"
        val screenshotStatus="最近截图：${service?.lastScreenshotDiagnosticStatus ?: "无障碍服务未连接"}\n这里记录软件的截图接口结果，不能代替手机按键截图的对照测试。"
        body.addView(ViewStyles.text(this,captureStatus+"\n"+fillStatus+"\n"+screenshotStatus,15f).apply { setTextIsSelectable(true) })
        body.addView(ViewStyles.button(this,"导出脱敏诊断") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text+"\n"+captureStatus+"\n"+fillStatus+"\n"+screenshotStatus),"导出诊断")) })
    }
}
