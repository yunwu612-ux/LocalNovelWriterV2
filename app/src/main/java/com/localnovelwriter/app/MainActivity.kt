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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
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
import org.json.JSONArray
import org.json.JSONObject

private data class Chapter(val id: Long, var title: String, var content: String)
private data class CharacterProfile(val id: Long, var name: String, var identity: String, var appearance: String, var personality: String, var background: String, var abilities: String, var relationships: String, var notes: String)
private data class WorldEntry(val id: Long, var name: String, var geography: String, var races: String, var history: String, var factions: String, var rules: String, var notes: String)
private data class Novel(val id: Long, var title: String, var chapters: MutableList<Chapter> = mutableListOf(), var characters: MutableList<CharacterProfile> = mutableListOf(), var worlds: MutableList<WorldEntry> = mutableListOf())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { NovelApp(this) } }
}

private class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("novel_data", Context.MODE_PRIVATE)
    fun load(): MutableList<Novel> = try {
        val arr = JSONArray(prefs.getString("data", "[]"))
        MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i); val chapters = mutableListOf<Chapter>(); val chars = mutableListOf<CharacterProfile>(); val worlds = mutableListOf<WorldEntry>()
            o.optJSONArray("chapters")?.let { a -> for (j in 0 until a.length()) { val x=a.getJSONObject(j); chapters += Chapter(x.getLong("id"),x.getString("title"),x.getString("content")) } }
            o.optJSONArray("characters")?.let { a -> for (j in 0 until a.length()) { val x=a.getJSONObject(j); chars += CharacterProfile(x.getLong("id"),x.optString("name"),x.optString("identity"),x.optString("appearance"),x.optString("personality"),x.optString("background"),x.optString("abilities"),x.optString("relationships"),x.optString("notes")) } }
            o.optJSONArray("worlds")?.let { a -> for (j in 0 until a.length()) { val x=a.getJSONObject(j); worlds += WorldEntry(x.getLong("id"),x.optString("name"),x.optString("geography"),x.optString("races"),x.optString("history"),x.optString("factions"),x.optString("rules"),x.optString("notes")) } }
            Novel(o.getLong("id"),o.getString("title"),chapters,chars,worlds)
        }
    } catch (_: Exception) { mutableListOf() }
    fun save(novels: List<Novel>) {
        val arr=JSONArray(); novels.forEach { n -> val o=JSONObject().apply { put("id",n.id); put("title",n.title) }
            o.put("chapters",JSONArray().apply { n.chapters.forEach { put(JSONObject().apply { put("id",it.id);put("title",it.title);put("content",it.content) }) } })
            o.put("characters",JSONArray().apply { n.characters.forEach { put(JSONObject().apply { put("id",it.id);put("name",it.name);put("identity",it.identity);put("appearance",it.appearance);put("personality",it.personality);put("background",it.background);put("abilities",it.abilities);put("relationships",it.relationships);put("notes",it.notes) }) } })
            o.put("worlds",JSONArray().apply { n.worlds.forEach { put(JSONObject().apply { put("id",it.id);put("name",it.name);put("geography",it.geography);put("races",it.races);put("history",it.history);put("factions",it.factions);put("rules",it.rules);put("notes",it.notes) }) } })
            arr.put(o)
        }; prefs.edit().putString("data",arr.toString()).apply()
    }
}

