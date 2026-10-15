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
    private var locked=false
    private var factor=1f
    private val gesture=CapsuleDragGesture(ViewConfiguration.getLongPressTimeout().toLong(),ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    private val hold=Runnable { if(gesture.hold(SystemClock.uptimeMillis()))performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    init {
        orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        background=ViewStyles.shape(context,0xFF181A20.toInt(),11f,0xFF434752.toInt());clipToOutline=true;elevation=0f
        setPadding(dp(4),0,dp(3),0)
        logo.setImageBitmap(bitmap);logo.scaleType=ImageView.ScaleType.CENTER_CROP
        logo.background=ViewStyles.shape(context,0xFFFFDA3B.toInt(),7f);logo.clipToOutline=true
        addView(logo,LayoutParams(dp(14),dp(14)).apply { rightMargin=dp(2) })
        label.setTextColor(0xFFFFFFFF.toInt());label.gravity=Gravity.CENTER_VERTICAL
        label.isSingleLine=true;label.ellipsize=TextUtils.TruncateAt.END;label.includeFontPadding=false
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP,9f)
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
    fun contentWidthDp():Float {
        val paint=android.text.TextPaint(label.paint).apply { textSize=label.textSize/factor }
        return 24f+paint.measureText(label.text.toString())/resources.displayMetrics.density
    }
    fun applyOptions(isLocked:Boolean,scale:Float) {
        gesture.setLocked(isLocked);locked=isLocked
        val next=scale.takeIf { it.isFinite() }?.coerceIn(.7f,2f) ?: 1f
        if(factor!=next) {
            factor=next
            fun size(value:Float)=ViewStyles.dp(context,value*factor)
            setPadding(size(4f),0,size(3f),0)
            logo.layoutParams=LayoutParams(size(14f),size(14f)).apply { rightMargin=size(2f) }
            logo.background=ViewStyles.shape(context,0xFFFFDA3B.toInt(),7f*factor)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP,9f*factor)
            background=ViewStyles.shape(context,0xFF181A20.toInt(),11f*factor,0xFF434752.toInt())
        }
        updateDescription()
    }
    private fun updateDescription() { contentDescription="对话军师，${label.text}，点按打开候选，"+if(locked)"位置已锁定" else "长按拖动" }
    fun setStatus(value:String) {
        if(label.text.toString()==value)return
        label.text=value
        updateDescription()
    }
    fun cancelGesture() { removeCallbacks(hold);gesture.cancel();onCancel() }
    override fun onDetachedFromWindow() { cancelGesture();super.onDetachedFromWindow() }
    fun release() { cancelGesture();logo.setImageDrawable(null);bitmap?.recycle() }
}
