package pl.tuneprint

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.tuneprint.game.GameSession
import pl.tuneprint.net.RankingPeriod
import pl.tuneprint.net.ClientCommand
import pl.tuneprint.net.RoomState
import pl.tuneprint.ui.*

private val modes = mapOf("OWNERS" to "Właściciele", "TITLE" to "Tytuł", "ARTIST" to "Wykonawca")

@Composable
fun OnlineScreens(game: OnlineGame) {
    val room = game.state
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = room != null || game.playerId != null || game.browsing || (game.busy && !game.preparing)) {
        if (room != null || game.playerId != null) confirmLeave = true else game.leave()
    }
    TunePrintTheme {
        Surface(Modifier.fillMaxSize()) {
            Column {
                val status = game.message?.takeUnless { game.browsing && !game.busy } ?: game.audioError
                if (status != null || (game.busy && !game.preparing)) {
                    Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 16.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(status ?: "Łączenie…", modifier = Modifier.weight(1f))
                        if (game.audioError != null) TextButton(onClick = game::retryAudio) { Text("Ponów audio") }
                    }
                }
                when {
                    room != null -> OnlineRoom(room, game) { confirmLeave = true }
                    game.playerId != null -> TextButton(onClick = { confirmLeave = true }) { Text("Opuść pokój") }
                    game.catalog.isEmpty() -> SpotifyGate(game)
                    game.browsing -> JoinRoomScreen(game.code, game::changeCode, { game.enterRoom(true) }, game::leave,
                        rooms = game.rooms.map { UiRoom(it.code, it.hostName, it.roundCount) },
                        onSelectRoom = { if (!game.busy) { game.changeCode(it); game.enterRoom(true) } },
                        error = game.message, onRefresh = game::browse, canJoin = !game.busy)
                    else -> HomeScreen(game.name, game::changeName, { game.enterRoom(false) }, game::browse,
                        heading = "Gra ze znajomymi", canEnter = !game.busy && game.catalog.isNotEmpty(),
                        settings = { OnlineSettings(game) })
                }
            }
        }
        if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false },
            title = { Text("Opuścić pokój?") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; game.leave() }) { Text("Opuść") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Zostań") } })
    }
}

