package pl.tuneprint

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps

private val cloudflare by lazy {
    DnsOverHttps.Builder()
        .client(OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build())
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
        .build()
}

internal class TunePrintDns(
    private val system: Dns = Dns.SYSTEM,
    private val publicLookup: (String) -> List<InetAddress> = { cloudflare.lookup(it) },
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        try {
            return system.lookup(hostname)
        } catch (error: UnknownHostException) {
            val host = hostname.removeSuffix(".")
            if (!host.equals("tuneprint.mliczek.com", ignoreCase = true)) throw error
            return publicLookup(host)
        }
    }
}
