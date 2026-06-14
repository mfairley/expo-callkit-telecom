package com.example.expocallkittelecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject

/**
 * Receives the package-internal `expo.modules.callkittelecom.ACTION_CALL_EVENT` broadcast, fired
 * when a call event can't reach a live JS observer — e.g. the user declined an incoming call while
 * the app was killed and `onCallEnded` had nowhere to go.
 *
 * The broadcast carries an `eventName` extra and a `payload` extra holding the JSON-encoded body JS
 * would have received, including the full `session` (so `serverCallId` and any push `metadata` are
 * available here without app-side state).
 *
 * A real app would notify its backend here so the caller stops ringing immediately (call goAsync()
 * and do the network call off the main thread). The example logs the payload and plays a short beep
 * so you can confirm it ran without a cable — watch `adb logcat -s CallEndedReceiver` as well.
 *
 * The manifest `<receiver>` is registered by the expo-callkit-telecom config plugin
 * (`androidEventReceiver` in app.config.ts); this source is copied in by
 * plugins/withCallEndedReceiver.js.
 */
class CallEndedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CallEndedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventName = intent.getStringExtra("eventName")
        if (eventName != "onCallEnded") return

        val payload = intent.getStringExtra("payload")?.let { JSONObject(it) }
        val session = payload?.optJSONObject("session")
        val incomingCall = session?.optJSONObject("incomingCallEvent")
        val serverCallId = incomingCall?.optString("serverCallId")
        val metadata = incomingCall?.optJSONObject("metadata")

        Log.i(
            TAG,
            "Call ended with no live JS observer - event: $eventName, " +
                "serverCallId: $serverCallId, metadata: $metadata",
        )

        playConfirmationBeep()
    }

    /**
     * Plays a short beep so you can tell the receiver ran without watching logcat — useful in the
     * killed-app case. goAsync() holds the (possibly freshly cold-started) process alive until the
     * tone finishes. Make sure the device isn't on silent. A real app would do its backend call
     * here instead.
     */
    private fun playConfirmationBeep() {
        val pendingResult = goAsync()
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
            Handler(Looper.getMainLooper())
                .postDelayed(
                    {
                        toneGenerator.release()
                        pendingResult.finish()
                    },
                    500,
                )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play confirmation beep: ${e.message}")
            pendingResult.finish()
        }
    }
}
