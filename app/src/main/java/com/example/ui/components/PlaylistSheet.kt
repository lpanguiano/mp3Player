package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrack
import com.example.model.LeoPlaylist
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MintMuted
import com.example.ui.theme.MintSubtext
import com.example.ui.theme.MintWhite
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.PureBlack
import com.example.util.AudioUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSheet(
    playlist: LeoPlaylist,
    currentTrackId: String?,
    savedPlaylists: List<LeoPlaylist>,
    onDismiss: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onAddFiles: () -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onSaveLocally: () -> Unit,
    onExportLeo: () -> Unit,
    onImportLeo: () -> Unit,
    onLoadSavedPlaylist: (LeoPlaylist) -> Unit,
    onDeleteSavedPlaylist: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isEditingName by remember { mutableStateOf(false) }
    var tempName by remember(playlist.name) { mutableStateOf(playlist.name) }
    var showSavedList by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PureBlack,
        dragHandle = null,
        modifier = Modifier.testTag("playlist_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .background(PureBlack)
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isEditingName) {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        modifier = Modifier.weight(1f).testTag("playlist_name_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = MintWhite,
                            unfocusedTextColor = MintWhite,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onRenamePlaylist(tempName)
                            isEditingName = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = PureBlack),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Column(modifier = Modifier.weight(1f).clickable { isEditingName = true }) {
                        Text(
                            text = playlist.name,
                            color = NeonGreen,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${playlist.tracks.size} pistas · Toca para renombrar",
                            color = MintMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_sheet_btn")) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MintWhite)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons row: Add MP3s, Save .leo, Open .leo, Saved
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddFiles,
                    modifier = Modifier.weight(1f).height(42.dp).testTag("sheet_add_mp3_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard, contentColor = NeonGreen),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("+ MP3", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onExportLeo,
                    modifier = Modifier.weight(1f).height(42.dp).testTag("sheet_export_leo_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard, contentColor = MintWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(".leo", fontSize = 13.sp)
                }

                Button(
                    onClick = onImportLeo,
                    modifier = Modifier.weight(1f).height(42.dp).testTag("sheet_import_leo_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard, contentColor = MintWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Abrir", fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showSavedList = !showSavedList },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (showSavedList) NeonGreen else MintSubtext),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (showSavedList) NeonGreen else DarkBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(if (showSavedList) "Ver Lista Actual" else "Listas Guardadas (${savedPlaylists.size})", fontSize = 12.sp)
                }

                Button(
                    onClick = onSaveLocally,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBorder, contentColor = MintWhite),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Guardar Copia", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Body: either Saved playlists or Current playlist items
            if (showSavedList) {
                Text("Listas guardadas en el dispositivo:", color = MintSubtext, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                if (savedPlaylists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No hay listas guardadas aún", color = MintMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(savedPlaylists) { _, saved ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkCard)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        onLoadSavedPlaylist(saved)
                                        showSavedList = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(saved.name, color = MintWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${saved.tracks.size} pistas · Formato .leo", color = MintMuted, fontSize = 12.sp)
                                }
                                IconButton(onClick = { onDeleteSavedPlaylist(saved.name) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = MintMuted)
                                }
                            }
                        }
                    }
                }
            } else {
                if (playlist.tracks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Lista vacía", color = MintWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Presiona '+ MP3' para agregar canciones", color = MintMuted, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(playlist.tracks, key = { _, item -> item.id }) { index, track ->
                            val isSelected = track.id == currentTrackId
                            PlaylistItemRow(
                                index = index,
                                track = track,
                                isSelected = isSelected,
                                isFirst = index == 0,
                                isLast = index == playlist.tracks.lastIndex,
                                onPlay = { onSelectTrack(index) },
                                onMoveUp = { onMoveTrack(index, index - 1) },
                                onMoveDown = { onMoveTrack(index, index + 1) },
                                onDelete = { onRemoveTrack(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistItemRow(
    index: Int,
    track: AudioTrack,
    isSelected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) DarkBorder else DarkCard)
            .border(1.dp, if (isSelected) NeonGreen else DarkBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number or play icon
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Reproduciendo", tint = NeonGreen)
            } else {
                Text("${index + 1}", color = MintMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.width(6.dp))

        // Title and duration
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isSelected) NeonGreen else MintWhite,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (track.durationMs > 0) {
                Text(
                    text = AudioUtil.formatTime(track.durationMs),
                    color = MintMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Reorder buttons
        IconButton(
            onClick = onMoveUp,
            enabled = !isFirst,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.ArrowUpward,
                contentDescription = "Subir",
                tint = if (isFirst) DarkBorder else MintSubtext,
                modifier = Modifier.size(16.dp)
            )
        }

        IconButton(
            onClick = onMoveDown,
            enabled = !isLast,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.ArrowDownward,
                contentDescription = "Bajar",
                tint = if (isLast) DarkBorder else MintSubtext,
                modifier = Modifier.size(16.dp)
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Eliminar de lista",
                tint = MintMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
