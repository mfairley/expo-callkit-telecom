package expo.modules.callkittelecom.managers

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import expo.modules.callkittelecom.IncomingCallActivity
import expo.modules.callkittelecom.LockedCallKeepAlive
import expo.modules.callkittelecom.R
import expo.modules.callkittelecom.models.CallSessionStatus
import expo.modules.callkittelecom.services.CallNotificationReceiver
import expo.modules.callkittelecom.store.CallStore
import expo.modules.callkittelecom.utils.CallKitTelecomLog
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages call notifications across all call states.
 *
 * Creates CallStyle notifications with full-screen intent for lock screen display and notification
 * shade answer/decline actions. Supports incoming, dialing, ongoing, and ended notification states.
 */
object CallNotificationManager {
    private const val TAG = "ExpoCallKitTelecom.Notification"

    private const val CHANNEL_INCOMING_PREFIX = "expo_callkit_telecom_incoming"
    private const val CHANNEL_ONGOING = "expo_callkit_telecom_ongoing"
    private const val NOTIFICATION_ID = 8400
    private const val ENDED_CANCEL_DELAY_MS = 2000L

    private const val KEY_DEFAULT_RINGTONE = "ExpoCallKitTelecomDefaultRingtone"
    private const val KEY_ANSWER_ON_LOCK_SCREEN = "ExpoCallKitTelecomAnswerOnLockScreen"
    private const val PREFS_NAME = "expo_callkit_telecom_notifications"
    private const val PREF_INCOMING_CHANNEL_ID = "incoming_channel_id"

    private var isInitialized = false
    private lateinit var appContext: Context

    /** The active incoming channel ID, derived from the configured ringtone. */
    private lateinit var incomingChannelId: String

    /**
     * The `androidAnswerOnLockScreen` plugin prop: a call answered on the lock screen stays there,
     * on the call screen, instead of unlocking and opening the app. Read once from the manifest.
     */
    internal var answerOnLockScreenEnabled = false
        private set

    /** The ring currently posted, so its Answer action can follow the lock state while it rings. */
    private data class Ring(
        val callId: UUID,
        val callerName: String?,
        val hasVideo: Boolean,
        val answersLocked: Boolean,
    )

    @Volatile private var ring: Ring? = null

    /**
     * Serialises the "Connecting…" post (main thread) with the ongoing post the app's fulfil makes
     * from its own thread, so the former cannot land after the latter (see [showConnectingCall]).
     */
    private val postLock = Any()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var delayedCancelJob: Job? = null

    /** Initializes notification channels. Safe to call repeatedly. */
    fun initialize(context: Context) {
        if (isInitialized) return

        appContext = context.applicationContext

        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createIncomingChannel(notificationManager)
        createOngoingChannel(notificationManager)

        answerOnLockScreenEnabled = readAnswerOnLockScreenConfig()
        if (answerOnLockScreenEnabled) trackLockState()

        isInitialized = true
        CallKitTelecomLog.d(TAG) {
            "Initialized notification channels, answerOnLockScreen: $answerOnLockScreenEnabled"
        }
    }

    /**
     * Creates the incoming call notification channel with the configured ringtone.
     *
     * Android caches channel settings after first creation, so sound changes are ignored on
     * subsequent calls. To handle ringtone config changes between app versions, the channel ID
     * includes a ringtone suffix. When the config changes, a new channel is created and the old one
     * is deleted.
     */
    private fun createIncomingChannel(notificationManager: NotificationManager) {
        val ringtoneConfig = readRingtoneConfig()
        val ringtoneUri = resolveRingtoneUri(ringtoneConfig)
        incomingChannelId = "${CHANNEL_INCOMING_PREFIX}_${ringtoneConfig ?: "default"}"

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousChannelId = prefs.getString(PREF_INCOMING_CHANNEL_ID, null)

        // Delete the old channel if the ringtone config changed
        if (previousChannelId != null && previousChannelId != incomingChannelId) {
            notificationManager.deleteNotificationChannel(previousChannelId)
            CallKitTelecomLog.d(TAG) { "Deleted old incoming channel: $previousChannelId" }
        }

        val ringtoneAttributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

        val channel =
            NotificationChannel(
                    incomingChannelId,
                    "Incoming calls",
                    NotificationManager.IMPORTANCE_HIGH,
                )
                .apply {
                    description = "Notifications for incoming calls"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(ringtoneUri, ringtoneAttributes)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                }

        notificationManager.createNotificationChannel(channel)
        prefs.edit().putString(PREF_INCOMING_CHANNEL_ID, incomingChannelId).apply()

        CallKitTelecomLog.d(TAG) {
            "Created incoming channel: $incomingChannelId, ringtone: $ringtoneConfig"
        }
    }

