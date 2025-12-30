package com.example.myapplication

import android.app.Application
import com.example.myapplication.util.NotificationChannels

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
    }
}
