package com.focusbuddy.data.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.focusbuddy.domain.time.TimeZoneSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.ZoneId

class DeviceTimeZoneSource(context: Context) : TimeZoneSource {
    private val appContext = context.applicationContext
    override val zones = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { trySend(ZoneId.systemDefault()) }
        }
        // Protected system broadcast; Android permits registration without an export flag.
        appContext.registerReceiver(receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))
        trySend(ZoneId.systemDefault())
        awaitClose { appContext.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
