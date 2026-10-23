package axon.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SelfTestResult(
    val ok: Boolean,
    val report: HygieneReport,
    val notes: List<String>
)

object SelfTest {

    // vmess JSON: ps = "VMess без шифрования", scy = none, tls отсутствует → небезопасный
    private const val VMESS_UNSAFE =
        "vmess://eyJ2IjoiMiIsInBzIjoiVk1lc3Mg0LHQtdC3INGI0LjRhNGA0L7QstCw0L3QuNGPIiwiYWRkIjoidm0tbm90bHMudGVzdCIsInBvcnQiOiI0NDMiLCJpZCI6ImI4MzEzODFkLTYzMjQtNGQ1My1hZDRmLThjZGE0OGIzMDgxMSIsImFpZCI6IjAiLCJzY3kiOiJub25lIiwibmV0IjoidGNwIiwidHlwZSI6Im5vbmUiLCJob3N0IjoiIiwicGF0aCI6IiIsInRscyI6IiIsInNuaSI6IiJ9"

    // vmess JSON: ps = "VMess надёжный", scy = auto, tls = tls → надёжный
    private const val VMESS_OK =
        "vmess://eyJ2IjoiMiIsInBzIjoiVk1lc3Mg0L3QsNC00ZHQttC90YvQuSIsImFkZCI6InZtLW9rLnRlc3QiLCJwb3J0IjoiNDQzIiwiaWQiOiJiODMxMzgxZC02MzI0LTRkNTMtYWQ0Zi04Y2RhNDhiMzA4MTEiLCJhaWQiOiIwIiwic2N5IjoiYXV0byIsIm5ldCI6InRjcCIsInR5cGUiOiJub25lIiwiaG9zdCI6IiIsInBhdGgiOiIiLCJ0bHMiOiJ0bHMiLCJzbmkiOiIifQ=="

    // 10 встроенных узлов: 4 надёжных, 1 дубль, 4 небезопасных, 1 битый
    private val payload = listOf(
        // надёжные
        "vless://a2c3d4e5-f6a7-8901-b234-c5d6e7f8a901@vless-ok.test:443?encryption=none&security=tls&sni=vless-ok.test#VLESS надёжный",
        VMESS_OK,
        "trojan://s3cretPass@trojan-ok.test:443?security=tls&sni=trojan-ok.test#Trojan надёжный",
        "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpzc2tleQ==@ss-ok.test:8388#SS надёжный",
        // дубль первого узла (тот же протокол/хост/порт/креденшл)
        "vless://a2c3d4e5-f6a7-8901-b234-c5d6e7f8a901@vless-ok.test:443?encryption=none&security=tls#Дубликат первого",
        // небезопасные
        "vless://c1c2c3c4-d5e6-f7a8-b9c0-d1e2f3a4b5c6@vless-notls.test:80?encryption=none&security=none#VLESS без TLS",
        VMESS_UNSAFE,
        "trojan://pass123@trojan-notls.test:80?security=none#Trojan без TLS",
        "ss://cmM0LW1kNTpwYXNz@ss-legacy.test:8388#SS устаревший шифр",
        // битый
        "vless://@broken.test:0#Битый"
    ).joinToString("\n")

    suspend fun run(): SelfTestResult = withContext(Dispatchers.Default) {
        val parsed = ProxyParser.parseAll(payload)
        val (kept, report) = Hygiene.clean(parsed)

        val notes = ArrayList<String>()
        if (parsed.size != 10) notes.add("Парсер вернул ${parsed.size} узлов вместо 10")
        if (report.kept != 4) notes.add("Оставлено ${report.kept} вместо 4")
        if (report.duplicates != 1) notes.add("Дубликатов ${report.duplicates} вместо 1")
        if (report.unsafe != 4) notes.add("Небезопасных ${report.unsafe} вместо 4")
        if (report.malformed != 1) notes.add("Битых ${report.malformed} вместо 1")

        val ok = notes.isEmpty() && kept.size == 4
        SelfTestResult(ok = ok, report = report, notes = notes)
    }
}
