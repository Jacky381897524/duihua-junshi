package com.loveadvisor.core.advisor
data class ConversationLevel(val label:String,val value:Int?,val position:Float?) {
    fun tint(green:Int,amber:Int,red:Int,unknown:Int):Int {
        val p=position ?: return unknown
        val a=if(p<.5f)green else amber;val b=if(p<.5f)amber else red;val t=if(p<.5f)p*2 else (p-.5f)*2
        var out=0
        for(shift in listOf(0,8,16,24)) { val left=(a ushr shift) and 255;val right=(b ushr shift) and 255;out=out or ((left+(right-left)*t).toInt() shl shift) }
        return out
    }
    companion object {
        fun from(value:Int?):ConversationLevel=when(value) {
            in 1..3 -> ConversationLevel("安全",value,(value!!-1)/8f)
            in 4..6 -> ConversationLevel("一般",value,(value!!-1)/8f)
            in 7..9 -> ConversationLevel("危险",value,(value!!-1)/8f)
            else -> ConversationLevel("待判断",null,null)
        }
    }
}
