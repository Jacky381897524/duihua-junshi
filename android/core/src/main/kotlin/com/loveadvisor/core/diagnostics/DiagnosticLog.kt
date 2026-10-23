package com.loveadvisor.core.diagnostics

import com.loveadvisor.core.api.ApiException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException

enum class LogStep { PROCESS_START,APP_CREATE,STARTUP,PAGE_OPEN,PAGE_CLOSE,UI_ACTION,SETTING_CHANGE,SERVICE_CREATE,SERVICE_BIND,SERVICE_READY,SERVICE_INTERRUPT,SERVICE_UNBIND,SERVICE_DESTROY,FOREGROUND_ENTER,FOREGROUND_LEAVE,OVERLAY_SHOW,OVERLAY_HIDE,READ_TAP,READ_NATIVE,READ_PARSE,READ_ACCEPT,READ_FINISH,SCREENSHOT_REQUEST,SCREENSHOT_RESULT,VISION_REQUEST,VISION_RESULT,REPLY_REQUEST,REPLY_RESULT,REFRESH_TAP,TARGET_REPLY_TAP,JUDGMENT_TAP,JUDGMENT_REQUEST,JUDGMENT_CACHE,JUDGMENT_RESULT,ADVICE_REQUEST,ADVICE_RESULT,COPY_TAP,COPY_RESULT,FILL_TAP,FILL_STAGE,FILL_RESULT,MODEL_TEST,MODEL_CATALOG,CONFIG_SAVE,LOCAL_TASK,LOG_OPEN,LOG_REFRESH,LOG_EXPORT,EXCEPTION,PROCESS_EXIT,WINDOW_EVENT,WINDOW_LOOKUP,EDITOR_FOCUS,OVERLAY_LAYOUT,TIME_CONTEXT,MEMORY_READ,MEMORY_LEARN,MEMORY_SAVE,MEMORY_EDIT,MEMORY_DELETE,MEMORY_RETRIEVE }
enum class LogState { START,STEP,OK,FAIL,CANCELLED,SKIP,WARN,CRASH }
enum class LogReason { NONE,UNEXPECTED,NO_FOREGROUND,NO_CHAT,NO_NODES,NEEDS_VISION,NO_MODEL_KEY,SCREENSHOT_SECURE,SCREENSHOT_FAILED,VISION_EMPTY,INVALID_RESPONSE,MODEL_REFUSED,HTTP_ERROR,AUTH_ERROR,RATE_LIMIT,REMOTE_ERROR,NETWORK_TIMEOUT,NETWORK_DNS,NETWORK_CONNECTION,NETWORK_TLS,INPUT_NOT_FOUND,INPUT_CONNECTION_MISSING,CONTEXT_CHANGED,DRAFT_CHANGED,FILL_TIMEOUT,NO_READ_CONTEXT,BUSY,USER_CANCELLED,STORAGE_FAILURE,SETTINGS_FAILURE,RESULT_STALE,STEP_FAILED,INCOMPLETE_PREVIOUS,UNCAUGHT_EXCEPTION,OK_DISPATCHED,UI_READ,UI_JUDGMENT,UI_FILL,UI_COPY,UI_ADVICE,UI_REFRESH,UI_SAVE,UI_BACK,UI_CHOOSE,UI_MODEL_TEST,UI_MODEL_CATALOG,UI_SETTINGS,UI_PERMISSIONS,UI_SERVICE_STOP,UI_CACHE_CLEAR,UI_LOG_EXPORT,UI_OTHER,IME_METHOD_ABSENT,IME_EDITOR_ABSENT,IME_OTHER_PACKAGE,IME_EDITOR_REJECTED,IME_CONNECTION_ABSENT,IME_CONNECTION_READY,FOCUS_REQUESTED,FOCUS_REJECTED,FOCUS_CANCELLED,FOCUS_TIMED_OUT,TITLE_MISMATCH,COMPOSER_LAYOUT_MISMATCH,NATIVE_INPUT_UNAVAILABLE,PAGE_MAIN,PAGE_MODEL_SETTINGS,PAGE_REPLY_STYLE,PAGE_OVERLAY_SETTINGS,PAGE_DIAGNOSTICS,PAGE_PERMISSIONS,PAGE_CONSULTATION,PAGE_JUDGMENT,PAGE_PICKER,PAGE_OTHER,UI_MODEL_SETTINGS,UI_OVERLAY_SETTINGS,UI_STYLE_SETTINGS,UI_TARGET_REPLY,UI_OPEN_PANEL,UI_CLOSE_PANEL,FILL_NATIVE_READ,FILL_CLICK,FILL_FOCUS,FILL_SET_TEXT,FILL_PASTE,FILL_SELECTION,FILL_VERIFY,UI_NATURAL_REPLIES,TOPIC_EXPIRED,UI_REALISTIC_REPLIES }
enum class LogMetric { DURATION_MS,COUNT,NODE_COUNT,MESSAGE_COUNT,WIDTH,HEIGHT,WINDOW_ID,HTTP_STATUS,SCREENSHOT_CODE,PLATFORM,CHAT_KIND,HAS_INPUT,HAS_DRAFT,INPUT_TYPE,IME_OPTIONS,API_LEVEL,RESULT_COUNT,CACHED,ENABLED,ATTEMPT,READY,EVENT_TYPE,WINDOW_CHANGES,EVENT_AGE_MS,WINDOW_COUNT,FOCUSED_APP_ID,TRANSIENT_FOCUSED,EXTERNAL_FOCUSED,EDITOR_ACTIVE,LEFT,TOP,EXPIRED_MESSAGE_COUNT }

