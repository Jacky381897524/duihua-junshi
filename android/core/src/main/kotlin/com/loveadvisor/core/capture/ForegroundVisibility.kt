package com.loveadvisor.core.capture

/** A visible/active old application alone is not proof that it still owns the foreground. */
object ForegroundVisibility {
    fun canRetain(windowId:Int?,apps:List<AppWindow>,transientFocused:Boolean,externalFocused:Boolean,ownEditorAttached:Boolean=false):Boolean {
        if(windowId==null || externalFocused)return false
        val focused=apps.filter { it.focused }.maxByOrNull { it.layer }
        if(focused!=null)return focused.id==windowId
        // This lease is created by a field tap in our attached editor, not by a
        // merely visible old chat. It is pinned to the exact previously proven app.
        if(ownEditorAttached)return apps.any { it.id==windowId }
        val underlying=apps.filter { it.active }.maxByOrNull { it.layer } ?: apps.maxByOrNull { it.layer }
        return transientFocused && underlying?.id==windowId
    }
}
