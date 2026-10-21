package com.loveadvisor.core.capture
import org.junit.Assert.*
import org.junit.Test

class WindowPackageCacheTest {
    @Test fun packageMissingWindowsEventResolvesEveryKnownChatWithoutTreeLookup() {
        for(pkg in listOf(ChatApps.WECHAT,"com.tencent.mobileqq","com.ss.android.ugc.aweme")) {
            val cache=WindowPackageCache();cache.remember(7,pkg)
            assertEquals(ForegroundSignal(pkg,7),cache.signal(listOf(AppWindow(7,true,true,4)),90,null,false))
        }
    }
    @Test fun launcherFocusWinsOverOldChatEvenWithNullEventPackage() {
        val cache=WindowPackageCache();cache.remember(7,ChatApps.WECHAT);cache.remember(8,"com.example.launcher")
        assertEquals(ForegroundSignal("com.example.launcher",8),cache.signal(listOf(AppWindow(7,false,true,9),AppWindow(8,true,false,4)),7,null,false))
    }
    @Test fun anotherWindowEventCannotTeachFocusedWindowItsPackage() {
        val cache=WindowPackageCache()
        assertNull(cache.signal(listOf(AppWindow(7,true,true,4)),90,"com.example.keyboard",true))
        assertNull(cache.packageFor(7))
    }
    @Test fun onlyMatchingStateEventCanTeachUnknownFocusedWindow() {
        val cache=WindowPackageCache();val apps=listOf(AppWindow(7,true,false,4))
        assertNull(cache.signal(apps,7,ChatApps.WECHAT,false))
        assertEquals(ForegroundSignal(ChatApps.WECHAT,7),cache.signal(apps,7,ChatApps.WECHAT,true))
    }
    @Test fun removalAndReuseRequireFreshOwnership() {
        val cache=WindowPackageCache();cache.remember(7,ChatApps.WECHAT);cache.forget(7)
        assertNull(cache.signal(listOf(AppWindow(7,true,true,4)),7,null,false))
        cache.remember(7,"com.tencent.mobileqq");assertEquals("com.tencent.mobileqq",cache.packageFor(7))
    }
    @Test fun cacheIsBoundedAndNeverReusesAnUnfocusedEntry() {
        val cache=WindowPackageCache(2);cache.remember(1,ChatApps.WECHAT);cache.remember(2,"com.tencent.mobileqq");cache.remember(3,"com.ss.android.ugc.aweme")
        assertNull(cache.packageFor(1));assertEquals("com.tencent.mobileqq",cache.packageFor(2))
        assertNull(cache.signal(listOf(AppWindow(3,false,true,4)),3,null,false))
    }
}
