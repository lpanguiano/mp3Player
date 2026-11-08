package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.ui.PlayerScreen
import com.example.ui.PlayerViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntentData(intent)

        setContent {
            MyApplicationTheme {
                PlayerScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentData(intent)
    }

    private fun handleIntentData(intent: Intent?) {
        val uri: Uri? = intent?.data
        if (uri != null) {
            intent.data = null // Clear to avoid re-triggering on reopen
            val path = uri.toString().lowercase()
            if (path.endsWith(".leo")) {
                viewModel.loadPlaylistFromSAF(uri)
            } else {
                viewModel.openAndPlaySingleAudio(uri)
            }
        }
    }
}
