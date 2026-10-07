package com.loveadvisor.core.api
class CatalogState(initialModelId:String?=null) {
    var selectedModelId:String?=initialModelId; private set
    var models:List<ApiModel> = emptyList(); private set
    var resolvedBaseUrl:String?=null; private set
    var error:String?=null; private set
    private var manualSelection=false
    fun apply(result:CatalogResult,currentRevision:Long):Boolean {
        if(result.profileRevision!=currentRevision) return false
        error=result.error
        if(result.error!=null || result.models.isEmpty()) return false
        models=result.models.toList(); resolvedBaseUrl=result.resolvedBaseUrl
        return true
    }
    fun select(id:String):Boolean {
        if(models.none { it.id==id }) return false
        selectedModelId=id; manualSelection=false; return true
    }
    fun manual(id:String):Boolean {
        if(id.isBlank() || id.length>256 || id.any { it=='\n' || it=='\r' }) return false
        selectedModelId=id.trim(); manualSelection=true; return true
    }
    fun invalidate() { models=emptyList(); resolvedBaseUrl=null; error=null; manualSelection=false }
    fun canSave():Boolean = !selectedModelId.isNullOrBlank() && (manualSelection || models.any { it.id==selectedModelId })
}
