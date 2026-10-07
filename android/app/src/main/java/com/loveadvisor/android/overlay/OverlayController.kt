package com.loveadvisor.android.overlay
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.*
import android.widget.ImageView
import com.loveadvisor.android.R
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.android.ui.DiagnosticsActivity
import com.loveadvisor.android.settings.SettingsActivity
import com.loveadvisor.android.settings.ReplyStyleActivity
import com.loveadvisor.android.advisor.ConsultationActivity
import com.loveadvisor.android.input.*
import com.loveadvisor.core.model.RectI
import com.loveadvisor.core.overlay.*
import com.loveadvisor.core.advisor.BatchState
import com.loveadvisor.core.capture.ChatApps
import kotlin.math.abs

/** Both surfaces use one anchor and dock calculation; their windows contain no empty touch column. */
class OverlayController(private val service:ChatCaptureService) {
    private val graph=service.graph
    private val manager=service.getSystemService(WindowManager::class.java)
    private var bubble:ImageView?=null; private var panel:AdvisorPanelView?=null
    private val bubbleWindow=WindowRegistration()
    private val panelWindow=WindowRegistration()
    private var bubbleParams:WindowManager.LayoutParams?=null; private var panelParams:WindowManager.LayoutParams?=null
    private var anchorX:Int?=null; private var anchorY:Int?=null; private var panelOnLeft:Boolean?=null
    private var state=BatchState(); private var keyboardVisible=false
    private var reading=false
    private var filling=false
    private var confirmation:DraftConfirmWindow?=null
    private var cancelFill:(()->Unit)?=null
    private var imeInsetBottom=0
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
            setOnClickListener { if(panel==null) show() else hide() }
            setOnApplyWindowInsetsListener { _,insets ->
                val bottom=if(insets.isVisible(WindowInsets.Type.ime()))insets.getInsets(WindowInsets.Type.ime()).bottom else 0
                if(bottom!=imeInsetBottom) { imeInsetBottom=bottom;post { resize() } }
                insets
            }
        }
        bubble=icon
        val a=available(); anchorX=a.right-size-ViewStyles.dp(service,8f); anchorY=a.top+(a.height*.25).toInt()
        bubbleParams=params(size,size,"LoveAdvisorBubble"); drag(icon)
    }
    private fun params(width:Int,height:Int,titleValue:String)=WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.LEFT; title=titleValue;setFitInsetsTypes(0) }
    fun showBubble() {
        if(!service.overlayAllowed()) return
        create(); layout()
        panel?.let { panelWindow.attach { manager.addView(it,panelParams) } }
        bubble?.let { bubbleWindow.attach { manager.addView(it,bubbleParams) } }
    }
    fun show() {
        if(!service.overlayAllowed()) return
        showBubble(); if(panel!=null) return
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
            panel?.toggleMenu(listOf("完整咨询" to ConsultationActivity::class.java,"回复风格设置" to ReplyStyleActivity::class.java,"模型设置" to SettingsActivity::class.java,"连接诊断" to DiagnosticsActivity::class.java).map { (label,activity) -> label to {
                service.startActivity(Intent(service,activity).putExtra("contactTitle",service.currentChatSnapshot?.identity?.displayTitle).putExtra("targetPackage",service.currentChatSnapshot?.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } })
        },{ hide() },{ graph.tracker.sameConversation(it) && ChatApps.supported(it.packageName) },{ graph.settings.load().reducedMotion },{ plan,token,callback -> service.describe(plan,token,callback) })
        view.contentDescription="聊天候选面板"
        panel=view; panelParams=params(1,1,"LoveAdvisorPanel"); panelOnLeft=null
        drag(view.dragHandle); view.setReading(reading); view.setFilling(filling); view.render(state); layout(); panelWindow.attach { manager.addView(view,panelParams) }
        OverlayAnimator.enter(view,graph.settings.load().reducedMotion,panel=true)
    }
    fun render(value:BatchState) { if(state==value) return; state=value; panel?.render(value);layout() }
    fun setReading(value:Boolean) { reading=value; panel?.setReading(value) }
    fun themeChanged() { val expanded=panel!=null;hide();if(expanded && service.overlayAllowed())show() }
    fun hide() { cancelFill?.invoke();cancelFill=null;filling=false;confirmation?.close();confirmation=null; panel?.let { it.release(); panelWindow.detach { manager.removeViewImmediate(it) } }; panel=null; panelParams=null; panelOnLeft=null; layout() }
    /** Retain the shared anchor and expansion while temporarily detaching both surfaces. */
    fun suppress() { if(confirmation!=null)filling=false;confirmation?.close();confirmation=null;panel?.let { it.dismissMenu();it.stopAnimations(); panelWindow.detach { manager.removeViewImmediate(it) } }; bubble?.let { bubbleWindow.detach { manager.removeViewImmediate(it) } } }
    fun resize() {
        val ime=service.windows.any { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        keyboardVisible=ime; layout()
    }
    private fun available():RectI {
        val metrics=manager.maximumWindowMetrics; val r=metrics.bounds; val inset=metrics.windowInsets.getInsets(WindowInsets.Type.systemBars())
        val windowIme=service.windows.firstOrNull { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.let { val b=Rect(); it.getBoundsInScreen(b); b.top }?.takeIf { it>r.top+r.height()*.25 && it<r.bottom }
        val insetIme=imeInsetBottom.takeIf { it>0 }?.let { r.bottom-it }
        val ime=listOfNotNull(windowIme,insetIme).minOrNull()
        val top=r.top+inset.top
        // Keep the composer and its toolbar visible above the keyboard even if no editable nodes are exposed.
        val bottom=ime?.let { OverlayGeometry.composerSafeBottom(it,service.resources.displayMetrics.density) } ?: (r.bottom-inset.bottom)
        return RectI(r.left+inset.left,top,maxOf(r.left+inset.left+1,r.right-inset.right),maxOf(top+1,minOf(r.bottom-inset.bottom,bottom)))
    }
    private fun layout() {
        val icon=bubble ?: return; val bp=bubbleParams ?: return
        val a=available(); val size=ViewStyles.dp(service,40f).coerceAtMost(minOf(a.width,a.height)).coerceAtLeast(1)
        val anchor=PanelBounds(anchorX ?: a.left,anchorY ?: a.top,size,size)
        val ball:PanelBounds; val candidate:PanelBounds?
        if(panel==null) { ball=OverlayGeometry.clamp(anchor,a); candidate=null }
        else {
            val initial=OverlayGeometry.measure(a,InsetsI(),null,service.resources.displayMetrics.density)
            val measured=initial.copy(heightPx=panel!!.preferredHeight(initial.widthPx).coerceIn(1,initial.heightPx))
            val dock=OverlayGeometry.dock(anchor,measured,a,panelOnLeft)
            ball=dock.bubble; candidate=dock.panel
            if(panelOnLeft==null) panelOnLeft=candidate.leftPx<ball.leftPx
        }
        anchorX=ball.leftPx; anchorY=ball.topPx
        fun position(view:View,p:WindowManager.LayoutParams,b:PanelBounds,window:WindowRegistration) {
            val changed=p.x!=b.leftPx || p.y!=b.topPx || p.width!=b.widthPx || p.height!=b.heightPx
            p.x=b.leftPx; p.y=b.topPx; p.width=b.widthPx; p.height=b.heightPx
            if(changed && window.registered) manager.updateViewLayout(view,p)
        }
        // Apply the two positions together on the main thread from the same dock result.
        panel?.let { v -> candidate?.let { b -> panelParams?.let { position(v,it,b,panelWindow) }; v.compact(b.heightPx<200*service.resources.displayMetrics.density) } }
        position(icon,bp,ball,bubbleWindow)
    }
    private fun drag(view:View) {
        var x=0f; var y=0f; var px=0; var py=0; var moved=false
        view.setOnTouchListener { v,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x=event.rawX; y=event.rawY; px=anchorX ?: 0; py=anchorY ?: 0; moved=false; true }
                MotionEvent.ACTION_MOVE -> { val dx=event.rawX-x; val dy=event.rawY-y; if(abs(dx)+abs(dy)>ViewStyles.dp(service,6f)) moved=true; if(moved) { anchorX=(px+dx).toInt(); anchorY=(py+dy).toInt(); layout() }; true }
                MotionEvent.ACTION_UP -> { if(!moved) v.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }
    fun destroy() { hide();suppress();bubble?.drawable?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle() }; bubble=null; panel=null; bubbleParams=null; panelParams=null; graph.controller.unlisten(listener) }
}
