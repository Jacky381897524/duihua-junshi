package com.loveadvisor.core.capture

/** Package ownership metadata only, never a title, contact or chat node. */
class WindowPackageCache(private val capacity:Int=64) {
    private val packages=linkedMapOf<Int,String>()
    @Synchronized fun remember(windowId:Int,packageName:String) {
        if(windowId<0 || packageName.length !in 1..200 || !packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")))return
        packages.remove(windowId);packages[windowId]=packageName
        while(packages.size>capacity.coerceIn(1,128))packages.remove(packages.keys.first())
    }
    @Synchronized fun forget(windowId:Int) { packages.remove(windowId) }
    @Synchronized fun packageFor(windowId:Int):String?=packages[windowId]
    @Synchronized fun signal(windows:List<AppWindow>,eventWindowId:Int,eventPackage:String?,validatedStateEvent:Boolean):ForegroundSignal? {
        val focused=windows.filter { it.focused }.maxByOrNull { it.layer } ?: return null
        if(validatedStateEvent && eventWindowId==focused.id && !eventPackage.isNullOrBlank())remember(focused.id,eventPackage)
        return packages[focused.id]?.let { ForegroundSignal(it,focused.id) }
    }
}