data class LogFault(val reason:LogReason,val types:List<String>,val frames:List<String>,val httpStatus:Int?=null) {
    companion object {
        private val classToken=Regex("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
        private val methodToken=Regex("(?:[A-Za-z_$][A-Za-z0-9_$]*|<init>|<clinit>)")
        private val frameToken=Regex("[A-Za-z_$][A-Za-z0-9_$.]*\\.(?:[A-Za-z_$][A-Za-z0-9_$]*|<init>|<clinit>):(?:-1|[0-9]{1,7})")

        /** Never reads Throwable.message, localizedMessage, toString, or source filenames. */
        fun capture(error:Throwable):LogFault {
            val seen=Collections.newSetFromMap(IdentityHashMap<Throwable,Boolean>())
            val causes=ArrayList<Throwable>(4)
            var current:Throwable?=error
            while(current!=null && causes.size<4 && seen.add(current)) {
                causes.add(current)
                current=try { current.cause } catch(_:Throwable) { null }
            }
            val types=causes.map { it.javaClass.name }.filter(::validType)
            val frames=causes.flatMap { cause ->
                (try { cause.stackTrace.take(128) } catch(_:Throwable) { emptyList() }).mapNotNull { frame ->
                    if(!validType(frame.className) || frame.methodName.length>80 || !methodToken.matches(frame.methodName)) null
                    else "${frame.className}.${frame.methodName}:${frame.lineNumber.coerceIn(-1,9_999_999)}"
                }
            }.sortedBy { if(it.startsWith("com.loveadvisor.")) 0 else 1 }.take(8)
            val status=causes.filterIsInstance<ApiException>().firstNotNullOfOrNull { it.status?.takeIf { value -> value in 100..599 } }
            val reason=causes.firstNotNullOfOrNull { cause ->
                when(cause) {
                    is com.loveadvisor.core.advisor.ExpiredConversationException -> LogReason.TOPIC_EXPIRED
                    is ApiException -> when(cause.status) {
                        401,403 -> LogReason.AUTH_ERROR
                        429 -> LogReason.RATE_LIMIT
                        in 500..599 -> LogReason.REMOTE_ERROR
                        in 400..499 -> LogReason.HTTP_ERROR
                        else -> null
                    }
                    is SocketTimeoutException -> LogReason.NETWORK_TIMEOUT
                    is UnknownHostException -> LogReason.NETWORK_DNS
                    is ConnectException -> LogReason.NETWORK_CONNECTION
                    is SSLException -> LogReason.NETWORK_TLS
                    is CancellationException -> LogReason.USER_CANCELLED
                    else -> null
                }
            } ?: LogReason.UNEXPECTED
            return sanitized(LogFault(reason,types,frames,status))
        }

        internal fun validType(value:String)=value.length in 1..180 && classToken.matches(value)
        internal fun validFrame(value:String)=value.length<=280 && frameToken.matches(value)
        internal fun sanitized(value:LogFault)=LogFault(value.reason,Collections.unmodifiableList(value.types.filter(::validType).take(4)),Collections.unmodifiableList(value.frames.filter(::validFrame).take(8)),value.httpStatus?.takeIf { it in 100..599 })
    }
}

/** A handle carries generated identifiers only; its outcome cannot be overwritten by a late callback. */
class LogOperation internal constructor(internal val owner:Any,internal val id:String,internal val parentId:String?,internal val step:LogStep,internal val startedAt:Long) {
    internal val ended=AtomicBoolean(false)
}

data class LogRecord(val id:String,val timestampMillis:Long,val session:String,val version:String,val build:Int,val pid:Int,val operationId:String?,val parentOperationId:String?,val step:LogStep,val state:LogState,val reason:LogReason,val metrics:Map<LogMetric,Long>,val fault:LogFault?)
enum class LogHistoryState { LOADING,EMPTY,LOADED,CORRUPT,OVERSIZED,UNAVAILABLE }
data class LogSnapshot(val events:List<LogRecord>,val historyState:LogHistoryState,val evicted:Long,val rejected:Long,val historyFailures:Long,val writeFailures:Long,val persisted:Boolean,val incompletePrevious:Int)
