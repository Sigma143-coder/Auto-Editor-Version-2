package com.example.data.model

import java.util.UUID

enum class AspectRatio(val label: String, val ratioWidth: Float, val ratioHeight: Float, val dimensionLabel: String) {
    LANDSCAPE_16_9("16:9", 16f, 9f, "1280×720 · faster"),
    PORTRAIT_9_16("9:16", 9f, 16f, "720×1280 · vertical"),
    SQUARE_1_1("1:1", 1f, 1f, "720×720 · square"),
    PORTRAIT_4_5("4:5", 4f, 5f, "864×1080 · portrait")
}

enum class ExportFps(val label: String, val fps: Int) {
    FPS_24("24 fps", 24),
    FPS_30("30 fps", 30),
    FPS_60("60 fps", 60)
}

enum class ExportQuality(val label: String) {
    P720("720p"),
    P1080("1080p"),
    P480("480p")
}

enum class ZoomType(val label: String) {
    ZOOM_IN("Zoom in"),
    ZOOM_OUT("Zoom out"),
    ALTERNATE("Alternate"),
    NONE("Static")
}

enum class CutTransition(val label: String, val iconName: String) {
    NONE("None", "none"),
    CROSSFADE("Crossfade", "crossfade"),
    FADE_TO_BLACK("Fade to black", "fade_black"),
    WIPE_LEFT("Wipe left", "wipe_left"),
    WIPE_RIGHT("Wipe right", "wipe_right"),
    SLIDE_LEFT("Slide left", "slide_left"),
    SLIDE_RIGHT("Slide right", "slide_right"),
    CIRCLE_OPEN("Circle open", "circle_open")
}

enum class AtmosphereEffect(val label: String) {
    NONE("None"),
    BW("B&W"),
    SEPIA("Sepia"),
    WARM("Warm"),
    COOL("Cool"),
    FILM_GRAIN("Film grain"),
    HEAVY_NOISE("Heavy noise"),
    VIGNETTE("Vignette"),
    GLITCH("Glitch"),
    VHS("VHS"),
    LIGHT_LEAK("Light leak"),
    SNOW("Snow"),
    DUST("Dust")
}

data class StoryboardClip(
    val id: String = UUID.randomUUID().toString(),
    val originalFileName: String,
    val imageUri: String? = null,
    val sampleResKey: String? = null,
    val parsedStartMs: Long = 0L,
    val durationMs: Long = 5000L,
    val zoomType: ZoomType = ZoomType.ZOOM_IN,
    val zoomDepthPercent: Float = 8f,
    val transitionToNext: CutTransition = CutTransition.CROSSFADE,
    val transitionDurationSec: Float = 0.40f
) {
    val endMs: Long get() = parsedStartMs + durationMs

    fun formatStartTime(): String {
        val totalSec = parsedStartMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val msTenth = (parsedStartMs % 1000) / 100
        return String.format("%02d:%02d.%d", min, sec, msTenth)
    }

    fun formatDuration(): String {
        val sec = durationMs / 1000.0
        return String.format("%.1fs", sec)
    }
}

data class SoundEffectItem(
    val id: String = UUID.randomUUID().toString(),
    val soundName: String,
    val timestampMs: Long,
    val volume: Float = 0.9f
)

data class CaptionItem(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled project",
    val voiceoverUri: String? = null,
    val voiceoverName: String? = null,
    val voiceoverDurationMs: Long = 60000L, // default 1 min
    val clips: List<StoryboardClip> = emptyList(),
    val soundEffects: List<SoundEffectItem> = emptyList(),
    val captionsScript: String = "",
    val captions: List<CaptionItem> = emptyList(),
    val aspectRatio: AspectRatio = AspectRatio.LANDSCAPE_16_9,
    val fps: ExportFps = ExportFps.FPS_24,
    val quality: ExportQuality = ExportQuality.P720,
    val globalZoomDepth: Float = 8f, // 8% as shown in screenshot
    val fadeInSec: Float = 0.5f,
    val fadeOutSec: Float = 0.6f,
    val activeEffect: AtmosphereEffect = AtmosphereEffect.NONE,
    val effectIntensity: Float = 0.50f,
    val globalTransition: CutTransition = CutTransition.CROSSFADE,
    val transitionDurationSec: Float = 0.40f,
    val randomizeTransitions: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalDurationMs: Long
        get() = if (voiceoverDurationMs > 0) {
            voiceoverDurationMs
        } else if (clips.isNotEmpty()) {
            clips.maxOf { it.endMs }
        } else {
            10000L
        }

    fun formatTotalDuration(): String {
        val totalSec = totalDurationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val tenths = (totalDurationMs % 1000) / 100
        return String.format("%02d:%02d.%d", min, sec, tenths)
    }
}

