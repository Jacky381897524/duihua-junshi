package com.loveadvisor.core.diagnostics
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
class DiagnosticRedactorTest {
    @Test fun automaticCaptureStagesRemainUsefulWithoutLeakingContent() {
        listOf("native-read","vision","recent").forEach { stage ->
            val event=DiagnosticRedactor.clean(DiagnosticEvent(stage,8,context="聊天正文 SECRET"))
            assertEquals(stage,event.stage)
            assertFalse(Gson().toJson(event).contains("SECRET"))
        }
    }
    @Test fun onlyWhitelistedFieldsCanReachExport() {
        val clean=DiagnosticRedactor.clean(DiagnosticEvent("catalog",100,401,"Bearer SECRET nested: 私密聊天","https://test/?key=SECRET","私密聊天"))
        val json=Gson().toJson(clean)
        assertTrue(json.contains("HTTP_401")); assertTrue(json.contains("catalog")); assertFalse(json.contains("SECRET")); assertFalse(json.contains("私密聊天"))
        assertEquals("unknown",DiagnosticRedactor.clean(DiagnosticEvent("Bearer SECRET",-10)).stage)
    }
}
