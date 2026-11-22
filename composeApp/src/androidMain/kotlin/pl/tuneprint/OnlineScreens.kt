package pl.tuneprint

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.unit.dp
import pl.tuneprint.audio.DemoAudio
import pl.tuneprint.game.GameSession
import pl.tuneprint.net.ClientCommand
import pl.tuneprint.net.RoomState
import pl.tuneprint.ui.*

private val modes = mapOf("OWNERS" to "Właściciele", "TITLE" to "Tytuł", "ARTIST" to "Wykonawca")

@Composable
fun OnlineScreens(game: OnlineGame, onExit: () -> Unit) {
    val room = game.state
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = room != null || game.playerId != null || game.browsing || game.busy) {
        if (room != null || game.playerId != null) confirmLeave = true else game.leave()
    }
    TunePrintTheme {
        Surface(Modifier.fillMaxSize()) {
            Column {
                val status = game.message?.takeUnless { game.browsing && !game.busy } ?: game.audioError
                if (status != null || game.busy) {
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
                    game.browsing -> JoinRoomScreen(game.code, game::changeCode, { game.enterRoom(true) }, game::leave,
                        rooms = game.rooms.map { UiRoom(it.code, it.hostName, it.roundCount) },
                        onSelectRoom = { if (!game.busy) { game.changeCode(it); game.enterRoom(true) } },
                        error = game.message, onRefresh = game::browse, canJoin = !game.busy)
                    else -> HomeScreen(game.name, game::changeName, { game.enterRoom(false) }, game::browse,
                        heading = "Gra ze znajomymi", canEnter = !game.busy && game.catalog.isNotEmpty(),
                        settings = { OnlineSettings(game) },
                        extraActions = { TextButton(onClick = onExit) { Text("Zagraj offline", color = MaterialTheme.colorScheme.secondary) } })
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
private fun OnlineSettings(game: OnlineGame) {
    var editServer by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showSpotify by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = {
        showSpotify = true
        if (game.topTracks.isEmpty()) game.importTopTracks()
    }, enabled = !game.busy, modifier = Modifier.fillMaxWidth()) {
        Text(if (game.topTracks.isEmpty()) "Importuj top utwory ze Spotify" else "Top utwory ze Spotify")
    }
    if (showSpotify) SpotifyTrackDialog(game) { showSpotify = false }
    OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Text(if (game.catalog.isEmpty()) "Wybierz utwory do gry" else "Moja playlista · ${game.catalog.size}/${DemoAudio.byKey.size}",
            color = MaterialTheme.colorScheme.onSurface)
    }
    if (expanded) Column {
        DemoAudio.byKey.forEach { (key, track) ->
            val checked = key in game.catalog
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(checked, enabled = !game.busy, role = Role.Checkbox) { game.toggleTrack(key) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Checkbox(checked, onCheckedChange = null)
                Column {
                    Text(track.title)
                    if (track.artist.isNotBlank()) Text(track.artist, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    TextButton(onClick = { editServer = !editServer }, modifier = Modifier.fillMaxWidth()) {
        Text(if (editServer) "Zwiń adres serwera" else "Adres serwera", color = MaterialTheme.colorScheme.secondary)
    }
    if (editServer) OutlinedTextField(value = game.endpoint, onValueChange = game::changeEndpoint, singleLine = true,
        label = { Text("IP komputera lub adres WebSocket") },
        supportingText = { Text("Użyj adresu podanego przy uruchomieniu serwera.") },
        enabled = !game.busy, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SpotifyTrackDialog(game: OnlineGame, close: () -> Unit) {
    val context = LocalContext.current
    val tracks = game.topTracks
    val available = remember(tracks) { tracks.mapNotNull { it.key }.toSet() }
    val dismiss = { game.cancelImport(); close() }
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.foundation.Image(painterResource(R.drawable.spotify_logo), "Spotify")
                Text("Twoje top utwory")
            }
        },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (game.importing) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Dokończ logowanie w przeglądarce, a następnie wróć do TunePrint.")
                    }
                }
                game.importMessage?.let { message ->
                    item { Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                }
                if (tracks.isNotEmpty()) {
                    item {
                        Text("Utwory z ostatniego półrocza: ${tracks.size}. Dostępne nagrania: ${available.size}.")
                        if (available.isNotEmpty()) {
                            TextButton(onClick = game::selectTopTracks,
                                enabled = !game.catalog.containsAll(available)) { Text("Wybierz wszystkie dostępne") }
                        }
                    }
                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        val key = track.key
                        val checked = key != null && key in game.catalog
                        val select = if (key != null) Modifier.toggleable(checked, role = Role.Checkbox) {
                            game.toggleTrack(key)
                        } else Modifier
                        Row(Modifier.fillMaxWidth().then(select).heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${index + 1}.", style = MaterialTheme.typography.labelMedium)
                            Column(Modifier.weight(1f)) {
                                Text(track.title)
                                Text(track.artist, style = MaterialTheme.typography.bodySmall)
                                if (key == null) Text("Brak nagrania w grze", style = MaterialTheme.typography.labelSmall)
                                TextButton(onClick = {
                                    val link = android.net.Uri.parse("https://open.spotify.com/track")
                                        .buildUpon().appendPath(track.id).build()
                                    try {
                                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, link))
                                    } catch (_: android.content.ActivityNotFoundException) {
                                        android.widget.Toast.makeText(context, "Brak aplikacji do otwarcia linku.",
                                            android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }) { Text("Otwórz w Spotify") }
                            }
                            if (key != null) Checkbox(checked, onCheckedChange = null)
                        }
                    }
                    if (!game.importing) item {
                        TextButton(onClick = game::clearTopTracks) { Text("Usuń listę z aplikacji") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(if (game.importing) "Anuluj" else "Gotowe") } },
        dismissButton = {
            if (!game.importing) TextButton(onClick = game::importTopTracks) {
                Text(if (tracks.isEmpty()) "Importuj" else "Importuj ponownie")
            }
        },
    )
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
            modeLabel = modes[room.mode], ownerHintsAvailable = room.mode == "OWNERS",
            onShare = {
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, "Dołącz do mojego pokoju w TunePrint. Kod: ${room.code}")
                context.startActivity(android.content.Intent.createChooser(share, "Udostępnij kod pokoju"))
            },
            modeControl = {
                Column {
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
            audioLevels = game.levels, acceptingAnswers = game.sessionConnected && game.remaining > 0,
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
