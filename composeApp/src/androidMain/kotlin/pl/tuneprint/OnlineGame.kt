package pl.tuneprint

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import pl.tuneprint.audio.DemoAudio
import pl.tuneprint.audio.RoundAudioPlayer
import pl.tuneprint.game.GameSession
import pl.tuneprint.net.ClientCommand
import pl.tuneprint.net.RoomListing
import pl.tuneprint.net.RoomState
import pl.tuneprint.net.ServerEvent

/** Socket callbacks, room changes and playback are serialized on the main thread. */
class OnlineGame(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    var active by mutableStateOf(saved.get<Boolean>("online") ?: false)
        private set
    var endpoint by mutableStateOf(saved.get<String>("endpoint") ?: "ws://10.0.2.2:8080/game")
        private set
    var name by mutableStateOf(saved.get<String>("name") ?: "")
        private set
    var catalog by mutableStateOf(saved.get<ArrayList<String>>("catalog")?.toSet() ?: emptySet())
        private set
    var code by mutableStateOf(saved.get<String>("code") ?: "")
        private set
    var browsing by mutableStateOf(false)
        private set
    var rooms by mutableStateOf(emptyList<RoomListing>())
        private set
    var state by mutableStateOf<RoomState?>(null)
        private set
    var playerId by mutableStateOf(saved.get<String>("playerId"))
        private set
    var busy by mutableStateOf(false)
        private set
    var sessionConnected by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var audioError by mutableStateOf<String?>(null)
        private set
    var remaining by mutableStateOf(0L)
        private set
    var levels by mutableStateOf(emptyList<Float>())
        private set
    var owners by mutableStateOf(saved.get<ArrayList<String>>("owners")?.toSet() ?: emptySet())
        private set
    var answerText by mutableStateOf(saved.get<String>("answerText") ?: "")
        private set

    private var token: String? = saved["token"]
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().pingInterval(5, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private var socket: WebSocket? = null
    private var opened = false
    private var awaitingResume = false
    private var foreground = false
    private var retryDelay = 1000L
    private val reconnect = Runnable { connectForSession() }
    private var deadline = 0L
    private val audio = RoundAudioPlayer(application)
    private var playingKey: String? = null
    private val tick = object : Runnable {
        override fun run() {
            remaining = remainingNow()
            updatePlayback()
            if (playingKey != null) levels = audio.levels()
            if (foreground && sessionConnected && state?.phase in timedPhases) handler.postDelayed(this, 100)
        }
    }

    fun enter(nickname: String) {
        active = true
        saved["online"] = true
        if (name.isBlank()) changeName(nickname)
    }

    fun changeName(value: String) {
        name = value.take(20)
        saved["name"] = name
    }

    fun changeEndpoint(value: String) {
        if (busy || state != null) return
        closeSocket()
        endpoint = value
        saved["endpoint"] = value
        message = null
    }

    fun toggleTrack(key: String) {
        if (busy || state != null) return
        catalog = if (key in catalog) catalog - key else catalog + key
        saved["catalog"] = ArrayList(catalog)
    }

    fun changeCode(value: String) {
        code = value
        saved["code"] = code
    }

    fun browse() {
        if (busy) return
        browsing = true
        requestEntry(ClientCommand("list"))
    }

    fun enterRoom(join: Boolean) {
        if (busy || name.isBlank() || catalog.isEmpty()) return
        requestEntry(ClientCommand(if (join) "join" else "create", name = name.trim(),
            code = if (join) code else null, catalogKeys = catalog.toList()))
    }

    private fun requestEntry(command: ClientCommand) {
        message = null
        busy = true
        if (opened) send(command) else connect(command)
    }

    private fun connectForSession() {
        val credential = token ?: return
        if (!active || !foreground || socket != null) return
        busy = true
        message = "Wznawianie połączenia…"
        awaitingResume = true
        connect(ClientCommand("resume", code = code, token = credential))
    }

    private fun connect(initial: ClientCommand) {
        closeSocket()
        val url = endpoint.trim()
        val request = try {
            require(url.startsWith("ws://") || url.startsWith("wss://"))
            Request.Builder().url(url).build()
        } catch (_: IllegalArgumentException) {
            busy = false
            message = "Podaj pełny adres, np. ws://192.168.1.10:8080/game."
            return
        }
        socket = client.newWebSocket(request, object : WebSocketListener() {
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
                disconnected("Brak połączenia z serwerem. Sprawdź adres i sieć.")
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
            "rooms" -> {
                rooms = event.rooms
                if (browsing) busy = false
            }
            "state" -> {
                val room = event.state ?: return
                event.token?.let {
                    token = it
                    saved["token"] = it
                }
                event.playerId?.let {
                    playerId = it
                    saved["playerId"] = it
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
                message = event.message ?: "Serwer odrzucił polecenie."
            }
            "left" -> resetRoom()
        }
    }

    private fun disconnected(reason: String) {
        closeSocket()
        sessionConnected = false
        busy = false
        message = reason
        stopAudio()
        handler.removeCallbacks(tick)
        if (token != null && foreground) {
            handler.postDelayed(reconnect, retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(8000L)
        }
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
        saved.remove<String>("token")
        saved.remove<String>("playerId")
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
    }

    fun exit() {
        leave()
        active = false
        saved["online"] = false
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
        connectForSession()
        restartClock()
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
        val track = DemoAudio.byKey[key]
        if (track == null) {
            audioError = "Brak nagrania dla tej rundy."
            return
        }
        audio.play(track.assetName, track.startAtMs, onStarted = {}, onError = { audioError = it },
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
        levels = emptyList()
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