@Composable
private fun SpotifyGate(game: OnlineGame) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("TunePrint", style = MaterialTheme.typography.headlineLarge)
        androidx.compose.foundation.Image(painterResource(R.drawable.spotify_logo), "Spotify")
        Text("Twoje najczęściej słuchane utwory", style = MaterialTheme.typography.titleLarge)
        val status = when {
            game.preparing -> "Apple Music · ${game.importPeriod.label}. Możesz przełączyć aplikację."
            game.importing -> "Dokończ logowanie w przeglądarce, a następnie wróć do TunePrint."
            else -> game.importMessage ?: "Połącz konto Spotify, żeby przygotować utwory do gry."
        }
        Text(status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (game.preparing || game.importing) {
            if (game.preparing) {
                val progress = game.progress
                LinearProgressIndicator(progress = { progress.checked.toFloat() / progress.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth())
                Text(
                    "Sprawdzone w tej partii: ${progress.checked} z ${progress.total}\nFragmenty z tego okresu: ${progress.found}/50",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (game.importing) TextButton(onClick = game::cancelImport) { Text("Anuluj logowanie") }
        } else {
            OutlinedButton(onClick = game::importTopTracks) { Text("Zaloguj się przez Spotify") }
        }
    }
}

@Composable
private fun OnlineSettings(game: OnlineGame) {
    var showSpotify by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    OutlinedButton(onClick = { showSpotify = true }, enabled = !game.busy, modifier = Modifier.fillMaxWidth()) {
        Text("Twoje utwory do gry · ${game.catalog.size}")
    }
    if (showSpotify) AlertDialog(onDismissRequest = { showSpotify = false },
        title = { androidx.compose.foundation.Image(painterResource(R.drawable.spotify_logo), "Spotify") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Utwory z fragmentem w trzech rankingach: ${game.catalog.size}") }
                itemsIndexed(game.topTracks, key = { _, track -> track.id }) { index, track ->
                    Column {
                        Text("${index + 1}. ${track.title}", style = MaterialTheme.typography.titleSmall)
                        Text(track.artists.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        Text(track.periods.joinToString(" · ") { it.label },
                            style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = {
                            val link = android.net.Uri.parse("https://open.spotify.com/track").buildUpon()
                                .appendPath(track.id).build()
                            try {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, link))
                            } catch (_: android.content.ActivityNotFoundException) {
                                android.widget.Toast.makeText(context, "Brak aplikacji do otwarcia linku.",
                                    android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Otwórz w Spotify") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showSpotify = false }) { Text("Gotowe") } },
        dismissButton = { TextButton(onClick = { showSpotify = false; game.importTopTracks() }) { Text("Zmień konto") } })
}

@Composable
private fun OnlineRoom(room: RoomState, game: OnlineGame, onLeave: () -> Unit) {
    val players = remember(room.players, room.ranking, room.result, room.answeredIds, room.readyIds, room.mode) {
        room.toUiPlayers()
    }
    val host = room.hostId == game.playerId
    val context = LocalContext.current
    val seconds = ((game.remaining + 999) / 1000).toInt()
    when (room.phase) {
        "LOBBY" -> LobbyScreen(players, { game.command(ClientCommand("start")) }, room.code, onLeave,
            isHost = host && game.sessionConnected, roundCount = room.roundCount, maxRounds = room.maxRounds,
            onRoundCountChange = { game.command(ClientCommand("configure", roundCount = it)) },
            capacity = room.capacity, onCapacityChange = { game.command(ClientCommand("configure", capacity = it)) },
            showOwnerCount = room.showOwnerCount,
            onShowOwnerCountChange = { game.command(ClientCommand("configure", showOwnerCount = it)) },
            canStart = game.sessionConnected && host && room.canStart,
            startLabel = when {
                !game.sessionConnected -> "Łączenie…"
                !host -> "Czekamy na gospodarza"
                !room.canStart -> "Potrzeba 3 połączonych osób"
                else -> "Rozpocznij grę"
            },
            modeLabel = "${modes[room.mode]} · ${room.period.label}", ownerHintsAvailable = room.mode == "OWNERS",
            onShare = {
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, "Dołącz do mojego pokoju w TunePrint. Kod: ${room.code}")
                context.startActivity(android.content.Intent.createChooser(share, "Udostępnij kod pokoju"))
            },
            modeControl = {
                Column {
                    Text("Okres słuchania", style = MaterialTheme.typography.titleMedium)
                    RankingPeriod.entries.forEach { period ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = period == room.period, enabled = host && game.sessionConnected,
                                role = Role.RadioButton, onClick = { game.command(ClientCommand("configure", period = period)) }),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = period == room.period, onClick = null)
                            Text(period.label, Modifier.padding(start = 12.dp))
                        }
                    }
                    Text("Tryb gry", style = MaterialTheme.typography.titleMedium)
                    modes.forEach { (mode, label) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = mode == room.mode, enabled = game.sessionConnected,
                                role = Role.RadioButton, onClick = { game.command(ClientCommand("configure", mode = mode)) }),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == room.mode, onClick = null)
                            Text(label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            })
        "COUNTDOWN" -> CountdownScreen(room.roundNumber, room.roundCount, seconds)
        "ROUND" -> RoundScreen(room.roundNumber, room.roundCount, game.remaining,
            GameSession.pointsFor(GameSession.ROUND_MS - game.remaining), players, game.owners,
            game.playerId in room.answeredIds, game::editOwners, { game.sendAnswer("answer") },
            acceptingAnswers = game.sessionConnected && game.remaining > 0,
            ownerCountHint = room.ownerCountHint, textAnswer = game.answerText.takeUnless { room.mode == "OWNERS" },
            onTextChange = game::editText, question = when (room.mode) {
                "TITLE" -> "Jaki to tytuł?"
                "ARTIST" -> "Kto wykonuje ten utwór?"
                else -> "Czyj to utwór?"
            })
        "RESULTS", "FINISHED" -> room.result?.let { result ->
            ResultsScreen(room.roundNumber, room.roundCount, result.title, result.artist,
                room.players.filter { it.id in result.ownerIds }.map { it.name }, players, seconds,
                game.playerId in room.readyIds, { game.command(ClientCommand("ready", roundNumber = room.roundNumber)) },
                onReplay = { game.command(ClientCommand("replay")) }, isFinal = room.phase == "FINISHED",
                canReplay = host && game.sessionConnected, onLeave = onLeave)
        }
    }
}

private fun RoomState.toUiPlayers(): List<UiPlayer> {
    val answers = result?.answers.orEmpty().associateBy { it.playerId }
    val ranks = ranking.associateBy { it.playerId }
    val owners = result?.ownerIds.orEmpty()
    val roster = if (result == null) players else players.sortedBy { ranking.indexOf(ranks[it.id]) }
    return roster.map { player ->
        val answer = answers[player.id]
        val selected = answer?.ownerIds.orEmpty()
        UiPlayer(player.id, player.name, score = ranks[player.id]?.points ?: 0,
            answered = player.id in answeredIds, ready = player.id in readyIds, roundPoints = answer?.points ?: 0,
            outcome = when {
                answer == null -> UiOutcome.NONE
                answer.elapsedMs == null -> UiOutcome.TIMEOUT
                answer.correct -> UiOutcome.CORRECT
                answer.accuracy > 0 -> UiOutcome.PARTIAL
                else -> UiOutcome.WRONG
            },
            answerName = answer?.text ?: players.filter { it.id in selected }.joinToString(", ") { it.name },
            hitCount = selected.count { it in owners }, totalOwners = owners.size,
            wrongSelections = selected.count { it !in owners }, textAnswerMode = mode != "OWNERS")
    }
}
