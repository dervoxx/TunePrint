package pl.tuneprint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.ViewModelProvider
import pl.tuneprint.game.RoomRoute

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
            BackHandler(game.roomSnapshot.route == RoomRoute.JOIN || game.roomSnapshot.route == RoomRoute.LOBBY) {
                game.leaveRoom()
            }
            App(
                snapshot = game.snapshot,
                room = game.roomSnapshot,
                nickname = game.nickname,
                onNicknameChange = game::updateNickname,
                onCreateRoom = game::createRoom,
                onOpenJoin = game::openJoin,
                onCodeChange = game::updateRoomCode,
                onJoinRoom = game::joinRoom,
                onSelectRoom = game::selectRoom,
                onLeaveRoom = game::leaveRoom,
                onRoundCountChange = game::updateRoundCount,
                onCapacityChange = game::updateCapacity,
                onShowOwnerCountChange = game::updateShowOwnerCount,
                availableRoundCount = game.availableRoundCount,
                onStart = game::start,
                onToggleOwner = game::toggleOwner,
                onSubmitAnswer = game::submitAnswer,
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
