package com.focusbuddy.ui.mascot

import android.app.NotificationManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Only called for an explicit preview or successful user action, never on reminder entry. */
@Composable
fun rememberCompanionSound(): () -> Unit {
    val context = LocalContext.current
    val player = remember { mutableStateOf<ToneGenerator?>(null) }
    DisposableEffect(Unit) { onDispose { player.value?.release() } }
    return remember(context) { {
        val audio = context.getSystemService(AudioManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL && notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            runCatching {
                val tone = player.value ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 20).also { player.value = it }
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 100)
            }
        }
    } }
}
