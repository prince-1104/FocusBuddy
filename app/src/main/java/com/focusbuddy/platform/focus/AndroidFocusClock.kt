package com.focusbuddy.platform.focus

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.focusbuddy.domain.focus.FocusClock
import java.time.Instant

class AndroidFocusClock(private val context: Context) : FocusClock {
    override fun now(): Instant = Instant.now()
    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
    override fun bootId(): String = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0).toString()
}
