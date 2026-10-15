package com.loveadvisor.core.overlay
import org.junit.Assert.*
import org.junit.Test

class NativeIslandEntryPolicyTest {
    private val eligible=IslandEnvironment(true,true,true,"build-one")
    @Test fun postingWithoutARealClickKeepsTheFallbackBall() {
        assertEquals(OverlayEntryMode.ISLAND_TEST,NativeIslandEntryPolicy.mode(true,null,eligible,true))
        assertEquals(OverlayEntryMode.BUBBLE,NativeIslandEntryPolicy.mode(true,null,eligible,false))
    }
    @Test fun onlyTheSameBuildWithPublishedNotificationMayUseTheIsland() {
        assertEquals(OverlayEntryMode.ISLAND,NativeIslandEntryPolicy.mode(true,"build-one",eligible,true))
        assertEquals(OverlayEntryMode.ISLAND_TEST,NativeIslandEntryPolicy.mode(true,"old-build",eligible,true))
        assertEquals(OverlayEntryMode.BUBBLE,NativeIslandEntryPolicy.mode(false,"build-one",eligible,true))
        for(env in listOf(eligible.copy(allowed=false),eligible.copy(vivo=false),eligible.copy(notificationsEnabled=false))) {
            assertEquals(OverlayEntryMode.BUBBLE,NativeIslandEntryPolicy.mode(true,"build-one",env,true))
        }
    }
    @Test fun ordinaryStaleOrFailedPanelClicksCannotConfirm() {
        assertFalse(NativeIslandEntryPolicy.canConfirm(false,true,true,eligible))
        assertFalse(NativeIslandEntryPolicy.canConfirm(true,false,true,eligible))
        assertFalse(NativeIslandEntryPolicy.canConfirm(true,true,false,eligible))
        assertFalse(NativeIslandEntryPolicy.canConfirm(true,true,true,eligible.copy(allowed=false)))
        assertTrue(NativeIslandEntryPolicy.canConfirm(true,true,true,eligible))
    }
    @Test fun leaseCannotSurviveNavigationOrReplacementOrLeavingTheApp() {
        assertTrue(NativeIslandEntryPolicy.currentLease("a","a","com.tencent.mm",7,3,"com.tencent.mm",7,3,true))
        assertFalse(NativeIslandEntryPolicy.currentLease("old","a","com.tencent.mm",7,3,"com.tencent.mm",7,3,true))
        assertFalse(NativeIslandEntryPolicy.currentLease("a","a","com.tencent.mm",7,3,"com.tencent.mm",7,4,true))
        assertFalse(NativeIslandEntryPolicy.currentLease("a","a","com.tencent.mm",7,3,"com.tencent.mobileqq",7,3,true))
        assertFalse(NativeIslandEntryPolicy.currentLease("a","a","com.tencent.mm",7,3,"com.tencent.mm",8,3,true))
        assertFalse(NativeIslandEntryPolicy.currentLease("a","a","com.tencent.mm",7,3,"com.tencent.mm",7,3,false))
    }
}
