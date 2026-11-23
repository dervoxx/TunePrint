package pl.tuneprint

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerAddressTest {
    @Test
    fun connectsToThePublicGameServerOverTls() {
        assertEquals("https://tuneprint.mliczek.com/game", gameServerRequest.url.toString())
    }
}
