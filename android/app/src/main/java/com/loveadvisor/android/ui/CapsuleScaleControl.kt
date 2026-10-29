package com.loveadvisor.android.ui

import android.content.Context
import android.view.Gravity
import android.widget.*
import kotlin.math.roundToInt
import com.loveadvisor.android.overlay.PanelPalette

/** One integer percent per slider step or button press, shared by every scale editor. */
class CapsuleScaleControl(context:Context,initial:Float,private val changed:(Float,Boolean)->Unit):LinearLayout(context) {
    private var percent=(initial*100).roundToInt().coerceIn(70,200)
    private val label=PanelPalette(context).text("",12f)
    private val slider=SeekBar(context).apply { max=130;progress=percent-70;contentDescription="胶囊大小" }
    init {
        orientation=VERTICAL;addView(label)
        val palette=PanelPalette(context)
        val row=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL }
        fun step(text:String,delta:Int):android.view.View {
            val button=palette.button(text) { setPercent(percent+delta,true) }.apply {
                textSize=19f;minimumWidth=0;minWidth=0;setPadding(0,0,0,0)
                background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x334E85E8),android.graphics.drawable.GradientDrawable().apply { shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(palette.card);setStroke(ViewStyles.dp(context,1f),palette.border) },null)
                importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            return FrameLayout(context).apply {
                contentDescription=if(delta<0)"缩小胶囊1%" else "放大胶囊1%";isFocusable=true;isClickable=true
                addView(button,FrameLayout.LayoutParams(ViewStyles.dp(context,32f),ViewStyles.dp(context,32f),Gravity.CENTER))
                setOnClickListener { button.performClick() }
            }
        }
        row.addView(step("−",-1),LayoutParams(ViewStyles.dp(context,48f),ViewStyles.dp(context,48f)))
        row.addView(slider,LayoutParams(0,ViewStyles.dp(context,40f),1f))
        row.addView(step("+",1),LayoutParams(ViewStyles.dp(context,48f),ViewStyles.dp(context,48f)))
        addView(row,LayoutParams(-1,-2));render()
        slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view:SeekBar,progress:Int,fromUser:Boolean) { if(fromUser)setPercent(progress+70,false) }
            override fun onStartTrackingTouch(view:SeekBar) {}
            override fun onStopTrackingTouch(view:SeekBar) { changed(percent/100f,true) }
        })
    }
    private fun render() { label.text="胶囊大小 · $percent%";slider.progress=percent-70 }
    private fun setPercent(value:Int,commit:Boolean) { percent=value.coerceIn(70,200);render();changed(percent/100f,commit) }
}
