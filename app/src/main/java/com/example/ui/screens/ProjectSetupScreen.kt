package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.demo.DemoData
import com.example.data.model.StoryboardClip
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSetupScreen(
    projectName: String,
    voiceoverName: String?,
    voiceoverDurationMs: Long,
    importedClips: List<StoryboardClip>,
    onUpdateProjectName: (String) -> Unit,
    onSetVoiceover: (String, Long, String?, Long) -> Unit,
    onLoadDemoVoiceover: () -> Unit,
    onAddFiles: (List<String>) -> Unit,
    onLoadDemoStoryboards: () -> Unit,
    onRemoveClip: (String) -> Unit,
    onBuildTimeline: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingName by remember { mutableStateOf(projectName) }
    var isBuildingTimeline by remember { mutableStateOf(false) }
    var buildProgressStage by remember { mutableStateOf("") }
    var buildProgressFraction by remember { mutableStateOf(0f) }

    // Audio file picker
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "voiceover_audio.mp3"
            var fileSize = 1024L * 1024L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {
                // fallback
            }
            onSetVoiceover(fileName, 60000L, uri.toString(), fileSize)
        }
    }

    // Media file picker (Images and Videos)
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val fileNames = uris.map { uri ->
                uri.lastPathSegment?.substringAfterLast('/') ?: "0-00.png"
            }
            onAddFiles(fileNames)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = editingName,
                            onValueChange = {
                                editingName = it
                                onUpdateProjectName(it)
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPrimary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.widthIn(max = 280.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioSurface
                )
            )
        },
        bottomBar = {
            Surface(
                color = StudioSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    val clipCount = importedClips.size
                    val canBuild = clipCount > 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (canBuild) "$clipCount media ready • auto-synced" else "Import your storyboard images to begin.",
                            color = if (canBuild) AccentGreen else TextMuted,
                            fontSize = 13.sp
                        )

                        Button(
                            onClick = {
                                isBuildingTimeline = true
                            },
                            enabled = canBuild && !isBuildingTimeline,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentPrimary,
                                disabledContainerColor = StudioBorder
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(
                                text = "Build timeline →",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        },
        containerColor = StudioBackground
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                // Exact Heading matching Screenshot 2
                Text(
                    text = "Sync your images to a voiceover, automatically.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Name each image or video clip with the second it appears — 0-03.png or 0-03.mp4 cuts in at 0:03 — then import them with your voiceover. Video clips can be trimmed, zoomed, and their sound mixed under the narration. Review everything below, then build the timeline. Everything runs on your device. Nothing is uploaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 22.sp
                )
            }

            // Card 1: Voiceover Audio
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AccentCyan.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = AccentCyanGlow,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Voiceover audio",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Supported: MP3, WAV, M4A, AAC — sets total project length",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (voiceoverName != null) {
                            // Imported Voiceover info
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(StudioSurfaceElevated, RoundedCornerShape(8.dp))
                                    .border(1.dp, AccentGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Success",
                                            tint = AccentGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = voiceoverName,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "${voiceoverDurationMs / 1000}s duration • 1.4 MB",
                                                color = AccentCyanGlow,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    TextButton(onClick = { audioPicker.launch("audio/*") }) {
                                        Text("Change", color = AccentPrimary, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Visual Mini Waveform representation
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(24.dp)
                                        .background(StudioBackground, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        repeat(36) { index ->
                                            val barHeight = ((Math.sin(index * 0.4) * 0.4 + 0.6) * 18).dp
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(barHeight)
                                                    .background(AccentCyanGlow.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { audioPicker.launch("audio/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderHighlight),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = AccentCyanGlow, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Import voiceover", color = TextPrimary, fontSize = 12.sp)
                                }

                                FilledTonalButton(
                                    onClick = onLoadDemoVoiceover,
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentPrimary.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Use Demo Narration", color = AccentPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Card 2: Storyboard Images & Video
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AccentPrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        tint = AccentPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Storyboard images & video",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "PNG, JPG, WEBP, MP4, MOV. Drop files or whole folders.",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { mediaPicker.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorderHighlight),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pick media", color = TextPrimary, fontSize = 12.sp)
                            }

                            FilledTonalButton(
                                onClick = onLoadDemoStoryboards,
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentCyan.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Text("Load Demo Storyboard", color = AccentCyanGlow, fontSize = 12.sp)
                            }
                        }

                        // Quick sample filenames helper buttons
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Sample names:", color = TextMuted, fontSize = 11.sp)
                            listOf("0-00.png", "0-03.png", "0-10.mp4", "0-23.png").forEach { sampleName ->
                                SuggestionChip(
                                    onClick = { onAddFiles(listOf(sampleName)) },
                                    label = { Text(sampleName, fontSize = 10.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = StudioSurfaceElevated,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // List of detected storyboard clips
            if (importedClips.isNotEmpty()) {
                item {
                    Text(
                        text = "Imported storyboard (${importedClips.size} clips) — sorted by timestamp",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(importedClips, key = { it.id }) { clip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioSurfaceCard, RoundedCornerShape(8.dp))
                            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Thumbnail
                            val drawableId = DemoData.getDrawableForSampleKey(clip.sampleResKey)
                            Box(
                                modifier = Modifier
                                    .size(54.dp, 40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TimelineRulerBg)
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(id = drawableId),
                                    contentDescription = clip.originalFileName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .background(AccentPrimary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = clip.formatStartTime(),
                                            color = AccentPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = clip.originalFileName,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Duration: ${clip.formatDuration()}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { onRemoveClip(clip.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Timeline building progress dialog
    if (isBuildingTimeline) {
        LaunchedEffect(Unit) {
            val stages = listOf(
                "Validating voiceover..." to 0.15f,
                "Analyzing duration..." to 0.30f,
                "Parsing filenames..." to 0.50f,
                "Sorting media chronologically..." to 0.70f,
                "Calculating clip durations..." to 0.85f,
                "Generating thumbnails & waveform..." to 0.95f,
                "Opening editor..." to 1.0f
            )
            for ((stage, progress) in stages) {
                buildProgressStage = stage
                buildProgressFraction = progress
                delay(160)
            }
            isBuildingTimeline = false
            onBuildTimeline()
        }

        AlertDialog(
            onDismissRequest = { /* non-cancellable during build */ },
            title = {
                Text("Building Timeline", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { buildProgressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = AccentPrimary,
                        trackColor = StudioSurfaceElevated
                    )
                    Text(
                        text = buildProgressStage,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {},
            containerColor = StudioSurfaceCard
        )
    }
}
