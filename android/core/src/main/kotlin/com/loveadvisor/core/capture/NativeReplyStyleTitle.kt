package com.loveadvisor.core.capture

import com.loveadvisor.core.advisor.NicknameText

/** Optional raw nickname for style lookup only; never changes the fill/session identity. */
object NativeReplyStyleTitle {
    fun read(window:WindowSnapshot?):String? {
        if(window==null || window.packageName!="com.tencent.mobileqq")return null
        val v=window.viewport
        if(!window.nodes.any { it.editable && it.bounds.top>v.top+v.height*.55 })return null
        return window.nodes.filter {
            it.id=="com.tencent.mobileqq:id/3kc" && !it.editable && NicknameText.valid(it.text.orEmpty()) &&
                it.bounds.top>=v.top && it.bounds.bottom<v.top+v.height*.15 && it.bounds.left>=v.left+v.width*.1
        }.singleOrNull()?.text
    }
}
