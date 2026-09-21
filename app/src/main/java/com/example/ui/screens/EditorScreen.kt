package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.components.TimelineView
import com.example.ui.components.VideoPlayerCanvas
import com.example.ui.theme.*
import com.example.ui.viewmodel.EditorTab
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    uiState: UiState,
    waveformAmplitudes: List<Float>,
    onBack: () -> Unit,
    onUpdateProjectName: (String) -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekRelative: (Long) -> Unit,
    onRestart: () -> Unit,
    onToggleLoop: () -> Unit,
    onSetActiveTab: (EditorTab) -> Unit,
    onSelectClip: (String?) -> Unit,
    onSelectCut: (Int) -> Unit,
    // Motion tab actions
    onSetZoomDepth: (Float) -> Unit,
    onApplyZoomToAll: (ZoomType) -> Unit,
    onAlternateZooms: () -> Unit,
    onClearZooms: () -> Unit,
    onSetFadeIn: (Float) -> Unit,
    onSetFadeOut: (Float) -> Unit,
    // Transitions tab actions
    onSetGlobalTransition: (CutTransition) -> Unit,
    onSetTransitionDuration: (Float) -> Unit,
    onApplyTransitionToAllCuts: (CutTransition) -> Unit,
    onToggleRandomizeTransitions: (Boolean) -> Unit,
    // Captions tab actions
    onUpdateCaptionsScript: (String) -> Unit,
    // Effects tab actions
    onSetAtmosphereEffect: (AtmosphereEffect) -> Unit,
    onSetEffectIntensity: (Float) -> Unit,
    // Audio tab actions
    onAddSoundEffect: (String) -> Unit,
    onRemoveSoundEffect: (String) -> Unit,
    onPreviewSoundEffect: (String) -> Unit,
    // Export actions
    onSetAspectRatio: (AspectRatio) -> Unit,
    onSetFps: (ExportFps) -> Unit,
    onSetQuality: (ExportQuality) -> Unit,
    onRenderMP4: () -> Unit,
    onDismissExportDialog: () -> Unit,
    onCancelExport: () -> Unit = {},
    onShareExportedVideo: () -> Unit = {},
    onOpenExportedVideo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val project = uiState.currentProject
    var showExportSheet by remember { mutableStateOf(false) }
    var showSidePanelOnMobile by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }

                        // Project title
                        Text(
                            text = project.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Audio status chip matching screenshot
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StudioSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = project.voiceoverName ?: "narration.wav",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Clips status chip matching screenshot
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StudioSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${project.clips.size} clips",
                                    color = TextPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Toggle controls drawer on mobile
                    IconButton(onClick = { showSidePanelOnMobile = !showSidePanelOnMobile }) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Controls",
                            tint = if (showSidePanelOnMobile) AccentPrimary else TextSecondary
                        )
                    }

                    // Export button
                    Button(
                        onClick = { showExportSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.MovieCreation,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioSurface
                )
            )
        },
        containerColor = StudioBackground
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main Workspace (Player + Side Controls)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val isWideScreen = maxWidth > 800.dp

                if (isWideScreen) {
                    // Side-by-side desktop layout matching screenshots
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Control Tabs Column (Motion, Transitions, Captions, Effects, Audio)
                        Surface(
                            color = StudioSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier
                                .width(340.dp)
                                .fillMaxHeight()
                        ) {
                            EditorTabContent(
                                uiState = uiState,
                                onSetActiveTab = onSetActiveTab,
                                onSetZoomDepth = onSetZoomDepth,
                                onApplyZoomToAll = onApplyZoomToAll,
                                onAlternateZooms = onAlternateZooms,
                                onClearZooms = onClearZooms,
                                onSetFadeIn = onSetFadeIn,
                                onSetFadeOut = onSetFadeOut,
                                onSetGlobalTransition = onSetGlobalTransition,
                                onSetTransitionDuration = onSetTransitionDuration,
                                onApplyTransitionToAllCuts = onApplyTransitionToAllCuts,
                                onToggleRandomizeTransitions = onToggleRandomizeTransitions,
                                onUpdateCaptionsScript = onUpdateCaptionsScript,
                                onSetAtmosphereEffect = onSetAtmosphereEffect,
                                onSetEffectIntensity = onSetEffectIntensity,
                                onAddSoundEffect = onAddSoundEffect,
                                onPreviewSoundEffect = onPreviewSoundEffect
                            )
                        }

                        // Center Preview Player Column
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            PlayerWithControls(
                                project = project,
                                currentPlaybackMs = uiState.currentPlaybackMs,
                                isPlaying = uiState.isPlaying,
                                isLooping = uiState.isLooping,
                                onTogglePlay = onTogglePlay,
                                onSeekRelative = onSeekRelative,
                                onRestart = onRestart,
                                onToggleLoop = onToggleLoop
                            )
                        }
                    }
                } else {
                    // Mobile / Vertical layout
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Center Preview Player
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.3f)
                        ) {
                            PlayerWithControls(
                                project = project,
                                currentPlaybackMs = uiState.currentPlaybackMs,
                                isPlaying = uiState.isPlaying,
                                isLooping = uiState.isLooping,
                                onTogglePlay = onTogglePlay,
                                onSeekRelative = onSeekRelative,
                                onRestart = onRestart,
                                onToggleLoop = onToggleLoop
                            )
                        }

                        // Collapsible or bottom panel for tabs
                        if (showSidePanelOnMobile) {
                            Surface(
                                color = StudioSurface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                            ) {
                                EditorTabContent(
                                    uiState = uiState,
                                    onSetActiveTab = onSetActiveTab,
                                    onSetZoomDepth = onSetZoomDepth,
                                    onApplyZoomToAll = onApplyZoomToAll,
                                    onAlternateZooms = onAlternateZooms,
                                    onClearZooms = onClearZooms,
                                    onSetFadeIn = onSetFadeIn,
                                    onSetFadeOut = onSetFadeOut,
                                    onSetGlobalTransition = onSetGlobalTransition,
                                    onSetTransitionDuration = onSetTransitionDuration,
                                    onApplyTransitionToAllCuts = onApplyTransitionToAllCuts,
                                    onToggleRandomizeTransitions = onToggleRandomizeTransitions,
                                    onUpdateCaptionsScript = onUpdateCaptionsScript,
                                    onSetAtmosphereEffect = onSetAtmosphereEffect,
                                    onSetEffectIntensity = onSetEffectIntensity,
                                    onAddSoundEffect = onAddSoundEffect,
                                    onPreviewSoundEffect = onPreviewSoundEffect
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Multi-Track Timeline (Ruler, Track V, Track A, Track FX)
            TimelineView(
                project = project,
                currentPlaybackMs = uiState.currentPlaybackMs,
                waveformAmplitudes = waveformAmplitudes,
                selectedClipId = uiState.selectedClipId,
                selectedCutIndex = uiState.selectedCutIndex,
                onSeekTo = onSeekTo,
                onSelectClip = onSelectClip,
                onSelectCut = onSelectCut,
                onAddSfxAtTime = { _ -> onAddSoundEffect("Whoosh") },
                onRemoveSfx = onRemoveSoundEffect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        }
    }

    // Export Bottom Sheet / Dialog
    if (showExportSheet) {
        ExportDialog(
            project = project,
            onDismiss = { showExportSheet = false },
            onSetAspectRatio = onSetAspectRatio,
            onSetFps = onSetFps,
            onSetQuality = onSetQuality,
            onRender = {
                showExportSheet = false
                onRenderMP4()
            }
        )
    }

    // Export Progress & Completion Dialog
    if (uiState.isExporting || uiState.exportCompleted) {
        Dialog(onDismissRequest = {
            if (!uiState.isExporting) onDismissExportDialog()
        }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            progress = { uiState.exportProgress },
                            color = AccentPrimary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.exportStage,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${(uiState.exportProgress * 100).toInt()}% • Encoding H.264 video & AAC audio",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = onCancelExport,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                        ) {
                            Text("Cancel export", fontSize = 12.sp)
                        }
                    } else if (uiState.exportCompleted) {
                        val result = uiState.exportResult
                        Surface(
                            shape = CircleShape,
                            color = AccentGreen.copy(alpha = 0.2f),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Export complete",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Specs card
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StudioSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Filename", color = TextMuted, fontSize = 11.sp)
                                    Text(
                                        text = result?.fileName ?: "autostory_video.mp4",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Resolution", color = TextMuted, fontSize = 11.sp)
                                    Text(
                                        text = result?.resolution ?: project.quality.label,
                                        color = TextPrimary,
                                        fontSize = 11.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("FPS", color = TextMuted, fontSize = 11.sp)
                                    Text(
                                        text = "${result?.fps ?: project.fps.fps} FPS",
                                        color = TextPrimary,
                                        fontSize = 11.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Duration", color = TextMuted, fontSize = 11.sp)
                                    Text(
                                        text = result?.formatDuration() ?: project.formatTotalDuration(),
                                        color = AccentCyanGlow,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("File size", color = TextMuted, fontSize = 11.sp)
                                    Text(
                                        text = result?.formatFileSize() ?: "2.1 MB",
                                        color = TextPrimary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action buttons: Save, Open, Share, Export again
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenExportedVideo,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = onShareExportedVideo,
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = StudioSurfaceElevated),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderHighlight),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = AccentCyanGlow, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", fontSize = 12.sp, color = TextPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRenderMP4,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Export again", fontSize = 11.sp, color = TextSecondary)
                            }

                            Button(
                                onClick = onDismissExportDialog,
                                colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save & Done", fontSize = 11.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerWithControls(
    project: Project,
    currentPlaybackMs: Long,
    isPlaying: Boolean,
    isLooping: Boolean,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onRestart: () -> Unit,
    onToggleLoop: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Video Preview Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val ratio = project.aspectRatio.ratioWidth / project.aspectRatio.ratioHeight
            VideoPlayerCanvas(
                project = project,
                currentPlaybackMs = currentPlaybackMs,
                modifier = Modifier
                    .aspectRatio(ratio)
                    .fillMaxHeight()
            )
        }

        // Playback Transport Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .background(StudioSurfaceCard, RoundedCornerShape(24.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Restart button
            IconButton(onClick = onRestart, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.FirstPage,
                    contentDescription = "Restart",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Seek -5s
            IconButton(onClick = { onSeekRelative(-5000L) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Replay5,
                    contentDescription = "-5s",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Play / Pause prominent button
            Surface(
                shape = CircleShape,
                color = AccentPrimary,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onTogglePlay)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Seek +5s
            IconButton(onClick = { onSeekRelative(5000L) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Forward5,
                    contentDescription = "+5s",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Loop toggle
            IconButton(onClick = onToggleLoop, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Repeat,
                    contentDescription = "Loop",
                    tint = if (isLooping) AccentCyanGlow else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Time Readout e.g. 00:04.2 / 01:30.0
            val currentSec = currentPlaybackMs / 1000
            val cMin = currentSec / 60
            val cSec = currentSec % 60
            val cTenth = (currentPlaybackMs % 1000) / 100
            val timeText = String.format("%02d:%02d.%d / %s", cMin, cSec, cTenth, project.formatTotalDuration())

            Text(
                text = timeText,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun EditorTabContent(
    uiState: UiState,
    onSetActiveTab: (EditorTab) -> Unit,
    onSetZoomDepth: (Float) -> Unit,
    onApplyZoomToAll: (ZoomType) -> Unit,
    onAlternateZooms: () -> Unit,
    onClearZooms: () -> Unit,
    onSetFadeIn: (Float) -> Unit,
    onSetFadeOut: (Float) -> Unit,
    onSetGlobalTransition: (CutTransition) -> Unit,
    onSetTransitionDuration: (Float) -> Unit,
    onApplyTransitionToAllCuts: (CutTransition) -> Unit,
    onToggleRandomizeTransitions: (Boolean) -> Unit,
    onUpdateCaptionsScript: (String) -> Unit,
    onSetAtmosphereEffect: (AtmosphereEffect) -> Unit,
    onSetEffectIntensity: (Float) -> Unit,
    onAddSoundEffect: (String) -> Unit,
    onPreviewSoundEffect: (String) -> Unit
) {
    val project = uiState.currentProject

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab row header
        ScrollableTabRow(
            selectedTabIndex = uiState.activeTab.ordinal,
            containerColor = StudioSurface,
            contentColor = AccentPrimary,
            edgePadding = 8.dp,
            divider = { Divider(color = StudioBorder) }
        ) {
            EditorTab.values().forEach { tab ->
                Tab(
                    selected = uiState.activeTab == tab,
                    onClick = { onSetActiveTab(tab) },
                    text = {
                        Text(
                            text = tab.label,
                            fontWeight = if (uiState.activeTab == tab) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                )
            }
        }

        // Active Tab Pane Body
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (uiState.activeTab) {
                // TAB 1: MOTION (Screenshot 3)
                EditorTab.MOTION -> {
                    item {
                        Text(
                            text = "MOTION — KEN BURNS ZOOM",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Click an image on the timeline to set its zoom. Set the depth, or apply to all here.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )
                    }

                    item {
                        Text(
                            text = "Zoom depth: ${project.globalZoomDepth.toInt()}%",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = project.globalZoomDepth,
                            onValueChange = onSetZoomDepth,
                            valueRange = 0f..50f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentPrimary,
                                activeTrackColor = AccentPrimary,
                                inactiveTrackColor = StudioSurfaceElevated
                            )
                        )
                    }

                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = { onApplyZoomToAll(ZoomType.ZOOM_IN) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("Zoom in all", fontSize = 10.sp)
                            }
                            FilledTonalButton(
                                onClick = { onApplyZoomToAll(ZoomType.ZOOM_OUT) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("Zoom out all", fontSize = 10.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = onAlternateZooms,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("Alternate", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = onClearZooms,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("Clear", fontSize = 10.sp, color = TextMuted)
                            }
                        }
                    }

                    item {
                        Divider(color = StudioBorder, modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "SCENE FADES",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Fade the opening and ending (video & audio).",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    item {
                        Text(
                            text = "Fade in: ${String.format("%.1f", project.fadeInSec)}s",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Slider(
                            value = project.fadeInSec,
                            onValueChange = onSetFadeIn,
                            valueRange = 0.0f..3.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentPrimary,
                                activeTrackColor = AccentPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Fade out: ${String.format("%.1f", project.fadeOutSec)}s",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Slider(
                            value = project.fadeOutSec,
                            onValueChange = onSetFadeOut,
                            valueRange = 0.0f..3.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentPrimary,
                                activeTrackColor = AccentPrimary
                            )
                        )
                    }
                }

                // TAB 2: TRANSITIONS (Screenshot 4)
                EditorTab.TRANSITIONS -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TRANSITIONS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Randomise", fontSize = 11.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(
                                    checked = project.randomizeTransitions,
                                    onCheckedChange = onToggleRandomizeTransitions,
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val cutIdx = uiState.selectedCutIndex
                        val guideText = if (cutIdx != null) {
                            "Setting transition for cut #${cutIdx + 1}"
                        } else {
                            "Tap a ◇ cut above to set its transition"
                        }
                        Text(
                            text = guideText,
                            fontSize = 11.sp,
                            color = if (cutIdx != null) AccentPink else TextMuted
                        )
                    }

                    item {
                        // Grid of 8 transitions matching screenshot 4
                        val transitions = CutTransition.values()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (row in transitions.toList().chunked(2)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    for (t in row) {
                                        val isSelected = project.globalTransition == t
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) AccentPrimary.copy(alpha = 0.2f) else StudioSurfaceCard,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) AccentPrimary else StudioBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onSetGlobalTransition(t) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Transform,
                                                    contentDescription = null,
                                                    tint = if (isSelected) AccentPrimary else TextMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = t.label,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) TextPrimary else TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Duration: ${String.format("%.2f", project.transitionDurationSec)}s",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Slider(
                            value = project.transitionDurationSec,
                            onValueChange = onSetTransitionDuration,
                            valueRange = 0.10f..1.50f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentPrimary,
                                activeTrackColor = AccentPrimary
                            )
                        )
                    }

                    item {
                        Button(
                            onClick = { onApplyTransitionToAllCuts(project.globalTransition) },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderHighlight),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Apply \"${project.globalTransition.label}\" to all cuts",
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // TAB 3: CAPTIONS (Screenshot 5)
                EditorTab.CAPTIONS -> {
                    item {
                        Text(
                            text = "CAPTIONS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Timestamped script automatically syncs subtitles onto the video.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    item {
                        var scriptInput by remember { mutableStateOf(project.captionsScript) }

                        OutlinedTextField(
                            value = scriptInput,
                            onValueChange = {
                                scriptInput = it
                                onUpdateCaptionsScript(it)
                            },
                            label = { Text("Script with timestamps [mm:ss]") },
                            placeholder = { Text("[0:00] First line...\n[0:05] Second line...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPrimary,
                                unfocusedBorderColor = StudioBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            minLines = 7,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Text(
                            text = "Parsed Subtitle Lines (${project.captions.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in project.captions) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = StudioSurfaceCard,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = String.format("[%02d:%02d]", (c.startMs / 1000) / 60, (c.startMs / 1000) % 60),
                                            color = AccentCyanGlow,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = c.text,
                                            color = TextPrimary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 4: EFFECTS (Screenshot 6)
                EditorTab.EFFECTS -> {
                    item {
                        Text(
                            text = "ATMOSPHERE & GENRE FX",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "One effect applies to the whole video.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    item {
                        // Grid of 13 atmosphere effects matching Screenshot 6
                        val effects = AtmosphereEffect.values()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (chunk in effects.toList().chunked(3)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    for (fx in chunk) {
                                        val isSelected = project.activeEffect == fx
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) AccentPrimary.copy(alpha = 0.25f) else StudioSurfaceCard,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) AccentPrimary else StudioBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onSetAtmosphereEffect(fx) }
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = fx.label,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color.White else TextSecondary
                                                )
                                            }
                                        }
                                    }
                                    // Fill empty columns in row
                                    for (empty in 0 until (3 - chunk.size)) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Intensity: ${(project.effectIntensity * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Slider(
                            value = project.effectIntensity,
                            onValueChange = onSetEffectIntensity,
                            valueRange = 0.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentPrimary,
                                activeTrackColor = AccentPrimary
                            )
                        )
                    }
                }

                // TAB 5: AUDIO (Screenshot 7)
                EditorTab.AUDIO -> {
                    item {
                        Text(
                            text = "SOUND EFFECTS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select a sound, then click the FX track to place it. Drag a marker to move it; click it to set volume or remove.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )
                    }

                    item {
                        val sfxLibrary = listOf("Whoosh", "Swoosh", "Pop", "Ding", "Click", "Boom")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (pair in sfxLibrary.chunked(2)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    for (sfx in pair) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = StudioSurfaceCard,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = { onPreviewSoundEffect(sfx) },
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.PlayCircle,
                                                            contentDescription = "Preview",
                                                            tint = AccentCyanGlow,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = sfx,
                                                        fontSize = 12.sp,
                                                        color = TextPrimary,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { onAddSoundEffect(sfx) },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Add,
                                                        contentDescription = "Add",
                                                        tint = AccentPink,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "+ Tap on the FX track below anytime to place sounds at that second.",
                            fontSize = 11.sp,
                            color = AccentPink
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportDialog(
    project: Project,
    onDismiss: () -> Unit,
    onSetAspectRatio: (AspectRatio) -> Unit,
    onSetFps: (ExportFps) -> Unit,
    onSetQuality: (ExportQuality) -> Unit,
    onRender: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurfaceCard),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Export video",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )

                // Aspect Ratio Selector
                Column {
                    Text("Aspect ratio", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AspectRatio.values().forEach { ar ->
                            val isSelected = project.aspectRatio == ar
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSetAspectRatio(ar) },
                                label = { Text(ar.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // FPS Selector
                Column {
                    Text("Framerate", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportFps.values().forEach { fps ->
                            val isSelected = project.fps == fps
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSetFps(fps) },
                                label = { Text(fps.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Quality Selector
                Column {
                    Text("Quality", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportQuality.values().forEach { q ->
                            val isSelected = project.quality == q
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSetQuality(q) },
                                label = { Text(q.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Specs summary
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StudioSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${project.aspectRatio.dimensionLabel} · ${project.clips.size} clips",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            text = project.formatTotalDuration(),
                            color = AccentCyanGlow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRender,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Render MP4", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
