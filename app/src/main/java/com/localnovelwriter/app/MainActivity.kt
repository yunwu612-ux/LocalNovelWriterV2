package com.localnovelwriter.app

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.Executors
import sh.calvin.reorderable.*

private data class Volume(val id: Long, var title: String)
private data class Chapter(
    val id: Long,
    var title: String,
    var content: String,
    var volumeId: Long = 0L,
    var status: String = "草稿",
    // Runtime-only cache; excluded from project JSON.
    var cachedWordCount: Int = -1
)
private fun Chapter.wordCount(): Int {
    if (cachedWordCount < 0) cachedWordCount = content.count { !it.isWhitespace() }
    return cachedWordCount
}

private data class CharacterProfile(
    val id: Long,
    var name: String,
    var identity: String,
    var appearance: String,
    var personality: String,
    var background: String,
    var abilities: String,
    var relationships: String,
    var notes: String
)
private data class WorldEntry(
    val id: Long,
    var name: String,
    var geography: String,
    var races: String,
    var history: String,
    var factions: String,
    var rules: String,
    var notes: String
)
private data class Novel(
    val id: Long,
    var title: String,
    val chapters: MutableList<Chapter> = mutableListOf(),
    val characters: MutableList<CharacterProfile> = mutableListOf(),
    val worlds: MutableList<WorldEntry> = mutableListOf(),
    val volumes: MutableList<Volume> = mutableListOf()
)
private data class TrashItem(
    val id: Long,
    val novelId: Long,
    val novelTitle: String,
    val kind: String,
    val title: String,
    val content: String = "",
    val volumeId: Long = 0L,
    val status: String = "草稿"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NovelApp(this) }
    }
}

private class LocalStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("novel_data", Context.MODE_PRIVATE)
    private val statsPrefs = context.getSharedPreferences("novel_stats", Context.MODE_PRIVATE)
    private val trashPrefs = context.getSharedPreferences("novel_trash", Context.MODE_PRIVATE)

    fun load(): MutableList<Novel> = try {
        val array = JSONArray(prefs.getString("data", "[]"))
        MutableList(array.length()) { i ->
            val o = array.getJSONObject(i)
            val chapters = mutableListOf<Chapter>()
            val characters = mutableListOf<CharacterProfile>()
            val worlds = mutableListOf<WorldEntry>()
            val volumes = mutableListOf<Volume>()
            o.optJSONArray("volumes")?.let { a ->
                for (j in 0 until a.length()) {
                    val x = a.getJSONObject(j); volumes += Volume(x.getLong("id"), x.optString("title"))
                }
            }
            o.optJSONArray("chapters")?.let { a ->
                for (j in 0 until a.length()) {
                    val x = a.getJSONObject(j)
                    chapters += Chapter(x.getLong("id"), x.optString("title"), x.optString("content"), x.optLong("volumeId", 0L), x.optString("status", "草稿"))
                }
            }
            o.optJSONArray("characters")?.let { a ->
                for (j in 0 until a.length()) {
                    val x = a.getJSONObject(j)
                    characters += CharacterProfile(x.getLong("id"), x.optString("name"), x.optString("identity"), x.optString("appearance"), x.optString("personality"), x.optString("background"), x.optString("abilities"), x.optString("relationships"), x.optString("notes"))
                }
            }
            o.optJSONArray("worlds")?.let { a ->
                for (j in 0 until a.length()) {
                    val x = a.getJSONObject(j)
                    worlds += WorldEntry(x.getLong("id"), x.optString("name"), x.optString("geography"), x.optString("races"), x.optString("history"), x.optString("factions"), x.optString("rules"), x.optString("notes"))
                }
            }
            if (volumes.isEmpty()) volumes += Volume(o.getLong("id") + 1_000_000L, "第一卷")
            chapters.forEach { if (it.volumeId == 0L) it.volumeId = volumes.first().id }
            Novel(o.getLong("id"), o.optString("title"), chapters, characters, worlds, volumes)
        }
    } catch (_: Exception) { mutableListOf() }

    fun save(novels: List<Novel>) {
        saveSerialized(serialize(novels))
    }

    fun serialize(novels: List<Novel>): String {
        val array = JSONArray()
        novels.forEach { novel ->
            val o = JSONObject().apply {
                put("id", novel.id); put("title", novel.title)
                put("volumes", JSONArray().apply { novel.volumes.forEach { v -> put(JSONObject().apply { put("id", v.id); put("title", v.title) }) } })
                put("chapters", JSONArray().apply { novel.chapters.forEach { c -> put(JSONObject().apply { put("id", c.id); put("title", c.title); put("content", c.content); put("volumeId", c.volumeId); put("status", c.status) }) } })
                put("characters", JSONArray().apply { novel.characters.forEach { c -> put(JSONObject().apply { put("id", c.id); put("name", c.name); put("identity", c.identity); put("appearance", c.appearance); put("personality", c.personality); put("background", c.background); put("abilities", c.abilities); put("relationships", c.relationships); put("notes", c.notes) }) } })
                put("worlds", JSONArray().apply { novel.worlds.forEach { w -> put(JSONObject().apply { put("id", w.id); put("name", w.name); put("geography", w.geography); put("races", w.races); put("history", w.history); put("factions", w.factions); put("rules", w.rules); put("notes", w.notes) }) } })
            }
            array.put(o)
        }
        return array.toString()
    }

    fun saveSerialized(json: String) {
        prefs.edit().putString("data", json).apply()
    }

    fun loadTrash(): MutableList<TrashItem> = try {
        val array = JSONArray(trashPrefs.getString("data", "[]"))
        MutableList(array.length()) { i ->
            val x = array.getJSONObject(i)
            TrashItem(x.getLong("id"), x.getLong("novelId"), x.optString("novelTitle"), x.optString("kind"), x.optString("title"), x.optString("content"), x.optLong("volumeId", 0L), x.optString("status", "草稿"))
        }
    } catch (_: Exception) { mutableListOf() }

    fun saveTrash(items: List<TrashItem>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply { put("id", item.id); put("novelId", item.novelId); put("novelTitle", item.novelTitle); put("kind", item.kind); put("title", item.title); put("content", item.content); put("volumeId", item.volumeId); put("status", item.status) }) }
        trashPrefs.edit().putString("data", array.toString()).apply()
    }

    fun backupProjectSnapshot(json: String): String? {
        return runCatching {
            val dir = File(appContext.filesDir, "sync_backups")
            dir.mkdirs()
            val file = File(dir, "backup_${System.currentTimeMillis()}.lnw")
            file.writeText(json, Charsets.UTF_8)
            val backups = dir.listFiles() ?: emptyArray()
            backups.sortedByDescending { backupFile -> backupFile.lastModified() }
                .drop(5)
                .forEach { oldBackup -> oldBackup.delete() }
            file.absolutePath
        }.getOrNull()
    }

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    fun recordWordProgress(novels: List<Novel>) {
        val today = todayKey(); val week = SimpleDateFormat("yyyy-'W'ww", Locale.US).format(Date()); val storedWeek = statsPrefs.getString("week", "")
        val total = novels.sumOf { n -> n.chapters.sumOf { it.content.count { ch -> !ch.isWhitespace() } } }.toLong()
        var lastTotal = statsPrefs.getLong("lastTotal", 0L)
        if (storedWeek != week) { statsPrefs.edit().putString("week", week).putLong("weekWords", 0L).putLong("lastTotal", total).apply(); lastTotal = total }
        val delta = (total - lastTotal).coerceAtLeast(0L)
        if (delta > 0L) statsPrefs.edit().putLong("weekWords", statsPrefs.getLong("weekWords", 0L) + delta).putLong("day_$today", statsPrefs.getLong("day_$today", 0L) + delta).putLong("lastTotal", total).apply()
        else if (statsPrefs.getLong("lastTotal", Long.MIN_VALUE) == Long.MIN_VALUE) statsPrefs.edit().putLong("lastTotal", total).apply()
    }
    fun totalWordCount(novels: List<Novel>): Int = novels.sumOf { n ->
        n.chapters.sumOf { chapter -> chapter.content.count { ch -> !ch.isWhitespace() } }
    }

    fun weeklyWords(): Long = statsPrefs.getLong("weekWords", 0L)
    fun todayWords(): Long = statsPrefs.getLong("day_${todayKey()}", 0L)
    fun weeklyGoal(): Int = statsPrefs.getInt("weeklyGoal", 5000)
    fun setWeeklyGoal(value: Int) { statsPrefs.edit().putInt("weeklyGoal", value).apply() }
}

