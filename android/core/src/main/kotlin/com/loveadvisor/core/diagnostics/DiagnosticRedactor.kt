package com.loveadvisor.core.diagnostics
data class DiagnosticEvent(val stage:String,val elapsedMs:Long,val status:Int?=null,val error:String?=null,val url:String?=null,val context:String?=null)
data class SafeDiagnosticEvent(val stage:String,val elapsedMs:Long,val code:String)
object DiagnosticRedactor {
    fun clean(event:DiagnosticEvent):SafeDiagnosticEvent {
        val stage=event.stage.takeIf { it in setOf("capture","native-read","vision","recent","ocr","catalog","advisor","fill","memory","practice","config","projection","glass-local") } ?: "unknown"
        val code=event.status?.takeIf { it in 100..599 }?.let { "HTTP_$it" } ?: if(event.error==null) "OK" else "NETWORK_OR_PROTOCOL_ERROR"
        return SafeDiagnosticEvent(stage,event.elapsedMs.coerceIn(0,3600000),code)
    }
}
