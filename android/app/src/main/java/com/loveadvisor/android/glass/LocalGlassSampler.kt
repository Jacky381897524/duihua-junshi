package com.loveadvisor.android.glass

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.loveadvisor.android.capture.ocr.*
import com.loveadvisor.core.api.Cancellation
import com.loveadvisor.core.model.RectI

data class GlassTarget(val windowId:Int,val windowBounds:RectI,val panelBounds:RectI)
/** Sampling pixels locally never enters the message reader or model request pipeline. */
class LocalGlassSampler(private val service:AccessibilityService,private val target:()->GlassTarget?,private val frame:(Bitmap)->Unit,private val error:(String)->Unit,private val clear:()->Unit={}) {
    private val main=Handler(Looper.getMainLooper()); private var running=false;private var cancel:Cancellation?=null
    private var reported=false
    private val tick=Runnable { sample() }
    fun start() { if(running)return;running=true;reported=false;main.post(tick) }
    fun stop() { running=false;cancel?.cancel();cancel=null;main.removeCallbacks(tick) }
    private fun sample() {
        if(!running)return
        val expected=target() ?: run { clear();stop();return }
        if(Build.VERSION.SDK_INT<34) { clear();if(!reported) { reported=true;error("此系统不支持窗口玻璃采样，使用清晰磨砂底色") };stop();return }
        val lease=Cancellation();cancel=lease
        try {
            WindowCapture(service).capture(expected.windowId,lease) { result ->
                if(result is CaptureImageResult.Success) {
                    try {
                        if(running && !lease.cancelled && expected==target()) {
                            val b=result.bitmap;val w=expected.windowBounds;val p=expected.panelBounds
                            val scaleX=b.width.toFloat()/w.width.coerceAtLeast(1);val scaleY=b.height.toFloat()/w.height.coerceAtLeast(1)
                            val x=((p.left-w.left)*scaleX).toInt().coerceIn(0,b.width-1);val y=((p.top-w.top)*scaleY).toInt().coerceIn(0,b.height-1)
                            val width=(p.width*scaleX).toInt().coerceIn(1,b.width-x);val height=(p.height*scaleY).toInt().coerceIn(1,b.height-y)
                            val crop=Bitmap.createBitmap(b,x,y,width,height)
                            val scaled=if(crop.width>700) Bitmap.createScaledBitmap(crop,700,(crop.height*700f/crop.width).toInt().coerceAtLeast(1),true) else crop
                            frame(scaled);if(scaled!==crop)scaled.recycle();if(crop!==b)crop.recycle()
                        }
                    } finally { result.bitmap.recycle() }
                } else if(running && !lease.cancelled) { clear();if(!reported) { reported=true;error((result as CaptureImageResult.Failure).message+"；暂用清晰磨砂底色") } }
                if(running && !lease.cancelled) main.postDelayed(tick,if(result is CaptureImageResult.Success) 750 else 3000)
            }
        } catch(_:Exception) { if(running) { clear();main.postDelayed(tick,3000) } }
    }
}
