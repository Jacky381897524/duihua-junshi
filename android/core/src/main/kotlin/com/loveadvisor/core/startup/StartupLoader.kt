package com.loveadvisor.core.startup

import java.util.concurrent.Executor

enum class StartupPhase { IDLE, LOADING, SLOW, READY, FAILED }
data class StartupState<T>(val phase:StartupPhase=StartupPhase.IDLE,val attempt:Long=0,val value:T?=null,val errorType:String?=null)

/** One background read shared by startup consumers. A slow read never blocks delivery/UI. */
class StartupLoader<T>(private val worker:Executor,private val delivery:Executor,private val load:()->T) {
    @Volatile var state=StartupState<T>();private set
    private var nextListener=0L
    private val listeners=linkedMapOf<Long,(StartupState<T>)->Unit>()
    @Synchronized fun observe(listener:(StartupState<T>)->Unit):()->Unit {
        val id=++nextListener;listeners[id]=listener;deliver(id,listener,state)
        return { synchronized(this) { listeners.remove(id);Unit } }
    }
    @Synchronized fun start() {
        if(state.phase in setOf(StartupPhase.LOADING,StartupPhase.SLOW,StartupPhase.READY))return
        val attempt=state.attempt+1
        publish(StartupState(StartupPhase.LOADING,attempt))
        worker.execute {
            val next=try { StartupState(StartupPhase.READY,attempt,load()) }
                catch(e:Exception) { StartupState<T>(StartupPhase.FAILED,attempt,errorType=e.javaClass.simpleName) }
            synchronized(this) { if(state.attempt==attempt)publish(next) }
        }
    }
    @Synchronized fun markSlow(attempt:Long) {
        if(state.phase==StartupPhase.LOADING && state.attempt==attempt)publish(state.copy(phase=StartupPhase.SLOW))
    }
    private fun publish(value:StartupState<T>) { state=value;listeners.toMap().forEach { (id,listener)->deliver(id,listener,value) } }
    private fun deliver(id:Long,listener:(StartupState<T>)->Unit,value:StartupState<T>) {
        delivery.execute {
            val current=synchronized(this) { listeners[id]===listener && state===value }
            if(current)listener(value)
        }
    }
}
