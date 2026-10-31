package pl.tuneprint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private lateinit var game: GameController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        game = ViewModelProvider(this)[GameController::class.java]
        setContent {
            App(
                snapshot = game.snapshot,
                onStart = game::start,
                onAnswer = game::answer,
                onReady = game::ready,
                onReplay = game::replay,
                audioLoading = game.audioLoading,
                audioLevels = game.audioLevels,
                error = game.error,
                onRetry = game::retryAudio,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        game.resume()
    }

    override fun onPause() {
        game.pause()
        super.onPause()
    }
}
