package com.example.myapplication.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object CallCaregiver {

    fun dial(context: Context, phone: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
