package com.focusbuddy

/** Unit tests own their databases and scheduler fakes; production startup is tested separately. */
class TestApplication : FocusBuddyApplication() { override fun onCreate() = Unit }
