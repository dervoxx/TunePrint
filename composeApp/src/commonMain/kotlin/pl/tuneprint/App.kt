package pl.tuneprint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.tuneprint.game.GamePhase
import pl.tuneprint.game.GameSession
import pl.tuneprint.game.GameSnapshot
import pl.tuneprint.ui.*

@Composable
fun App(
    snapshot: GameSnapshot,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onStart: () -> Unit,
    onAnswer: (String) -> Unit,
    onReady: () -> Unit,
    onReplay: () -> Unit,
    audioLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    val players = snapshot.players.map { player ->
        val answer = snapshot.result?.answers?.firstOrNull { it.player.id == player.id }
        UiPlayer(
            id = player.id,
            name = if (snapshot.phase == GamePhase.LOBBY && player.id == GameSession.LOCAL_PLAYER_ID) nickname.ifBlank { "Ty" } else player.name,
            score = snapshot.ranking.first { it.player.id == player.id }.points,
            answered = player.id in snapshot.answeredPlayerIds,
            ready = player.id in snapshot.readyPlayerIds,
            roundPoints = answer?.points ?: 0,
            outcome = when {
                answer == null -> UiOutcome.NONE
                answer.selectedOwnerId == null -> UiOutcome.TIMEOUT
                answer.correct -> UiOutcome.CORRECT
                else -> UiOutcome.WRONG
            },
            answerName = answer?.selectedOwnerId?.let { id -> snapshot.players.first { it.id == id }.name },
        )
    }
    TunePrintTheme {
        when {
            error != null || audioLoading -> Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (error == null) {
                        CircularProgressIndicator()
                        Text("Nadstaw uszu…", style = MaterialTheme.typography.headlineMedium)
                    } else {
                        Text("Nie możemy odtworzyć utworu", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Text(error, textAlign = TextAlign.Center)
                        Button(onClick = onRetry) { Text("Spróbuj ponownie") }
                        TextButton(onClick = onReplay) { Text("Wróć do lobby") }
                    }
                }
            }
            snapshot.phase == GamePhase.LOBBY -> LobbyScreen(nickname, onNicknameChange, players, onStart)
            snapshot.phase == GamePhase.COUNTDOWN -> CountdownScreen(snapshot.roundNumber, snapshot.totalRounds, snapshot.countdown)
            snapshot.phase == GamePhase.ROUND -> RoundScreen(
                snapshot.roundNumber, snapshot.totalRounds, snapshot.remainingMs,
                GameSession.pointsFor(GameSession.ROUND_MS - snapshot.remainingMs),
                players, snapshot.selectedOwnerId, onAnswer,
            )
            snapshot.phase == GamePhase.RESULTS -> snapshot.result?.let { result ->
                ResultsScreen(
                    snapshot.roundNumber, snapshot.totalRounds, result.title, result.artist, result.owner.name,
                    players, ((snapshot.remainingMs + 999) / 1000).toInt(),
                    GameSession.LOCAL_PLAYER_ID in snapshot.readyPlayerIds, onReady,
                )
            }
            snapshot.phase == GamePhase.FINISHED -> FinalScreen(players, onReplay)
        }
    }
}
