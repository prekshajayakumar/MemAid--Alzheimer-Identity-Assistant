package com.example.myapplication.util

import android.Manifest
import android.content.Context
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

object SmsHelper {

    fun sendText(context: Context, phone: String, message: String): Boolean {
        if (phone.isBlank() || message.isBlank()) return false

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) return false

        return try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    phone,
                    null,
                    ArrayList(parts),
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    phone,
                    null,
                    message,
                    null,
                    null
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}