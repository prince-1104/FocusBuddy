package com.focusbuddy

import android.app.Application
import com.focusbuddy.app.AppContainer

class FocusBuddyApplication : Application() {
    val container by lazy { AppContainer(this) }
}
