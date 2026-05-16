package expo.modules.callkittelecom.managers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import expo.modules.callkittelecom.IncomingCallActivity
import expo.modules.callkittelecom.services.CallNotificationReceiver
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
    private const val PREFS_NAME = "expo_callkit_telecom_notifications"
    private const val PREF_INCOMING_CHANNEL_ID = "incoming_channel_id"

    private var isInitialized = false
    private lateinit var appContext: Context

    /** The active incoming channel ID, derived from the configured ringtone. */
    private lateinit var incomingChannelId: String

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

        isInitialized = true
        CallKitTelecomLog.d(TAG) { "Initialized notification channels" }
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
    fun showIncomingCall(context: Context, callId: UUID, callerName: String?, hasVideo: Boolean) {
        cancelDelayedCancel()
        val ctx = context.applicationContext
        val displayName = callerName ?: "Unknown"

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
        val answerPI =
            PendingIntent.getActivity(
                ctx,
                callId.hashCode(),
                answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

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
                    if (hasVideo) "Incoming video call" else "Incoming call",
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
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .build()

        postNotification(ctx, callId, displayName, "incoming call")
        notify(ctx, notification)
    }

    /** Shows a dialing notification for outgoing calls with a hangup action. */
    fun showDialingCall(context: Context, callId: UUID, callerName: String?) {
        cancelDelayedCancel()
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

    /** Switches the notification to ongoing call style with a call duration timer. */
    fun showOngoingCall(
        context: Context,
        callId: UUID,
        callerName: String?,
        connectedAtMs: Long = System.currentTimeMillis(),
    ) {
        cancelDelayedCancel()
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
        notify(ctx, notification)
    }

    /** Shows a brief "Call Ended" notification that auto-cancels after ~2 seconds. */
    fun showEndedCall(context: Context, callId: UUID, callerName: String?) {
        cancelDelayedCancel()
        val ctx = context.applicationContext
        val displayName = callerName ?: "Unknown"

        val notification =
            buildBase(ctx, CHANNEL_ONGOING, displayName, "Call ended")
                .setOngoing(false)
                .setAutoCancel(true)
                .build()

        postNotification(ctx, callId, displayName, "ended call")
        notify(ctx, notification)

        delayedCancelJob =
            scope.launch {
                delay(ENDED_CANCEL_DELAY_MS)
                cancel(ctx)
            }
    }

    /** Cancels any active call notification. */
    fun cancel(context: Context) {
        cancelDelayedCancel()
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        CallKitTelecomLog.d(TAG) { "Cancelled call notification" }
    }

    /** Cancels any pending delayed-cancel job without cancelling the notification. */
    private fun cancelDelayedCancel() {
        delayedCancelJob?.cancel()
        delayedCancelJob = null
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
