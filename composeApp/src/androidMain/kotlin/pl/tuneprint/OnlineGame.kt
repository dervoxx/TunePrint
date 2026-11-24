package pl.tuneprint

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import pl.tuneprint.audio.RoundAudioPlayer
import pl.tuneprint.game.GameSession
import kotlinx.coroutines.CompletableDeferred
import pl.tuneprint.net.RankingPeriod
import pl.tuneprint.net.CatalogProgress
import pl.tuneprint.net.ImportTrack
import pl.tuneprint.net.ClientCommand
import pl.tuneprint.net.RoomListing
import pl.tuneprint.net.RoomState
import pl.tuneprint.net.ServerEvent

internal val gameServerRequest = Request.Builder().url("wss://tuneprint.mliczek.com/game").build()

/** Socket callbacks, room changes and playback are serialized on the main thread. */
class OnlineGame(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val profile = application.getSharedPreferences("player", Application.MODE_PRIVATE)
    var name by mutableStateOf(saved.get<String>("name") ?: profile.getString("name", null) ?: "")
        private set
    internal var topTracks by mutableStateOf<List<ImportTrack>>(emptyList())
        private set
    var catalog by mutableStateOf(emptySet<String>())
        private set
    var progress by mutableStateOf(pl.tuneprint.net.CatalogProgress(0, 0, 0))
        private set
    var preparing by mutableStateOf(false)
        private set
    var importPeriod by mutableStateOf(RankingPeriod.LONG)
        private set
    private var pageReply: CompletableDeferred<Set<String>>? = null
    private var previouslyFound = 0
    private var startedImport = false
    var importing by mutableStateOf(false)
        private set
    var importMessage by mutableStateOf<String?>(null)
        private set
    private var importJob: Job? = null
    var code by mutableStateOf(saved.get<String>("code") ?: "")
        private set
    var browsing by mutableStateOf(false)
        private set
    var rooms by mutableStateOf(emptyList<RoomListing>())
        private set
    var state by mutableStateOf<RoomState?>(null)
        private set
    var playerId by mutableStateOf(saved.get<String>("streamPlayerId"))
        private set
    var busy by mutableStateOf(false)
        private set
    var sessionConnected by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var audioError by mutableStateOf<String?>(null)
        private set
    var remaining by mutableLongStateOf(0L)
        private set
    var owners by mutableStateOf(saved.get<ArrayList<String>>("owners")?.toSet() ?: emptySet())
        private set
    var answerText by mutableStateOf(saved.get<String>("answerText") ?: "")
        private set

    private var token: String? = saved["streamToken"]
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .dns(TunePrintDns())
        .pingInterval(5, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private var socket: WebSocket? = null
    private var opened = false
    private var awaitingResume = false
    private var foreground = false
    private var retryDelay = 1000L
    private val reconnect = Runnable { connectForSession() }
    private var deadline = 0L
    private val audio = RoundAudioPlayer()
    private var playingKey: String? = null
    private val tick = object : Runnable {
        override fun run() {
            remaining = remainingNow()
            updatePlayback()
            if (foreground && sessionConnected && state?.phase in timedPhases) handler.postDelayed(this, 100)
        }
    }

    fun changeName(value: String) {
        name = value.take(20)
        saved["name"] = name
    }

    fun importTopTracks() {
        if (importing || preparing || busy || state != null) return
        message = null
        startedImport = true
        catalog = emptySet()
        topTracks = emptyList()
        val app = getApplication<Application>()
        importing = true
        importMessage = null
        importJob = viewModelScope.launch {
            try {
                val spotify = SpotifyTopTracks(app.getString(R.string.spotify_client_id))
                val imported = spotify.importTracks(::matchPage) { address ->
                    app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                topTracks = imported
                catalog = imported.map { it.id }.toSet()
                if (catalog.isEmpty()) importMessage = "W rankingach Spotify nie znaleziono nagrań z fragmentem."

            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SpotifyImportException) {
                importMessage = error.message
            } catch (_: android.content.ActivityNotFoundException) {
                importMessage = "Do logowania potrzebna jest przeglądarka."
            } catch (_: Exception) {
                importMessage = "Import nie powiódł się. Sprawdź połączenie i spróbuj ponownie."
            } finally {
                preparing = false
                busy = false
                importing = false
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
    }

    private suspend fun matchPage(period: RankingPeriod, found: Int, tracks: List<ImportTrack>): Set<String> {
        importing = false
        preparing = true
        importPeriod = period
        previouslyFound = found
        progress = CatalogProgress(0, tracks.size, found)
        for (attempt in 1..3) {
            val reply = CompletableDeferred<Set<String>>()
            pageReply = reply
            try {
                requestEntry(ClientCommand("import", tracks = tracks, reportProgress = true))
                return reply.await()
            } catch (error: java.io.IOException) {
                if (attempt == 3) throw error
                closeSocket()
                kotlinx.coroutines.delay(attempt * 1000L)
            } finally {
                pageReply = null
            }
        }
        error("Import interrupted")
    }

    fun changeCode(value: String) {
        code = value
        saved["code"] = code
    }

    fun browse() {
        if (busy || catalog.isEmpty()) return
        browsing = true
        requestEntry(ClientCommand("list"))
    }

    fun enterRoom(join: Boolean) {
        if (busy || name.isBlank() || catalog.isEmpty()) return
        requestEntry(ClientCommand(if (join) "join" else "create", name = name.trim(),
            code = if (join) code else null,
            tracks = topTracks))
    }

    private fun requestEntry(command: ClientCommand) {
        message = null
        busy = true
        if (opened) send(command) else connect(command)
    }

    private fun connectForSession() {
        val credential = token ?: return
        if (!foreground || socket != null) return
        busy = true
        message = "Wznawianie połączenia…"
        awaitingResume = true
        connect(ClientCommand("resume", code = code, token = credential))
    }

    private fun connect(initial: ClientCommand) {
        closeSocket()
        socket = client.newWebSocket(gameServerRequest, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = onCurrent(webSocket) {
                opened = true
                send(initial)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val receivedAt = SystemClock.elapsedRealtime()
                onCurrent(webSocket) {
                    try {
                        receive(json.decodeFromString(ServerEvent.serializer(), text), receivedAt)
                    } catch (_: SerializationException) {
                        disconnected("Nieprawidłowa odpowiedź serwera.")
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = onCurrent(webSocket) {
                disconnected("Serwer zakończył połączenie.")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = onCurrent(webSocket) {
                disconnected("Brak połączenia z serwerem. Sprawdź połączenie z internetem.")
            }
        })
    }

    private fun onCurrent(source: WebSocket, action: () -> Unit) {
        handler.post { if (socket === source) action() }
    }

    fun command(command: ClientCommand) {
        if (foreground && sessionConnected) {
            message = null
            send(command)
        }
    }

    private fun send(command: ClientCommand) {
        if (socket?.send(json.encodeToString(ClientCommand.serializer(), command)) != true) {
            disconnected("Nie udało się wysłać polecenia.")
        }
    }

    private fun receive(event: ServerEvent, receivedAt: Long) {
        when (event.type) {
            "catalogProgress" -> if (preparing) event.progress?.let { progress = it.copy(found = previouslyFound + it.found) }
            "catalog" -> {
                busy = false
                pageReply?.complete(event.catalogKeys.orEmpty().toSet())
            }
            "rooms" -> {
                rooms = event.rooms
                if (browsing) busy = false
            }
            "state" -> {
                val room = event.state ?: return
                event.token?.let {
                    token = it
                    saved["streamToken"] = it
                    profile.edit {
                        putString("name", name)
                        remove("catalog")
                        remove("endpoint")
                    }
                }
                event.playerId?.let {
                    playerId = it
                    saved["streamPlayerId"] = it
                }
                changeCode(room.code)
                val resuming = awaitingResume
                busy = false
                browsing = false
                awaitingResume = false
                retryDelay = 1000L
                sessionConnected = true
                val draftRound = "${room.code}:${room.roundNumber}"
                val sameRound = draftRound == saved.get<String>("draftRound")
                if (!sameRound || (room.phase != "ROUND" && state?.phase != room.phase)) {
                    clearDraft()
                    saved["draftRound"] = draftRound
                }
                if (resuming || state?.phase != room.phase) message = null
                if (room.roundNumber != state?.roundNumber || room.phase != state?.phase) stopAudio()
                state = room
                deadline = receivedAt + room.remainingMs
                restartClock()
                if (resuming && sameRound && canAnswer()) sendAnswer("draft")
            }
            "error" -> {
                if (awaitingResume) resetRoom()
                busy = false
                showRequestError(event.message ?: "Serwer odrzucił polecenie.")
            }
            "left" -> resetRoom()
        }
    }

    private fun disconnected(reason: String) {
        closeSocket()
        sessionConnected = false
        busy = false
        showRequestError(reason)
        stopAudio()
        handler.removeCallbacks(tick)
        if (token != null && foreground) {
            handler.postDelayed(reconnect, retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(8000L)
        }
    }

    private fun showRequestError(reason: String) {
        if (preparing) {
            pageReply?.completeExceptionally(SpotifyImportException(reason))
            importMessage = reason
        } else message = reason
    }

    private fun closeSocket() {
        handler.removeCallbacks(reconnect)
        val old = socket
        socket = null
        opened = false
        old?.cancel()
    }

    fun leave() {
        // Queue leave before the close frame so the server sees an explicit departure.
        if (opened && (sessionConnected || busy)) {
            val old = socket
            old?.send(json.encodeToString(ClientCommand.serializer(), ClientCommand("leave")))
            old?.close(1000, null)
            socket = null
        }
        resetRoom()
    }

    private fun resetRoom() {
        closeSocket()
        stopAudio()
        handler.removeCallbacks(tick)
        saved.remove<String>("streamToken")
        saved.remove<String>("streamPlayerId")
        saved.remove<String>("draftRound")
        token = null
        playerId = null
        state = null
        sessionConnected = false
        awaitingResume = false
        busy = false
        browsing = false
        rooms = emptyList()
        message = null
        changeCode("")
        clearDraft()
        ensureStartup()
    }

    private fun clearDraft() {
        owners = emptySet()
        answerText = ""
        saved.remove<ArrayList<String>>("owners")
        saved.remove<String>("answerText")
    }

    fun editOwners(id: String) {
        if (!canAnswer()) return
        owners = if (id in owners) owners - id else owners + id
        saved["owners"] = ArrayList(owners)
        sendAnswer("draft")
    }

    fun editText(value: String) {
        if (!canAnswer()) return
        answerText = value.take(200)
        saved["answerText"] = answerText
        sendAnswer("draft")
    }

    private fun canAnswer(): Boolean {
        val room = state ?: return false
        return foreground && sessionConnected && room.phase == "ROUND" &&
            playerId !in room.answeredIds && remainingNow() > 0
    }

    fun sendAnswer(type: String) {
        val room = state ?: return
        if (!canAnswer()) return
        send(ClientCommand(type, roundNumber = room.roundNumber,
            ownerIds = owners.toList().takeIf { room.mode == "OWNERS" },
            title = answerText.takeIf { room.mode == "TITLE" },
            artist = answerText.takeIf { room.mode == "ARTIST" }))
    }

    private fun remainingNow() = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)

    fun resume() {
        foreground = true
        ensureStartup()
        restartClock()
    }

    private fun ensureStartup() {
        if (!foreground) return
        if (token != null) connectForSession()
        else if (!startedImport) importTopTracks()
    }

    fun pause() {
        foreground = false
        handler.removeCallbacks(tick)
        handler.removeCallbacks(reconnect)
        stopAudio()
    }

    private fun restartClock() {
        handler.removeCallbacks(tick)
        if (foreground && sessionConnected) tick.run()
    }

    private fun updatePlayback() {
        val room = state
        val key = room?.audioKey
        if (room?.phase != "ROUND" || key == null || remaining == 0L) {
            stopAudio()
            return
        }
        if (playingKey == key || audioError != null) return
        playingKey = key
        val preview = room.previewUrl
        if (preview == null) {
            audioError = "Serwer nie udostępnił fragmentu dla tej rundy."
            return
        }
        audio.play(preview, onStarted = {}, onError = { audioError = it },
            elapsedInRoundMs = { GameSession.ROUND_MS - remainingNow() })
    }

    fun retryAudio() {
        stopAudio()
        restartClock()
    }

    private fun stopAudio() {
        if (playingKey != null) audio.stop()
        playingKey = null
        audioError = null
    }

    override fun onCleared() {
        closeSocket()
        handler.removeCallbacksAndMessages(null)
        audio.release()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private companion object {
        val timedPhases = setOf("COUNTDOWN", "ROUND", "RESULTS")
    }
}
