package axon.app.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

object SubscriptionStore {

    private const val PREFS = "axon_store"
    private const val KEY_SUBS = "subscriptions"
    private const val KEY_NODES = "nodes"

    private lateinit var prefs: SharedPreferences

    val subscriptions = mutableStateListOf<Subscription>()
    val nodes = mutableStateListOf<ProxyNode>()

    var lastReport by mutableStateOf<HygieneReport?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        load()
    }

    fun addSubscription(name: String, url: String) {
        val sub = Subscription(
            id = System.currentTimeMillis().toString(36),
            name = name.trim().ifBlank { "Подписка ${subscriptions.size + 1}" },
            url = url.trim()
        )
        subscriptions.add(sub)
        persist()
    }

    fun removeSubscription(id: String) {
        subscriptions.removeAll { it.id == id }
        nodes.removeAll { it.subscriptionId == id }
        if (lastReport != null && subscriptions.isEmpty()) lastReport = null
        persist()
    }

    suspend fun refresh(sub: Subscription): HygieneReport = refresh(listOf(sub))

    suspend fun refreshAll(): HygieneReport = refresh(subscriptions.toList())

    private suspend fun refresh(targets: List<Subscription>): HygieneReport {
        busy = true
        try {
            val fetched = ArrayList<ProxyNode>()
            for (s in targets) {
                val body = runCatching { Fetcher.fetch(s.url) }.getOrElse { "" }
                fetched.addAll(ProxyParser.parseAll(body).map { it.copy(subscriptionId = s.id) })
            }
            val targetIds = targets.map { it.id }.toSet()
            nodes.removeAll { it.subscriptionId in targetIds }
            val (kept, report) = Hygiene.clean(fetched)
            nodes.addAll(kept)
            lastReport = report
            persist()
            return report
        } finally {
            busy = false
        }
    }

    private fun load() {
        subscriptions.clear()
        nodes.clear()
        runCatching {
            val subsJson = JSONArray(prefs.getString(KEY_SUBS, "[]"))
            for (i in 0 until subsJson.length()) {
                val o = subsJson.getJSONObject(i)
                subscriptions.add(
                    Subscription(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        url = o.getString("url"),
                        active = o.optBoolean("active", true)
                    )
                )
            }
            val nodesJson = JSONArray(prefs.getString(KEY_NODES, "[]"))
            for (i in 0 until nodesJson.length()) {
                nodes.add(nodeFromJson(nodesJson.getJSONObject(i)))
            }
        }
    }

    private fun persist() {
        if (!::prefs.isInitialized) return
        val subs = JSONArray()
        subscriptions.forEach {
            subs.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("url", it.url)
                    .put("active", it.active)
            )
        }
        val ns = JSONArray()
        nodes.forEach { ns.put(nodeToJson(it)) }
        prefs.edit()
            .putString(KEY_SUBS, subs.toString())
            .putString(KEY_NODES, ns.toString())
            .apply()
    }

    private fun nodeToJson(n: ProxyNode): JSONObject = JSONObject()
        .put("protocol", n.protocol)
        .put("name", n.name)
        .put("host", n.host)
        .put("port", n.port)
        .put("credential", n.credential)
        .put("security", n.security)
        .put("cipher", n.cipher)
        .put("allowInsecure", n.allowInsecure)
        .put("subscriptionId", n.subscriptionId)
        .put("raw", n.raw)

    private fun nodeFromJson(o: JSONObject): ProxyNode = ProxyNode(
        protocol = o.getString("protocol"),
        name = o.getString("name"),
        host = o.getString("host"),
        port = o.getInt("port"),
        credential = o.getString("credential"),
        security = o.optString("security", "none"),
        cipher = o.optString("cipher", ""),
        allowInsecure = o.optBoolean("allowInsecure", false),
        subscriptionId = o.optString("subscriptionId", ""),
        raw = o.optString("raw", "")
    )
}
