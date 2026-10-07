package com.ararabr.app

import android.app.Application
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AraraApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize OneSignal SDK (App ID: 7d5dd27c-9a76-42e6-9938-e44f876b5d07)
        OneSignal.Debug.logLevel = LogLevel.NONE
        OneSignal.initWithContext(this, AppConfig.ONESIGNAL_APP_ID)

        // Prompt user for push notification permission on Android 13+
        CoroutineScope(Dispatchers.IO).launch {
            OneSignal.Notifications.requestPermission(true)
        }
    }
}