    private fun createOngoingChannel(notificationManager: NotificationManager) {
        val channel =
            NotificationChannel(
                    CHANNEL_ONGOING,
                    "Ongoing calls",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                .apply {
                    description = "Notifications for active calls"
                    setSound(null, null)
                    enableVibration(false)
                }

        notificationManager.createNotificationChannel(channel)
    }

    /** Reads `ExpoCallKitTelecomDefaultRingtone` from AndroidManifest metadata. */
    private fun readRingtoneConfig(): String? =
        try {
            val appInfo =
                appContext.packageManager.getApplicationInfo(
                    appContext.packageName,
                    PackageManager.GET_META_DATA,
                )
            val value = appInfo.metaData?.getString(KEY_DEFAULT_RINGTONE)
            if (value == "default") null else value
        } catch (_: Throwable) {
            null
        }

    /** Reads `ExpoCallKitTelecomAnswerOnLockScreen` from AndroidManifest metadata. */
    private fun readAnswerOnLockScreenConfig(): Boolean =
        try {
            val appInfo =
                appContext.packageManager.getApplicationInfo(
                    appContext.packageName,
                    PackageManager.GET_META_DATA,
                )
            appInfo.metaData?.getBoolean(KEY_ANSWER_ON_LOCK_SCREEN, false) ?: false
        } catch (_: Throwable) {
            false
        }

    /**
     * Resolves a ringtone config value to a sound URI.
     * - `null` / "default" → system default ringtone
     * - custom filename → `android.resource://` URI for the raw resource
     */
    private fun resolveRingtoneUri(config: String?): Uri {
        if (config == null) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }

        val resId = appContext.resources.getIdentifier(config, "raw", appContext.packageName)
        if (resId == 0) {
            CallKitTelecomLog.e(TAG) {
                "Ringtone raw resource not found: $config, falling back to system default"
            }
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }

        return Uri.parse("android.resource://${appContext.packageName}/$resId")
    }

    /** Shows an incoming call notification with answer/decline actions and full-screen intent. */
    fun showIncomingCall(context: Context, callId: UUID, callerName: String?, hasVideo: Boolean) =
        showIncomingCall(context, callId, callerName, hasVideo, quiet = false)

