package com.focusbuddy

import android.app.Application
import com.focusbuddy.app.AppContainer

open class FocusBuddyApplication : Application() {
    override fun onCreate() { super.onCreate(); container.reminderScheduler.initialize() }
    val container by lazy { AppContainer(this) }
}
