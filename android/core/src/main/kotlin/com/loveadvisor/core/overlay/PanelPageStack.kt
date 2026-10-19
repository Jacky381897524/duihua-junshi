package com.loveadvisor.core.overlay

class PanelPageStack {
    private data class Page(val id:String,val editable:Boolean)
    private val pages=mutableListOf<Page>()
    val currentId get()=pages.lastOrNull()?.id
    val editable get()=pages.lastOrNull()?.editable ?: false
    val depth get()=pages.size
    fun push(id:String,editable:Boolean):Boolean {
        require(id.isNotBlank())
        if(currentId==id)return false
        pages.add(Page(id,editable));return true
    }
    fun pop():Boolean { if(pages.isEmpty())return false;pages.removeAt(pages.lastIndex);return true }
    fun clear()=pages.clear()
}
