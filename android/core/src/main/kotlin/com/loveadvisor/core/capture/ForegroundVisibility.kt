package com.loveadvisor.core.capture

/** A visible/active old application alone is not proof that it still owns the foreground. */
object ForegroundVisibility {
    fun canRetain(windowId:Int?,apps:List<AppWindow>,transientFocused:Boolean,externalFocused:Boolean):Boolean {
        if(windowId==null || externalFocused)return false
        val focused=apps.filter { it.focused }.maxByOrNull { it.layer }
        if(focused!=null)return focused.id==windowId
        val underlying=apps.filter { it.active }.maxByOrNull { it.layer } ?: apps.maxByOrNull { it.layer }
        return transientFocused && underlying?.id==windowId
    }
}