    /**
     * Posts the ring. [quiet] updates a posted ring in place without alerting again — no restarted
     * ringtone, heads-up or full-screen intent — to swap its Answer action (see [trackLockState]).
     */
    private fun showIncomingCall(
        context: Context,
        callId: UUID,
        callerName: String?,
        hasVideo: Boolean,
        quiet: Boolean,
    ) {
        cancelDelayedCancel()
        val ctx = context.applicationContext
        val displayName = callerName ?: ctx.getString(R.string.expo_callkit_telecom_unknown_caller)
        val answersLocked = answersOnLockScreen(ctx)

        val answerPI =
            if (answersLocked) {
                // SystemUI dismisses the keyguard (asks for the PIN) before it sends an action
                // button's activity PendingIntent — even one for a show-when-locked activity
                // (StatusBarRemoteInputCallback.handleRemoteViewClick) — so on the lock screen
                // Answer is a broadcast: CallNotificationReceiver answers and shows the call on
                // the lock-screen call screen (IncomingCallActivity).
                PendingIntent.getBroadcast(
                    ctx,
                    callId.hashCode() + 5,
                    Intent(ctx, CallNotificationReceiver::class.java).apply {
                        action = CallNotificationReceiver.ACTION_ANSWER
                        putExtra(CallNotificationReceiver.EXTRA_CALL_ID, callId.toString())
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                // Answer action uses PendingIntent.getActivity() to bring the app to
                // foreground. On Android 12+, BroadcastReceivers cannot start activities
                // from the background, so the answer action must launch the Activity
                // directly. CallNotificationReceiver intercepts the intent via
                // onNewIntent to trigger the actual call answer.
                val answerIntent =
                    ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        action = CallNotificationReceiver.ACTION_ANSWER
                        putExtra(CallNotificationReceiver.EXTRA_CALL_ID, callId.toString())
                    } ?: Intent()
                PendingIntent.getActivity(
                    ctx,
                    callId.hashCode(),
                    answerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        val declineIntent =
            Intent(ctx, CallNotificationReceiver::class.java).apply {
                action = CallNotificationReceiver.ACTION_DECLINE
                putExtra(CallNotificationReceiver.EXTRA_CALL_ID, callId.toString())
            }
        val declinePI =
            PendingIntent.getBroadcast(
                ctx,
                callId.hashCode() + 1,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val fullScreenIntent = buildIncomingCallFullScreenIntent(ctx, callId)

        val notification =
            buildBase(
                    ctx,
                    incomingChannelId,
                    displayName,
                    ctx.getString(
                        if (hasVideo) R.string.expo_callkit_telecom_incoming_video_call
                        else R.string.expo_callkit_telecom_incoming_call,
                    ),
                )
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        buildPerson(displayName),
                        declinePI,
                        answerPI,
                    )
                )
                .setFullScreenIntent(fullScreenIntent, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(quiet)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .build()

        postNotification(ctx, callId, displayName, "incoming call")
        ring = Ring(callId, callerName, hasVideo, answersLocked)
        notify(ctx, notification)
    }

    /** Shows a dialing notification for outgoing calls with a hangup action. */
    fun showDialingCall(context: Context, callId: UUID, callerName: String?) {
        cancelDelayedCancel()
        ring = null
        val ctx = context.applicationContext
        val displayName = callerName ?: "Unknown"

        val hangupIntent =
            Intent(ctx, CallNotificationReceiver::class.java).apply {
                action = CallNotificationReceiver.ACTION_DECLINE
                putExtra(CallNotificationReceiver.EXTRA_CALL_ID, callId.toString())
            }
        val hangupPI =
            PendingIntent.getBroadcast(
                ctx,
                callId.hashCode() + 2,
                hangupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val contentIntent = buildFullScreenIntent(ctx, callId)

        val notification =
            buildBase(ctx, CHANNEL_ONGOING, displayName, "Dialing...")
                .setStyle(
                    NotificationCompat.CallStyle.forOngoingCall(buildPerson(displayName), hangupPI)
                )
                .setFullScreenIntent(contentIntent, true)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false)
                .build()

        postNotification(ctx, callId, displayName, "dialing call")
        notify(ctx, notification)
    }

    /**
     * Stands in for the ring notification between a lock-screen answer and the app's fulfil: the
     * answer takes the ring down, and the ongoing notification only comes with [showOngoingCall],
     * so without this the shade is empty while the call connects — with no way back to a call
     * screen that stepped aside (or never started, see [CallNotificationReceiver]) and no hang-up.
     * Ongoing style with the hang-up action and no timer; the tap opens the lock-screen call
     * screen, or the app when [onLockScreen] is false. Posted only while the session is CONNECTING:
     * an answer that was ignored (the call was already answered or had ended) must not replace the
     * ongoing or ended notification.
     */
    fun showConnectingCall(
        context: Context,
        callId: UUID,
        callerName: String?,
        onLockScreen: Boolean,
    ) {
        if (CallStore.session(callId)?.status != CallSessionStatus.CONNECTING) return
        cancelDelayedCancel()
        ring = null
        val ctx = context.applicationContext
        val displayName = callerName ?: ctx.getString(R.string.expo_callkit_telecom_unknown_caller)

        val hangupIntent =
            Intent(ctx, CallNotificationReceiver::class.java).apply {
                action = CallNotificationReceiver.ACTION_DECLINE
                putExtra(CallNotificationReceiver.EXTRA_CALL_ID, callId.toString())
            }
        val hangupPI =
            PendingIntent.getBroadcast(
                ctx,
                callId.hashCode() + 2,
                hangupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val contentIntent =
            if (onLockScreen) buildLockedCallIntent(ctx, callId)
            else buildFullScreenIntent(ctx, callId)

        val notification =
            buildBase(
                    ctx,
                    CHANNEL_ONGOING,
                    displayName,
                    ctx.getString(R.string.expo_callkit_telecom_connecting),
                )
                .setStyle(
                    NotificationCompat.CallStyle.forOngoingCall(buildPerson(displayName), hangupPI),
                )
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false)
                .build()

        postNotification(ctx, callId, displayName, "connecting call")
        // The app's fulfil moves the session to CONNECTED and then posts the ongoing notification
        // (showOngoingCall) from its own thread. Checked again under the lock that post takes, so
        // this one cannot slip in after it and sit over it for the rest of the call.
        synchronized(postLock) {
            if (CallStore.session(callId)?.status != CallSessionStatus.CONNECTING) return
            notify(ctx, notification)
        }
    }

    /** Switches the notification to ongoing call style with a call duration timer. */
    fun showOngoingCall(
        context: Context,
        callId: UUID,
        callerName: String?,
        connectedAtMs: Long = System.currentTimeMillis(),
    ) {
        cancelDelayedCancel()
        ring = null
        val ctx = context.applicationContext
        val displayName = callerName ?: "Unknown"

        val hangupIntent =
            Intent(ctx, CallNotificationReceiver::class.java).apply {
                action = CallNotificationReceiver.ACTION_DECLINE
                putExtra(CallNotificationReceiver.EXTRA_CALL_ID, callId.toString())
            }
        val hangupPI =
            PendingIntent.getBroadcast(
                ctx,
                callId.hashCode() + 2,
                hangupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        // A call answered on the lock screen opens its call screen: a notification tap (unlike an
        // action button) shows a show-when-locked activity without unlocking.
        val contentIntent =
            if (LockedCallKeepAlive.isHeld(callId)) buildLockedCallIntent(ctx, callId)
            else buildFullScreenIntent(ctx, callId)

        val notification =
            buildBase(ctx, CHANNEL_ONGOING, displayName, "Ongoing call")
                .setStyle(
                    NotificationCompat.CallStyle.forOngoingCall(buildPerson(displayName), hangupPI)
                )
                .setFullScreenIntent(contentIntent, true)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setUsesChronometer(true)
                .setWhen(connectedAtMs)
                .build()

        postNotification(ctx, callId, displayName, "ongoing call")
        // Under the lock so a "Connecting…" post racing from the main thread cannot follow it.
        synchronized(postLock) { notify(ctx, notification) }
    }

    /** Shows a brief "Call Ended" notification that auto-cancels after ~2 seconds. */
    fun showEndedCall(context: Context, callId: UUID, callerName: String?) {
        cancelDelayedCancel()
        ring = null
        val ctx = context.applicationContext
        val displayName = callerName ?: "Unknown"

        val notification =
            buildBase(ctx, CHANNEL_ONGOING, displayName, "Call ended")
                .setOngoing(false)
                .setAutoCancel(true)
                .build()

        postNotification(ctx, callId, displayName, "ended call")
        // Under the lock like the ongoing post, so a "Connecting…" post cannot follow it.
        synchronized(postLock) { notify(ctx, notification) }

        delayedCancelJob =
            scope.launch {
                delay(ENDED_CANCEL_DELAY_MS)
                cancel(ctx)
            }
    }

    /** Cancels any active call notification. */
    fun cancel(context: Context) {
        cancelDelayedCancel()
        ring = null
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        CallKitTelecomLog.d(TAG) { "Cancelled call notification" }
    }

    /** Cancels any pending delayed-cancel job without cancelling the notification. */
    private fun cancelDelayedCancel() {
        delayedCancelJob?.cancel()
        delayedCancelJob = null
    }

    /**
     * Whether a ring's Answer is taken on the lock screen: the opt-in is on, the app's JS is
     * running to drive the call (see [LockedCallKeepAlive.canKeepAlive]), and the device is locked
     * (PIN, pattern or password), or its screen is off on a secured device, when the keyguard
     * (possibly after its lock delay) is what the user will see first.
     */
    private fun answersOnLockScreen(ctx: Context): Boolean {
        if (!answerOnLockScreenEnabled || !LockedCallKeepAlive.canKeepAlive(ctx)) return false
        val keyguard = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val power = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        // isDeviceLocked: a swipe-only keyguard dismisses without credentials, like unlocked.
        return keyguard.isDeviceLocked || (!power.isInteractive && keyguard.isDeviceSecure)
    }

    /**
     * Keeps a ringing call's Answer action matched to the lock state: the ring may be posted
     * unlocked and answered after the phone locked (or the other way round), and each state needs
     * its own action. The screen turning off or on and the user unlocking are the moments the lock
     * state changes; a mismatched ring is then updated in place, quietly.
     */
    private fun trackLockState() {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val current = ring ?: return
                    if (current.answersLocked == answersOnLockScreen(appContext)) return
                    val status = CallStore.session(current.callId)?.status
                    if (status != CallSessionStatus.RINGING) return
                    showIncomingCall(
                        appContext,
                        current.callId,
                        current.callerName,
                        current.hasVideo,
                        quiet = true,
                    )
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        // System broadcasts still reach a receiver that is not exported.
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Creates a Person with an icon for use in CallStyle notifications. */
    private fun buildPerson(displayName: String): Person =
        Person.Builder()
            .setName(displayName)
            .setIcon(IconCompat.createWithResource(appContext, android.R.drawable.ic_menu_call))
            .setImportant(true)
            .build()

    /** Creates a base notification builder with shared configuration. */
    private fun buildBase(
        ctx: Context,
        channelId: String,
        title: String,
        text: String,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(getAppIconRes(ctx))
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    /** Posts notification, logging success or permission errors. */
    private fun postNotification(ctx: Context, callId: UUID, displayName: String, label: String) {
        CallKitTelecomLog.d(TAG) {
            "Showing $label notification - callId: $callId, caller: $displayName"
        }
    }

    /** Notifies via NotificationManagerCompat, catching posting failures. */
    private fun notify(ctx: Context, notification: Notification) {
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            CallKitTelecomLog.e(TAG) { "Failed to post notification: ${e.message}" }
        }
    }

    /** Builds a full-screen intent targeting IncomingCallActivity for lock screen display. */
    private fun buildIncomingCallFullScreenIntent(context: Context, callId: UUID): PendingIntent {
        val intent =
            Intent(context, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId.toString())
            }

        return PendingIntent.getActivity(
            context,
            callId.hashCode() + 4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Opens the lock-screen call screen of [callId], a call answered on the lock screen. */
    private fun buildLockedCallIntent(context: Context, callId: UUID): PendingIntent =
        PendingIntent.getActivity(
            context,
            callId.hashCode() + 6,
            IncomingCallActivity.answeredOnLockScreenIntent(context, callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Builds a full-screen intent that launches the app's main activity. */
    private fun buildFullScreenIntent(context: Context, callId: UUID): PendingIntent {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(CallNotificationReceiver.EXTRA_CALL_ID, callId.toString())
            } ?: Intent()

        return PendingIntent.getActivity(
            context,
            callId.hashCode() + 3,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Resolves the app's icon resource for the notification small icon. */
    private fun getAppIconRes(context: Context): Int =
        try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            appInfo.icon.takeIf { it != 0 } ?: android.R.drawable.sym_def_app_icon
        } catch (_: PackageManager.NameNotFoundException) {
            android.R.drawable.sym_def_app_icon
        }
}
