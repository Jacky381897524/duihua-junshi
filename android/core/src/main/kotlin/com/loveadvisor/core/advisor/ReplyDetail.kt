package com.loveadvisor.core.advisor
import com.google.gson.JsonParser
data class ReplyDetail(val why:String,val whenToUse:String,val caution:String,val positive:String,val ambiguous:String,val rejection:String)
object ReplyDetailCodec {
    fun decode(raw:String):ReplyDetail {
        val o=JsonParser.parseString(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()).asJsonObject
        fun field(key:String,max:Int):String { val s=o[key].asString.trim();require(s.isNotBlank() && s.codePointCount(0,s.length)<=max) { "详情格式不完整或内容过长，请重试" };return s }
        return ReplyDetail(field("why",60),field("whenToUse",60),field("caution",60),field("positive",35),field("ambiguous",35),field("rejection",35))
    }
}
