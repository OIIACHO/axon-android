package axon.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import axon.app.core.ProxyNode
import axon.app.core.SelfTest
import axon.app.core.SelfTestResult
import axon.app.core.Subscription
import axon.app.core.SubscriptionStore
import axon.app.core.TcpProbe
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AxonRoot() {
    AxonTheme {
        var openNodes by remember { mutableStateOf<String?>(null) }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (openNodes == null) "AXON — менеджер подписок" else "Узлы подписки")
                    },
                    navigationIcon = {
                        if (openNodes != null) {
                            IconButton(onClick = { openNodes = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                            }
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            val target = openNodes
            if (target == null) {
                MainTabs(Modifier.padding(padding)) { openNodes = it }
            } else {
                NodesScreen(Modifier.padding(padding), target)
            }
        }
    }
}

@Composable
private fun MainTabs(modifier: Modifier, onOpenNodes: (String) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Подписки") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Самотест") })
        }
        when (tab) {
            0 -> SubscriptionsTab(onOpenNodes)
            1 -> SelfTestTab()
        }
    }
}

@Composable
private fun SubscriptionsTab(onOpenNodes: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val store = SubscriptionStore
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Добавьте подписку: https-ссылка, base64-список или узлы vless/vmess/trojan/ss. " +
                "При каждом обновлении AXON удаляет дубли, небезопасные и битые узлы.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Название") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Ссылка на подписку или узел") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        store.addSubscription(name, url)
                        name = ""
                        url = ""
                    }
                },
                enabled = url.isNotBlank()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Добавить")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { scope.launch { store.refreshAll() } },
                enabled = store.subscriptions.isNotEmpty() && !store.busy
            ) {
                Text(if (store.busy) "Обновление…" else "Обновить все")
            }
        }

        store.lastReport?.let { r ->
            Spacer(Modifier.height(12.dp))
            Text(
                "Получено ${r.received} · Оставлено ${r.kept} · Дубли ${r.duplicates} · " +
                    "Небезопасные ${r.unsafe} · Битые ${r.malformed}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))
        if (store.subscriptions.isEmpty()) {
            Text("Подписок пока нет.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(store.subscriptions, key = { it.id }) { sub ->
                    SubscriptionCard(sub, onOpenNodes)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(sub: Subscription, onOpenNodes: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val nodeCount = SubscriptionStore.nodes.count { it.subscriptionId == sub.id }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = { onOpenNodes(sub.id) })) {
        Column(Modifier.padding(12.dp)) {
            Text(sub.name, style = MaterialTheme.typography.titleSmall)
            Text(
                sub.url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Узлов после чистки: $nodeCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Row {
                TextButton(onClick = { scope.launch { SubscriptionStore.refresh(sub) } }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Обновить")
                }
                TextButton(onClick = { SubscriptionStore.removeSubscription(sub.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Удалить")
                }
            }
        }
    }
}

@Composable
private fun SelfTestTab() {
    var result by remember { mutableStateOf<SelfTestResult?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Самотест", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Прогоняет 10 встроенных узлов через парсер и движок гигиены. " +
                "Ожидание: 4 надёжных, 1 дубль, 4 небезопасных, 1 битый.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                running = true
                scope.launch {
                    result = SelfTest.run()
                    running = false
                }
            },
            enabled = !running
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (running) "Выполняется…" else "Запустить самотест")
        }

        result?.let { r ->
            Spacer(Modifier.height(12.dp))
            if (r.ok) {
                Text(
                    "✅ Самотест пройден",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    "❌ Самотест не пройден",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
                r.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(8.dp))
            Text("Получено: ${r.report.received}", style = MaterialTheme.typography.bodySmall)
            Text("Оставлено: ${r.report.kept}", style = MaterialTheme.typography.bodySmall)
            Text("Дубли: ${r.report.duplicates}", style = MaterialTheme.typography.bodySmall)
            Text("Небезопасные: ${r.report.unsafe}", style = MaterialTheme.typography.bodySmall)
            Text("Битые: ${r.report.malformed}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("Детали гигиены:", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.fillMaxWidth()) {
                items(r.report.details) { d ->
                    Text(d, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun NodesScreen(modifier: Modifier, subscriptionId: String) {
    val sub = SubscriptionStore.subscriptions.firstOrNull { it.id == subscriptionId }
    val nodes = SubscriptionStore.nodes.filter { it.subscriptionId == subscriptionId }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(sub?.name ?: "Узлы", style = MaterialTheme.typography.titleMedium)
        sub?.let {
            Text(
                it.url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        if (nodes.isEmpty()) {
            Text("Узлов нет. Вернитесь назад и обновите подписку.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(nodes, key = { it.identityKey }) { node ->
                    NodeRow(node)
                }
            }
        }
    }
}

@Composable
private fun NodeRow(node: ProxyNode) {
    var probing by remember { mutableStateOf(false) }
    var reachable by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(node.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${node.protocol} · ${node.host}:${node.port} · " +
                        if (node.security.equals("none", true) || node.security.isBlank())
                            "без TLS"
                        else node.security,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    scope.launch {
                        probing = true
                        reachable = TcpProbe.reachable(node.host, node.port)
                        probing = false
                    }
                },
                enabled = !probing
            ) {
                if (probing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    when (reachable) {
                        true -> Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Узел доступен",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        false -> Icon(
                            Icons.Filled.Cancel,
                            contentDescription = "Узел недоступен",
                            tint = MaterialTheme.colorScheme.error
                        )
                        null -> Icon(Icons.Filled.PlayArrow, contentDescription = "Проверить узел")
                    }
                }
            }
        }
    }
}
