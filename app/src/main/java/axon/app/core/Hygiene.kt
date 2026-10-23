package axon.app.core

object Hygiene {

    private val LEGACY_SS_CIPHERS = setOf(
        "rc4", "rc4-md5", "aes-128-cfb", "aes-192-cfb", "aes-256-cfb",
        "aes-128-ctr", "aes-192-ctr", "aes-256-ctr",
        "camellia-128-cfb", "camellia-192-cfb", "camellia-256-cfb",
        "chacha20", "chacha20-ietf", "bf-cfb", "cast5-cfb", "des-cfb",
        "seed-cfb", "table", "none"
    )

    fun clean(nodes: List<ProxyNode>): Pair<List<ProxyNode>, HygieneReport> {
        val seen = HashSet<String>()
        val kept = ArrayList<ProxyNode>()
        var duplicates = 0
        var unsafe = 0
        var malformed = 0
        val details = ArrayList<String>()

        for (n in nodes) {
            if (!isValid(n)) {
                malformed++
                details.add("Битый: ${display(n)}")
                continue
            }
            if (!seen.add(n.identityKey)) {
                duplicates++
                details.add("Дубликат: ${display(n)}")
                continue
            }
            val reason = unsafeReason(n)
            if (reason != null) {
                unsafe++
                details.add("Небезопасный ($reason): ${display(n)}")
                continue
            }
            kept.add(n)
        }

        val report = HygieneReport(
            received = nodes.size,
            kept = kept.size,
            duplicates = duplicates,
            unsafe = unsafe,
            unreachable = 0,
            malformed = malformed,
            details = details
        )
        return kept to report
    }

    fun isValid(n: ProxyNode): Boolean =
        n.host.isNotBlank() && n.port in 1..65535 && n.credential.isNotBlank()

    fun unsafeReason(n: ProxyNode): String? {
        when (n.protocol.lowercase()) {
            "vless", "trojan" -> {
                if (n.security.isBlank() || n.security.equals("none", true)) return "нет TLS"
                if (n.allowInsecure) return "allowInsecure"
            }
            "vmess" -> {
                if (n.cipher.equals("none", true)) return "scy=none"
                if (n.security.isBlank() || n.security.equals("none", true)) return "нет TLS"
                if (n.allowInsecure) return "allowInsecure"
            }
            "ss" -> {
                val c = n.cipher.lowercase()
                if (c in LEGACY_SS_CIPHERS) return "устаревший шифр $c"
            }
        }
        return null
    }

    private fun display(n: ProxyNode) =
        n.name.ifBlank { "${n.protocol}-${n.host}:${n.port}" }
}
