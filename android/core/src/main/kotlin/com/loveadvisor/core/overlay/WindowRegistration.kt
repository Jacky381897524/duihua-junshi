package com.loveadvisor.core.overlay

/** Main-thread window ownership; a view can be registered before its first frame attaches it. */
class WindowRegistration {
    var registered = false
        private set

    fun attach(add: () -> Unit) {
        if (registered) return
        registered = true
        try {
            add()
        } catch (failure: Throwable) {
            registered = false
            throw failure
        }
    }

    fun detach(remove: () -> Unit) {
        if (!registered) return
        remove()
        registered = false
    }
}
