package com.loveadvisor.android.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import com.loveadvisor.core.advisor.IdentityCheckStatus

/** A fixed-size status symbol; probing never changes the panel's text or height. */
class IdentityCheckView(context:Context,private val palette:PanelPalette,private val reduced:()->Boolean):View(context) {
    private var status=IdentityCheckStatus.IDLE
    private var angle=0f
    private var spinner:ValueAnimator?=null
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND }
    private val ring=RectF()
    fun render(value:IdentityCheckStatus) {
        if(status==value)return
        status=value;contentDescription=when(value) { IdentityCheckStatus.CHECKING->"正在核对对象";IdentityCheckStatus.VERIFIED->"对象已核对";IdentityCheckStatus.UNAVAILABLE->"对象暂未确认";else->"尚未核对对象" }
        updateAnimation();invalidate()
    }
    private fun updateAnimation() {
        stop()
        if(status==IdentityCheckStatus.CHECKING && isAttachedToWindow && isShown && !reduced() && ValueAnimator.areAnimatorsEnabled()) {
            spinner=ValueAnimator.ofFloat(0f,360f).apply { duration=900;repeatCount=ValueAnimator.INFINITE;interpolator=LinearInterpolator();addUpdateListener { angle=it.animatedValue as Float;invalidate() };start() }
        }
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val size=minOf(width,height).toFloat();val inset=size*.19f
        pen.strokeWidth=size*.1f
        ring.set((width-size)/2+inset,(height-size)/2+inset,(width+size)/2-inset,(height+size)/2-inset)
        pen.color=when(status) { IdentityCheckStatus.CHECKING->palette.blue;IdentityCheckStatus.VERIFIED->0xFF56CC91.toInt();else->palette.muted }
        pen.alpha=if(status==IdentityCheckStatus.IDLE || status==IdentityCheckStatus.UNAVAILABLE)100 else 255
        canvas.drawArc(ring,if(status==IdentityCheckStatus.CHECKING)angle-90 else -90f,if(status==IdentityCheckStatus.CHECKING)260f else 360f,false,pen)
        if(status==IdentityCheckStatus.VERIFIED) {
            canvas.drawLine(width*.32f,height*.51f,width*.46f,height*.64f,pen)
            canvas.drawLine(width*.46f,height*.64f,width*.71f,height*.36f,pen)
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();updateAnimation() }
    override fun onDetachedFromWindow() { stop();super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility:Int) { super.onWindowVisibilityChanged(visibility);updateAnimation() }
    fun stop() { spinner?.cancel();spinner=null }
}
