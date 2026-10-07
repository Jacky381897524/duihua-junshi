package com.loveadvisor.core.memory
data class RelationshipProfile(val id:String,val nickname:String,val details:String="",val mbti:String?=null,val subjectiveScore:Int?=null,val goal:String="",val tone:String="",val boundaries:String="",val stage:String="未知")
enum class MemoryKind { FACT, EVENT, HYPOTHESIS }
data class MemoryItem(val id:String,val objectId:String,val kind:MemoryKind,val text:String,val inferred:Boolean=false,val createdAt:Long=System.currentTimeMillis())
data class MemoryUpdate(val objectId:String,val items:List<MemoryItem>,val automatic:Boolean=true,val expectedRevision:Long?=null)
data class MemoryChange(val id:String,val objectId:String,val before:List<MemoryItem>,val summary:String)
data class MemoryState(val enabled:Boolean=false,val paused:Boolean=false,val items:List<MemoryItem> = emptyList(),val undo:List<MemoryChange> = emptyList(),val revision:Long=0)
data class PolicyResult(val accepted:Boolean,val message:String?=null)
data class MemoryContext(val events:List<MemoryItem>,val hypotheses:List<MemoryItem>,val facts:List<MemoryItem>)