@Composable
private fun NovelApp(context: Context) {
    val store=remember{LocalStore(context)}; var novels by remember{mutableStateOf(store.load())}; var novelId by remember{mutableStateOf<Long?>(null)}; var chapterId by remember{mutableStateOf<Long?>(null)}; var section by remember{mutableStateOf("chapters")}; var charId by remember{mutableStateOf<Long?>(null)}; var worldId by remember{mutableStateOf<Long?>(null)}; var dark by remember{mutableStateOf(false)}
    fun save(){store.save(novels)}
    val novel=novels.firstOrNull{it.id==novelId}; val chapter=novel?.chapters?.firstOrNull{it.id==chapterId}
    BackHandler(enabled=true){ when { chapter!=null -> { chapterId=null; save() }; novel!=null -> { novelId=null; section="chapters"; charId=null; worldId=null; save() }; else -> finishActivity(context) } }
    MaterialTheme(if(dark) darkColorScheme() else lightColorScheme()) {
        AnimatedContent(targetState=Triple(novelId,chapterId,if(charId!=null)"char" else if(worldId!=null)"world" else section),transitionSpec={fadeIn().togetherWith(fadeOut())},label="screen") { target ->
            when {
                target.first==null -> HomeScreen(novels,dark,{dark=!dark},{ val n=Novel(System.currentTimeMillis(),"未命名小说",mutableListOf(Chapter(System.currentTimeMillis()+1,"第一章",""))); novels=(novels+n).toMutableList(); save(); novelId=n.id }) { novelId=it }
                chapter!=null -> EditorScreen(chapter,novel!!.title,{chapterId=null;save()},{chapter.title=it.first;chapter.content=it.second;save()})
                charId!=null -> { val c=novel!!.characters.first{it.id==charId}; CharacterEditor(c,{charId=null;save()},{charId=null;save()}) }
                worldId!=null -> { val w=novel!!.worlds.first{it.id==worldId}; WorldEditor(w,{worldId=null;save()},{worldId=null;save()}) }
                novel!=null -> NovelScreen(novel,section,{section=it},{chapterId=it},{ val c=Chapter(System.currentTimeMillis(),"第${novel.chapters.size+1}章",""); novel.chapters+=c; save(); chapterId=c.id },{novel.title=it;save()},{val c=CharacterProfile(System.currentTimeMillis(),"新人物","","","","","","","");novel.characters+=c;save();charId=c.id},{val w=WorldEntry(System.currentTimeMillis(),"新世界","","","","","","");novel.worlds+=w;save();worldId=w.id},{id->novel.characters.removeIf{it.id==id};save()},{id->novel.worlds.removeIf{it.id==id};save()})
            }
        }
    }
}

private fun finishActivity(context: Context){(context as? ComponentActivity)?.finish()}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HomeScreen(novels:List<Novel>,dark:Boolean,onDark:()->Unit,onNew:()->Unit,onOpen:(Long)->Unit){ Scaffold(topBar={TopAppBar(title={Column{Text("本地小说 v2.0",fontWeight=FontWeight.Bold);Text("完全离线 · 无 AI",fontSize=12.sp)}},actions={IconButton(onDark){Icon(if(dark)Icons.Default.LightMode else Icons.Default.DarkMode,null)}})},floatingActionButton={FloatingActionButton(onNew){Icon(Icons.Default.Add,null)}}){p-> if(novels.isEmpty()) Box(Modifier.fillMaxSize().padding(p),Alignment.Center){Text("还没有小说\n点击右下角 + 开始写作",fontSize=20.sp)} else LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(novels){n->Card(Modifier.fillMaxWidth().clickable{onOpen(n.id)}){Column(Modifier.padding(18.dp)){Text(n.title,fontSize=20.sp,fontWeight=FontWeight.Bold);Text("${n.chapters.size} 章 · ${n.characters.size} 人物 · ${n.worlds.size} 世界资料")}}}}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun NovelScreen(n:Novel,section:String,onSection:(String)->Unit,onOpen:(Long)->Unit,onAdd:()->Unit,onRename:(String)->Unit,onAddChar:()->Unit,onAddWorld:()->Unit,onDelChar:(Long)->Unit,onDelWorld:(Long)->Unit){ var rename by remember{mutableStateOf(false)}; var title by remember(n.id){mutableStateOf(n.title)}; Scaffold(topBar={TopAppBar(title={Text(n.title)},actions={IconButton({rename=true}){Icon(Icons.Default.Edit,null)}})},floatingActionButton={if(section=="chapters")FloatingActionButton(onAdd){Icon(Icons.Default.Add,null)}}){p->Column(Modifier.fillMaxSize().padding(p)){Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(section=="chapters",{onSection("chapters")},{label={Text("章节")}});FilterChip(section=="characters",{onSection("characters")},{label={Text("人物")}});FilterChip(section=="world",{onSection("world")},{label={Text("世界")}})};when(section){"chapters"->LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){items(n.chapters){c->Card(Modifier.fillMaxWidth().clickable{onOpen(c.id)}){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Description,null);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(c.title,fontWeight=FontWeight.Bold);Text("${c.content.length} 字",fontSize=12.sp)}}}}};"characters"->DataList(n.characters.map{it.id to (it.name.ifBlank{"未命名人物"})},onAddChar,onDelChar);"world"->DataList(n.worlds.map{it.id to (it.name.ifBlank{"未命名世界"})},onAddWorld,onDelWorld)}}};if(rename)AlertDialog(onDismissRequest={rename=false},title={Text("重命名小说")},text={OutlinedTextField(title,{title=it},singleLine=true)},confirmButton={TextButton({if(title.isNotBlank())onRename(title);rename=false}){Text("保存")}},dismissButton={TextButton({rename=false}){Text("取消")}})} }

