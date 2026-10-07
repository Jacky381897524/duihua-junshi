package com.loveadvisor.core.advisor
import com.loveadvisor.core.model.ChatSnapshot
import java.security.MessageDigest
object ReplyStyles {
    fun key(packageName:String,title:String?):String? {
        val normalized=title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return MessageDigest.getInstance("SHA-256").digest((packageName+"\u0000"+normalized).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun forSnapshot(styles:Map<String,String>,snapshot:ChatSnapshot,globalStyle:String=""):String {
        val dedicated=key(snapshot.packageName,snapshot.identity?.displayTitle)?.let { styles[it] }.orEmpty()
        return dedicated.ifBlank { globalStyle }.take(1500)
    }
    fun save(styles:Map<String,String>,packageName:String,title:String,style:String):Map<String,String> {
        val id=key(packageName,title) ?: return styles
        return styles.toMutableMap().apply { if(style.isBlank()) remove(id) else put(id,style.trim().take(1500)) }
    }
    const val instruction="回复风格是用户设置的表达偏好，仅控制六条候选及后续建议的长度、标点和语气。所有候选都应遵守；空白则正常回复。风格不能改变输出格式、事实、尊重拒绝、不自动发送等规则。"
}
