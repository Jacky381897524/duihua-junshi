package com.loveadvisor.android.diagnostics

import android.content.Context
import android.os.Process
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.loveadvisor.android.BuildConfig
import com.loveadvisor.core.diagnostics.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Metadata only. Callers cannot supply message text, identities, URLs or operation IDs. */
object DiagnosticLogs {
    private val worker=Executors.newSingleThreadScheduledExecutor { task -> Thread(task,"diagnostic-journal").apply { isDaemon=true } }
    @Volatile private var journal:DiagnosticJournal?=null
    @Volatile private var fatalSummary=""
    private val context=ThreadLocal<LogOperation?>()
    fun parent()=context.get()
    fun <T> within(operation:LogOperation?,block:()->T):T { val previous=context.get();context.set(operation);try { return block() } finally { context.set(previous) } }
    @Synchronized fun attach(context:Context) {
        if(journal!=null)return
        val app=context.applicationContext ?: context
        val log=DiagnosticJournal({ File(app.filesDir,"diagnostic-logs/timeline.json") },worker,BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,Process.myPid(),delayPersist={ task -> worker.schedule(task,250,TimeUnit.MILLISECONDS);Unit })
        journal=log
        log.event(LogStep.PROCESS_START,LogState.OK,metrics=mapOf(LogMetric.API_LEVEL to android.os.Build.VERSION.SDK_INT.toLong()))
        // Fatal callbacks must still reach Android's original handler. A small synchronous
        // receipt survives when the asynchronous journal has no time to finish writing.
        val original=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(DiagnosticCrashHandler({ thread,error ->
            if(original!=null)original.uncaughtException(thread,error)
            else { Process.killProcess(Process.myPid());kotlin.system.exitProcess(10) }
        },{ error ->
                val fault=LogFault.capture(error)
                log.event(LogStep.EXCEPTION,LogState.CRASH,LogReason.UNCAUGHT_EXCEPTION,fault=fault)
                val receipt=File(app.filesDir,"diagnostic-logs/fatal.json")
                receipt.parentFile?.mkdirs()
                val payload=Gson().toJson(mapOf("version" to BuildConfig.VERSION_NAME,"build" to BuildConfig.VERSION_CODE,"pid" to Process.myPid(),"time" to System.currentTimeMillis(),"fault" to fault))
                java.io.FileOutputStream(receipt).use { it.write(payload.toByteArray());it.fd.sync() }
        }))
        worker.execute {
            val receipt=File(app.filesDir,"diagnostic-logs/fatal.json")
            if(receipt.isFile)try {
                if(receipt.length()>32_768)throw IllegalArgumentException()
                val root=JsonParser.parseString(receipt.readText()).asJsonObject
                val version=root.get("version")?.asString?.takeIf { it.matches(Regex("V[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,5}")) } ?: "unknown"
                val build=root.get("build")?.asLong?.takeIf { it in 0..Int.MAX_VALUE } ?: 0L
                val pid=root.get("pid")?.asLong?.takeIf { it in 0..Int.MAX_VALUE } ?: 0L
                val time=root.get("time")?.asLong?.takeIf { it in 0..253402300799999L } ?: 0L
                val fault=root.getAsJsonObject("fault")
                fun tokens(name:String,max:Int):List<String> {
                    val pattern=if(name=="types")Regex("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*") else Regex("[A-Za-z_$][A-Za-z0-9_$.]*\\.(?:[A-Za-z_$][A-Za-z0-9_$]*|<init>|<clinit>):(?:-1|[0-9]{1,7})")
                    return fault.getAsJsonArray(name).take(max).mapNotNull { it.takeIf { v->v.isJsonPrimitive && v.asJsonPrimitive.isString }?.asString?.takeIf { s->s.length<=240 && pattern.matches(s) } }
                }
                // The receipt's original version/PID/time are validated numeric metadata;
                // no unvalidated persisted strings are replayed into the journal.
                val captured=LogFault(LogReason.UNCAUGHT_EXCEPTION,tokens("types",4),tokens("frames",8),fault.get("httpStatus")?.takeIf { !it.isJsonNull }?.asInt?.takeIf { it in 100..599 })
                fatalSummary="最近异常退出收据：版本=$version/$build PID=$pid 时间戳=$time\n类型=${captured.types.joinToString(" <- ")}\n${captured.frames.joinToString("\n")}\n收据在下次异常退出时覆盖；系统强杀等可能没有收据。"
            } catch(error:Exception) { log.event(LogStep.PROCESS_EXIT,LogState.WARN,LogReason.STORAGE_FAILURE,fault=LogFault.capture(error)) }
        }
    }
    fun event(step:LogStep,state:LogState=LogState.STEP,reason:LogReason=LogReason.NONE,operation:LogOperation?=context.get(),metrics:Map<LogMetric,Long> = emptyMap(),fault:LogFault?=null) { journal?.event(step,state,reason,operation,metrics,fault) }
    fun begin(step:LogStep,parent:LogOperation?=context.get(),metrics:Map<LogMetric,Long> = emptyMap()):LogOperation?=journal?.begin(step,parent,metrics)
    fun end(operation:LogOperation?,state:LogState,reason:LogReason=LogReason.NONE,fault:LogFault?=null,metrics:Map<LogMetric,Long> = emptyMap()) { journal?.end(operation,state,reason,fault,metrics) }
    fun failed(step:LogStep,error:Exception,operation:LogOperation?=context.get()) { val fault=LogFault.capture(error);event(step,LogState.FAIL,fault.reason,operation,fault=fault) }
    fun text(limit:Int=200)=(journal?.text(limit) ?: "诊断日志尚未初始化，不能据此判断没有故障。")+"\n"+fatalSummary+"\n启动步骤 COUNT 编号："+com.loveadvisor.core.startup.StartupStage.entries.joinToString { "${it.ordinal}=${it.name}" }+"\n平台 PLATFORM：0=未知，1=微信，2=QQ，3=抖音，4=其他已启用社交应用；CHAT_KIND：1=单聊，2=群聊。候选请求 REPLY_REQUEST 的 ENABLED：2=真实（优先），1=袪味，0=原版规则；PASTE_REPLY_TAP的COUNT为单句字数，不记录剪贴板正文；MEMORY_READ的ME_MESSAGE_COUNT/OTHER_MESSAGE_COUNT分别为本次双方消息数，COUNT为新增去重消息数，MEMORY_RETRIEVE的COUNT为本次检索资料数；后台记忆分类有25秒总时限，前台生成优先。REQUEST_CHARS为请求字符数，不是token；INPUT_TOKENS/OUTPUT_TOKENS/CACHED_INPUT_TOKENS是服务返回的输入/输出/缓存输入用量，未提供时不显示，不代表零。三段表达SETTING_CHANGE的ENABLED：0=默认，1=袪味，2=真实。"
    fun flush() { journal?.flush() }
    fun step(id:String)=when {
        id.startsWith("test-")->LogStep.MODEL_TEST
        id.startsWith("catalog")->LogStep.MODEL_CATALOG
        id=="judgment"->LogStep.JUDGMENT_REQUEST
        id=="advisor"->LogStep.REPLY_REQUEST
        id=="vision" || id=="ocr"->LogStep.VISION_REQUEST
        id=="native-read"->LogStep.READ_NATIVE
        id=="capture"->LogStep.READ_TAP
        id=="fill"->LogStep.FILL_RESULT
        id=="startup"->LogStep.STARTUP
        id=="process_exit_history"->LogStep.PROCESS_EXIT
        id=="config"->LogStep.CONFIG_SAVE
        id=="diagnostic_export"->LogStep.LOG_EXPORT
        id=="memory"->LogStep.MEMORY_EDIT
        else->LogStep.LOCAL_TASK
    }
    /** Classify known local feedback, but NEVER retain the supplied string. */
    fun reason(value:String)=when {
        value.contains("已超过24小时")->LogReason.TOPIC_EXPIRED
        value.contains("草稿") && (value.contains("变化") || value.contains("保留") || value.contains("已有"))->LogReason.DRAFT_CHANGED
        value.contains("取消")->LogReason.USER_CANCELLED
        value.contains("超时")->LogReason.FILL_TIMEOUT
        value.contains("未定位") || value.contains("输入框") && (value.contains("未就绪") || value.contains("未提供") || value.contains("无法确认"))->LogReason.INPUT_NOT_FOUND
        value.contains("系统保护")->LogReason.SCREENSHOT_SECURE
        value.contains("未返回可用聊天")->LogReason.VISION_EMPTY
        value.contains("密钥") || value.contains("配置视觉")->LogReason.NO_MODEL_KEY
        value.contains("先读取") || value.contains("先点击读取")->LogReason.NO_READ_CONTEXT
        value.contains("变化") || value.contains("核验") || value.contains("重新读取")->LogReason.CONTEXT_CHANGED
        value.contains("前台") || value.contains("请打开微信")->LogReason.NO_FOREGROUND
        value.contains("聊天") || value.contains("窗口")->LogReason.NO_CHAT
        else->LogReason.UNEXPECTED
    }
    fun action(value:String)=when {
        value in setOf("复制单句回复","开始分析","粘贴")->LogReason.UI_PASTE_REPLY
        value=="读取信息"->LogReason.UI_READ
        value=="判断分析" || value=="选句指定回复" || value=="选句回复"->LogReason.UI_JUDGMENT
        value=="填入"->LogReason.UI_FILL
        value=="复制"->LogReason.UI_COPY
        value=="建议"->LogReason.UI_ADVICE
        value=="指定回复" || value.startsWith("围绕这句")->LogReason.UI_TARGET_REPLY
        value=="模型设置"->LogReason.UI_MODEL_SETTINGS
        value=="悬浮窗设置"->LogReason.UI_OVERLAY_SETTINGS
        value=="回复风格设置"->LogReason.UI_STYLE_SETTINGS
        value.startsWith("刷新换一批")->LogReason.UI_REFRESH
        value.startsWith("保存")->LogReason.UI_SAVE
        value.startsWith("返回") || value=="取消"->LogReason.UI_BACK
        value.startsWith("测试")->LogReason.UI_MODEL_TEST
        value=="刷新模型"->LogReason.UI_MODEL_CATALOG
        value.contains("诊断日志") || value=="导出日志文件"->LogReason.UI_LOG_EXPORT
        value.contains("清除缓存")->LogReason.UI_CACHE_CLEAR
        value.contains("停止辅助")->LogReason.UI_SERVICE_STOP
        value.contains("权限") || value.contains("无障碍") || value=="允许悬浮窗"->LogReason.UI_PERMISSIONS
        value.contains("设置")->LogReason.UI_SETTINGS
        else->LogReason.UI_OTHER
    }
    fun page(id:String)=when(id) {
        "MainActivity"->LogReason.PAGE_MAIN
        "SettingsActivity","models"->LogReason.PAGE_MODEL_SETTINGS
        "ReplyStyleActivity","style-editor"->LogReason.PAGE_REPLY_STYLE
        "OverlaySettingsActivity","overlay-settings"->LogReason.PAGE_OVERLAY_SETTINGS
        "DiagnosticsActivity"->LogReason.PAGE_DIAGNOSTICS
        "PermissionsActivity"->LogReason.PAGE_PERMISSIONS
        "ConsultationActivity"->LogReason.PAGE_CONSULTATION
        "pasted-reply"->LogReason.PAGE_PASTE_REPLY
        "SocialAppsActivity"->LogReason.PAGE_SOCIAL_APPS
        "judgment-list"->LogReason.PAGE_JUDGMENT
        "model-picker","style-objects","style-chat-kind"->LogReason.PAGE_PICKER
        else->LogReason.PAGE_OTHER
    }
    fun dispatch(value:String,action:()->Unit) {
        val op=begin(LogStep.UI_ACTION)
        event(LogStep.UI_ACTION,reason=this.action(value),operation=op)
        try { within(op) { action() };end(op,LogState.OK,LogReason.OK_DISPATCHED) }
        catch(error:Exception) { val fault=LogFault.capture(error);end(op,LogState.FAIL,fault.reason,fault);throw error }
    }
    fun feedback(value:String) { event(LogStep.UI_ACTION,LogState.WARN,reason(value)) }
    fun <T> recover(step:LogStep,operation:LogOperation?=context.get(),block:()->T):T?=try { block() }
        catch(cancel:java.util.concurrent.CancellationException) { event(step,LogState.CANCELLED,LogReason.USER_CANCELLED,operation);null }
        catch(error:Exception) { failed(step,error,operation);null }
}
