package expo.modules.callkittelecom

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import expo.modules.callkittelecom.managers.CallManager
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
 */
class IncomingCallActivity : Activity() {
    companion object {
        private const val TAG = "ExpoCallKitTelecom.IncomingCallActivity"
        const val EXTRA_CALL_ID = "expo.modules.callkittelecom.EXTRA_CALL_ID"
    }

    private var callId: UUID? = null
    private var isAnswering = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureWindowForLockScreen()
        setContentView(R.layout.activity_incoming_call)

        val id = parseCallId() ?: return
        callId = id

        val session = CallStore.session(id)
        if (session == null || session.status != CallSessionStatus.RINGING) {
            CallKitTelecomLog.d(TAG) { "No ringing session for $id, finishing" }
            finish()
            return
        }

        val caller = session.remoteParticipants.firstOrNull()

        bindAppBranding()
        bindCallerInfo(caller?.displayName, session.options.hasVideo)
        loadAvatar(caller?.avatarUrl)
        bindButtons(id, session.options.hasVideo)
        observeSessionChanges(id)

        CallKitTelecomLog.d(TAG) { "Showing incoming call UI - callId: $id" }
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

    private fun bindAppBranding() {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            findViewById<ImageView>(R.id.expo_callkit_telecom_app_icon)
                .setImageDrawable(packageManager.getApplicationIcon(appInfo))
            findViewById<TextView>(R.id.expo_callkit_telecom_app_name).text =
                packageManager.getApplicationLabel(appInfo)
        } catch (_: Exception) {
            // Non-critical — branding just won't show
        }
    }

    private fun bindCallerInfo(displayName: String?, hasVideo: Boolean) {
        val name = displayName ?: "Unknown"

        findViewById<TextView>(R.id.expo_callkit_telecom_avatar_text).text =
            name.firstOrNull()?.uppercase() ?: "?"

        findViewById<TextView>(R.id.expo_callkit_telecom_caller_name).text = name

        findViewById<TextView>(R.id.expo_callkit_telecom_subtitle).text =
            if (hasVideo) "Incoming video call" else "Incoming call"
    }

    /**
     * Loads the caller's avatar from [avatarUrl] on a background thread. On success, displays a
     * circular-cropped image and hides the initial letter. On failure, silently keeps the initial
     * letter fallback.
     */
    private fun loadAvatar(avatarUrl: String?) {
        if (avatarUrl.isNullOrBlank()) return

        val avatarImage = findViewById<ImageView>(R.id.expo_callkit_telecom_avatar_image)
        val avatarText = findViewById<TextView>(R.id.expo_callkit_telecom_avatar_text)

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
                avatarImage.setImageDrawable(drawable)
                avatarImage.visibility = View.VISIBLE
                avatarText.visibility = View.GONE
            }
        }
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
    }

    /**
     * Answers the call directly, then dismisses the keyguard and launches the main Activity so the
     * user transitions into the in-call UI.
     *
     * The call is answered immediately (media connection starts) regardless of whether the keyguard
     * dismissal succeeds. This matches the behavior of iOS CallKit where audio connects before the
     * device is unlocked.
     */
    private fun onAnswerTapped(id: UUID) {
        if (isAnswering) return
        isAnswering = true
        CallKitTelecomLog.d(TAG) { "Answer tapped - callId: $id" }

        // Answer immediately — don't wait for keyguard dismissal
        CallManager.shared.answerCall(id)

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
     * Observes session updates to auto-dismiss when the call is no longer ringing (answered
     * elsewhere, timed out, or ended by the remote side).
     *
     * When [isAnswering] is true (user tapped answer locally), only auto-dismiss for ENDED status —
     * the CONNECTING transition is expected and handled by the keyguard dismissal flow.
     */
    private fun observeSessionChanges(id: UUID) {
        scope.launch {
            CallStore.sessionUpdates(id).collect { session ->
                if (session.status == CallSessionStatus.ENDED) {
                    finish()
                } else if (!isAnswering && session.status != CallSessionStatus.RINGING) {
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
        val id = callId ?: return
        val session = CallStore.session(id)
        if (session == null || session.status == CallSessionStatus.ENDED) {
            finish()
        } else if (!isAnswering && session.status != CallSessionStatus.RINGING) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
