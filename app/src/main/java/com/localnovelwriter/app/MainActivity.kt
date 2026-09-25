package com.localnovelwriter.app

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Chapter(val id: Long, var title: String, var content: String)
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
    val worlds: MutableList<WorldEntry> = mutableListOf()
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

    fun load(): MutableList<Novel> = try {
        val array = JSONArray(prefs.getString("data", "[]"))
        MutableList(array.length()) { i ->
            val o = array.getJSONObject(i)
            val chapters = mutableListOf<Chapter>()
            val characters = mutableListOf<CharacterProfile>()
            val worlds = mutableListOf<WorldEntry>()
            o.optJSONArray("chapters")?.let { a ->
                for (j in 0 until a.length()) {
                    val x = a.getJSONObject(j)
                    chapters += Chapter(x.getLong("id"), x.optString("title"), x.optString("content"))
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
            Novel(o.getLong("id"), o.optString("title"), chapters, characters, worlds)
        }
    } catch (_: Exception) { mutableListOf() }

    fun save(novels: List<Novel>) {
        val array = JSONArray()
        novels.forEach { novel ->
            val o = JSONObject().apply {
                put("id", novel.id); put("title", novel.title)
                put("chapters", JSONArray().apply { novel.chapters.forEach { c -> put(JSONObject().apply { put("id", c.id); put("title", c.title); put("content", c.content) }) } })
                put("characters", JSONArray().apply { novel.characters.forEach { c -> put(JSONObject().apply { put("id", c.id); put("name", c.name); put("identity", c.identity); put("appearance", c.appearance); put("personality", c.personality); put("background", c.background); put("abilities", c.abilities); put("relationships", c.relationships); put("notes", c.notes) }) } })
                put("worlds", JSONArray().apply { novel.worlds.forEach { w -> put(JSONObject().apply { put("id", w.id); put("name", w.name); put("geography", w.geography); put("races", w.races); put("history", w.history); put("factions", w.factions); put("rules", w.rules); put("notes", w.notes) }) } })
            }
            array.put(o)
        }
        prefs.edit().putString("data", array.toString()).apply()
    }

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun recordWordProgress(novels: List<Novel>) {
        val today = todayKey()
        val week = SimpleDateFormat("yyyy-'W'ww", Locale.US).format(Date())
        val storedWeek = statsPrefs.getString("week", "")
        val total = novels.sumOf { n -> n.chapters.sumOf { it.content.count { ch -> !ch.isWhitespace() } } }.toLong()
        var lastTotal = statsPrefs.getLong("lastTotal", 0L)
        if (storedWeek != week) {
            statsPrefs.edit().putString("week", week).putLong("weekWords", 0L).putLong("lastTotal", total).apply()
            lastTotal = total
        }
        val delta = (total - lastTotal).coerceAtLeast(0L)
        if (delta > 0L) {
            val editor = statsPrefs.edit()
            editor.putLong("weekWords", statsPrefs.getLong("weekWords", 0L) + delta)
            editor.putLong("day_$today", statsPrefs.getLong("day_$today", 0L) + delta)
            editor.putLong("lastTotal", total)
            editor.apply()
        } else if (statsPrefs.getLong("lastTotal", Long.MIN_VALUE) == Long.MIN_VALUE) {
            statsPrefs.edit().putLong("lastTotal", total).apply()
        }
    }

    fun weeklyWords(): Long = statsPrefs.getLong("weekWords", 0L)
    fun todayWords(): Long = statsPrefs.getLong("day_${todayKey()}", 0L)
}

@Composable
private fun NovelApp(context: Context) {
    val store = remember { LocalStore(context) }
    var novels by remember { mutableStateOf(store.load()) }
    var novelId by remember { mutableStateOf<Long?>(null) }
    var chapterId by remember { mutableStateOf<Long?>(null) }
    var section by remember { mutableStateOf("chapters") }
    var characterId by remember { mutableStateOf<Long?>(null) }
    var worldId by remember { mutableStateOf<Long?>(null) }
    var dark by remember { mutableStateOf(false) }
    var dockTab by remember { mutableStateOf("books") }

    fun save() { store.save(novels); store.recordWordProgress(novels) }
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
            targetState = Triple(novelId, chapterId, if (characterId != null) "character" else if (worldId != null) "world" else section),
            transitionSpec = { fadeIn().togetherWith(fadeOut()) },
            label = "screen"
        ) { target ->
            when {
                target.first == null -> HomeScreen(
                    novels = novels, dark = dark, onDark = { dark = !dark }, dockTab = dockTab, onDockTab = { dockTab = it },
                    weekWords = store.weeklyWords(), todayWords = store.todayWords(), totalWords = totalWords,
                    onToggleOrientation = {
                        val activity = context as? Activity
                        if (activity != null) {
                            activity.requestedOrientation = if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                    },
                    onNew = {
                        val id = System.currentTimeMillis()
                        val n = Novel(id, "未命名小说"); n.chapters += Chapter(id + 1, "第一章", "")
                        novels = (novels + n).toMutableList(); save(); novelId = id
                    }, onOpen = { novelId = it }
                )
                chapter != null && novel != null -> EditorScreen(chapter, novel.title, { chapterId = null; save() }, { title, content -> chapter.title = title; chapter.content = content; save() })
                characterId != null && novel != null -> novel.characters.firstOrNull { it.id == characterId }?.let { CharacterEditor(it) { characterId = null; save() } }
                worldId != null && novel != null -> novel.worlds.firstOrNull { it.id == worldId }?.let { WorldEditor(it) { worldId = null; save() } }
                novel != null -> NovelScreen(
                    novel = novel, section = section, onSection = { section = it }, onOpenChapter = { chapterId = it },
                    onAddChapter = { val id = System.currentTimeMillis(); novel.chapters += Chapter(id, "第${novel.chapters.size + 1}章", ""); save(); chapterId = id },
                    onRename = { novel.title = it; save() },
                    onAddCharacter = { val id = System.currentTimeMillis(); novel.characters += CharacterProfile(id, "新人物", "", "", "", "", "", "", ""); save(); characterId = id },
                    onAddWorld = { val id = System.currentTimeMillis(); novel.worlds += WorldEntry(id, "新世界", "", "", "", "", "", ""); save(); worldId = id },
                    onDeleteCharacter = { id -> novel.characters.removeAll { it.id == id }; save() },
                    onDeleteWorld = { id -> novel.worlds.removeAll { it.id == id }; save() }
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    novels: List<Novel>, dark: Boolean, onDark: () -> Unit, dockTab: String, onDockTab: (String) -> Unit,
    weekWords: Long, todayWords: Long, totalWords: Int, onToggleOrientation: () -> Unit, onNew: () -> Unit, onOpen: (Long) -> Unit
) {
    val orientation = androidx.compose.ui.platform.LocalConfiguration.current.orientation
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("本地小说 v2.1", fontWeight = FontWeight.Bold); Text("完全离线 · 无 AI", fontSize = 12.sp) } },
                actions = {
                    IconButton(onClick = onToggleOrientation) { Icon(if (orientation == Configuration.ORIENTATION_LANDSCAPE) Icons.Default.StayCurrentPortrait else Icons.Default.ScreenRotation, "切换横竖屏") }
                    IconButton(onClick = onDark) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, null) }
                }
            )
        },
        bottomBar = { HomeDock(dockTab, onDockTab) },
        floatingActionButton = { if (dockTab == "books") FloatingActionButton(onClick = onNew) { Icon(Icons.Default.Add, null) } }
    ) { padding ->
        AnimatedContent(targetState = dockTab, transitionSpec = { fadeIn().togetherWith(fadeOut()) }, modifier = Modifier.fillMaxSize().padding(padding), label = "dock") { tab ->
            when (tab) {
                "books" -> {
                    if (novels.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("还没有小说\n点击右下角 + 开始写作", fontSize = 20.sp) }
                    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(novels, key = { it.id }) { n ->
                            Card(Modifier.fillMaxWidth().animateContentSize().clickable { onOpen(n.id) }) {
                                Column(Modifier.padding(18.dp)) {
                                    Text(n.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text("${n.chapters.size} 章 · ${n.characters.size} 人物 · ${n.worlds.size} 世界资料")
                                    val count = n.chapters.sumOf { it.content.count { ch -> !ch.isWhitespace() } }
                                    Text("${count} 字", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                "notice" -> AnnouncementScreen()
                else -> StatsScreen(weekWords, todayWords, totalWords)
            }
        }
    }
}

@Composable
private fun AnnouncementScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("公告栏", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("V2.1 更新", fontWeight = FontWeight.Bold, fontSize = 20.sp); Spacer(Modifier.height(8.dp)); Text("新增底部 Dock、每周字数统计、横竖屏切换和更多页面动效。") } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("本地数据", fontWeight = FontWeight.Bold, fontSize = 20.sp); Spacer(Modifier.height(8.dp)); Text("小说内容继续保存在手机本地，不需要账号，也不会上传服务器。") } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("更新提示", fontWeight = FontWeight.Bold, fontSize = 20.sp); Spacer(Modifier.height(8.dp)); Text("以后更新请直接安装新 APK，不要先卸载旧版本。") } } }
    }
}

