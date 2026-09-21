package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngine
import com.example.data.database.AppDatabase
import com.example.data.demo.DemoData
import com.example.data.model.*
import com.example.data.repository.ProjectRepository
import com.example.rendering.ExportResult
import com.example.rendering.VideoRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class ScreenState {
    PROJECTS_LIST,
    PROJECT_SETUP,
    EDITOR
}

enum class EditorTab(val label: String) {
    MOTION("Motion"),
    TRANSITIONS("Transitions"),
    CAPTIONS("Captions"),
    EFFECTS("Effects"),
    AUDIO("Audio")
}

data class UiState(
    val screen: ScreenState = ScreenState.PROJECTS_LIST,
    val projects: List<Project> = listOf(DemoData.createInitialProject()),
    val currentProject: Project = DemoData.createInitialProject(),
    val isPlaying: Boolean = false,
    val currentPlaybackMs: Long = 0L,
    val isLooping: Boolean = false,
    val activeTab: EditorTab = EditorTab.MOTION,
    val selectedClipId: String? = null,
    val selectedCutIndex: Int? = null,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportStage: String = "Preparing project...",
    val exportCompleted: Boolean = false,
    val exportResult: ExportResult? = null,
    val showClipEditDialog: Boolean = false,
    val temporaryImportedClips: List<StoryboardClip> = emptyList(),
    val temporaryVoiceoverName: String? = null,
    val temporaryVoiceoverDurationMs: Long = 60000L,
    val temporaryFileSize: Long = 1024L * 1024L
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(AppDatabase.getInstance(application).projectDao())
    private val videoRenderer = VideoRenderer(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var exportJob: Job? = null
    private var lastTriggeredSfxMs: Long = -1L

    val waveformAmplitudes: List<Float> = AudioEngine.generateWaveformAmplitudes(140)

    init {
        // Load projects from local Room database
        viewModelScope.launch {
            repository.getProjectsFlow().collect { savedProjects ->
                if (savedProjects.isNotEmpty()) {
                    _uiState.update { state ->
                        state.copy(
                            projects = savedProjects,
                            currentProject = if (savedProjects.any { it.id == state.currentProject.id }) {
                                savedProjects.first { it.id == state.currentProject.id }
                            } else {
                                state.currentProject
                            }
                        )
                    }
                } else {
                    // Seed initial demo project into database
                    val initial = DemoData.createInitialProject()
                    repository.saveProject(initial)
                }
            }
        }
    }

    fun navigateTo(screen: ScreenState) {
        pause()
        _uiState.update { it.copy(screen = screen) }
    }

    fun startNewProject(
        name: String = "Untitled project",
        ratio: AspectRatio = AspectRatio.LANDSCAPE_16_9,
        quality: ExportQuality = ExportQuality.P720,
        fps: ExportFps = ExportFps.FPS_24
    ) {
        pause()
        val blankProject = Project(
            name = name,
            aspectRatio = ratio,
            quality = quality,
            fps = fps,
            voiceoverDurationMs = 60000L
        )
        _uiState.update {
            it.copy(
                screen = ScreenState.PROJECT_SETUP,
                currentProject = blankProject,
                temporaryImportedClips = emptyList(),
                temporaryVoiceoverName = null,
                temporaryVoiceoverDurationMs = 60000L,
                currentPlaybackMs = 0L
            )
        }
    }

    fun openProject(project: Project) {
        pause()
        _uiState.update {
            it.copy(
                screen = ScreenState.EDITOR,
                currentProject = project,
                currentPlaybackMs = 0L,
                selectedClipId = project.clips.firstOrNull()?.id
            )
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            _uiState.update { state ->
                val updated = state.projects.filterNot { it.id == projectId }
                state.copy(projects = updated)
            }
        }
    }

    fun updateProjectName(newName: String) {
        _uiState.update { state ->
            val updated = state.currentProject.copy(name = newName)
            saveProjectUpdate(state, updated)
        }
    }

    // --- SETUP FLOW ---

    fun setSetupVoiceover(name: String, durationMs: Long, uri: String? = null, fileSize: Long = 1024L * 1024L) {
        _uiState.update { state ->
            state.copy(
                temporaryVoiceoverName = name,
                temporaryVoiceoverDurationMs = durationMs,
                temporaryFileSize = fileSize,
                currentProject = state.currentProject.copy(
                    voiceoverName = name,
                    voiceoverDurationMs = durationMs,
                    voiceoverUri = uri
                )
            )
        }
    }

    fun loadSampleVoiceover() {
        setSetupVoiceover("narration_part2.wav", 75000L, null, 1400000L)
    }

    fun addFilesToSetup(fileNames: List<String>) {
        val currentClips = _uiState.value.temporaryImportedClips.toMutableList()
        var indexOffset = currentClips.size

        for (fileName in fileNames) {
            val parsedMs = TimestampParser.parseFilenameToMs(fileName, indexOffset)
            val sampleKey = "sample_scene_${(indexOffset % 6) + 1}"
            currentClips.add(
                StoryboardClip(
                    originalFileName = fileName,
                    sampleResKey = sampleKey,
                    parsedStartMs = parsedMs
                )
            )
            indexOffset++
        }

        val totalMs = _uiState.value.temporaryVoiceoverDurationMs
        val calculated = TimestampParser.recalculateClipDurations(currentClips, totalMs)

        _uiState.update { it.copy(temporaryImportedClips = calculated) }
    }

    fun loadDemoStoryboardPack() {
        val demoClips = DemoData.createSampleStoryboards()
        val totalMs = _uiState.value.temporaryVoiceoverDurationMs
        val calculated = TimestampParser.recalculateClipDurations(demoClips, totalMs)
        _uiState.update { it.copy(temporaryImportedClips = calculated) }
    }

    fun removeSetupClip(clipId: String) {
        val filtered = _uiState.value.temporaryImportedClips.filterNot { it.id == clipId }
        val calculated = TimestampParser.recalculateClipDurations(filtered, _uiState.value.temporaryVoiceoverDurationMs)
        _uiState.update { it.copy(temporaryImportedClips = calculated) }
    }

    fun buildTimelineFromSetup() {
        val tempClips = _uiState.value.temporaryImportedClips
        val clipsToUse = if (tempClips.isNotEmpty()) {
            tempClips
        } else {
            DemoData.createSampleStoryboards()
        }

        val voName = _uiState.value.temporaryVoiceoverName ?: "voiceover_synced.wav"
        val voDuration = _uiState.value.temporaryVoiceoverDurationMs.coerceAtLeast(60000L)

        val updatedClips = TimestampParser.recalculateClipDurations(clipsToUse, voDuration)

        val updatedProject = _uiState.value.currentProject.copy(
            voiceoverName = voName,
            voiceoverDurationMs = voDuration,
            clips = updatedClips,
            soundEffects = listOf(
                SoundEffectItem(soundName = "Whoosh", timestampMs = updatedClips.getOrNull(1)?.parsedStartMs ?: 3000L),
                SoundEffectItem(soundName = "Ding", timestampMs = updatedClips.getOrNull(2)?.parsedStartMs ?: 12000L),
                SoundEffectItem(soundName = "Boom", timestampMs = updatedClips.getOrNull(3)?.parsedStartMs ?: 24000L)
            ),
            captions = updatedClips.mapIndexed { idx, clip ->
                CaptionItem(
                    startMs = clip.parsedStartMs,
                    endMs = clip.endMs,
                    text = "Clip ${idx + 1} synchronized at ${clip.formatStartTime()}"
                )
            }
        )

        viewModelScope.launch {
            repository.saveProject(updatedProject)
        }

        _uiState.update { state ->
            val updatedList = if (state.projects.any { it.id == updatedProject.id }) {
                state.projects.map { if (it.id == updatedProject.id) updatedProject else it }
            } else {
                state.projects + updatedProject
            }
            state.copy(
                screen = ScreenState.EDITOR,
                currentProject = updatedProject,
                projects = updatedList,
                currentPlaybackMs = 0L,
                selectedClipId = updatedClips.firstOrNull()?.id
            )
        }
    }

    // --- PLAYBACK ENGINE ---

    fun togglePlay() {
        if (_uiState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = true) }

        playbackJob = viewModelScope.launch {
            val tickInterval = 40L // ~25 fps update rate for smooth playhead & zoom
            val totalMs = _uiState.value.currentProject.totalDurationMs

            while (isActive) {
                delay(tickInterval)
                val currentMs = _uiState.value.currentPlaybackMs
                val nextMs = currentMs + tickInterval

                // Check and trigger sound effects
                val sfxInTick = _uiState.value.currentProject.soundEffects.filter {
                    it.timestampMs in currentMs..nextMs && it.timestampMs != lastTriggeredSfxMs
                }
                for (sfx in sfxInTick) {
                    AudioEngine.playSoundEffect(sfx.soundName)
                    lastTriggeredSfxMs = sfx.timestampMs
                }

                if (nextMs >= totalMs) {
                    if (_uiState.value.isLooping) {
                        _uiState.update { it.copy(currentPlaybackMs = 0L) }
                        lastTriggeredSfxMs = -1L
                    } else {
                        _uiState.update { it.copy(currentPlaybackMs = totalMs, isPlaying = false) }
                        break
                    }
                } else {
                    _uiState.update { it.copy(currentPlaybackMs = nextMs) }
                }
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(ms: Long) {
        val totalMs = _uiState.value.currentProject.totalDurationMs
        val clamped = ms.coerceIn(0L, totalMs)
        _uiState.update { it.copy(currentPlaybackMs = clamped) }

        val activeClip = _uiState.value.currentProject.clips.firstOrNull { clamped in it.parsedStartMs..it.endMs }
        if (activeClip != null) {
            _uiState.update { it.copy(selectedClipId = activeClip.id) }
        }
    }

    fun seekRelative(deltaMs: Long) {
        seekTo(_uiState.value.currentPlaybackMs + deltaMs)
    }

    fun restart() {
        seekTo(0L)
    }

    fun toggleLoop() {
        _uiState.update { it.copy(isLooping = !it.isLooping) }
    }

    // --- EDITOR TABS & SELECTION ---

    fun setActiveTab(tab: EditorTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectClip(clipId: String?) {
        _uiState.update { it.copy(selectedClipId = clipId, selectedCutIndex = null) }
        if (clipId != null) {
            val clip = _uiState.value.currentProject.clips.firstOrNull { it.id == clipId }
            if (clip != null) {
                seekTo(clip.parsedStartMs)
            }
        }
    }

    fun selectCut(cutIndex: Int) {
        _uiState.update { it.copy(selectedCutIndex = cutIndex, activeTab = EditorTab.TRANSITIONS) }
        val clips = _uiState.value.currentProject.clips
        if (cutIndex in clips.indices) {
            seekTo(clips[cutIndex].endMs - 500L)
        }
    }

    // --- MOTION SETTINGS ---

    fun setGlobalZoomDepth(depthPercent: Float) {
        _uiState.update { state ->
            val updated = state.currentProject.copy(
                globalZoomDepth = depthPercent,
                clips = state.currentProject.clips.map { it.copy(zoomDepthPercent = depthPercent) }
            )
            saveProjectUpdate(state, updated)
        }
    }

    fun applyZoomToAll(type: ZoomType) {
        _uiState.update { state ->
            val updated = state.currentProject.copy(
                clips = state.currentProject.clips.map { it.copy(zoomType = type) }
            )
            saveProjectUpdate(state, updated)
        }
    }

    fun alternateZooms() {
        _uiState.update { state ->
            val updated = state.currentProject.copy(
                clips = state.currentProject.clips.mapIndexed { index, clip ->
                    clip.copy(zoomType = if (index % 2 == 0) ZoomType.ZOOM_IN else ZoomType.ZOOM_OUT)
                }
            )
            saveProjectUpdate(state, updated)
        }
    }

    fun clearZooms() {
        applyZoomToAll(ZoomType.NONE)
    }

    // --- TRANSITIONS & FADES ---

    fun setFadeIn(seconds: Float) {
        _uiState.update { state ->
            val updated = state.currentProject.copy(fadeInSec = seconds)
            saveProjectUpdate(state, updated)
        }
    }

    fun setFadeOut(seconds: Float) {
        _uiState.update { state ->
            val updated = state.currentProject.copy(fadeOutSec = seconds)
            saveProjectUpdate(state, updated)
        }
    }

    fun setGlobalTransition(transition: CutTransition) {
        _uiState.update { state ->
            val updated = state.currentProject.copy(
                globalTransition = transition,
                clips = state.currentProject.clips.map { it.copy(transitionToNext = transition) }
            )
            saveProjectUpdate(state, updated)
        }
    }

    fun setTransitionDuration(seconds: Float) {
        _uiState.update { state ->
            val updated = state.currentProject.copy(
                transitionDurationSec = seconds,
                clips = state.currentProject.clips.map { it.copy(transitionDurationSec = seconds) }
            )
            saveProjectUpdate(state, updated)
        }
    }

    fun applyTransitionToAllCuts(transition: CutTransition) {
        setGlobalTransition(transition)
    }

    fun toggleRandomizeTransitions(enabled: Boolean) {
        _uiState.update { state ->
            val transList = listOf(CutTransition.CROSSFADE, CutTransition.FADE_TO_BLACK, CutTransition.WIPE_LEFT, CutTransition.SLIDE_LEFT)
            val updated = state.currentProject.copy(
                randomizeTransitions = enabled,
                clips = if (enabled) {
                    state.currentProject.clips.mapIndexed { idx, clip ->
                        clip.copy(transitionToNext = transList[idx % transList.size])
                    }
                } else {
                    state.currentProject.clips.map { it.copy(transitionToNext = state.currentProject.globalTransition) }
                }
            )
            saveProjectUpdate(state, updated)
        }
    }

    // --- CAPTIONS ---

    fun updateCaptionsScript(scriptText: String) {
        val parsed = TimestampParser.parseScript(scriptText)
        _uiState.update { state ->
            val updated = state.currentProject.copy(
                captionsScript = scriptText,
                captions = parsed
            )
            saveProjectUpdate(state, updated)
        }
    }

    // --- EFFECTS ---

    fun setAtmosphereEffect(effect: AtmosphereEffect) {
        _uiState.update { state ->
            val updated = state.currentProject.copy(activeEffect = effect)
            saveProjectUpdate(state, updated)
        }
    }

    fun setEffectIntensity(intensity: Float) {
        _uiState.update { state ->
            val updated = state.currentProject.copy(effectIntensity = intensity)
            saveProjectUpdate(state, updated)
        }
    }

    // --- AUDIO FX ---

    fun addSoundEffect(soundName: String) {
        val currentMs = _uiState.value.currentPlaybackMs
        val sfx = SoundEffectItem(soundName = soundName, timestampMs = currentMs)
        _uiState.update { state ->
            val updated = state.currentProject.copy(
                soundEffects = state.currentProject.soundEffects + sfx
            )
            saveProjectUpdate(state, updated)
        }
        AudioEngine.playSoundEffect(soundName)
    }

    fun removeSoundEffect(sfxId: String) {
        _uiState.update { state ->
            val updated = state.currentProject.copy(
                soundEffects = state.currentProject.soundEffects.filterNot { it.id == sfxId }
            )
            saveProjectUpdate(state, updated)
        }
    }

    fun previewSoundEffect(soundName: String) {
        AudioEngine.playSoundEffect(soundName)
    }

    // --- EXPORT CONTROLS (REAL MP4 VIDEO RENDERING) ---

    fun setAspectRatio(ratio: AspectRatio) {
        _uiState.update { state ->
            val updated = state.currentProject.copy(aspectRatio = ratio)
            saveProjectUpdate(state, updated)
        }
    }

    fun setFps(fps: ExportFps) {
        _uiState.update { state ->
            val updated = state.currentProject.copy(fps = fps)
            saveProjectUpdate(state, updated)
        }
    }

    fun setQuality(quality: ExportQuality) {
        _uiState.update { state ->
            val updated = state.currentProject.copy(quality = quality)
            saveProjectUpdate(state, updated)
        }
    }

    fun renderMP4() {
        if (_uiState.value.isExporting) return
        pause()
        exportJob?.cancel()

        _uiState.update {
            it.copy(
                isExporting = true,
                exportProgress = 0.02f,
                exportStage = "Preparing project...",
                exportCompleted = false,
                exportResult = null
            )
        }

        exportJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = videoRenderer.renderProject(_uiState.value.currentProject) { progress, stage ->
                    _uiState.update {
                        it.copy(
                            exportProgress = progress,
                            exportStage = stage
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportCompleted = true,
                        exportResult = result
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportCompleted = false,
                        exportStage = "Export cancelled"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportCompleted = false,
                        exportStage = "Error: ${e.localizedMessage ?: "Export failed"}"
                    )
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _uiState.update { it.copy(isExporting = false, exportCompleted = false) }
    }

    fun dismissExportDialog() {
        _uiState.update { it.copy(exportCompleted = false, isExporting = false) }
    }

    fun shareExportedVideo(context: Context) {
        val result = _uiState.value.exportResult ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, result.contentUri)
            putExtra(Intent.EXTRA_SUBJECT, result.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share exported video"))
    }

    fun openExportedVideo(context: Context) {
        val result = _uiState.value.exportResult ?: return
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(result.contentUri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(viewIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "No video player installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveProjectUpdate(state: UiState, updatedProject: Project): UiState {
        val updatedProjects = state.projects.map {
            if (it.id == updatedProject.id) updatedProject else it
        }
        viewModelScope.launch {
            repository.saveProject(updatedProject)
        }
        return state.copy(
            currentProject = updatedProject,
            projects = updatedProjects
        )
    }

    override fun onCleared() {
        super.onCleared()
        pause()
        exportJob?.cancel()
    }
}
