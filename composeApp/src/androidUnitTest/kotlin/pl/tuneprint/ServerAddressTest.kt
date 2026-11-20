package pl.tuneprint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerAddressTest {
    @Test
    fun phoneAcceptsLanAddressWithOrWithoutPort() {
        assertEquals("http://192.168.1.10:8080/game", serverRequest(" 192.168.1.10 ").url.toString())
        assertEquals("http://laptop:9001/game", serverRequest("laptop:9001").url.toString())
        assertEquals("http://[::1]:8080/game", serverRequest("[::1]").url.toString())
    }

    @Test
    fun preservesExplicitWebSocketEndpointAndRejectsOtherSchemes() {
        assertEquals("https://example.org/rooms", serverRequest("wss://example.org/rooms").url.toString())
        assertFailsWith<IllegalArgumentException> { serverRequest("https://example.org") }
        assertFailsWith<IllegalArgumentException> { serverRequest("") }
    }
}
