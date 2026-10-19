package com.loveadvisor.core.advisor

data class ReplyStyleEntry(val key:String,val title:String?,val style:String,val packageName:String="com.tencent.mm")
data class ReplyStyleCatalog(val styles:Map<String,String> = emptyMap(),val titles:Map<String,String> = emptyMap(),val apps:Map<String,String> = emptyMap(),val aliases:Map<String,String> = emptyMap()) {
    private fun entry(key:String)=styles[key]?.let { ReplyStyleEntry(key,titles[key],it,apps[key] ?: "com.tencent.mm") }
    fun entries()=styles.keys.mapNotNull(::entry).sortedWith(compareBy({ it.title==null },{ it.title.orEmpty() },{ it.key }))
    fun remove(key:String)=copy(styles=styles-key,titles=titles-key,apps=apps-key,aliases=aliases.filterValues { it!=key })
    fun match(title:String?,packageName:String):ReplyStyleEntry? {
        if(title.isNullOrBlank())return null
        listOfNotNull(ReplyStyles.rawKey(packageName,title),ReplyStyles.key(packageName,title)).distinct().forEach { key ->
            entry(key)?.takeIf { apps[key]==null || it.packageName==packageName }?.let { return it.copy(packageName=packageName) }
        }
        ReplyStyles.key(packageName,title)?.let { aliases[it] }?.let(::entry)?.takeIf { it.packageName==packageName }?.let { return it }
        return entries().filter { it.packageName==packageName && it.title?.let(NicknameText::canonical)==NicknameText.canonical(title) }.singleOrNull()
    }
    fun suggestions(title:String,packageName:String)=entries().filter { it.packageName==packageName && it.title!=null && NicknameText.suggestionKey(it.title)==NicknameText.suggestionKey(title) }
    fun bindAlias(observedTitle:String,key:String,packageName:String):ReplyStyleCatalog {
        require(NicknameText.valid(observedTitle)) { "请填写完整昵称，最多512个字符" }
        val target=entry(key)
        require(target!=null && target.packageName==packageName) { "只能关联当前平台已保存的专属风格" }
        val existing=match(observedTitle,packageName)
        require(existing==null || existing.key==key) { "这个昵称已关联其他专属风格，请先核对昵称" }
        val alias=ReplyStyles.key(packageName,observedTitle)!!
        return if(alias==key)this else copy(aliases=aliases+(alias to key))
    }
    fun save(title:String,style:String,replacingKey:String?=null,packageName:String="com.tencent.mm"):ReplyStyleCatalog {
        val name=title.trim();val key=ReplyStyles.key(packageName,name) ?: return this
        require(NicknameText.valid(name)) { "请填写完整昵称，最多512个字符" }
        val previous=replacingKey ?: match(name,packageName)?.key
        require(previous==null || previous==key || !styles.containsKey(key)) { "该昵称已有另一份专属风格，请选择对应对象编辑" }
        if(style.isBlank())return remove(previous ?: key)
        val oldAliases=if(previous!=null && (apps[previous] ?: "com.tencent.mm")==packageName)aliases.filterValues { it==previous } else emptyMap()
        val clean=if(previous!=null && previous!=key)remove(previous) else this
        return clean.copy(styles=ReplyStyles.save(clean.styles,packageName,name,style),titles=clean.titles+(key to name),apps=clean.apps+(key to packageName),aliases=clean.aliases+oldAliases.mapValues { key })
    }
    fun rememberTitle(title:String?,packageName:String="com.tencent.mm"):ReplyStyleCatalog {
        val found=match(title,packageName) ?: return this
        return if(found.title==null && title!=null)copy(titles=titles+(found.key to title.trim()),apps=apps+(found.key to packageName)) else this
    }
}
