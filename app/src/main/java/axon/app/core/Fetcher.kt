package axon.app.core

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object Fetcher {

    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val u = url.trim()
        // Прямая ссылка на узел или вставленный список/base64 — сети не нужно.
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) {
            return@withContext u
        }
        val conn = URL(u).openConnection() as? HttpURLConnection
            ?: error("Не HTTP(S): $u")
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code: $u")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            normalize(body)
        } finally {
            conn.disconnect()
        }
    }

    // Многие подписочные эндпоинты отдают base64-кодированный список узлов.
    fun normalize(body: String): String {
        val t = body.trim()
        if (t.contains("://")) return t
        return runCatching { String(b64decode(t), Charsets.UTF_8) }.getOrDefault(t)
    }

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
