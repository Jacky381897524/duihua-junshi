package com.loveadvisor.android.storage
import com.google.gson.Gson
import com.loveadvisor.core.api.*
import org.junit.Assert.*
import org.junit.Test
class AutomaticSettingsTest {
    @Test fun confirmedNicknameAliasesAndOriginalUnicodeSurviveReopening() {
        val saved=AppSettings.decode("""{"replyStyles":{"target":"浪漫"},"replyStyleTitles":{"target":"🍃    L 。👩🏽‍💻"},"replyStyleApps":{"target":"com.tencent.mm"},"replyStyleAliases":{"alias":"target"},"globalReplyStyle":"简短"}""")
        val reopened=Gson().toJsonTree(AppSettings.decode(Gson().toJson(saved))).asJsonObject
        assertEquals("target",reopened["replyStyleAliases"]?.asJsonObject?.get("alias")?.asString)
        assertEquals("🍃    L 。👩🏽‍💻",reopened["replyStyleTitles"]?.asJsonObject?.get("target")?.asString)
        assertEquals("简短",reopened["globalReplyStyle"]?.asString)
        assertEquals(0,Gson().toJsonTree(AppSettings.decode("{}")).asJsonObject["replyStyleAliases"]?.asJsonObject?.size())
    }
    @Test fun lightFrostDefaultsOnAndUserCanRestorePlainPanelPermanently() {
        val defaults=Gson().toJsonTree(AppSettings.decode("{}")).asJsonObject
        assertEquals(true,defaults["frostedPanelEnabled"]?.asBoolean)
        val saved=AppSettings.decode("""{"frostedPanelEnabled":false,"globalReplyStyle":"简短","capsuleLocked":true,"profile":{"apiKey":"fake"}}""")
        val reopened=Gson().toJsonTree(AppSettings.decode(Gson().toJson(saved))).asJsonObject
        assertEquals(false,reopened["frostedPanelEnabled"]?.asBoolean)
        assertEquals("简短",saved.globalReplyStyle);assertTrue(saved.capsuleLocked);assertEquals("fake",saved.profile.apiKey)
    }
    @Test fun capsuleLockAndScalePersistWhileOlderUsersStartUnlocked() {
        val defaults=Gson().toJsonTree(AppSettings.decode("{}")).asJsonObject
        assertEquals(false,defaults["capsuleLocked"]?.asBoolean)
        assertEquals(1.0,defaults["capsuleScale"]?.asDouble ?: -1.0,0.001)
        val saved=AppSettings.decode("""{"capsuleLocked":true,"capsuleScale":1.73,"profile":{"apiKey":"fake"}}""")
        val reopened=Gson().toJsonTree(AppSettings.decode(Gson().toJson(saved))).asJsonObject
        assertEquals(true,reopened["capsuleLocked"]?.asBoolean);assertEquals(1.73,reopened["capsuleScale"]?.asDouble ?: -1.0,0.001)
        assertEquals("fake",saved.profile.apiKey)
        assertEquals(.7,Gson().toJsonTree(AppSettings.decode("""{"capsuleScale":0.1}""")).asJsonObject["capsuleScale"]?.asDouble ?: -1.0,.001)
        assertEquals(2.0,Gson().toJsonTree(AppSettings.decode("""{"capsuleScale":9}""")).asJsonObject["capsuleScale"]?.asDouble ?: -1.0,.001)
    }
    @Test fun topCapsuleDefaultsOffAndRemembersItsAdjustedPosition() {
        val defaults=Gson().toJsonTree(AppSettings.decode("{}")).asJsonObject
        assertEquals(false,defaults["topCapsuleEnabled"]?.asBoolean)
        val saved=AppSettings.decode("""{"topCapsuleEnabled":true,"capsuleOffsetXdp":9.5,"capsuleOffsetYdp":-2,"globalReplyStyle":"简短"}""")
        val reopened=Gson().toJsonTree(AppSettings.decode(Gson().toJson(saved))).asJsonObject
        assertEquals(true,reopened["topCapsuleEnabled"]?.asBoolean)
        assertEquals(9.5,reopened["capsuleOffsetXdp"]!!.asDouble,0.001)
        assertEquals(-2.0,reopened["capsuleOffsetYdp"]!!.asDouble,0.001)
        assertEquals("简短",saved.globalReplyStyle)
    }
    @Test fun nativeIslandIsOptInAndMigrationPreservesOtherSettings() {
        val defaults=Gson().toJsonTree(AppSettings.decode("{}")).asJsonObject
        assertEquals(false,defaults["nativeIslandEnabled"]?.asBoolean)
        val saved=AppSettings.decode("""{"nativeIslandEnabled":true,"nativeIslandConfirmedBuild":"test-build","bubbleAutoHideSeconds":60,"globalReplyStyle":"简短","profile":{"apiKey":"fake"}}""")
        val json=Gson().toJsonTree(AppSettings.decode(Gson().toJson(saved))).asJsonObject
        assertEquals(false,json["nativeIslandEnabled"]?.asBoolean)
        assertEquals(true,json["topCapsuleEnabled"]?.asBoolean)
        assertEquals("test-build",json["nativeIslandConfirmedBuild"]?.asString)
        assertEquals(60,saved.bubbleAutoHideSeconds);assertEquals("简短",saved.globalReplyStyle);assertEquals("fake",saved.profile.apiKey)
    }
    @Test fun oldSettingsHaveFifteenSecondIdleTimeoutAndCustomTimeoutSurvivesReopening() {
        val defaults=Gson().toJsonTree(AppSettings.decode("{}")).asJsonObject
        assertEquals(15,defaults["bubbleAutoHideSeconds"]?.asInt)
        val reopened=Gson().toJsonTree(AppSettings.decode(Gson().toJson(AppSettings.decode("""{"bubbleAutoHideSeconds":45}""")))).asJsonObject
        assertEquals(45,reopened["bubbleAutoHideSeconds"]?.asInt)
        for (invalid in listOf(-1,0,Int.MAX_VALUE)) {
            val migrated=Gson().toJsonTree(AppSettings.decode("""{"bubbleAutoHideSeconds":$invalid}""" )).asJsonObject
            assertEquals(15,migrated["bubbleAutoHideSeconds"]?.asInt)
        }
    }
    @Test fun globalReplyStylePersistsAndOldSettingsDefaultToEmpty() {
        val saved=SettingsData(globalReplyStyle="没有标点符号",replyStyles=mapOf("object-key" to "浪漫"))
        val reopened=AppSettings.decode(Gson().toJson(saved))
        assertEquals("没有标点符号",reopened.globalReplyStyle)
        assertEquals("浪漫",reopened.replyStyles["object-key"])
        assertEquals("",AppSettings.decode("{}").globalReplyStyle)
    }
    @Test fun customJudgeAndStyleObjectNamesSurviveOpeningAgain() {
        val raw="""{"judgeProvider":"CUSTOM","judgeCustom":{"baseUrl":"https://example.com/v1","apiKey":"fake-custom","selectedModelId":"my-judge"},"replyStyles":{"abc":"简短"},"replyStyleTitles":{"abc":"小雨"}}"""
        val s=AppSettings.decode(Gson().toJson(AppSettings.decode(raw)))
        assertEquals("CUSTOM",s.judgeProvider);assertTrue(s.judgeProfile().custom)
        assertEquals("my-judge",s.judgeProfile().model);assertEquals("fake-custom",s.judgeProfile().apiKey)
        assertEquals("小雨",s.replyStyleTitles["abc"]);assertEquals("简短",s.replyStyles["abc"])
    }
    @Test fun defaultJudgeNeverUsesCustomKeyOrModelAndReusesOnlyOpenRouterKey() {
        val s=AppSettings.decode("""{"profile":{"baseUrl":"https://openrouter.ai/api/v1","apiKey":"openrouter-key"},"judgeCustom":{"baseUrl":"https://example.com/v1","apiKey":"custom-key","selectedModelId":"custom-model"}}""")
        assertEquals("OPENROUTER",s.judgeProvider);assertFalse(s.judgeProfile().custom)
        assertEquals(ProviderPresets.JEV_MODEL,s.judgeProfile().model);assertEquals("openrouter-key",s.judgeProfile().apiKey)
    }
    @Test fun oldSettingsDefaultToNoContactStylesAndCollapseAfterFill() {
        val json=Gson().toJsonTree(AppSettings.decode("{}" )).asJsonObject
        assertEquals("collapse",json["fillPanelBehavior"]?.asString)
        assertEquals(0,json["replyStyles"]?.asJsonObject?.size())
    }
    @Test fun roundTripPreservesStyleAndKeepBehaviorAlongsideApiSelections() {
        val raw="""{"replyStyles":{"[\"com.tencent.mm\",\"小雨\"]":"温柔简短"},"fillPanelBehavior":"keep","profile":{"baseUrl":"https://openrouter.ai/api/v1","apiKey":"fake","selectedModelId":"chosen"}}"""
        val loaded=AppSettings.decode(raw)
        val json=Gson().toJsonTree(AppSettings.decode(Gson().toJson(loaded))).asJsonObject
        assertEquals("keep",json["fillPanelBehavior"]?.asString)
        assertEquals("温柔简短",json["replyStyles"]?.asJsonObject?.get("[\"com.tencent.mm\",\"小雨\"]")?.asString)
        assertEquals("chosen",loaded.profile.selectedModelId)
    }
    @Test fun oldConfigurationGetsVisionDefaultsAndFixedJudgeWithoutLosingChosenReply() {
        val migrated=AppSettings.decode("""{"profile":{"baseUrl":"https://example.com/v1","apiKey":"fake","selectedModelId":"chosen"},"automaticAnalysis":false,"jev":{"model":"old","endpoint":"https://old.invalid","apiKey":"fake"}}""")
        assertEquals("chosen",migrated.profile.selectedModelId)
        assertEquals("CUSTOM",migrated.replyProvider)
        assertEquals("qwen/qwen2.5-vl-72b-instruct",migrated.vision.selectedModelId)
        assertEquals(ProviderPresets.JEV_MODEL,migrated.jev.model); assertEquals(ProviderPresets.JEV_ENDPOINT,migrated.jev.endpoint)
        assertEquals("",migrated.jev.apiKey) // an old custom judgment service's key must not move to OpenRouter
        assertTrue(migrated.automaticAnalysis)
    }
    @Test fun openingAgainPreservesSelectionsInsteadOfReapplyingDefaults() {
        val data=SettingsData(profile=ProviderPresets.OPENROUTER.reply("fake").copy(selectedModelId="my-reply"),vision=ProviderPresets.OPENROUTER.vision("fake").copy(selectedModelId="my-vision"))
        val loaded=AppSettings.decode(Gson().toJson(data)); assertEquals("my-reply",loaded.profile.selectedModelId); assertEquals("my-vision",loaded.vision.selectedModelId)
    }
}
