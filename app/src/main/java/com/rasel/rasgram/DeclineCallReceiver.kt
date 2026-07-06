package com.rasel.rasgram

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.firestore.FirebaseFirestore

class DeclineCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra("callId") ?: return

        // Update Firestore: call declined
        FirebaseFirestore.getInstance()
            .collection("calls")
            .document(callId)
            .update("status", "declined")

        // Dismiss the notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(RasgramMessagingService.CALL_NOTIFICATION_ID)
    }
}