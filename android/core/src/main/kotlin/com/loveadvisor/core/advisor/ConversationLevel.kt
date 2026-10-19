package com.loveadvisor.core.advisor
data class ConversationLevel(val label:String,val value:Int?,val position:Float?) {
    companion object {
        fun from(value:Int?):ConversationLevel=when(value) {
            in 1..3 -> ConversationLevel("安全",value,(value!!-1)/8f)
            in 4..6 -> ConversationLevel("一般",value,(value!!-1)/8f)
            in 7..9 -> ConversationLevel("危险",value,(value!!-1)/8f)
            else -> ConversationLevel("待判断",null,null)
        }
    }
}
