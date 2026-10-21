package com.loveadvisor.android.ui

import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.content.ClipData
import android.provider.Settings
import android.widget.TextView
import androidx.core.content.FileProvider
import com.loveadvisor.android.BuildConfig
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.diagnostics.DiagnosticLogs as Logs
import com.loveadvisor.core.diagnostics.*
import java.io.File

class DiagnosticsActivity:BaseActivity() {
    private lateinit var timeline:TextView
    private lateinit var exits:TextView
    private var exitHistory="系统退出记录正在读取；尚不能据此判断没有故障。"
    private fun safeDevice(value:String)=value.filter { it.isLetterOrDigit() || it in " ._-" }.take(100)
    private fun header():String {
        val config=graph.settings.load()
        val wechat=try { @Suppress("DEPRECATION") val p=packageManager.getPackageInfo("com.tencent.mm",0);safeDevice(p.versionName.orEmpty())+" / ${p.longVersionCode}" } catch(_:android.content.pm.PackageManager.NameNotFoundException) { "未安装或不可查询" }
        return "军师版本：${BuildConfig.VERSION_NAME}\n版本代码：${BuildConfig.VERSION_CODE}\n设备：${safeDevice(Build.MANUFACTURER)} ${safeDevice(Build.MODEL)}\nAndroid：${safeDevice(Build.VERSION.RELEASE)}（API ${Build.VERSION.SDK_INT}）\n微信：$wechat\n悬浮窗：${Settings.canDrawOverlays(this)}\n服务连接：${ChatCaptureService.instance!=null}\n回复模型已选择：${!config.profile.selectedModelId.isNullOrBlank()}\n视觉模型已选择：${!config.vision.selectedModelId.isNullOrBlank()}"
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        Logs.event(LogStep.LOG_OPEN,LogState.OK)
        page("诊断日志","按操作编号串联步骤和结果，记录错误码与代码位置。保存在本机，清除缓存会保留；不记录聊天、昵称、密钥或截图。")
        body.addView(ViewStyles.text(this,header(),13f).apply { setTextIsSelectable(true) })
        body.addView(ViewStyles.text(this,"最近记录",17f,bold=true))
        timeline=ViewStyles.text(this,Logs.text(120),12f).apply { setTextIsSelectable(true) };body.addView(timeline)
        body.addView(ViewStyles.button(this,"刷新日志") { Logs.event(LogStep.LOG_REFRESH,LogState.OK);timeline.text=Logs.text(120) })
        body.addView(ViewStyles.button(this,"导出日志文件",true) { export() })
        body.addView(ViewStyles.text(this,"导出包含保留范围内的全部记录。默认最多30天、10,000条、4MiB，超限会轮换并标明数量。强杀、断电或系统未提供的信息可能只留下未完成记录，不能保证每次直接得出唯一根因。",12f,ViewStyles.MUTED))
        body.addView(ViewStyles.text(this,"启动阶段：\n${com.loveadvisor.android.AppStartupTrace.description()}",12f,ViewStyles.MUTED).apply { setTextIsSelectable(true) })
        exits=ViewStyles.text(this,"最近进程退出：\n$exitHistory",12f,ViewStyles.MUTED).apply { setTextIsSelectable(true) };body.addView(exits)
        work("process_exit_history",{ cancel -> cancel.check();com.loveadvisor.android.diagnostics.ProcessExitHistory.read(applicationContext) },{ result -> exitHistory=result;exits.text="最近进程退出：\n$result" },{
            exitHistory="系统退出记录暂不可用；不能据此判断没有发生故障。";exits.text=exitHistory
        })
    }
    private fun export() {
        Logs.event(LogStep.LOG_EXPORT,LogState.START)
        val metadata=header()+"\n\n启动阶段：\n"+com.loveadvisor.android.AppStartupTrace.description()+"\n\n最近进程退出：\n"+exitHistory
        work("diagnostic_export",{ cancel ->
            cancel.check()
            val folder=File(cacheDir,"diagnostic-exports");check(folder.isDirectory || folder.mkdirs())
            folder.listFiles()?.filter { it.isFile && it.name.startsWith("diagnostic-") && it.extension=="txt" && it.lastModified()<System.currentTimeMillis()-7*86_400_000L }?.forEach { it.delete() }
            File(folder,"diagnostic-${BuildConfig.VERSION_NAME}-${System.currentTimeMillis()}.txt").also { it.writeText(metadata+"\n\n"+Logs.text(10_000));cancel.check() }
        },{ file ->
            val uri=FileProvider.getUriForFile(this,"$packageName.diagnostics",file)
            val share=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            share.clipData=ClipData.newRawUri("诊断日志",uri)
            try { startActivity(Intent.createChooser(share,"导出诊断日志"));Logs.event(LogStep.LOG_EXPORT,LogState.OK,LogReason.OK_DISPATCHED) }
            catch(error:Exception) { Logs.failed(LogStep.LOG_EXPORT,error);toast("没有可用的分享应用，请安装后重试") }
        })
    }
}