private fun exportText(novel: Novel, chapters: List<Chapter>): String {
    val out = StringBuilder()
    out.append("# ").append(novel.title).append("\n\n")
    val selected = chapters.toSet()

    novel.volumes.forEach { volume ->
        val list = chapters.filter { it.volumeId == volume.id }
        if (list.isNotEmpty()) {
            out.append("## ").append(volume.title).append("\n\n")
            list.forEach { c ->
                out.append("### ").append(c.title).append("\n\n")
                out.append(c.content).append("\n\n")
            }
        }
    }

    val ungrouped = chapters.filter { c ->
        novel.volumes.none { it.id == c.volumeId } && selected.contains(c)
    }
    if (ungrouped.isNotEmpty()) {
        out.append("## 未分卷\n\n")
        ungrouped.forEach { c ->
            out.append("### ").append(c.title).append("\n\n")
            out.append(c.content).append("\n\n")
        }
    }
    return out.toString().trimEnd() + "\n"
}

private fun importNovelFromText(text: String, forcedId: Long? = null): Novel {
    val lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    var title = "导入小说"
    var volume = Volume(System.currentTimeMillis() + 1000L, "第一卷")
    val volumes = mutableListOf(volume)
    val chapters = mutableListOf<Chapter>()
    var current: Chapter? = null
    fun finish() { current?.let { chapters += it }; current = null }
    for (raw in lines) {
        val line = raw.trimEnd()
        when {
            line.startsWith("# ") && !line.startsWith("## ") -> title = line.removePrefix("# ").trim().ifBlank { title }
            line.startsWith("## ") && !line.startsWith("### ") -> { finish(); volume = Volume(System.currentTimeMillis() + volumes.size + 1000L, line.removePrefix("## ").trim().ifBlank { "第${volumes.size + 1}卷" }); volumes += volume }
            line.startsWith("### ") -> { finish(); current = Chapter(System.currentTimeMillis() + chapters.size + 1L, line.removePrefix("### ").trim().ifBlank { "第${chapters.size + 1}章" }, "", volume.id) }
            line.matches(Regex("^第.+章$")) && current == null -> current = Chapter(System.currentTimeMillis() + chapters.size + 1L, line.trim(), "", volume.id)
            else -> {
                if (current == null) {
                    current = Chapter(System.currentTimeMillis() + chapters.size + 1L, "第一章", "", volume.id)
                }
                current = current?.copy(
                    content = if (current!!.content.isEmpty()) line else current!!.content + "\n" + line
                )
            }
        }
    }
    finish()
    if (chapters.isEmpty()) chapters += Chapter(System.currentTimeMillis() + 1L, "第一章", text, volume.id)
    return Novel(forcedId ?: System.currentTimeMillis(), title, chapters, volumes = volumes)
}

private fun novelToJson(novel: Novel): String {
    val o = JSONObject().apply {
        put("id", novel.id); put("title", novel.title)
        put("volumes", JSONArray().apply { novel.volumes.forEach { v -> put(JSONObject().apply { put("id", v.id); put("title", v.title) }) } })
        put("chapters", JSONArray().apply { novel.chapters.forEach { c -> put(JSONObject().apply { put("id", c.id); put("title", c.title); put("content", c.content); put("volumeId", c.volumeId); put("status", c.status) }) } })
        put("characters", JSONArray().apply { novel.characters.forEach { c -> put(JSONObject().apply { put("id", c.id); put("name", c.name); put("identity", c.identity); put("appearance", c.appearance); put("personality", c.personality); put("background", c.background); put("abilities", c.abilities); put("relationships", c.relationships); put("notes", c.notes) }) } })
        put("worlds", JSONArray().apply { novel.worlds.forEach { w -> put(JSONObject().apply { put("id", w.id); put("name", w.name); put("geography", w.geography); put("races", w.races); put("history", w.history); put("factions", w.factions); put("rules", w.rules); put("notes", w.notes) }) } })
    }
    return o.toString()
}

private fun novelFromJson(json: String, fallbackId: Long): Novel {
    val o = JSONObject(json)
    val volumes = mutableListOf<Volume>()
    val chapters = mutableListOf<Chapter>()
    val characters = mutableListOf<CharacterProfile>()
    val worlds = mutableListOf<WorldEntry>()
    o.optJSONArray("volumes")?.let { a -> for (i in 0 until a.length()) { val x=a.getJSONObject(i); volumes += Volume(x.getLong("id"), x.optString("title")) } }
    o.optJSONArray("chapters")?.let { a -> for (i in 0 until a.length()) { val x=a.getJSONObject(i); chapters += Chapter(x.getLong("id"), x.optString("title"), x.optString("content"), x.optLong("volumeId", 0L), x.optString("status", "草稿")) } }
    o.optJSONArray("characters")?.let { a -> for (i in 0 until a.length()) { val x=a.getJSONObject(i); characters += CharacterProfile(x.getLong("id"), x.optString("name"), x.optString("identity"), x.optString("appearance"), x.optString("personality"), x.optString("background"), x.optString("abilities"), x.optString("relationships"), x.optString("notes")) } }
    o.optJSONArray("worlds")?.let { a -> for (i in 0 until a.length()) { val x=a.getJSONObject(i); worlds += WorldEntry(x.getLong("id"), x.optString("name"), x.optString("geography"), x.optString("races"), x.optString("history"), x.optString("factions"), x.optString("rules"), x.optString("notes")) } }
    if (volumes.isEmpty()) volumes += Volume(fallbackId + 1000L, "第一卷")
    chapters.forEach { if (it.volumeId == 0L) it.volumeId = volumes.first().id }
    return Novel(o.optLong("id", fallbackId), o.optString("title", "未命名小说"), chapters, characters, worlds, volumes)
}


private fun projectToJson(novels: List<Novel>, trash: List<TrashItem>): String {
    val root = JSONObject()
    root.put("format", "LocalNovelWriterProject")
    root.put("version", 1)
    root.put("exportedAt", System.currentTimeMillis())
    root.put("novels", JSONArray().apply { novels.forEach { put(JSONObject(novelToJson(it))) } })
    root.put("trash", JSONArray().apply {
        trash.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id); put("novelId", item.novelId); put("novelTitle", item.novelTitle)
                put("kind", item.kind); put("title", item.title); put("content", item.content)
                put("volumeId", item.volumeId); put("status", item.status)
            })
        }
    })
    return root.toString()
}

private data class ProjectSnapshot(val novels: MutableList<Novel>, val trash: MutableList<TrashItem>)

private fun projectFromJson(json: String): ProjectSnapshot {
    val root = JSONObject(json)
    require(root.optString("format") == "LocalNovelWriterProject") { "不是 LocalNovelWriter 项目文件" }
    val novels = mutableListOf<Novel>()
    root.optJSONArray("novels")?.let { a ->
        for (i in 0 until a.length()) novels += novelFromJson(a.getJSONObject(i).toString(), System.currentTimeMillis() + i)
    }
    val trash = mutableListOf<TrashItem>()
    root.optJSONArray("trash")?.let { a ->
        for (i in 0 until a.length()) {
            val x = a.getJSONObject(i)
            trash += TrashItem(x.getLong("id"), x.getLong("novelId"), x.optString("novelTitle"), x.optString("kind"), x.optString("title"), x.optString("content"), x.optLong("volumeId", 0L), x.optString("status", "草稿"))
        }
    }
    return ProjectSnapshot(novels, trash)
}

