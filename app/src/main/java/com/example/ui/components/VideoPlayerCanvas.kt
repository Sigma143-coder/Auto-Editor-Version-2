package com.example.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.demo.DemoData
import com.example.data.model.*
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun VideoPlayerCanvas(
    project: Project,
    currentPlaybackMs: Long,
    modifier: Modifier = Modifier
) {
    val clips = project.clips
    val totalMs = project.totalDurationMs

    // Determine currently active clip and next clip for transition blending
    var activeClipIndex = -1
    var activeClip: StoryboardClip? = null
    var nextClip: StoryboardClip? = null

    for (i in clips.indices) {
        val c = clips[i]
        if (currentPlaybackMs >= c.parsedStartMs && currentPlaybackMs < c.endMs) {
            activeClipIndex = i
            activeClip = c
            nextClip = clips.getOrNull(i + 1)
            break
        }
    }

    if (activeClip == null && clips.isNotEmpty()) {
        if (currentPlaybackMs >= (clips.lastOrNull()?.endMs ?: 0L)) {
            activeClip = clips.last()
            activeClipIndex = clips.lastIndex
        } else {
            activeClip = clips.first()
            activeClipIndex = 0
            nextClip = clips.getOrNull(1)
        }
    }

    // Determine active caption
    val activeCaption = project.captions.find {
        currentPlaybackMs >= it.startMs && currentPlaybackMs <= it.endMs
    }

    // Opening fade in / ending fade out alpha
    val fadeInMs = (project.fadeInSec * 1000).toLong()
    val fadeOutMs = (project.fadeOutSec * 1000).toLong()

    val sceneFadeAlpha = when {
        fadeInMs > 0 && currentPlaybackMs < fadeInMs -> {
            (currentPlaybackMs.toFloat() / fadeInMs).coerceIn(0f, 1f)
        }
        fadeOutMs > 0 && currentPlaybackMs > (totalMs - fadeOutMs) -> {
            ((totalMs - currentPlaybackMs).toFloat() / fadeOutMs).coerceIn(0f, 1f)
        }
        else -> 1f
    }

    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(Color.Black)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (activeClip != null) {
            val clipStart = activeClip.parsedStartMs
            val clipDur = activeClip.durationMs.coerceAtLeast(500L)
            val clipProgress = ((currentPlaybackMs - clipStart).toFloat() / clipDur).coerceIn(0f, 1f)

            // Ken Burns zoom scale calculation
            val zoomDepthFraction = (activeClip.zoomDepthPercent / 100f).coerceAtLeast(0.02f)
            val currentScale = when (activeClip.zoomType) {
                ZoomType.ZOOM_IN -> 1.0f + (zoomDepthFraction * clipProgress)
                ZoomType.ZOOM_OUT -> (1.0f + zoomDepthFraction) - (zoomDepthFraction * clipProgress)
                ZoomType.ALTERNATE -> {
                    if (activeClipIndex % 2 == 0) {
                        1.0f + (zoomDepthFraction * clipProgress)
                    } else {
                        (1.0f + zoomDepthFraction) - (zoomDepthFraction * clipProgress)
                    }
                }
                ZoomType.NONE -> 1.0f
            }

            // Transition window check
            val transitionDurationMs = (activeClip.transitionDurationSec * 1000).toLong()
            val timeUntilEnd = activeClip.endMs - currentPlaybackMs
            val isInTransition = nextClip != null && timeUntilEnd in 0..transitionDurationMs
            val transitionProgress = if (isInTransition) {
                1f - (timeUntilEnd.toFloat() / transitionDurationMs).coerceIn(0f, 1f)
            } else {
                0f
            }

            val currentDrawableId = DemoData.getDrawableForSampleKey(activeClip.sampleResKey)
            val nextDrawableId = nextClip?.let { DemoData.getDrawableForSampleKey(it.sampleResKey) }

            val currentPainter = painterResource(id = currentDrawableId)
            val nextPainter = nextDrawableId?.let { painterResource(id = it) }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Draw Current Clip with Ken Burns transform
                scale(scale = currentScale, pivot = Offset(canvasWidth / 2f, canvasHeight / 2f)) {
                    with(currentPainter) {
                        draw(size = size, alpha = sceneFadeAlpha)
                    }
                }

                // If in transition, blend next clip
                if (isInTransition && nextPainter != null) {
                    when (activeClip.transitionToNext) {
                        CutTransition.CROSSFADE -> {
                            with(nextPainter) {
                                draw(size = size, alpha = transitionProgress * sceneFadeAlpha)
                            }
                        }
                        CutTransition.FADE_TO_BLACK -> {
                            // Dip to black at midpoint then reveal next
                            if (transitionProgress < 0.5f) {
                                val blackAlpha = (transitionProgress * 2f).coerceIn(0f, 1f)
                                drawRect(Color.Black, alpha = blackAlpha)
                            } else {
                                val revealAlpha = ((transitionProgress - 0.5f) * 2f).coerceIn(0f, 1f)
                                with(nextPainter) {
                                    draw(size = size, alpha = revealAlpha * sceneFadeAlpha)
                                }
                            }
                        }
                        CutTransition.WIPE_LEFT -> {
                            val clipX = canvasWidth * (1f - transitionProgress)
                            drawIntoCanvas { canvas ->
                                canvas.save()
                                canvas.clipRect(androidx.compose.ui.geometry.Rect(clipX, 0f, canvasWidth, canvasHeight))
                                with(nextPainter) {
                                    draw(size = size, alpha = sceneFadeAlpha)
                                }
                                canvas.restore()
                            }
                        }
                        CutTransition.WIPE_RIGHT -> {
                            val clipX = canvasWidth * transitionProgress
                            drawIntoCanvas { canvas ->
                                canvas.save()
                                canvas.clipRect(androidx.compose.ui.geometry.Rect(0f, 0f, clipX, canvasHeight))
                                with(nextPainter) {
                                    draw(size = size, alpha = sceneFadeAlpha)
                                }
                                canvas.restore()
                            }
                        }
                        CutTransition.SLIDE_LEFT -> {
                            translate(left = canvasWidth * (1f - transitionProgress)) {
                                with(nextPainter) {
                                    draw(size = size, alpha = sceneFadeAlpha)
                                }
                            }
                        }
                        CutTransition.SLIDE_RIGHT -> {
                            translate(left = -canvasWidth * (1f - transitionProgress)) {
                                with(nextPainter) {
                                    draw(size = size, alpha = sceneFadeAlpha)
                                }
                            }
                        }
                        CutTransition.CIRCLE_OPEN -> {
                            val maxRadius = kotlin.math.hypot(canvasWidth, canvasHeight) / 2f
                            val currentRadius = maxRadius * transitionProgress
                            val path = Path().apply {
                                addOval(
                                    androidx.compose.ui.geometry.Rect(
                                        center = Offset(canvasWidth / 2f, canvasHeight / 2f),
                                        radius = currentRadius
                                    )
                                )
                            }
                            drawIntoCanvas { canvas ->
                                canvas.save()
                                canvas.clipPath(path)
                                with(nextPainter) {
                                    draw(size = size, alpha = sceneFadeAlpha)
                                }
                                canvas.restore()
                            }
                        }
                        CutTransition.NONE -> {}
                    }
                }

                // Apply Atmosphere & Genre FX
                drawAtmosphereEffect(
                    effect = project.activeEffect,
                    intensity = project.effectIntensity,
                    timeMs = currentPlaybackMs
                )

                // Opening/ending scene fade overlay
                if (sceneFadeAlpha < 1f) {
                    drawRect(Color.Black, alpha = 1f - sceneFadeAlpha)
                }
            }
        } else {
            // Empty preview state
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "No clips in timeline",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray
                )
                Text(
                    text = "Import images or load demo storyboard to begin preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Subtitle / Caption Overlay
        if (activeCaption != null && activeCaption.text.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = activeCaption.text,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private fun DrawScope.drawAtmosphereEffect(
    effect: AtmosphereEffect,
    intensity: Float,
    timeMs: Long
) {
    if (effect == AtmosphereEffect.NONE || intensity <= 0.01f) return

    when (effect) {
        AtmosphereEffect.NONE -> {}
        AtmosphereEffect.BW -> {
            drawRect(Color(0xFF808080), blendMode = BlendMode.Color, alpha = intensity * 0.95f)
        }
        AtmosphereEffect.SEPIA -> {
            drawRect(Color(0xFF704214), blendMode = BlendMode.Color, alpha = intensity * 0.75f)
            drawRect(Color(0xFFFFCC80), blendMode = BlendMode.Overlay, alpha = intensity * 0.35f)
        }
        AtmosphereEffect.WARM -> {
            drawRect(Color(0xFFF59E0B), blendMode = BlendMode.Overlay, alpha = intensity * 0.45f)
        }
        AtmosphereEffect.COOL -> {
            drawRect(Color(0xFF06B6D4), blendMode = BlendMode.Overlay, alpha = intensity * 0.45f)
        }
        AtmosphereEffect.VIGNETTE -> {
            val maxR = size.width.coerceAtLeast(size.height)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = intensity * 0.9f)),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = maxR * 0.65f
                )
            )
        }
        AtmosphereEffect.FILM_GRAIN, AtmosphereEffect.HEAVY_NOISE -> {
            val count = if (effect == AtmosphereEffect.HEAVY_NOISE) 150 else 70
            val random = Random(timeMs / 80)
            for (i in 0 until count) {
                val x = random.nextFloat() * size.width
                val y = random.nextFloat() * size.height
                val radius = random.nextFloat() * 1.8f + 0.5f
                val color = if (random.nextBoolean()) Color.White else Color.Black
                drawCircle(color, radius = radius, center = Offset(x, y), alpha = intensity * 0.25f)
            }
        }
        AtmosphereEffect.GLITCH, AtmosphereEffect.VHS -> {
            // Horizontal scan lines + subtle chromatic shift
            val step = 6f
            var y = 0f
            while (y < size.height) {
                drawRect(Color.Black.copy(alpha = intensity * 0.18f), topLeft = Offset(0f, y), size = Size(size.width, 2f))
                y += step
            }
            // RGB edge line simulation
            val glitchPhase = (sin(timeMs / 150.0) * 8.0 * intensity).toFloat()
            drawRect(
                Color.Cyan.copy(alpha = intensity * 0.15f),
                topLeft = Offset(glitchPhase, 0f),
                size = Size(size.width, size.height),
                blendMode = BlendMode.Screen
            )
            drawRect(
                Color.Red.copy(alpha = intensity * 0.15f),
                topLeft = Offset(-glitchPhase, 0f),
                size = Size(size.width, size.height),
                blendMode = BlendMode.Screen
            )
        }
        AtmosphereEffect.LIGHT_LEAK -> {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFB923C).copy(alpha = intensity * 0.6f),
                        Color(0xFFF43F5E).copy(alpha = intensity * 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.1f, size.height * 0.1f),
                    radius = size.width * 0.7f
                )
            )
        }
        AtmosphereEffect.SNOW, AtmosphereEffect.DUST -> {
            val count = if (effect == AtmosphereEffect.SNOW) 55 else 35
            for (i in 0 until count) {
                val seed = i * 1337
                val speed = (i % 5 + 1) * 0.0003f
                val y = ((timeMs * speed + (i * 0.17f)) % 1f) * size.height
                val x = (((i * 47) % 100) / 100f * size.width) + (sin(timeMs / 400.0 + i) * 6f).toFloat()
                val radius = if (effect == AtmosphereEffect.SNOW) (i % 3 + 1.5f) else (i % 2 + 1f)
                drawCircle(
                    Color.White.copy(alpha = intensity * 0.45f),
                    radius = radius,
                    center = Offset(x, y)
                )
            }
        }
    }
}
