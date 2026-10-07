package com.loveadvisor.android.overlay
import android.animation.ValueAnimator
import android.view.View
object OverlayAnimator {
    fun enter(view:View,reduced:Boolean,delay:Long=0,panel:Boolean=false) {
        view.animate().cancel()
        if(reduced || !ValueAnimator.areAnimatorsEnabled()) { stop(view); return }
        view.alpha=0f; view.translationY=if(panel) 0f else 12*view.resources.displayMetrics.density
        view.scaleX=1f; view.scaleY=1f
        view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(if(panel) 200 else 180).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }
    fun stop(view:View) { view.animate().cancel(); view.alpha=1f; view.translationY=0f; view.scaleX=1f; view.scaleY=1f }
}
