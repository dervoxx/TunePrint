package pl.tuneprint

import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okhttp3.Dns

class TunePrintDnsTest {
    private val answer = listOf(InetAddress.getLoopbackAddress())
    private fun resolver(resolve: (String) -> List<InetAddress>) = object : Dns {
        override fun lookup(hostname: String) = resolve(hostname)
    }

    @Test fun prefersSystemAnswer() {
        val dns = TunePrintDns(resolver { answer }) { error("Unexpected public lookup") }
        assertEquals(answer, dns.lookup("tuneprint.mliczek.com"))
    }

    @Test fun fallsBackOnlyForGameHostname() {
        val dns = TunePrintDns(resolver { throw UnknownHostException(it) }) {
            assertEquals("tuneprint.mliczek.com", it)
            answer
        }
        assertEquals(answer, dns.lookup("tuneprint.mliczek.com."))
        assertFailsWith<UnknownHostException> { dns.lookup("private.tuneprint.mliczek.com") }
    }
}
