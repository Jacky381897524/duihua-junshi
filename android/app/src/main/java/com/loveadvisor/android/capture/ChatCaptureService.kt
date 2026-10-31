package com.loveadvisor.android.capture
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.os.*
import android.graphics.Bitmap
import android.util.Base64
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.loveadvisor.android.AppGraph
import com.loveadvisor.android.overlay.OverlayController
import com.loveadvisor.android.capture.ocr.*
import com.loveadvisor.core.api.*
import com.loveadvisor.core.capture.*
import com.loveadvisor.core.model.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.UUID
import com.loveadvisor.core.advisor.ReplyPlan
import com.loveadvisor.core.advisor.PanelReplyRestoration
import com.loveadvisor.core.advisor.CachedHeaderProof
import com.loveadvisor.android.input.VisualFillReference
import com.loveadvisor.android.input.VerifiedInputMethod
import com.loveadvisor.core.input.ComposerFocusResult
import com.loveadvisor.core.input.FillStageTrace
import com.loveadvisor.core.input.ImeFillPolicy
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.loveadvisor.android.diagnostics.DiagnosticLogs as Logs
import com.loveadvisor.core.diagnostics.*

/** Passive events check foreground metadata. Only an explicit gesture reads or analyzes messages. */
class ChatCaptureService:AccessibilityService() {
    val graph get()=AppGraph.get(this)
    lateinit var reader:NodeSnapshotReader; private set
    private lateinit var overlay:OverlayController
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val nativeWorker=Executors.newSingleThreadExecutor { r -> Thread(r,"wechat-native-reader") }
    private val foregroundWorker=Executors.newSingleThreadExecutor { r -> Thread(r,"chat-foreground-metadata") }
    private val foregroundGate=CaptureReadGate()
    private val readGate=CaptureReadGate()
    private val manualRead=ManualReadGate()
    private var readLog:LogOperation?=null
    private var releaseReadLearning:(()->Unit)?=null
    private var fillLog:LogOperation?=null
    private val parser=AutomaticChatParser()
    private val conversation=AutomaticConversation()
    private var identity:ConversationIdentity?=null
    private var lastWindow:WindowSnapshot?=null
    private var foregroundWindowId:Int?=null
    private var wechatForeground=false
    private var foregroundPackage:String?=null
    private var eventWindowMetadata:List<AccessibilityWindowInfo>?=null
    private var lastSupplementalMetadataAt=0L
    /** One OS snapshot is reused throughout this main-thread event/layout pass. */
    fun windowMetadata():List<AccessibilityWindowInfo> = if(Looper.myLooper()==Looper.getMainLooper())eventWindowMetadata ?: windows else windows
    private lateinit var island:com.loveadvisor.android.island.NativeIslandCoordinator
    val islandDiagnostic get()=if(::island.isInitialized)island.diagnostic else "无障碍服务未连接"
    fun islandScope():com.loveadvisor.android.island.IslandLease? {
        val pkg=foregroundPackage ?: return null
        val id=foregroundWindowId ?: return null
        return if(overlayAllowed() && overlaySuspensions==0)com.loveadvisor.android.island.IslandLease("",pkg,id,navigation) else null
    }
    fun islandEnvironment()=com.loveadvisor.core.overlay.IslandEnvironment(
        ::reader.isInitialized && overlayAllowed() && overlaySuspensions==0,
        Build.MANUFACTURER.equals("vivo",true) || Build.BRAND.equals("vivo",true) || Build.BRAND.equals("iqoo",true),
        com.loveadvisor.android.island.NativeIslandNotification(this).enabled(),Build.FINGERPRINT)
    fun startNativeIslandTest() {
        graph.settings.update { it.copy(nativeIslandEnabled=true,nativeIslandConfirmedBuild=null) }
        if(::island.isInitialized)island.stop()
        checkForeground()
    }
    fun onNativeIslandTap(leaseId:String,complete:(Boolean)->Unit={}) {
        val lease=if(::island.isInitialized)island.lease else null
        if(!::reader.isInitialized || lease==null || lease.id!=leaseId || !overlayAllowed() || overlaySuspensions!=0) { complete(false);return }
        nativeWorker.execute {
            val fresh=Logs.recover(LogStep.FOREGROUND_ENTER) { reader.foreground() }
            main.post {
                val valid=instance===this && lease===island.lease && lease.generation==navigation && overlayAllowed() && overlaySuspensions==0 && fresh?.packageName==lease.packageName && fresh.windowId==lease.windowId
                complete(valid && island.handleNativeTap(leaseId))
            }
        }
    }
    private var pastedSnapshot:ChatSnapshot?=null
    val currentChatSnapshot get()=pastedSnapshot?.takeIf { graph.tracker.lastSnapshot==it } ?: conversation.current
    val glassWindow get()=lastForegroundWindow?.takeIf { overlayAllowed() }
    private var lastForegroundWindow:WindowSnapshot?=null
    private var projecting=false
    private var stopping=false
    private var overlaySuspensions=0
    private var lastReservedCaptureStart=0L
    private var lastActualCaptureStart=0L
    private val editorEpoch=java.util.concurrent.atomic.AtomicLong()
    fun editorGeneration()=editorEpoch.get()
    @androidx.annotation.RequiresApi(33)
    override fun onCreateInputMethod():android.accessibilityservice.InputMethod=VerifiedInputMethod(this) { editorEpoch.incrementAndGet() }
    @Volatile private var navigation=0L
    private var foregroundRetries=0
    private val foregroundStages=ArrayDeque<String>()
    private val foregroundTraceStarted=SystemClock.elapsedRealtime()
    val lastForegroundStages get()=foregroundStages.joinToString(" → ").ifEmpty { "尚未取得前台事件" }
    private fun foregroundStage(stage:String) {
        if(foregroundStages.size>=24)foregroundStages.removeFirst()
        foregroundStages.addLast("${SystemClock.elapsedRealtime()-foregroundTraceStarted}ms $stage")
    }
    private var imePackages=emptySet<String>()
    private var stopStartup:(()->Unit)?=null
    private var visualCancel:Cancellation?=null
    private var visualFill:VisualFillReference?=null
    private val savedFillReferences=mutableMapOf<String,VisualFillReference>()
    private var cancelCachedHeader:(()->Unit)?=null
    private var headerCheckPending=false
    private var lastHeaderCheck=0L
    private var headerRetryRequested=false
    private var headerNavigationRevision=0L
    private val headerRetry=Runnable { checkCachedHeader(allowTitleOcr=true) }
    private val windowLogTimes=linkedMapOf<String,Long>()
    private fun recordWindowEvent(key:String,now:Long):Boolean {
        val last=windowLogTimes[key]
        if(last!=null && now-last<2000)return false
        if(windowLogTimes.size>=64 && key !in windowLogTimes)windowLogTimes.remove(windowLogTimes.keys.first())
        windowLogTimes[key]=now;return true
    }
    private fun scheduleHeaderCheck() {
        if(!::overlay.isInitialized || !overlay.hasRetainedPanel())return
        if(headerCheckPending || cancelCachedHeader!=null) { headerRetryRequested=true;return }
        if(!main.hasCallbacks(headerRetry))main.postDelayed(headerRetry,250)
    }
    private fun retryHeaderIfRequested() {
        if(headerRetryRequested) { headerRetryRequested=false;scheduleHeaderCheck() }
    }
    var lastFillDiagnosticStatus="尚未点击填入"; private set
    private val fillTrace=FillStageTrace { SystemClock.elapsedRealtime() }
    val lastFillStages get()=fillTrace.description()
    var lastScreenshotDiagnosticStatus="本次服务尚未请求截图"; private set
    var lastComposerDiagnosticStatus="尚未定位输入区域"; private set
    var lastStyleNicknameFromOcr=false; private set
    private data class VisionReply(val result:VisionConversation,val windowId:Int,val cancel:Cancellation,val ticket:ManualReadGate.Ticket,val navigation:Long,val fillReference:VisualFillReference?)
    private var pendingVision:VisionReply?=null
    var diagnosticStatus="按需读取：尚未检查前台窗口"; private set
    var lastWechatDiagnosticStatus="尚未点击读取信息"; private set
    private val debounced=Runnable { checkForeground() }
    private val readPending=Runnable { observe() }
    private val cleanup=object:Runnable { override fun run() { worker.execute { Logs.recover(LogStep.LOCAL_TASK) { graph.recent.cleanup() } }; main.postDelayed(this,12*3600000L) } }
    private val receiver=object:BroadcastReceiver() { override fun onReceive(context:Context?,intent:Intent?) {
        when(intent?.action) { Intent.ACTION_SCREEN_OFF -> leaveForeground(); Intent.ACTION_USER_PRESENT -> checkForeground() }
    } }
    private fun platform(pkg:String?)=when(pkg) { ChatApps.WECHAT->1L;"com.tencent.mobileqq"->2L;"com.ss.android.ugc.aweme"->3L;else->if(ChatApps.supported(pkg))4L else 0L }
    override fun onCreate() { super.onCreate();Logs.event(LogStep.SERVICE_CREATE,LogState.OK) }
    override fun onUnbind(intent:Intent?):Boolean { Logs.event(LogStep.SERVICE_UNBIND,LogState.WARN);Logs.flush();return super.onUnbind(intent) }
    override fun onServiceConnected() {
        Logs.event(LogStep.SERVICE_BIND,LogState.OK)
        stopping=false
        stopStartup?.invoke()
        val startup=com.loveadvisor.android.AppStartup.get(this)
        stopStartup=startup.observe { state ->
            if(!stopping && state.phase==com.loveadvisor.core.startup.StartupPhase.READY)connectReadyService()
            else if(state.phase in setOf(com.loveadvisor.core.startup.StartupPhase.SLOW,com.loveadvisor.core.startup.StartupPhase.FAILED))diagnosticStatus="正在等待本地设置；打开对话军师查看加载状态"
        }
        startup.start()
    }
    private fun connectReadyService() {
        if(::reader.isInitialized) { ensureOverlay();return }
        instance=this; reader=NodeSnapshotReader(this); overlay=OverlayController(this);island=com.loveadvisor.android.island.NativeIslandCoordinator(this,overlay)
        Logs.event(LogStep.SERVICE_READY,LogState.OK)
        graph.tracker.lastSnapshot?.takeIf { ChatApps.supported(it.packageName) }?.let { conversation.restore(it);identity=it.identity }
        imePackages=Logs.recover(LogStep.SERVICE_READY) { getSystemService(android.view.inputmethod.InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet() } ?: emptySet()
        val screenEvents=IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_USER_PRESENT) }
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,screenEvents,RECEIVER_NOT_EXPORTED) else registerReceiver(receiver,screenEvents)
        main.post(cleanup); checkForeground()
    }
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        if(event==null || !::reader.isInitialized) return
        val pkg=event.packageName?.toString()
        if(event.eventType==AccessibilityEvent.TYPE_WINDOWS_CHANGED && event.windowChanges and (AccessibilityEvent.WINDOWS_CHANGE_REMOVED or AccessibilityEvent.WINDOWS_CHANGE_ADDED)!=0) reader.forgetWindow(event.windowId)
        val structural=event.eventType==AccessibilityEvent.TYPE_WINDOWS_CHANGED || event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if(ChatApps.supported(pkg) && event.eventType in setOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) {
            if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)headerNavigationRevision++
            scheduleHeaderCheck()
        }
        if(ChatApps.supported(pkg) && event.eventType==AccessibilityEvent.TYPE_VIEW_SCROLLED && manualRead.active!=null) {
            cancelScreenRead("读取期间屏幕已滚动，本次读取已取消；已保留上次内容")
        }
        // Some apps/OEMs expose a new application's content/focus before its
        // structural event. Inspect window metadata only, never its text/nodes.
        val supplemental=event.eventType in setOf(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,AccessibilityEvent.TYPE_VIEW_FOCUSED) && pkg!=null && pkg!=packageName && pkg!="com.android.systemui" && pkg !in imePackages && event.windowId!=foregroundWindowId && (wechatForeground || ChatApps.supported(pkg))
        if(!structural && !supplemental)return
        if(!structural) {
            val now=SystemClock.uptimeMillis()
            if(now-lastSupplementalMetadataAt<16)return
            lastSupplementalMetadataAt=now
        }
        val previous=eventWindowMetadata
        val snapshot=windows;eventWindowMetadata=snapshot
        try { inspectForegroundEvent(event,pkg,snapshot) } finally { eventWindowMetadata=previous }
    }
    private fun inspectForegroundEvent(event:AccessibilityEvent,pkg:String?,currentWindows:List<AccessibilityWindowInfo>) {
        foregroundStage(if(event.eventType==AccessibilityEvent.TYPE_WINDOWS_CHANGED)"windows-event" else "state-event")
        val transientWindow=currentWindows.any { it.id==event.windowId && it.type in setOf(android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD,android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) }
        // Event class names describe views as well as Activities. Resolve actual foreground
        // windows before hiding anything; an editor/IME event is not proof of an app switch.
        if(!transientWindow && event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && CaptureEventPolicy.navigationChanged(pkg,event.className?.toString().orEmpty())) {
            reader.recordWindowClass(event.windowId,event.className?.toString().orEmpty())
            // The same Activity regaining focus after an overlay is not a new read.
            // Changed real windows are also checked by updateForeground below.
            if(event.windowId>=0 && foregroundWindowId!=null && event.windowId!=foregroundWindowId) {
                readGate.invalidate();cancelScreenRead("本次屏幕读取已取消；已保留上次内容")
            }
        }
        val apps=currentWindows.filter { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
        val appMetadata=apps.map { AppWindow(it.id,it.isFocused,it.isActive,it.layer) }
        // WINDOWS_CHANGED does not promise a packageName. A validated mapping
        // by focused window ID removes the remote application-root dependency.
        val signal=reader.foregroundSignal(appMetadata,event.windowId,pkg,event.eventType!=AccessibilityEvent.TYPE_WINDOWS_CHANGED && !transientWindow)
        val externalFocused=currentWindows.any { it.isFocused && it.type !in setOf(AccessibilityWindowInfo.TYPE_APPLICATION,AccessibilityWindowInfo.TYPE_INPUT_METHOD,AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) }
        val focusedId=appMetadata.filter { it.focused }.maxByOrNull { it.layer }?.id
        val editor=::overlay.isInitialized && overlay.hasEditorLease()
        val changes=if(event.eventType==AccessibilityEvent.TYPE_WINDOWS_CHANGED)event.windowChanges else 0
        if(recordWindowEvent("${event.eventType}:$changes:${currentWindows.size}:$externalFocused:$editor:$focusedId",SystemClock.uptimeMillis()))
            Logs.event(LogStep.WINDOW_EVENT,metrics=mapOf(LogMetric.EVENT_TYPE to event.eventType.toLong(),LogMetric.WINDOW_CHANGES to changes.toLong(),LogMetric.EVENT_AGE_MS to (SystemClock.uptimeMillis()-event.eventTime).coerceAtLeast(0),LogMetric.WINDOW_COUNT to currentWindows.size.toLong(),LogMetric.EXTERNAL_FOCUSED to if(externalFocused)1L else 0L,LogMetric.EDITOR_ACTIVE to if(editor)1L else 0L)+(focusedId?.let { mapOf(LogMetric.FOCUSED_APP_ID to it.toLong()) } ?: emptyMap()))
        if(signal!=null && !externalFocused) {
            // No app-root IPC and no wait behind a conversation read. Only OS window metadata.
            foregroundGate.invalidate();main.removeCallbacks(debounced)
            val frame=android.graphics.Rect().apply { apps.first { it.id==signal.windowId }.getBoundsInScreen(this) }
            val next=WindowSnapshot(signal.packageName,signal.windowId,RectI(frame.left,frame.top,frame.right,frame.bottom),emptyList())
            if(!canObserve())leaveForeground()
            else if(!ChatApps.supported(signal.packageName) || !frame.isEmpty) {
                foregroundRetries=0
                if(!wechatForeground || foregroundPackage!=next.packageName || foregroundWindowId!=next.windowId || lastForegroundWindow?.viewport!=next.viewport)updateForeground(next,currentWindows)
            } else if(!main.hasCallbacks(debounced))main.post(debounced)
        } else {
            // Even an IME/overlay event can coincide with the app losing foreground.
            // Keep a known app only while focus metadata still proves it; don't wait for root IPC.
            val known=knownForeground(currentWindows)
            if(known!=null && canObserve()) {
                foregroundGate.invalidate();main.removeCallbacks(debounced);foregroundRetries=0
                if(lastForegroundWindow?.viewport!=known.viewport)updateForeground(known,currentWindows)
                else if(transientWindow)overlay.resize()
            } else {
                foregroundStage("window-proof-missing")
                if(wechatForeground)leaveForeground("正在核验前台，已保留已读内容")
                if(!main.hasCallbacks(debounced))main.post(debounced)
            }
        }
    }
    private fun hasForegroundProof(windowId:Int?,currentWindows:List<AccessibilityWindowInfo>):Boolean {
        val apps=currentWindows.filter { it.type==AccessibilityWindowInfo.TYPE_APPLICATION }.map { AppWindow(it.id,it.isFocused,it.isActive,it.layer) }
        val transientFocused=currentWindows.any { it.isFocused && it.type in setOf(AccessibilityWindowInfo.TYPE_INPUT_METHOD,AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) }
        val externalFocused=currentWindows.any { it.isFocused && it.type !in setOf(AccessibilityWindowInfo.TYPE_APPLICATION,AccessibilityWindowInfo.TYPE_INPUT_METHOD,AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) }
        return ForegroundVisibility.canRetain(windowId,apps,transientFocused,externalFocused,windowId==foregroundWindowId && ::overlay.isInitialized && overlay.hasEditorLease())
    }
    private fun knownForeground(currentWindows:List<AccessibilityWindowInfo>):WindowSnapshot? {
        val pkg=foregroundPackage ?: return null
        val id=foregroundWindowId ?: return null
        if(!hasForegroundProof(id,currentWindows))return null
        val current=currentWindows.firstOrNull { it.id==id && it.type==AccessibilityWindowInfo.TYPE_APPLICATION } ?: return null
        val frame=android.graphics.Rect().apply { current.getBoundsInScreen(this) }
        if(frame.isEmpty)return null
        return WindowSnapshot(pkg,id,RectI(frame.left,frame.top,frame.right,frame.bottom),emptyList())
    }
    private fun locked()=getSystemService(KeyguardManager::class.java).isKeyguardLocked || !getSystemService(PowerManager::class.java).isInteractive
    private fun canObserve()=!stopping && CapturePolicy.overlayVisible(graph.settings.load().overlayEnabled,locked(),projecting)
    private fun checkForeground() {
        if(!canObserve()) { leaveForeground(); return }
        val snapshot=windowMetadata()
        val apps=snapshot.filter { it.type==AccessibilityWindowInfo.TYPE_APPLICATION }.map { AppWindow(it.id,it.isFocused,it.isActive,it.layer) }
        val signal=reader.foregroundSignal(apps,-1,null,false)
        if(signal!=null) {
            val frame=android.graphics.Rect().apply { snapshot.first { it.id==signal.windowId }.getBoundsInScreen(this) }
            updateForeground(WindowSnapshot(signal.packageName,signal.windowId,RectI(frame.left,frame.top,frame.right,frame.bottom),emptyList()),snapshot);return
        }
        knownForeground(snapshot)?.let { updateForeground(it,snapshot);return }
        if(wechatForeground)leaveForeground("前台归属已变化；已保留已读内容")
        val lease=foregroundGate.begin() ?: return
        foregroundStage("verify-start")
        foregroundWorker.execute {
            val window=Logs.recover(LogStep.FOREGROUND_ENTER) { reader.foreground() }
            main.post {
                val completion=foregroundGate.complete(lease)
                if(completion.accept && canObserve())updateForeground(window)
                if(completion.readAgain && !main.hasCallbacks(debounced)) main.post(debounced)
            }
        }
    }
    /** True only when this exact snapshot is accepted, independently of retaining known UI. */
    private fun updateForeground(window:WindowSnapshot?,currentWindows:List<AccessibilityWindowInfo> = windowMetadata()):Boolean {
        val accepted=window!=null && (!ChatApps.supported(window.packageName) || hasForegroundProof(window.windowId,currentWindows))
        if(window!=null && !accepted)foregroundStage("late-window-rejected")
        val current=if(accepted)window else knownForeground(currentWindows)
        if(current==null) {
            foregroundStage("root-unconfirmed-hide")
            val retry=foregroundRetries++<2
            // Hide first. Bounded recovery is for re-entry, never permission to linger on home.
            leaveForeground("暂未取得前台窗口；已保留已读内容")
            if(retry && canObserve())main.postDelayed(debounced,80)
            return false
        }
        foregroundRetries=0
        if(!accepted) {
            foregroundStage("root-missing-focus-kept")
            if(lastForegroundWindow?.viewport==current.viewport)return false
        }
        if(!ChatApps.supported(current.packageName)) { diagnosticStatus="当前前台应用不在已启用的聊天应用范围"; leaveForeground(); return false }
        val changedWindow=foregroundWindowId!=null && (foregroundWindowId!=current.windowId || foregroundPackage!=current.packageName)
        if(changedWindow) {
            // Switching directly between supported split panes must not transfer
            // the old editor's keyboard lease to the newly focused application.
            overlay.releaseEditorFocus()
            cancelScreenRead("本次屏幕读取已取消；已保留上次内容")
        }
        val entering=!wechatForeground
        foregroundWindowId=current.windowId;foregroundPackage=current.packageName; wechatForeground=true
        lastForegroundWindow=current
        if((entering || changedWindow) && overlay.hasRetainedPanel())prepareCachedPanel()
        diagnosticStatus="按需读取 · ${ChatApps.label(current.packageName)}窗口 ${current.windowId} · ${current.viewport.width}×${current.viewport.height} · 仅检查前台，未收集聊天正文"
        if(entering) { foregroundStage("show-confirmed-chat");Logs.event(LogStep.FOREGROUND_ENTER,LogState.OK,metrics=mapOf(LogMetric.PLATFORM to platform(current.packageName),LogMetric.WINDOW_ID to current.windowId.toLong())) }
        ensureOverlay(); overlay.resize()
        return accepted
    }
    /** Runs only for the active Read Information gesture, including its vision verification step. */
    private fun observe() {
        val ticket=manualRead.active ?: return
        if(!overlayAllowed()) { leaveForeground(); return }
        val lease=readGate.begin() ?: run { main.removeCallbacks(readPending); main.postDelayed(readPending,120); return }
        val submittedIdentity=identity; val submittedNavigation=navigation;val submittedPackage=foregroundPackage
        val parent=readLog
        val log=Logs.begin(LogStep.READ_NATIVE,parent)
        nativeWorker.execute {
            val w=try { reader.read(submittedIdentity,submittedPackage) } catch(error:Exception) { val fault=LogFault.capture(error);Logs.end(log,LogState.FAIL,fault.reason,fault);null }
            val result=w?.let { Logs.recover(LogStep.READ_PARSE,log) { parser.extract(it) } }
            main.post {
                val completion=readGate.complete(lease)
                if(completion.accept && manualRead.accepts(ticket) && submittedNavigation==navigation && canObserve()) {
                    Logs.end(log,if(w==null)LogState.FAIL else LogState.OK,if(w==null)LogReason.NO_NODES else LogReason.NONE,metrics=mapOf(LogMetric.NODE_COUNT to (w?.nodes?.size ?: 0).toLong()))
                    if(w==null) finishRead(ticket,"系统未返回聊天窗口，请重新点击读取信息")
                    else {
                        if(!updateForeground(w)) {
                            finishRead(ticket,"当前窗口已变化，本次读取已取消；已保留上次内容")
                            return@post
                        }
                        if(manualRead.accepts(ticket) && w.packageName==submittedPackage && ChatApps.supported(w.packageName)) {
                            lastWechatDiagnosticStatus="手动读取 · ${ChatApps.label(w.packageName)}窗口 ${w.windowId} · ${w.viewport.width}×${w.viewport.height} · ${w.nodes.size} 个节点 · ${when(result) { is CaptureResult.Readable -> "原生单聊读取成功"; is CaptureResult.NeedsImage -> result.reason; else -> pagePauseMessage(w) }}"
                            processWindow(w,result ?: CaptureResult.NotChat,ticket)
                        }
                    }
                }
                if(!completion.accept || !manualRead.accepts(ticket) || submittedNavigation!=navigation)Logs.end(log,LogState.CANCELLED,LogReason.RESULT_STALE)
                if(completion.readAgain && !main.hasCallbacks(debounced)) main.postDelayed(debounced,120)
            }
        }
    }
    private fun processWindow(w:WindowSnapshot,result:CaptureResult,ticket:ManualReadGate.Ticket) {
        Logs.event(LogStep.READ_PARSE,if(result==CaptureResult.NotChat)LogState.FAIL else LogState.OK,when(result) { is CaptureResult.NeedsImage->LogReason.NEEDS_VISION;is CaptureResult.Readable->LogReason.NONE;else->LogReason.NO_CHAT },readLog,metrics=mapOf(LogMetric.NODE_COUNT to w.nodes.size.toLong(),LogMetric.WINDOW_ID to w.windowId.toLong(),LogMetric.PLATFORM to platform(w.packageName)))
        val verifying=pendingVision; pendingVision=null
        val title=if(w.packageName==ChatApps.WECHAT)TextSceneParser.title(w.nodes,w.viewport)?.trim() else null
        if(verifying!=null && (verifying.windowId!=w.windowId || verifying.navigation!=navigation || verifying.ticket!=ticket || verifying.cancel.cancelled || title!=null && title!=verifying.result.title)) {
            cancelScreenRead("识别期间窗口变化，本次读取已取消；已保留上次内容"); return
        }
        lastWindow=w; overlay.resize()
        if(verifying!=null && (result is CaptureResult.NeedsImage || result is CaptureResult.Readable)) {
            visualCancel=null
            if(result is CaptureResult.Readable && verifying.result.kind==ChatKind.DIRECT) {
                accept(VisualMediaMerge.merge(result.snapshot,verifying.result,w),ticket,verifying.fillReference);return
            }
            val visual=verifying.result
            val id=identity?.takeIf { it.displayTitle==visual.title && it.kind==visual.kind } ?: ConversationIdentity("chat-${UUID.randomUUID()}",visual.title,visual.kind,true)
            val styleTitle=if(visual.kind==ChatKind.GROUP)GroupTitle.matchingName(visual.title,visual.memberCount) else null
            accept(ChatSnapshot(w.packageName,w.windowId,id,visual.messages,source=CaptureSource.OCR,replyStyleTitle=styleTitle,contextLimit=ConversationContextPolicy.limit(visual.kind,graph.settings.load().groupContextLimit),readAtMillis=visual.readAtMillis),ticket,verifying.fillReference)
            return
        }
        when(result) {
            is CaptureResult.Readable -> {
                if(VisualMediaMerge.hasMedia(w)) captureVision(w,ticket)
                else { visualCancel?.cancel(); visualCancel=null;accept(result.snapshot,ticket) }
            }
            is CaptureResult.NeedsImage -> captureVision(w,ticket)
            else -> finishRead(ticket,pagePauseMessage(w))
        }
    }
    private fun accept(observed:ChatSnapshot,ticket:ManualReadGate.Ticket,fillReference:VisualFillReference?=null) {
        if(!manualRead.accepts(ticket)) return
        Logs.event(LogStep.READ_ACCEPT,LogState.OK,operation=readLog,metrics=mapOf(LogMetric.MESSAGE_COUNT to observed.messages.size.toLong(),LogMetric.CHAT_KIND to if(observed.identity?.kind==ChatKind.GROUP)2L else 1L,LogMetric.HAS_INPUT to if(fillReference?.composer!=null)1L else 0L))
        if(identity!=null && (identity!!.displayTitle!=observed.identity?.displayTitle || !conversation.continues(observed))) {
            conversation.clear(); identity=null
            graph.controller.invalidate("已读取另一段聊天")
        }
        val nativeStyleTitle=if(observed.identity?.kind==ChatKind.GROUP)null else NativeReplyStyleTitle.read(lastWindow?.takeIf { it.windowId==observed.windowId && it.packageName==observed.packageName })
        var snapshot=observed.copy(replyStyleTitle=nativeStyleTitle ?: observed.replyStyleTitle)
        // A title proof belongs only to the manual read that produced it. A native
        // read must never inherit a different conversation's earlier screenshot.
        visualFill=fillReference
        val entered=identity==null
        if(entered) graph.recent.match(observed)?.let { stored -> conversation.restore(stored.copy(windowId=observed.windowId)); snapshot=snapshot.copy(identity=stored.identity) }
        identity=snapshot.identity
        conversation.observe(snapshot,System.currentTimeMillis(),entered)
        val merged=conversation.current ?: run { finishRead(ticket,"未取得可分析的对话，请重新读取"); return }
        visualFill?.let { ref -> merged.identity?.let { savedFillReferences[merged.packageName+":"+it.id]=ref } }
        val proof=fillReference?.viewport?.let { v -> CachedHeaderProof(fillReference.stamp,v.width,v.height,fillReference.conversationStamp) }
        graph.controller.observe(merged,"正在生成六条候选…")
        // Reattach the last successful batch to this proven identity before a new
        // request. A provider error must not erase that saved batch from the panel.
        graph.replySessions.findByIdentity(merged)?.let { saved ->
            if(proof!=null)graph.replySessions.bindHeaderProof(merged,proof)
            val target=saved.target?.takeIf { target -> merged.messages.any { it.id==target.messageId } }
            graph.controller.restore(saved.copy(snapshot=merged,target=target),merged.windowId)
        }
        worker.execute { Logs.recover(LogStep.LOCAL_TASK) { graph.recent.remember(merged) } }
        graph.currentObjectId=null; graph.controller.analyze(merged,objectId=null,diagnosticParent=readLog,headerProof=proof)
        finishRead(ticket)
    }
    private fun captureVision(w:WindowSnapshot,ticket:ManualReadGate.Ticket) {
        if(!manualRead.accepts(ticket)) return
        val config=graph.settings.load()
        val key=config.vision.apiKey.ifBlank { if(ProviderPresets.ownsOpenRouter(config.vision)) ProviderPresets.openRouterKey(config.profile,config.vision) else "" }
        if(key.isBlank()) { finishRead(ticket,"请在设置填写视觉接口密钥，再点击读取信息"); return }
        val cancel=Cancellation(); visualCancel=cancel; val lease=navigation
        val visionLog=Logs.begin(LogStep.VISION_REQUEST,readLog)
        graph.controller.awaitVision(w,"正在读取${ChatApps.label(w.packageName)}气泡与双方…")
        captureWindow(w.windowId,cancel) { image ->
            if(cancel.cancelled || lease!=navigation || !manualRead.accepts(ticket) || locked()) { Logs.end(visionLog,LogState.CANCELLED,LogReason.RESULT_STALE);if(image is CaptureImageResult.Success) image.bitmap.recycle(); return@captureWindow }
            when(image) {
                is CaptureImageResult.Failure -> { Logs.end(visionLog,LogState.FAIL,if(image.protected)LogReason.SCREENSHOT_SECURE else LogReason.SCREENSHOT_FAILED);finishRead(ticket,"${image.message}，请点击读取信息重试") }
                is CaptureImageResult.Success -> worker.execute {
                    var scaledToRecycle:Bitmap?=null
                    try {
                        val original=image.bitmap
                        val scaled=if(original.width>1200) Bitmap.createScaledBitmap(original,1200,(original.height*1200f/original.width).toInt(),true) else original
                        if(scaled!==original)scaledToRecycle=scaled
                        val positionScale=original.height.toDouble()/scaled.height
                        val titleStamp=VisualFillReference.stamp(original)
                        val footerStamp=VisualFillReference.footerStamp(original)
                        val conversationStamp=VisualFillReference.conversationStamp(original)
                        val nativeInput=w.nodes.filter { it.editable && !it.text.orEmpty().contains("搜索") && ImeFillPolicy.composerAllowed(it.bounds,w.viewport) }.map { it.bounds }.distinct().singleOrNull()
                        val localInput=if(nativeInput==null)Logs.recover(LogStep.READ_PARSE,visionLog) {
                            com.loveadvisor.core.input.WechatComposerLocator.locate(w.packageName,original.width,original.height,original::getPixel)?.let { r ->
                                RectI(w.viewport.left+(r.left*w.viewport.width.toDouble()/original.width).toInt(),w.viewport.top+(r.top*w.viewport.height.toDouble()/original.height).toInt(),
                                    w.viewport.left+(r.right*w.viewport.width.toDouble()/original.width).toInt(),w.viewport.top+(r.bottom*w.viewport.height.toDouble()/original.height).toInt())
                            }?.takeIf { ImeFillPolicy.composerAllowed(it,w.viewport) }
                        } else null
                        val bytes=ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG,90,bytes)
                        if(scaled!==original) scaled.recycle(); original.recycle()
                        val readVisual=Logs.within(visionLog) { graph.vision.read(config.vision.copy(apiKey=key),"data:image/jpeg;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP),cancel,w.packageName,config.groupContextLimit,selfDisplayName=config.socialSelfNames[w.packageName]) }
                        val modelInput=readVisual.composer?.let { r -> RectI(w.viewport.left+(r.left*positionScale).toInt(),w.viewport.top+(r.top*positionScale).toInt(),w.viewport.left+(r.right*positionScale).toInt(),w.viewport.top+(r.bottom*positionScale).toInt()) }?.takeIf { ImeFillPolicy.composerAllowed(it,w.viewport) }
                        val input=nativeInput ?: localInput ?: modelInput
                        val visual=readVisual.copy(positions=readVisual.positions.mapValues { (_,y)->w.viewport.top+y*positionScale })
                        cancel.check()
                        main.post {
                            if(!cancel.cancelled && lease==navigation && manualRead.accepts(ticket) && overlayAllowed()) {
                                lastComposerDiagnosticStatus=when { nativeInput!=null -> "原生文字输入区域";localInput!=null -> "本地识别空白文字输入区域";modelInput!=null -> "视觉返回文字输入区域";else -> "本次未确认文字输入区域" }
                                Logs.event(LogStep.VISION_RESULT,LogState.OK,operation=visionLog,metrics=mapOf(LogMetric.MESSAGE_COUNT to visual.messages.size.toLong(),LogMetric.HAS_INPUT to if(input!=null)1L else 0L));Logs.end(visionLog,LogState.OK)
                                pendingVision=VisionReply(visual,w.windowId,cancel,ticket,lease,titleStamp?.let { VisualFillReference(w.windowId,lease,it,input,footerStamp,w.viewport,conversationStamp=conversationStamp) })
                                observe() // One local verification read; no second screenshot or automatic retry.
                            } else Logs.end(visionLog,LogState.CANCELLED,LogReason.RESULT_STALE)
                        }
                    } catch(_:java.util.concurrent.CancellationException) { Logs.end(visionLog,LogState.CANCELLED,LogReason.USER_CANCELLED) }
                    catch(e:Exception) { val fault=LogFault.capture(e);Logs.end(visionLog,LogState.FAIL,fault.reason,fault);main.post { if(!cancel.cancelled && lease==navigation && manualRead.accepts(ticket)) {
                        val reason=graph.safeError(e)
                        lastWechatDiagnosticStatus="视觉读取失败 · $reason"
                        finishRead(ticket,reason)
                    } } }
                    finally { scaledToRecycle?.let { if(!it.isRecycled)it.recycle() };if(!image.bitmap.isRecycled) image.bitmap.recycle() }
                }
            }
        }
    }
    private fun captureWindow(windowId:Int,cancel:Cancellation,callback:(CaptureImageResult)->Unit) {
        val log=Logs.begin(LogStep.SCREENSHOT_REQUEST,fillLog ?: readLog,metrics=mapOf(LogMetric.WINDOW_ID to windowId.toLong(),LogMetric.API_LEVEL to Build.VERSION.SDK_INT.toLong()))
        val detach=Build.VERSION.SDK_INT<34
        val now=SystemClock.elapsedRealtime()
        val deadline=now+5000L
        val delay=CaptureTiming.delay(lastReservedCaptureStart,now,detach)
        lastReservedCaptureStart=now+delay
        if(detach) { overlaySuspensions++;if(::island.isInitialized)island.suspend();overlay.suppress() }
        var completed=false
        fun finish(image:CaptureImageResult) {
            if(completed) { if(image is CaptureImageResult.Success)image.bitmap.recycle();return }
            completed=true
            val expired=SystemClock.elapsedRealtime()>=deadline
            val failure=image as? CaptureImageResult.Failure
            val state=if(cancel.cancelled)LogState.CANCELLED else if(failure!=null || expired)LogState.FAIL else LogState.OK
            val reason=when { cancel.cancelled->LogReason.USER_CANCELLED;failure?.protected==true->LogReason.SCREENSHOT_SECURE;failure!=null || expired->LogReason.SCREENSHOT_FAILED;else->LogReason.NONE }
            Logs.event(LogStep.SCREENSHOT_RESULT,state,reason,log,metrics=mapOf(LogMetric.SCREENSHOT_CODE to (failure?.code ?: 0).toLong()));Logs.end(log,state,reason)
            lastScreenshotDiagnosticStatus=when(image) {
                is CaptureImageResult.Success -> "无障碍截图成功 · API ${Build.VERSION.SDK_INT}"
                is CaptureImageResult.Failure -> "无障碍截图失败 · code=${image.code} · 受保护=${image.protected} · API ${Build.VERSION.SDK_INT}"
            }
            if(detach) { overlaySuspensions--;ensureOverlay() }
            if(cancel.cancelled) { if(image is CaptureImageResult.Success)image.bitmap.recycle() }
            else if(image is CaptureImageResult.Success && SystemClock.elapsedRealtime()>=deadline) { image.bitmap.recycle();callback(CaptureImageResult.Failure(-1,"窗口截图超时，请重试")) }
            else callback(image)
        }
        main.postDelayed({ finish(CaptureImageResult.Failure(-1,"窗口截图超时，请重试")) },5000)
        // Also lets detached legacy overlays leave the compositor before capture.
        lateinit var attempt:Runnable
        attempt=Runnable {
            if(completed)return@Runnable
            if(SystemClock.elapsedRealtime()>=deadline) { finish(CaptureImageResult.Failure(-1,"窗口截图超时，请重试"));return@Runnable }
            if(cancel.cancelled || !overlayAllowed())finish(CaptureImageResult.Failure(-1,"聊天应用前台已变化"))
            else {
                val actualNow=SystemClock.elapsedRealtime()
                val remaining=CaptureTiming.delay(lastActualCaptureStart,actualNow,false)
                if(remaining>0) { main.postDelayed(attempt,remaining);return@Runnable }
                lastActualCaptureStart=actualNow
                lastReservedCaptureStart=maxOf(lastReservedCaptureStart,actualNow)
                try { WindowCapture(this).capture(windowId,cancel) { finish(it) } } catch(error:Exception) { Logs.failed(LogStep.SCREENSHOT_RESULT,error,log);finish(CaptureImageResult.Failure(-1,"系统暂未允许窗口截图")) }
            }
        }
        main.postDelayed(attempt,delay)
    }
    private fun finishRead(ticket:ManualReadGate.Ticket,message:String?=null) {
        if(!manualRead.finish(ticket)) return
        releaseReadLearning?.invoke();releaseReadLearning=null
        Logs.event(LogStep.READ_FINISH,if(message==null)LogState.OK else LogState.FAIL,if(message==null)LogReason.NONE else Logs.reason(message),readLog)
        Logs.end(readLog,if(message==null)LogState.OK else LogState.FAIL,if(message==null)LogReason.NONE else Logs.reason(message));readLog=null
        visualCancel?.cancel(); visualCancel=null; pendingVision=null; main.removeCallbacks(readPending)
        overlay.setReading(false)
        if(message!=null) { graph.controller.awaitCapture(message);graph.controller.status(message,failure=true) }
    }
    fun parse(window:WindowSnapshot)=parser.extract(window)
    fun readConfirmedWindow()=reader.read(identity)
    fun readFillWindow()=reader.readWithTargets(identity)
    fun currentForFill(window:WindowSnapshot):ChatSnapshot? {
        val current=currentChatSnapshot ?: return null
        if(!ChatApps.supported(window.packageName) || window.packageName!=current.packageName || window.windowId!=current.windowId || window.confirmedIdentity?.id!=current.identity?.id) return null
        val title=current.identity?.displayTitle
        if(title.isNullOrBlank() || NativeReplyStyleTitle.read(window)!=title) return null
        val parsed=parser.extract(window)
        if(parsed is CaptureResult.Readable) {
            // Keep the manually read/media-enriched snapshot; verification must not erase media or mutate its token.
            return current
        }
        return if((current.source==CaptureSource.OCR || current.source==CaptureSource.PASTE && conversation.current?.source==CaptureSource.OCR) && overlayAllowed() && (window.packageName!=ChatApps.WECHAT || WeChatPageClassifier.classify(window) in setOf(WeChatPageKind.CHAT_OR_UNKNOWN,WeChatPageKind.GROUP))) current else null
    }
    fun captureNow() {
        cancelCachedHeader?.invoke();cancelCachedHeader=null
        pastedSnapshot=null
        Logs.event(LogStep.READ_TAP)
        if(!overlayAllowed()) { notifyUser("请打开已支持的聊天应用聊天后点击读取信息"); return }
        if(manualRead.active!=null || graph.coordinator.state.loading) { Logs.event(LogStep.READ_TAP,LogState.SKIP,LogReason.BUSY);return }
        readLog=Logs.begin(LogStep.READ_TAP,metrics=mapOf(LogMetric.PLATFORM to platform(foregroundPackage)))
        releaseReadLearning?.invoke();releaseReadLearning=graph.personalMemory.beginForeground()
        manualRead.begin(); overlay.setReading(true); graph.controller.status("正在读取当前聊天屏幕…"); observe()
    }
    fun captureImage() { captureNow() }
    /** Style editor or cache identity check: native header/local OCR, no analysis/network/navigation. */
    fun readStyleNickname(headerOnly:Boolean=false,onVerifiedImage:((VisualFillReference)->Unit)?=null,callback:(String?,String?)->Unit):()->Unit {
        val log=Logs.begin(if(headerOnly)LogStep.REPLY_CACHE_RESTORE else LogStep.LOCAL_TASK)
        if(!headerOnly)Logs.event(LogStep.PAGE_OPEN,reason=LogReason.PAGE_REPLY_STYLE,operation=log)
        val cancel=Cancellation()
        val pkg=foregroundPackage;val windowId=foregroundWindowId;val generation=navigation
        val foregroundMetadata=lastForegroundWindow
        val previous=conversation.current;val previousImage=visualFill
        var completed=false
        fun current()=!cancel.cancelled && instance===this && overlayAllowed() && generation==navigation && pkg==foregroundPackage && windowId==foregroundWindowId
        fun complete(resultPackage:String?,title:String?,localOcr:Boolean=false) {
            if(completed || cancel.cancelled)return
            completed=true;cancel.cancel();Logs.end(log,if(resultPackage==null)LogState.CANCELLED else if(title==null)LogState.FAIL else LogState.OK,if(resultPackage==null)LogReason.RESULT_STALE else if(title==null)LogReason.NO_NODES else LogReason.NONE);lastStyleNicknameFromOcr=localOcr;callback(resultPackage,title)
        }
        fun finish(title:String?,localOcr:Boolean=false,reference:VisualFillReference?=null) {
            if(!current()) { complete(null,null);return }
            nativeWorker.execute {
                val fresh=Logs.recover(LogStep.FOREGROUND_ENTER,log) { reader.foreground() }
                main.post {
                    val valid=current() && fresh!=null && fresh.packageName==pkg && fresh.windowId==windowId
                    if(valid && title!=null && reference!=null)onVerifiedImage?.invoke(reference)
                    complete(if(valid)pkg else null,title,localOcr)
                }
            }
        }
        if(pkg==null || windowId==null || !current()) { main.post { complete(null,null) };return { cancel.cancel();Logs.end(log,LogState.CANCELLED,LogReason.USER_CANCELLED) } }
        main.postDelayed({ if(!completed && !cancel.cancelled)complete(if(current())pkg else null,null) },6500)
        nativeWorker.execute {
            val window=Logs.recover(LogStep.READ_NATIVE,log) { if(headerOnly)reader.readHeader(pkg) else reader.read(expectedPackage=pkg) }
                ?: foregroundMetadata?.takeIf { headerOnly && it.packageName==pkg && it.windowId==windowId && it.viewport.width>0 && it.viewport.height>0 }
            val native=NativeReplyStyleTitle.read(window)
            main.post {
                if(!current() || window==null || window.windowId!=windowId || window.packageName!=pkg) { complete(null,null);return@post }
                if(native!=null) { finish(native);return@post }
                val bars=getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top
                val crop=ReplyStyleHeader.crop(pkg,window.viewport,bars,resources.displayMetrics.density)
                if(crop==null) { finish(null);return@post }
                captureWindow(windowId,cancel) { image ->
                    if(image !is CaptureImageResult.Success) { finish(null);return@captureWindow }
                    val original=image.bitmap
                    if(!current()) { original.recycle();complete(null,null);return@captureWindow }
                    nativeWorker.execute imageWork@{
                        if(!current()) { original.recycle();main.post { complete(null,null) };return@imageWork }
                        var cropped:Bitmap?=null
                        var ocr:MlKitOcr?=null
                        try {
                            // Reuse the already-read nickname only after a fresh title-strip match.
                            // This keeps the confirmed correction usable on Refresh without a second OCR spelling.
                            val known=if(headerOnly)previous?.identity?.displayTitle else previous?.replyStyleTitle ?: previous?.identity?.displayTitle
                            if(known!=null && previous!=null && previous===conversation.current && previous.packageName==pkg && previous.windowId==windowId &&
                                previousImage!=null && previousImage===visualFill && previousImage.windowId==windowId && previousImage.generation==generation &&
                                VisualFillReference.stamp(original)==previousImage.stamp &&
                                (!graph.replySessions.hasAmbiguousTitle(pkg,known) || previousImage.conversationStamp!=null && previousImage.conversationStamp==VisualFillReference.conversationStamp(original))) {
                                original.recycle();finish(known,localOcr=previous.source==CaptureSource.OCR);return@imageWork
                            }
                            val v=window.viewport
                            // Rebuild ephemeral fill evidence from this same locally verified image.
                            // No old input coordinates or raw images are loaded from persistent cache.
                            val reference=if(headerOnly)VisualFillReference.stamp(original)?.let { stamp ->
                                val nativeInput=window.nodes.filter { it.editable && ImeFillPolicy.composerAllowed(it.bounds,v) }.map { it.bounds }.distinct().singleOrNull()
                                val localInput=if(nativeInput==null)com.loveadvisor.core.input.WechatComposerLocator.locate(pkg,original.width,original.height,original::getPixel)?.let { r ->
                                    RectI(v.left+(r.left*v.width.toDouble()/original.width).toInt(),v.top+(r.top*v.height.toDouble()/original.height).toInt(),v.left+(r.right*v.width.toDouble()/original.width).toInt(),v.top+(r.bottom*v.height.toDouble()/original.height).toInt())
                                }?.takeIf { ImeFillPolicy.composerAllowed(it,v) } else null
                                VisualFillReference(windowId,generation,stamp,nativeInput ?: localInput,VisualFillReference.footerStamp(original),v,conversationStamp=VisualFillReference.conversationStamp(original))
                            } else null
                            val cached=if(reference!=null && pkg!=null)graph.replySessions.findByHeaderProof(pkg,CachedHeaderProof(reference.stamp,v.width,v.height,reference.conversationStamp)) else null
                            if(cached!=null) {
                                Logs.event(LogStep.REPLY_CACHE_RESTORE,LogState.STEP,LogReason.CACHE_HEADER_PROOF_MATCH,log)
                                original.recycle();finish(cached.snapshot.identity?.displayTitle,localOcr=true,reference=reference);return@imageWork
                            }
                            val x=((crop.left-v.left)*original.width.toDouble()/v.width).toInt().coerceIn(0,original.width-1)
                            val y=((crop.top-v.top)*original.height.toDouble()/v.height).toInt().coerceIn(0,original.height-1)
                            val right=((crop.right-v.left)*original.width.toDouble()/v.width).toInt().coerceIn(x+1,original.width)
                            val bottom=((crop.bottom-v.top)*original.height.toDouble()/v.height).toInt().coerceIn(y+1,original.height)
                            val header=Bitmap.createBitmap(original,x,y,right-x,bottom-y);cropped=header
                            if(header!==original)original.recycle()
                            val recognizer=MlKitOcr();ocr=recognizer
                            val cachedEntries=if(headerOnly)graph.replySessions.all().filter { it.snapshot.packageName==pkg } else emptyList()
                            val cachedTitles=cachedEntries.mapNotNull { it.snapshot.identity?.displayTitle }
                            recognizer.recognize(header,cancel,{ boxes,_ ->
                                val matched=boxes?.let { ReplyStyleHeader.cachedTitle(it,cachedTitles,header.width,header.height) }
                                val title=matched ?: boxes?.let { ReplyStyleHeader.title(it) }
                                if(headerOnly)Logs.event(LogStep.REPLY_CACHE_RESTORE,LogState.STEP,if(matched!=null)LogReason.CACHE_HEADER_OCR_MATCH else LogReason.CACHE_HEADER_OCR_MISS,log,metrics=mapOf(LogMetric.COUNT to (boxes?.size ?: 0).toLong(),LogMetric.RESULT_COUNT to cachedTitles.size.toLong(),LogMetric.CACHE_DUPLICATE_COUNT to cachedTitles.groupingBy { it }.eachCount().values.sumOf { (it-1).coerceAtLeast(0) }.toLong(),LogMetric.CACHE_PROOF_COUNT to cachedEntries.count { it.headerProof?.conversationStamp!=null }.toLong()))
                                finish(title,localOcr=true,reference=reference)
                            },{
                                if(!header.isRecycled)header.recycle();recognizer.close()
                            },lines=headerOnly)
                        } catch(error:Exception) {
                            Logs.failed(LogStep.READ_PARSE,error,log)
                            cropped?.let { if(!it.isRecycled)it.recycle() };if(!original.isRecycled)original.recycle();ocr?.close();finish(null)
                        }
                    }
                }
            }
        }
        return { cancel.cancel();Logs.end(log,LogState.CANCELLED,LogReason.USER_CANCELLED) }
    }
    /** Opening the panel may perform one local title-strip OCR, never a chat/vision/model request. */
    fun prepareCachedPanel() {
        if(!graph.replySessions.hasEntries() || manualRead.active!=null || graph.coordinator.state.loading || cancelCachedHeader!=null || !overlayAllowed())return
        val pkg=foregroundPackage ?: return
        val id=foregroundWindowId ?: return
        val lease=navigation
        val revision=headerNavigationRevision
        val check=graph.controller.beginPanelRestore(pkg,id)
        var freshFill:VisualFillReference?=null
        cancelCachedHeader=readStyleNickname(headerOnly=true,onVerifiedImage={ freshFill=it }) { actual,title ->
            cancelCachedHeader=null
            retryHeaderIfRequested()
            if(lease!=navigation || revision!=headerNavigationRevision || pkg!=foregroundPackage || id!=foregroundWindowId || !overlayAllowed())return@readStyleNickname
            val verified=title.takeIf { actual==pkg }
            val proof=freshFill?.viewport?.let { v -> CachedHeaderProof(freshFill!!.stamp,v.width,v.height,freshFill!!.conversationStamp) }
            val result=graph.controller.completePanelRestore(check,verified,nativeTitle=!lastStyleNicknameFromOcr,proof=proof)
            when(result) {
                PanelReplyRestoration.Result.RESTORED -> graph.tracker.lastSnapshot?.let { applyCachedSnapshot(it,freshFill) }
                PanelReplyRestoration.Result.RETAINED -> {
                    // Only a matching verified title may replace ephemeral fill evidence.
                    if(verified!=null && verified==identity?.displayTitle) {
                        val reference=freshFill ?: visualFill?.takeIf { it.windowId==id && (it.viewport==null || it.viewport==lastForegroundWindow?.viewport) }?.copy(generation=navigation)
                        reference?.let { visualFill=it;savedFillReferences[pkg+":"+identity?.id]=it }
                    }
                }
                PanelReplyRestoration.Result.UNAVAILABLE -> { conversation.clear();identity=null;pastedSnapshot=null;visualFill=null }
                PanelReplyRestoration.Result.STALE -> return@readStyleNickname
            }
            val currentSnapshot=graph.tracker.lastSnapshot
            if(verified!=null && verified==currentSnapshot?.identity?.displayTitle && freshFill?.viewport!=null) {
                val ref=freshFill!!;val viewport=ref.viewport!!
                graph.replySessions.bindHeaderProof(currentSnapshot,CachedHeaderProof(ref.stamp,viewport.width,viewport.height,ref.conversationStamp))
            }
            Logs.event(LogStep.REPLY_CACHE_RESTORE,if(result==PanelReplyRestoration.Result.UNAVAILABLE)LogState.SKIP else LogState.OK,
                reason=when {
                    result==PanelReplyRestoration.Result.UNAVAILABLE -> LogReason.CACHE_TITLE_MISS
                    result==PanelReplyRestoration.Result.RETAINED && verified!=identity?.displayTitle -> LogReason.CACHE_TITLE_UNVERIFIED
                    result==PanelReplyRestoration.Result.RETAINED -> LogReason.CACHE_CURRENT_RETAINED
                    else -> LogReason.NONE
                },metrics=mapOf(LogMetric.RESULT_COUNT to (graph.coordinator.state.current?.batch?.replies?.size ?: 0).toLong()))
            graph.controller.publish()
        }
    }
    private fun applyCachedSnapshot(snapshot:ChatSnapshot,freshFill:VisualFillReference?=null) {
        conversation.restore(snapshot);identity=snapshot.identity;pastedSnapshot=null
        val pkg=snapshot.packageName
        visualFill=freshFill ?: savedFillReferences[pkg+":"+snapshot.identity?.id]?.takeIf { it.viewport==null || it.viewport==lastForegroundWindow?.viewport }?.copy(windowId=snapshot.windowId,generation=navigation)
        visualFill?.let { savedFillReferences[pkg+":"+snapshot.identity?.id]=it }
    }
    private fun restoreCached(pkg:String?,title:String,windowId:Int?,freshFill:VisualFillReference?=null):Boolean {
        if(pkg==null || windowId==null)return false
        val saved=graph.replySessions.find(pkg,title) ?: return false
        val snapshot=saved.snapshot.copy(windowId=windowId)
        applyCachedSnapshot(snapshot,freshFill)
        val restored=graph.controller.restore(saved,windowId)
        if(restored) { graph.replySessions.touch(saved);Logs.event(LogStep.REPLY_CACHE_RESTORE,LogState.OK,metrics=mapOf(LogMetric.CACHED to 1L)) }
        return restored
    }
    /** Content changes use metadata only. Navigation can verify a missing title via one local crop. */
    private fun checkCachedHeader(allowTitleOcr:Boolean=false) {
        if(!wechatForeground || !graph.replySessions.hasEntries() || manualRead.active!=null || graph.coordinator.state.loading || headerCheckPending || cancelCachedHeader!=null || !::overlay.isInitialized || overlay.hasEditorLease())return
        val now=SystemClock.uptimeMillis();if(now-lastHeaderCheck<1500) { if(!main.hasCallbacks(headerRetry))main.postDelayed(headerRetry,1500-(now-lastHeaderCheck));return }
        lastHeaderCheck=now;headerCheckPending=true
        val pkg=foregroundPackage;val id=foregroundWindowId;val lease=navigation
        // Do not queue node traversal ahead of the foreground hide/show metadata path.
        nativeWorker.execute {
            val header=Logs.recover(LogStep.WINDOW_LOOKUP) { reader.readHeader(pkg) }
            val title=NativeReplyStyleTitle.read(header)
            main.post {
                headerCheckPending=false
                if(allowTitleOcr && title==null && lease==navigation && pkg==foregroundPackage && id==foregroundWindowId && overlayAllowed() && overlay.hasRetainedPanel()) {
                    prepareCachedPanel();return@post
                }
                if(lease==navigation && pkg==foregroundPackage && id==foregroundWindowId && overlayAllowed() && header?.windowId==id && title!=null && title!=identity?.displayTitle) {
                    if(!restoreCached(pkg,title,id)) {
                        conversation.clear();identity=null;pastedSnapshot=null;visualFill=null
                        graph.controller.invalidate("此对象暂无已读候选；其他对象候选已保留")
                    }
                }
                retryHeaderIfRequested()
            }
        }
    }
    fun analyzePasted(text:String) {
        if(!overlayAllowed() || manualRead.active!=null || graph.coordinator.state.loading) { notifyUser("请等待当前操作完成，并保持聊天应用前台");return }
        val base=conversation.current?.takeIf { it.packageName==foregroundPackage && it.windowId==foregroundWindowId && it.identity?.id==identity?.id }
        val snapshot=try { PastedConversation.create(text,packageName,base) } catch(error:IllegalArgumentException) { notifyUser(error.message.orEmpty());return }
        Logs.event(LogStep.PASTE_REPLY_TAP,metrics=mapOf(LogMetric.COUNT to text.length.toLong(),LogMetric.CACHED to if(base!=null)1L else 0L))
        pastedSnapshot=snapshot
        graph.controller.analyze(snapshot,goal="回复用户粘贴的这句原话。没有读取完整对话，不假造语境或称呼，不把它当作已确认的真实聊天记录。",objectId=null)
    }
    fun refresh() {
        Logs.event(LogStep.REFRESH_TAP)
        val snapshot=currentChatSnapshot
        val token=graph.tracker.currentToken
        if(!overlayAllowed() || snapshot==null || token==null || graph.tracker.lastSnapshot!=snapshot || token.packageName!=snapshot.packageName || token.conversationId!=snapshot.identity?.id) { graph.controller.status("请先点击读取信息，再刷新候选"); return }
        if(manualRead.active!=null || graph.coordinator.state.loading) return
        graph.currentObjectId=null; graph.controller.analyze(snapshot,objectId=null,replyTarget=graph.coordinator.targetFor(snapshot))
    }
    fun replyTo(target:com.loveadvisor.core.model.ReplyTarget?) {
        Logs.event(LogStep.TARGET_REPLY_TAP)
        val snapshot=conversation.current ?: return notifyUser("请先读取当前对话")
        if(!overlayAllowed() || snapshot.identity?.kind !in setOf(ChatKind.DIRECT,ChatKind.GROUP) || graph.tracker.lastSnapshot!=snapshot) { notifyUser("请重新读取当前对话");return }
        if(target!=null && snapshot.messages.count { it.id==target.messageId && it.speaker==Speaker.OTHER }!=1) { notifyUser("请选择已读的对方消息");return }
        graph.controller.analyze(snapshot,objectId=null,replyTarget=target)
    }
    fun describe(plan:ReplyPlan,token:SessionToken,callback:(com.loveadvisor.core.advisor.ReplyDetail?,String?)->Unit) {
        val snapshot=currentChatSnapshot ?: return callback(null,"请先读取当前聊天")
        if(!graph.tracker.isCurrent(token))return callback(null,"聊天已变化，请重新读取")
        val cancel=Cancellation()
        val sourceBatch=graph.coordinator.state.current?.batch
        val releaseLearning=graph.personalMemory.beginForeground()
        val log=Logs.begin(LogStep.ADVICE_REQUEST)
        worker.execute {
            var error:Exception?=null
            val detail=try { Logs.within(log) { graph.automaticAdvisor.detail(graph.inputFor(snapshot,"","",null,plan.targetMessageId?.let(::ReplyTarget)).let { input -> if(sourceBatch==null)input else input.copy(naturalRepliesEnabled=sourceBatch.naturalRepliesEnabled,realisticRepliesEnabled=sourceBatch.realisticRepliesEnabled,personalContext=sourceBatch.personalContext) },plan,cancel) } } catch(e:Exception) { error=e;Logs.failed(LogStep.ADVICE_RESULT,e,log);null } finally { releaseLearning() }
            main.post {
                if(graph.tracker.isCurrent(token) && graph.coordinator.state.current?.batch?.replies?.any { it===plan }==true) { Logs.end(log,if(detail!=null)LogState.OK else LogState.FAIL,if(detail!=null)LogReason.NONE else LogReason.STEP_FAILED);callback(detail,error?.let { graph.safeError(it) }) }
                else Logs.end(log,LogState.CANCELLED,LogReason.RESULT_STALE)
            }
        }
    }
    fun confirmDraft(snapshot:ChatSnapshot) { if(ChatApps.supported(snapshot.packageName)) accept(snapshot,manualRead.begin()) }
    fun captureGeneration()=navigation
    fun interactionForPanel(callback:(com.loveadvisor.core.memory.InteractionPresentation)->Unit) {
        val snapshot=currentChatSnapshot;val token=graph.tracker.currentToken
        if(snapshot==null || token==null) { callback(com.loveadvisor.core.memory.InteractionPresentation());return }
        worker.execute {
            val value=try { com.loveadvisor.core.memory.InteractionPresenter.build(graph.personalMemory.repository.state(),snapshot,graph.coordinator.targetFor(snapshot)) } catch(error:Exception) { Logs.failed(LogStep.MEMORY_RETRIEVE,error);com.loveadvisor.core.memory.InteractionPresentation() }
            main.post { if(instance===this && graph.tracker.isCurrent(token))callback(value) }
        }
    }
    fun fillDiagnostic(message:String,reset:Boolean=false) {
        if(reset) { fillTrace.begin();fillLog=Logs.begin(LogStep.FILL_TAP) }
        lastFillDiagnosticStatus=message
    }
    fun finishFillLog(success:Boolean,message:String,cancelled:Boolean=false) {
        val state=if(success)LogState.OK else if(cancelled)LogState.CANCELLED else LogState.FAIL
        val reason=if(success)LogReason.NONE else if(cancelled)LogReason.USER_CANCELLED else Logs.reason(message)
        Logs.event(LogStep.FILL_RESULT,state,reason,fillLog);Logs.end(fillLog,state,reason);fillLog=null
    }
    fun currentFillOperation()=fillLog
    /** Fixed stage labels and editor flags only; never record drafts, candidate text or names. */
    fun fillStage(stage:String) { fillStageRecorder()(stage) }
    fun fillStageRecorder():(String)->Unit {
        val recorder=fillTrace.recorder();val log=fillLog
        return { stage -> recorder(stage);Logs.event(LogStep.FILL_STAGE,reason=when(stage.substringBefore(' ')) {
            "native-unavailable"->LogReason.NATIVE_INPUT_UNAVAILABLE;"ime-method-absent"->LogReason.IME_METHOD_ABSENT;"ime-editor-absent"->LogReason.IME_EDITOR_ABSENT;"ime-other-package"->LogReason.IME_OTHER_PACKAGE;"ime-editor-rejected"->LogReason.IME_EDITOR_REJECTED;"ime-connection-absent"->LogReason.IME_CONNECTION_ABSENT;"ime-connection-ready"->LogReason.IME_CONNECTION_READY;"focus-requested"->LogReason.FOCUS_REQUESTED;"focus-rejected"->LogReason.FOCUS_REJECTED;"focus-cancelled"->LogReason.FOCUS_CANCELLED;"focus-timed_out"->LogReason.FOCUS_TIMED_OUT;"title-mismatch"->LogReason.TITLE_MISMATCH;"composer-layout-mismatch"->LogReason.COMPOSER_LAYOUT_MISMATCH;else->LogReason.STEP_FAILED
        },operation=log) }
    }
    /** Local screenshot verifies the manually read title; no model call or new chat collection. */
    fun verifyVisualFill(token:SessionToken,requireComposerLayout:Boolean=false,callback:(Boolean)->Unit) {
        val ref=visualFill
        val recordStage=fillStageRecorder()
        val liveWindow=foregroundWindowId;val lease=navigation
        if(ref==null || liveWindow==null || foregroundPackage!=token.packageName || !overlayAllowed() || !graph.tracker.sameConversation(token)) { callback(false);return }
        val cancel=Cancellation();var completed=false
        fun finish(value:Boolean) { if(!completed) { completed=true;cancel.cancel();callback(value) } }
        main.postDelayed({ finish(false) },5000)
        captureWindow(liveWindow,cancel) { image ->
            val matches=if(image is CaptureImageResult.Success) {
                try {
                    val titleMatches=VisualFillReference.stamp(image.bitmap)==ref.stamp
                    val layoutMatches=!requireComposerLayout || ref.viewport==lastForegroundWindow?.viewport && ref.footerStamp!=null && VisualFillReference.footerStamp(image.bitmap)==ref.footerStamp
                    if(!titleMatches)recordStage("title-mismatch")
                    else if(!layoutMatches)recordStage("composer-layout-mismatch")
                    titleMatches && layoutMatches
                } finally { image.bitmap.recycle() }
            } else { recordStage("verify-screenshot-failed code=${(image as CaptureImageResult.Failure).code}");false }
            finish(matches && ref===visualFill && lease==navigation && liveWindow==foregroundWindowId && foregroundPackage==token.packageName && overlayAllowed() && graph.tracker.sameConversation(token))
        }
    }
    fun focusVisualComposer(token:SessionToken,callback:(ComposerFocusResult)->Unit) {
        val ref=visualFill;val input=ref?.composer ?: ref?.keyboardButton
        val lease=navigation
        if(foregroundPackage!=token.packageName || !overlayAllowed() || !graph.tracker.sameConversation(token)) { callback(ComposerFocusResult.CONTEXT_CHANGED);return }
        // Never tap a stale bottom coordinate when the IME has already changed the layout.
        if(windows.any { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }) { callback(ComposerFocusResult.KEYBOARD_VISIBLE);return }
        if(ref==null) { callback(ComposerFocusResult.NO_REFERENCE);return }
        if(input==null) { callback(ComposerFocusResult.NO_COMPOSER);return }
        verifyVisualFill(token,requireComposerLayout=true) { verified ->
        if(ref!==visualFill || lease!=navigation || !overlayAllowed()) { callback(ComposerFocusResult.CONTEXT_CHANGED);return@verifyVisualFill }
        if(windows.any { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }) { callback(ComposerFocusResult.KEYBOARD_VISIBLE);return@verifyVisualFill }
        if(!verified) { callback(ComposerFocusResult.UNVERIFIED_LAYOUT);return@verifyVisualFill }
        val path=Path().apply { moveTo((input.left+input.right)/2f,(input.top+input.bottom)/2f) }
        overlaySuspensions++;if(::island.isInitialized)island.suspend();overlay.suppress()
        val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,80)).build()
        var completed=false
        fun finish(value:ComposerFocusResult) { if(!completed) { completed=true;overlaySuspensions--;ensureOverlay();callback(if(lease==navigation && overlayAllowed())value else ComposerFocusResult.CONTEXT_CHANGED) } }
        main.postDelayed({ finish(ComposerFocusResult.TIMED_OUT) },2000)
        val submitted=dispatchGesture(gesture,object:GestureResultCallback() {
            override fun onCompleted(gestureDescription:GestureDescription?) { finish(ComposerFocusResult.REQUESTED) }
            override fun onCancelled(gestureDescription:GestureDescription?) { finish(ComposerFocusResult.CANCELLED) }
        },main)
        if(!submitted)finish(ComposerFocusResult.REJECTED)
        }
    }
    fun canCapture()=!stopping && graph.settings.load().overlayEnabled && !locked()
    fun overlayAllowed()=!stopping && CapturePolicy.overlayVisible(graph.settings.load().overlayEnabled,locked(),projecting,wechatForeground)
    fun ensureOverlay() { if(::overlay.isInitialized) {
        if(overlayAllowed() && overlaySuspensions==0) {
            if(graph.settings.load().topCapsuleEnabled) {
                if(::island.isInitialized && island.lease!=null)island.suspend()
                overlay.setEntryMode(com.loveadvisor.core.overlay.OverlayEntryMode.TOP_CAPSULE)
            } else if(::island.isInitialized)island.refreshEligibility(islandEnvironment())
            overlay.showBubble()
        } else {
            if(::island.isInitialized)island.suspend()
            if(overlaySuspensions>0)overlay.suppress() else overlay.suspendForBackground()
        }
    } }
    fun islandReading(value:Boolean) { if(::island.isInitialized)island.updateStatus(value,graph.coordinator.state,graph.coordinator.state.current?.let { graph.tracker.sameConversation(it.ticket.session) }==true) }
    fun showFloating() { graph.settings.update { it.copy(overlayEnabled=true) }; checkForeground() }
    fun settingsChanged() { if(::island.isInitialized && !graph.settings.load().nativeIslandEnabled) { if(graph.settings.load().topCapsuleEnabled)island.suspend() else island.stop() };if(!canObserve()) leaveForeground() else checkForeground() }
    fun stopAssistance() {
        Logs.event(LogStep.UI_ACTION,reason=LogReason.UI_SERVICE_STOP)
        stopping=true
        graph.controller.awaitCapture("辅助服务已停止；已保留已读内容")
        stopService(Intent(this,ProjectionService::class.java))
        if(::island.isInitialized)island.stop()
        leaveForeground("辅助服务已停止")
        disableSelf()
    }
    fun beginProjectionCapture() { projecting=true; leaveForeground() }
    fun endProjectionCapture() { projecting=false; checkForeground() }
    fun previewOcr(draft:ConversationDraft) { graph.controller.status("请返回聊天应用，点击读取信息获取当前聊天") }
    fun notifyUser(text:String) { if(text.contains("失败") || text.contains("请") || text.contains("未能"))Logs.feedback(text);Toast.makeText(this,text,Toast.LENGTH_SHORT).show() }
    private fun cancelScreenRead(message:String="本次读取已取消；已保留上次内容") {
        releaseReadLearning?.invoke();releaseReadLearning=null
        val interrupted=manualRead.active!=null
        if(interrupted) { Logs.end(readLog,LogState.CANCELLED,Logs.reason(message));readLog=null }
        manualRead.cancel(); main.removeCallbacks(readPending)
        visualCancel?.cancel(); visualCancel=null; pendingVision=null
        lastWindow=null; navigation++
        if(::overlay.isInitialized) overlay.setReading(false)
        if(interrupted)graph.controller.awaitCapture(message)
    }
    private fun leaveForeground(message:String="已保留已读内容，返回后可继续使用") {
        cancelCachedHeader?.invoke();cancelCachedHeader=null
        val wasForeground=wechatForeground
        if(wasForeground) { foregroundStage("hide-foreground-lost");Logs.event(LogStep.FOREGROUND_LEAVE,LogState.OK) }
        wechatForeground=false; foregroundWindowId=null;foregroundPackage=null
        lastForegroundWindow=null
        main.removeCallbacks(debounced);foregroundGate.invalidate();readGate.invalidate();cancelScreenRead(message)
        if(::overlay.isInitialized) { overlay.suspendForBackground();ensureOverlay() }
        if(wasForeground)foregroundStage("overlays-detached")
    }
    private fun pagePauseMessage(w:WindowSnapshot)=when(WeChatPageClassifier.classify(w)) {
        WeChatPageKind.MINI_PROGRAM -> "小程序页面，请进入微信真实聊天再读取"
        WeChatPageKind.WEB_PAGE -> "网页页面，请进入微信真实聊天再读取"
        WeChatPageKind.HOME -> "微信导航页面，请进入真实聊天再读取"
        WeChatPageKind.CONTACT_PROFILE -> "联系人资料页面，请进入真实聊天再读取"
        WeChatPageKind.GROUP -> "请重新读取当前群聊，核对消息与成员"
        WeChatPageKind.INVALID -> "窗口范围暂不可用，请稍后点击读取信息"
        else -> "未识别为可读取的聊天，请重新读取"
    }
    override fun onInterrupt() { Logs.event(LogStep.SERVICE_INTERRUPT,LogState.WARN);stopService(Intent(this,ProjectionService::class.java));if(::island.isInitialized)island.suspend();foregroundGate.invalidate();readGate.invalidate();cancelScreenRead();if(::overlay.isInitialized)overlay.suspendForBackground();Logs.flush() }
    override fun onConfigurationChanged(newConfig:android.content.res.Configuration) { super.onConfigurationChanged(newConfig);if(::overlay.isInitialized)overlay.resize();if(::reader.isInitialized)checkForeground() }
    override fun onDestroy() { Logs.event(LogStep.SERVICE_DESTROY,LogState.OK);stopping=true;stopStartup?.invoke();stopStartup=null;if(::reader.isInitialized)graph.controller.awaitCapture("辅助服务已停止；已保留已读内容");stopService(Intent(this,ProjectionService::class.java));if(::island.isInitialized)island.destroy(); main.removeCallbacksAndMessages(null);foregroundGate.invalidate();readGate.invalidate();cancelScreenRead();if(::overlay.isInitialized)overlay.destroy();worker.shutdownNow();nativeWorker.shutdownNow();foregroundWorker.shutdownNow();Logs.recover(LogStep.SERVICE_DESTROY) { unregisterReceiver(receiver) };if(instance===this)instance=null;Logs.flush();super.onDestroy() }
    companion object { @Volatile var instance:ChatCaptureService?=null; private set }
}
