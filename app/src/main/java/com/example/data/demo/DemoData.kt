package com.example.data.demo

import com.example.R
import com.example.data.model.*
import java.util.UUID

object DemoData {

    fun createInitialProject(): Project {
        val totalMs = 90000L // 1:30.0

        val clips = listOf(
            StoryboardClip(
                id = "clip_1",
                originalFileName = "0-00_intro.png",
                sampleResKey = "sample_scene_1",
                parsedStartMs = 0L,
                durationMs = 3000L,
                zoomType = ZoomType.ZOOM_IN,
                zoomDepthPercent = 8f,
                transitionToNext = CutTransition.CROSSFADE,
                transitionDurationSec = 0.40f
            ),
            StoryboardClip(
                id = "clip_2",
                originalFileName = "0-03_character.png",
                sampleResKey = "sample_scene_2",
                parsedStartMs = 3000L,
                durationMs = 9000L,
                zoomType = ZoomType.ZOOM_OUT,
                zoomDepthPercent = 10f,
                transitionToNext = CutTransition.WIPE_LEFT,
                transitionDurationSec = 0.40f
            ),
            StoryboardClip(
                id = "clip_3",
                originalFileName = "0-12_desk_setup.png",
                sampleResKey = "sample_scene_3",
                parsedStartMs = 12000L,
                durationMs = 11000L,
                zoomType = ZoomType.ZOOM_IN,
                zoomDepthPercent = 8f,
                transitionToNext = CutTransition.SLIDE_RIGHT,
                transitionDurationSec = 0.40f
            ),
            StoryboardClip(
                id = "clip_4",
                originalFileName = "0_23_20260920000636.png",
                sampleResKey = "sample_scene_4",
                parsedStartMs = 23000L,
                durationMs = 15000L,
                zoomType = ZoomType.ZOOM_OUT,
                zoomDepthPercent = 12f,
                transitionToNext = CutTransition.CIRCLE_OPEN,
                transitionDurationSec = 0.40f
            ),
            StoryboardClip(
                id = "clip_5",
                originalFileName = "0-38_night_city.png",
                sampleResKey = "sample_scene_5",
                parsedStartMs = 38000L,
                durationMs = 22000L,
                zoomType = ZoomType.ZOOM_IN,
                zoomDepthPercent = 8f,
                transitionToNext = CutTransition.FADE_TO_BLACK,
                transitionDurationSec = 0.50f
            ),
            StoryboardClip(
                id = "clip_6",
                originalFileName = "1-00_conclusion.png",
                sampleResKey = "sample_scene_6",
                parsedStartMs = 60000L,
                durationMs = 30000L,
                zoomType = ZoomType.ZOOM_OUT,
                zoomDepthPercent = 10f,
                transitionToNext = CutTransition.CROSSFADE,
                transitionDurationSec = 0.40f
            )
        )

        val soundEffects = listOf(
            SoundEffectItem(soundName = "Whoosh", timestampMs = 3000L, volume = 0.8f),
            SoundEffectItem(soundName = "Pop", timestampMs = 12000L, volume = 0.9f),
            SoundEffectItem(soundName = "Ding", timestampMs = 23000L, volume = 0.85f),
            SoundEffectItem(soundName = "Boom", timestampMs = 38000L, volume = 0.95f),
            SoundEffectItem(soundName = "Whoosh", timestampMs = 60000L, volume = 0.8f)
        )

        val captionsText = """
            [0:00] In the quiet midnight studio, a new cut began to take shape.
            [0:03] Every frame aligns directly to the speaker's vocal cadence.
            [0:12] By simply naming files with their timestamp, the cut builds itself.
            [0:23] Motion, zoom, and atmosphere FX bring still images to life.
            [0:38] Sound effects drop in automatically right on key moments.
            [1:00] Export high quality video directly on your Android device.
        """.trimIndent()

        val parsedCaptions = TimestampParser.parseScript(captionsText)

        return Project(
            id = "demo_project_1",
            name = "part 2 second video",
            voiceoverName = "part 2 second video.wav",
            voiceoverDurationMs = totalMs,
            clips = clips,
            soundEffects = soundEffects,
            captionsScript = captionsText,
            captions = parsedCaptions,
            aspectRatio = AspectRatio.LANDSCAPE_16_9,
            fps = ExportFps.FPS_24,
            quality = ExportQuality.P720,
            globalZoomDepth = 8f,
            fadeInSec = 0.5f,
            fadeOutSec = 0.6f,
            activeEffect = AtmosphereEffect.NONE,
            effectIntensity = 0.50f,
            globalTransition = CutTransition.CROSSFADE,
            transitionDurationSec = 0.40f
        )
    }

    fun createSampleStoryboards(): List<StoryboardClip> {
        return listOf(
            StoryboardClip(
                originalFileName = "0-00.png",
                sampleResKey = "sample_scene_1",
                parsedStartMs = 0L,
                durationMs = 4000L
            ),
            StoryboardClip(
                originalFileName = "0-04_opening.png",
                sampleResKey = "sample_scene_2",
                parsedStartMs = 4000L,
                durationMs = 10000L
            ),
            StoryboardClip(
                originalFileName = "0-14_details.png",
                sampleResKey = "sample_scene_3",
                parsedStartMs = 14000L,
                durationMs = 12000L
            ),
            StoryboardClip(
                originalFileName = "0-26_action.png",
                sampleResKey = "sample_scene_4",
                parsedStartMs = 26000L,
                durationMs = 14000L
            ),
            StoryboardClip(
                originalFileName = "0-40_outro.png",
                sampleResKey = "sample_scene_5",
                parsedStartMs = 40000L,
                durationMs = 20000L
            )
        )
    }

    fun getDrawableForSampleKey(key: String?): Int {
        return when (key) {
            "sample_scene_1" -> R.drawable.sample_scene_1
            "sample_scene_2" -> R.drawable.sample_scene_2
            "sample_scene_3" -> R.drawable.sample_scene_3
            "sample_scene_4" -> R.drawable.sample_scene_4
            "sample_scene_5" -> R.drawable.sample_scene_5
            "sample_scene_6" -> R.drawable.sample_scene_6
            else -> R.drawable.sample_scene_1
        }
    }
}
