package expo.modules.callkittelecom

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Chronometer
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.ToggleButton
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.telecom.CallEndpointCompat
import androidx.core.view.ViewCompat
import expo.modules.callkittelecom.managers.CallAudioManager
import expo.modules.callkittelecom.managers.CallManager
import expo.modules.callkittelecom.managers.CallNotificationManager
import expo.modules.callkittelecom.models.CallParticipant
import expo.modules.callkittelecom.models.CallSession
import expo.modules.callkittelecom.models.CallSessionStatus
import expo.modules.callkittelecom.store.CallStore
import expo.modules.callkittelecom.utils.CallKitTelecomLog
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native full-screen incoming call Activity displayed over the lock screen.
 *
 * Shows caller information with answer/decline buttons. Automatically dismisses when the call
 * leaves the RINGING state (answered, declined, timed out, or ended elsewhere).
 *
 * Answer flow: answers the call directly, dismisses the keyguard via
 * [KeyguardManager.requestDismissKeyguard], then launches the main Activity so the user sees the
 * in-call UI after unlocking.
 *
 * Lock-screen answer flow (`androidAnswerOnLockScreen`): when the device is locked at answer and
 * the app's JS is running ([staysOnLockScreen]), the screen stays up as the call screen instead —
 * no unlock, so no PIN, and nothing of the app over the lock screen. It swaps the answer/decline
 * row for mute, speaker, open app and hang up, shows "Connecting…" and then the call's duration,
 * holds a proximity wake lock so a cheek cannot press buttons, and [LockedCallKeepAlive] keeps JS
 * running until the call ends; the screen closes when the call ends. Only open app asks for the
 * PIN. The same applies to a call answered from the notification on the lock screen
 * ([answeredOnLockScreenIntent]).
 */
class IncomingCallActivity : Activity() {
    companion object {
        private const val TAG = "ExpoCallKitTelecom.IncomingCallActivity"
        const val EXTRA_CALL_ID = "expo.modules.callkittelecom.EXTRA_CALL_ID"

        /** Marks a call already answered on the lock screen (see [answeredOnLockScreenIntent]). */
        private const val EXTRA_ANSWERED_ON_LOCK_SCREEN =
            "expo.modules.callkittelecom.EXTRA_ANSWERED_ON_LOCK_SCREEN"

        private const val WAKE_LOCK_TAG = "expo-callkit-telecom:lock-screen-call"

        /**
         * How long an open-app tap made before this window had focus waits for it. Focus comes
         * within about a second of the screen coming back on top; later, the tap is stale.
         */
        private const val OPEN_APP_FOCUS_WAIT_MS = 3_000L

        /**
         * Shows [callId], already answered, as the call screen over the lock screen — for a call
         * answered from the notification, and the ongoing notification of such a call. Opened after
         * the phone is unlocked, it forwards to the app instead.
         */
        fun answeredOnLockScreenIntent(context: Context, callId: UUID): Intent =
            Intent(context, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(EXTRA_CALL_ID, callId.toString())
                putExtra(EXTRA_ANSWERED_ON_LOCK_SCREEN, true)
            }
    }

    private var callId: UUID? = null
    private var isAnswering = false

    /** Whether this screen is the call screen of a call answered on the lock screen. */
    private var isLockedCall = false
    private var timerStarted = false

    /** Between onResume and onPause; the proximity lock is only held while this is true. */
    private var resumed = false

    /**
     * When open app was tapped before this window had focus ([SystemClock.uptimeMillis]); the
     * unlock is asked for once it has, within [OPEN_APP_FOCUS_WAIT_MS].
     */
    private var openAppPendingSince: Long? = null
    private var proximityLock: PowerManager.WakeLock? = null

    /** Set once WAKE_LOCK turned out to be missing; the lock is then never attempted again. */
    private var proximityLockUnavailable = false

    /** The in-call back handler, an [OnBackInvokedCallback] (API 33+); untyped for older APIs. */
    private var backCallback: Any? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var subtitle: TextView
    private lateinit var timer: Chronometer
    private lateinit var muteButton: ImageButton
    private lateinit var muteLabel: TextView
    private lateinit var speakerButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureWindowForLockScreen()

        val id = parseCallId() ?: return
        callId = id

