package com.loveadvisor.android.storage

import android.content.Context
import com.loveadvisor.core.storage.CacheCleaner

/** Only disposable cache directories; filesDir, Keystore and codeCacheDir are never passed in. */
class AppCache(private val context: Context) {
    private fun cleaner() = CacheCleaner(listOf(context.cacheDir) + context.externalCacheDirs.orEmpty().filterNotNull())
    @Synchronized fun usage(checkCancelled: () -> Unit = {}) = cleaner().usage(checkCancelled)
    @Synchronized fun clear(checkCancelled: () -> Unit = {}) = cleaner().clear(checkCancelled)
}
