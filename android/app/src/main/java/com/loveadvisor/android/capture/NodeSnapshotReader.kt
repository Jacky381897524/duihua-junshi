package com.loveadvisor.android.capture
import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.loveadvisor.core.model.*
import com.loveadvisor.core.capture.WindowSnapshot
import com.loveadvisor.core.capture.AppWindow
import com.loveadvisor.core.capture.ForegroundWindowSelector
import com.loveadvisor.core.capture.CapturePolicy
import android.os.SystemClock
class NodeSnapshotReader(private val service:AccessibilityService) {
    private val windowClasses=linkedMapOf<Int,String>()
    @Synchronized fun recordWindowClass(id:Int,name:String) { if(id>=0) { windowClasses[id]=name; while(windowClasses.size>32) windowClasses.remove(windowClasses.keys.first()) } }
    @Synchronized fun forgetWindow(id:Int) { windowClasses.remove(id) }
    @Synchronized private fun windowClass(id:Int)=windowClasses[id]
    data class WindowRead(val window:WindowSnapshot,val editables:List<Pair<NodeRecord,AccessibilityNodeInfo>>)
    private fun applicationWindow():android.view.accessibility.AccessibilityWindowInfo? {
        val windows=service.windows
        val apps=windows.filter { it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
        val overlayActive=windows.any { (it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY || it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) && it.isActive }
        val selected=ForegroundWindowSelector.select(apps.map { AppWindow(it.id,it.isFocused,it.isActive,it.layer) },overlayActive) ?: return null
        return apps.firstOrNull { it.id==selected.id }
    }
    /** Passive foreground detection reads package/window metadata only, never message nodes. */
    fun foreground():WindowSnapshot? {
        val application=applicationWindow() ?: return null
        val pkg=application.root?.packageName?.toString() ?: return null
        val frame=Rect(); application.getBoundsInScreen(frame)
        return WindowSnapshot(pkg,application.id,RectI(frame.left,frame.top,frame.right,frame.bottom),emptyList(),windowClassName=windowClass(application.id))
    }
    fun read(identity:ConversationIdentity?=null,expectedPackage:String?=null):WindowSnapshot?=readWithTargets(identity,expectedPackage)?.window
    fun readWithTargets(identity:ConversationIdentity?=null,expectedPackage:String?=null):WindowRead? {
        val application=applicationWindow() ?: return null
        val frame=Rect(); application.getBoundsInScreen(frame)
        val viewport=RectI(frame.left,frame.top,frame.right,frame.bottom)
        val root=application.root ?: return null
        if(expectedPackage!=null && root.packageName?.toString()!=expectedPackage) {
            val r=Rect(); root.getBoundsInScreen(r)
            return WindowRead(WindowSnapshot(root.packageName?.toString().orEmpty(),application.id,CapturePolicy.viewport(viewport,RectI(r.left,r.top,r.right,r.bottom)),emptyList()),emptyList())
        }
        val read=snapshotRoot(root,identity,viewport,windowClass(application.id))
        return read.copy(window=read.window.copy(windowId=application.id))
    }
    fun editable():AccessibilityNodeInfo? {
        val read=readWithTargets() ?: return null
        val v=read.window.viewport
        return read.editables.filter { it.first.bounds.top>=v.top+v.height*.55 && !it.first.text.orEmpty().contains("搜索") }.maxByOrNull { it.first.bounds.top }?.second
    }
    companion object {
    fun snapshotRoot(root:AccessibilityNodeInfo,identity:ConversationIdentity?=null,windowBounds:RectI?=null,windowClassName:String?=null):WindowRead {
        val bounds=Rect(); root.getBoundsInScreen(bounds)
        val viewport=CapturePolicy.viewport(windowBounds ?: RectI(0,0,0,0),RectI(bounds.left,bounds.top,bounds.right,bounds.bottom))
        val records=mutableListOf<NodeRecord>()
        val editables=mutableListOf<Pair<NodeRecord,AccessibilityNodeInfo>>()
        val deadline=SystemClock.elapsedRealtime()+1200
        fun walk(node:AccessibilityNodeInfo,parentKey:String,key:String,parentBounds:RectI?,depth:Int) {
            if(records.size>=800 || depth>35 || SystemClock.elapsedRealtime()>deadline || (depth>0 && !node.isVisibleToUser)) return
            val r=Rect(); node.getBoundsInScreen(r); val rectangle=RectI(r.left,r.top,r.right,r.bottom)
            if(depth>0 && rectangle.width>0 && rectangle.height>0 && (rectangle.right<=viewport.left || rectangle.left>=viewport.right || rectangle.bottom<=viewport.top || rectangle.top>=viewport.bottom)) return
            val container=parentBounds?.takeIf { it.width>=rectangle.width && it.width<viewport.width*.85 && it.height<viewport.height*.35 }
            val id=node.viewIdResourceName
            val role=when { node.contentDescription?.toString()?.contains("头像")==true -> "avatar"; id?.endsWith("/mjq")==true -> "sender"; node.contentDescription?.toString()?.contains("群公告")==true -> "announcement"; else -> null }
            val editable=isTextTarget(node)
            val record=NodeRecord(id,node.text?.toString(),node.className?.toString().orEmpty(),editable,rectangle,container,parentKey,role,node.contentDescription?.toString(),key)
            records.add(record); if(editable) editables.add(record to node)
            for(i in 0 until node.childCount.coerceAtMost(100)) {
                if(records.size>=800 || SystemClock.elapsedRealtime()>deadline) break
                node.getChild(i)?.let { walk(it,key,"$key/$i",rectangle,depth+1) }
            }
        }
        walk(root,"","root",null,0)
        // Some OEM trees omit children yet still expose the focused app input via findFocus.
        runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()?.let { focused ->
            if(focused.windowId==root.windowId && focused.packageName==root.packageName && isTextTarget(focused)) {
                val record=record(focused).copy(editable=true)
                if(editables.none { it.first.id==record.id && it.first.bounds==record.bounds }) { records.add(record);editables.add(record to focused) }
            }
        }
        return WindowRead(WindowSnapshot(root.packageName?.toString().orEmpty(),root.windowId,viewport,records,identity,windowClassName),editables)
    }
        private fun isTextTarget(node:AccessibilityNodeInfo)=node.isEditable || node.className?.toString()?.endsWith("EditText")==true && node.actionList.any { it.id==AccessibilityNodeInfo.ACTION_SET_TEXT || it.id==AccessibilityNodeInfo.ACTION_PASTE }
        fun record(node:AccessibilityNodeInfo):NodeRecord { val r=Rect(); node.getBoundsInScreen(r); return NodeRecord(node.viewIdResourceName,node.text?.toString(),node.className?.toString().orEmpty(),isTextTarget(node),RectI(r.left,r.top,r.right,r.bottom)) }
    }
}
