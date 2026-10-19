package com.loveadvisor.android.overlay
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.widget.ImageView
import com.loveadvisor.android.R
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.android.settings.*
import com.loveadvisor.android.input.*
import com.loveadvisor.core.model.RectI
import com.loveadvisor.core.overlay.*
import com.loveadvisor.core.advisor.BatchState
import com.loveadvisor.core.advisor.ReplyStyleEditorContext
import com.loveadvisor.core.capture.ChatApps
import kotlin.math.abs
import kotlin.math.roundToInt

/** Both surfaces use one anchor and dock calculation; their windows contain no empty touch column. */
class OverlayController(private val service:ChatCaptureService) {
    private val graph=service.graph
    private val manager=service.getSystemService(WindowManager::class.java)
    private var bubble:ImageView?=null; private var panel:AdvisorPanelView?=null
    private val bubbleWindow=WindowRegistration()
    private val panelWindow=WindowRegistration()
    private var frostedWindow:FrostedPanelWindow?=null
    private var useFrostedWindow=false
    private var bubbleParams:WindowManager.LayoutParams?=null; private var panelParams:WindowManager.LayoutParams?=null
    private var anchorX:Int?=null; private var anchorY:Int?=null; private var panelOnLeft:Boolean?=null
    private var state=BatchState(); private var keyboardVisible=false
    private var reading=false
    private var filling=false
    private var cancelStyleRead:(()->Unit)?=null
    private var confirmation:DraftConfirmWindow?=null
    private var cancelFill:(()->Unit)?=null
    private var analysisController:com.loveadvisor.android.analysis.MessageAnalysisController?=null
    private var analysisSession:com.loveadvisor.core.model.SessionToken?=null
    private var imeInsetBottom=0
    private val main=Handler(Looper.getMainLooper())
    private val idle=BubbleIdlePolicy()
    private var edge:BubbleEdge?=null
    private var edgeAnimator:ValueAnimator?=null
    private var motionX:Float?=null
    private var dragging=false
    private var lastAvailable:RectI?=null
    private var entryMode=OverlayEntryMode.BUBBLE
    private var islandPanel=false
    private var islandBounds:PanelBounds?=null
    private var capsule:TopCapsuleView?=null
    private val capsuleWindow=WindowRegistration()
    private var capsuleParams:WindowManager.LayoutParams?=null
    private var capsuleBounds:PanelBounds?=null
    private var capsuleX:Float?=null;private var capsuleY:Float?=null
    private var capsuleStartX=0f;private var capsuleStartY=0f
    private var capsulePreviewScale:Float?=null
    private val autoHide=Runnable {
        if(!canAutoHide()) idle.pause()
        else if(idle.hideIfDue(SystemClock.uptimeMillis())) {
            val p=bubbleParams ?: return@Runnable
            val a=available();val current=PanelBounds(p.x,p.y,p.width,p.height)
            edge=BubbleEdgeGeometry.nearest(current,a)
            val full=BubbleEdgeGeometry.place(current,a,edge!!,false)
            anchorX=full.leftPx;anchorY=full.topPx
            moveBubbleTo(BubbleEdgeGeometry.place(full,a,edge!!,true,physicalDisplay()).leftPx)
        }
    }
    private val listener:(BatchState)->Unit={ render(it) }
    init { graph.controller.listen(listener) }
    private fun create() {
        if(bubble!=null) return
        val size=ViewStyles.dp(service,40f)
        val image=BitmapFactory.decodeResource(service.resources,R.drawable.advisor_logo_new,BitmapFactory.Options().apply { inScaled=false })
        image?.density=Bitmap.DENSITY_NONE
        val icon=ImageView(service).apply {
            scaleType=ImageView.ScaleType.CENTER_CROP; setImageBitmap(image)
            background=ViewStyles.shape(service,0xFFFFDA3B.toInt(),20f); clipToOutline=true; elevation=0f
            contentDescription="打开对话军师悬浮窗"
            setOnClickListener { bubbleClick() }
            setOnApplyWindowInsetsListener { _,insets ->
                val bottom=if(insets.isVisible(WindowInsets.Type.ime()))insets.getInsets(WindowInsets.Type.ime()).bottom else 0
                if(bottom!=imeInsetBottom) { imeInsetBottom=bottom;post { resize() } }
                insets
            }
        }
        bubble=icon
        val a=available(); anchorX=a.right-size-ViewStyles.dp(service,8f); anchorY=a.top+(a.height*.25).toInt()
        bubbleParams=params(size,size,"LoveAdvisorBubble").apply { flags=flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS }; drag(icon)
    }
    private fun createCapsule() {
        if(capsule!=null)return
        capsule=TopCapsuleView(service,{ if(panel==null)showPanel(true) else hide() },{
            val base=capsuleGeometry(0f,0f)
            val start=TopCapsuleGeometry.offsetOf(capsuleBounds ?: base,base,service.resources.displayMetrics.density)
            capsuleStartX=start.x;capsuleStartY=start.y
        },{ dx,dy ->
            val density=service.resources.displayMetrics.density
            capsuleX=capsuleStartX+dx/density;capsuleY=capsuleStartY+dy/density;layout()
        },{
            val actual=capsuleBounds;val base=capsuleGeometry(0f,0f);val density=service.resources.displayMetrics.density
            if(actual!=null) {
                val offset=TopCapsuleGeometry.offsetOf(actual,base,density)
                graph.settings.update { it.copy(capsuleOffsetXdp=offset.x,capsuleOffsetYdp=offset.y) }
            }
            capsuleX=null;capsuleY=null;layout()
        },{
            capsuleX=null;capsuleY=null
            main.post { if(capsuleWindow.registered)layout() }
        })
        capsuleParams=params(1,1,"LoveAdvisorTopCapsule").apply {
            flags=flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        updateCapsuleStatus()
    }
    private fun updateCapsuleStatus() {
        val current=state.current?.let { graph.tracker.sameConversation(it.ticket.session) && it.batch.replies.size==6 }==true
        capsule?.setStatus(NativeIslandEntryPolicy.status(reading,state,current))
    }
    private fun capsuleGeometry(x:Float,y:Float):PanelBounds {
        val metrics=manager.maximumWindowMetrics
        val bars=metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
        val physical=physicalDisplay();val safe=available()
        val area=physical.copy(bottom=minOf(physical.bottom,safe.bottom))
        val cuts=metrics.windowInsets.displayCutout?.boundingRects.orEmpty().map { RectI(it.left,it.top,it.right,it.bottom) }
        return TopCapsuleGeometry.place(area,bars.top,service.resources.displayMetrics.density,cuts,x,y,capsulePreviewScale ?: graph.settings.load().capsuleScale,capsule?.contentWidthDp() ?: 52f)
    }
    private fun finishCapsuleScale() {
        val value=capsulePreviewScale ?: return
        graph.settings.update { it.copy(capsuleScale=value) };capsulePreviewScale=null;layout()
    }
    private fun capsuleMenuControls():CapsuleMenuControls {
        val config=graph.settings.load()
        return CapsuleMenuControls(config.capsuleLocked,capsulePreviewScale ?: config.capsuleScale,{
            val locked=!graph.settings.load().capsuleLocked
            graph.settings.update { it.copy(capsuleLocked=locked) };layout();locked
        },{ value,commit ->
            capsulePreviewScale=value.coerceIn(.7f,2f);layout()
            if(commit)finishCapsuleScale()
        },{ finishCapsuleScale() })
    }
    private fun params(width:Int,height:Int,titleValue:String)=WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.LEFT; title=titleValue;setFitInsetsTypes(0) }
    fun showBubble() {
        if(!service.overlayAllowed()) return
        create()
        if(entryMode==OverlayEntryMode.TOP_CAPSULE) { createCapsule();updateCapsuleStatus() }
        layout()
        if(entryMode==OverlayEntryMode.TOP_CAPSULE) {
            try {
                val first=!capsuleWindow.registered
                capsule?.let { capsuleWindow.attach { manager.addView(it,capsuleParams) };if(first)OverlayAnimator.enter(it,graph.settings.load().reducedMotion,panel=true) }
            } catch(_:RuntimeException) {
                graph.settings.update { it.copy(topCapsuleEnabled=false) };setEntryMode(OverlayEntryMode.BUBBLE)
                service.notifyUser("未能显示左上角胶囊，已恢复悬浮球")
            }
        } else capsule?.let { capsuleWindow.detach { manager.removeViewImmediate(it) } }
        panel?.let { attachPanel(it) }
        if(entryMode==OverlayEntryMode.BUBBLE || entryMode==OverlayEntryMode.ISLAND_TEST) bubble?.let { bubbleWindow.attach { manager.addView(it,bubbleParams) } }
        else bubble?.let { bubbleWindow.detach { manager.removeViewImmediate(it) } }
        scheduleAutoHide()
    }
    fun show() { showPanel(false) }
    private fun showPanel(native:Boolean) {
        if(!service.overlayAllowed()) return
        if(panel!=null) { if(islandPanel==native) return else hide() }
        showBubble();islandPanel=native;islandBounds=null
        stopEdgeMotion();idle.interact(SystemClock.uptimeMillis(),idleSeconds());idle.pause();main.removeCallbacks(autoHide)
        useFrostedWindow=graph.settings.load().frostedPanelEnabled && android.os.Build.VERSION.SDK_INT>=31
        val view=AdvisorPanelView(service,{ ClipboardController.copy(service,it.text);hide() },{ plan,token ->
            if(!filling) {
                filling=true;panel?.setFilling(true)
                cancelFill=ReplyFiller(service).fill(token,plan.text,{ draft,confirm,cancel ->
                    confirmation?.close()
                    confirmation=DraftConfirmWindow(service,draft,{ confirmation=null;confirm() },{ confirmation=null;cancel() })
                    confirmation?.show()
                },{ success,message ->
                    cancelFill=null;filling=false;confirmation?.close();confirmation=null;panel?.setFilling(false);panel?.fillResult(message);service.notifyUser(message)
                    if(success && graph.settings.load().fillPanelBehavior!="keep") hide() else layout()
                })
            }
        },{ service.refresh() },{ service.captureNow() },{ anchor ->
            panel?.toggleMenu(listOf("回复风格设置" to { editReplyStyle() },"悬浮窗设置" to { editOverlaySettings() },"模型设置" to { editModelSettings() }),capsuleMenuControls())
        },{ hide() },{ graph.tracker.isCurrent(it) && ChatApps.supported(it.packageName) },{ graph.settings.load().reducedMotion },{ plan,token,callback -> service.describe(plan,token,callback) },if(native) PanelDragMode.TOP_GRIP else PanelDragMode.TITLE,onResize={ layout() },frosted=useFrostedWindow,onEditingFocus={ panelEditingFocus(it) },onAnalyze={ showMessageAnalysis() })
        view.contentDescription="聊天候选面板"
        panel=view; panelParams=params(1,1,"LoveAdvisorPanel"); panelOnLeft=null
        view.setOnApplyWindowInsetsListener { _,insets ->
            val bottom=if(insets.isVisible(WindowInsets.Type.ime()))insets.getInsets(WindowInsets.Type.ime()).bottom else 0
            if(bottom!=imeInsetBottom) { imeInsetBottom=bottom;view.post { resize() } }
            insets
        }
        if(native) dragIsland(view.dragHandle) else drag(view.dragHandle); view.setReading(reading); view.setFilling(filling);view.setReadContext(readSnapshot()?.identity?.displayTitle);view.setAnalysisAvailable(readSnapshot()!=null);view.render(state); layout(); attachPanel(view)
        OverlayAnimator.enter(view,graph.settings.load().reducedMotion,panel=true)
    }
    private fun attachPanel(view:AdvisorPanelView) {
        val p=panelParams ?: return
        panelWindow.attach {
            if(useFrostedWindow && android.os.Build.VERSION.SDK_INT>=31) {
                try {
                    val host=frostedWindow ?: FrostedPanelWindow(service,view).also { frostedWindow=it }
                    host.show(p)
                } catch(_:RuntimeException) {
                    runCatching { frostedWindow?.release() };frostedWindow=null;useFrostedWindow=false
                    view.useFrostedWindow(false)
                    manager.addView(view,p)
                    service.notifyUser("当前系统未能启用毛玻璃，已使用清晰面板")
                }
            } else manager.addView(view,p)
        }
    }
    private fun editReplyStyle() {
        if(filling || reading) { service.notifyUser("请等待当前读取或填入完成");return }
        if(cancelStyleRead!=null)return
        val view=panel ?: return
        view.fillResult("正在读取当前昵称…");view.setReading(true)
        cancelStyleRead=service.readStyleNickname { pkg,title ->
            cancelStyleRead=null
            if(panel!==view)return@readStyleNickname
            view.setReading(false)
            if(pkg==null) { service.notifyUser("聊天页面已变化，请重新打开回复风格设置");return@readStyleNickname }
            val snapshot=readSnapshot()
            val context=ReplyStyleEditorContext.resolve(snapshot,pkg,title)
            fun openEditor(kind:com.loveadvisor.core.model.ChatKind) {
            val name=context.title
            val global=graph.settings.load().let { if(kind==com.loveadvisor.core.model.ChatKind.GROUP)it.globalGroupReplyStyle else it.globalReplyStyle }
            val editor=ReplyStyleEditorView(service,pkg,name,graph.settings.replyStyleCatalog(kind),global,
                { graph.settings.saveReplyStyleCatalog(it,kind);service.notifyUser("专属风格已保存，下次生成或刷新时生效") },
                { value -> graph.settings.update { if(kind==com.loveadvisor.core.model.ChatKind.GROUP)it.copy(globalGroupReplyStyle=value) else it.copy(globalReplyStyle=value) };service.notifyUser("全对象风格已保存，下次生成或刷新时生效") },
                { panel?.closeStyleEditor() },onChooseObject={ entries,selected ->
                    view.showPage("style-objects",SavedStylePickerView(service,entries,{ entry -> selected(entry);view.popPage() },{ view.popPage() },{ selected(null);view.popPage() }),editable=true)
                },kind=kind,nicknameFromOcr=service.lastStyleNicknameFromOcr)
            view.fillResult("风格设置已打开；保存后在下一次生成或刷新时生效")
            view.showStyleEditor(editor) {}
            }
            val knownKind=context.kind
            if(knownKind!=null)openEditor(knownKind)
            else view.showPage("style-chat-kind",ModelPickerView(service,"当前对话类型（读取后可自动确认）",listOf("单聊专属 / 全对象风格","群聊专属 / 全部群聊风格"),-1,{ index -> view.popPage();openEditor(if(index==1)com.loveadvisor.core.model.ChatKind.GROUP else com.loveadvisor.core.model.ChatKind.DIRECT) },{ view.popPage() }),editable=true)
        }
    }
    private fun panelEditingFocus(editing:Boolean) {
        val view=panel ?: return
        val p=panelParams ?: return
        if(!editing) {
            view.clearFocus()
            service.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken,0)
        }
        p.flags=if(editing)p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() else p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        p.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        if(panelWindow.registered) {
            if(android.os.Build.VERSION.SDK_INT>=31 && frostedWindow!=null)frostedWindow?.update(p) else manager.updateViewLayout(view,p)
        }
        layout()
    }
    private fun editModelSettings() {
        val view=panel ?: return
        val scope=SettingsWorkScope(graph)
        val host=object:SettingsFormHost {
            override fun choose(title:String,items:List<String>,selected:Int,onSelected:(Int)->Unit) {
                view.showPage("model-picker",ModelPickerView(service,title,items,selected,{ index -> view.popPage();onSelected(index) },{ view.popPage() }),editable=true)
            }
            override fun <T> work(id:String,task:(com.loveadvisor.core.api.Cancellation)->T,onSuccess:(T)->Unit,onFailure:(String)->Unit)=scope.work(id,task,onSuccess,onFailure)
            override fun message(text:String)=service.notifyUser(text)
            override fun close() { view.popPage() }
        }
        view.showPage("models",ModelSettingsView(service,graph,host,compact=true),editable=true,closed={ scope.close() })
    }
    private fun editOverlaySettings() {
        val view=panel ?: return
        val previous=islandBounds
        view.showPage("overlay-settings",OverlaySettingsView(service,graph,{
            view.closeStyleEditor();hide()
            val mode=if(graph.settings.load().topCapsuleEnabled)OverlayEntryMode.TOP_CAPSULE else OverlayEntryMode.BUBBLE
            setEntryMode(mode);service.settingsChanged()
            if(service.overlayAllowed()) { showPanel(mode==OverlayEntryMode.TOP_CAPSULE);if(mode==OverlayEntryMode.TOP_CAPSULE)islandBounds=previous;layout() }
            service.notifyUser("悬浮窗设置已保存")
        },{ view.popPage() },compact=true),editable=true)
    }
    private fun detachPanel(view:AdvisorPanelView) {
        panelWindow.detach {
            val host=frostedWindow
            if(host!=null && android.os.Build.VERSION.SDK_INT>=31)host.hide() else manager.removeViewImmediate(view)
        }
    }
    private fun readSnapshot()=service.currentChatSnapshot?.takeIf { graph.tracker.currentToken!=null && graph.tracker.lastSnapshot==it }
    private fun showMessageAnalysis() {
        val view=panel ?: return
        val snapshot=readSnapshot()
        if(snapshot==null) { service.notifyUser("请先读取信息，才能判断分析");return }
        if(reading || filling || state.loading) { service.notifyUser("请等待当前操作完成");return }
        val token=graph.tracker.currentToken ?: return
        val controller=analysisController ?: com.loveadvisor.android.analysis.MessageAnalysisController(graph).also { analysisController=it }
        analysisSession=token
        fun current()=panel===view && graph.tracker.isCurrent(token) && readSnapshot()==snapshot
        val list=MessageAnalysisListView(service,snapshot,{ message ->
            if(!current()) { service.notifyUser("对话已变化，请重新读取");return@MessageAnalysisListView }
            val detail=MessageJudgmentView(service,message,{ view.popPage() },{
                if(current()) { view.closeStyleEditor();service.replyTo(com.loveadvisor.core.model.ReplyTarget(message.id)) } else service.notifyUser("对话已变化，请重新读取")
            })
            var cancel:(()->Unit)?=null
            fun load() { detail.show(null,"正在判断这句话…");cancel?.invoke();cancel=controller.analyze(snapshot,message.id) { result,error ->
                if(current())detail.show(result,error,retry=if(error!=null)({ load() }) else null)
            } }
            view.showPage("judgment-detail",detail,editable=true,closed={ cancel?.invoke() });load()
        },{ view.popPage() },{ if(current()) { view.closeStyleEditor();service.replyTo(null) } })
        view.showPage("judgment-list",list,editable=true,closed={ controller.cancel();analysisSession=null })
    }
    fun render(value:BatchState) {
        state=value
        if(analysisSession?.let { !graph.tracker.isCurrent(it) }==true) { panel?.closeStyleEditor();analysisController?.invalidate() }
        panel?.setReadContext(readSnapshot()?.identity?.displayTitle);panel?.setAnalysisAvailable(readSnapshot()!=null);panel?.render(value);updateCapsuleStatus();layout()
    }
    fun showFromIsland():Boolean { showPanel(true);return panelWindow.registered }
    fun setEntryMode(value:OverlayEntryMode) {
        if(entryMode==value) return
        // Confirmation of the already open native test must not close that panel.
        if(panel!=null && !(islandPanel && value!=OverlayEntryMode.BUBBLE)) hide()
        entryMode=value
        if(value==OverlayEntryMode.ISLAND || value==OverlayEntryMode.TOP_CAPSULE) { main.removeCallbacks(autoHide);idle.pause();stopEdgeMotion();bubble?.let { bubbleWindow.detach { manager.removeViewImmediate(it) } } }
    }
    fun setReading(value:Boolean) { reading=value; panel?.setReading(value);updateCapsuleStatus();service.islandReading(value);layout() }
    fun themeChanged() { val expanded=panel!=null;val native=islandPanel;hide();if(expanded && service.overlayAllowed())showPanel(native) }
    /** A foreground transition hides surfaces, but is not a request to discard an editor draft. */
    fun suspendForBackground() {
        main.removeCallbacks(autoHide);idle.pause();stopEdgeMotion();dragging=false
        cancelFill?.invoke();cancelFill=null;filling=false;confirmation?.close();confirmation=null
        panel?.let { it.stopAnimations();detachPanel(it) }
        bubble?.let { bubbleWindow.detach { manager.removeViewImmediate(it) } }
        capsule?.let { OverlayAnimator.stop(it);capsuleWindow.detach { manager.removeViewImmediate(it) } }
    }
    fun hide() {
        val expanded=panel!=null
        cancelStyleRead?.invoke();cancelStyleRead=null
        cancelFill?.invoke();cancelFill=null;filling=false;confirmation?.close();confirmation=null
        panel?.let { it.release();detachPanel(it) }
        if(android.os.Build.VERSION.SDK_INT>=31)frostedWindow?.release()
        frostedWindow=null;useFrostedWindow=false
        panel=null;panelParams=null;panelOnLeft=null;islandPanel=false;islandBounds=null;layout();scheduleAutoHide(restart=expanded)
    }
    /** Retain the shared anchor and expansion while temporarily detaching both surfaces. */
    fun suppress() {
        main.removeCallbacks(autoHide);idle.pause();stopEdgeMotion();dragging=false
        if(confirmation!=null)filling=false;confirmation?.close();confirmation=null
        panel?.let { it.dismissMenu();it.closeStyleEditor();it.stopAnimations();detachPanel(it) }
        bubble?.let { bubbleWindow.detach { manager.removeViewImmediate(it) } }
        capsule?.let { OverlayAnimator.stop(it);capsuleWindow.detach { manager.removeViewImmediate(it) } }
    }
    private fun idleSeconds()=graph.settings.load().bubbleAutoHideSeconds
    private fun canAutoHide()=entryMode!=OverlayEntryMode.ISLAND && entryMode!=OverlayEntryMode.TOP_CAPSULE && panel==null && !dragging && bubbleWindow.registered && service.overlayAllowed()
    private fun scheduleAutoHide(restart:Boolean=false) {
        main.removeCallbacks(autoHide)
        if(!canAutoHide()) { idle.pause();return }
        val now=SystemClock.uptimeMillis()
        if(restart && !idle.hidden) idle.interact(now,idleSeconds()) else idle.resume(now,idleSeconds())
        idle.deadlineAt?.let { main.postDelayed(autoHide,(it-now).coerceAtLeast(0)) }
    }
    private fun bubbleClick() {
        if(idle.reveal(SystemClock.uptimeMillis(),idleSeconds())) {
            val p=bubbleParams ?: return
            val a=available();val current=PanelBounds(p.x,p.y,p.width,p.height)
            val full=BubbleEdgeGeometry.place(current,a,edge ?: BubbleEdgeGeometry.nearest(current,a),false)
            anchorX=full.leftPx;anchorY=full.topPx
            moveBubbleTo(full.leftPx);scheduleAutoHide()
        } else if(panel==null) show() else hide()
    }
    private fun stopEdgeMotion(freeze:Boolean=false) {
        edgeAnimator?.let { it.removeAllUpdateListeners();it.removeAllListeners();it.cancel() };edgeAnimator=null
        motionX=if(freeze) bubbleParams?.x?.toFloat() else null
    }
    private fun moveBubbleTo(targetX:Int) {
        val p=bubbleParams ?: return
        val from=p.x.toFloat();stopEdgeMotion()
        bubble?.contentDescription=if(idle.hidden) "显示对话军师悬浮球" else "打开对话军师悬浮窗"
        if(graph.settings.load().reducedMotion || !ValueAnimator.areAnimatorsEnabled() || !bubbleWindow.registered || from==targetX.toFloat()) { layout();return }
        motionX=from
        val animation=ValueAnimator.ofFloat(from,targetX.toFloat()).apply {
            duration=250;interpolator=android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                motionX=it.animatedValue as Float
                // Only the icon's small window moves each frame; no screen reads or model requests.
                val x=motionX!!.roundToInt()
                if(bubbleWindow.registered && panel==null && p.x!=x) { p.x=x;bubble?.let { v -> manager.updateViewLayout(v,p) } }
            }
            addListener(object:AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation:Animator) {
                    if(edgeAnimator===animation) { edgeAnimator=null;motionX=null;layout() }
                }
            })
        }
        edgeAnimator=animation;animation.start()
    }
    fun resize() {
        val ime=service.windows.any { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        keyboardVisible=ime; layout()
    }
    private fun available():RectI {
        val metrics=manager.maximumWindowMetrics; val r=metrics.bounds; val inset=metrics.windowInsets.getInsets(WindowInsets.Type.systemBars())
        val cutout=metrics.windowInsets.getInsets(WindowInsets.Type.displayCutout())
        val windowIme=service.windows.firstOrNull { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.let { val b=Rect(); it.getBoundsInScreen(b); b.top }?.takeIf { it>r.top+r.height()*.25 && it<r.bottom }
        val insetIme=imeInsetBottom.takeIf { it>0 }?.let { r.bottom-it }
        val ime=listOfNotNull(windowIme,insetIme).minOrNull()
        val top=if(entryMode==OverlayEntryMode.TOP_CAPSULE)maxOf(inset.top,metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top) else inset.top
        return OverlayViewport.available(RectI(r.left,r.top,r.right,r.bottom),InsetsI(inset.left,top,inset.right,inset.bottom),
            InsetsI(cutout.left,cutout.top,cutout.right,cutout.bottom),ime,service.resources.displayMetrics.density)
    }
    private fun physicalDisplay():RectI=manager.maximumWindowMetrics.bounds.let { RectI(it.left,it.top,it.right,it.bottom) }
    private fun layout() {
        val icon=bubble ?: return; val bp=bubbleParams ?: return
        val a=available()
        if(lastAvailable!=null && lastAvailable!=a) stopEdgeMotion()
        lastAvailable=a
        val size=ViewStyles.dp(service,40f).coerceAtMost(minOf(a.width,a.height)).coerceAtLeast(1)
        val anchor=PanelBounds(anchorX ?: a.left,anchorY ?: a.top,size,size)
        val ball:PanelBounds; val candidate:PanelBounds?
        if(panel==null) {
            ball=if(idle.hidden) BubbleEdgeGeometry.place(anchor,a,edge ?: BubbleEdgeGeometry.nearest(anchor,a),false) else OverlayGeometry.clamp(anchor,a)
            candidate=null
        }
        else {
            val initial=OverlayGeometry.measure(a,InsetsI(),null,service.resources.displayMetrics.density)
            // A complete settings menu may be taller than the normal three-candidate panel.
            // Still respect the current keyboard/composer-safe viewport; small screens scroll.
            val maxHeight=if(panel!!.currentPageId=="overlay-settings")(a.height*.72f).toInt() else if(panel!!.currentPageId in setOf("models","model-picker"))minOf(initial.heightPx,(a.height*.78f).toInt()) else if(panel!!.menuExpanded)a.height else initial.heightPx
            val measured=initial.copy(heightPx=panel!!.preferredHeight(initial.widthPx).coerceIn(1,maxHeight.coerceAtLeast(1)))
            if(islandPanel) {
                val opened=IslandPanelGeometry.open(measured,a,ViewStyles.dp(service,12f))
                candidate=islandBounds?.let { OverlayGeometry.clamp(opened.copy(leftPx=it.leftPx,topPx=it.topPx),a) } ?: opened
                islandBounds=candidate;ball=OverlayGeometry.clamp(anchor,a)
            } else {
                val dock=OverlayGeometry.dock(anchor,measured,a,panelOnLeft)
                ball=dock.bubble;candidate=dock.panel
                if(panelOnLeft==null) panelOnLeft=candidate.leftPx<ball.leftPx
            }
        }
        anchorX=ball.leftPx; anchorY=ball.topPx
        fun position(view:View,p:WindowManager.LayoutParams,b:PanelBounds,window:WindowRegistration) {
            val changed=p.x!=b.leftPx || p.y!=b.topPx || p.width!=b.widthPx || p.height!=b.heightPx
            p.x=b.leftPx; p.y=b.topPx; p.width=b.widthPx; p.height=b.heightPx
            if(changed && window.registered) {
                val host=frostedWindow
                if(window===panelWindow && host!=null && android.os.Build.VERSION.SDK_INT>=31)host.update(p) else manager.updateViewLayout(view,p)
            }
        }
        if(entryMode==OverlayEntryMode.TOP_CAPSULE) {
            val config=graph.settings.load()
            capsule?.applyOptions(config.capsuleLocked,capsulePreviewScale ?: config.capsuleScale)
            val b=capsuleGeometry(capsuleX ?: config.capsuleOffsetXdp,capsuleY ?: config.capsuleOffsetYdp)
            capsuleBounds=b
            capsule?.let { v -> capsuleParams?.let { position(v,it,b,capsuleWindow) } }
        }
        // Ordinary ball/panel dock together; the capsule is a separate fixed-size entry.
        panel?.let { v -> candidate?.let { b -> panelParams?.let { position(v,it,b,panelWindow) }; v.compact(b.heightPx<200*service.resources.displayMetrics.density) } }
        val resting=if(panel==null && idle.hidden) BubbleEdgeGeometry.place(ball,a,edge ?: BubbleEdgeGeometry.nearest(ball,a),true,physicalDisplay()) else ball
        val displayed=if(panel==null && motionX!=null) resting.copy(leftPx=motionX!!.roundToInt()) else resting
        icon.contentDescription=if(idle.hidden) "显示对话军师悬浮球" else "打开对话军师悬浮窗"
        position(icon,bp,displayed,bubbleWindow)
    }
    private fun dragIsland(view:View) {
        var startX=0f;var startY=0f;var origin:PanelBounds?=null
        view.setOnTouchListener { _,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX=event.rawX;startY=event.rawY;origin=islandBounds;true }
                MotionEvent.ACTION_MOVE -> { origin?.let { islandBounds=it.copy(leftPx=it.leftPx+(event.rawX-startX).toInt(),topPx=it.topPx+(event.rawY-startY).toInt());layout() };true }
                MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> { origin=null;true }
                else -> false
            }
        }
    }
    private fun drag(view:View) {
        var x=0f; var y=0f; var px=0; var py=0; var moved=false
        view.setOnTouchListener { v,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    main.removeCallbacks(autoHide);idle.pause();stopEdgeMotion(freeze=true);dragging=true
                    x=event.rawX;y=event.rawY;px=bubbleParams?.x ?: 0;py=anchorY ?: 0;moved=false;true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx=event.rawX-x;val dy=event.rawY-y
                    if(abs(dx)+abs(dy)>ViewStyles.dp(service,6f)) moved=true
                    if(moved) {
                        idle.interact(SystemClock.uptimeMillis(),idleSeconds());idle.pause();motionX=null;edge=null
                        anchorX=(px+dx).toInt();anchorY=(py+dy).toInt();layout()
                    };true
                }
                MotionEvent.ACTION_UP -> { dragging=false;motionX=null;if(!moved) v.performClick();scheduleAutoHide(restart=true);true }
                MotionEvent.ACTION_CANCEL -> { dragging=false;motionX=null;layout();scheduleAutoHide(restart=true);true }
                else -> false
            }
        }
    }
    fun destroy() { analysisController?.close();analysisController=null;hide();suppress();capsule?.release();capsule=null;main.removeCallbacksAndMessages(null);bubble?.drawable?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle() }; bubble=null; panel=null; bubbleParams=null; panelParams=null; graph.controller.unlisten(listener) }
}
