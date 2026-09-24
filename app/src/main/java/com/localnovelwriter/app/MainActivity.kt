package com.localnovelwriter.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import org.json.JSONArray
import org.json.JSONObject

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
                    characters += CharacterProfile(
                        x.getLong("id"), x.optString("name"), x.optString("identity"),
                        x.optString("appearance"), x.optString("personality"), x.optString("background"),
                        x.optString("abilities"), x.optString("relationships"), x.optString("notes")
                    )
                }
            }
            o.optJSONArray("worlds")?.let { a ->
                for (j in 0 until a.length()) {
                    val x = a.getJSONObject(j)
                    worlds += WorldEntry(
                        x.getLong("id"), x.optString("name"), x.optString("geography"),
                        x.optString("races"), x.optString("history"), x.optString("factions"),
                        x.optString("rules"), x.optString("notes")
                    )
                }
            }
            Novel(o.getLong("id"), o.optString("title"), chapters, characters, worlds)
        }
    } catch (_: Exception) {
        mutableListOf()
    }

    fun save(novels: List<Novel>) {
        val array = JSONArray()
        novels.forEach { novel ->
            val o = JSONObject()
            o.put("id", novel.id)
            o.put("title", novel.title)
            o.put("chapters", JSONArray().apply {
                novel.chapters.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("title", c.title)
                        put("content", c.content)
                    })
                }
            })
            o.put("characters", JSONArray().apply {
                novel.characters.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("identity", c.identity)
                        put("appearance", c.appearance)
                        put("personality", c.personality)
                        put("background", c.background)
                        put("abilities", c.abilities)
                        put("relationships", c.relationships)
                        put("notes", c.notes)
                    })
                }
            })
            o.put("worlds", JSONArray().apply {
                novel.worlds.forEach { w ->
                    put(JSONObject().apply {
                        put("id", w.id)
                        put("name", w.name)
                        put("geography", w.geography)
                        put("races", w.races)
                        put("history", w.history)
                        put("factions", w.factions)
                        put("rules", w.rules)
                        put("notes", w.notes)
                    })
                }
            })
            array.put(o)
        }
        prefs.edit().putString("data", array.toString()).apply()
    }
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

    fun save() = store.save(novels)

    val novel = novels.firstOrNull { it.id == novelId }
    val chapter = novel?.chapters?.firstOrNull { it.id == chapterId }

    BackHandler {
        when {
            chapter != null -> {
                chapterId = null
                save()
            }
            characterId != null -> {
                characterId = null
                save()
            }
            worldId != null -> {
                worldId = null
                save()
            }
            novel != null -> {
                novelId = null
                section = "chapters"
                save()
            }
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
                    novels = novels,
                    dark = dark,
                    onDark = { dark = !dark },
                    onNew = {
                        val id = System.currentTimeMillis()
                        val n = Novel(id, "未命名小说")
                        n.chapters += Chapter(id + 1, "第一章", "")
                        novels = (novels + n).toMutableList()
                        save()
                        novelId = id
                    },
                    onOpen = { novelId = it }
                )
                chapter != null && novel != null -> EditorScreen(
                    chapter = chapter,
                    novelTitle = novel.title,
                    onBack = { chapterId = null; save() },
                    onSave = { title, content -> chapter.title = title; chapter.content = content; save() }
                )
                characterId != null && novel != null -> {
                    val c = novel.characters.firstOrNull { it.id == characterId }
                    if (c != null) CharacterEditor(c, { characterId = null; save() })
                }
                worldId != null && novel != null -> {
                    val w = novel.worlds.firstOrNull { it.id == worldId }
                    if (w != null) WorldEditor(w, { worldId = null; save() })
                }
                novel != null -> NovelScreen(
                    novel = novel,
                    section = section,
                    onSection = { section = it },
                    onOpenChapter = { chapterId = it },
                    onAddChapter = {
                        val id = System.currentTimeMillis()
                        novel.chapters += Chapter(id, "第${novel.chapters.size + 1}章", "")
                        save()
                        chapterId = id
                    },
                    onRename = { novel.title = it; save() },
                    onAddCharacter = {
                        val id = System.currentTimeMillis()
                        novel.characters += CharacterProfile(id, "新人物", "", "", "", "", "", "", "")
                        save()
                        characterId = id
                    },
                    onAddWorld = {
                        val id = System.currentTimeMillis()
                        novel.worlds += WorldEntry(id, "新世界", "", "", "", "", "", "")
                        save()
                        worldId = id
                    },
                    onDeleteCharacter = { id -> novel.characters.removeAll { it.id == id }; save() },
                    onDeleteWorld = { id -> novel.worlds.removeAll { it.id == id }; save() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    novels: List<Novel>,
    dark: Boolean,
    onDark: () -> Unit,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("本地小说 v2.0", fontWeight = FontWeight.Bold)
                        Text("完全离线 · 无 AI", fontSize = 12.sp)
                    }
                },
                actions = {
                    IconButton(onClick = onDark) {
                        Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Icon(Icons.Default.Add, null) }
        }
    ) { padding ->
        if (novels.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("还没有小说\n点击右下角 + 开始写作", fontSize = 20.sp)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(novels, key = { it.id }) { n ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(n.id) }) {
                        Column(Modifier.padding(18.dp)) {
                            Text(n.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("${n.chapters.size} 章 · ${n.characters.size} 人物 · ${n.worlds.size} 世界资料")
                        }
                    }
                }
            }
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
