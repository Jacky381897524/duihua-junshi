package com.loveadvisor.android.overlay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.graphics.Rect
import android.widget.EditText
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ScrollView
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.overlay.PanelPageStack
import com.loveadvisor.core.overlay.PanelEditingFocus

/** Pages with pinned controls own their internal scroll viewport. */
interface PanelViewportPage { val preferredViewportHeightDp:Int }

/** All settings and selectors live inside the existing accessibility window. */
class PanelPageHost(context:Context,private val onFocusChanged:(Boolean)->Unit,private val onChanged:()->Unit):FrameLayout(context) {
    private data class Page(val viewport:ViewGroup,val content:View,val closed:()->Unit)
    private val stack=PanelPageStack()
    private val editing=PanelEditingFocus()
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
        val viewport=if(view is PanelViewportPage)FrameLayout(context).apply {
            setPadding(dp(14),dp(14),dp(14),dp(12));addView(view,LayoutParams(-1,-1));isClickable=true
        } else ScrollView(context).apply {
            setPadding(dp(14),dp(14),dp(14),dp(12));clipToPadding=true;isFillViewport=false
            addView(view,LayoutParams(-1,-2));isClickable=true
        }
        pages.add(Page(viewport,view,onClosed));addView(viewport,LayoutParams(-1,-1));visibility=VISIBLE
        view.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> onChanged() }
        editing.pageChanged(stack.editable);onFocusChanged(false);onChanged()
    }
    fun pop():Boolean {
        if(!stack.pop())return false
        val removed=pages.removeAt(pages.lastIndex);removeView(removed.viewport)
        pages.lastOrNull()?.viewport?.visibility=VISIBLE
        if(!hasPages)visibility=GONE
        editing.pageChanged(stack.editable);onFocusChanged(false);removed.closed();onChanged();return true
    }
    fun clear() {
        if(!hasPages)return
        val removed=pages.toList();pages.clear();stack.clear();removeAllViews();visibility=GONE
        editing.pageChanged(false);onFocusChanged(false);removed.asReversed().forEach { it.closed() };onChanged()
    }
    fun releaseEditingFocus() { editing.release();onFocusChanged(false) }
    override fun dispatchTouchEvent(event:MotionEvent):Boolean {
        // Acquire window/IME focus for a real field tap, never just for opening or
        // reattaching a retained page. Coordinates are used locally, not logged.
        if(event.actionMasked==MotionEvent.ACTION_DOWN)fieldAt(pages.lastOrNull()?.content,event.rawX.toInt(),event.rawY.toInt())?.let { field ->
            if(editing.fieldTapped()) { onFocusChanged(true);field.requestFocus();showKeyboardAfterFocus(field) }
        }
        return super.dispatchTouchEvent(event)
    }
    override fun onWindowFocusChanged(hasWindowFocus:Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if(hasWindowFocus && editing.requested)(findFocus() as? EditText)?.let(::showKeyboardAfterFocus)
    }
    private fun showKeyboardAfterFocus(field:EditText) { field.post {
        if(editing.requested && field.isAttachedToWindow && field.hasFocus() && field.hasWindowFocus())context.getSystemService(InputMethodManager::class.java)?.showSoftInput(field,InputMethodManager.SHOW_IMPLICIT)
    } }
    private fun fieldAt(view:View?,x:Int,y:Int):EditText? {
        if(view==null || !view.isShown || !view.isEnabled)return null
        val bounds=Rect();if(!view.getLocalVisibleRect(bounds))return null
        val screen=IntArray(2);view.getLocationOnScreen(screen);bounds.offset(screen[0],screen[1])
        if(!bounds.contains(x,y))return null
        if(view is EditText)return view
        if(view is ViewGroup)for(i in view.childCount-1 downTo 0)fieldAt(view.getChildAt(i),x,y)?.let { return it }
        return null
    }
    fun preferredHeight(widthPx:Int):Int {
        val page=pages.lastOrNull() ?: return 0
        (page.content as? PanelViewportPage)?.let { return dp(it.preferredViewportHeightDp) }
        page.content.measure(MeasureSpec.makeMeasureSpec((widthPx-dp(28)).coerceAtLeast(1),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
        return maxOf(dp(240),page.content.measuredHeight+dp(26))
    }
    private fun dp(value:Int)=ViewStyles.dp(context,value.toFloat())
}