/**
 * Parser for timestamp filenames according to AutoEditor conventions:
 * "Name each image or video clip with the second it appears — 0-03.png or 0-03.mp4 cuts in at 0:03"
 */
object TimestampParser {
    fun parseFilenameToMs(fileName: String, fallbackIndex: Int = 0): Long {
        val cleanName = fileName.substringBeforeLast('.')

        // Pattern 1: mm-ss or mm_ss or mm:ss with optional milliseconds (.5 or .500) and optional suffix
        // e.g. "0-03", "01-25", "0_23_20260920000636", "0-03.500", "01-15_scene"
        val minSecRegex = Regex("""^(\d{1,2})[-_:](\d{1,2})(?:\.(\d{1,3}))?(?:[-_].*)?$""")
        val minSecMatch = minSecRegex.find(cleanName)
        if (minSecMatch != null) {
            val minutes = minSecMatch.groupValues[1].toLongOrNull() ?: 0L
            val seconds = minSecMatch.groupValues[2].toLongOrNull() ?: 0L
            val msStr = minSecMatch.groupValues.getOrNull(3)
            val milliseconds = if (!msStr.isNullOrEmpty()) {
                msStr.padEnd(3, '0').toLongOrNull() ?: 0L
            } else {
                0L
            }
            return (minutes * 60 + seconds) * 1000L + milliseconds
        }

        // Pattern 2: Pure seconds e.g. "15s" or "45_intro" or "04_scene"
        val pureSecRegex = Regex("""^(\d{1,4})(?:s|_|$)""")
        val pureSecMatch = pureSecRegex.find(cleanName)
        if (pureSecMatch != null) {
            val seconds = pureSecMatch.groupValues[1].toLongOrNull()
            if (seconds != null) {
                return seconds * 1000L
            }
        }

        // Fallback: 5 seconds per clip
        return fallbackIndex * 5000L
    }

    /**
     * Recalculates durations between sorted clips based on total project length
     */
    fun recalculateClipDurations(clips: List<StoryboardClip>, totalDurationMs: Long): List<StoryboardClip> {
        if (clips.isEmpty()) return emptyList()

        val sorted = clips.sortedBy { it.parsedStartMs }
        val result = mutableListOf<StoryboardClip>()

        for (i in sorted.indices) {
            val current = sorted[i]
            val nextStart = if (i + 1 < sorted.size) sorted[i + 1].parsedStartMs else totalDurationMs
            val duration = (nextStart - current.parsedStartMs).coerceAtLeast(1000L)
            result.add(current.copy(durationMs = duration))
        }

        return result
    }

    /**
     * Parse timestamped script (.srt, .vtt, or inline timestamps like [0:03] or NoteGPT ranges)
     */
    fun parseScript(script: String): List<CaptionItem> {
        if (script.isBlank()) return emptyList()
        val items = mutableListOf<CaptionItem>()

        // Check for inline bracketed markers like "[0:03] Hello world" or "[00:15.5] Next scene"
        val bracketRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d+))?\]\s*([^\[]+)""")
        val matches = bracketRegex.findAll(script).toList()

        if (matches.isNotEmpty()) {
            for (i in matches.indices) {
                val m = matches[i]
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val ms = m.groupValues[3].take(3).padEnd(3, '0').toLongOrNull() ?: 0L
                val start = (min * 60 + sec) * 1000L + ms
                val text = m.groupValues[4].trim()

                val nextStart = if (i + 1 < matches.size) {
                    val nextM = matches[i + 1]
                    val nMin = nextM.groupValues[1].toLongOrNull() ?: 0L
                    val nSec = nextM.groupValues[2].toLongOrNull() ?: 0L
                    (nMin * 60 + nSec) * 1000L
                } else {
                    start + 4000L
                }

                items.add(CaptionItem(startMs = start, endMs = nextStart, text = text))
            }
            return items
        }

        // Fallback: split by lines and assign 4s intervals
        val lines = script.lines().filter { it.isNotBlank() }
        var currentMs = 0L
        for (line in lines) {
            items.add(CaptionItem(startMs = currentMs, endMs = currentMs + 4000L, text = line.trim()))
            currentMs += 4000L
        }

        return items
    }
}
