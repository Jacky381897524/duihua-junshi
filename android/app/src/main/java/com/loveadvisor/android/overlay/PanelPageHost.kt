package com.loveadvisor.android.overlay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.overlay.PanelPageStack

/** All settings and selectors live inside the existing accessibility window. */
class PanelPageHost(context:Context,private val onFocusChanged:(Boolean)->Unit,private val onChanged:()->Unit):FrameLayout(context) {
    private data class Page(val viewport:ScrollView,val content:View,val closed:()->Unit)
    private val stack=PanelPageStack()
    private val pages=mutableListOf<Page>()
    val hasPages get()=stack.depth>0
    val currentId get()=stack.currentId
    init {
        visibility=GONE;isClickable=true
        background=ViewStyles.shape(context,PanelPalette(context).opaqueSurface,24f)
        clipToOutline=true
    }
    fun push(id:String,view:View,editable:Boolean=false,onClosed:()->Unit={}) {
        if(!stack.push(id,editable))return
        pages.lastOrNull()?.viewport?.visibility=GONE
        (view.parent as? ViewGroup)?.removeView(view)
        val viewport=ScrollView(context).apply {
            setPadding(dp(14),dp(14),dp(14),dp(12));clipToPadding=true;isFillViewport=false
            addView(view,LayoutParams(-1,-2));isClickable=true
        }
        pages.add(Page(viewport,view,onClosed));addView(viewport,LayoutParams(-1,-1));visibility=VISIBLE
        view.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> onChanged() }
        onFocusChanged(stack.editable);onChanged()
    }
    fun pop():Boolean {
        if(!stack.pop())return false
        val removed=pages.removeAt(pages.lastIndex);removeView(removed.viewport)
        pages.lastOrNull()?.viewport?.visibility=VISIBLE
        if(!hasPages)visibility=GONE
        onFocusChanged(stack.editable);removed.closed();onChanged();return true
    }
    fun clear() {
        if(!hasPages)return
        val removed=pages.toList();pages.clear();stack.clear();removeAllViews();visibility=GONE
        onFocusChanged(false);removed.asReversed().forEach { it.closed() };onChanged()
    }
    fun preferredHeight(widthPx:Int):Int {
        val page=pages.lastOrNull() ?: return 0
        page.content.measure(MeasureSpec.makeMeasureSpec((widthPx-dp(28)).coerceAtLeast(1),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
        return maxOf(dp(240),page.content.measuredHeight+dp(26))
    }
    private fun dp(value:Int)=ViewStyles.dp(context,value.toFloat())
}