@Composable
private fun StatsScreen(weekWords: Long, todayWords: Long, totalWords: Int) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("本周统计字数", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item { StatCard("本周新增", weekWords, Icons.Default.BarChart) }
        item { StatCard("今日新增", todayWords, Icons.Default.Today) }
        item { StatCard("当前总字数", totalWords.toLong(), Icons.Default.MenuBook) }
        item { Text("统计从 V2.1 开始累计；每次保存内容时自动记录新增字数。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun StatCard(title: String, value: Long, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val scale by animateFloatAsState(if (value > 0) 1f else 0.98f, animationSpec = spring(), label = title)
    Card(Modifier.fillMaxWidth().graphicsLayer(scaleX = scale, scaleY = scale)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column { Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("$value 字", fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelScreen(
    novel: Novel,
    section: String,
    onSection: (String) -> Unit,
    onOpenChapter: (Long) -> Unit,
    onAddChapter: () -> Unit,
    onRename: (String) -> Unit,
    onAddCharacter: () -> Unit,
    onAddWorld: () -> Unit,
    onDeleteCharacter: (Long) -> Unit,
    onDeleteWorld: (Long) -> Unit
) {
    var rename by remember(novel.id) { mutableStateOf(false) }
    var title by remember(novel.id) { mutableStateOf(novel.title) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(novel.title) },
                actions = {
                    IconButton(onClick = { rename = true }) { Icon(Icons.Default.Edit, null) }
                }
            )
        },
        floatingActionButton = {
            if (section == "chapters") {
                FloatingActionButton(onClick = onAddChapter) { Icon(Icons.Default.Add, null) }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = section == "chapters", onClick = { onSection("chapters") }, label = { Text("章节") })
                FilterChip(selected = section == "characters", onClick = { onSection("characters") }, label = { Text("人物") })
                FilterChip(selected = section == "world", onClick = { onSection("world") }, label = { Text("世界") })
            }

            when (section) {
                "chapters" -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(novel.chapters, key = { it.id }) { c ->
                        Card(Modifier.fillMaxWidth().clickable { onOpenChapter(c.id) }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, null)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.title, fontWeight = FontWeight.Bold)
                                    Text("${c.content.length} 字", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                "characters" -> DataList(
                    items = novel.characters.map { it.id to it.name.ifBlank { "未命名人物" } },
                    onAdd = onAddCharacter,
                    onDelete = onDeleteCharacter
                )
                else -> DataList(
                    items = novel.worlds.map { it.id to it.name.ifBlank { "未命名世界" } },
                    onAdd = onAddWorld,
                    onDelete = onDeleteWorld
                )
            }
        }
    }

    if (rename) {
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("重命名小说") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) onRename(title)
                    rename = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun DataList(items: List<Pair<Long, String>>, onAdd: () -> Unit, onDelete: (Long) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Button(onClick = onAdd, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("新建")
            }
        }
        items(items, key = { it.first }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.second, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onDelete(item.first) }) { Icon(Icons.Default.Delete, null) }
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
        snapshotFlow { listOf(name, identity, appearance, personality, background, abilities, relationships, notes) }
            .collectLatest {
                c.name = name; c.identity = identity; c.appearance = appearance; c.personality = personality
                c.background = background; c.abilities = abilities; c.relationships = relationships; c.notes = notes
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
        snapshotFlow { listOf(name, geography, races, history, factions, rules, notes) }
            .collectLatest {
                w.name = name; w.geography = geography; w.races = races; w.history = history
                w.factions = factions; w.rules = rules; w.notes = notes
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
private fun EditorScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun FormField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, minLines = 2)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(chapter: Chapter, novelTitle: String, onBack: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember(chapter.id) { mutableStateOf(chapter.title) }
    var content by remember(chapter.id) { mutableStateOf(chapter.content) }

    LaunchedEffect(title, content) {
        delay(400)
        onSave(title, content)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(novelTitle) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { Text("${content.count { !it.isWhitespace() }} 字", Modifier.padding(end = 16.dp)) }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )
            HorizontalDivider()
            BasicTextField(
                value = content,
                onValueChange = { content = it },
                textStyle = TextStyle(fontSize = 18.sp, lineHeight = 30.sp, color = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp),
                decorationBox = { inner ->
                    if (content.isEmpty()) {
                        Text("在这里开始写正文……\n\n内容会自动保存在手机本地。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
                    }
                    inner()
                }
            )
        }
    }
}
