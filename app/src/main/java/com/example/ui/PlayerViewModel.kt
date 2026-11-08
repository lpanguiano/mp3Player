package com.example.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LeoPlaylistManager
import com.example.model.AudioTrack
import com.example.model.LeoPlaylist
import com.example.playback.AudioPlayerService
import com.example.playback.PlaybackState
import com.example.util.AudioUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var playerService: AudioPlayerService? = null
    private var isBound = false

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPlaylist = MutableStateFlow(LeoPlaylist("Lista Actual"))
    val currentPlaylist: StateFlow<LeoPlaylist> = _currentPlaylist.asStateFlow()

    private val _savedPlaylists = MutableStateFlow<List<LeoPlaylist>>(emptyList())
    val savedPlaylists: StateFlow<List<LeoPlaylist>> = _savedPlaylists.asStateFlow()

    private val _showPlaylistSheet = MutableStateFlow(false)
    val showPlaylistSheet: StateFlow<Boolean> = _showPlaylistSheet.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AudioPlayerService.LocalBinder
            playerService = binder?.getService()
            isBound = true

            playerService?.let { s ->
                val activeTracks = s.getCurrentPlaylist()
                if (activeTracks.isNotEmpty()) {
                    _currentPlaylist.value = LeoPlaylist(
                        name = s.getCurrentPlaylistName(),
                        tracks = activeTracks
                    )
                }

                viewModelScope.launch {
                    s.playbackState.collectLatest { state ->
                        _playbackState.value = state
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerService = null
            isBound = false
        }
    }

    init {
        bindAudioService()
        refreshSavedPlaylists()
    }

    private fun bindAudioService() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioPlayerService::class.java)
        context.startService(intent) // Ensure service lifecycle runs
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun openAndPlaySingleAudio(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                takePersistablePermission(context, uri)
                val track = AudioUtil.resolveTrackFromUri(context, uri)
                val newPlaylist = LeoPlaylist(
                    name = track.title,
                    tracks = listOf(track)
                )
                _currentPlaylist.value = newPlaylist
                playerService?.setPlaylistAndPlay(listOf(track), 0)
                _statusMessage.value = "Reproduciendo: ${track.title}"
            } catch (e: Exception) {
                _statusMessage.value = "Error al abrir audio: ${e.localizedMessage}"
            }
        }
    }

    fun addMultipleAudios(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            val newTracks = mutableListOf<AudioTrack>()
            for (uri in uris) {
                takePersistablePermission(context, uri)
                val track = AudioUtil.resolveTrackFromUri(context, uri)
                newTracks.add(track)
            }

            val updatedTracks = _currentPlaylist.value.tracks + newTracks
            val updated = _currentPlaylist.value.copy(tracks = updatedTracks)
            _currentPlaylist.value = updated

            // If nothing is playing, start from first newly added track
            if (!_playbackState.value.isPlaying && _playbackState.value.currentTrack == null && updatedTracks.isNotEmpty()) {
                val startIndex = (_currentPlaylist.value.tracks.size - newTracks.size).coerceAtLeast(0)
                playerService?.setPlaylistAndPlay(updatedTracks, startIndex)
            }
            _statusMessage.value = "${newTracks.size} archivos agregados"
        }
    }

    fun playTrackAtIndex(index: Int) {
        val tracks = _currentPlaylist.value.tracks
        if (index in tracks.indices) {
            playerService?.setPlaylistAndPlay(tracks, index)
        }
    }

    fun togglePlayPause() {
        playerService?.togglePlayPause()
    }

    fun seekRelative(deltaMs: Long) {
        playerService?.seekRelative(deltaMs)
    }

    fun seekTo(positionMs: Long) {
        playerService?.seekTo(positionMs)
    }

    fun playNext() {
        playerService?.playNext()
    }

    fun playPrevious() {
        playerService?.playPrevious()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerService?.setPlaybackSpeed(speed)
        _statusMessage.value = "Velocidad: ${speed}x"
    }

    fun setLoopPointA() {
        playerService?.setLoopPointA()
        val pos = _playbackState.value.currentPositionMs
        _statusMessage.value = "Punto A marcado en ${AudioUtil.formatTime(pos)}"
    }

    fun setLoopPointB() {
        playerService?.setLoopPointB()
        val pos = _playbackState.value.currentPositionMs
        _statusMessage.value = "Punto B marcado en ${AudioUtil.formatTime(pos)} · Bucle activo"
    }

    fun clearLoop() {
        playerService?.clearLoop()
        _statusMessage.value = "Bucle A-B desactivado"
    }

    fun toggleLoopAB() {
        playerService?.toggleLoopAB()
    }

    // Playlist editing actions
    fun removeTrack(index: Int) {
        val tracks = _currentPlaylist.value.tracks.toMutableList()
        if (index in tracks.indices) {
            val removed = tracks.removeAt(index)
            _currentPlaylist.value = _currentPlaylist.value.copy(tracks = tracks)
            _statusMessage.value = "Eliminado: ${removed.title}"
        }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val tracks = _currentPlaylist.value.tracks.toMutableList()
        if (fromIndex in tracks.indices && toIndex in tracks.indices && fromIndex != toIndex) {
            val item = tracks.removeAt(fromIndex)
            tracks.add(toIndex, item)
            _currentPlaylist.value = _currentPlaylist.value.copy(tracks = tracks)
        }
    }

    fun clearPlaylist() {
        _currentPlaylist.value = LeoPlaylist("Nueva Lista")
        playerService?.stopPlayback()
        _statusMessage.value = "Lista vaciada"
    }

    fun setPlaylistName(newName: String) {
        _currentPlaylist.value = _currentPlaylist.value.copy(name = newName.ifBlank { "Mi Lista" })
    }

    fun saveCurrentPlaylistLocally() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val success = LeoPlaylistManager.saveLocally(context, _currentPlaylist.value)
            if (success) {
                _statusMessage.value = "Lista '${_currentPlaylist.value.name}' guardada"
                refreshSavedPlaylists()
            } else {
                _statusMessage.value = "Error al guardar la lista"
            }
        }
    }

    fun savePlaylistToSAF(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val success = LeoPlaylistManager.saveToUri(context, uri, _currentPlaylist.value)
            if (success) {
                _statusMessage.value = "Archivo .leo exportado con éxito"
            } else {
                _statusMessage.value = "Error al exportar archivo .leo"
            }
        }
    }

    fun loadPlaylistFromSAF(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            takePersistablePermission(context, uri)
            val playlist = LeoPlaylistManager.loadFromUri(context, uri)
            if (playlist != null) {
                loadPlaylist(playlist)
                _statusMessage.value = "Lista cargada: ${playlist.name}"
            } else {
                _statusMessage.value = "Error al leer archivo .leo"
            }
        }
    }

    fun loadPlaylist(playlist: LeoPlaylist) {
        _currentPlaylist.value = playlist
        if (playlist.tracks.isNotEmpty()) {
            playerService?.setPlaylistAndPlay(playlist.tracks, 0)
        }
    }

    fun deleteLocalPlaylist(name: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            LeoPlaylistManager.deleteLocal(context, name)
            refreshSavedPlaylists()
            _statusMessage.value = "Lista eliminada"
        }
    }

    fun refreshSavedPlaylists() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            _savedPlaylists.value = LeoPlaylistManager.listLocalPlaylists(context)
        }
    }

    fun setShowPlaylistSheet(show: Boolean) {
        _showPlaylistSheet.value = show
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun takePersistablePermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: Exception) {}
            isBound = false
        }
    }
}
