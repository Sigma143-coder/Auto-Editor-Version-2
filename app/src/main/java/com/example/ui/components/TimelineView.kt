package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.demo.DemoData
import com.example.data.model.*
import com.example.ui.theme.*
import kotlin.math.max

@Composable
fun TimelineView(
    project: Project,
    currentPlaybackMs: Long,
    waveformAmplitudes: List<Float>,
    selectedClipId: String?,
    selectedCutIndex: Int?,
    onSeekTo: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onSelectCut: (Int) -> Unit,
    onAddSfxAtTime: (Long) -> Unit,
    onRemoveSfx: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalMs = project.totalDurationMs.coerceAtLeast(10000L)
    // Scale: Pixels per second. Allows comfortable viewing & scrubbing
    val pxPerSec = 40.dp
    val totalSeconds = totalMs / 1000f
    val timelineWidth = max(600.dp.value, totalSeconds * pxPerSec.value).dp

    val scrollState = rememberScrollState()

    // Auto-scroll timeline to keep playhead in view when playing
    LaunchedEffect(currentPlaybackMs) {
        val playheadFrac = currentPlaybackMs.toFloat() / totalMs
        val targetScroll = (playheadFrac * scrollState.maxValue).toInt()
        if (targetScroll in 0..scrollState.maxValue && kotlin.math.abs(scrollState.value - targetScroll) > 300) {
            scrollState.scrollTo((targetScroll - 200).coerceAtLeast(0))
        }
    }

    Surface(
        color = TimelineTrackBg,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, StudioBorder, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Track Headers Column (V, A, FX)
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .background(TimelineRulerBg)
                    .drawBehind {
                        drawLine(
                            color = StudioBorder,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
            ) {
                // Top corner for ruler
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .fillMaxWidth()
                        .background(StudioSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = "Ruler",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Divider(color = StudioBorder)

                // Track V
                Box(
                    modifier = Modifier
                        .height(58.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "V",
                        color = AccentPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Divider(color = StudioBorder)

                // Track A
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "A",
                        color = AccentCyanGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Divider(color = StudioBorder)

                // Track FX
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "FX",
                        color = AccentPink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Scrollable Timeline Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                Column(modifier = Modifier.width(timelineWidth)) {
                    // 1. Time Ruler
                    TimelineRuler(
                        totalMs = totalMs,
                        width = timelineWidth,
                        modifier = Modifier.height(28.dp)
                    )

                    Divider(color = StudioBorder)

                    // 2. Track V (Video clips & Diamond cut markers)
                    Box(
                        modifier = Modifier
                            .height(58.dp)
                            .fillMaxWidth()
                            .background(StudioBackground)
                    ) {
                        for (i in project.clips.indices) {
                            val clip = project.clips[i]
                            val startFrac = (clip.parsedStartMs.toFloat() / totalMs).coerceIn(0f, 1f)
                            val endFrac = (clip.endMs.toFloat() / totalMs).coerceIn(0f, 1f)
                            val clipWidth = (timelineWidth * (endFrac - startFrac)).coerceAtLeast(40.dp)
                            val clipLeft = timelineWidth * startFrac

                            val isSelected = clip.id == selectedClipId

                            Box(
                                modifier = Modifier
                                    .offset(x = clipLeft)
                                    .width(clipWidth)
                                    .height(58.dp)
                                    .padding(vertical = 2.dp, horizontal = 1.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) TimelineClipSelected else TimelineClipBg)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) AccentPrimary else StudioBorderHighlight,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onSelectClip(clip.id) }
                            ) {
                                // Thumbnail strip background preview
                                val drawableId = DemoData.getDrawableForSampleKey(clip.sampleResKey)
                                androidx.compose.foundation.Image(
                                    painter = painterResource(id = drawableId),
                                    contentDescription = clip.originalFileName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    alpha = 0.45f,
                                    contentScale = ContentScale.Crop
                                )

                                // Text details overlay
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Duration badge
                                    Box(
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = clip.formatDuration(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Filename
                                    Text(
                                        text = clip.originalFileName,
                                        color = TextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Diamond Transition Handle between clips
                            if (i < project.clips.lastIndex) {
                                val cutIndex = i
                                val cutLeft = clipLeft + clipWidth - 11.dp
                                val isCutSelected = selectedCutIndex == cutIndex

                                Box(
                                    modifier = Modifier
                                        .offset(x = cutLeft, y = 18.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (isCutSelected) AccentPink else StudioSurfaceCard)
                                        .border(1.5.dp, if (isCutSelected) Color.White else AccentPink, CircleShape)
                                        .clickable { onSelectCut(cutIndex) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "◇",
                                        color = if (isCutSelected) Color.White else AccentPink,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = StudioBorder)

                    // 3. Track A (Voiceover audio waveform)
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .fillMaxWidth()
                            .background(StudioBackground)
                    ) {
                        AudioWaveformCanvas(
                            amplitudes = waveformAmplitudes,
                            currentPlaybackMs = currentPlaybackMs,
                            totalMs = totalMs,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Voiceover label watermark
                        Text(
                            text = project.voiceoverName ?: "narration.wav",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 8.dp)
                        )
                    }

                    Divider(color = StudioBorder)

                    // 4. Track FX (Sound Effects track)
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .fillMaxWidth()
                            .background(StudioBackground)
                            .pointerInput(totalMs) {
                                detectTapGestures { offset ->
                                    val frac = offset.x / size.width
                                    val clickMs = (frac * totalMs).toLong()
                                    onAddSfxAtTime(clickMs)
                                }
                            }
                    ) {
                        if (project.soundEffects.isEmpty()) {
                            Text(
                                text = "+ Tap here to place sound effects",
                                color = TextMuted,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 8.dp)
                            )
                        }

                        for (sfx in project.soundEffects) {
                            val sfxFrac = (sfx.timestampMs.toFloat() / totalMs).coerceIn(0f, 1f)
                            val sfxLeft = timelineWidth * sfxFrac

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .offset(x = sfxLeft - 8.dp, y = 4.dp)
                                    .background(StudioSurfaceElevated, RoundedCornerShape(12.dp))
                                    .border(1.dp, AccentPink, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .clickable { onRemoveSfx(sfx.id) }
                            ) {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = sfx.soundName,
                                    tint = AccentPink,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = sfx.soundName,
                                    color = TextPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Interactive Scrubber & Playhead Overlay
                val density = LocalDensity.current
                val timelineWidthPx = with(density) { timelineWidth.toPx() }
                val playheadFrac = (currentPlaybackMs.toFloat() / totalMs).coerceIn(0f, 1f)
                val playheadXPx = timelineWidthPx * playheadFrac
                val playheadXDp = with(density) { playheadXPx.toDp() }

                Box(
                    modifier = Modifier
                        .offset(x = playheadXDp - 8.dp)
                        .width(16.dp)
                        .fillMaxHeight()
                        .pointerInput(totalMs, timelineWidthPx) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val newX = playheadXPx + change.position.x
                                val newFrac = (newX / timelineWidthPx).coerceIn(0f, 1f)
                                onSeekTo((newFrac * totalMs).toLong())
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val headWidth = size.width
                        val centerX = headWidth / 2f

                        // Scrubber head marker
                        val headPath = Path().apply {
                            moveTo(centerX - 6.dp.toPx(), 0f)
                            lineTo(centerX + 6.dp.toPx(), 0f)
                            lineTo(centerX + 6.dp.toPx(), 14.dp.toPx())
                            lineTo(centerX, 20.dp.toPx())
                            lineTo(centerX - 6.dp.toPx(), 14.dp.toPx())
                            close()
                        }
                        drawPath(headPath, color = TimelineScrubber)

                        // Vertical red scrubber line across all tracks
                        drawLine(
                            color = TimelineScrubber,
                            start = Offset(centerX, 18.dp.toPx()),
                            end = Offset(centerX, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRuler(
    totalMs: Long,
    width: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier
            .width(width)
            .background(TimelineRulerBg)
    ) {
        textPaint.textSize = 9.sp.toPx()

        val totalSec = (totalMs / 1000).toInt()
        val stepSec = when {
            totalSec <= 60 -> 5
            totalSec <= 180 -> 15
            else -> 30
        }

        var sec = 0
        while (sec <= totalSec) {
            val frac = sec.toFloat() / totalSec
            val x = size.width * frac

            // Major tick
            drawLine(
                color = StudioBorderHighlight,
                start = Offset(x, size.height - 10.dp.toPx()),
                end = Offset(x, size.height),
                strokeWidth = 1.5.dp.toPx()
            )

            // Text time label (e.g. 0:00, 0:30, 1:00)
            val min = sec / 60
            val s = sec % 60
            val label = String.format("%d:%02d", min, s)
            drawContext.canvas.nativeCanvas.drawText(label, x, size.height - 12.dp.toPx(), textPaint)

            // Minor intermediate ticks
            val nextSec = sec + stepSec
            if (nextSec <= totalSec) {
                val midX = size.width * ((sec + stepSec / 2f) / totalSec)
                drawLine(
                    color = StudioBorder,
                    start = Offset(midX, size.height - 5.dp.toPx()),
                    end = Offset(midX, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            sec += stepSec
        }
    }
}

@Composable
private fun AudioWaveformCanvas(
    amplitudes: List<Float>,
    currentPlaybackMs: Long,
    totalMs: Long,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val totalBars = amplitudes.size
        val barWidth = size.width / totalBars.toFloat()
        val currentFrac = (currentPlaybackMs.toFloat() / totalMs).coerceIn(0f, 1f)

        for (i in 0 until totalBars) {
            val amp = amplitudes[i]
            val x = i * barWidth
            val barFrac = i.toFloat() / totalBars
            val isPlayed = barFrac <= currentFrac

            val barHeight = (size.height * 0.8f * amp).coerceAtLeast(3.dp.toPx())
            val top = (size.height - barHeight) / 2f

            drawRect(
                color = if (isPlayed) TimelineWaveformActive else TimelineWaveform,
                topLeft = Offset(x + 1.dp.toPx(), top),
                size = Size((barWidth - 2.dp.toPx()).coerceAtLeast(1.dp.toPx()), barHeight)
            )
        }
    }
}
