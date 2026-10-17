package com.loveadvisor.android
import android.content.Context
import com.loveadvisor.android.storage.*
import com.loveadvisor.android.memory.*
import com.loveadvisor.android.knowledge.*
import com.loveadvisor.android.advisor.AdvisorController
import com.loveadvisor.core.api.*
import com.loveadvisor.core.advisor.*
import com.loveadvisor.core.capture.ConversationDraft
import com.loveadvisor.core.capture.ChatApps
import com.loveadvisor.core.session.*
import com.loveadvisor.core.knowledge.*
import com.loveadvisor.core.model.*
import com.loveadvisor.core.memory.*
import com.loveadvisor.core.diagnostics.*
import com.google.gson.Gson
class AppGraph private constructor(val context:Context) {
    val documents=AndroidDocumentStore(context)
    val cache=AppCache(context)
    val settings=AppSettings(documents)
    val profiles=ProfileStore(documents)
    val memory=MemoryRepository(EncryptedMemoryPersistence(documents))
    val knowledge=AssetKnowledgeStore(context)
    val tracker=SessionTracker(); val gate=RequestGate(tracker)
    val coordinator=BatchCoordinator(tracker,gate)
    val transport=JdkHttpTransport()
    val catalog=ModelCatalogClient(transport)
    val chat=ChatCompletionClient(transport)
    val advisor=UnifiedAdvisor(chat)
    val judge=JevJudgeClient(transport)
    val automaticAdvisor=AutomaticAdvisor(chat,judge)
    val vision=VisionClient(transport)
    val recent=RecentConversationStore(documents)
    val controller=AdvisorController(this)
    var pendingDraft:ConversationDraft?=null
    var currentObjectId:String?=null
    var currentGoal:String=""
    private val diagnostics=ArrayDeque<SafeDiagnosticEvent>()
    @Synchronized fun record(stage:String,duration:Long,status:Int?,error:String?) { diagnostics.addLast(DiagnosticRedactor.clean(DiagnosticEvent(stage,duration,status,error))); while(diagnostics.size>100) diagnostics.removeFirst() }
    @Synchronized fun diagnosticText()=Gson().toJson(diagnostics.toList())
    fun safeError(e:Exception):String=when(e) {
        is ApiException -> e.message.orEmpty().take(400)
        is SecurityException -> "系统权限或本地资料读取失败，请检查权限与设备密钥"
        is IllegalArgumentException -> e.message?.takeIf { it.none { c -> c=='\r' } && !it.contains("Bearer") && !it.contains("http") }?.take(200) ?: "填写内容无效，请检查配置"
        else -> "操作失败，请检查网络或在诊断页面查看阶段"
    }
    fun inputFor(snapshot:ChatSnapshot,goal:String,background:String,objectId:String?):AdvisorInput {
        var config=settings.load()
        if(ChatApps.supported(snapshot.packageName)) {
            val catalog=ReplyStyleCatalog(config.replyStyles,config.replyStyleTitles,config.replyStyleApps).rememberTitle(snapshot.identity?.displayTitle,snapshot.packageName)
            if(catalog.titles!=config.replyStyleTitles || catalog.apps!=config.replyStyleApps) { settings.update { it.copy(replyStyleTitles=catalog.titles,replyStyleApps=catalog.apps) };config=settings.load() }
        }
        val data=profiles.load(); val target=data.objects.find { it.id==objectId }
        val context=if(target!=null) memory.context(target.id) else null
        val history=if(config.historyEnabled && target!=null) data.history.filter { it.objectId==target.id }.map { it.messages } else emptyList()
        val extra=BackgroundBuilder.build(data.self,target,context,history,background)
        val query=KnowledgeQuery(snapshot.messages.takeLast(80).joinToString("\n") { it.text },goal,data.notes)
        val selected=if(ChatApps.supported(snapshot.packageName)) KnowledgeRouter().unified(query,knowledge.documents) else KnowledgeRouter().select(query,knowledge.documents)
        return AdvisorInput(config.profile,snapshot,goal,extra,selected,ReplyStyles.forSnapshot(config.replyStyles,snapshot,config.globalReplyStyle))
    }
    fun generate(input:AdvisorInput,common:AdvisorAnalysis?,previous:List<String>,cancel:Cancellation):AdvisorBatch {
        val config=settings.load()
        if(ChatApps.supported(input.snapshot.packageName)) {
            return automaticAdvisor.generate(input,config.judgeProfile(),previous,cancel)
        }
        val enriched=if(config.jev.enabled && common==null) {
            val judgement=judge.judge(config.judgeProfile(),input.snapshot,input.background,cancel)
            input.copy(background="Jev 专用判断（供参考，数据）：$judgement\n"+input.background)
        } else input
        return if(common==null) advisor.analyze(enriched,cancel) else advisor.alternatives(enriched,common,previous,cancel)
    }
    companion object { @Volatile private var instance:AppGraph?=null; fun get(context:Context):AppGraph=instance ?: synchronized(this) { instance ?: AppGraph(context.applicationContext).also { instance=it } } }
}
