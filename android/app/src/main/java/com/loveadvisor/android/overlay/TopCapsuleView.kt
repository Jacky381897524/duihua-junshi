package com.loveadvisor.android.overlay

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.widget.*
import com.loveadvisor.android.R
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.overlay.*

/** Own accessibility overlay entry: no notification, screen collection or model dependency. */
class TopCapsuleView(context:Context,onTap:()->Unit,onBegin:()->Unit,onMove:(Float,Float)->Unit,onCommit:()->Unit,private val onCancel:()->Unit):LinearLayout(context) {
    private val logo=ImageView(context)
    private val bitmap=BitmapFactory.decodeResource(resources,R.drawable.advisor_logo_new,BitmapFactory.Options().apply { inScaled=false;inSampleSize=16 })
    private val label=TextView(context)
    private val gesture=CapsuleDragGesture(ViewConfiguration.getLongPressTimeout().toLong(),ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    private val hold=Runnable { if(gesture.hold(SystemClock.uptimeMillis()))performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    init {
        orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        background=ViewStyles.shape(context,0xFF181A20.toInt(),11f,0xFF434752.toInt());clipToOutline=true;elevation=0f
        setPadding(dp(5),0,dp(5),0)
        logo.setImageBitmap(bitmap);logo.scaleType=ImageView.ScaleType.CENTER_CROP
        logo.background=ViewStyles.shape(context,0xFFFFDA3B.toInt(),7f);logo.clipToOutline=true
        addView(logo,LayoutParams(dp(14),dp(14)).apply { rightMargin=dp(3) })
        label.setTextColor(0xFFFFFFFF.toInt());label.gravity=Gravity.CENTER_VERTICAL
        label.isSingleLine=true;label.ellipsize=TextUtils.TruncateAt.END;label.includeFontPadding=false
        label.setAutoSizeTextTypeUniformWithConfiguration(7,9,1,TypedValue.COMPLEX_UNIT_SP)
        addView(label,LayoutParams(0,-1,1f))
        setOnClickListener { onTap() }
        setOnTouchListener { v,event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    removeCallbacks(hold);onBegin();gesture.down(event.rawX,event.rawY,event.eventTime)
                    postDelayed(hold,ViewConfiguration.getLongPressTimeout().toLong());true
                }
                MotionEvent.ACTION_MOVE -> { gesture.move(event.rawX,event.rawY,event.eventTime)?.let { onMove(it.x,it.y) };true }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(hold)
                    when(gesture.finish(event.eventTime)) { CapsuleTouchAction.CLICK -> v.performClick();CapsuleTouchAction.DRAG_END -> onCommit();CapsuleTouchAction.NONE -> onCancel() }
                    true
                }
                MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN -> { cancelGesture();true }
                else -> true
            }
        }
        setStatus("待读取")
    }
    private fun dp(value:Int)=ViewStyles.dp(context,value.toFloat())
    fun setStatus(value:String) {
        if(label.text.toString()==value)return
        label.text=value
        contentDescription="对话军师，$value，点按打开候选，长按拖动"
    }
    fun cancelGesture() { removeCallbacks(hold);gesture.cancel();onCancel() }
    override fun onDetachedFromWindow() { cancelGesture();super.onDetachedFromWindow() }
    fun release() { cancelGesture();logo.setImageDrawable(null);bitmap?.recycle() }
}