private class LanSyncManager(
    private val context: Context,
    private val store: LocalStore
) {
    private var server: ServerSocket? = null
    private var serverThread: Thread? = null
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile var running: Boolean = false
        private set
    @Volatile var lastMessage: String = "尚未同步"
        private set


    fun localAddress(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }

    fun start(getSnapshot: () -> String, onReceive: (String) -> Unit) {
        if (running) return
        runCatching {
            val socket = ServerSocket(38471)
            server = socket
            running = true
            serverThread = Thread {
                while (running) {
                    try {
                        val client = socket.accept()
                        executor.execute { handle(client, getSnapshot, onReceive) }
                    } catch (_: Exception) {
                        if (running) lastMessage = "联动服务异常"
                    }
                }
            }.also { it.isDaemon = true; it.start() }
            lastMessage = "电脑联动已开启"
        }.onFailure { lastMessage = "无法开启联动端口：${it.message ?: "未知错误"}" }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
        serverThread = null
        lastMessage = "联动已关闭"
    }

    private fun handle(socket: java.net.Socket, getSnapshot: () -> String, onReceive: (String) -> Unit) {
        socket.use { s ->
            s.soTimeout = 10_000
            val input = s.getInputStream().bufferedReader(Charsets.UTF_8)
            val requestLine = input.readLine() ?: return
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase(Locale.US)] = line.substring(idx + 1).trim()
            }
            val method = requestLine.substringBefore(' ')
            val path = requestLine.substringAfter(' ').substringBefore(' ')
            if (method == "GET" && path == "/api/info") {
                writeResponse(s, 200, JSONObject().apply { put("app", "LocalNovelWriter"); put("version", "2.6"); put("port", 38471) }.toString())
                return
            }
            if (method == "GET" && path == "/api/project") {
                writeResponse(s, 200, getSnapshot())
                mainHandler.post { lastMessage = "电脑已读取手机项目" }
                return
            }
            if (method == "POST" && path == "/api/project") {
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                val body = CharArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(body, read, length - read)
                    if (n <= 0) break
                    read += n
                }
                if (read != length) { writeResponse(s, 400, "请求数据不完整"); return }
                runCatching {
                    JSONObject(String(body).trim())
                    mainHandler.post { onReceive(String(body).trim()) }
                    writeResponse(s, 200, JSONObject().apply { put("ok", true); put("message", "同步数据已接收") }.toString())
                    mainHandler.post { lastMessage = "电脑已发送项目到手机" }
                }.onFailure { writeResponse(s, 400, "项目数据无效") }
                return
            }
            writeResponse(s, 404, "Not Found")
        }
    }

    private fun writeResponse(socket: java.net.Socket, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
        out.write("HTTP/1.1 $code ${if (code == 200) "OK" else "Bad Request"}\r\n")
        out.write("Content-Type: application/json; charset=utf-8\r\n")
        out.write("Content-Length: ${bytes.size}\r\n")
        out.write("Connection: close\r\n\r\n")
        out.flush()
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
    }

    suspend fun discoverComputers(): List<String> = withContext(Dispatchers.IO) {
        val ip = localAddress() ?: return@withContext emptyList()
        val parts = ip.split('.')
        if (parts.size != 4) return@withContext emptyList()
        val prefix = parts.take(3).joinToString(".")
        val found = mutableListOf<String>()
        for (start in 1..254 step 32) {
            val jobs = (start until minOf(start + 32, 255)).map { host ->
                async {
                    val base = "http://$prefix.$host:38471"
                    runCatching {
                        val url = URL(base + "/api/info")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"; connectTimeout = 220; readTimeout = 350
                        }
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        conn.disconnect()
                        val obj = JSONObject(body)
                        if (obj.optString("app") == "LocalNovelWriter" && obj.optString("platform") == "windows") base else null
                    }.getOrNull()
                }
            }
            jobs.awaitAll().forEach { it?.let(found::add) }
        }
        found.distinct()
    }

    suspend fun pullFromComputer(baseUrl: String): Result<String> = runCatching {
        val url = URL(baseUrl.trimEnd('/') + "/api/project")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 5000; readTimeout = 15000
        }
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.also { conn.disconnect() }
    }

    suspend fun testComputer(baseUrl: String): Result<String> = runCatching {
        val url = URL(baseUrl.trimEnd('/') + "/api/info")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 3000; readTimeout = 3000
        }
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.also { conn.disconnect() }
    }

    suspend fun pushToComputer(baseUrl: String, snapshot: String): Result<String> = runCatching {
        val url = URL(baseUrl.trimEnd('/') + "/api/project")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 5000; readTimeout = 15000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) }
        val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        conn.disconnect()
        response
    }
}