@Composable private fun DataList(items:List<Pair<Long,String>>,onAdd:()->Unit,onDelete:(Long)->Unit){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Button(onAdd,Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("新建")}};items(items){(id,name)->Card(Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(name,Modifier.weight(1f),fontWeight=FontWeight.Bold);IconButton({onDelete(id)}){Icon(Icons.Default.Delete,null)}}}}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CharacterEditor(c:CharacterProfile,onBack:()->Unit,onSave:()->Unit){EditorScaffold("人物资料",onBack,onSave){var name by remember{mutableStateOf(c.name)};var identity by remember{mutableStateOf(c.identity)};var appearance by remember{mutableStateOf(c.appearance)};var personality by remember{mutableStateOf(c.personality)};var background by remember{mutableStateOf(c.background)};var abilities by remember{mutableStateOf(c.abilities)};var relationships by remember{mutableStateOf(c.relationships)};var notes by remember{mutableStateOf(c.notes)};LaunchedEffect(Unit){snapshotFlow{listOf(name,identity,appearance,personality,background,abilities,relationships,notes)}.collect{c.name=name;c.identity=identity;c.appearance=appearance;c.personality=personality;c.background=background;c.abilities=abilities;c.relationships=relationships;c.notes=notes}};FormField("姓名",name){name=it};FormField("身份",identity){identity=it};FormField("外貌",appearance){appearance=it};FormField("性格",personality){personality=it};FormField("背景",background){background=it};FormField("能力",abilities){abilities=it};FormField("人物关系",relationships){relationships=it};FormField("备注",notes){notes=it}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun WorldEditor(w:WorldEntry,onBack:()->Unit,onSave:()->Unit){EditorScaffold("世界资料",onBack,onSave){var name by remember{mutableStateOf(w.name)};var geography by remember{mutableStateOf(w.geography)};var races by remember{mutableStateOf(w.races)};var history by remember{mutableStateOf(w.history)};var factions by remember{mutableStateOf(w.factions)};var rules by remember{mutableStateOf(w.rules)};var notes by remember{mutableStateOf(w.notes)};LaunchedEffect(Unit){snapshotFlow{listOf(name,geography,races,history,factions,rules,notes)}.collect{w.name=name;w.geography=geography;w.races=races;w.history=history;w.factions=factions;w.rules=rules;w.notes=notes}};FormField("世界名称",name){name=it};FormField("地理环境",geography){geography=it};FormField("种族/居民",races){races=it};FormField("历史背景",history){history=it};FormField("国家/势力",factions){factions=it};FormField("规则/力量体系",rules){rules=it};FormField("备注",notes){notes=it}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EditorScaffold(title:String,onBack:()->Unit,onSave:()->Unit,content:@Composable ColumnScope.()->Unit){Scaffold(topBar={TopAppBar(title={Text(title)},navigationIcon={IconButton(onBack){Icon(Icons.Default.ArrowBack,null)}},actions={TextButton(onSave){Text("保存")}})}){p->Column(Modifier.fillMaxSize().padding(p).padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}}

@Composable private fun FormField(label:String,value:String,onValue:(String)->Unit){OutlinedTextField(value,onValue,Modifier.fillMaxWidth(),label={Text(label)},minLines=2)}

@Composable private fun EditorScreen(chapter:Chapter,novelTitle:String,onBack:()->Unit,onSave:(Pair<String,String>)->Unit){var title by remember(chapter.id){mutableStateOf(chapter.title)};var content by remember(chapter.id){mutableStateOf(chapter.content)};LaunchedEffect(title,content){delay(400);onSave(title to content)};Scaffold(topBar={TopAppBar(title={Text(novelTitle)},navigationIcon={IconButton(onBack){Icon(Icons.Default.ArrowBack,null)}},actions={Text("${content.count{!it.isWhitespace()}} 字",Modifier.padding(end=16.dp))})}){p->Column(Modifier.fillMaxSize().padding(p).padding(horizontal=18.dp)){BasicTextField(title,{title=it},textStyle=TextStyle(fontSize=24.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground),singleLine=true,modifier=Modifier.fillMaxWidth().padding(vertical=16.dp));HorizontalDivider();BasicTextField(content,{content=it},textStyle=TextStyle(fontSize=18.sp,lineHeight=30.sp,color=MaterialTheme.colorScheme.onBackground),modifier=Modifier.fillMaxWidth().weight(1f).padding(top=16.dp),decorationBox={inner->if(content.isEmpty())Text("在这里开始写正文……\n\n内容会自动保存在手机本地。",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=18.sp);inner()})}}}
