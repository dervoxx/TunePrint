package pl.tuneprint

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.tuneprint.game.GamePhase
import pl.tuneprint.game.GameSession
import pl.tuneprint.game.GameSnapshot
import pl.tuneprint.game.RoomRoute
import pl.tuneprint.game.RoomSnapshot
import pl.tuneprint.ui.*

private data class ScreenFrame(
    val snapshot: GameSnapshot,
    val room: RoomSnapshot,
    val nickname: String,
    val audioLevels: List<Float>,
    val audioLoading: Boolean,
    val error: String?,
) {
    val key: String get() = when {
        error != null -> "error"
        room.route != RoomRoute.GAME -> room.route.name
        else -> "${snapshot.phase}:${snapshot.roundNumber}"
    }
}

@Composable
fun App(
    snapshot: GameSnapshot,
    room: RoomSnapshot,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onOpenJoin: () -> Unit,
    onCodeChange: (String) -> Unit,
    onJoinRoom: () -> Unit,
    onSelectRoom: (String) -> Unit,
    onLeaveRoom: () -> Unit,
    onRoundCountChange: (Int) -> Unit,
    onCapacityChange: (Int) -> Unit,
    onShowOwnerCountChange: (Boolean) -> Unit,
    availableRoundCount: Int,
    onStart: () -> Unit,
    onToggleOwner: (String) -> Unit,
    onSubmitAnswer: () -> Unit,
    onReady: () -> Unit,
    onReplay: () -> Unit,
    audioLoading: Boolean,
    audioLevels: List<Float>,
    error: String?,
    onRetry: () -> Unit,
    onOpenOnline: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    TunePrintTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedVisibility(visible, enter = fadeIn(tween(220)), modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = ScreenFrame(snapshot, room, nickname, audioLevels, audioLoading, error),
                    contentKey = { it.key },
                    transitionSpec = { fadeIn(tween(180, 90)) togetherWith fadeOut(tween(130)) },
                    modifier = Modifier.fillMaxSize(),
                    label = "game-screen",
                ) { frame ->
                    // Each outgoing screen keeps its own immutable frame until its exit finishes.
                    val state = frame.snapshot
                    val players = state.players.map { player ->
                        val answer = state.result?.answers?.firstOrNull { it.player.id == player.id }
                        UiPlayer(
                            id = player.id,
                            name = player.name,
                            score = state.ranking.first { it.player.id == player.id }.points,
                            answered = player.id in state.answeredPlayerIds,
                            ready = player.id in state.readyPlayerIds,
                            roundPoints = answer?.points ?: 0,
                            outcome = when {
                                answer == null -> UiOutcome.NONE
                                answer.selectedOwnerIds == null -> UiOutcome.TIMEOUT
                                answer.correct -> UiOutcome.CORRECT
                                answer.accuracy > 0 -> UiOutcome.PARTIAL
                                else -> UiOutcome.WRONG
                            },
                            answerName = answer?.selectedOwnerIds?.let { ids -> state.players.filter { it.id in ids }.joinToString(", ") { it.name } },
                            hitCount = answer?.selectedOwnerIds?.count { id -> state.result?.owners?.any { it.id == id } == true } ?: 0,
                            totalOwners = state.result?.owners?.size ?: 0,
                            wrongSelections = answer?.selectedOwnerIds?.count { id -> state.result?.owners?.none { it.id == id } == true } ?: 0,
                        )
                    }
                    CompositionLocalProvider(LocalScreenTransition provides this) {
                        when {
                            frame.error != null -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                Column(
                                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("Błąd odtwarzania", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                                    Text(frame.error, textAlign = TextAlign.Center)
                                    Button(onClick = onRetry) { Text("Ponów") }
                                    TextButton(onClick = onReplay) { Text("Wróć") }
                                }
                            }
                            frame.room.route == RoomRoute.HOME -> HomeScreen(
                                frame.nickname, onNicknameChange, onCreateRoom, onOpenJoin,
                                heading = "Gra offline",
                                extraActions = { Button(onClick = onOpenOnline) { Text("Graj online") } },
                            )
                            frame.room.route == RoomRoute.JOIN -> JoinRoomScreen(
                                frame.room.code, onCodeChange, onJoinRoom, onLeaveRoom,
                                rooms = frame.room.availableRooms.map { UiRoom(it.code, it.hostName, it.roundCount) },
                                onSelectRoom = onSelectRoom, error = frame.room.joinError,
                            )
                            frame.room.route == RoomRoute.LOBBY -> LobbyScreen(
                                frame.room.players.map { member -> players.first { it.id == member.id } },
                                onStart, frame.room.code, onLeaveRoom,
                                isHost = frame.room.isHost,
                                roundCount = frame.room.roundCount,
                                maxRounds = if (frame.room.route == room.route) availableRoundCount else frame.room.maxRounds,
                                onRoundCountChange = onRoundCountChange,
                                capacity = frame.room.capacity,
                                onCapacityChange = onCapacityChange,
                                showOwnerCount = frame.room.showOwnerCount,
                                onShowOwnerCountChange = onShowOwnerCountChange,
                            )
                            state.phase == GamePhase.COUNTDOWN -> CountdownScreen(state.roundNumber, state.totalRounds, state.countdown)
                            state.phase == GamePhase.ROUND -> RoundScreen(
                                state.roundNumber, state.totalRounds, state.remainingMs,
                                GameSession.pointsFor(GameSession.ROUND_MS - state.remainingMs),
                                players, state.selectedOwnerIds,
                                GameSession.LOCAL_PLAYER_ID in state.answeredPlayerIds,
                                onToggleOwner, onSubmitAnswer,
                                audioLevels = frame.audioLevels,
                                acceptingAnswers = !frame.audioLoading,
                                ownerCountHint = state.ownerCountHint,
                            )
                            (state.phase == GamePhase.RESULTS || state.phase == GamePhase.FINISHED) -> state.result?.let { result ->
                                ResultsScreen(
                                    state.roundNumber, state.totalRounds, result.title, result.artist, result.owners.map { it.name },
                                    players, ((state.remainingMs + 999) / 1000).toInt(),
                                    GameSession.LOCAL_PLAYER_ID in state.readyPlayerIds, onReady,
                                    onReplay = onReplay, isFinal = state.phase == GamePhase.FINISHED,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
