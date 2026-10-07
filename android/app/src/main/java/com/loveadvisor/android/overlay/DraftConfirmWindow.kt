package com.loveadvisor.android.overlay

import android.graphics.Rect
import android.graphics.PixelFormat
import android.view.*
import android.widget.*
import com.loveadvisor.android.capture.ChatCaptureService
import com.loveadvisor.android.ui.ViewStyles
import com.loveadvisor.core.model.RectI
import com.loveadvisor.core.overlay.WindowRegistration

class DraftConfirmWindow(private val service:ChatCaptureService,draft:String,onConfirm:()->Unit,onCancel:()->Unit) {
    private val manager=service.getSystemService(WindowManager::class.java)
    private val registration=WindowRegistration()
    private val palette=PanelPalette(service)
    private val root=FrameLayout(service).apply { background=ViewStyles.shape(service,palette.surface,24f,palette.border);clipToOutline=true }
    private var closed=false
    init {
        val column=ViewStyles.column(service,18)
        root.addView(column)
        column.addView(palette.text("替换当前草稿？",17f,bold=true))
        column.addView(palette.text("输入框里已有未发送的内容",12f,palette.muted).apply { setPadding(0,dp(10),0,dp(8)) })
        val scroll=ScrollView(service)
        scroll.addView(palette.text(draft,13f).apply { setTextIsSelectable(true);setPadding(dp(10),dp(10),dp(10),dp(10));background=ViewStyles.shape(service,palette.card,12f) })
        column.addView(scroll,LinearLayout.LayoutParams(-1,if(draft.length>80)dp(120) else -2))
        column.addView(palette.text("确认后只填入，不会自动发送",11f,palette.muted).apply { setPadding(0,dp(10),0,dp(12)) })
        val actions=ViewStyles.row(service)
        actions.addView(palette.button("保留草稿") { close();onCancel() },LinearLayout.LayoutParams(0,dp(34),1f).apply { rightMargin=dp(8) })
        actions.addView(palette.button("替换并填入",true) { close();onConfirm() },LinearLayout.LayoutParams(0,dp(34),1f))
        column.addView(actions)
    }
    private fun dp(n:Int)=ViewStyles.dp(service,n.toFloat())
    fun show() {
        val bounds=manager.maximumWindowMetrics.bounds
        val params=WindowManager.LayoutParams(minOf(dp(320),bounds.width()-dp(24)),-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.CENTER;title="LoveAdvisorDraftConfirm" }
        registration.attach { manager.addView(root,params) }
    }
    fun close() { if(closed)return;closed=true;registration.detach { manager.removeViewImmediate(root) } }
}