@Composable
private fun NovelApp(context: Context) {
    val store = remember { LocalStore(context) }
    val syncManager = remember { LanSyncManager(context, store) }
    var syncDialog by remember { mutableStateOf(false) }
    var syncUrl by remember { mutableStateOf("") }
    var syncMessage by remember { mutableStateOf("") }
    var syncBusy by remember { mutableStateOf(false) }
    var syncFound by remember { mutableStateOf<List<String>>(emptyList()) }
    var syncBackupMessage by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { syncManager.stop() } }
    var novels by remember { mutableStateOf(store.load()) }
    var trash by remember { mutableStateOf(store.loadTrash()) }
    var novelId by remember { mutableStateOf<Long?>(null) }
    var chapterId by remember { mutableStateOf<Long?>(null) }
    var section by remember { mutableStateOf("chapters") }
    var characterId by remember { mutableStateOf<Long?>(null) }
    var worldId by remember { mutableStateOf<Long?>(null) }
    var dark by remember { mutableStateOf(false) }
    var dockTab by remember { mutableStateOf("books") }
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val text = pendingExportText
        pendingExportText = null
        if (uri != null && text != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
            }
        }
    }

    var pendingProjectJson by remember { mutableStateOf<String?>(null) }
    fun snapshotJson(): String = projectToJson(novels, trash)

    val projectExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val data = pendingProjectJson
        pendingProjectJson = null
        if (uri != null && data != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(data) }
        }
    }
    val projectImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runCatching {
            val data = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return@runCatching
            store.backupProjectSnapshot(snapshotJson())
            val snapshot = projectFromJson(data)
            novels = snapshot.novels
            trash = snapshot.trash
            store.save(novels)
            store.saveTrash(trash)
            store.recordWordProgress(novels)
            novelId = null
            syncMessage = "完整项目已导入"
        }.onFailure { syncMessage = "导入失败：${it.message ?: "项目文件无效"}" }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (!text.isNullOrBlank()) {
                    val imported = importNovelFromText(text)
                    novels = (novels + imported).toMutableList()
                    store.save(novels)
                    novelId = imported.id
                }
            }
        }
    }

    // Cached library word count: only recalculated when data is explicitly saved.
    // Keep this state declared before local save() so the local function can capture it.
    var totalWords by remember { mutableIntStateOf(store.totalWordCount(novels)) }

    val editorSaveScope = rememberCoroutineScope()
    var editorSaveRevision by remember { mutableLongStateOf(0L) }

    fun save() {
        // Invalidate any background autosave so it cannot overwrite this newer snapshot.
        editorSaveRevision += 1L
        store.save(novels)
        store.saveTrash(trash)
        store.recordWordProgress(novels)
        totalWords = store.totalWordCount(novels)
    }

    // Snapshot lightweight model lists on the UI thread, then serialize off-thread.
    // A revision check prevents an older autosave from overwriting a newer save.
    fun saveEditorContent() {
        val revision = ++editorSaveRevision
        val snapshot = novels.map { n ->
            Novel(
                n.id, n.title,
                n.chapters.map { it.copy() }.toMutableList(),
                n.characters.map { it.copy() }.toMutableList(),
                n.worlds.map { it.copy() }.toMutableList(),
                n.volumes.map { it.copy() }.toMutableList()
            )
        }
        editorSaveScope.launch {
            val json = withContext(Dispatchers.Default) { store.serialize(snapshot) }
            if (revision == editorSaveRevision) store.saveSerialized(json)
        }
    }

    fun importSnapshot(json: String) {
        runCatching {
            store.backupProjectSnapshot(snapshotJson())
            val snapshot = projectFromJson(json)
            novels = snapshot.novels
            trash = snapshot.trash
            store.save(novels)
            store.saveTrash(trash)
            store.recordWordProgress(novels)
            novelId = null
            syncMessage = "同步完成：手机已更新"
        }.onFailure { syncMessage = "同步失败：${it.message ?: "数据无效"}" }
    }

    fun exportNovel(novel: Novel, chapters: List<Chapter> = novel.chapters) {
        pendingExportText = exportText(novel, chapters)
        val safeTitle = novel.title.ifBlank { "未命名小说" }
        exportLauncher.launch("$safeTitle.txt")
    }

    fun moveChapterToTrash(novel: Novel, chapter: Chapter) {
        trash = (trash + TrashItem(
            id = System.currentTimeMillis(),
            novelId = novel.id,
            novelTitle = novel.title,
            kind = "chapter",
            title = chapter.title,
            content = chapter.content,
            volumeId = chapter.volumeId,
            status = chapter.status
        )).toMutableList()
        novel.chapters.removeAll { it.id == chapter.id }
        save()
    }

    fun moveVolumeToTrash(novel: Novel, volume: Volume) {
        val chapters = novel.chapters.filter { it.volumeId == volume.id }
        val chapterJson = JSONArray().apply {
            chapters.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("title", c.title); put("content", c.content)
                    put("volumeId", c.volumeId); put("status", c.status)
                })
            }
        }.toString()
        trash = (trash + TrashItem(
            id = System.currentTimeMillis(),
            novelId = novel.id,
            novelTitle = novel.title,
            kind = "volume",
            title = volume.title,
            content = chapterJson,
            volumeId = volume.id
        )).toMutableList()
        novel.chapters.removeAll { it.volumeId == volume.id }
        novel.volumes.removeAll { it.id == volume.id }
        if (novel.volumes.isEmpty()) {
            val newId = System.currentTimeMillis()
            novel.volumes += Volume(newId, "第一卷")
        }
        save()
    }

    val novel = novels.firstOrNull { it.id == novelId }
    val chapter = novel?.chapters?.firstOrNull { it.id == chapterId }
    BackHandler {
        when {
            chapter != null -> { chapterId = null; save() }
            characterId != null -> { characterId = null; save() }
            worldId != null -> { worldId = null; save() }
            novel != null -> { novelId = null; section = "chapters"; save() }
            else -> (context as? ComponentActivity)?.finish()
        }
    }

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = Color(0xFFB9B7FF),
            secondary = Color(0xFF8DD8CC),
            surface = Color(0xFF171923),
            background = Color(0xFF101116)
        ) else lightColorScheme(
            primary = Color(0xFF5655C7),
            secondary = Color(0xFF287E78),
            background = Color(0xFFF6F7FC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFECEEF8)
        )
    ) {
        // 恢复轻量页面动效：只对“页面入口/退出”做 160ms 的淡入 + 小幅滑动，
        // 列表、编辑器和卡片内部不做持续动画，避免 V2.4 那种大范围动画带来的卡顿。
        val screenToken = when {
            novelId == null -> "home"
            chapterId != null -> "chapter:$novelId:$chapterId"
            characterId != null -> "character:$novelId:$characterId"
            worldId != null -> "world:$novelId:$worldId"
            else -> "novel:$novelId:$section"
        }
        PageEnterTransition(screenToken) {
            when {
            novelId == null -> HomeScreen(
                    novels = novels,
                    dark = dark,
                    onDark = { dark = !dark },
                    dockTab = dockTab,
                    onDockTab = { dockTab = it },
                    weekWords = store.weeklyWords(),
                    todayWords = store.todayWords(),
                    weeklyGoal = store.weeklyGoal(),
                    totalWords = totalWords,
                    trashCount = trash.size,
                    onToggleOrientation = {
                        val activity = context as? Activity
                        if (activity != null) {
                            activity.requestedOrientation = if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                    },
                    onNew = {
                        val id = System.currentTimeMillis()
                        val volume = Volume(id + 1000L, "第一卷")
                        val n = Novel(id, "未命名小说", mutableListOf(Chapter(id + 1, "第一章", "", volume.id)), volumes = mutableListOf(volume))
                        novels = (novels + n).toMutableList()
                        save()
                        novelId = id
                    },
                    onImport = { importLauncher.launch(arrayOf("text/plain", "text/markdown", "text/*")) },
                    onOpen = { novelId = it },
                    onOpenSync = { syncDialog = true },
                    onExportProject = { pendingProjectJson = snapshotJson(); projectExportLauncher.launch("LocalNovelWriter-项目备份.lnw") },
                    onImportProject = { projectImportLauncher.launch(arrayOf("application/octet-stream", "application/json", "text/*")) },
                    onExport = { n -> exportNovel(n) },
                    onDelete = { id ->
                        novels.firstOrNull { it.id == id }?.let { n ->
                            trash = (trash + TrashItem(System.currentTimeMillis(), n.id, n.title, "novel", n.title, novelToJson(n))).toMutableList()
                        }
                        novels = novels.filterNot { it.id == id }.toMutableList()
                        save()
                    },
                    onRestoreTrash = { item ->
                        if (item.kind == "novel") {
                            val restored = novelFromJson(item.content, item.novelId)
                            novels = (novels + restored).toMutableList()
                        } else {
                            val target = novels.firstOrNull { it.id == item.novelId }
                            if (target != null && item.kind == "chapter") {
                                val volumeId = if (target.volumes.any { it.id == item.volumeId }) item.volumeId else target.volumes.firstOrNull()?.id ?: 0L
                                target.chapters += Chapter(item.id, item.title, item.content, volumeId, item.status)
                            } else if (target != null && item.kind == "volume") {
                                val volumeId = if (target.volumes.any { it.id == item.volumeId }) System.currentTimeMillis() else item.volumeId
                                target.volumes += Volume(volumeId, item.title)
                                runCatching {
                                    val a = JSONArray(item.content)
                                    for (i in 0 until a.length()) {
                                        val o = a.getJSONObject(i)
                                        target.chapters += Chapter(o.getLong("id"), o.optString("title"), o.optString("content"), volumeId, o.optString("status", "草稿"))
                                    }
                                }
                            }
                        }
                        trash = trash.filterNot { it.id == item.id }.toMutableList()
                        save()
                    },
                    onPermanentDeleteTrash = { item -> trash = trash.filterNot { it.id == item.id }.toMutableList(); save() },
                    onClearTrash = { trash = mutableListOf(); save() }
                )
                chapter != null && novel != null -> EditorScreen(
                    chapter = chapter,
                    novelTitle = novel.title,
                    onBack = { chapterId = null; save() },
                    onSave = { title, content ->
                        chapter.title = title
                        if (chapter.content != content) chapter.cachedWordCount = -1
                        chapter.content = content
                        saveEditorContent()
                    }
                )
                characterId != null && novel != null -> novel.characters.firstOrNull { it.id == characterId }?.let { CharacterEditor(it) { characterId = null; save() } }
                worldId != null && novel != null -> novel.worlds.firstOrNull { it.id == worldId }?.let { WorldEditor(it) { worldId = null; save() } }
                novel != null -> NovelScreen(
                    novel = novel,
                    section = section,
                    onSection = { section = it },
                    onOpenChapter = { chapterId = it },
                    onAddChapter = {
                        val id = System.currentTimeMillis()
                        val volumeId = novel.volumes.firstOrNull()?.id ?: run { val v = Volume(id + 1000L, "第一卷"); novel.volumes += v; v.id }
                        novel.chapters += Chapter(id, "第${novel.chapters.size + 1}章", "", volumeId)
                        save(); chapterId = id
                    },
                    onRename = { novel.title = it; save() },
                    onAddVolume = { val id = System.currentTimeMillis(); novel.volumes += Volume(id, "第${novel.volumes.size + 1}卷"); save() },
                    onRenameVolume = { id, title -> novel.volumes.firstOrNull { it.id == id }?.title = title; save() },
                    onDeleteVolume = { id -> novel.volumes.firstOrNull { it.id == id }?.let { moveVolumeToTrash(novel, it) } },
                    onMoveChapter = { chapterIdToMove, volumeId -> novel.chapters.firstOrNull { it.id == chapterIdToMove }?.volumeId = volumeId; save() },
                    onReorderChapters = { volumeId, from, to ->
                        val list = novel.chapters.filter { it.volumeId == volumeId }.toMutableList()
                        if (from in list.indices && to in list.indices) {
                            val moved = list.removeAt(from); list.add(to, moved)
                            novel.chapters.removeAll { it.volumeId == volumeId }
                            novel.chapters.addAll(list)
                            save()
                        }
                    },
                    onDeleteChapter = { id -> novel.chapters.firstOrNull { it.id == id }?.let { moveChapterToTrash(novel, it) } },
                    onStatusChange = { id, status -> novel.chapters.firstOrNull { it.id == id }?.status = status; save() },
                    onAddCharacter = { val id = System.currentTimeMillis(); novel.characters += CharacterProfile(id, "新人物", "", "", "", "", "", "", ""); save(); characterId = id },
                    onAddWorld = { val id = System.currentTimeMillis(); novel.worlds += WorldEntry(id, "新世界", "", "", "", "", "", ""); save(); worldId = id },
                    onOpenCharacter = { characterId = it },
                    onOpenWorld = { worldId = it },
                    onDeleteCharacter = { id -> novel.characters.removeAll { it.id == id }; save() },
                    onDeleteWorld = { id -> novel.worlds.removeAll { it.id == id }; save() },
                    onExportAll = { exportNovel(novel) }
                )
            }
        }
    }
    if (syncDialog) {
        SyncCenterDialog(
            manager = syncManager,
            snapshotProvider = { snapshotJson() },
            onImportSnapshot = { importSnapshot(it) },
            url = syncUrl,
            onUrlChange = { syncUrl = it },
            message = syncMessage,
            onMessageChange = { syncMessage = it },
            foundComputers = syncFound,
            onFoundComputers = { syncFound = it },
            busy = syncBusy,
            onBusyChange = { syncBusy = it },
            backupMessage = syncBackupMessage,
            onBackupMessage = { syncBackupMessage = it },
            onClose = { syncDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncCenterDialog(
    manager: LanSyncManager,
    snapshotProvider: () -> String,
    onImportSnapshot: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    message: String,
    onMessageChange: (String) -> Unit,
    foundComputers: List<String>,
    onFoundComputers: (List<String>) -> Unit,
    busy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    backupMessage: String,
    onBackupMessage: (String) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(manager.running) }
    var pendingIncoming by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("设备联动") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("手机与电脑可在同一 Wi-Fi 下直接同步，不需要账号或云服务器。", fontSize = 14.sp)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("让电脑连接手机", fontWeight = FontWeight.Bold)
                        Text(if (running) "联动服务：已开启" else "联动服务：未开启")
                        if (running) Text("地址：http://${manager.localAddress() ?: "无法获取IP"}:38471", fontSize = 13.sp)
                        Button(onClick = {
                            if (running) { manager.stop(); running = false; onMessageChange("联动已关闭") }
                            else { manager.start(snapshotProvider) { json -> onImportSnapshot(json) }; running = manager.running; onMessageChange(manager.lastMessage) }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(if (running) Icons.Default.Stop else Icons.Default.Wifi, null)
                            Spacer(Modifier.width(6.dp)); Text(if (running) "关闭电脑联动" else "开启电脑联动")
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("连接电脑", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(enabled = !busy, onClick = {
                        onBusyChange(true); onMessageChange("正在搜索同一 Wi-Fi 下的电脑……")
                        scope.launch {
                            val found = manager.discoverComputers()
                            onFoundComputers(found)
                            onMessageChange(if (found.isEmpty()) "没有发现电脑，请确认电脑端已开启手机联动并且两台设备在同一 Wi-Fi。" else "发现 ${found.size} 台电脑")
                            if (found.size == 1) onUrlChange(found.first())
                            onBusyChange(false)
                        }
                    }) {
                        Icon(Icons.Default.Search, null); Spacer(Modifier.width(4.dp)); Text("搜索电脑")
                    }
                }
                if (foundComputers.isNotEmpty()) {
                    foundComputers.forEach { address ->
                        Card(Modifier.fillMaxWidth().clickable { onUrlChange(address) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Computer, null)
                                Spacer(Modifier.width(8.dp))
                                Text(address, modifier = Modifier.weight(1f))
                                Text("选择", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    singleLine = true,
                    label = { Text("电脑地址，例如 http://192.168.1.10:38471") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy && url.isNotBlank(), onClick = {
                        onBusyChange(true); onMessageChange("正在测试电脑连接……")
                        scope.launch {
                            val result = manager.testComputer(url)
                            result.onSuccess { onMessageChange("电脑连接成功：$it") }.onFailure { onMessageChange("连接失败：${it.message ?: "连接失败"}") }
                            onBusyChange(false)
                        }
                    }, modifier = Modifier.weight(1f)) { Text("测试连接") }
                    OutlinedButton(enabled = !busy && url.isNotBlank(), onClick = {
                        onBusyChange(true); onMessageChange("正在读取电脑项目……")
                        scope.launch {
                            val result = manager.pullFromComputer(url)
                            result.onSuccess {
                                onMessageChange("已读取电脑项目，请确认后覆盖手机。")
                                onMessageChange("电脑项目已读取；为防止丢稿，确认后会先自动备份手机当前项目。")
                                onFoundComputers(foundComputers)
                                // 通过临时状态交给外层无法直接传递，因此使用 manager 暂存。
                                pendingIncoming = it
                            }.onFailure { onMessageChange("读取失败：${it.message ?: "连接失败"}") }
                            onBusyChange(false)
                        }
                    }, modifier = Modifier.weight(1f)) { Text("电脑 → 手机") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy && url.isNotBlank(), onClick = {
                        onBusyChange(true); onMessageChange("正在发送手机项目到电脑……")
                        scope.launch {
                            val result = manager.pushToComputer(url, snapshotProvider())
                            result.onSuccess { onMessageChange("同步完成：电脑已收到手机项目") }.onFailure { onMessageChange("发送失败：${it.message ?: "连接失败"}") }
                            onBusyChange(false)
                        }
                    }, modifier = Modifier.weight(1f)) { Text("手机 → 电脑") }
                    Button(enabled = !busy && url.isNotBlank(), onClick = {
                        onBusyChange(true); onMessageChange("正在进行双向同步……先把手机项目发送给电脑，再读取电脑合并结果。")
                        scope.launch {
                            val pushed = manager.pushToComputer(url, snapshotProvider())
                            if (pushed.isFailure) {
                                onMessageChange("双向同步失败：${pushed.exceptionOrNull()?.message ?: "发送失败"}")
                            } else {
                                val pulled = manager.pullFromComputer(url)
                                pulled.onSuccess { pendingIncoming = it; onMessageChange("电脑已合并手机项目，确认后将把合并结果写回手机。") }
                                    .onFailure { onMessageChange("读取合并结果失败：${it.message ?: "连接失败"}") }
                            }
                            onBusyChange(false)
                        }
                    }, modifier = Modifier.weight(1f)) { Text("双向智能同步") }
                }
                if (pendingIncoming != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("发现待处理的电脑项目", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("手机当前项目会先自动备份，再应用电脑项目。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val incoming = pendingIncoming
                                    if (incoming != null) {
                                        onImportSnapshot(incoming)
                                        onBackupMessage("已自动备份手机当前项目")
                                        pendingIncoming = null
                                    }
                                }, modifier = Modifier.weight(1f)) { Text("应用并备份") }
                                OutlinedButton(onClick = { pendingIncoming = null; onMessageChange("已取消应用电脑项目") }, modifier = Modifier.weight(1f)) { Text("取消") }
                            }
                        }
                    }
                }
                if (backupMessage.isNotBlank()) Text(backupMessage, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                Text("建议：每次同步前保留一份完整项目备份。发生冲突时，先不要覆盖双方数据。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } }
    )
}

@Composable
private fun HomeDock(selected: String, onSelected: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(selected == "books", { onSelected("books") }, icon = { Icon(Icons.Default.MenuBook, null) }, label = { Text("书籍") })
        NavigationBarItem(selected == "notice", { onSelected("notice") }, icon = { Icon(Icons.Default.Campaign, null) }, label = { Text("公告") })
        NavigationBarItem(selected == "stats", { onSelected("stats") }, icon = { Icon(Icons.Default.BarChart, null) }, label = { Text("本周统计") })
        NavigationBarItem(selected == "trash", { onSelected("trash") }, icon = { Icon(Icons.Default.DeleteSweep, null) }, label = { Text("回收站") })
    }
}

@Composable
private fun PageEnterTransition(
    token: String,
    distanceDp: Float = 18f,
    duration: Int = 170,
    content: @Composable () -> Unit
) {
    val alpha = remember(token) { Animatable(0.96f) }
    val offset = remember(token) { Animatable(distanceDp) }

    LaunchedEffect(token) {
        kotlinx.coroutines.coroutineScope {
            launch { alpha.animateTo(1f, tween(duration, easing = FastOutSlowInEasing)) }
            launch { offset.animateTo(0f, tween(duration, easing = FastOutSlowInEasing)) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha.value
                translationX = offset.value.dp.toPx()
            }
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    novels: List<Novel>, dark: Boolean, onDark: () -> Unit, dockTab: String, onDockTab: (String) -> Unit,
    weekWords: Long, todayWords: Long, weeklyGoal: Int, totalWords: Int, trashCount: Int,
    onToggleOrientation: () -> Unit, onNew: () -> Unit, onImport: () -> Unit, onOpen: (Long) -> Unit,
    onOpenSync: () -> Unit, onExportProject: () -> Unit, onImportProject: () -> Unit,
    onExport: (Novel) -> Unit, onDelete: (Long) -> Unit, onRestoreTrash: (TrashItem) -> Unit,
    onPermanentDeleteTrash: (TrashItem) -> Unit, onClearTrash: () -> Unit
) {
    val orientation = androidx.compose.ui.platform.LocalConfiguration.current.orientation
    var deleteTarget by remember { mutableStateOf<Novel?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("本地小说 V3.0", modifier = Modifier.padding(24.dp, 22.dp, 24.dp, 12.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                NavigationDrawerItem(label = { Text("导入小说") }, selected = false, onClick = { drawerScope.launch { drawerState.close() }; onImport() }, icon = { Icon(Icons.Default.FileOpen, null) })
                NavigationDrawerItem(label = { Text("设备联动") }, selected = false, onClick = { drawerScope.launch { drawerState.close() }; onOpenSync() }, icon = { Icon(Icons.Default.Sync, null) })
                NavigationDrawerItem(label = { Text("导出完整项目") }, selected = false, onClick = { drawerScope.launch { drawerState.close() }; onExportProject() }, icon = { Icon(Icons.Default.Archive, null) })
                NavigationDrawerItem(label = { Text("导入完整项目") }, selected = false, onClick = { drawerScope.launch { drawerState.close() }; onImportProject() }, icon = { Icon(Icons.Default.Unarchive, null) })
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(label = { Text(if (orientation == Configuration.ORIENTATION_LANDSCAPE) "切换为竖屏" else "切换为横屏") }, selected = false, onClick = { drawerScope.launch { drawerState.close() }; onToggleOrientation() }, icon = { Icon(if (orientation == Configuration.ORIENTATION_LANDSCAPE) Icons.Default.StayCurrentPortrait else Icons.Default.ScreenRotation, null) })
                NavigationDrawerItem(label = { Text(if (dark) "切换为浅色模式" else "切换为深色模式") }, selected = false, onClick = { drawerScope.launch { drawerState.close() }; onDark() }, icon = { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, null) })
            }
        }
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { drawerScope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "打开侧边栏") } },
                title = { Text("本地小说 V3.0", fontWeight = FontWeight.Bold) }
            )
        },
        bottomBar = { HomeDock(dockTab, onDockTab) },
        floatingActionButton = { if (dockTab == "books") FloatingActionButton(onClick = onNew) { Icon(Icons.Default.Add, null) } }
    ) { padding ->
        // 底部标签保留轻量横向切换动效；LazyColumn 本身不做动画。
        Box(Modifier.fillMaxSize().padding(padding)) {
            PageEnterTransition("home:$dockTab", distanceDp = 10f, duration = 130) {
            when (dockTab) {
                "books" -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "library_header") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(26.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(Modifier.padding(20.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("我的书架", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            Spacer(Modifier.height(4.dp))
                                            Text("把灵感整理成故事", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
                                        }
                                        Icon(Icons.Default.AutoStories, null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LibraryMetric("小说", novels.size)
                                        LibraryMetric("章节", novels.sumOf { it.chapters.size })
                                        LibraryMetric("人物", novels.sumOf { it.characters.size })
                                    }
                                }
                            }
                        }
                        if (novels.isEmpty()) {
                            item(key = "empty_library") {
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.AutoStories, null, modifier = Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(12.dp))
                                        Text("书架还是空的", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        Text("创建第一本小说，开始你的故事。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(14.dp))
                                        Button(onClick = onNew) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("新建小说") }
                                    }
                                }
                            }
                        } else items(novels, key = { it.id }) { n ->
                            var menu by remember(n.id) { mutableStateOf(false) }
                            Card(
                                onClick = { onOpen(n.id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(54.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(n.title.ifBlank { "未命名小说" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Spacer(Modifier.height(7.dp))
                                        Text("${n.volumes.size} 卷  ·  ${n.chapters.size} 章", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(3.dp))
                                        Text("${n.characters.size} 个人物  ·  ${n.worlds.size} 份世界资料", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Box {
                                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(text = { Text("导出整本") }, leadingIcon = { Icon(Icons.Default.FileDownload, null) }, onClick = { menu = false; onExport(n) })
                                            DropdownMenuItem(text = { Text("删除小说") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; deleteTarget = n })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "notice" -> AnnouncementScreen()
                "trash" -> TrashScreen(trashCount, onRestoreTrash, onPermanentDeleteTrash, onClearTrash)
                else -> StatsScreen(weekWords, todayWords, weeklyGoal, totalWords)
            }
            }
        }
    }
    if (deleteTarget != null) {
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("删除小说？") }, text = { Text("小说会先移动到回收站，可以之后恢复。") },
            confirmButton = { TextButton(onClick = { deleteTarget?.let { onDelete(it.id) }; deleteTarget = null }) { Text("移入回收站") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } })
    }
    }
}

@Composable
private fun LibraryMetric(label: String, value: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value.toString(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnnouncementScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("公告栏", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("📌 永久公告", fontWeight = FontWeight.Bold, fontSize = 20.sp); Spacer(Modifier.height(8.dp)); Text("每次更新 App 前，请先在应用内导出小说备份。\n\n建议同时保留 TXT 备份与完整的本地数据备份。更新过程中不要卸载旧版本，以免丢失本地数据。") } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("V3.0 更新", fontWeight = FontWeight.Bold, fontSize = 20.sp); Spacer(Modifier.height(8.dp)); Text("重绘书籍、章节、人物、世界观与写作页面；保留轻量页面过渡，优化长篇列表展示与页面渲染。") } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("本地数据", fontWeight = FontWeight.Bold, fontSize = 20.sp); Spacer(Modifier.height(8.dp)); Text("小说内容继续保存在手机本地，不需要账号，也不会上传服务器。") } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("更新提示", fontWeight = FontWeight.Bold, fontSize = 20.sp); Spacer(Modifier.height(8.dp)); Text("以后更新请直接安装新 APK，不要先卸载旧版本。") } } }
    }
}

@Composable
private fun StatsScreen(weekWords: Long, todayWords: Long, weeklyGoal: Int, totalWords: Int) {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    var currentGoal by remember(weeklyGoal) { mutableStateOf(weeklyGoal) }
    var goalText by remember(weeklyGoal) { mutableStateOf(weeklyGoal.toString()) }
    val progress = if (currentGoal > 0) (weekWords.toFloat() / currentGoal.toFloat()).coerceIn(0f, 1f) else 0f
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("本周统计字数", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item { StatCard("本周新增", weekWords, Icons.Default.BarChart) }
        item { StatCard("今日新增", todayWords, Icons.Default.Today) }
        item { StatCard("当前总字数", totalWords.toLong(), Icons.Default.MenuBook) }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) {
                Text("本周写作目标", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp)); Text("$weekWords / $currentGoal 字 · ${(progress * 100).toInt()}%")
                Spacer(Modifier.height(10.dp)); OutlinedTextField(value = goalText, onValueChange = { goalText = it.filter(Char::isDigit) }, singleLine = true, label = { Text("目标字数") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp)); Button(onClick = { val value = goalText.toIntOrNull()?.coerceAtLeast(0) ?: currentGoal; store.setWeeklyGoal(value); currentGoal = value }, modifier = Modifier.fillMaxWidth()) { Text("保存本周目标") }
            } }
        }
        item { Text("统计从 V2.1 开始累计；每次保存内容时自动记录新增字数。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun StatCard(title: String, value: Long, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$value 字", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashScreen(count: Int, onRestore: (TrashItem) -> Unit, onDelete: (TrashItem) -> Unit, onClear: () -> Unit) {
    var clearConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val items = remember { mutableStateOf(LocalStore(context).loadTrash()) }
    LaunchedEffect(count) { items.value = LocalStore(context).loadTrash() }
    Scaffold(topBar = { TopAppBar(title = { Text("回收站 ($count)") }, actions = { if (count > 0) TextButton(onClick = { clearConfirm = true }) { Text("清空") } }) }) { padding ->
        if (items.value.isEmpty()) Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("回收站是空的") }
        else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items.value, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.Bold); Text("${if (item.kind == "chapter") "章节" else if (item.kind == "volume") "分卷" else "小说"} · ${item.novelTitle}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { onRestore(item) }) { Text("恢复") }
                    IconButton(onClick = { onDelete(item) }) { Icon(Icons.Default.DeleteForever, "永久删除") }
                } }
            }
        }
    }
    if (clearConfirm) AlertDialog(onDismissRequest = { clearConfirm = false }, title = { Text("清空回收站？") }, text = { Text("清空后无法恢复。") }, confirmButton = { TextButton(onClick = { onClear(); clearConfirm = false }) { Text("清空") } }, dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelScreen(
    novel: Novel, section: String, onSection: (String) -> Unit, onOpenChapter: (Long) -> Unit, onAddChapter: () -> Unit,
    onRename: (String) -> Unit, onAddVolume: () -> Unit, onRenameVolume: (Long, String) -> Unit, onDeleteVolume: (Long) -> Unit,
    onMoveChapter: (Long, Long) -> Unit, onReorderChapters: (Long, Int, Int) -> Unit, onDeleteChapter: (Long) -> Unit,
    onStatusChange: (Long, String) -> Unit, onAddCharacter: () -> Unit, onAddWorld: () -> Unit,
    onOpenCharacter: (Long) -> Unit, onOpenWorld: (Long) -> Unit,
    onDeleteCharacter: (Long) -> Unit, onDeleteWorld: (Long) -> Unit, onExportAll: () -> Unit
) {
    var rename by remember(novel.id) { mutableStateOf(false) }
    var title by remember(novel.id) { mutableStateOf(novel.title) }
    var exportDialog by remember(novel.id) { mutableStateOf(false) }
    var search by remember(novel.id) { mutableStateOf("") }
    var appliedSearch by remember(novel.id) { mutableStateOf("") }
    LaunchedEffect(search) {
        delay(220)
        appliedSearch = if (search == " ") "" else search
    }
    var selectedChapterIds by remember(novel.id) { mutableStateOf(novel.chapters.map { it.id }.toSet()) }
    var renameVolumeTarget by remember { mutableStateOf<Volume?>(null) }
    var deleteVolumeTarget by remember { mutableStateOf<Volume?>(null) }
    var volumeTitle by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(novel.title, maxLines = 1); Text("创作空间", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                actions = {
                    if (section == "chapters") IconButton(onClick = { search = if (search.isEmpty()) " " else "" }) { Icon(Icons.Default.Search, "搜索章节") }
                    IconButton(onClick = { exportDialog = true }) { Icon(Icons.Default.FileDownload, "导出") }
                    IconButton(onClick = { rename = true }) { Icon(Icons.Default.Edit, "重命名") }
                }
            )
        },
        floatingActionButton = {
            if (section == "chapters") FloatingActionButton(onClick = onAddChapter, shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Add, null) }
            else if (section == "characters") FloatingActionButton(onClick = onAddCharacter, shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.PersonAdd, null) }
            else FloatingActionButton(onClick = onAddWorld, shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Public, null) }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(section == "chapters", { onSection("chapters") }, label = { Text("章节 ${novel.chapters.size}") }, leadingIcon = { Icon(Icons.Default.FormatListBulleted, null) })
                    FilterChip(section == "characters", { onSection("characters") }, label = { Text("人物 ${novel.characters.size}") }, leadingIcon = { Icon(Icons.Default.Groups, null) })
                    FilterChip(section == "world", { onSection("world") }, label = { Text("世界 ${novel.worlds.size}") }, leadingIcon = { Icon(Icons.Default.Public, null) })
                }
            }
            when (section) {
                    "chapters" -> Column(Modifier.fillMaxSize()) {
                        if (search.isNotEmpty()) OutlinedTextField(value = if (search == " ") "" else search, onValueChange = { search = it }, singleLine = true, label = { Text("搜索章节标题或正文") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) } }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                        ChapterAndVolumeList(novel, onOpenChapter, onAddVolume, onRenameVolume = { id, t -> renameVolumeTarget = novel.volumes.firstOrNull { it.id == id }; volumeTitle = t }, onDeleteVolume = { id -> deleteVolumeTarget = novel.volumes.firstOrNull { it.id == id } }, onMoveChapter, onReorderChapters, onDeleteChapter, onStatusChange, appliedSearch)
                    }
                    "characters" -> DataList(
                        items = novel.characters.map { it.id to it.name.ifBlank { "未命名人物" } },
                        onAdd = onAddCharacter,
                        onOpen = onOpenCharacter,
                        onDelete = onDeleteCharacter
                    )
                    else -> DataList(
                        items = novel.worlds.map { it.id to it.name.ifBlank { "未命名世界" } },
                        onAdd = onAddWorld,
                        onOpen = onOpenWorld,
                        onDelete = onDeleteWorld
                    )
                }
        }
    }
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text("重命名小说") }, text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onRename(title); rename = false }) { Text("保存") } }, dismissButton = { TextButton(onClick = { rename = false }) { Text("取消") } })
    if (renameVolumeTarget != null) AlertDialog(onDismissRequest = { renameVolumeTarget = null }, title = { Text("重命名分卷") }, text = { OutlinedTextField(value = volumeTitle, onValueChange = { volumeTitle = it }, singleLine = true, label = { Text("分卷名称") }) }, confirmButton = { TextButton(onClick = { if (volumeTitle.isNotBlank()) renameVolumeTarget?.let { onRenameVolume(it.id, volumeTitle) }; renameVolumeTarget = null }) { Text("保存") } }, dismissButton = { TextButton(onClick = { renameVolumeTarget = null }) { Text("取消") } })
    if (deleteVolumeTarget != null) AlertDialog(onDismissRequest = { deleteVolumeTarget = null }, title = { Text("删除分卷？") }, text = { Text("分卷中的章节会一起移动到回收站，可以之后恢复。") }, confirmButton = { TextButton(onClick = { deleteVolumeTarget?.let { onDeleteVolume(it.id) }; deleteVolumeTarget = null }) { Text("移入回收站") } }, dismissButton = { TextButton(onClick = { deleteVolumeTarget = null }) { Text("取消") } })
    if (exportDialog) {
        AlertDialog(onDismissRequest = { exportDialog = false }, title = { Text("导出小说") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Button(onClick = { exportDialog = false; onExportAll() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.MenuBook, null); Spacer(Modifier.width(6.dp)); Text("导出整本") }; Spacer(Modifier.height(12.dp)); Text("选择章节导出", fontWeight = FontWeight.Bold); novel.chapters.forEach { c -> Row(Modifier.fillMaxWidth().clickable { selectedChapterIds = if (selectedChapterIds.contains(c.id)) selectedChapterIds - c.id else selectedChapterIds + c.id }, verticalAlignment = Alignment.CenterVertically) { Checkbox(selectedChapterIds.contains(c.id), { checked -> selectedChapterIds = if (checked) selectedChapterIds + c.id else selectedChapterIds - c.id }); Text(c.title) } } } }, confirmButton = { TextButton(enabled = selectedChapterIds.isNotEmpty(), onClick = { exportDialog = false; SelectedExportBus.request = novel to novel.chapters.filter { selectedChapterIds.contains(it.id) } }) { Text("导出所选") } }, dismissButton = { TextButton(onClick = { exportDialog = false }) { Text("取消") } })
    }
}

