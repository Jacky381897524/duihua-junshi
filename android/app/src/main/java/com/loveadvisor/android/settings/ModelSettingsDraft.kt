package com.loveadvisor.android.settings

import com.loveadvisor.android.storage.SettingsData
import com.loveadvisor.core.api.*

/** Applies only model fields, so concurrent local style edits cannot be overwritten. */
class ModelSettingsDraft(private val candidate:SettingsData) {
    fun validated():SettingsData {
        val reply=candidate.profile;val vision=candidate.vision
        EndpointResolver().resolve(reply);EndpointResolver().resolve(vision)
        require(!reply.selectedModelId.isNullOrBlank() && !vision.selectedModelId.isNullOrBlank()) { "请选择回复和视觉模型" }
        require(reply.apiKey.isNotBlank() && reply.apiKey.none { it.isISOControl() }) { "请填写有效的回复密钥" }
        val visionKey=vision.apiKey.ifBlank { if(ProviderPresets.ownsOpenRouter(vision))ProviderPresets.openRouterKey(reply,vision) else "" }
        require(visionKey.isNotBlank() && visionKey.none { it.isISOControl() }) { "请填写有效的视觉密钥" }
        val judge=candidate.judgeProfile()
        require(judge.apiKey.isNotBlank() && judge.apiKey.none { it.isISOControl() }) { "请填写有效的判断密钥" }
        if(judge.custom) { require(judge.model.isNotBlank()) { "请选择判断模型" };EndpointResolver().resolve(candidate.judgeCustom) }
        return candidate
    }
    fun applyTo(current:SettingsData):SettingsData {
        val value=validated()
        return current.copy(profile=value.profile,vision=value.vision,models=value.models,visionModels=value.visionModels,replyProvider=value.replyProvider,visionProvider=value.visionProvider,providerProfiles=value.providerProfiles,manualModel=false,automaticAnalysis=true,jev=value.jev,judgeProvider=value.judgeProvider,judgeCustom=value.judgeCustom)
    }
}
