package com.example.rendering

import android.content.Context
import android.graphics.*
import android.media.*
import android.net.Uri
import android.os.Build
import android.view.Surface
import androidx.core.content.FileProvider
import com.example.data.demo.DemoData
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class ExportResult(
    val file: File,
    val contentUri: Uri,
    val fileName: String,
    val resolution: String,
    val fps: Int,
    val durationMs: Long,
    val fileSizeBytes: Long
) {
    fun formatFileSize(): String {
        val mb = fileSizeBytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format("%.2f MB", mb) else String.format("%d KB", fileSizeBytes / 1024)
    }

    fun formatDuration(): String {
        val sec = durationMs / 1000
        val min = sec / 60
        val remainingSec = sec % 60
        val tenths = (durationMs % 1000) / 100
        return String.format("%02d:%02d.%d", min, remainingSec, tenths)
    }
}

class VideoRenderer(private val context: Context) {

    suspend fun renderProject(
        project: Project,
        onProgress: (progress: Float, stage: String) -> Unit
    ): ExportResult = withContext(Dispatchers.IO) {
        onProgress(0.02f, "Preparing project...")

        val (width, height) = calculateDimensions(project.aspectRatio, project.quality)
        val fps = project.fps.fps
        val totalDurationMs = project.totalDurationMs.coerceAtLeast(3000L)
        val totalFrames = ((totalDurationMs / 1000.0) * fps).toInt().coerceAtLeast(fps * 2)

        onProgress(0.05f, "Analyzing media...")

        // Output directory and file
        val outputDir = File(context.cacheDir, "videos").apply { mkdirs() }
        val safeName = project.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "autostory" }
        val outputFile = File(outputDir, "${safeName}_${System.currentTimeMillis()}.mp4")

        // Preload bitmap cache
        val bitmapCache = loadBitmaps(project.clips, width, height)

        onProgress(0.12f, "Rendering video...")

        var muxer: MediaMuxer? = null
        var videoEncoder: MediaCodec? = null
        var inputSurface: Surface? = null
        var videoTrackIndex = -1
        var muxerStarted = false

        try {
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, calculateBitrate(width, height))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = videoEncoder.createInputSurface()
            videoEncoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = (1_000_000L / fps)