private object SelectedExportBus { var request by mutableStateOf<Pair<Novel, List<Chapter>>?>(null) }

@Composable
private fun ChapterAndVolumeList(
    novel: Novel, onOpenChapter: (Long) -> Unit, onAddVolume: () -> Unit, onRenameVolume: (Long, String) -> Unit,
    onDeleteVolume: (Long) -> Unit, onMoveChapter: (Long, Long) -> Unit, onReorderChapters: (Long, Int, Int) -> Unit,
    onDeleteChapter: (Long) -> Unit, onStatusChange: (Long, String) -> Unit, searchQuery: String
) {
    val localContext = LocalContext.current
    var pendingText by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> val text = pendingText; pendingText = null; if (uri != null && text != null) localContext.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) } }
    LaunchedEffect(SelectedExportBus.request) { val request = SelectedExportBus.request ?: return@LaunchedEffect; if (request.first.id == novel.id) { pendingText = exportText(request.first, request.second); launcher.launch("${request.first.title}-选中章节.txt"); SelectedExportBus.request = null } }
    var menuChapter by remember { mutableStateOf<Chapter?>(null) }
    var statusChapter by remember { mutableStateOf<Chapter?>(null) }
    val normalizedSearch = searchQuery.trim()
    val chapterIdsInOrder = novel.chapters.map { it.id }
    val chaptersByVolume by remember(novel.id, normalizedSearch, chapterIdsInOrder) {
        derivedStateOf {
            val filtered = if (normalizedSearch.isBlank()) novel.chapters.toList()
            else novel.chapters.filter { it.title.contains(normalizedSearch, ignoreCase = true) || it.content.contains(normalizedSearch, ignoreCase = true) }
            filtered.groupBy { it.volumeId }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("章节目录", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer); Text("${novel.volumes.size} 卷 · ${novel.chapters.size} 章", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)) }; FilledTonalButton(onClick = onAddVolume) { Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(4.dp)); Text("新建分卷") } } } }
        novel.volumes.forEach { volume ->
            item(key = "volume_${volume.id}") {
                var expanded by remember(volume.id) { mutableStateOf(true) }
                val chapters = chaptersByVolume[volume.id].orEmpty()
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) }
                            Column(Modifier.weight(1f)) { Text(volume.title, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("${chapters.size} 章", fontSize = 12.sp) }
                            IconButton(onClick = { onRenameVolume(volume.id, volume.title) }) { Icon(Icons.Default.Edit, "重命名") }
                            IconButton(onClick = { onDeleteVolume(volume.id) }) { Icon(Icons.Default.Delete, "删除分卷") }
                        }
                        if (expanded) {
                            if (searchQuery.isBlank()) {
                                ReorderableColumn(
                                    list = chapters,
                                    onSettle = { from, to -> onReorderChapters(volume.id, from, to) },
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                                ) { _, c, isDragging ->
                                    key(c.id) {
                                        ReorderableItem {
                                            Card(
                                                Modifier.fillMaxWidth()
                                                    .graphicsLayer(alpha = if (isDragging) 0.72f else 1f)
                                                    .clickable { onOpenChapter(c.id) },
                                                shape = RoundedCornerShape(15.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                            ) {
                                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.DragHandle, null, modifier = Modifier.longPressDraggableHandle())
                                                    Spacer(Modifier.width(8.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(c.title, fontWeight = FontWeight.Bold)
                                                        Text("${c.wordCount()} 字 · ${c.status}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    IconButton(onClick = { statusChapter = c }) { Icon(Icons.Default.Flag, "章节状态") }
                                                    IconButton(onClick = { menuChapter = c }) { Icon(Icons.Default.MoreVert, "章节操作") }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                chapters.forEach { c ->
                                    Card(Modifier.fillMaxWidth().padding(bottom = 4.dp).clickable { onOpenChapter(c.id) }) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Description, null)
                                            Spacer(Modifier.width(8.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(c.title, fontWeight = FontWeight.Bold)
                                                Text("${c.wordCount()} 字 · ${c.status}", fontSize = 12.sp)
                                            }
                                            IconButton(onClick = { statusChapter = c }) { Icon(Icons.Default.Flag, null) }
                                            IconButton(onClick = { menuChapter = c }) { Icon(Icons.Default.MoreVert, null) }
                                        }
                                    }
                                }
                            }
                            if (chapters.isEmpty()) Text("没有匹配章节", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (menuChapter != null) AlertDialog(onDismissRequest = { menuChapter = null }, title = { Text("章节操作") }, text = { Column { TextButton(onClick = { menuChapter?.let { onDeleteChapter(it.id) }; menuChapter = null }, modifier = Modifier.fillMaxWidth()) { Text("移入回收站") }; TextButton(onClick = { menuChapter?.let { onOpenChapter(it.id) }; menuChapter = null }, modifier = Modifier.fillMaxWidth()) { Text("打开章节") }; Text("移动到其他分卷", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)); novel.volumes.filter { it.id != menuChapter?.volumeId }.forEach { v -> TextButton(onClick = { menuChapter?.let { onMoveChapter(it.id, v.id) }; menuChapter = null }, modifier = Modifier.fillMaxWidth()) { Text(v.title) } } } }, confirmButton = {}, dismissButton = { TextButton(onClick = { menuChapter = null }) { Text("取消") } })
    if (statusChapter != null) AlertDialog(onDismissRequest = { statusChapter = null }, title = { Text("章节状态") }, text = { Column { listOf("草稿", "修改中", "已完成", "锁定").forEach { s -> TextButton(onClick = { statusChapter?.let { onStatusChange(it.id, s) }; statusChapter = null }, modifier = Modifier.fillMaxWidth()) { Text(s) } } } }, confirmButton = {})
}

@Composable
private fun DataList(
    items: List<Pair<Long, String>>,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "data_list_header") {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (items.isEmpty()) "从零开始构建" else "资料档案", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("${items.size} 项资料 · 点击卡片查看详情", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f))
                    }
                    FilledTonalButton(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("新建") }
                }
            }
        }
        if (items.isEmpty()) {
            item(key = "empty_data") {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EditNote, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("还没有资料", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("新建一条资料，逐步完善你的设定。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else items(items, key = { it.first }) { item ->
            Card(
                Modifier.fillMaxWidth().clickable { onOpen(item.first) },
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Article, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.second, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                        Text("设定资料 · 查看与编辑", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDelete(item.first) }) { Icon(Icons.Default.DeleteOutline, "删除") }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterEditor(c: CharacterProfile, onBack: () -> Unit) {
    var name by remember(c.id) { mutableStateOf(c.name) }
    var identity by remember(c.id) { mutableStateOf(c.identity) }
    var appearance by remember(c.id) { mutableStateOf(c.appearance) }
    var personality by remember(c.id) { mutableStateOf(c.personality) }
    var background by remember(c.id) { mutableStateOf(c.background) }
    var abilities by remember(c.id) { mutableStateOf(c.abilities) }
    var relationships by remember(c.id) { mutableStateOf(c.relationships) }
    var notes by remember(c.id) { mutableStateOf(c.notes) }

    LaunchedEffect(c.id) {
        snapshotFlow {
            listOf(name, identity, appearance, personality, background, abilities, relationships, notes)
        }.collectLatest {
            c.name = name
            c.identity = identity
            c.appearance = appearance
            c.personality = personality
            c.background = background
            c.abilities = abilities
            c.relationships = relationships
            c.notes = notes
        }
    }

    EditorScaffold("人物资料", onBack) {
        FormSection("基础档案", Icons.Default.Badge, "先确定角色的身份与外在特征") {
            FormField("姓名", name) { name = it }
            FormField("身份 / 职业", identity) { identity = it }
            FormField("外貌特征", appearance) { appearance = it }
        }
        FormSection("性格与经历", Icons.Default.Psychology, "让人物拥有自己的动机与背景") {
            FormField("性格", personality) { personality = it }
            FormField("背景故事", background) { background = it }
        }
        FormSection("能力与关系", Icons.Default.Groups, "记录角色在故事中的作用") {
            FormField("能力", abilities) { abilities = it }
            FormField("人物关系", relationships) { relationships = it }
            FormField("备注", notes) { notes = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldEditor(w: WorldEntry, onBack: () -> Unit) {
    var name by remember(w.id) { mutableStateOf(w.name) }
    var geography by remember(w.id) { mutableStateOf(w.geography) }
    var races by remember(w.id) { mutableStateOf(w.races) }
    var history by remember(w.id) { mutableStateOf(w.history) }
    var factions by remember(w.id) { mutableStateOf(w.factions) }
    var rules by remember(w.id) { mutableStateOf(w.rules) }
    var notes by remember(w.id) { mutableStateOf(w.notes) }

    LaunchedEffect(w.id) {
        snapshotFlow {
            listOf(name, geography, races, history, factions, rules, notes)
        }.collectLatest {
            w.name = name
            w.geography = geography
            w.races = races
            w.history = history
            w.factions = factions
            w.rules = rules
            w.notes = notes
        }
    }

    EditorScaffold("世界观设定", onBack) {
        FormSection("世界概览", Icons.Default.Public, "定义这个世界的基本面貌") {
            FormField("世界名称", name) { name = it }
            FormField("地理环境", geography) { geography = it }
            FormField("种族 / 居民", races) { races = it }
        }
        FormSection("历史与势力", Icons.Default.AccountTree, "整理世界如何发展、由谁影响") {
            FormField("历史背景", history) { history = it }
            FormField("国家 / 势力", factions) { factions = it }
        }
        FormSection("运行规则", Icons.Default.AutoAwesome, "记录魔法、科技或力量体系") {
            FormField("规则 / 力量体系", rules) { rules = it }
            FormField("备注", notes) { notes = it }
        }
    }
}

@Composable
private fun FormSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("资料会保存在当前小说中", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
                    }
                    Icon(Icons.Default.EditNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                }
            }
            content()
        }
    }
}

@Composable
private fun FormField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = 2,
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(chapter: Chapter, novelTitle: String, onBack: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember(chapter.id) { mutableStateOf(chapter.title) }
    var content by remember(chapter.id) { mutableStateOf(chapter.content) }
    var readingMode by remember(chapter.id) { mutableStateOf(false) }
    // Debounce autosave to reduce JSON serialization and disk writes while typing.
    LaunchedEffect(title, content) { delay(1000); onSave(title, content) }
    var wordCount by remember(chapter.id) { mutableIntStateOf(content.count { !it.isWhitespace() }) }
    LaunchedEffect(content) {
        val snapshot = content
        delay(250)
        wordCount = withContext(Dispatchers.Default) { snapshot.count { !it.isWhitespace() } }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(novelTitle, maxLines = 1)
                        Text(if (readingMode) "沉浸阅读" else "正在写作 · 自动保存", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.padding(end = 4.dp)) {
                        Text("$wordCount 字", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    IconButton(onClick = { readingMode = !readingMode }) { Icon(if (readingMode) Icons.Default.Edit else Icons.Default.MenuBook, if (readingMode) "退出阅读模式" else "阅读模式") }
                }
            )
        }
    ) { padding ->
        if (readingMode) {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 22.dp)) {
                Text(title, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(20.dp))
                Text(content.ifBlank { "（本章暂无正文）" }, fontSize = 19.sp, lineHeight = 34.sp)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it },
                            textStyle = TextStyle(fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            decorationBox = { inner -> if (title.isEmpty()) Text("章节标题", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        BasicTextField(
                            value = content,
                            onValueChange = { content = it },
                            textStyle = TextStyle(fontSize = 18.sp, lineHeight = 31.sp, color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp),
                            decorationBox = { inner ->
                                if (content.isEmpty()) Text("在这里开始写正文……\n\n你的灵感会自动保存在手机本地。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, lineHeight = 30.sp)
                                inner()
                            }
                        )
                    }
                }
            }
        }
    }
}