        val session = CallStore.session(id)
        val answeredOnLockScreen = intent.getBooleanExtra(EXTRA_ANSWERED_ON_LOCK_SCREEN, false)
        val showable =
            session != null &&
                (session.status == CallSessionStatus.RINGING ||
                    (answeredOnLockScreen && session.status.isAnswered()))
        if (!showable) {
            CallKitTelecomLog.d(TAG) { "No ringing session for $id, finishing" }
            finish()
            return
        }
        if (answeredOnLockScreen && !isKeyguardLocked()) {
            // The ongoing notification of a lock-screen call, tapped after unlocking.
            launchMainActivity()
            finish()
            return
        }

        bindContent(id, session)
        loadAvatar(session.remoteParticipants.firstOrNull()?.avatarUrl)
        observeSessionChanges(id)
        observeSpeaker()

        if (answeredOnLockScreen) {
            isAnswering = true
            enterLockedCall(id)
        }

        CallKitTelecomLog.d(TAG) { "Showing incoming call UI - callId: $id" }
    }

    /**
     * A call answered on the lock screen while this screen already shows it (singleInstance): it
     * becomes the call screen — or, unlocked, the app opens. Other intents — another call's ring —
     * leave it as it is.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val id = callId ?: return
        if (!intent.getBooleanExtra(EXTRA_ANSWERED_ON_LOCK_SCREEN, false)) return
        if (intent.getStringExtra(EXTRA_CALL_ID) != id.toString()) return
        if (!isKeyguardLocked()) {
            leaveForApp()
            return
        }
        isAnswering = true
        enterLockedCall(id)
    }

    @Suppress("DEPRECATION")
    private fun configureWindowForLockScreen() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }

    private fun parseCallId(): UUID? {
        val callIdStr = intent.getStringExtra(EXTRA_CALL_ID)
        return try {
            UUID.fromString(callIdStr)
        } catch (_: Exception) {
            CallKitTelecomLog.e(TAG) { "Invalid or missing call ID: $callIdStr" }
            finish()
            null
        }
    }

    /** Inflates the layout and binds every view to [session]. */
    private fun bindContent(id: UUID, session: CallSession) {
        setContentView(R.layout.activity_incoming_call)
        subtitle = findViewById(R.id.expo_callkit_telecom_subtitle)
        timer = findViewById(R.id.expo_callkit_telecom_timer)
        muteButton = findViewById(R.id.expo_callkit_telecom_mute_button)
        muteLabel = findViewById(R.id.expo_callkit_telecom_mute_label)
        speakerButton = findViewById(R.id.expo_callkit_telecom_speaker_button)

        val caller = session.remoteParticipants.firstOrNull()
        bindAppBranding()
        bindCallerInfo(caller, session.options.hasVideo)
        bindButtons(id, session.options.hasVideo)
    }

    /** Header icon and name, and the open-app button, which carries the app's name. */
    private fun bindAppBranding() {
        val appName =
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                findViewById<ImageView>(R.id.expo_callkit_telecom_app_icon)
                    .setImageDrawable(packageManager.getApplicationIcon(appInfo))
                packageManager.getApplicationLabel(appInfo)
            } catch (_: Exception) {
                // Non-critical — branding just won't show
                null
            }
        findViewById<TextView>(R.id.expo_callkit_telecom_app_name).text = appName
        findViewById<TextView>(R.id.expo_callkit_telecom_open_app_label).text = appName
        findViewById<ImageButton>(R.id.expo_callkit_telecom_open_app_button).contentDescription =
            appName
    }

    /**
     * Binds the caller's name and avatar fallback. The avatar shows the display name's initial, or
     * a placeholder when there's no display name, since a phone number or email has no initial.
     */
    private fun bindCallerInfo(caller: CallParticipant?, hasVideo: Boolean) {
        val initial = caller?.displayName?.trim()?.firstOrNull()?.uppercase()
        val avatarText = findViewById<TextView>(R.id.expo_callkit_telecom_avatar_text)
        if (initial != null) {
            avatarText.text = initial
        } else {
            avatarText.visibility = View.GONE
            findViewById<ImageView>(R.id.expo_callkit_telecom_avatar_placeholder).visibility =
                View.VISIBLE
        }

        findViewById<TextView>(R.id.expo_callkit_telecom_caller_name).text =
            caller?.displayNameOrHandle ?: getString(R.string.expo_callkit_telecom_unknown_caller)

        subtitle.text =
            getString(
                if (hasVideo) R.string.expo_callkit_telecom_incoming_video_call
                else R.string.expo_callkit_telecom_incoming_call,
            )
    }

    /**
     * Loads the caller's avatar from [avatarUrl] on a background thread. On success, displays a
     * circular-cropped image and hides the initial or placeholder. On failure, silently keeps that
     * fallback.
     */
    private fun loadAvatar(avatarUrl: String?) {
        if (avatarUrl.isNullOrBlank()) return

        scope.launch {
            val drawable =
                withContext(Dispatchers.IO) {
                    try {
                        val connection = URL(avatarUrl).openConnection() as HttpURLConnection
                        connection.connectTimeout = 5_000
                        connection.readTimeout = 5_000
                        val bitmap =
                            connection.inputStream.use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        bitmap?.let {
                            RoundedBitmapDrawableFactory.create(resources, it).apply {
                                isCircular = true
                            }
                        }
                    } catch (e: Exception) {
                        CallKitTelecomLog.d(TAG) { "Avatar load failed: ${e.message}" }
                        null
                    }
                }

            if (drawable != null) {
                showAvatar(drawable)
            }
        }
    }

    /** Shows [drawable] as the avatar in the views currently inflated. */
    private fun showAvatar(drawable: Drawable) {
        findViewById<ImageView>(R.id.expo_callkit_telecom_avatar_image).apply {
            setImageDrawable(drawable)
            visibility = View.VISIBLE
        }
        findViewById<View>(R.id.expo_callkit_telecom_avatar_text).visibility = View.GONE
        findViewById<View>(R.id.expo_callkit_telecom_avatar_placeholder).visibility = View.GONE
    }

    private fun bindButtons(id: UUID, hasVideo: Boolean) {
        val answerButton = findViewById<ImageButton>(R.id.expo_callkit_telecom_answer_button)
        if (hasVideo) {
            answerButton.setImageResource(R.drawable.expo_callkit_telecom_ic_videocam)
        }
        answerButton.setOnClickListener { onAnswerTapped(id) }

        findViewById<ImageButton>(R.id.expo_callkit_telecom_decline_button).setOnClickListener {
            onDeclineTapped(id)
        }

        muteButton.setOnClickListener {
            val muted = CallStore.session(id)?.isMuted ?: false
            CallManager.shared.setMuted(id, !muted)
        }
        bindMuted(CallStore.session(id)?.isMuted ?: false)

        // TalkBack announces the speaker toggle as one, on or off, in the system's language.
        speakerButton.accessibilityDelegate =
            object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = ToggleButton::class.java.name
                    info.isCheckable = true
                    // Its replacement, the tri-state setChecked(Int), is API 36+.
                    @Suppress("DEPRECATION")
                    info.isChecked = host.isActivated
                }
            }
        // The same route override as the JS setAudioSessionPortOverride. The button's state
        // follows the endpoint Core-Telecom then reports (observeSpeaker), and JS sees the change
        // as onAudioRouteChanged, like any other route change.
        speakerButton.setOnClickListener {
            CallAudioManager.setAudioSessionPortOverride(!speakerButton.isActivated)
        }
        bindSpeaker(CallAudioManager.activeEndpoint.value)

        findViewById<ImageButton>(R.id.expo_callkit_telecom_open_app_button).setOnClickListener {
            onOpenAppTapped()
        }

        findViewById<ImageButton>(R.id.expo_callkit_telecom_hangup_button).setOnClickListener {
            onDeclineTapped(id)
        }
    }

    /** Mute toggle: the well is red while muted, and its label says what a tap does. */
    private fun bindMuted(muted: Boolean) {
        muteButton.isActivated = muted
        val text =
            getString(
                if (muted) R.string.expo_callkit_telecom_unmute
                else R.string.expo_callkit_telecom_mute,
            )
        muteButton.contentDescription = text
        muteLabel.text = text
    }

    /**
     * Speaker toggle state, and the proximity lock with it. The state description is what TalkBack
     * announces when the route changes after a tap; setActivated alone announces nothing.
     */
    private fun bindSpeaker(endpoint: CallEndpointCompat?) {
        val speakerOn = endpoint?.type == CallEndpointCompat.TYPE_SPEAKER
        speakerButton.isActivated = speakerOn
        ViewCompat.setStateDescription(
            speakerButton,
            getString(
                if (speakerOn) R.string.expo_callkit_telecom_speaker_on
                else R.string.expo_callkit_telecom_speaker_off,
            ),
        )
        updateProximityLock()
    }

    /**
     * Holds the proximity lock only while this screen is resumed and the call is heard at the ear:
     * no route yet or the earpiece, and not a video call. On the loudspeaker, a headset or in a
     * video call the user looks at the screen, so a hand near the sensor must not blank it (the
     * platform dialer applies the same rule). Resumed, because the session and endpoint collectors
     * keep calling this after Back or Home released the lock, and a stopped screen must not blank
     * whatever the user went on to. A no-op until the screen is the call screen
     * ([acquireProximityLock]).
     */
    private fun updateProximityLock() {
        val endpoint = CallAudioManager.activeEndpoint.value
        val atEar = endpoint == null || endpoint.type == CallEndpointCompat.TYPE_EARPIECE
        val video = callId?.let { CallStore.session(it)?.options?.hasVideo } ?: false
        if (resumed && atEar && !video) acquireProximityLock() else releaseProximityLock()
    }

    /** Speaker toggle follows the call's Core-Telecom endpoint, whoever changed it. */
    private fun observeSpeaker() {
        scope.launch { CallAudioManager.activeEndpoint.collect { bindSpeaker(it) } }
    }

    /**
     * Answers the call directly, then dismisses the keyguard and launches the main Activity so the
     * user transitions into the in-call UI — or, locked with the app's JS running, stays as the
     * call screen ([enterLockedCall]).
     *
     * The call is answered immediately (media connection starts) regardless of whether the keyguard
     * dismissal succeeds. This matches the behavior of iOS CallKit where audio connects before the
     * device is unlocked.
     */
    private fun onAnswerTapped(id: UUID) {
        if (isAnswering) return
        isAnswering = true
        CallKitTelecomLog.d(TAG) { "Answer tapped - callId: $id" }

        // Locked, with the app's JS running: stay on this screen as the call screen (unlocking
        // would ask for the PIN). Decided, and the call held, before the answer: answerCall emits
        // onCallAnswered synchronously, and an app that fulfils at once posts the ongoing
        // notification from its own thread while this method is still running, so the hold must
        // already be visible to showOngoingCall for that notification's tap to return here
        // rather than open the app. The receiver's locked branch orders it the same way.
        val staysHere = staysOnLockScreen()
        if (staysHere) LockedCallKeepAlive.hold(this, id)

        // Answer immediately — don't wait for keyguard dismissal
        CallManager.shared.answerCall(id)

        if (staysHere) {
            enterLockedCall(id)
            return
        }

        // Dismiss keyguard, then bring the main app to the foreground
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    launchMainActivity()
                    finish()
                }

                override fun onDismissCancelled() {
                    // User cancelled unlock — call is still answered, launch anyway
                    launchMainActivity()
                    finish()
                }

                override fun onDismissError() {
                    launchMainActivity()
                    finish()
                }
            },
        )
    }

    /**
     * Turns this screen into the call screen of [id], answered on the lock screen, and keeps JS
     * running for it. The call engine lives in the app's JS, which is running (see
     * [staysOnLockScreen]): `onCallAnswered` reaches it as usual, it connects its media and
     * fulfils, and the session goes CONNECTED under this screen.
     */
    private fun enterLockedCall(id: UUID) {
        if (isLockedCall) return
        isLockedCall = true
        CallKitTelecomLog.d(TAG) { "Staying on the lock screen as the call screen - callId: $id" }

        // Held first (a no-op where the caller already held it), so every notification posted from
        // here on — below, or by the app's fulfil on its own thread — targets this screen.
        LockedCallKeepAlive.hold(this, id)

        // Ringing kept the screen on; in the call the screen may time out (the call goes on).
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        CallStore.session(id)?.let { session ->
            bindInCallState(session)
            val callerName = session.remoteParticipants.firstOrNull()?.displayNameOrHandle
            if (session.status == CallSessionStatus.CONNECTED) {
                // Already connected: opened from the ongoing notification (onCreate with the
                // answered extra), or adopted after a prompt fulfil. The ongoing notification
                // may have gone out with the app as its tap target; post it again so its tap
                // returns here.
                CallNotificationManager.showOngoingCall(
                    this,
                    id,
                    callerName,
                    session.connectedAt?.toEpochMilli() ?: System.currentTimeMillis(),
                )
            } else {
                // The answer took the ring notification down and the ongoing one only comes at
                // fulfil: keep one up in between, so Back leaves a way back to this screen and
                // a hang-up in the shade. Already posted by the receiver on its path; a repeat
                // is a silent in-place update.
                CallNotificationManager.showConnectingCall(
                    this,
                    id,
                    callerName,
                    onLockScreen = true,
                )
            }
        }
        registerInCallBack()
        updateProximityLock()
    }

    /** Swaps the ringing actions for the in-call ones and binds the call's state. */
    private fun bindInCallState(session: CallSession) {
        findViewById<View>(R.id.expo_callkit_telecom_ringing_actions).visibility = View.GONE
        findViewById<View>(R.id.expo_callkit_telecom_incall_actions).visibility = View.VISIBLE
        timerStarted = false
        bindCallState(session)
    }

    /**
     * Shows the answered call's state: "Connecting…" until the app fulfils the answer, which is
     * when its media is connected (`connectedAt`), then the call's kind and a timer running from
     * that moment. It is on the session, so a screen opened later (the ongoing notification) times
     * the call at once. Mute follows the session too.
     */
    private fun bindCallState(session: CallSession) {
        bindMuted(session.isMuted)
        // A video call (or one that became one) keeps the screen on.
        updateProximityLock()
        val connectedAt =
            session.connectedAt?.takeIf { session.status == CallSessionStatus.CONNECTED }
        // Before the timer's guard: the call's kind can change mid-call (reportVideo).
        subtitle.text =
            getString(
                when {
                    connectedAt == null -> R.string.expo_callkit_telecom_connecting
                    session.options.hasVideo -> R.string.expo_callkit_telecom_video_call
                    else -> R.string.expo_callkit_telecom_voice_call
                },
            )
        if (connectedAt == null || timerStarted) return
        timerStarted = true
        val connectedAgoMs =
            (System.currentTimeMillis() - connectedAt.toEpochMilli()).coerceAtLeast(0L)
        timer.base = SystemClock.elapsedRealtime() - connectedAgoMs
        timer.visibility = View.VISIBLE
        timer.start()
    }

    private fun CallSessionStatus.isAnswered(): Boolean =
        this == CallSessionStatus.CONNECTING || this == CallSessionStatus.CONNECTED

    /**
     * Locked behind a PIN, pattern or password. A swipe-only keyguard doesn't count: it dismisses
     * without credentials, so those calls go to the app as before.
     */
    private fun isKeyguardLocked(): Boolean =
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked

    /**
     * Whether a call answered now stays on this screen: the `androidAnswerOnLockScreen` opt-in is
     * on, the device is locked, and the app's JS is running to drive the call. Otherwise the
     * default flow applies (unlock, open the app) — on a cold start too, as nothing here starts
     * React.
     */
    private fun staysOnLockScreen(): Boolean =
        CallNotificationManager.answerOnLockScreenEnabled &&
            isKeyguardLocked() &&
            LockedCallKeepAlive.canKeepAlive(this)

    private fun launchMainActivity() {
        val intent =
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        if (intent != null) {
            startActivity(intent)
        }
    }

    private fun onDeclineTapped(id: UUID) {
        CallKitTelecomLog.d(TAG) { "Decline tapped - callId: $id" }
        CallManager.shared.endCall(id)
        finish()
    }

    /**
     * Open app, in the call: asks to unlock (the PIN) and opens the app once unlocked. Cancelled,
     * the call screen stays — nothing of the app shows over the lock screen.
     *
     * Asked only while this window has focus: tapped while the screen is still coming back on top
     * (some OEM lock screens hand focus over late), the bouncer would open behind it, unseen, and
     * take every tap. Such a tap asks once focus comes ([onWindowFocusChanged]), if that is soon.
     */
    private fun onOpenAppTapped() {
        if (!isKeyguardLocked()) {
            leaveForApp()
            return
        }
        if (!hasWindowFocus()) {
            CallKitTelecomLog.d(TAG) { "Open app tapped before focus, unlocking once focused" }
            openAppPendingSince = SystemClock.uptimeMillis()
            return
        }
        openAppPendingSince = null
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                // Unlocked by the user, though the keyguard may still read as locked while it
                // goes away — no second check.
                override fun onDismissSucceeded() = handOverToApp()

                override fun onDismissCancelled() {
                    CallKitTelecomLog.d(TAG) { "Unlock cancelled, staying on the call screen" }
                }

                override fun onDismissError() {
                    CallKitTelecomLog.e(TAG) { "Unlock failed, staying on the call screen" }
                }
            },
        )
    }

    /** Hands the call over to the app when unlocked ([handOverToApp]); locked, does nothing. */
    private fun leaveForApp() {
        if (isKeyguardLocked()) return
        handOverToApp()
    }

    /**
     * Hands the call over to the app: the app shows its own call screen. The app never shows over
     * the lock screen, so this is safe even while the keyguard is still going away.
     */
    private fun handOverToApp() {
        releaseProximityLock()
        launchMainActivity()
        finish()
    }

    /** Back, in the call: steps aside behind the lock screen; the ongoing notification returns. */
    private fun stepAside() {
        openAppPendingSince = null
        releaseProximityLock()
        moveTaskToBack(true)
    }

    private fun registerInCallBack() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backCallback != null) return
        val callback = OnBackInvokedCallback { stepAside() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback,
        )
        backCallback = callback
    }

    /** Back without [OnBackInvokedCallback] (API 31–32, or legacy back handling). */
    @Deprecated("Superseded by OnBackInvokedCallback on API 33+")
    override fun onBackPressed() {
        if (isLockedCall) {
            stepAside()
            return
        }
        @Suppress("DEPRECATION") super.onBackPressed()
    }

    /**
     * Holds a proximity wake lock for the call on this screen: the screen goes off at the ear, so a
     * cheek cannot press its buttons. The call itself does not depend on the screen. Held without a
     * timeout on purpose: [releaseProximityLock] runs on pause, on a route change and when the call
     * ends, and a timeout would light the screen at the ear mid-call.
     */
    @SuppressLint("WakelockTimeout")
    private fun acquireProximityLock() {
        if (!isLockedCall || proximityLockUnavailable || proximityLock?.isHeld == true) return
        // The config plugin adds WAKE_LOCK with the opt-in; an app that set the meta-data by
        // hand may lack it, and acquire() would throw. The call goes on without the lock.
        val granted =
            checkSelfPermission(Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            proximityLockUnavailable = true
            CallKitTelecomLog.w(TAG) { "WAKE_LOCK not granted, skipping the proximity lock" }
            return
        }
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        val lock =
            proximityLock
                ?: power
                    .newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, WAKE_LOCK_TAG)
                    .also {
                        it.setReferenceCounted(false)
                        proximityLock = it
                    }
        lock.acquire()
    }

    private fun releaseProximityLock() {
        val lock = proximityLock ?: return
        // Leaves the screen off until the phone is away from the ear.
        if (lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
    }

    /**
     * Observes session updates to auto-dismiss when the call is no longer ringing (answered
     * elsewhere, timed out, or ended by the remote side).
     *
     * When [isAnswering] is true (user tapped answer locally), only auto-dismiss for ENDED status —
     * the CONNECTING transition is expected and handled by the keyguard dismissal flow. In the
     * lock-screen call the screen follows the session instead ([bindCallState]), and a call
     * answered from the notification while this screen showed its ring is adopted rather than
     * dismissed ([adoptLockedAnswer]).
     */
    private fun observeSessionChanges(id: UUID) {
        scope.launch {
            CallStore.sessionUpdates(id).collect { session ->
                if (session.status == CallSessionStatus.ENDED) {
                    finish()
                } else if (isLockedCall) {
                    bindCallState(session)
                } else if (!isAnswering && session.status != CallSessionStatus.RINGING) {
                    if (adoptLockedAnswer(id)) return@collect
                    CallKitTelecomLog.d(TAG) {
                        "Call no longer ringing (${session.status.value}), finishing"
                    }
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        val id = callId ?: return
        val session = CallStore.session(id)
        if (session == null || session.status == CallSessionStatus.ENDED) {
            finish()
        } else if (!isAnswering && session.status != CallSessionStatus.RINGING) {
            if (!adoptLockedAnswer(id)) finish()
        } else {
            // Back on screen after stepping aside.
            updateProximityLock()
        }
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        releaseProximityLock()
    }

    /** Focused: runs an open-app tap made just before ([onOpenAppTapped]). */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val since = openAppPendingSince ?: return
        if (!hasFocus) return
        openAppPendingSince = null
        if (SystemClock.uptimeMillis() - since <= OPEN_APP_FOCUS_WAIT_MS) onOpenAppTapped()
    }

    /** Home, in the call (after an unlock without the app): the proximity lock goes with it. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        releaseProximityLock()
    }

    /**
     * Takes over a call answered from the notification while this screen showed its ring, instead
     * of closing on the state change: [CallNotificationReceiver] holds the call before it answers,
     * so the hold is seen here first. A call answered elsewhere (a headset, the system call UI) is
     * not held and closes this screen, as before.
     */
    private fun adoptLockedAnswer(id: UUID): Boolean {
        if (!LockedCallKeepAlive.isHeld(id)) return false
        isAnswering = true
        enterLockedCall(id)
        return true
    }

    override fun finish() {
        releaseProximityLock()
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (backCallback as? OnBackInvokedCallback)?.let {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
        }
        releaseProximityLock()
        scope.cancel()
    }
}
