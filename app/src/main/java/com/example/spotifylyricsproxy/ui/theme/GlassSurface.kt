package com.example.spotifylyricsproxy.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A subtle glass surface for transient controls (seek pill, bottom nav, floating buttons).
 *
 * This is NOT a full liquid-glass implementation; it is a restrained, production-safe
 * translucent material designed to float above album-colored backgrounds.
 *
 * Usage:
 * - Expanded seek pill
 * - Bottom navigation container
 * - Floating action buttons
 *
 * Do NOT use for: lyrics, cache rows, settings groups, playlist cards, correction forms.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(999.dp),
    glassAlpha: Float = 0.14f,
    borderAlpha: Float = 0.20f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.3f)
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = glassAlpha + 0.06f),
                        Color.White.copy(alpha = (glassAlpha - 0.04f).coerceAtLeast(0f))
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = borderAlpha),
                shape = shape
            ),
        content = content
    )
}

/**
 * A pill-shaped glass surface specifically for the expanded seek control.
 * Slightly stronger treatment than the generic GlassSurface.
 */
@Composable
fun SeekGlassPill(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        glassAlpha = 0.16f,
        borderAlpha = 0.22f,
        content = content
    )
}

/**
 * A subtle glass container for the bottom navigation bar.
 */
@Composable
fun NavGlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        glassAlpha = 0.10f,
        borderAlpha = 0.16f,
        content = content
    )
}
