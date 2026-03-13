package com.example.myapplication.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myapplication.MainActivity
import com.example.myapplication.util.CaregiverPrefs

class KioskModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return

        val enabled = CaregiverPrefs.isKioskEnabled(context)

        if (!enabled) return

        val i = Intent(context, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }
}