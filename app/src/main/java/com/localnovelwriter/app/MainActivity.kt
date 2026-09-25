package com.localnovelwriter.app

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import sh.calvin.reorderable.*

private data class Volume(val id: Long, var title: String)
private data class Chapter(
    val id: Long,
    var title: String,
    var content: String,
    var volumeId: Long = 0L,
    var status: String = "草稿"
)
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
        prefs.edit().putString("data", array.toString()).apply()
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
            else -> if (current == null) { current = Chapter(System.currentTimeMillis() + chapters.size + 1L, "第一章", "", volume.id) }; current = current?.copy(content = if (current!!.content.isEmpty()) line else current!!.content + "\n" + line)
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

@Composable
private fun NovelApp(context: Context) {
    val store = remember { LocalStore(context) }
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

    fun save() {
        store.save(novels)
        store.saveTrash(trash)
        store.recordWordProgress(novels)
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
    val totalWords = novels.sumOf { n -> n.chapters.sumOf { it.content.count { ch -> !ch.isWhitespace() } } }

    BackHandler {
        when {
            chapter != null -> { chapterId = null; save() }
            characterId != null -> { characterId = null; save() }
            worldId != null -> { worldId = null; save() }
            novel != null -> { novelId = null; section = "chapters"; save() }
            else -> (context as? ComponentActivity)?.finish()
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        AnimatedContent(
            targetState = Triple(
                novelId,
                chapterId,
                if (characterId != null) "character"
                else if (worldId != null) "world"
                else section
            ),
            transitionSpec = { (fadeIn() + slideInHorizontally { it / 8 }).togetherWith(fadeOut()) },
            label = "screen"
        ) { target ->
            when {
                target.first == null -> HomeScreen(
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
                    onSave = { title, content -> chapter.title = title; chapter.content = content; save() }
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
                    onDeleteCharacter = { id -> novel.characters.removeAll { it.id == id }; save() },
                    onDeleteWorld = { id -> novel.worlds.removeAll { it.id == id }; save() },
                    onExportAll = { exportNovel(novel) }
                )
            }
        }
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    novels: List<Novel>, dark: Boolean, onDark: () -> Unit, dockTab: String, onDockTab: (String) -> Unit,
    weekWords: Long, todayWords: Long, weeklyGoal: Int, totalWords: Int, trashCount: Int,
    onToggleOrientation: () -> Unit, onNew: () -> Unit, onImport: () -> Unit, onOpen: (Long) -> Unit,
    onExport: (Novel) -> Unit, onDelete: (Long) -> Unit, onRestoreTrash: (TrashItem) -> Unit,
    onPermanentDeleteTrash: (TrashItem) -> Unit, onClearTrash: () -> Unit
) {
    val orientation = androidx.compose.ui.platform.LocalConfiguration.current.orientation
    var deleteTarget by remember { mutableStateOf<Novel?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("本地小说 v2.3", fontWeight = FontWeight.Bold); Text("完全离线 · 无 AI", fontSize = 12.sp) } },
                actions = {
                    if (dockTab == "books") IconButton(onClick = onImport) { Icon(Icons.Default.FileOpen, "导入小说") }
                    IconButton(onClick = onToggleOrientation) { Icon(if (orientation == Configuration.ORIENTATION_LANDSCAPE) Icons.Default.StayCurrentPortrait else Icons.Default.ScreenRotation, "切换横竖屏") }
                    IconButton(onClick = onDark) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, null) }
                }
            )
        },
        bottomBar = { HomeDock(dockTab, onDockTab) },
        floatingActionButton = { if (dockTab == "books") FloatingActionButton(onClick = onNew) { Icon(Icons.Default.Add, null) } }
    ) { padding ->
        AnimatedContent(targetState = dockTab, transitionSpec = { fadeIn() togetherWith fadeOut() }, modifier = Modifier.fillMaxSize().padding(padding), label = "dock") { tab ->
            when (tab) {
                "books" -> {
                    if (novels.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("还没有小说\n点击右下角 + 开始写作", fontSize = 20.sp) }
                    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(novels, key = { it.id }) { n ->
                            var menu by remember(n.id) { mutableStateOf(false) }
                            Card(Modifier.fillMaxWidth().animateContentSize()) {
                                Column(Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).clickable { onOpen(n.id) }) {
                                            Text(n.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(6.dp))
                                            Text("${n.volumes.size} 卷 · ${n.chapters.size} 章 · ${n.characters.size} 人物 · ${n.worlds.size} 世界资料")
                                        }
                                        Box {
                                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                                DropdownMenuItem(text = { Text("导出整本") }, leadingIcon = { Icon(Icons.Default.FileDownload, null) }, onClick = { menu = false; onExport(n) })
                                                DropdownMenuItem(text = { Text("删除小说") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; deleteTarget = n })
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    val count = n.chapters.sumOf { it.content.count { ch -> !ch.isWhitespace() } }
                                    Text("$count 字", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
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
    if (deleteTarget != null) {
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("删除小说？") }, text = { Text("小说会先移动到回收站，可以之后恢复。") },
            confirmButton = { TextButton(onClick = { deleteTarget?.let { onDelete(it.id) }; deleteTarget = null }) { Text("移入回收站") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } })
    }
}

@Composable
private fun AnnouncementScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("公告栏", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("📌 永久公告", fontWeight = FontWeight.Bold, fontSize = 20.sp); Spacer(Modifier.height(8.dp)); Text("每次更新 App 前，请先在应用内导出小说备份。\n\n建议同时保留 TXT 备份与完整的本地数据备份。更新过程中不要卸载旧版本，以免丢失本地数据。") } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("V2.3 更新", fontWeight = FontWeight.Bold, fontSize = 20.sp); Spacer(Modifier.height(8.dp)); Text("新增章节状态、回收站、写作目标、阅读模式、章节拖动排序、章节搜索、小说导入/导出，以及分卷删除。") } } }
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
    val scale by animateFloatAsState(if (value > 0) 1f else 0.98f, animationSpec = spring(), label = title)
    Card(Modifier.fillMaxWidth().graphicsLayer(scaleX = scale, scaleY = scale)) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(16.dp)); Column { Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("$value 字", fontSize = 30.sp, fontWeight = FontWeight.Bold) } } }
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
    onDeleteCharacter: (Long) -> Unit, onDeleteWorld: (Long) -> Unit, onExportAll: () -> Unit
) {
    var rename by remember(novel.id) { mutableStateOf(false) }
    var title by remember(novel.id) { mutableStateOf(novel.title) }
    var exportDialog by remember(novel.id) { mutableStateOf(false) }
    var search by remember(novel.id) { mutableStateOf("") }
    var selectedChapterIds by remember(novel.id) { mutableStateOf(novel.chapters.map { it.id }.toSet()) }
    var renameVolumeTarget by remember { mutableStateOf<Volume?>(null) }
    var deleteVolumeTarget by remember { mutableStateOf<Volume?>(null) }
    var volumeTitle by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text(novel.title) }, actions = {
        if (section == "chapters") IconButton(onClick = { search = if (search.isEmpty()) " " else "" }) { Icon(Icons.Default.Search, "搜索章节") }
        IconButton(onClick = { exportDialog = true }) { Icon(Icons.Default.FileDownload, "导出") }
        IconButton(onClick = { rename = true }) { Icon(Icons.Default.Edit, "重命名") }
    }) }, floatingActionButton = { if (section == "chapters") FloatingActionButton(onClick = onAddChapter) { Icon(Icons.Default.Add, null) } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(section == "chapters", { onSection("chapters") }, label = { Text("章节") })
                FilterChip(section == "characters", { onSection("characters") }, label = { Text("人物") })
                FilterChip(section == "world", { onSection("world") }, label = { Text("世界") })
            }
            AnimatedContent(targetState = section, transitionSpec = { (fadeIn() + slideInHorizontally { it / 10 }).togetherWith(fadeOut()) }, modifier = Modifier.fillMaxSize(), label = "bookSection") { current ->
                when (current) {
                    "chapters" -> Column(Modifier.fillMaxSize()) {
                        if (search.isNotEmpty()) OutlinedTextField(value = if (search == " ") "" else search, onValueChange = { search = it }, singleLine = true, label = { Text("搜索章节标题或正文") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, null) } }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                        ChapterAndVolumeList(novel, onOpenChapter, onAddVolume, onRenameVolume = { id, t -> renameVolumeTarget = novel.volumes.firstOrNull { it.id == id }; volumeTitle = t }, onDeleteVolume = { id -> deleteVolumeTarget = novel.volumes.firstOrNull { it.id == id } }, onMoveChapter, onReorderChapters, onDeleteChapter, onStatusChange, if (search == " ") "" else search)
                    }
                    "characters" -> DataList(novel.characters.map { it.id to it.name.ifBlank { "未命名人物" } }, onAddCharacter, onDeleteCharacter)
                    else -> DataList(novel.worlds.map { it.id to it.name.ifBlank { "未命名世界" } }, onAddWorld, onDeleteWorld)
                }
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("分卷", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("${novel.volumes.size} 卷 · ${novel.chapters.size} 章") }; Button(onClick = onAddVolume) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("新建分卷") } } } }
        novel.volumes.forEach { volume ->
            item(key = "volume_${volume.id}") {
                var expanded by remember(volume.id) { mutableStateOf(true) }
                val chapters = novel.chapters.filter { it.volumeId == volume.id }.filter { c -> searchQuery.isBlank() || c.title.contains(searchQuery, true) || c.content.contains(searchQuery, true) }
                Card(Modifier.fillMaxWidth().animateContentSize()) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                                    .graphicsLayer(alpha = if (isDragging) 0.7f else 1f)
                                                    .clickable { onOpenChapter(c.id) }
                                            ) {
                                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.DragHandle, null, modifier = Modifier.longPressDraggableHandle())
                                                    Spacer(Modifier.width(8.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(c.title, fontWeight = FontWeight.Bold)
                                                        Text("${c.content.count { !it.isWhitespace() }} 字 · ${c.status}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                Text("${c.content.count { !it.isWhitespace() }} 字 · ${c.status}", fontSize = 12.sp)
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
private fun DataList(items: List<Pair<Long, String>>, onAdd: () -> Unit, onDelete: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = onAdd, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("新建") } }
        items(items, key = { it.first }) { item -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(item.second, Modifier.weight(1f), fontWeight = FontWeight.Bold); IconButton(onClick = { onDelete(item.first) }) { Icon(Icons.Default.Delete, null) } } } }
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
        FormField("姓名", name) { name = it }
        FormField("身份", identity) { identity = it }
        FormField("外貌", appearance) { appearance = it }
        FormField("性格", personality) { personality = it }
        FormField("背景", background) { background = it }
        FormField("能力", abilities) { abilities = it }
        FormField("人物关系", relationships) { relationships = it }
        FormField("备注", notes) { notes = it }
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

    EditorScaffold("世界资料", onBack) {
        FormField("世界名称", name) { name = it }
        FormField("地理环境", geography) { geography = it }
        FormField("种族/居民", races) { races = it }
        FormField("历史背景", history) { history = it }
        FormField("国家/势力", factions) { factions = it }
        FormField("规则/力量体系", rules) { rules = it }
        FormField("备注", notes) { notes = it }
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun FormField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = 2
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(chapter: Chapter, novelTitle: String, onBack: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember(chapter.id) { mutableStateOf(chapter.title) }
    var content by remember(chapter.id) { mutableStateOf(chapter.content) }
    var readingMode by remember(chapter.id) { mutableStateOf(false) }
    LaunchedEffect(title, content) { delay(400); onSave(title, content) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text(novelTitle); if (readingMode) Text("阅读模式", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            actions = {
                Text("${content.count { !it.isWhitespace() }} 字", Modifier.padding(end = 8.dp))
                IconButton(onClick = { readingMode = !readingMode }) { Icon(if (readingMode) Icons.Default.Edit else Icons.Default.MenuBook, if (readingMode) "退出阅读模式" else "阅读模式") }
            }
        )
    }) { padding ->
        if (readingMode) {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp)) {
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(22.dp)); Text(content.ifBlank { "（本章暂无正文）" }, fontSize = 19.sp, lineHeight = 34.sp)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                BasicTextField(value = title, onValueChange = { title = it }, textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground), singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                HorizontalDivider()
                BasicTextField(value = content, onValueChange = { content = it }, textStyle = TextStyle(fontSize = 18.sp, lineHeight = 30.sp, color = MaterialTheme.colorScheme.onBackground), modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp), decorationBox = { inner -> if (content.isEmpty()) Text("在这里开始写正文……\n\n内容会自动保存在手机本地。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp); inner() })
            }
        }
    }
}
