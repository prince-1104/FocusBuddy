package com.focusbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.focusbuddy.ui.FocusBuddyApp
import com.focusbuddy.ui.theme.FocusBuddyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusBuddyTheme { FocusBuddyApp() }
        }
    }
}
