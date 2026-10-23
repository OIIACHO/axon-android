package axon.app.core

import android.util.Base64
import org.json.JSONObject
import java.net.URLDecoder

object ProxyParser {

    private val PROTOCOLS = listOf("vless://", "vmess://", "trojan://", "ss://")

    fun parseAll(text: String): List<ProxyNode> =
        text.lines()
            .map { it.trim() }
            .filter { line -> PROTOCOLS.any { p -> line.startsWith(p) } }
            .mapNotNull { uri -> runCatching { parse(uri) }.getOrNull() }

    fun parse(uri: String): ProxyNode? = when {
        uri.startsWith("vless://") -> parseGeneric(uri.removePrefix("vless://"), "vless", uri)
        uri.startsWith("vmess://") -> parseVmess(uri.removePrefix("vmess://"), uri)
        uri.startsWith("trojan://") -> parseGeneric(uri.removePrefix("trojan://"), "trojan", uri)
        uri.startsWith("ss://") -> parseShadowsocks(uri.removePrefix("ss://"), uri)
        else -> null
    }

    // vless://uuid@host:port?params#name   |   trojan://password@host:port?params#name
    private fun parseGeneric(body: String, protocol: String, raw: String): ProxyNode? {
        val name = urlDecode(body.substringAfter('#', ""))
        val noHash = body.substringBefore('#')
        val query = noHash.substringAfter('?', "")
        val main = noHash.substringBefore('?')
        if (!main.contains('@')) return null
        val cred = main.substringBefore('@')
        val hostPort = main.substringAfter('@')
        if (!hostPort.contains(':')) return null
        val host = stripBrackets(hostPort.substringBeforeLast(':'))
        val port = hostPort.substringAfterLast(':').filter { it.isDigit() }.toIntOrNull() ?: return null
        if (host.isBlank()) return null
        val params = parseQuery(query)
        val security = params["security"]?.lowercase()
            ?: if (protocol == "trojan") "tls" else "none"
        return ProxyNode(
            protocol = protocol,
            name = name.ifBlank { "$protocol-$host" },
            host = host,
            port = port,
            credential = cred,
            security = security,
            cipher = params["encryption"] ?: "",
            allowInsecure = params["allowInsecure"] == "1" || params["allowInsecure"].equals("true", true),
            raw = raw
        )
    }

    // vmess://base64(JSON)
    private fun parseVmess(b64: String, raw: String): ProxyNode? {
        val json = runCatching { String(b64decode(b64), Charsets.UTF_8) }.getOrNull() ?: return null
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val host = o.optString("add")
        if (host.isBlank()) return null
        val port = when (val p = o.opt("port")) {
            is Int -> p
            is String -> p.toIntOrNull() ?: return null
            else -> return null
        }
        val tls = o.optString("tls").lowercase()
        return ProxyNode(
            protocol = "vmess",
            name = o.optString("ps").ifBlank { "vmess-$host" },
            host = host,
            port = port,
            credential = o.optString("id"),
            security = if (tls.isBlank()) "none" else tls,
            cipher = o.optString("scy", "auto").lowercase(),
            allowInsecure = o.optBoolean("allowInsecure", false),
            raw = raw
        )
    }

    // ss://base64(method:password)@host:port#name  |  ss://base64(method:password@host:port)#name
    private fun parseShadowsocks(body: String, raw: String): ProxyNode? {
        val name = urlDecode(body.substringAfter('#', ""))
        val main = body.substringBefore('#')
        if (main.contains('@')) {
            val userInfo = main.substringBefore('@')
            val hostPort = main.substringAfter('@')
            val (host, port) = splitHostPort(hostPort) ?: return null
            val decoded = runCatching { String(b64decode(userInfo), Charsets.UTF_8) }.getOrElse { userInfo }
            if (!decoded.contains(':')) return null
            return ssNode(
                method = decoded.substringBefore(':'),
                password = decoded.substringAfter(':'),
                host = host,
                port = port,
                name = name,
                raw = raw
            )
        }
        val decoded = runCatching { String(b64decode(main), Charsets.UTF_8) }.getOrNull() ?: return null
        val cred = decoded.substringBefore('@')
        val hostPort = decoded.substringAfter('@')
        val (host, port) = splitHostPort(hostPort) ?: return null
        if (!cred.contains(':')) return null
        return ssNode(
            method = cred.substringBefore(':'),
            password = cred.substringAfter(':'),
            host = host,
            port = port,
            name = name,
            raw = raw
        )
    }

    private fun ssNode(
        method: String,
        password: String,
        host: String,
        port: Int,
        name: String,
        raw: String
    ): ProxyNode? {
        if (method.isBlank() || password.isBlank()) return null
        return ProxyNode(
            protocol = "ss",
            name = name.ifBlank { "ss-$host" },
            host = host,
            port = port,
            credential = password,
            security = "psk",
            cipher = method.lowercase(),
            raw = raw
        )
    }

    private fun splitHostPort(s: String): Pair<String, Int>? {
        if (!s.contains(':')) return null
        val host = stripBrackets(s.substringBeforeLast(':'))
        val port = s.substringAfterLast(':').filter { it.isDigit() }.toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null
        return host to port
    }

    private fun parseQuery(q: String): Map<String, String> {
        if (q.isBlank()) return emptyMap()
        return q.split('&')
            .filter { it.contains('=') }
            .associate { urlDecode(it.substringBefore('=')) to urlDecode(it.substringAfter('=')) }
    }

    private fun urlDecode(s: String): String =
        runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun stripBrackets(h: String): String =
        h.removePrefix("[").removeSuffix("]")

    private fun b64decode(s: String): ByteArray {
        val t = s.replace('-', '+')
            .replace('_', '/')
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        val padded = when (t.length % 4) {
            2 -> t + "=="
            3 -> t + "="
            else -> t
        }
        return Base64.decode(padded, Base64.DEFAULT)
    }
}
