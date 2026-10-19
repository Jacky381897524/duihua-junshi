package com.loveadvisor.android.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.KeyEvent
import android.widget.*
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.android.R
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.model.SessionToken

enum class PanelDragMode { TITLE, TOP_GRIP }
data class CapsuleMenuControls(val locked:Boolean,val scale:Float,val toggleLock:()->Boolean,val resize:(Float,Boolean)->Unit,val finishResize:()->Unit)
class AdvisorPanelView(context:Context,private val onCopy:(ReplyPlan)->Unit,private val onFill:(ReplyPlan,SessionToken)->Unit,private val onRefresh:()->Unit,onRead:()->Unit,onMenu:(View)->Unit,onClose:()->Unit,private val isCurrent:(SessionToken)->Boolean,private val reduced:()->Boolean,private val onDetails:((ReplyPlan,SessionToken,(ReplyDetail?,String?)->Unit)->Unit)?=null,dragMode:PanelDragMode=PanelDragMode.TITLE,private val onResize:()->Unit={},private val frosted:Boolean=false,private val onEditingFocus:(Boolean)->Unit={},onAnalyze:()->Unit={}):FrameLayout(context) {
    val dragHandle:View
    private val palette=PanelPalette(context,frosted)
    private val scoreLabel=if(com.loveadvisor.android.AppGraph.get(context).settings.load().judgeProvider=="CUSTOM")"评分" else "Jev"
    private val column=ViewStyles.column(context)
    private val summary:TextView; private val status:TextView; private val scroll:ScrollView
    private val advice:TextView;private val level:TextView
    private val analysisRow:LinearLayout
    private var shortActionLabels=false
    private val analyze:Button
    private var analysisAvailable=false
    private var readContextTitle:String?=null
    private var statusMessage="等待点击读取；聊天变化不会自动请求模型"
    val currentPageId get()=pages.currentId
    private val cards:LinearLayout; private val refresh:Button; private val read:Button; private val count:TextView
    private var displayed:VisibleBatch?=null
    private var reading=false; private var loading=false; private var filling=false
    private var menuLayer:View?=null
    private var menuContent:LinearLayout?=null
    private var menuFinished:(()->Unit)?=null
    private val pages=PanelPageHost(context,onEditingFocus) { invalidateHeight();installEditorBack();onResize() }
    private var removeEditorBack:(()->Unit)?=null
    private var cachedWidth=-1;private var cachedHeight=0;private var compacted=false
    val menuExpanded get()=menuLayer!=null || pages.hasPages
    private val collapseHeight get()=dp(30)
    private fun textRowHeight(value:Int)=ViewStyles.dp(context,value*resources.configuration.fontScale.coerceAtLeast(1f))
    init {
        useFrostedWindow(frosted); clipToOutline=true; elevation=0f
        val pad=dp(12); column.setPadding(pad,pad,pad,0); addView(column,LayoutParams(-1,-1).apply { bottomMargin=collapseHeight })
        val header=ViewStyles.row(context)
        val title=palette.text("军师分析",17f,bold=true).apply { maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END }
        dragHandle=if(dragMode==PanelDragMode.TOP_GRIP) {
            val row=FrameLayout(context)
            val grip=palette.text("•••",18f,palette.blue,bold=true).apply { gravity=Gravity.CENTER;contentDescription="拖动候选面板" }
            row.addView(grip,FrameLayout.LayoutParams(dp(64),dp(24),Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            column.addView(row,LinearLayout.LayoutParams(-1,dp(24)))
            grip
        } else title
        header.addView(title,LinearLayout.LayoutParams(0,-1,1f))
        val menu=palette.button("⋯") {}; menu.contentDescription="更多设置"
        menu.setOnClickListener { onMenu(menu) }
        header.addView(menu,LinearLayout.LayoutParams(textRowHeight(28),textRowHeight(28)))
        val close=palette.button("×") { onClose() }; close.contentDescription="收起候选窗"
        header.addView(close,LinearLayout.LayoutParams(textRowHeight(28),textRowHeight(28)).apply { leftMargin=dp(6) })
        column.addView(header,rowParams(textRowHeight(28),9))
        analysisRow=ViewStyles.row(context).apply { gravity=Gravity.CENTER_VERTICAL }
        summary=palette.text("对方真实意图：读取后判断",12f,bold=true).apply { maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END }
        analysisRow.addView(summary,LinearLayout.LayoutParams(0,-2,1f))
        analyze=palette.button("判断分析") { onAnalyze() }.apply { contentDescription="判断分析" }
        analysisRow.addView(analyze,LinearLayout.LayoutParams(dp(52),textRowHeight(28)).apply { leftMargin=dp(4) })
        read=palette.button("读取信息") { onRead() }.apply { contentDescription="读取信息";maxLines=2 }
        analysisRow.addView(read,LinearLayout.LayoutParams(dp(52),textRowHeight(28)).apply { leftMargin=dp(4) })
        listOf(analyze,read).forEach { it.textSize=10.5f;it.setPadding(dp(4),0,dp(4),0) }
        column.addView(analysisRow,rowParams(-2,5))
        status=palette.text(statusMessage,11f,palette.muted).apply { maxLines=2 }
        column.addView(status,rowParams(-2,10))
        val label=ViewStyles.row(context)
        advice=palette.text("建议：先读取当前对话",12f).apply { maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END }
        column.addView(advice,rowParams(-2,6))
        level=palette.text("等级 · 待判断",11f,palette.muted)
        label.addView(level,LinearLayout.LayoutParams(0,-2,1f))
        count=palette.text("0 / 6",11f,palette.muted); label.addView(count)
        column.addView(label,rowParams(-2,7))
        scroll=ScrollView(context).apply { isFillViewport=false; clipToPadding=true; isVerticalScrollBarEnabled=true }
        cards=ViewStyles.column(context); scroll.addView(cards); column.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        scroll.setOnScrollChangeListener { _,_,_,_,_ -> updateCount() }
        refresh=palette.button("刷新换一批 · 6 条") { onRefresh() }.apply { textSize=12f }
        column.addView(refresh,LinearLayout.LayoutParams(-1,textRowHeight(30)).apply { topMargin=dp(9) })
        val collapse=FrameLayout(context).apply {
            contentDescription="收起候选面板";isFocusable=true;setOnClickListener { onClose() }
        }
        collapse.addView(View(context).apply {
            setBackgroundColor(palette.border);alpha=.45f
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },FrameLayout.LayoutParams(-1,dp(1),Gravity.TOP).apply { leftMargin=pad;rightMargin=pad;topMargin=dp(6) })
        // Center inside the area below the divider; a margin on a centered icon
        // shifts the icon itself and can leave it against the panel's bottom edge.
        val collapseContent=FrameLayout(context)
        collapse.addView(collapseContent,FrameLayout.LayoutParams(-1,-1).apply { topMargin=dp(7) })
        collapseContent.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_panel_collapse)
            imageTintList=ColorStateList.valueOf(palette.blue)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },FrameLayout.LayoutParams(dp(20),dp(14),Gravity.CENTER))
        // Keep the thin close band reachable even when large fonts fill a short viewport.
        addView(collapse,LayoutParams(-1,collapseHeight,Gravity.BOTTOM))
        addView(pages,LayoutParams(-1,-1).apply { bottomMargin=collapseHeight })
        updateButtons()
    }
    private fun dp(value:Int)=ViewStyles.dp(context,value.toFloat())
    fun useFrostedWindow(enabled:Boolean) { background=ViewStyles.shape(context,if(enabled)android.graphics.Color.TRANSPARENT else palette.opaqueSurface,24f,palette.border) }
    private fun invalidateHeight() { cachedWidth=-1 }
    private fun rowParams(height:Int,bottom:Int)=LinearLayout.LayoutParams(-1,height).apply { bottomMargin=dp(bottom) }
    private fun updateButtons() {
        analyze.alpha=if(analysisAvailable && !reading && !loading && !filling)1f else .45f
        read.isEnabled=!reading && !loading && !filling; read.text=if(reading) "读取中…" else if(shortActionLabels)"读取" else "读取信息"
        refresh.isEnabled=!reading && !loading && !filling && displayed?.let { isCurrent(it.ticket.session) }==true
        refresh.text=if(loading) "正在生成六条…" else "刷新换一批 · 6 条"
        for(i in 0 until cards.childCount) (cards.getChildAt(i) as? ReplyCardView)?.let { card ->
            card.copyButton.isEnabled=!filling
            card.fillButton?.let { button -> button.isEnabled=!filling && !reading && !loading && displayed?.let { isCurrent(it.ticket.session) }==true;button.alpha=if(button.isEnabled)1f else .45f }
        }
    }
    fun setReading(value:Boolean) { reading=value; updateButtons() }
    fun setAnalysisAvailable(value:Boolean) { analysisAvailable=value;updateButtons() }
    fun setReadContext(title:String?) { readContextTitle=title;renderStatus() }
    fun setFilling(value:Boolean) { filling=value; updateButtons() }
    private fun renderStatus() { status.text=readContextTitle?.let { "已读：$it\n$statusMessage" } ?: statusMessage;invalidateHeight() }
    fun fillResult(message:String) { statusMessage=message;renderStatus() }
    fun release() { dismissMenu();closeStyleEditor();stopAnimations() }
    fun showStyleEditor(editor:View,closed:()->Unit) {
        dismissMenu();pages.clear();pages.push("reply-style",editor,true,closed)
    }
    fun showPage(id:String,content:View,editable:Boolean=false,closed:()->Unit={}) {
        dismissMenu();pages.push(id,content,editable,closed)
    }
    fun popPage()=pages.pop()
    fun closeStyleEditor() { pages.clear() }
    private fun installEditorBack() {
        if(!pages.hasPages) { removeEditorBack?.invoke();removeEditorBack=null;return }
        if(android.os.Build.VERSION.SDK_INT>=33 && removeEditorBack==null) {
            val dispatcher=findOnBackInvokedDispatcher() ?: return
            val callback=android.window.OnBackInvokedCallback { pages.pop() }
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback)
            removeEditorBack={ dispatcher.unregisterOnBackInvokedCallback(callback) }
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();installEditorBack() }
    override fun onDetachedFromWindow() { removeEditorBack?.invoke();removeEditorBack=null;super.onDetachedFromWindow() }
    override fun dispatchKeyEvent(event:KeyEvent):Boolean {
        if(pages.hasPages && event.keyCode==KeyEvent.KEYCODE_BACK) {
            if(event.action==KeyEvent.ACTION_UP)pages.pop()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    /** An in-panel menu stays in the accessibility overlay and creates no application popup window. */
    fun toggleMenu(items:List<Pair<String,()->Unit>>,capsule:CapsuleMenuControls?=null) {
        if(menuLayer!=null) { dismissMenu();return }
        val layer=FrameLayout(context).apply { contentDescription="悬浮窗设置菜单";setOnClickListener { dismissMenu() } }
        val menu=ViewStyles.column(context,8).apply { background=ViewStyles.shape(context,palette.surface,16f,palette.border);elevation=dp(4).toFloat();setOnClickListener {} }
        fun item(entry:Pair<String,()->Unit>) { menu.addView(palette.button(entry.first) { dismissMenu();entry.second() }.apply {
            minimumHeight=textRowHeight(34);setPadding(dp(10),dp(5),dp(10),dp(5))
        },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(4) }) }
        items.firstOrNull()?.let { item(it) }
        if(capsule!=null) {
            val lock=palette.button("") {}
            fun showLock(locked:Boolean) {
                val color=if(locked)0xFFC53838.toInt() else 0xFF16723E.toInt()
                lock.text=if(locked)"解锁胶囊 · 已锁定" else "锁定胶囊 · 已解锁"
                lock.setTextColor(color);lock.background=ViewStyles.shape(context,if(locked)0xFFFFE8E8.toInt() else 0xFFE6F6EB.toInt(),12f,color)
                lock.contentDescription=lock.text
            }
            showLock(capsule.locked);lock.setOnClickListener { showLock(capsule.toggleLock()) }
            lock.minimumHeight=textRowHeight(36);lock.setPadding(dp(10),dp(5),dp(10),dp(5))
            menu.addView(lock,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(7) })
            val sizeLabel=palette.text("胶囊大小 · ${(capsule.scale*100).toInt()}%",12f)
            menu.addView(sizeLabel)
            val slider=SeekBar(context).apply { max=1300;progress=((capsule.scale-.7f)*1000).toInt().coerceIn(0,1300);contentDescription="胶囊大小，70%至200%" }
            slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(view:SeekBar,progress:Int,fromUser:Boolean) { if(fromUser) { val value=.7f+progress/1000f;sizeLabel.text="胶囊大小 · ${(value*100).toInt()}%";capsule.resize(value,false) } }
                override fun onStartTrackingTouch(view:SeekBar) {}
                override fun onStopTrackingTouch(view:SeekBar) { capsule.resize(.7f+view.progress/1000f,true) }
            })
            menu.addView(slider,LinearLayout.LayoutParams(-1,dp(36)))
            val range=ViewStyles.row(context)
            range.addView(palette.text("70%",10f,palette.muted),LinearLayout.LayoutParams(0,-2,1f))
            range.addView(palette.text("200%",10f,palette.muted))
            menu.addView(range,LinearLayout.LayoutParams(-1,-2))
            menuFinished=capsule.finishResize
        }
        items.drop(1).forEach { item(it) }
        val viewport=ScrollView(context).apply { isFillViewport=false;addView(menu) }
        layer.addView(viewport,LayoutParams(menuWidth(width),-2,Gravity.TOP or Gravity.RIGHT).apply { topMargin=dp(44);rightMargin=dp(12);bottomMargin=dp(6) })
        menuLayer=layer;menuContent=menu;addView(layer,LayoutParams(-1,-1).apply { bottomMargin=collapseHeight })
        invalidateHeight();onResize()
    }
    fun dismissMenu() {
        val wasOpen=menuExpanded
        val finished=menuFinished;menuFinished=null
        menuLayer?.let { removeView(it) };menuLayer=null;menuContent=null
        finished?.invoke()
        if(wasOpen) { invalidateHeight();onResize() }
    }
    /** Measure real cards including font scaling; long replies remain fully scrollable. */
    fun preferredHeight(widthPx:Int):Int {
        if(pages.hasPages)return pages.preferredHeight(widthPx)+collapseHeight
        if(cachedWidth==widthPx)return cachedHeight
        val available=(widthPx-column.paddingLeft-column.paddingRight).coerceAtLeast(1)
        sizeHeader(available)
        var height=column.paddingTop+column.paddingBottom+collapseHeight
        for(i in 0 until column.childCount) {
            val child=column.getChildAt(i); if(child===scroll) continue
            val lp=child.layoutParams as LinearLayout.LayoutParams
            child.measure(MeasureSpec.makeMeasureSpec(available,MeasureSpec.EXACTLY),if(lp.height>=0) MeasureSpec.makeMeasureSpec(lp.height,MeasureSpec.EXACTLY) else MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
            height+=child.measuredHeight+lp.topMargin+lp.bottomMargin
        }
        if(cards.childCount==0) height+=dp(90)
        for(i in 0 until minOf(3,cards.childCount)) {
            val card=cards.getChildAt(i)
            card.measure(MeasureSpec.makeMeasureSpec(available,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
            val lp=card.layoutParams as LinearLayout.LayoutParams
            height+=card.measuredHeight+lp.topMargin+if(i<2) lp.bottomMargin else 0
        }
        menuContent?.let { menu ->
            menu.measure(MeasureSpec.makeMeasureSpec(menuWidth(widthPx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
            height=maxOf(height,dp(44)+menu.measuredHeight+dp(6)+collapseHeight)
        }
        cachedWidth=widthPx;cachedHeight=height
        return height
    }
    private fun updateCount() {
        val top=scroll.scrollY; val bottom=top+scroll.height
        val visible=(0 until cards.childCount).filter { val c=cards.getChildAt(it);c.top>=top-1 && c.bottom<=bottom+1 }
        count.text=if(visible.isEmpty()) if(cards.childCount==0) "0 / 6" else "6 条 · 滑动查看" else "${visible.first()+1}–${visible.last()+1} / 6"
    }
    private fun menuWidth(panelWidth:Int)=minOf(textRowHeight(200),(panelWidth-dp(24)).coerceAtLeast(1))
    private fun sizeHeader(available:Int) {
        val sizing=com.loveadvisor.core.overlay.PanelHeaderLayout.measure(available,resources.displayMetrics.density,resources.configuration.fontScale)
        shortActionLabels=sizing.shortLabels
        val analyzeLabel=if(shortActionLabels)"判断" else "判断分析"
        val readLabel=if(reading)"读取中…" else if(shortActionLabels)"读取" else "读取信息"
        if(analyze.text.toString()!=analyzeLabel)analyze.text=analyzeLabel
        if(read.text.toString()!=readLabel)read.text=readLabel
        for(action in listOf(analyze,read)) {
            val lp=action.layoutParams as LinearLayout.LayoutParams
            lp.width=sizing.actionWidth;lp.height=sizing.actionHeight;lp.leftMargin=sizing.gap
        }
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        sizeHeader((MeasureSpec.getSize(widthMeasureSpec)-column.paddingLeft-column.paddingRight).coerceAtLeast(1))
        (menuContent?.parent as? ScrollView)?.let { viewport ->
            val lp=viewport.layoutParams
            val next=menuWidth(MeasureSpec.getSize(widthMeasureSpec))
            if(lp.width!=next) { lp.width=next;viewport.layoutParams=lp }
        }
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) { super.onSizeChanged(w,h,oldw,oldh); post { updateCount() } }
    fun render(state:BatchState) {
        invalidateHeight()
        loading=state.loading
        val visible=state.current
        statusMessage=state.error ?: when {
            state.loading && visible!=null -> "保留上一轮 · 正在生成新候选"
            state.loading -> "正在生成并由判断模型排序六条…"
            visible!=null -> if(visible.batch.kind==com.loveadvisor.core.model.ChatKind.DIRECT && visible.batch.replies.firstOrNull()?.targetMessageId!=null)
                "回复原话：${visible.batch.replies.first().targetLabel.orEmpty()}" else "判断仅供参考 · $scoreLabel 为候选适合程度"
            else -> "点击读取信息后生成候选；聊天变化不会自动刷新"
        }
        renderStatus()
        if(visible!=null) {
            val a=visible.batch.analysis
            val current=isCurrent(visible.ticket.session)
            summary.text=if(current)"对方真实意图：${a.intent}" else "对方真实意图：等待重新读取"
            advice.text=if(current)"建议：${a.action}" else "建议：聊天已变化，请重新读取"
            val grade=ConversationLevel.from(if(current)a.risk else null)
            level.text="等级 · ${grade.label}${grade.value?.let { " $it/9" }.orEmpty()}"
            level.setTextColor(palette.levelColor(grade))
            if(displayed!=visible) {
                displayed=visible; cards.removeAllViews()
                visible.batch.replies.forEachIndexed { i,plan -> cards.addView(ReplyCardView(context,i,plan,{ onCopy(plan) },{ onFill(plan,visible.ticket.session) },{ callback -> onDetails?.invoke(plan,visible.ticket.session,callback) ?: callback(null,"请重新读取后查看详情") },compact=true,scoreLabel=scoreLabel,frosted=frosted,onContentChanged={ invalidateHeight();onResize() })) }
                scroll.scrollTo(0,0)
                cards.post { updateCount() }
            }
        } else { displayed=null; cards.removeAllViews(); summary.text="对方真实意图：读取后判断";advice.text="建议：先读取当前对话";level.text="等级 · 待判断";level.setTextColor(palette.muted); count.text="0 / 6" }
        updateButtons()
    }
    fun compact(enabled:Boolean) { if(compacted==enabled)return;compacted=enabled;summary.maxLines=if(enabled) 1 else 2; status.maxLines=if(enabled) 1 else 2;invalidateHeight() }
    fun stopAnimations() { OverlayAnimator.stop(this); for(i in 0 until cards.childCount) OverlayAnimator.stop(cards.getChildAt(i)) }
}
