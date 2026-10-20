package com.focusbuddy.ui.components

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.focusbuddy.domain.model.MascotSettings
import com.focusbuddy.domain.mascot.*

val LocalMascotSettings = staticCompositionLocalOf { MascotSettings() }

/** Also honors Android's system Remove animations preference. */
@Composable
fun mascotMotionAllowed(settings: MascotSettings): Boolean {
    if (androidx.compose.ui.platform.LocalInspectionMode.current) return false
    val context = LocalContext.current
    var systemMotion by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { systemMotion = ValueAnimator.areAnimatorsEnabled() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return settings.animationsEnabled && systemMotion
}

/** Original vector geometry. Static artwork is always available; no assets need loading. */
@Composable
fun MascotArtwork(modifier: Modifier = Modifier, description: String? = null,
    settings: MascotSettings = LocalMascotSettings.current, moment: MascotMoment = MascotMoment.Resting,
    reactionKey: Long = 0, animateEntrance: Boolean = false) {
    val variant = MascotVariant.from(settings.selectedMascot)
    val motion = mascotMotionAllowed(settings)
    val lift = remember { Animatable(0f) }
    LaunchedEffect(moment, reactionKey, motion) {
        lift.snapTo(0f)
        if (motion && (animateEntrance || reactionKey > 0)) {
            lift.animateTo(1f, tween(if (settings.personality == com.focusbuddy.domain.model.MascotPersonality.Playful) 180 else 260))
            lift.animateTo(0f, tween(320))
        }
    }
    Canvas(modifier.graphicsLayer { translationY = if (motion) -lift.value * 5.dp.toPx() else 0f }
        .semantics {
            if (description != null) contentDescription = "$description, ${variant.label}"
            stateDescription = when (moment) {
                MascotMoment.Resting -> "At ease"
                MascotMoment.UpcomingReminder -> "Ready for your reminder"
                MascotMoment.TaskCompleted -> "Celebrating a completed task"
                MascotMoment.DayCompleted -> "Celebrating all tasks complete"
            }
        }) {
        val w = size.width; val h = size.height
        val ink = Color(0xFF362B55)
        val body = Color(settings.selectedColor)
        val shade = androidx.compose.ui.graphics.lerp(body, ink, .18f)
        val happy = moment == MascotMoment.TaskCompleted || moment == MascotMoment.DayCompleted
        drawOval(Color(0x22362B55), Offset(w * .15f, h * .87f), Size(w * .7f, h * .07f))
        when (variant) {
            MascotVariant.Mallow -> {
                drawOval(shade, Offset(w * .2f, h * .06f), Size(w * .2f, h * .43f))
                drawOval(shade, Offset(w * .6f, h * .06f), Size(w * .2f, h * .43f))
                drawOval(body, Offset(w * .08f, h * .3f), Size(w * .84f, h * .6f))
            }
            MascotVariant.Luma -> {
                val ears = Path().apply { moveTo(w * .16f, h * .4f); lineTo(w * .16f, h * .15f); lineTo(w * .4f, h * .32f); lineTo(w * .6f, h * .32f); lineTo(w * .84f, h * .15f); lineTo(w * .84f, h * .4f); close() }
                drawPath(ears, shade)
                drawOval(body, Offset(w * .1f, h * .25f), Size(w * .8f, h * .66f))
                drawOval(shade, Offset(w * .08f, h * .52f), Size(w * .17f, h * .27f))
                drawOval(shade, Offset(w * .75f, h * .52f), Size(w * .17f, h * .27f))
                drawCircle(Color(0xFFFFF5E9), w * .15f, Offset(w * .35f, h * .51f))
                drawCircle(Color(0xFFFFF5E9), w * .15f, Offset(w * .65f, h * .51f))
            }
            MascotVariant.Pip -> {
                drawLine(ink, Offset(w * .5f, h * .36f), Offset(w * .5f, h * .16f), w * .025f)
                drawOval(Color(0xFF73B69C), Offset(w * .23f, h * .09f), Size(w * .28f, h * .17f))
                drawOval(Color(0xFF91C6AD), Offset(w * .49f, h * .04f), Size(w * .28f, h * .17f))
                drawRoundRect(body, Offset(w * .13f, h * .3f), Size(w * .74f, h * .59f), androidx.compose.ui.geometry.CornerRadius(w * .24f))
            }
        }
        val eyeY = h * .54f
        listOf(.35f, .65f).forEach { x ->
            if (happy) drawArc(ink, 195f, 150f, false, Offset(w * (x - .05f), eyeY - h * .025f), Size(w * .1f, h * .08f), style = Stroke(w * .018f))
            else drawCircle(ink, w * .025f, Offset(w * x, eyeY))
        }
        drawOval(Color(0xFFE8AAB9), Offset(w * .21f, h * .62f), Size(w * .13f, h * .055f))
        drawOval(Color(0xFFE8AAB9), Offset(w * .66f, h * .62f), Size(w * .13f, h * .055f))
        if (variant == MascotVariant.Luma) {
            drawPath(Path().apply { moveTo(w * .46f, h * .61f); lineTo(w * .54f, h * .61f); lineTo(w * .5f, h * .68f); close() }, ink)
        } else drawArc(ink, 15f, 150f, false, Offset(w * .44f, h * .61f), Size(w * .12f, h * .1f), style = Stroke(w * .016f))
        if (moment == MascotMoment.UpcomingReminder) {
            drawCircle(Color(0xFFFFF5E9), w * .12f, Offset(w * .84f, h * .24f))
            drawCircle(ink, w * .09f, Offset(w * .84f, h * .24f), style = Stroke(w * .014f))
            drawLine(ink, Offset(w * .84f, h * .24f), Offset(w * .84f, h * .18f), w * .014f)
            drawLine(ink, Offset(w * .84f, h * .24f), Offset(w * .89f, h * .24f), w * .014f)
        }
        if (happy) listOf(.1f, .9f).forEach { x ->
            drawLine(shade, Offset(w * (x - .04f), h * .15f), Offset(w * (x + .04f), h * .15f), w * .02f)
            drawLine(shade, Offset(w * x, h * .11f), Offset(w * x, h * .19f), w * .02f)
        }
    }
}

@Composable
fun MascotContainer(title: String, message: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MascotArtwork(Modifier.size(136.dp), "Your companion")
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
