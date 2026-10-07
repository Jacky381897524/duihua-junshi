package com.loveadvisor.core.knowledge
class KnowledgeRouter {
    fun unified(query:KnowledgeQuery,documents:List<KnowledgeDocument>):List<KnowledgeExcerpt> {
        val fixed=documents.filter { it.id in setOf("jev-copilot.md","romance-master.md") }.map { KnowledgeExcerpt(it.id,it.title,it.path,it.source,excerpt(it.text,setOf("expression","invite")).take(1000)) }
        val relevant=select(query,documents.filterNot { it.id in setOf("jev-copilot.md","romance-master.md") }).take(1).map { it.copy(text=it.text.take(1200)) }
        return fixed+relevant
    }
    private val terms=mapOf(
        "boundary" to listOf("不想","不要联系","拒绝","边界","停止","不合适","收线"),
        "conflict" to listOf("生气","吵架","冲突","误会","对不起","冷战","修复"),
        "comfort" to listOf("累","烦","难过","失落","压力","委屈","安慰"),
        "invite" to listOf("约","见面","吃饭","周末","咖啡","电影","邀请"),
        "work" to listOf("办事","同事","工作","资料","领导"),
        "expression" to listOf("聊天","接话","夸","调侃","润色","幽默","尴尬"),
        "personality" to listOf("mbti","依恋","人格","性格"),
        "family" to listOf("婚姻","家庭","育儿","家务","结婚","金钱","父母"),
        "evidence" to listOf("证据","论文","书单","科学"),
        "digital" to listOf("在线","数字关系","网恋","已读","回复慢"),
        "theory" to listOf("心理学","哲学","社交体系","互惠"),
        "safety" to listOf("法律","危机","安全","求助"),
        "memory" to listOf("长期记忆","关系档案","背景")
    )
    fun select(query:KnowledgeQuery,documents:List<KnowledgeDocument>):List<KnowledgeExcerpt> {
        val input=(query.text+" "+query.goal).lowercase()
        val matched=terms.mapValues { (_,words)->words.count { input.contains(it) } }.filterValues { it>0 }.toMutableMap()
        if(matched.isEmpty()) matched["expression"]=1
        val scored=(query.notes+documents).distinctBy { it.id }.map { doc ->
            var score=doc.tags.sumOf { tag -> (matched[tag]?:0) * if(tag=="boundary") 120 else 50 }
            for((_,words) in terms) for(word in words) if(input.contains(word) && doc.title.contains(word)) score+=10
            val tokenWords=Regex("[a-zA-Z]{3,}|[\\u4e00-\\u9fff]{2,6}").findAll(query.goal).map { it.value }.toList()
            score+=tokenWords.count { doc.text.contains(it,true) }*4
            doc to score
        }.filter { it.second>0 }.sortedWith(compareByDescending<Pair<KnowledgeDocument,Int>> { it.second }.thenBy { it.first.id })
        return scored.take(3).map { (doc,_)->KnowledgeExcerpt(doc.id,doc.title,doc.path,doc.source,excerpt(doc.text,matched.keys)) }
    }
    private fun excerpt(text:String,topics:Set<String>):String {
        val sections=text.split(Regex("(?m)(?=^## )"))
        val selected=sections.sortedByDescending { part -> topics.sumOf { topic -> terms[topic].orEmpty().count { part.contains(it) } } }.firstOrNull().orEmpty()
        return selected.take(2000)
    }
}
