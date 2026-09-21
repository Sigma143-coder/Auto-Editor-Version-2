package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ProjectSetupScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.ScreenState

class MainActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsState()

                when (uiState.screen) {
                    ScreenState.PROJECTS_LIST -> {
                        ProjectsScreen(
                            projects = uiState.projects,
                            onOpenProject = { project -> viewModel.openProject(project) },
                            onStartNewProject = { name, ratio, quality, fps ->
                                viewModel.startNewProject(name, ratio, quality, fps)
                            },
                            onDeleteProject = { id -> viewModel.deleteProject(id) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    ScreenState.PROJECT_SETUP -> {
                        ProjectSetupScreen(
                            projectName = uiState.currentProject.name,
                            voiceoverName = uiState.temporaryVoiceoverName,
                            voiceoverDurationMs = uiState.temporaryVoiceoverDurationMs,
                            importedClips = uiState.temporaryImportedClips,
                            onUpdateProjectName = { viewModel.updateProjectName(it) },
                            onSetVoiceover = { name, dur, uri, size -> viewModel.setSetupVoiceover(name, dur, uri, size) },
                            onLoadDemoVoiceover = { viewModel.loadSampleVoiceover() },
                            onAddFiles = { fileNames -> viewModel.addFilesToSetup(fileNames) },
                            onLoadDemoStoryboards = { viewModel.loadDemoStoryboardPack() },
                            onRemoveClip = { clipId -> viewModel.removeSetupClip(clipId) },
                            onBuildTimeline = { viewModel.buildTimelineFromSetup() },
                            onBack = { viewModel.navigateTo(ScreenState.PROJECTS_LIST) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    ScreenState.EDITOR -> {
                        EditorScreen(
                            uiState = uiState,
                            waveformAmplitudes = viewModel.waveformAmplitudes,
                            onBack = { viewModel.navigateTo(ScreenState.PROJECTS_LIST) },
                            onUpdateProjectName = { viewModel.updateProjectName(it) },
                            onTogglePlay = { viewModel.togglePlay() },
                            onSeekTo = { ms -> viewModel.seekTo(ms) },
                            onSeekRelative = { delta -> viewModel.seekRelative(delta) },
                            onRestart = { viewModel.restart() },
                            onToggleLoop = { viewModel.toggleLoop() },
                            onSetActiveTab = { tab -> viewModel.setActiveTab(tab) },
                            onSelectClip = { id -> viewModel.selectClip(id) },
                            onSelectCut = { cutIdx -> viewModel.selectCut(cutIdx) },
                            onSetZoomDepth = { depth -> viewModel.setGlobalZoomDepth(depth) },
                            onApplyZoomToAll = { type -> viewModel.applyZoomToAll(type) },
                            onAlternateZooms = { viewModel.alternateZooms() },
                            onClearZooms = { viewModel.clearZooms() },
                            onSetFadeIn = { sec -> viewModel.setFadeIn(sec) },
                            onSetFadeOut = { sec -> viewModel.setFadeOut(sec) },
                            onSetGlobalTransition = { t -> viewModel.setGlobalTransition(t) },
                            onSetTransitionDuration = { sec -> viewModel.setTransitionDuration(sec) },
                            onApplyTransitionToAllCuts = { t -> viewModel.applyTransitionToAllCuts(t) },
                            onToggleRandomizeTransitions = { en -> viewModel.toggleRandomizeTransitions(en) },
                            onUpdateCaptionsScript = { text -> viewModel.updateCaptionsScript(text) },
                            onSetAtmosphereEffect = { fx -> viewModel.setAtmosphereEffect(fx) },
                            onSetEffectIntensity = { intensity -> viewModel.setEffectIntensity(intensity) },
                            onAddSoundEffect = { name -> viewModel.addSoundEffect(name) },
                            onRemoveSoundEffect = { id -> viewModel.removeSoundEffect(id) },
                            onPreviewSoundEffect = { name -> viewModel.previewSoundEffect(name) },
                            onSetAspectRatio = { ratio -> viewModel.setAspectRatio(ratio) },
                            onSetFps = { fps -> viewModel.setFps(fps) },
                            onSetQuality = { q -> viewModel.setQuality(q) },
                            onRenderMP4 = { viewModel.renderMP4() },
                            onDismissExportDialog = { viewModel.dismissExportDialog() },
                            onCancelExport = { viewModel.cancelExport() },
                            onShareExportedVideo = { viewModel.shareExportedVideo(this@MainActivity) },
                            onOpenExportedVideo = { viewModel.openExportedVideo(this@MainActivity) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.pause()
    }
}
