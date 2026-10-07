package com.loveadvisor.core.capture

/** Only the requested native chat apps participate in foreground capture. */
object ChatApps {
    const val WECHAT="com.tencent.mm"
    const val QQ="com.tencent.mobileqq"
    const val DOUYIN="com.ss.android.ugc.aweme"
    val packages=setOf(WECHAT,QQ,DOUYIN)
    fun supported(packageName:String?)=packageName in packages
    fun label(packageName:String?)=when(packageName) { WECHAT -> "微信";QQ -> "QQ";DOUYIN -> "抖音";else -> "聊天" }
}
