package com.loveadvisor.core.diagnostics
class DiagnosticCrashHandler(private val delegate:(Thread,Throwable)->Unit,private val receipt:(Throwable)->Unit):Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread:Thread,error:Throwable) {
        try { receipt(error) } catch(_:Throwable) { /* Logging must not replace the fatal error. */ }
        finally { delegate(thread,error) }
    }
}
