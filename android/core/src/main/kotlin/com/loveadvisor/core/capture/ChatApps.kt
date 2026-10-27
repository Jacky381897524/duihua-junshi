package com.loveadvisor.core.capture

/** Registry for the shared native/vision pipeline; inclusion does not imply device certification. */
object ChatApps {
    const val WECHAT="com.tencent.mm"
    const val QQ="com.tencent.mobileqq"
    const val DOUYIN="com.ss.android.ugc.aweme"
    val labels=linkedMapOf(WECHAT to "微信",QQ to "QQ",DOUYIN to "抖音",
        "cn.soulapp.android" to "Soul","com.xingin.xhs" to "小红书","com.twitter.android" to "X",
        "com.p1.mobile.putong" to "探探","com.immomo.momo" to "陌陌","com.wepie.ivy" to "青藤之恋",
        "com.intelcupid.shesay" to "她说","com.qingqi.dianbo" to "微光","com.wepie.ivylove.overseas" to "青藤之恋海外版","cn.xiaochuankeji.tieba" to "最右",
        "com.netease.cloudmusic" to "网易云音乐","com.whatsapp" to "WhatsApp","com.whatsapp.w4b" to "WhatsApp Business",
        "com.instagram.android" to "Instagram","com.discord" to "Discord","org.telegram.messenger" to "Telegram",
        "org.thoughtcrime.securesms" to "Signal","jp.naver.line.android" to "LINE","com.kakao.talk" to "KakaoTalk",
        "com.facebook.orca" to "Messenger","com.snapchat.android" to "Snapchat","com.zhiliaoapp.musically" to "TikTok",
        "com.tinder" to "Tinder","com.bumble.app" to "Bumble","co.hinge.app" to "Hinge",
        "xyz.blueskyweb.app" to "Bluesky","com.instagram.barcelona" to "Threads","com.reddit.frontpage" to "Reddit",
        "tv.danmaku.bili" to "哔哩哔哩","com.zhihu.android" to "知乎","com.sina.weibo" to "微博",
        "com.smile.gifmaker" to "快手","com.alibaba.android.rimet" to "钉钉","com.ss.android.lark" to "飞书",
        "com.tencent.wework" to "企业微信","com.microsoft.teams" to "Teams","com.Slack" to "Slack")
    @Volatile private var extra:Map<String,String> = emptyMap()
    val packages get()=labels.keys+extra.keys
    fun configureAdditional(apps:Map<String,String>) { extra=apps.filterKeys { Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+").matches(it) && !it.startsWith("com.android.") && !it.startsWith("android.") }.mapValues { it.value.take(60) } }
    fun supported(packageName:String?)=packageName in packages
    fun label(packageName:String?)=labels[packageName] ?: extra[packageName] ?: "聊天"
}
