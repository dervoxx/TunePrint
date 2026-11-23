package pl.tuneprint.server

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
import io.ktor.server.response.respondText
import io.ktor.http.ContentType
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

internal fun Application.gameServer(games: GameServer = GameServer(), apple: AppleCatalog = AppleCatalog()) {
    install(WebSockets) {
        pingPeriodMillis = 15_000
        timeoutMillis = 15_000
        maxFrameSize = 128 * 1024L
    }
    launch {
        while (isActive) {
            delay(500)
            games.tick()
        }
    }
    routing {
        get("/.well-known/assetlinks.json") {
            call.respondText(
                """[{"relation":["delegate_permission/common.handle_all_urls"],"target":{"namespace":"android_app","package_name":"pl.tuneprint","sha256_cert_fingerprints":["B7:CC:0F:94:B6:0F:5B:C0:A8:FA:58:E1:40:53:F1:48:49:CE:28:D5:D7:08:DD:12:89:F9:5C:1F:D7:F8:0A:FB"]}}]""",
                ContentType.Application.Json
            )
        }
        get("/spotify/callback") {
            call.response.headers.append("Cache-Control", "no-store")
            call.response.headers.append("Referrer-Policy", "no-referrer")
            call.respondText("""
                <!doctype html><html lang="pl"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
                <title>Powrót do TunePrint</title><p><a id="open">Wróć do TunePrint</a></p>
                <script>
                const query = new URLSearchParams(location.search);
                document.getElementById('open').href = 'intent://tuneprint.mliczek.com/spotify/callback?' +
                    query.toString() + '#Intent;scheme=https;package=pl.tuneprint;end';
                history.replaceState(null, '', location.pathname);
                </script></html>
            """.trimIndent(), ContentType.Text.Html)
        }
        webSocket("/game") {
            val events = Channel<ServerEvent>(32)
            val peer = Peer { event ->
                if (events.trySend(event).isFailure) cancel("Client cannot receive updates")
            }
            val writer = launch {
                for (event in events) send(Json.encodeToString(event))
            }
            var catalog = emptyMap<String, Recording>()
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
                    if (command.type == "import" || command.tracks != null) {
                        if (command.type !in setOf("import", "create", "join") || peer.roomCode != null) {
                            peer.error("Import jest dostępny przed wejściem do pokoju.")
                            continue
                        }
                        catalog = emptyMap()
                        try {
                            catalog = apple.resolve(command.tracks.orEmpty())
                        } catch (error: IllegalArgumentException) {
                            peer.error(error.message ?: "Niepoprawna lista Spotify.")
                            continue
                        } catch (_: java.io.IOException) {
                            peer.error("Apple chwilowo nie odpowiada. Ponów import.")
                            continue
                        }
                        if (command.type == "import") {
                            peer.send(ServerEvent("catalog", catalogKeys = catalog.keys.toList()))
                            continue
                        }
                    }
                    games.command(peer, command, catalog)
                }
            } finally {
                games.disconnect(peer)
                events.close()
                writer.cancel()
            }
        }
    }
}