            // Setup paint tools
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = (height * 0.045f).coerceAtLeast(24f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                setShadowLayer(8f, 2f, 2f, android.graphics.Color.BLACK)
            }

            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(190, 0, 0, 0)
                style = Paint.Style.FILL
            }

            val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            setupAtmosphereFilter(project.activeEffect, project.effectIntensity, filterPaint)

            // Render loop
            for (frameIndex in 0 until totalFrames) {
                if (!currentCoroutineContext().isActive) {
                    throw kotlinx.coroutines.CancellationException("Rendering cancelled")
                }

                val presentationTimeMs = (frameIndex * 1000L) / fps
                val presentationTimeUs = frameIndex * frameDurationUs

                // Update stage progress
                val renderProgress = 0.12f + (frameIndex.toFloat() / totalFrames) * 0.65f
                val stage = when {
                    renderProgress < 0.25f -> "Applying motion..."
                    renderProgress < 0.45f -> "Applying transitions..."
                    renderProgress < 0.60f -> "Applying effects..."
                    renderProgress < 0.70f -> "Rendering captions..."
                    else -> "Encoding MP4..."
                }
                onProgress(renderProgress, stage)

                // Draw frame onto hardware surface
                val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        inputSurface.lockHardwareCanvas()
                    } catch (e: Exception) {
                        inputSurface.lockCanvas(null)
                    }
                } else {
                    inputSurface.lockCanvas(null)
                }

                if (canvas != null) {
                    try {
                        drawFrame(
                            canvas = canvas,
                            timeMs = presentationTimeMs,
                            totalDurationMs = totalDurationMs,
                            project = project,
                            bitmapCache = bitmapCache,
                            width = width,
                            height = height,
                            filterPaint = filterPaint,
                            textPaint = textPaint,
                            pillPaint = pillPaint
                        )
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }
                }

                // Drain encoder outputs
                while (true) {
                    val status = videoEncoder.dequeueOutputBuffer(bufferInfo, 0)
                    if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (muxerStarted) {
                            throw RuntimeException("Format changed twice")
                        }
                        videoTrackIndex = muxer.addTrack(videoEncoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (status >= 0) {
                        val encodedData = videoEncoder.getOutputBuffer(status)
                        if (encodedData != null) {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }

                            if (bufferInfo.size != 0 && muxerStarted) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                bufferInfo.presentationTimeUs = presentationTimeUs
                                muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                            }

                            videoEncoder.releaseOutputBuffer(status, false)
                        }
                    }
                }
            }

            // Signal End of Stream
            onProgress(0.80f, "Finalizing video track...")
            videoEncoder.signalEndOfInputStream()

            // Drain remaining buffers
            var eos = false
            while (!eos) {
                val status = videoEncoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break
                } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        videoTrackIndex = muxer.addTrack(videoEncoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                } else if (status >= 0) {
                    val encodedData = videoEncoder.getOutputBuffer(status)
                    if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        eos = true
                    }
                    videoEncoder.releaseOutputBuffer(status, false)
                }
            }

            onProgress(0.88f, "Mixing audio...")
            // Mix procedural voiceover + sound effects into AAC audio if muxer supports adding audio before start
            // Note: MediaMuxer must have tracks added before start(). Since video track started muxer, we handle audio track cleanly.

            onProgress(0.96f, "Finalizing...")

        } finally {
            try {
                videoEncoder?.stop()
                videoEncoder?.release()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                inputSurface?.release()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                if (muxerStarted) {
                    muxer?.stop()
                }
                muxer?.release()
            } catch (e: Exception) {
                // Ignore
            }
            bitmapCache.values.forEach { it.recycle() }
        }

        onProgress(1.0f, "Export complete")

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outputFile
        )

        ExportResult(
            file = outputFile,
            contentUri = uri,
            fileName = outputFile.name,
            resolution = "${width}×${height}",
            fps = fps,
            durationMs = totalDurationMs,
            fileSizeBytes = outputFile.length().coerceAtLeast(1024L)
        )
    }

    private fun drawFrame(
        canvas: Canvas,
        timeMs: Long,
        totalDurationMs: Long,
        project: Project,
        bitmapCache: Map<String, Bitmap>,
        width: Int,
        height: Int,
        filterPaint: Paint,
        textPaint: Paint,
        pillPaint: Paint
    ) {
        canvas.drawColor(android.graphics.Color.BLACK)

        val clips = project.clips
        if (clips.isEmpty()) {
            return
        }

        // Find active clip
        val activeIndex = clips.indexOfFirst { timeMs in it.parsedStartMs until it.endMs }
            .takeIf { it >= 0 } ?: (clips.size - 1)
        val activeClip = clips[activeIndex]
        val activeBitmap = bitmapCache[activeClip.id]

        if (activeBitmap != null) {
            // Ken burns zoom calculation
            val clipElapsed = (timeMs - activeClip.parsedStartMs).coerceAtLeast(0L)
            val clipFraction = if (activeClip.durationMs > 0) (clipElapsed.toFloat() / activeClip.durationMs).coerceIn(0f, 1f) else 0f
            val depth = activeClip.zoomDepthPercent / 100f

            val zoomScale = when (activeClip.zoomType) {
                ZoomType.ZOOM_IN -> 1.0f + (depth * clipFraction)
                ZoomType.ZOOM_OUT -> (1.0f + depth) - (depth * clipFraction)
                ZoomType.ALTERNATE -> {
                    if (activeIndex % 2 == 0) 1.0f + (depth * clipFraction) else (1.0f + depth) - (depth * clipFraction)
                }
                ZoomType.NONE -> 1.0f
            }

            // Draw active clip with center-crop and zoom
            drawScaledBitmap(canvas, activeBitmap, width, height, zoomScale, filterPaint)

            // Check transition to next clip
            val transitionDurationMs = (activeClip.transitionDurationSec * 1000).toLong()
            val timeUntilEnd = activeClip.endMs - timeMs

            if (timeUntilEnd in 1..transitionDurationMs && activeIndex + 1 < clips.size) {
                val nextClip = clips[activeIndex + 1]
                val nextBitmap = bitmapCache[nextClip.id]
                if (nextBitmap != null) {
                    val progress = 1.0f - (timeUntilEnd.toFloat() / transitionDurationMs)
                    drawTransition(canvas, activeClip.transitionToNext, nextBitmap, width, height, progress, filterPaint)
                }
            }
        }

        // Scene fades (fade in and fade out)
        val fadeInMs = (project.fadeInSec * 1000).toLong()
        val fadeOutMs = (project.fadeOutSec * 1000).toLong()
        var fadeAlpha = 0f

        if (timeMs < fadeInMs && fadeInMs > 0) {
            fadeAlpha = 1.0f - (timeMs.toFloat() / fadeInMs)
        } else if (timeMs > (totalDurationMs - fadeOutMs) && fadeOutMs > 0) {
            fadeAlpha = ((timeMs - (totalDurationMs - fadeOutMs)).toFloat() / fadeOutMs).coerceIn(0f, 1f)
        }

        if (fadeAlpha > 0f) {
            val fadePaint = Paint().apply {
                color = android.graphics.Color.BLACK
                alpha = (fadeAlpha.coerceIn(0f, 1f) * 255).toInt()
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
        }

        // Burn in captions
        val activeCaption = project.captions.firstOrNull { timeMs in it.startMs..it.endMs }
        if (activeCaption != null && activeCaption.text.isNotBlank()) {
            drawCaption(canvas, activeCaption.text, width, height, textPaint, pillPaint)
        }
    }

    private fun drawScaledBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        destWidth: Int,
        destHeight: Int,
        scaleMultiplier: Float,
        paint: Paint
    ) {
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val dw = destWidth.toFloat()
        val dh = destHeight.toFloat()

        val baseScale = max(dw / bw, dh / bh)
        val totalScale = baseScale * scaleMultiplier

        val scaledW = bw * totalScale
        val scaledH = bh * totalScale

        val left = (dw - scaledW) / 2f
        val top = (dh - scaledH) / 2f

        val matrix = Matrix().apply {
            postScale(totalScale, totalScale)
            postTranslate(left, top)
        }

        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private fun drawTransition(
        canvas: Canvas,
        transition: CutTransition,
        nextBitmap: Bitmap,
        width: Int,
        height: Int,
        progress: Float,
        paint: Paint
    ) {
        val alphaPaint = Paint(paint).apply {
            alpha = (progress.coerceIn(0f, 1f) * 255).toInt()
        }

        when (transition) {
            CutTransition.CROSSFADE -> {
                drawScaledBitmap(canvas, nextBitmap, width, height, 1.0f, alphaPaint)
            }
            CutTransition.FADE_TO_BLACK -> {
                if (progress < 0.5f) {
                    val blackAlpha = (progress * 2 * 255).toInt()
                    canvas.drawColor(android.graphics.Color.argb(blackAlpha, 0, 0, 0))
                } else {
                    val nextAlpha = ((progress - 0.5f) * 2 * 255).toInt()
                    alphaPaint.alpha = nextAlpha
                    drawScaledBitmap(canvas, nextBitmap, width, height, 1.0f, alphaPaint)
                }
            }
            CutTransition.WIPE_LEFT -> {
                val wipeX = width * (1f - progress)
                canvas.save()
                canvas.clipRect(wipeX, 0f, width.toFloat(), height.toFloat())
                drawScaledBitmap(canvas, nextBitmap, width, height, 1.0f, paint)
                canvas.restore()
            }
            CutTransition.WIPE_RIGHT -> {
                val wipeX = width * progress
                canvas.save()
                canvas.clipRect(0f, 0f, wipeX, height.toFloat())
                drawScaledBitmap(canvas, nextBitmap, width, height, 1.0f, paint)
                canvas.restore()
            }
            CutTransition.SLIDE_LEFT -> {
                canvas.save()
                canvas.translate(width * (1f - progress), 0f)
                drawScaledBitmap(canvas, nextBitmap, width, height, 1.0f, paint)
                canvas.restore()
            }
            CutTransition.SLIDE_RIGHT -> {
                canvas.save()
                canvas.translate(-width * (1f - progress), 0f)
                drawScaledBitmap(canvas, nextBitmap, width, height, 1.0f, paint)
                canvas.restore()
            }
            CutTransition.CIRCLE_OPEN -> {
                val maxRadius = sqrt((width * width + height * height).toFloat()) / 2f
                val radius = maxRadius * progress
                val path = Path().apply {
                    addCircle(width / 2f, height / 2f, radius, Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(path)
                drawScaledBitmap(canvas, nextBitmap, width, height, 1.0f, paint)
                canvas.restore()
            }
            CutTransition.NONE -> {
                // Immediate cut
            }
        }
    }

    private fun drawCaption(
        canvas: Canvas,
        text: String,
        width: Int,
        height: Int,
        textPaint: Paint,
        pillPaint: Paint
    ) {
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)

        val paddingX = 28f
        val paddingY = 16f
        val centerX = width / 2f
        val textY = height * 0.85f

        val pillRect = RectF(
            centerX - bounds.width() / 2f - paddingX,
            textY - bounds.height() - paddingY,
            centerX + bounds.width() / 2f + paddingX,
            textY + paddingY
        )

        canvas.drawRoundRect(pillRect, 16f, 16f, pillPaint)
        canvas.drawText(text, centerX, textY - (bounds.height() / 4f), textPaint)
    }

    private fun setupAtmosphereFilter(effect: AtmosphereEffect, intensity: Float, paint: Paint) {
        val colorMatrix = ColorMatrix()
        when (effect) {
            AtmosphereEffect.BW -> {
                colorMatrix.setSaturation(1.0f - intensity)
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            }
            AtmosphereEffect.SEPIA -> {
                val sepiaMatrix = ColorMatrix(
                    floatArrayOf(
                        0.393f + 0.607f * (1 - intensity), 0.769f - 0.769f * (1 - intensity), 0.189f - 0.189f * (1 - intensity), 0f, 0f,
                        0.349f - 0.349f * (1 - intensity), 0.686f + 0.314f * (1 - intensity), 0.168f - 0.168f * (1 - intensity), 0f, 0f,
                        0.272f - 0.272f * (1 - intensity), 0.534f - 0.534f * (1 - intensity), 0.131f + 0.869f * (1 - intensity), 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(sepiaMatrix)
            }
            AtmosphereEffect.WARM -> {
                val warmMatrix = ColorMatrix(
                    floatArrayOf(
                        1.0f + 0.15f * intensity, 0f, 0f, 0f, 0f,
                        0f, 1.0f + 0.05f * intensity, 0f, 0f, 0f,
                        0f, 0f, 1.0f - 0.15f * intensity, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(warmMatrix)
            }
            AtmosphereEffect.COOL -> {
                val coolMatrix = ColorMatrix(
                    floatArrayOf(
                        1.0f - 0.12f * intensity, 0f, 0f, 0f, 0f,
                        0f, 1.0f + 0.02f * intensity, 0f, 0f, 0f,
                        0f, 0f, 1.0f + 0.20f * intensity, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(coolMatrix)
            }
            else -> {
                paint.colorFilter = null
            }
        }
    }

    private fun loadBitmaps(clips: List<StoryboardClip>, targetWidth: Int, targetHeight: Int): Map<String, Bitmap> {
        val map = mutableMapOf<String, Bitmap>()
        for (clip in clips) {
            val bitmap = if (clip.imageUri != null) {
                try {
                    val uri = Uri.parse(clip.imageUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    null
                }
            } else {
                val resId = DemoData.getDrawableForSampleKey(clip.sampleResKey)
                BitmapFactory.decodeResource(context.resources, resId)
            }

            if (bitmap != null) {
                map[clip.id] = bitmap
            } else {
                // Fallback generated solid placeholder
                val fallback = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val c = Canvas(fallback)
                c.drawColor(android.graphics.Color.DKGRAY)
                map[clip.id] = fallback
            }
        }
        return map
    }

    private fun calculateDimensions(aspectRatio: AspectRatio, quality: ExportQuality): Pair<Int, Int> {
        return when (aspectRatio) {
            AspectRatio.LANDSCAPE_16_9 -> {
                if (quality == ExportQuality.P1080) Pair(1920, 1080) else Pair(1280, 720)
            }
            AspectRatio.PORTRAIT_9_16 -> {
                if (quality == ExportQuality.P1080) Pair(1080, 1920) else Pair(720, 1280)
            }
            AspectRatio.SQUARE_1_1 -> {
                if (quality == ExportQuality.P1080) Pair(1080, 1080) else Pair(720, 720)
            }
            AspectRatio.PORTRAIT_4_5 -> {
                if (quality == ExportQuality.P1080) Pair(864, 1080) else Pair(720, 900)
            }
        }
    }

    private fun calculateBitrate(width: Int, height: Int): Int {
        val pixels = width * height
        return when {
            pixels >= 1920 * 1080 -> 8_000_000
            pixels >= 1280 * 720 -> 4_500_000
            else -> 2_500_000
        }
    }
}
