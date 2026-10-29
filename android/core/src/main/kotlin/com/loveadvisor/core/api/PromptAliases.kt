package com.loveadvisor.core.api

import com.google.gson.*

/** Lossless, per-request aliases for opaque internal IDs. Never rewrites chat prose. */
class PromptAliases {
    private val forward=linkedMapOf<String,String>()
    private val reverse=mutableMapOf<String,String>()
    private val reserved=mutableSetOf<String>()
    private val keys=setOf("id","messageId","targetMessageId","lastMessageId","sourceId","peerId","ownerId","owner","contactId","supportingSourceIds","sourceIds")
    private val opaque=Regex("[A-Za-z0-9_-]{24,}")
    fun messages(messages:List<PromptMessage>):List<PromptMessage> {
        val roots=messages.map { parse(it.content) }
        roots.filterNotNull().forEach { root -> visit(root) { reserved.add(it) } }
        roots.filterNotNull().forEach { collect(it) }
        return messages.mapIndexed { index,message -> roots[index]?.let { root -> message.copy(content=transform(root,false).toString()) } ?: message }
    }
    fun encode(root:JsonElement):JsonElement { collect(root);return transform(root,false) }
    fun restore(content:String):String {
        if(reverse.isEmpty())return content
        val root=parse(content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()) ?: return content
        return transform(root,true).toString()
    }
    private fun parse(raw:String):JsonElement?=if(raw.trimStart().firstOrNull() in listOf('{','['))runCatching { JsonParser.parseString(raw) }.getOrNull() else null
    private fun visit(root:JsonElement,key:String="",action:(String)->Unit) {
        when {
            root.isJsonObject -> root.asJsonObject.entrySet().forEach { (k,v)->visit(v,k,action) }
            root.isJsonArray -> root.asJsonArray.forEach { visit(it,key,action) }
            root.isJsonPrimitive && root.asJsonPrimitive.isString && key in keys -> action(root.asString)
        }
    }
    private fun collect(root:JsonElement) {
        val ids=mutableListOf<String>();visit(root) { reserved.add(it);if(opaque.matches(it))ids.add(it) }
        ids.distinct().forEach { id -> if(id !in forward) {
            var next=forward.size+1;var alias="i$next"
            while(alias in reserved || alias in reverse) { next++;alias="i$next" }
            forward[id]=alias;reverse[alias]=id
        } }
    }
    private fun transform(root:JsonElement,decode:Boolean,key:String=""):JsonElement=when {
        root.isJsonObject -> JsonObject().apply { root.asJsonObject.entrySet().forEach { (k,v)->add(k,transform(v,decode,k)) } }
        root.isJsonArray -> JsonArray().apply { root.asJsonArray.forEach { add(transform(it,decode,key)) } }
        root.isJsonPrimitive && root.asJsonPrimitive.isString && key in keys -> JsonPrimitive((if(decode)reverse else forward)[root.asString] ?: root.asString)
        else -> root.deepCopy()
    }
}
