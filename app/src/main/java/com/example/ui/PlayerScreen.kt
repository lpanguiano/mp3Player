package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.components.OscilloscopeWaveformView
import com.example.ui.components.PlaylistSheet
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.MintMuted
import com.example.ui.theme.MintSubtext
import com.example.ui.theme.MintWhite
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.PureBlack
import com.example.util.AudioUtil

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsState()
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()
    val savedPlaylists by viewModel.savedPlaylists.collectAsState()
    val showPlaylistSheet by viewModel.showPlaylistSheet.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val loadingState by viewModel.loadingState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Request notification permission on Android 13+ for background playback controls
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    // SAF Activity Result Launchers
    val openMp3Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.openAndPlaySingleAudio(it) }
    }

    val addMultipleMp3Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addMultipleAudios(uris)
        }
    }

    val exportLeoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.savePlaylistToSAF(it) }
    }

    val importLeoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadPlaylistFromSAF(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().background(PureBlack),
        containerColor = PureBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // TOP HEADER: Quick selection bar (Select MP3 / Select Playlist)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button to select MP3 file
                OutlinedButton(
                    onClick = { openMp3Launcher.launch(arrayOf("audio/mpeg", "audio/mp3", "audio/*")) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DarkCard,
                        contentColor = NeonGreen
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("select_mp3_button").height(40.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir MP3", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                // Button to open playlist / lists menu
                Button(
                    onClick = { viewModel.setShowPlaylistSheet(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkBorder,
                        contentColor = MintWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("open_playlist_button").height(40.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (currentPlaylist.tracks.isNotEmpty()) "Lista (${currentPlaylist.tracks.size})" else "Listas .leo",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // TOP SECTION: File Name and Playback Timers
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val trackTitle = playbackState.currentTrack?.title ?: "Ningún archivo seleccionado"
                Text(
                    text = trackTitle,
                    color = MintWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("current_track_name")
                )

                Spacer(Modifier.height(4.dp))

                // Time indicators and Track Position
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Current elapsed time
                    Text(
                        text = AudioUtil.formatTime(playbackState.currentPositionMs),
                        color = NeonGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("elapsed_time")
                    )

                    // Track index if part of a playlist
                    if (playbackState.totalTracks > 1 && playbackState.currentIndex >= 0) {
                        Text(
                            text = "[${playbackState.currentIndex + 1} / ${playbackState.totalTracks}]",
                            color = MintMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Total track duration
                    Text(
                        text = AudioUtil.formatTime(playbackState.durationMs),
                        color = MintSubtext,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("total_duration")
                    )
                }
            }

            // UPPER HALF: Waveform Audio Spectrum (matching attached user image)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.05f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val activeTrack = playbackState.currentTrack
                val playlistTrack = currentPlaylist.tracks.find {
                    it.id == activeTrack?.id || it.uriString == activeTrack?.uriString
                }
                val ch1 = if (!activeTrack?.waveformPoints.isNullOrEmpty()) {
                    activeTrack!!.waveformPoints
                } else {
                    playlistTrack?.waveformPoints ?: emptyList()
                }
                val ch2 = if (!activeTrack?.waveformPointsCh2.isNullOrEmpty()) {
                    activeTrack!!.waveformPointsCh2
                } else {
                    playlistTrack?.waveformPointsCh2 ?: emptyList()
                }

                OscilloscopeWaveformView(
                    waveformCh1 = ch1,
                    waveformCh2 = ch2,
                    currentPositionMs = playbackState.currentPositionMs,
                    durationMs = if (playbackState.durationMs > 0L) playbackState.durationMs else (playlistTrack?.durationMs ?: 0L),
                    isPlaying = playbackState.isPlaying,
                    onSeek = { targetMs -> viewModel.seekTo(targetMs) },
                    loopPointA = playbackState.loopPointA,
                    loopPointB = playbackState.loopPointB,
                    modifier = Modifier.fillMaxSize()
                )

                // Non-blocking compact indicator while waveform renders progressively from left to right
                if (loadingState.isAnalyzingSpectrum) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .border(1.dp, NeonGreen.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Espectro: ${(loadingState.progress * 100).toInt()}%",
                                color = NeonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { loadingState.progress },
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = NeonGreen,
                                trackColor = DarkCard
                            )
                        }
                    }
                } else if (loadingState.isLoading) {
                    // Blocking loading overlay (kept only for explicit blocking loads if ever needed)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.90f))
                            .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(0.92f)
                        ) {
                            Text(
                                text = if (loadingState.totalItems > 1) {
                                    "Cargando archivo (${loadingState.currentItem}/${loadingState.totalItems}):"
                                } else {
                                    "Cargando archivo:"
                                },
                                color = NeonGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = loadingState.fileName,
                                color = MintWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(14.dp))

                            LinearProgressIndicator(
                                progress = { loadingState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = NeonGreen,
                                trackColor = DarkCard
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Analizando audio...",
                                    color = MintMuted,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${(loadingState.progress * 100).toInt()}%",
                                    color = NeonGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // CONTROLS STRIP: A-B Loop & Speed Selector (0.75x, 1.0x, 1.5x, 2.0x)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ROW: A-B Loop Repeat
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkCard)
                        .border(
                            1.dp,
                            if (playbackState.loopPointA != null && playbackState.loopPointB != null) NeonGreen else DarkBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            tint = if (playbackState.loopPointA != null && playbackState.loopPointB != null) NeonGreen else MintSubtext,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Bucle A-B:",
                            color = MintSubtext,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Button Punto A
                        Button(
                            onClick = { viewModel.setLoopPointA() },
                            modifier = Modifier.height(32.dp).testTag("set_loop_a_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (playbackState.loopPointA != null) NeonGreen else DarkBorder,
                                contentColor = if (playbackState.loopPointA != null) PureBlack else MintWhite
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (playbackState.loopPointA != null) NeonGreen else DarkBorder
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            val textA = playbackState.loopPointA?.let { "A: ${AudioUtil.formatTime(it)}" } ?: "Punto A"
                            Text(textA, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Button Punto B
                        Button(
                            onClick = { viewModel.setLoopPointB() },
                            modifier = Modifier.height(32.dp).testTag("set_loop_b_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (playbackState.loopPointB != null) NeonGreen else DarkBorder,
                                contentColor = if (playbackState.loopPointB != null) PureBlack else MintWhite
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (playbackState.loopPointB != null) NeonGreen else DarkBorder
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            val textB = playbackState.loopPointB?.let { "B: ${AudioUtil.formatTime(it)}" } ?: "Punto B"
                            Text(textB, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Clear loop button if any point is set
                        if (playbackState.loopPointA != null || playbackState.loopPointB != null) {
                            IconButton(
                                onClick = { viewModel.clearLoop() },
                                modifier = Modifier.size(32.dp).testTag("clear_loop_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Quitar bucle", tint = MintWhite, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // ROW: Speed Selector (0.75x, 1.0x, 1.5x, 2.0x)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Velocidad:",
                            color = MintSubtext,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.75f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                            val isSelected = playbackState.playbackSpeed == speed
                            Button(
                                onClick = { viewModel.setPlaybackSpeed(speed) },
                                modifier = Modifier.height(30.dp).testTag("speed_${speed}x_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) NeonGreen else DarkBorder,
                                    contentColor = if (isSelected) PureBlack else MintWhite
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) NeonGreen else DarkBorder
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "${speed}x",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // LOWER HALF: Big, thumb-friendly buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.15f)
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // ROW 1: Audio Anterior and Siguiente Audio (Half-width buttons as requested!)
                // "Los botones audio anterior y siguiente audio seran de la mitad de ancho que los otros botones."
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Audio anterior (half-width button)
                    Button(
                        onClick = { viewModel.playPrevious() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("button_previous_audio"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkCard,
                            contentColor = MintWhite
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Audio anterior", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Ant.", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    // Spacer in the middle so the anterior and siguiente remain half the width of other buttons
                    Spacer(Modifier.weight(1.4f))

                    // Siguiente audio (half-width button)
                    Button(
                        onClick = { viewModel.playNext() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("button_next_audio"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkCard,
                            contentColor = MintWhite
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sig.", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Default.SkipNext, contentDescription = "Siguiente audio", modifier = Modifier.size(18.dp))
                    }
                }

                // ROW 2: Jump backward buttons (-20 seg & -2 seg) - Large buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Retroceder 20 seg
                    Button(
                        onClick = { viewModel.seekRelative(-20000L) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .testTag("button_rewind_20s"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkCard,
                            contentColor = MintWhite
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, DarkBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FastRewind, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("-20 SEG", fontSize = 16.sp, fontWeight = FontWeight.Black, color = MintWhite)
                                Text("Retroceder", fontSize = 10.sp, color = MintMuted)
                            }
                        }
                    }

                    // Retroceder 2 segundos
                    Button(
                        onClick = { viewModel.seekRelative(-2000L) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .testTag("button_rewind_2s"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkCard,
                            contentColor = MintWhite
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, DarkBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FastRewind, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("-2 SEG", fontSize = 16.sp, fontWeight = FontWeight.Black, color = MintWhite)
                                Text("Retroceder", fontSize = 10.sp, color = MintMuted)
                            }
                        }
                    }
                }

                // ROW 3: Central PAUSA / REPRODUCIR button - Extra large, very prominent and easy to click
                Button(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("button_play_pause"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (playbackState.isPlaying) DarkCard else NeonGreen,
                        contentColor = if (playbackState.isPlaying) NeonGreen else PureBlack
                    ),
                    border = if (playbackState.isPlaying) androidx.compose.foundation.BorderStroke(2.dp, NeonGreen) else null,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pausa" else "Reproducir",
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (playbackState.isPlaying) "PAUSA" else "REPRODUCIR",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // ROW 4: Jump forward buttons (+5 seg & +30 seg) - Large buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Adelantar 5 seg
                    Button(
                        onClick = { viewModel.seekRelative(5000L) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .testTag("button_forward_5s"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkCard,
                            contentColor = MintWhite
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, DarkBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("+5 SEG", fontSize = 16.sp, fontWeight = FontWeight.Black, color = MintWhite)
                                Text("Adelantar", fontSize = 10.sp, color = MintMuted)
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.FastForward, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(28.dp))
                        }
                    }

                    // Adelantar 30 seg
                    Button(
                        onClick = { viewModel.seekRelative(30000L) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .testTag("button_forward_30s"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkCard,
                            contentColor = MintWhite
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, DarkBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("+30 SEG", fontSize = 16.sp, fontWeight = FontWeight.Black, color = MintWhite)
                                Text("Adelantar", fontSize = 10.sp, color = MintMuted)
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.FastForward, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }

    // Modal sheet for .leo playlist management and editing
    if (showPlaylistSheet) {
        PlaylistSheet(
            playlist = currentPlaylist,
            currentTrackId = playbackState.currentTrack?.id,
            savedPlaylists = savedPlaylists,
            onDismiss = { viewModel.setShowPlaylistSheet(false) },
            onSelectTrack = { index -> viewModel.playTrackAtIndex(index) },
            onAddFiles = { addMultipleMp3Launcher.launch(arrayOf("audio/mpeg", "audio/mp3", "audio/*")) },
            onRemoveTrack = { index -> viewModel.removeTrack(index) },
            onMoveTrack = { from, to -> viewModel.moveTrack(from, to) },
            onRenamePlaylist = { newName -> viewModel.setPlaylistName(newName) },
            onSaveLocally = { viewModel.saveCurrentPlaylistLocally() },
            onExportLeo = {
                val cleanName = currentPlaylist.name
                    .removeSuffix(".json")
                    .removeSuffix(".leo")
                    .ifBlank { "MiLista" } + ".leo"
                exportLeoLauncher.launch(cleanName)
            },
            onImportLeo = { importLeoLauncher.launch(arrayOf("*/*")) },
            onLoadSavedPlaylist = { saved -> viewModel.loadSavedLocalPlaylist(saved) },
            onDeleteSavedPlaylist = { name -> viewModel.deleteLocalPlaylist(name) }
        )
    }
}
