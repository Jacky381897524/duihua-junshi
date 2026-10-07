package com.loveadvisor.core.api
import com.google.gson.JsonParser
import com.loveadvisor.core.advisor.AdvisorFixtures
import org.junit.Assert.*
import org.junit.Test
class JevJudgeClientTest {
    @Test fun specialProtocolIsSequentialInputAndKeepsAllSevenQuestions() {
        LocalApiServer { ServerReply(200,"""{"answers":{"best_action":{"choice":"acknowledge"},"danger_level":{"score":3}}}""") }.use { s ->
            val result=JevJudgeClient(JdkHttpTransport()).judge(JevProfile(true,s.profile.baseUrl+"/systemone","FAKE_KEY","jev-id",true),AdvisorFixtures.snapshot(),"背景",Cancellation())
            assertTrue(result.contains("acknowledge")); val json=JsonParser.parseString(s.requests.single().body).asJsonObject
            assertEquals("jev-id",json["model"].asString); assertEquals(7,json["questions"].asJsonObject.size()); assertNotNull(json["state"].asJsonObject["chat"]); assertFalse(s.requests.single().path.contains("chat/completions"))
        }
    }
}
