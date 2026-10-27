package expo.modules.callkittelecom

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import com.facebook.react.jstasks.HeadlessJsTaskContext
import expo.modules.callkittelecom.models.CallSessionStatus
import expo.modules.callkittelecom.store.CallStore
import expo.modules.callkittelecom.utils.CallKitTelecomLog
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the app's JS running for calls answered on the lock screen.
 *
 * React Native pauses every JS timer while its host activity is paused. A call answered on the lock
 * screen runs with the app's main activity stopped behind the keyguard, so the call engine's timers
 * (offer debounce, signalling reconnect, ...) would not fire until the phone is unlocked. React
 * Native keeps timers running while a headless JS task is active, so one [TASK_KEY] task is held
 * for as long as any such call lasts. The module's JS registers [TASK_KEY] as a task that never
 * resolves; it is finished from here when the last held call ends (its session is ENDED or gone).
 *
 * The keep-alive does not start React: every lock-screen path is gated on [canKeepAlive], and
 * should React go down while a call is held (a reload), the task is started again in the next React
 * context ([resume]).
 *
 * The JS side may not have registered [TASK_KEY] yet when the task starts (the module imported
 * late); JS then finishes it at once. The task is checked again after [RETRY_DELAY_MS] and
 * restarted, at most [MAX_TASK_STARTS] times per hold.
 *
 * All state is confined to the main thread, except that [isHeld] and [isHolding] may be read from
 * any thread.
 */
object LockedCallKeepAlive {
    private const val TAG = "ExpoCallKitTelecom.LockedCallKeepAlive"

    /** The headless task key the module's JS registers (`src/lockScreenKeepAlive.ts`). */
    const val TASK_KEY = "ExpoCallKitTelecomCallKeepAlive"

    private const val RETRY_DELAY_MS = 1_500L
    private const val MAX_TASK_STARTS = 3
    private const val END_POLL_MS = 1_000L

    private val main = Handler(Looper.getMainLooper())

    /** Calls currently held; the task runs while this is non-empty. Written on the main thread. */
    private val heldCalls: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    /**
     * The host whose React contexts are watched ([watchHost]). An app has one: `ReactApplication`
     * returns the same [ReactHost] for the life of the process (Expo's creates it lazily, once), so
     * the listener is added once; a second instance, should an app ever have one, gets its own.
     */
    private var watchedHost: ReactHost? = null
    private var reactContext: ReactContext? = null
    private var taskId: Int? = null
    private var taskStarts = 0

    /** The app's [ReactHost], when the application is a [ReactApplication]. */
    fun reactHost(context: Context): ReactHost? =
        (context.applicationContext as? ReactApplication)?.reactHost

    /**
     * Whether a call can be kept alive now: the app's React context is up. The call screen only
     * answers on the lock screen when the app's JS is there to drive the call; otherwise the
     * default flow (unlock, open the app) applies.
     */
    fun canKeepAlive(context: Context): Boolean = reactHost(context)?.currentReactContext != null

    /** Whether [callId] is held — a call answered on the lock screen that has not ended. */
    fun isHeld(callId: UUID): Boolean = callId in heldCalls

    /** Whether any call is held. */
    fun isHolding(): Boolean = heldCalls.isNotEmpty()

    /**
     * Holds the keep-alive for [callId] until the call ends. Idempotent: holding a call again only
     * makes sure the task runs. With React not up, the task starts once it is.
     */
    fun hold(context: Context, callId: UUID) {
        val appContext = context.applicationContext
        runOnMain {
            if (heldCalls.add(callId)) {
                CallKitTelecomLog.d(TAG) { "Holding keep-alive for $callId" }
                watchCallEnd(callId)
            }
            startTask(appContext)
        }
    }

    /** Makes sure the task runs, now or once React is up. */
    private fun startTask(appContext: Context) {
        val host = reactHost(appContext)
        if (host == null) {
            CallKitTelecomLog.e(TAG) { "No ReactHost, keep-alive not started" }
            return
        }
        watchHost(host)
        // Checked after watchHost: a context that came up before the listener was added is seen
        // here, one that comes up after it is seen by the listener, and one seen by both starts
        // the task once (resume skips the context the task already runs in).
        val current = host.currentReactContext
        if (current != null) {
            ensureTask(current)
        } else {
            CallKitTelecomLog.d(TAG) { "React not running yet, waiting for its context" }
        }
    }

    /**
     * Listens, once per host, for every React context it creates: the first one while React is not
     * up yet, and a new one after a reload or a restart while calls are held, when the task went
     * down with the old context.
     */
    private fun watchHost(host: ReactHost) {
        if (watchedHost === host) return
        watchedHost = host
        host.addReactInstanceEventListener(
            object : ReactInstanceEventListener {
                override fun onReactContextInitialized(context: ReactContext) = resume(context)
            },
        )
    }

    /** Starts the task in [context], a React context the task does not run in yet. */
    private fun resume(context: ReactContext) {
        runOnMain {
            if (!isHolding() || reactContext === context) return@runOnMain
            CallKitTelecomLog.d(TAG) { "New React context, starting keep-alive" }
            // A previous task id belongs to the previous context.
            taskId = null
            taskStarts = 0
            ensureTask(context)
        }
    }

    /** Starts the task unless it runs, then checks again later in case JS finished it at once. */
    private fun ensureTask(context: ReactContext) {
        if (!isHolding()) return
        reactContext = context
        val tasks = HeadlessJsTaskContext.getInstance(context)
        if (taskId?.let { tasks.isTaskRunning(it) } == true) return
        if (taskStarts >= MAX_TASK_STARTS) {
            CallKitTelecomLog.e(TAG) { "Keep-alive task never stayed up, giving up" }
            return
        }
        taskStarts++
        try {
            // Timeout 0 = no timeout; allowed in the foreground, since the app may come to the
            // front (unlocked) while the call runs.
            taskId = tasks.startTask(HeadlessJsTaskConfig(TASK_KEY, Arguments.createMap(), 0, true))
            CallKitTelecomLog.d(TAG) { "Keep-alive task started: $taskId (start $taskStarts)" }
        } catch (e: Exception) {
            CallKitTelecomLog.e(TAG, e) { "Keep-alive task failed to start" }
        }
        // Skipped once released, or when a later hold moved to another React context.
        main.postDelayed({ if (reactContext === context) ensureTask(context) }, RETRY_DELAY_MS)
    }

    /** Polls on the main looper — native, unlike JS timers — until the session is ENDED or gone. */
    private fun watchCallEnd(callId: UUID) {
        main.postDelayed(
            object : Runnable {
                override fun run() {
                    if (callId !in heldCalls) return
                    val session = CallStore.session(callId)
                    if (session == null || session.status == CallSessionStatus.ENDED) {
                        release(callId)
                    } else {
                        main.postDelayed(this, END_POLL_MS)
                    }
                }
            },
            END_POLL_MS,
        )
    }

    private fun release(callId: UUID) {
        heldCalls.remove(callId)
        CallKitTelecomLog.d(TAG) { "Call $callId ended, released" }
        if (isHolding()) return

        val id = taskId
        val context = reactContext
        taskId = null
        reactContext = null
        taskStarts = 0
        if (id != null && context != null) {
            val tasks = HeadlessJsTaskContext.getInstance(context)
            if (tasks.isTaskRunning(id)) tasks.finishTask(id)
            CallKitTelecomLog.d(TAG) { "Keep-alive task finished: $id" }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
