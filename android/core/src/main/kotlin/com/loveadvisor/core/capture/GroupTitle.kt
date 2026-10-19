package com.loveadvisor.core.capture

object GroupTitle {
    fun matchingName(raw:String,confirmedMemberCount:Int?):String {
        val title=raw.trim()
        if(confirmedMemberCount==null)return title
        val suffix=Regex("[（(](\\d+)[）)]$").find(title) ?: return title
        return if(suffix.groupValues[1].toIntOrNull()==confirmedMemberCount)title.substring(0,suffix.range.first).trim().ifBlank { title } else title
    }
}
