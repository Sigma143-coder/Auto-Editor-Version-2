package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Query("SELECT * FROM storyboard_clips WHERE projectId = :projectId ORDER BY sortOrder ASC, parsedStartMs ASC")
    suspend fun getClipsForProject(projectId: String): List<StoryboardClipEntity>

    @Query("SELECT * FROM sound_effects WHERE projectId = :projectId ORDER BY timestampMs ASC")
    suspend fun getSoundEffectsForProject(projectId: String): List<SoundEffectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<StoryboardClipEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSoundEffects(sfx: List<SoundEffectEntity>)

    @Query("DELETE FROM storyboard_clips WHERE projectId = :projectId")
    suspend fun deleteClipsForProject(projectId: String)

    @Query("DELETE FROM sound_effects WHERE projectId = :projectId")
    suspend fun deleteSoundEffectsForProject(projectId: String)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)
}
