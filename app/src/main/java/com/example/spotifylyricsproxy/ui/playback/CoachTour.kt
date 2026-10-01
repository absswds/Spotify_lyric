package com.example.spotifylyricsproxy.ui.playback

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifylyricsproxy.R

/** Where each explained control currently sits, in root coordinates. */
private val coachBounds = mutableStateMapOf<String, Rect>()

/** Marks a control the button tour can point at. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.coachTarget(key: String): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(key, requester) {
        coachRequesters[key] = requester
        onDispose { coachRequesters.remove(key) }
    }
    bringIntoViewRequester(requester).onGloballyPositioned { coachBounds[key] = it.boundsInRoot() }
}

/** Lets the tour scroll a control into view first (the menu is taller than a landscape screen). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private val coachRequesters = mutableMapOf<String, BringIntoViewRequester>()

/** [inMenu]: the control lives in the player menu, which the tour opens first. */
private data class CoachStep(val key: String, val title: Int, val desc: Int, val inMenu: Boolean = false)

private val STEPS = listOf(
    CoachStep("lyrics", R.string.tour_lyrics_title, R.string.tour_lyrics_desc),
    CoachStep("progress", R.string.tour_progress_title, R.string.tour_progress_desc),
    CoachStep("play", R.string.tour_play_title, R.string.tour_play_desc),
    CoachStep("shuffle", R.string.tour_shuffle_title, R.string.tour_shuffle_desc),
    CoachStep("repeat", R.string.tour_repeat_title, R.string.tour_repeat_desc),
    CoachStep("translate", R.string.tour_translate_title, R.string.tour_translate_desc),
    CoachStep("menu", R.string.tour_menu_title, R.string.tour_menu_desc),
    CoachStep("menu_quick", R.string.tour_menu_quick_title, R.string.tour_menu_quick_desc, inMenu = true),
    CoachStep("menu_research", R.string.tour_menu_research_title, R.string.tour_menu_research_desc, inMenu = true),
    CoachStep("menu_offset", R.string.tour_menu_offset_title, R.string.tour_menu_offset_desc, inMenu = true),
    CoachStep("menu_display", R.string.tour_menu_display_title, R.string.tour_menu_display_desc, inMenu = true),
    CoachStep("menu_library", R.string.tour_menu_library_title, R.string.tour_menu_library_desc, inMenu = true),
    CoachStep("menu_settings", R.string.tour_menu_settings_title, R.string.tour_menu_settings_desc, inMenu = true)
)

private val TourAccent = Color(0xFF1ED760)

/**
 * One-time tour after the first-launch guide: dims the player, cuts a spotlight
 * around one control at a time and explains it. The spotlight glides between
 * controls; tapping anywhere moves on.
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun CoachTour(menuOpen: Boolean, onOpenMenu: () -> Unit, onDone: () -> Unit) {
    // Player controls not in this layout are left out; menu ones only exist once it opens.
    val steps = remember { STEPS.filter { it.inMenu || coachBounds[it.key]?.isEmpty == false } }
    var index by remember { mutableIntStateOf(0) }
    val step = steps.getOrNull(index)
    if (step == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    // Entering the menu part: open the sheet and let it slide in before pointing.
    var menuSettled by remember { mutableStateOf(false) }
    LaunchedEffect(step.inMenu, menuOpen) {
        if (step.inMenu && !menuOpen) onOpenMenu()
        if (step.inMenu && menuOpen) {
            kotlinx.coroutines.delay(450)
            menuSettled = true
        }
    }
    if (step.inMenu && !menuSettled) return
    LaunchedEffect(step.key) {
        coachRequesters[step.key]?.bringIntoView()
    }
    val target = coachBounds[step.key]
    if (target == null || target.isEmpty) {
        // Not shown right now (e.g. a toggle these lyrics don't offer).
        LaunchedEffect(index) { index++ }
        return
    }
    val pad = with(LocalDensity.current) { 8.dp.toPx() }
    val spec = spring<Float>(dampingRatio = 0.78f, stiffness = 220f)
    val left by animateFloatAsState(target.left - pad, spec, label = "spotL")
    val top by animateFloatAsState(target.top - pad, spec, label = "spotT")
    val right by animateFloatAsState(target.right + pad, spec, label = "spotR")
    val bottom by animateFloatAsState(target.bottom + pad, spec, label = "spotB")
    val appear by animateFloatAsState(1f, tween(400), label = "tourIn")
    val pulse by rememberInfiniteTransition(label = "spotPulse")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "pulse")
    val next: () -> Unit = { index++ }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = appear
            }
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.74f))
                val corner = CornerRadius(22.dp.toPx())
                drawRoundRect(
                    Color.Transparent, Offset(left, top), Size(right - left, bottom - top),
                    corner, blendMode = BlendMode.Clear
                )
                val grow = pulse * 10.dp.toPx()
                drawRoundRect(
                    TourAccent.copy(alpha = 0.6f * (1f - pulse)),
                    Offset(left - grow, top - grow),
                    Size(right - left + grow * 2, bottom - top + grow * 2),
                    CornerRadius(corner.x + grow), style = Stroke(2.dp.toPx())
                )
            }
            .clickable(MutableInteractionSource(), indication = null, onClick = next)
    ) {
        val density = LocalDensity.current
        val below = target.center.y < constraints.maxHeight / 2f
        val gap = with(density) { (if (below) bottom else constraints.maxHeight - top).toDp() } + 16.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = if (below) gap else 0.dp, bottom = if (below) 0.dp else gap),
            contentAlignment = if (below) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 4 }) togetherWith
                        (fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 4 })
                },
                label = "tourBubble"
            ) { i ->
                val s = steps[i]
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xF2202A36))
                        .padding(18.dp)
                ) {
                    Text(stringResource(s.title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(s.desc), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1} / ${steps.size}", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        if (i < steps.size - 1) {
                            Text(
                                stringResource(R.string.onboarding_skip), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDone).padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Text(
                            stringResource(if (i < steps.size - 1) R.string.onboarding_next else R.string.tour_done),
                            color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(TourAccent)
                                .clickable(onClick = next)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
