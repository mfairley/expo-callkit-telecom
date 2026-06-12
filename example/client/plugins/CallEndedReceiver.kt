package com.example.expocallkittelecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives the package-internal `expo.modules.callkittelecom.ACTION_CALL_ENDED` broadcast,
 * fired only when an incoming call ends and no live JS observer exists to receive
 * `onCallEnded` — e.g. the user declined while the app was killed.
 *
 * A real app would notify its backend here so the caller stops ringing immediately
 * (call goAsync() and do the network call off the main thread). The example just logs
 * the extras — watch with `adb logcat -s CallEndedReceiver`.
 *
 * Copied into the generated android project by plugins/withCallEndedReceiver.js.
 */
class CallEndedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CallEndedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra("callId")
        val eventId = intent.getStringExtra("eventId")
        val serverCallId = intent.getStringExtra("serverCallId")
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        val metadata = intent.getSerializableExtra("metadata") as? Map<String, Any?>
        Log.i(
            TAG,
            "Call ended with no live JS observer - callId: $callId, eventId: $eventId, " +
                "serverCallId: $serverCallId, metadata: $metadata",
        )
    }
}
