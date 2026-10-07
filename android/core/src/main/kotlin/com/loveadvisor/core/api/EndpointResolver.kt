package com.loveadvisor.core.api
import java.net.URI

class EndpointResolver {
    fun resolve(profile: ApiProfile): EndpointSet {
        val uri = try { URI((profile.resolvedBaseUrl ?: profile.baseUrl).trim()) } catch(_:Exception) { throw IllegalArgumentException("服务地址格式错误") }
        require(uri.scheme in listOf("https","http") && !uri.host.isNullOrBlank()) { "请填写完整的 HTTPS 服务地址" }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) { "服务地址不能包含凭证、查询参数或片段" }
        require(uri.scheme != "http" || profile.allowLanHttp && isLocalHost(uri.host)) { "HTTP 仅供明确启用的局域网服务" }
        var path = uri.rawPath.orEmpty().trimEnd('/')
        if (path.endsWith("/chat/completions")) path = path.removeSuffix("/chat/completions")
        val authority = URI(uri.scheme, null, uri.host, uri.port, null, null, null).toString().trimEnd('/')
        val base = authority + path
        val models = if(path.isBlank()) listOf("$base/models","$base/v1/models") else listOf("$base/models")
        return EndpointSet(models,"$base/chat/completions")
    }
    companion object {
        fun isLocalHost(host: String): Boolean {
            val h=host.lowercase().removePrefix("[").removeSuffix("]")
            if(h=="localhost" || h=="::1" || h.endsWith(".local")) return true
            val p=h.split('.').map { it.toIntOrNull() ?: return false }
            if(p.size!=4 || p.any { it !in 0..255 }) return false
            return p[0]==127 || p[0]==10 || p[0]==192 && p[1]==168 || p[0]==172 && p[1] in 16..31
        }
    }
}
