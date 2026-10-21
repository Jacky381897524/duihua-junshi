package com.loveadvisor.core.advisor
import com.google.gson.JsonObject
import com.google.gson.JsonParser
class AdvisorCodec {
    fun parse(text:String):AdvisorBatch? {
        return try {
            var raw=text.trim()
            if(raw.startsWith("```")) raw=raw.replaceFirst(Regex("^```(?:json)?\\s*",RegexOption.IGNORE_CASE),"").removeSuffix("```").trim()
            val root=JsonParser.parseString(raw).asJsonObject
            fun string(o:JsonObject,key:String):String { val e=o.get(key); require(e!=null && e.isJsonPrimitive && e.asJsonPrimitive.isString); return e.asString.trim() }
            fun optional(o:JsonObject,key:String):String? = o.get(key)?.takeUnless { it.isJsonNull }?.let { string(o,key) }
            fun strings(o:JsonObject,key:String):List<String> = o.getAsJsonArray(key).map { require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString.trim() }
            val a=root.getAsJsonObject("analysis")
            val risk=a.get("risk")?.takeUnless { it.isJsonNull }?.let { require(it.isJsonPrimitive && it.asString.toIntOrNull()!=null); it.asString.toInt() }
            val analysis=AdvisorAnalysis(strings(a,"facts"),strings(a,"inferences"),strings(a,"unknowns"),string(a,"intent"),risk,string(a,"action"))
            val replies=root.getAsJsonArray("replies").map { element ->
                val o=element.asJsonObject; val b=o.getAsJsonObject("branches")
                val score=o.get("score")?.takeUnless { it.isJsonNull }?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber); it.asDouble }
                ReplyPlan(string(o,"id"),Strategy.valueOf(string(o,"strategy")),string(o,"text"),string(o,"reason"),string(o,"suitableWhen"),string(o,"cost"),ReplyBranches(string(b,"positive"),string(b,"ambiguous"),string(b,"rejection")),score,targetMessageId=optional(o,"targetMessageId"),topic=optional(o,"topic"))
            }
            AdvisorBatch(analysis.copy(strategies=replies.map { it.strategy }.distinct()),replies)
        } catch(_:Exception) { null }
    }
}
