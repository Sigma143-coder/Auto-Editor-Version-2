package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import com.example.data.model.*

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val voiceoverUri: String?,
    val voiceoverName: String?,
    val voiceoverDurationMs: Long,
    val captionsScript: String,
    val aspectRatio: String,
    val fps: String,
    val quality: String,
    val globalZoomDepth: Float,
    val fadeInSec: Float,
    val fadeOutSec: Float,
    val activeEffect: String,
    val effectIntensity: Float,
    val globalTransition: String,
    val transitionDurationSec: Float,
    val randomizeTransitions: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "storyboard_clips",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class StoryboardClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val originalFileName: String,
    val imageUri: String?,
    val sampleResKey: String?,
    val parsedStartMs: Long,
    val durationMs: Long,
    val zoomType: String,
    val zoomDepthPercent: Float,
    val transitionToNext: String,
    val transitionDurationSec: Float,
    val sortOrder: Int
)

@Entity(
    tableName = "sound_effects",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class SoundEffectEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val soundName: String,
    val timestampMs: Long,
    val volume: Float
)
