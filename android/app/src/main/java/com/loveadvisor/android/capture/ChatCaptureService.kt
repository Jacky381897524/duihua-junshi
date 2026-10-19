package com.loveadvisor.android.capture
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.os.*
import android.graphics.Bitmap
import android.util.Base64
import android.view.accessibility.AccessibilityEvent
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
import com.loveadvisor.android.input.VisualFillReference
import com.loveadvisor.android.input.VerifiedInputMethod
import com.loveadvisor.core.input.ImeFillPolicy
import android.accessibilityservice.GestureDescription
import android.graphics.Path

/** Passive events check foreground metadata. Only an explicit gesture reads or analyzes messages. */
class ChatCaptureService:AccessibilityService() {
    val graph get()=AppGraph.get(this)
    lateinit var reader:NodeSnapshotReader; private set
    private lateinit var overlay:OverlayController
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val nativeWorker=Executors.newSingleThreadExecutor { r -> Thread(r,"wechat-native-reader") }
    private val readGate=CaptureReadGate()
    private val manualRead=ManualReadGate()
    private val parser=AutomaticChatParser()
    private val conversation=AutomaticConversation()
    private var identity:ConversationIdentity?=null
    private var lastWindow:WindowSnapshot?=null
    private var foregroundWindowId:Int?=null
    private var wechatForeground=false
    private var foregroundPackage:String?=null
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
            val fresh=runCatching { reader.foreground() }.getOrNull()
            main.post {
                val valid=instance===this && lease===island.lease && lease.generation==navigation && overlayAllowed() && overlaySuspensions==0 && fresh?.packageName==lease.packageName && fresh.windowId==lease.windowId
                complete(valid && island.handleNativeTap(leaseId))
            }
        }
    }
    val currentChatSnapshot get()=conversation.current
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
    private var imePackages=emptySet<String>()
    private var visualCancel:Cancellation?=null
    private var visualSignature:String?=null
    private var visualFill:VisualFillReference?=null
    var lastFillDiagnosticStatus="尚未点击填入"; private set
    var lastScreenshotDiagnosticStatus="本次服务尚未请求截图"; private set
    var lastStyleNicknameFromOcr=false; private set
    private data class VisionReply(val result:VisionConversation,val windowId:Int,val cancel:Cancellation,val ticket:ManualReadGate.Ticket,val navigation:Long,val fillReference:VisualFillReference?)
    private var pendingVision:VisionReply?=null
    var diagnosticStatus="按需读取：尚未检查前台窗口"; private set
    var lastWechatDiagnosticStatus="尚未点击读取信息"; private set
    private val debounced=Runnable { checkForeground() }
    private val readPending=Runnable { observe() }
    private val cleanup=object:Runnable { override fun run() { worker.execute { runCatching { graph.recent.cleanup() } }; main.postDelayed(this,12*3600000L) } }
    private val receiver=object:BroadcastReceiver() { override fun onReceive(context:Context?,intent:Intent?) {
        when(intent?.action) { Intent.ACTION_SCREEN_OFF -> leaveForeground(); Intent.ACTION_USER_PRESENT -> checkForeground() }
    } }
    override fun onServiceConnected() {
        stopping=false
        instance=this; reader=NodeSnapshotReader(this); overlay=OverlayController(this);island=com.loveadvisor.android.island.NativeIslandCoordinator(this,overlay)
        imePackages=runCatching { getSystemService(android.view.inputmethod.InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet() }.getOrDefault(emptySet())
        val screenEvents=IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_USER_PRESENT) }
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,screenEvents,RECEIVER_NOT_EXPORTED) else registerReceiver(receiver,screenEvents)
        main.post(cleanup); checkForeground()
    }
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        if(event==null || !::reader.isInitialized) return
        val pkg=event.packageName?.toString()
        if(event.eventType==AccessibilityEvent.TYPE_WINDOWS_CHANGED && event.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_REMOVED!=0) reader.forgetWindow(event.windowId)
        if(pkg==packageName) {
            val name=event.className?.toString().orEmpty()
            if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && name.startsWith(packageName) && !name.startsWith("$packageName.overlay.")) leaveForeground()
            return
        }
        if(ChatApps.supported(pkg) && event.eventType==AccessibilityEvent.TYPE_VIEW_SCROLLED && manualRead.active!=null) {
            reset("屏幕已滚动，请重新点击读取信息")
        }
        val structural=event.eventType==AccessibilityEvent.TYPE_WINDOWS_CHANGED || event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        // Content/text/scroll events do not collect chat nodes, take screenshots, or start requests.
        if(!structural || !CaptureEventPolicy.shouldObserve(pkg,true,packageName)) return
        if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && CaptureEventPolicy.foregroundAppChanged(pkg,event.className?.toString().orEmpty(),imePackages)) {
            leaveForeground(); return
        }
        if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && CaptureEventPolicy.navigationChanged(pkg,event.className?.toString().orEmpty())) {
            reader.recordWindowClass(event.windowId,event.className?.toString().orEmpty())
            readGate.invalidate(); reset("聊天界面已变化，点击读取信息后更新")
        }
        foregroundRetries=0
        if(!main.hasCallbacks(debounced)) main.postDelayed(debounced,120)
    }
    private fun locked()=getSystemService(KeyguardManager::class.java).isKeyguardLocked || !getSystemService(PowerManager::class.java).isInteractive
    private fun canObserve()=!stopping && CapturePolicy.overlayVisible(graph.settings.load().overlayEnabled,locked(),projecting)
    private fun checkForeground() {
        if(!canObserve()) { leaveForeground(); return }
        val lease=readGate.begin() ?: return
        nativeWorker.execute {
            val window=runCatching { reader.foreground() }.getOrNull()
            main.post {
                val completion=readGate.complete(lease)
                if(completion.accept && canObserve()) updateForeground(window)
                if(completion.readAgain && !main.hasCallbacks(debounced)) main.postDelayed(debounced,120)
            }
        }
    }
    private fun updateForeground(window:WindowSnapshot?) {
        if(window==null) {
            leaveForeground("尚未取得前台窗口，返回聊天应用后再读取")
            if(foregroundRetries++<2) main.postDelayed(debounced,400)
            return
        }
        foregroundRetries=0
        if(!ChatApps.supported(window.packageName)) { diagnosticStatus="当前前台应用不是微信、QQ或抖音"; leaveForeground(); return }
        if(foregroundWindowId!=null && (foregroundWindowId!=window.windowId || foregroundPackage!=window.packageName)) reset("聊天窗口已变化，请点击读取信息")
        foregroundWindowId=window.windowId;foregroundPackage=window.packageName; wechatForeground=true
        lastForegroundWindow=window
        diagnosticStatus="按需读取 · ${ChatApps.label(window.packageName)}窗口 ${window.windowId} · ${window.viewport.width}×${window.viewport.height} · 仅检查前台，未收集聊天正文"
        ensureOverlay(); overlay.resize()
    }
    /** Runs only for the active Read Information gesture, including its vision verification step. */
    private fun observe() {
        val ticket=manualRead.active ?: return
        if(!overlayAllowed()) { leaveForeground(); return }
        val lease=readGate.begin() ?: run { main.removeCallbacks(readPending); main.postDelayed(readPending,120); return }
        val submittedIdentity=identity; val submittedNavigation=navigation;val submittedPackage=foregroundPackage
        nativeWorker.execute {
            val started=SystemClock.elapsedRealtime()
            val w=runCatching { reader.read(submittedIdentity,submittedPackage) }.getOrNull()
            val result=w?.let { parser.extract(it) }
            main.post {
                val completion=readGate.complete(lease)
                if(completion.accept && manualRead.accepts(ticket) && submittedNavigation==navigation && canObserve()) {
                    graph.record("native-read",SystemClock.elapsedRealtime()-started,null,null)
                    if(w==null) finishRead(ticket,"系统未返回微信窗口，请重新点击读取信息")
                    else {
                        updateForeground(w)
                        if(manualRead.accepts(ticket) && w.packageName==submittedPackage && ChatApps.supported(w.packageName)) {
                            lastWechatDiagnosticStatus="手动读取 · ${ChatApps.label(w.packageName)}窗口 ${w.windowId} · ${w.viewport.width}×${w.viewport.height} · ${w.nodes.size} 个节点 · ${when(result) { is CaptureResult.Readable -> "原生单聊读取成功"; is CaptureResult.NeedsImage -> result.reason; else -> pagePauseMessage(w) }}"
                            processWindow(w,result ?: CaptureResult.NotChat,ticket)
                        }
                    }
                }
                if(completion.readAgain && !main.hasCallbacks(debounced)) main.postDelayed(debounced,120)
            }
        }
    }
    private fun processWindow(w:WindowSnapshot,result:CaptureResult,ticket:ManualReadGate.Ticket) {
        val verifying=pendingVision; pendingVision=null
        val title=if(w.packageName==ChatApps.WECHAT)TextSceneParser.title(w.nodes,w.viewport)?.trim() else null
        if(verifying!=null && (verifying.windowId!=w.windowId || verifying.navigation!=navigation || verifying.ticket!=ticket || verifying.cancel.cancelled || title!=null && title!=verifying.result.title)) {
            reset("识别期间聊天窗口已变化，请重新读取"); return
        }
        lastWindow=w; overlay.resize()
        if(verifying!=null && (result is CaptureResult.NeedsImage || result is CaptureResult.Readable)) {
            visualCancel=null
            visualSignature=signature(w)
            visualFill=verifying.fillReference
            if(result is CaptureResult.Readable && verifying.result.kind==ChatKind.DIRECT) {
                accept(VisualMediaMerge.merge(result.snapshot,verifying.result,w),ticket);return
            }
            val visual=verifying.result
            val id=identity?.takeIf { it.displayTitle==visual.title && it.kind==visual.kind } ?: ConversationIdentity("chat-${UUID.randomUUID()}",visual.title,visual.kind,true)
            val styleTitle=if(visual.kind==ChatKind.GROUP)GroupTitle.matchingName(visual.title,visual.memberCount) else null
            accept(ChatSnapshot(w.packageName,w.windowId,id,visual.messages,source=CaptureSource.OCR,replyStyleTitle=styleTitle,contextLimit=ConversationContextPolicy.limit(visual.kind,graph.settings.load().groupContextLimit)),ticket)
            return
        }
        when(result) {
            is CaptureResult.Readable -> {
                if(VisualMediaMerge.hasMedia(w)) captureVision(w,ticket)
                else { visualCancel?.cancel(); visualCancel=null; visualSignature=null; accept(result.snapshot,ticket) }
            }
            is CaptureResult.NeedsImage -> captureVision(w,ticket)
            else -> finishRead(ticket,pagePauseMessage(w))
        }
    }
    private fun accept(observed:ChatSnapshot,ticket:ManualReadGate.Ticket) {
        if(!manualRead.accepts(ticket)) return
        if(identity!=null && (identity!!.displayTitle!=observed.identity?.displayTitle || !conversation.continues(observed))) {
            conversation.clear(); identity=null
            graph.controller.invalidate("已读取另一段聊天")
        }
        val nativeStyleTitle=if(observed.identity?.kind==ChatKind.GROUP)null else NativeReplyStyleTitle.read(lastWindow?.takeIf { it.windowId==observed.windowId && it.packageName==observed.packageName })
        var snapshot=observed.copy(replyStyleTitle=nativeStyleTitle ?: observed.replyStyleTitle)
        val entered=identity==null
        if(entered) graph.recent.match(observed)?.let { stored -> conversation.restore(stored.copy(windowId=observed.windowId)); snapshot=snapshot.copy(identity=stored.identity) }
        identity=snapshot.identity
        conversation.observe(snapshot,System.currentTimeMillis(),entered)
        val merged=conversation.current ?: run { finishRead(ticket,"未取得可分析的对话，请重新读取"); return }
        graph.controller.observe(merged,"正在生成六条候选…")
        worker.execute { runCatching { graph.recent.remember(merged) }.onFailure { graph.record("recent",0,null,"failure") } }
        graph.currentObjectId=null; graph.controller.analyze(merged,objectId=null)
        finishRead(ticket)
    }
    private fun signature(w:WindowSnapshot):String="${w.windowId}:"+w.nodes.filter { !it.editable && !TextSceneParser.isUi(it.text.orEmpty()) }.joinToString("|") { "${it.bounds.left},${it.bounds.top},${it.text}" }
    private fun captureVision(w:WindowSnapshot,ticket:ManualReadGate.Ticket) {
        if(!manualRead.accepts(ticket)) return
        val config=graph.settings.load()
        val key=config.vision.apiKey.ifBlank { if(ProviderPresets.ownsOpenRouter(config.vision)) ProviderPresets.openRouterKey(config.profile,config.vision) else "" }
        if(key.isBlank()) { finishRead(ticket,"请在设置填写视觉接口密钥，再点击读取信息"); return }
        val cancel=Cancellation(); visualCancel=cancel; val lease=navigation; val started=SystemClock.elapsedRealtime()
        visualSignature=signature(w)
        graph.controller.awaitVision(w,"正在读取${ChatApps.label(w.packageName)}气泡与双方…")
        captureWindow(w.windowId,cancel) { image ->
            if(cancel.cancelled || lease!=navigation || !manualRead.accepts(ticket) || locked()) { if(image is CaptureImageResult.Success) image.bitmap.recycle(); return@captureWindow }
            when(image) {
                is CaptureImageResult.Failure -> finishRead(ticket,"${image.message}，请点击读取信息重试")
                is CaptureImageResult.Success -> worker.execute {
                    var scaledToRecycle:Bitmap?=null
                    try {
                        val original=image.bitmap
                        val scaled=if(original.width>1200) Bitmap.createScaledBitmap(original,1200,(original.height*1200f/original.width).toInt(),true) else original
                        if(scaled!==original)scaledToRecycle=scaled
                        val positionScale=original.height.toDouble()/scaled.height
                        val titleStamp=VisualFillReference.stamp(original)
                        val footerStamp=VisualFillReference.footerStamp(original)
                        val bytes=ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG,90,bytes)
                        if(scaled!==original) scaled.recycle(); original.recycle()
                        val readVisual=graph.vision.read(config.vision.copy(apiKey=key),"data:image/jpeg;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP),cancel,w.packageName,config.groupContextLimit)
                        val input=readVisual.composer?.let { r -> RectI(w.viewport.left+(r.left*positionScale).toInt(),w.viewport.top+(r.top*positionScale).toInt(),w.viewport.left+(r.right*positionScale).toInt(),w.viewport.top+(r.bottom*positionScale).toInt()) }?.takeIf { ImeFillPolicy.composerAllowed(it,w.viewport) }
                        val visual=readVisual.copy(positions=readVisual.positions.mapValues { (_,y)->w.viewport.top+y*positionScale })
                        cancel.check()
                        main.post {
                            if(!cancel.cancelled && lease==navigation && manualRead.accepts(ticket) && overlayAllowed()) {
                                graph.record("vision",SystemClock.elapsedRealtime()-started,null,null)
                                pendingVision=VisionReply(visual,w.windowId,cancel,ticket,lease,titleStamp?.let { VisualFillReference(w.windowId,lease,it,input,footerStamp,w.viewport) })
                                observe() // One local verification read; no second screenshot or automatic retry.
                            }
                        }
                    } catch(_:java.util.concurrent.CancellationException) { }
                    catch(e:Exception) { main.post { if(!cancel.cancelled && lease==navigation && manualRead.accepts(ticket)) {
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
            if(cancel.cancelled || !overlayAllowed())finish(CaptureImageResult.Failure(-1,"微信前台已变化"))
            else {
                val actualNow=SystemClock.elapsedRealtime()
                val remaining=CaptureTiming.delay(lastActualCaptureStart,actualNow,false)
                if(remaining>0) { main.postDelayed(attempt,remaining);return@Runnable }
                lastActualCaptureStart=actualNow
                lastReservedCaptureStart=maxOf(lastReservedCaptureStart,actualNow)
                runCatching { WindowCapture(this).capture(windowId,cancel) { finish(it) } }.onFailure { finish(CaptureImageResult.Failure(-1,"系统暂未允许窗口截图")) }
            }
        }
        main.postDelayed(attempt,delay)
    }
    private fun finishRead(ticket:ManualReadGate.Ticket,message:String?=null) {
        if(!manualRead.finish(ticket)) return
        visualCancel?.cancel(); visualCancel=null; pendingVision=null; main.removeCallbacks(readPending)
        overlay.setReading(false)
        if(message!=null) { graph.controller.pauseCapture(message);graph.controller.status(message,failure=true) }
    }
    fun parse(window:WindowSnapshot)=parser.extract(window)
    fun readConfirmedWindow()=reader.read(identity)
    fun readFillWindow()=reader.readWithTargets(identity)
    fun currentForFill(window:WindowSnapshot):ChatSnapshot? {
        val current=conversation.current ?: return null
        if(!ChatApps.supported(window.packageName) || window.packageName!=current.packageName || window.windowId!=current.windowId || window.confirmedIdentity?.id!=current.identity?.id) return null
        val nativeTitle=TextSceneParser.title(window.nodes,window.viewport)?.trim()
        if(nativeTitle!=null && nativeTitle!=current.identity?.displayTitle) return null
        val parsed=parser.extract(window)
        if(parsed is CaptureResult.Readable) {
            // Keep the manually read/media-enriched snapshot; verification must not erase media or mutate its token.
            return current
        }
        return if(current.source==CaptureSource.OCR && overlayAllowed() && (window.packageName!=ChatApps.WECHAT || WeChatPageClassifier.classify(window) in setOf(WeChatPageKind.CHAT_OR_UNKNOWN,WeChatPageKind.GROUP)) && (nativeTitle==current.identity?.displayTitle || signature(window)==visualSignature)) current else null
    }
    fun captureNow() {
        if(!overlayAllowed()) { notifyUser("请打开微信、QQ或抖音聊天后点击读取信息"); return }
        if(manualRead.active!=null || graph.coordinator.state.loading) return
        manualRead.begin(); overlay.setReading(true); graph.controller.status("正在读取当前聊天屏幕…"); observe()
    }
    fun captureImage() { captureNow() }
    /** Explicit style-menu gesture only: native nickname or cropped local OCR, no analysis/network/navigation. */
    fun readStyleNickname(callback:(String?,String?)->Unit):()->Unit {
        val cancel=Cancellation()
        val pkg=foregroundPackage;val windowId=foregroundWindowId;val generation=navigation
        val previous=conversation.current;val previousImage=visualFill
        var completed=false
        fun current()=!cancel.cancelled && instance===this && overlayAllowed() && generation==navigation && pkg==foregroundPackage && windowId==foregroundWindowId
        fun complete(resultPackage:String?,title:String?,localOcr:Boolean=false) {
            if(completed || cancel.cancelled)return
            completed=true;cancel.cancel();lastStyleNicknameFromOcr=localOcr;callback(resultPackage,title)
        }
        fun finish(title:String?,localOcr:Boolean=false) {
            if(!current()) { complete(null,null);return }
            nativeWorker.execute {
                val fresh=runCatching { reader.foreground() }.getOrNull()
                main.post { complete(if(current() && fresh!=null && fresh.packageName==pkg && fresh.windowId==windowId)pkg else null,title,localOcr) }
            }
        }
        if(pkg==null || windowId==null || !current()) { main.post { complete(null,null) };return { cancel.cancel() } }
        main.postDelayed({ if(!completed && !cancel.cancelled)complete(if(current())pkg else null,null) },6500)
        nativeWorker.execute {
            val window=runCatching { reader.read(expectedPackage=pkg) }.getOrNull()
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
                    var cropped:Bitmap?=null
                    var ocr:MlKitOcr?=null
                    try {
                        // Reuse the already-read nickname only after a fresh title-strip match.
                        // This keeps the confirmed correction usable on Refresh without a second OCR spelling.
                        val known=previous?.replyStyleTitle ?: previous?.identity?.displayTitle
                        if(known!=null && previous!=null && previous===conversation.current && previous.packageName==pkg && previous.windowId==windowId &&
                            previousImage!=null && previousImage===visualFill && previousImage.windowId==windowId && previousImage.generation==generation &&
                            VisualFillReference.stamp(original)==previousImage.stamp) {
                            original.recycle();finish(known,localOcr=previous.source==CaptureSource.OCR);return@captureWindow
                        }
                        val v=window.viewport
                        val x=((crop.left-v.left)*original.width.toDouble()/v.width).toInt().coerceIn(0,original.width-1)
                        val y=((crop.top-v.top)*original.height.toDouble()/v.height).toInt().coerceIn(0,original.height-1)
                        val right=((crop.right-v.left)*original.width.toDouble()/v.width).toInt().coerceIn(x+1,original.width)
                        val bottom=((crop.bottom-v.top)*original.height.toDouble()/v.height).toInt().coerceIn(y+1,original.height)
                        val header=Bitmap.createBitmap(original,x,y,right-x,bottom-y);cropped=header
                        if(header!==original)original.recycle()
                        val recognizer=MlKitOcr();ocr=recognizer
                        recognizer.recognize(header,cancel,{ boxes,_ -> finish(boxes?.let { ReplyStyleHeader.title(it) },localOcr=true) },{
                            if(!header.isRecycled)header.recycle();recognizer.close()
                        })
                    } catch(_:Exception) {
                        cropped?.let { if(!it.isRecycled)it.recycle() };if(!original.isRecycled)original.recycle();ocr?.close();finish(null)
                    }
                }
            }
        }
        return { cancel.cancel() }
    }
    fun refresh() {
        val snapshot=conversation.current
        val token=graph.tracker.currentToken
        if(!overlayAllowed() || snapshot==null || snapshot.windowId!=foregroundWindowId || token==null || token.packageName!=snapshot.packageName || token.windowId!=snapshot.windowId || token.conversationId!=snapshot.identity?.id) { graph.controller.status("请先点击读取信息，再刷新候选"); return }
        if(manualRead.active!=null || graph.coordinator.state.loading) return
        graph.currentObjectId=null; graph.controller.analyze(snapshot,objectId=null,replyTarget=graph.coordinator.targetFor(snapshot))
    }
    fun replyTo(target:com.loveadvisor.core.model.ReplyTarget?) {
        val snapshot=conversation.current ?: return notifyUser("请先读取当前群聊")
        if(!overlayAllowed() || snapshot.identity?.kind!=ChatKind.GROUP || graph.tracker.lastSnapshot!=snapshot) { notifyUser("请重新读取当前群聊");return }
        graph.controller.analyze(snapshot,objectId=null,replyTarget=target)
    }
    fun describe(plan:ReplyPlan,token:SessionToken,callback:(com.loveadvisor.core.advisor.ReplyDetail?,String?)->Unit) {
        val snapshot=conversation.current ?: return callback(null,"请先读取当前聊天")
        if(!graph.tracker.isCurrent(token))return callback(null,"聊天已变化，请重新读取")
        val cancel=Cancellation()
        worker.execute {
            val detail=runCatching { graph.automaticAdvisor.detail(graph.inputFor(snapshot,"","",null),plan,cancel) }
            main.post {
                if(graph.tracker.isCurrent(token) && graph.coordinator.state.current?.batch?.replies?.any { it===plan }==true)callback(detail.getOrNull(),detail.exceptionOrNull()?.let { graph.safeError(it as? Exception ?: Exception()) })
            }
        }
    }
    fun confirmDraft(snapshot:ChatSnapshot) { if(ChatApps.supported(snapshot.packageName)) accept(snapshot,manualRead.begin()) }
    fun captureGeneration()=navigation
    fun fillDiagnostic(message:String) { lastFillDiagnosticStatus=message }
    /** Local screenshot verifies the manually read title; no model call or new chat collection. */
    fun verifyVisualFill(token:SessionToken,requireComposerLayout:Boolean=false,callback:(Boolean)->Unit) {
        val ref=visualFill
        if(ref==null || ref.windowId!=token.windowId || ref.generation!=navigation || !overlayAllowed() || !graph.tracker.sameConversation(token)) { callback(false);return }
        val cancel=Cancellation();var completed=false
        fun finish(value:Boolean) { if(!completed) { completed=true;cancel.cancel();callback(value) } }
        main.postDelayed({ finish(false) },5000)
        captureWindow(ref.windowId,cancel) { image ->
            val matches=if(image is CaptureImageResult.Success) {
                try { VisualFillReference.stamp(image.bitmap)==ref.stamp && (!requireComposerLayout || ref.viewport==lastForegroundWindow?.viewport && ref.footerStamp!=null && VisualFillReference.footerStamp(image.bitmap)==ref.footerStamp) } finally { image.bitmap.recycle() }
            } else false
            finish(matches && ref===visualFill && ref.generation==navigation && overlayAllowed() && graph.tracker.sameConversation(token))
        }
    }
    fun focusVisualComposer(token:SessionToken,callback:(Boolean)->Unit) {
        val ref=visualFill;val input=ref?.composer ?: ref?.keyboardButton
        if(ref==null || input==null || ref.generation!=navigation || ref.windowId!=token.windowId || !overlayAllowed() || !graph.tracker.sameConversation(token)) { callback(false);return }
        // Never tap a stale bottom coordinate when the IME has already changed the layout.
        if(windows.any { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }) { callback(false);return }
        verifyVisualFill(token,requireComposerLayout=true) { verified ->
        if(!verified || ref!==visualFill || ref.generation!=navigation || windows.any { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }) { callback(false);return@verifyVisualFill }
        val path=Path().apply { moveTo((input.left+input.right)/2f,(input.top+input.bottom)/2f) }
        overlaySuspensions++;if(::island.isInitialized)island.suspend();overlay.suppress()
        val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,80)).build()
        var completed=false
        fun finish(value:Boolean) { if(!completed) { completed=true;overlaySuspensions--;ensureOverlay();callback(value && ref.generation==navigation && overlayAllowed()) } }
        main.postDelayed({ finish(false) },2000)
        val submitted=dispatchGesture(gesture,object:GestureResultCallback() {
            override fun onCompleted(gestureDescription:GestureDescription?) { finish(true) }
            override fun onCancelled(gestureDescription:GestureDescription?) { finish(false) }
        },main)
        if(!submitted)finish(false)
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
            if(::island.isInitialized) { if(overlaySuspensions>0)island.suspend() else island.stop() }
            overlay.suppress()
        }
    } }
    fun islandReading(value:Boolean) { if(::island.isInitialized)island.updateStatus(value,graph.coordinator.state,graph.coordinator.state.current?.let { graph.tracker.sameConversation(it.ticket.session) }==true) }
    fun showFloating() { graph.settings.update { it.copy(overlayEnabled=true) }; checkForeground() }
    fun settingsChanged() { if(::island.isInitialized && !graph.settings.load().nativeIslandEnabled) { if(graph.settings.load().topCapsuleEnabled)island.suspend() else island.stop() };if(!canObserve()) leaveForeground() else checkForeground() }
    fun stopAssistance() {
        stopping=true
        stopService(Intent(this,ProjectionService::class.java))
        if(::island.isInitialized)island.stop()
        leaveForeground("辅助服务已停止")
        disableSelf()
    }
    fun beginProjectionCapture() { projecting=true; leaveForeground() }
    fun endProjectionCapture() { projecting=false; checkForeground() }
    fun previewOcr(draft:ConversationDraft) { graph.controller.status("请返回微信，点击读取信息获取当前聊天") }
    fun notifyUser(text:String) { Toast.makeText(this,text,Toast.LENGTH_SHORT).show() }
    private fun reset(message:String="点击读取信息，获取当前微信对话") {
        manualRead.cancel(); main.removeCallbacks(readPending)
        visualCancel?.cancel(); visualCancel=null; pendingVision=null; visualSignature=null;visualFill=null
        identity=null; conversation.clear(); lastWindow=null; navigation++
        if(::overlay.isInitialized) overlay.setReading(false)
        if(ChatApps.supported(graph.tracker.currentToken?.packageName) || ChatApps.supported(graph.coordinator.state.current?.ticket?.session?.packageName)) graph.controller.pauseCapture(message)
    }
    private fun leaveForeground(message:String="微信已在后台；返回后点击图标展开") {
        wechatForeground=false; foregroundWindowId=null;foregroundPackage=null
        lastForegroundWindow=null
        main.removeCallbacks(debounced); readGate.invalidate(); reset(message)
        if(::overlay.isInitialized) { overlay.hide(); ensureOverlay() }
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
    override fun onInterrupt() { stopService(Intent(this,ProjectionService::class.java));if(::island.isInitialized)island.stop();readGate.invalidate(); reset(); if(::overlay.isInitialized) { overlay.hide();overlay.suppress() } }
    override fun onConfigurationChanged(newConfig:android.content.res.Configuration) { super.onConfigurationChanged(newConfig);if(::overlay.isInitialized)overlay.themeChanged();if(::reader.isInitialized)checkForeground() }
    override fun onDestroy() { stopping=true;stopService(Intent(this,ProjectionService::class.java));if(::island.isInitialized)island.destroy(); main.removeCallbacksAndMessages(null); readGate.invalidate(); reset(); if(::overlay.isInitialized) overlay.destroy(); worker.shutdownNow(); nativeWorker.shutdownNow(); runCatching { unregisterReceiver(receiver) }; instance=null; super.onDestroy() }
    companion object { @Volatile var instance:ChatCaptureService?=null; private set }
}
