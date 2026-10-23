package axon.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object TcpProbe {

    suspend fun reachable(host: String, port: Int, timeoutMs: Int = 4_000): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { s ->
                    s.tcpNoDelay = true
                    s.connect(InetSocketAddress(host, port), timeoutMs)
                    s.isConnected
                }
            }.getOrDefault(false)
        }
}
