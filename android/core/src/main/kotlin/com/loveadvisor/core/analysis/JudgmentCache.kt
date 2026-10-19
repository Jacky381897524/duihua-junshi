package com.loveadvisor.core.analysis
/** Page cancellation does not discard a verified session's bounded result cache. */
class JudgmentCache<T>(private val limit:Int=200) {
    init { require(limit>0) }
    private val entries=LinkedHashMap<JudgmentKey,T>()
    val size get()=entries.size
    fun get(key:JudgmentKey):T?=entries[key]
    fun put(key:JudgmentKey,value:T) { entries.remove(key);entries[key]=value;while(entries.size>limit)entries.remove(entries.keys.first()) }
    fun clear()=entries.clear()
}
