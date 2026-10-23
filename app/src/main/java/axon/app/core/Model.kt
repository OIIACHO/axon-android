package axon.app.core

data class ProxyNode(
    val protocol: String,
    val name: String,
    val host: String,
    val port: Int,
    val credential: String,
    val security: String = "none",
    val cipher: String = "",
    val allowInsecure: Boolean = false,
    val subscriptionId: String = "",
    val raw: String = ""
) {
    val identityKey: String
        get() = "$protocol|${host.lowercase()}|$port|$credential"
}

data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val active: Boolean = true
)

data class HygieneReport(
    val received: Int,
    val kept: Int,
    val duplicates: Int,
    val unsafe: Int,
    val unreachable: Int,
    val malformed: Int,
    val details: List<String> = emptyList()
)
