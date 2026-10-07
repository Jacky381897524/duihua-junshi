package com.loveadvisor.core.advisor
import com.loveadvisor.core.api.ApiProfile
import com.loveadvisor.core.model.*
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class UnifiedPromptTest {
    @Test fun largeImportKeepsLatestMessagesWithinBoundedBudgetAndNeverSerializesCredentials() {
        val snapshot=AdvisorFixtures.snapshot().copy(messages=(0..100).map { ChatMessage("m$it",Speaker.OTHER,null,"$it-"+"字".repeat(20000)) })
        val input=AdvisorInput(ApiProfile(baseUrl="https://test",apiKey="PRIVATE_KEY",selectedModelId="m"),snapshot)
        val json=UnifiedPrompt.data(input); val messages=JsonParser.parseString(json).asJsonObject["messages"].asJsonArray
        assertTrue(messages.sumOf { it.asJsonObject["text"].asString.length }<=24000); assertTrue(messages.last().asJsonObject["text"].asString.startsWith("100-")); assertFalse(json.contains("PRIVATE_KEY"))
    }
}
