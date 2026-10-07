package com.loveadvisor.core.advisor

data class ReplyStyleEntry(val key:String,val title:String?,val style:String,val packageName:String="com.tencent.mm")
data class ReplyStyleCatalog(val styles:Map<String,String> = emptyMap(),val titles:Map<String,String> = emptyMap(),val apps:Map<String,String> = emptyMap()) {
    fun entries()=styles.entries.map { ReplyStyleEntry(it.key,titles[it.key],it.value,apps[it.key] ?: "com.tencent.mm") }.sortedWith(compareBy({ it.title==null },{ it.title.orEmpty() },{ it.key }))
    fun remove(key:String)=copy(styles=styles-key,titles=titles-key,apps=apps-key)
    fun save(title:String,style:String,replacingKey:String?=null,packageName:String="com.tencent.mm"):ReplyStyleCatalog {
        val normalized=title.trim(); val key=ReplyStyles.key(packageName,normalized) ?: return this
        val clean=if(replacingKey!=null && replacingKey!=key)remove(replacingKey) else this
        if(style.isBlank())return clean.remove(key)
        return clean.copy(styles=ReplyStyles.save(clean.styles,packageName,normalized,style),titles=clean.titles+(key to normalized),apps=clean.apps+(key to packageName))
    }
    fun rememberTitle(title:String?,packageName:String="com.tencent.mm"):ReplyStyleCatalog {
        val key=ReplyStyles.key(packageName,title) ?: return this
        return if(styles.containsKey(key) && (titles[key]!=title?.trim() || apps[key]!=packageName))copy(titles=titles+(key to title!!.trim()),apps=apps+(key to packageName)) else this
    }
}
