package pl.tuneprint.server

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import pl.tuneprint.net.ClientCommand
import pl.tuneprint.net.ServerEvent
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    if (host == "0.0.0.0") {
        printLanAddresses(port)
    } else {
        println("TunePrint · ws://$host:$port/game")
    }
    embeddedServer(CIO, host = host, port = port) { gameServer() }.start(wait = true)
}

private fun printLanAddresses(port: Int) {
    println("TunePrint · emulator Android: ws://10.0.2.2:$port/game")
    val addresses = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }.map { it.hostAddress }.distinct()
    } catch (_: SocketException) { emptyList() }
    addresses.forEach { println("Adres dla telefonu w tej samej sieci: ws://$it:$port/game") }
}

internal fun Application.gameServer(games: GameServer = GameServer()) {
    install(WebSockets) {
        pingPeriodMillis = 15_000
        timeoutMillis = 15_000
        maxFrameSize = 16 * 1024L
    }
    launch {
        while (isActive) {
            delay(500)
            games.tick()
        }
    }
    routing {
        webSocket("/game") {
            val events = Channel<ServerEvent>(32)
            val peer = Peer { event ->
                if (events.trySend(event).isFailure) cancel("Client cannot receive updates")
            }
            val writer = launch {
                for (event in events) send(Json.encodeToString(event))
            }
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) {
                        peer.error("Oczekiwano tekstowej wiadomości JSON.")
                        continue
                    }
                    val command = try {
                        Json.decodeFromString<ClientCommand>(frame.readText())
                    } catch (_: SerializationException) {
                        peer.error("Niepoprawna wiadomość JSON.")
                        continue
                    }
                    games.command(peer, command)
                }
            } finally {
                games.disconnect(peer)
                events.close()
                writer.cancel()
            }
        }
    }
}
