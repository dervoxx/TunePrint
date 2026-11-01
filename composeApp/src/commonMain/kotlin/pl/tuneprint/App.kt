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
import pl.tuneprint.ui.*

private data class ScreenFrame(
    val snapshot: GameSnapshot,
    val audioLevels: List<Float>,
    val audioLoading: Boolean,
    val error: String?,
) {
    val key: String get() = if (error != null) "error" else "${snapshot.phase}:${snapshot.roundNumber}"
}

@Composable
fun App(
    snapshot: GameSnapshot,
    onStart: () -> Unit,
    onAnswer: (String) -> Unit,
    onReady: () -> Unit,
    onReplay: () -> Unit,
    audioLoading: Boolean,
    audioLevels: List<Float>,
    error: String?,
    onRetry: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    TunePrintTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedVisibility(visible, enter = fadeIn(tween(220)), modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = ScreenFrame(snapshot, audioLevels, audioLoading, error),
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
                                answer.selectedOwnerId == null -> UiOutcome.TIMEOUT
                                answer.correct -> UiOutcome.CORRECT
                                else -> UiOutcome.WRONG
                            },
                            answerName = answer?.selectedOwnerId?.let { id -> state.players.first { it.id == id }.name },
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
                            state.phase == GamePhase.LOBBY -> LobbyScreen(players, onStart)
                            state.phase == GamePhase.COUNTDOWN -> CountdownScreen(state.roundNumber, state.totalRounds, state.countdown)
                            state.phase == GamePhase.ROUND -> RoundScreen(
                                state.roundNumber, state.totalRounds, state.remainingMs,
                                GameSession.pointsFor(GameSession.ROUND_MS - state.remainingMs),
                                players, state.selectedOwnerId, onAnswer,
                                audioLevels = frame.audioLevels,
                                acceptingAnswers = !frame.audioLoading,
                            )
                            state.phase == GamePhase.RESULTS -> state.result?.let { result ->
                                ResultsScreen(
                                    state.roundNumber, state.totalRounds, result.title, result.artist, result.owner.name,
                                    players, ((state.remainingMs + 999) / 1000).toInt(),
                                    GameSession.LOCAL_PLAYER_ID in state.readyPlayerIds, onReady,
                                )
                            }
                            state.phase == GamePhase.FINISHED -> FinalScreen(players, onReplay)
                        }
                    }
                }
            }
        }
    }
}
