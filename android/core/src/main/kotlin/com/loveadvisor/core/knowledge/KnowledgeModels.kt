package com.loveadvisor.core.knowledge
data class KnowledgeDocument(val id:String,val title:String,val path:String,val tags:List<String>,val source:String,val text:String)
data class KnowledgeQuery(val text:String,val goal:String="",val notes:List<KnowledgeDocument> = emptyList())
data class KnowledgeExcerpt(val id:String,val title:String,val path:String,val source:String,val text:String)
