package com.loveadvisor.core.overlay

/** Current-session proof only: a past successful build cannot prove a new native surface exists. */
class IslandSessionState {
    private var scope:String?=null
    var id:String?=null;private set
    var published=false
    var confirmed=false;private set
    var failed=false;private set
    private var started=0L
    fun enter(key:String,newId:String,now:Long):Boolean {
        if(scope==key) return false
        stop();scope=key;id=newId;started=now;return true
    }
    fun confirm(received:String,panelShown:Boolean):Boolean {
        if(received!=id || !published || failed || !panelShown) return false
        confirmed=true;return true
    }
    fun expired(now:Long)=!confirmed && id!=null && now-started>=60000L
    fun fail() { published=false;confirmed=false;failed=true;id=null }
    fun stop() { scope=null;id=null;published=false;confirmed=false;failed=false;started=0 }
}
