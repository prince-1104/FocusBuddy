package com.focusbuddy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Code-native placeholder artwork. Decorative unless a caller supplies a description. */
@Composable
fun MascotArtwork(modifier: Modifier = Modifier, description: String? = null) {
    Canvas(modifier.semantics { if (description != null) contentDescription = description }) {
        val w = size.width
        val h = size.height
        val ink = Color(0xFF3B2C68)
        drawOval(Color(0xFFDAD0EB), Offset(w * .15f, h * .85f), Size(w * .7f, h * .09f))
        drawOval(Color(0xFFB9A2EA), Offset(w * .2f, h * .06f), Size(w * .2f, h * .42f))
        drawOval(Color(0xFFB9A2EA), Offset(w * .6f, h * .06f), Size(w * .2f, h * .42f))
        drawOval(Color(0xFFD9C8FA), Offset(w * .08f, h * .28f), Size(w * .84f, h * .62f))
        drawCircle(ink, w * .025f, Offset(w * .36f, h * .55f))
        drawCircle(ink, w * .025f, Offset(w * .64f, h * .55f))
        drawOval(Color(0xFFEAAEBD), Offset(w * .23f, h * .61f), Size(w * .14f, h * .065f))
        drawOval(Color(0xFFEAAEBD), Offset(w * .63f, h * .61f), Size(w * .14f, h * .065f))
        drawArc(ink, 15f, 150f, false, Offset(w * .44f, h * .59f), Size(w * .12f, h * .1f),
            style = Stroke(width = w * .015f))
    }
}

@Composable
fun MascotContainer(title: String, message: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MascotArtwork(Modifier.size(136.dp), "Lavender bunny companion with a gentle smile")
            Text(title, style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
