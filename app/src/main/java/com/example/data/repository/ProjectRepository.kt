package com.example.data.repository

import com.example.data.database.*
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ProjectRepository(private val projectDao: ProjectDao) {

    fun getProjectsFlow(): Flow<List<Project>> {
        return projectDao.getAllProjects().map { entities ->
            entities.map { entity ->
                val clips = projectDao.getClipsForProject(entity.id)
                val sfx = projectDao.getSoundEffectsForProject(entity.id)
                mapToDomain(entity, clips, sfx)
            }
        }
    }

    suspend fun getProjectById(id: String): Project? = withContext(Dispatchers.IO) {
        val entity = projectDao.getProjectById(id) ?: return@withContext null
        val clips = projectDao.getClipsForProject(id)
        val sfx = projectDao.getSoundEffectsForProject(id)
        mapToDomain(entity, clips, sfx)
    }

    suspend fun saveProject(project: Project) = withContext(Dispatchers.IO) {
        val entity = ProjectEntity(
            id = project.id,
            name = project.name,
            voiceoverUri = project.voiceoverUri,
            voiceoverName = project.voiceoverName,
            voiceoverDurationMs = project.voiceoverDurationMs,
            captionsScript = project.captionsScript,
            aspectRatio = project.aspectRatio.name,
            fps = project.fps.name,
            quality = project.quality.name,
            globalZoomDepth = project.globalZoomDepth,
            fadeInSec = project.fadeInSec,
            fadeOutSec = project.fadeOutSec,
            activeEffect = project.activeEffect.name,
            effectIntensity = project.effectIntensity,
            globalTransition = project.globalTransition.name,
            transitionDurationSec = project.transitionDurationSec,
            randomizeTransitions = project.randomizeTransitions,
            createdAt = project.createdAt,
            updatedAt = System.currentTimeMillis()
        )

        val clipEntities = project.clips.mapIndexed { index, clip ->
            StoryboardClipEntity(
                id = clip.id,
                projectId = project.id,
                originalFileName = clip.originalFileName,
                imageUri = clip.imageUri,
                sampleResKey = clip.sampleResKey,
                parsedStartMs = clip.parsedStartMs,
                durationMs = clip.durationMs,
                zoomType = clip.zoomType.name,
                zoomDepthPercent = clip.zoomDepthPercent,
                transitionToNext = clip.transitionToNext.name,
                transitionDurationSec = clip.transitionDurationSec,
                sortOrder = index
            )
        }

        val sfxEntities = project.soundEffects.map { sfx ->
            SoundEffectEntity(
                id = sfx.id,
                projectId = project.id,
                soundName = sfx.soundName,
                timestampMs = sfx.timestampMs,
                volume = sfx.volume
            )
        }

        projectDao.insertProject(entity)
        projectDao.deleteClipsForProject(project.id)
        if (clipEntities.isNotEmpty()) {
            projectDao.insertClips(clipEntities)
        }
        projectDao.deleteSoundEffectsForProject(project.id)
        if (sfxEntities.isNotEmpty()) {
            projectDao.insertSoundEffects(sfxEntities)
        }
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        projectDao.deleteProject(id)
    }

    private fun mapToDomain(
        entity: ProjectEntity,
        clips: List<StoryboardClipEntity>,
        sfx: List<SoundEffectEntity>
    ): Project {
        val domainClips = clips.map { c ->
            StoryboardClip(
                id = c.id,
                originalFileName = c.originalFileName,
                imageUri = c.imageUri,
                sampleResKey = c.sampleResKey,
                parsedStartMs = c.parsedStartMs,
                durationMs = c.durationMs,
                zoomType = runCatching { ZoomType.valueOf(c.zoomType) }.getOrDefault(ZoomType.ZOOM_IN),
                zoomDepthPercent = c.zoomDepthPercent,
                transitionToNext = runCatching { CutTransition.valueOf(c.transitionToNext) }.getOrDefault(CutTransition.CROSSFADE),
                transitionDurationSec = c.transitionDurationSec
            )
        }

        val domainSfx = sfx.map { s ->
            SoundEffectItem(
                id = s.id,
                soundName = s.soundName,
                timestampMs = s.timestampMs,
                volume = s.volume
            )
        }

        return Project(
            id = entity.id,
            name = entity.name,
            voiceoverUri = entity.voiceoverUri,
            voiceoverName = entity.voiceoverName,
            voiceoverDurationMs = entity.voiceoverDurationMs,
            clips = domainClips,
            soundEffects = domainSfx,
            captionsScript = entity.captionsScript,
            captions = TimestampParser.parseScript(entity.captionsScript),
            aspectRatio = runCatching { AspectRatio.valueOf(entity.aspectRatio) }.getOrDefault(AspectRatio.LANDSCAPE_16_9),
            fps = runCatching { ExportFps.valueOf(entity.fps) }.getOrDefault(ExportFps.FPS_24),
            quality = runCatching { ExportQuality.valueOf(entity.quality) }.getOrDefault(ExportQuality.P720),
            globalZoomDepth = entity.globalZoomDepth,
            fadeInSec = entity.fadeInSec,
            fadeOutSec = entity.fadeOutSec,
            activeEffect = runCatching { AtmosphereEffect.valueOf(entity.activeEffect) }.getOrDefault(AtmosphereEffect.NONE),
            effectIntensity = entity.effectIntensity,
            globalTransition = runCatching { CutTransition.valueOf(entity.globalTransition) }.getOrDefault(CutTransition.CROSSFADE),
            transitionDurationSec = entity.transitionDurationSec,
            randomizeTransitions = entity.randomizeTransitions,
            createdAt = entity.createdAt
        )
    }
}
