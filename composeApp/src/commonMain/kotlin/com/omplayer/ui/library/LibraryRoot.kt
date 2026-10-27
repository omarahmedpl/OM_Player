package com.omplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.AppSettings
import com.omplayer.FormFactor
import com.omplayer.LocalAppDeps
import com.omplayer.PlatformBackHandler
import com.omplayer.data.LibraryState
import com.omplayer.model.MediaFolder
import com.omplayer.model.MediaItem
import com.omplayer.theme.OmRadius
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import com.omplayer.ui.OmWordmark
import com.omplayer.ui.components.OmIconButton
import com.omplayer.ui.components.OmText
import com.omplayer.ui.components.OmTextField
import com.omplayer.ui.components.pressable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** In-memory navigation. Folder remembers where to go back to. */
internal sealed interface Dest {
    data object Home : Dest
    data object Videos : Dest
    data object Music : Dest
    data object Folders : Dest
    data object Network : Dest
    data object Settings : Dest
    data class Folder(val id: String, val from: Dest) : Dest
}

private class NavItem(val dest: Dest, val label: String, val icon: ImageVector)

/**
 * Library shell. CONTRACT (called from App.kt): signature must stay as-is.
 * Phone: top bar + bottom navigation. Desktop: collapsible sidebar + content.
 */
@Composable
fun LibraryRoot(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onPlay: (item: MediaItem, queue: List<MediaItem>) -> Unit,
) {
    val deps = LocalAppDeps.current
    val repo = deps.repository
    val c = OmTheme.colors
    val desktop = deps.platform.formFactor == FormFactor.Desktop
    val state by repo.state.collectAsState()
    LaunchedEffect(Unit) { repo.refresh() }

    var dest by remember { mutableStateOf<Dest>(if (desktop) Dest.Home else Dest.Videos) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var prefs by remember { mutableStateOf(ListPrefs()) }
    var recentUrls by remember { mutableStateOf(emptyList<String>()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<MediaItem?>(null) }

    val folders = (state as? LibraryState.Ready)?.folders.orEmpty()
    val allItems = folders.flatMap { it.items }
    val searching = query.isNotBlank()

    val actions = remember(onPlay) { ItemActions(onPlay, { info = it }, { notice = it }) }

    fun goBack(): Boolean = when {
        searching -> { query = ""; true }
        searchOpen -> { searchOpen = false; true }
        dest is Dest.Folder -> { dest = (dest as Dest.Folder).from; true }
        else -> false
    }

    // System Back (Android): clear search, close search, leave folder. At the top level the system handles it.
    PlatformBackHandler(enabled = !desktop && (searching || searchOpen || dest is Dest.Folder)) { goBack() }

    val focus = remember { FocusRequester() }
    LaunchedEffect(desktop) { if (desktop) runCatching { focus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(c.bg)
            .then(if (desktop) Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { e ->
                e.type == KeyEventType.KeyDown && e.key == Key.Escape && goBack()
            } else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)),
    ) {
        val body: @Composable () -> Unit = {
            Body(
                dest = dest, state = state, folders = folders, allItems = allItems, query = query, prefs = prefs,
                actions = actions, settings = settings, onSettingsChange = onSettingsChange, recentUrls = recentUrls,
                onRecentUrls = { recentUrls = it }, onPlay = onPlay,
                onOpenFolder = { dest = Dest.Folder(it.id, dest) },
                onRefresh = { },
                repoAccess = { repo.requestAccess() },
            )
        }
        val title = titleFor(dest, folders)
        val canBack = dest is Dest.Folder

        if (desktop) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val collapsed = maxWidth < 900.dp
                Row(Modifier.fillMaxSize()) {
                    Sidebar(dest, collapsed) { dest = it; query = "" }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        DesktopHeader(
                            title = title, canBack = canBack, query = query, onQuery = { query = it },
                            showControls = dest.hasListControls(), prefs = prefs, onPrefs = { prefs = it },
                            onBack = { goBack() },
                        )
                        Box(Modifier.weight(1f).fillMaxWidth()) { body() }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                PhoneTopBar(
                    title = title, canBack = canBack, searchOpen = searchOpen, query = query,
                    onQuery = { query = it }, onOpenSearch = { searchOpen = true },
                    onCloseSearch = { searchOpen = false; query = "" }, onBack = { goBack() },
                    showControls = dest.hasListControls(), prefs = prefs, onPrefs = { prefs = it },
                )
                Box(Modifier.weight(1f).fillMaxWidth()) { body() }
                BottomNav(dest) { dest = it; query = ""; searchOpen = false }
            }
        }

        // Transient notice (toast replacement).
        notice?.let { msg ->
            LaunchedEffect(msg) { delay(2200); if (notice == msg) notice = null }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (desktop) OmSpace.s8 else 88.dp)) {
                Box(Modifier.clip(RoundedCornerShape(OmRadius.md)).background(c.surface3).border(1.dp, c.hairlineStrong, RoundedCornerShape(OmRadius.md)).padding(horizontal = OmSpace.s4, vertical = OmSpace.s3)) {
                    OmText(msg, size = 14.sp)
                }
            }
        }
        info?.let { item ->
            InfoDialog(item, folders.firstOrNull { it.id == item.folderId }?.path, onDismiss = { info = null })
        }
    }
}

private fun Dest.hasListControls() = this is Dest.Home || this is Dest.Videos || this is Dest.Music || this is Dest.Folders || this is Dest.Folder

private fun titleFor(dest: Dest, folders: List<MediaFolder>) = when (dest) {
    Dest.Home -> "Home"
    Dest.Videos -> "Videos"
    Dest.Music -> "Music"
    Dest.Folders -> "Folders"
    Dest.Network -> "Network stream"
    Dest.Settings -> "Settings"
    is Dest.Folder -> folders.firstOrNull { it.id == dest.id }?.name ?: "Folder"
}

@Composable
private fun Body(
    dest: Dest, state: LibraryState, folders: List<MediaFolder>, allItems: List<MediaItem>, query: String,
    prefs: ListPrefs, actions: ItemActions, settings: AppSettings, onSettingsChange: (AppSettings) -> Unit,
    recentUrls: List<String>, onRecentUrls: (List<String>) -> Unit, onPlay: (MediaItem, List<MediaItem>) -> Unit,
    onOpenFolder: (MediaFolder) -> Unit, onRefresh: () -> Unit, repoAccess: () -> Unit,
) {
    val repo = LocalAppDeps.current.repository
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val refresh: () -> Unit = { scope.launch { repo.refresh() } }
    // Settings and Network don't depend on library state.
    when (dest) {
        Dest.Settings -> { SettingsScreen(settings, onSettingsChange); return }
        Dest.Network -> { NetworkScreen(recentUrls, onRecentUrls, onPlay); return }
        else -> {}
    }
    when (state) {
        LibraryState.Loading -> LoadingSkeleton()
        LibraryState.NeedsPermission -> PermissionState(repoAccess)
        is LibraryState.Failed -> FailedState(state.message, refresh)
        is LibraryState.Ready -> when {
            query.isNotBlank() -> SearchResults(query, allItems, prefs, actions)
            else -> when (dest) {
                Dest.Home -> HomeContent(folders, prefs, showContinue = true, actions, onOpenFolder, refresh)
                Dest.Folders -> HomeContent(folders, prefs, showContinue = false, actions, onOpenFolder, refresh)
                Dest.Videos -> if (LocalAppDeps.current.platform.formFactor == FormFactor.Phone)
                    HomeContent(folders, prefs, showContinue = true, actions, onOpenFolder, refresh)
                else ItemList(allItems.filter { !it.isAudio }, prefs, actions, Modifier.fillMaxSize())
                Dest.Music -> MusicContent(allItems, prefs, actions)
                is Dest.Folder -> folders.firstOrNull { it.id == dest.id }?.let { FolderContent(it, prefs, actions) }
                else -> {}
            }
        }
    }
}

// ---------- phone chrome ----------

@Composable
private fun PhoneTopBar(
    title: String, canBack: Boolean, searchOpen: Boolean, query: String, onQuery: (String) -> Unit,
    onOpenSearch: () -> Unit, onCloseSearch: () -> Unit, onBack: () -> Unit,
    showControls: Boolean, prefs: ListPrefs, onPrefs: (ListPrefs) -> Unit,
) {
    val c = OmTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = OmSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            searchOpen -> {
                OmIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Close search", onCloseSearch)
                OmTextField(
                    query, onQuery, Modifier.weight(1f).padding(end = OmSpace.s2),
                    placeholder = "Search videos", leading = Icons.Rounded.Search, description = "Search videos",
                )
            }
            canBack -> {
                OmIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                OmText(title, size = 20.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OmIconButton(Icons.Rounded.Search, "Search", onOpenSearch)
                if (showControls) ViewControls(prefs, onPrefs)
            }
            else -> {
                if (title == "Videos") OmWordmark(modifier = Modifier.padding(start = OmSpace.s2).weight(1f))
                else OmText(title, size = 22.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = OmSpace.s2).weight(1f))
                OmIconButton(Icons.Rounded.Search, "Search", onOpenSearch)
                if (showControls) ViewControls(prefs, onPrefs)
            }
        }
    }
}

@Composable
private fun BottomNav(dest: Dest, onSelect: (Dest) -> Unit) {
    val c = OmTheme.colors
    val items = listOf(
        NavItem(Dest.Videos, "Videos", Icons.Rounded.VideoLibrary),
        NavItem(Dest.Music, "Music", Icons.Rounded.MusicNote),
        NavItem(Dest.Network, "Network", Icons.Rounded.Public),
        NavItem(Dest.Settings, "Settings", Icons.Rounded.Settings),
    )
    val current = if (dest is Dest.Folder) Dest.Videos else dest
    Column(Modifier.fillMaxWidth().background(c.surface1)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        Row(Modifier.fillMaxWidth()) {
            items.forEach { n ->
                val on = current == n.dest
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp)
                        .pressable({ onSelect(n.dest) }, role = Role.Tab)
                        .semantics { selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) c.primarySoft else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = 18.dp, vertical = 4.dp)) {
                        Icon(n.icon, null, tint = if (on) c.primary else c.text3, modifier = Modifier.size(22.dp))
                    }
                    OmText(n.label, color = if (on) c.primary else c.text3, size = 11.sp, weight = FontWeight.Medium)
                }
            }
        }
    }
}

// ---------- desktop chrome ----------

@Composable
private fun Sidebar(dest: Dest, collapsed: Boolean, onSelect: (Dest) -> Unit) {
    val c = OmTheme.colors
    val items = listOf(
        NavItem(Dest.Home, "Home", Icons.Rounded.Home),
        NavItem(Dest.Videos, "Videos", Icons.Rounded.VideoLibrary),
        NavItem(Dest.Music, "Music", Icons.Rounded.MusicNote),
        NavItem(Dest.Folders, "Folders", Icons.Rounded.Folder),
        NavItem(Dest.Network, "Network", Icons.Rounded.Public),
        NavItem(Dest.Settings, "Settings", Icons.Rounded.Settings),
    )
    val current = if (dest is Dest.Folder) dest.from else dest
    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier.width(if (collapsed) 64.dp else 220.dp).fillMaxHeight().background(c.surface1).padding(OmSpace.s2),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(Modifier.height(56.dp).fillMaxWidth().padding(horizontal = OmSpace.s2), contentAlignment = Alignment.CenterStart) {
                if (collapsed) com.omplayer.ui.OmMark(28.dp) else OmWordmark()
            }
            items.forEach { n ->
                val on = current == n.dest
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(OmRadius.sm))
                        .background(if (on) c.primarySoft else androidx.compose.ui.graphics.Color.Transparent)
                        .pressable({ onSelect(n.dest) }, role = Role.Tab)
                        .semantics { selected = on; if (collapsed) contentDescription = n.label }
                        .padding(horizontal = if (collapsed) 0.dp else OmSpace.s3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
                ) {
                    Icon(n.icon, null, tint = if (on) c.primary else c.text2, modifier = Modifier.size(22.dp))
                    if (!collapsed) {
                        Spacer(Modifier.width(OmSpace.s3))
                        OmText(n.label, color = if (on) c.text else c.text2, weight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.hairline))
    }
}

@Composable
private fun DesktopHeader(
    title: String, canBack: Boolean, query: String, onQuery: (String) -> Unit,
    showControls: Boolean, prefs: ListPrefs, onPrefs: (ListPrefs) -> Unit, onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = OmSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OmSpace.s2),
    ) {
        if (canBack) OmIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        OmText(title, size = 22.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = if (canBack) 0.dp else OmSpace.s2).weight(1f))
        OmTextField(
            query, onQuery, Modifier.width(280.dp), placeholder = "Search", leading = Icons.Rounded.Search,
            description = "Search all videos",
        )
        if (query.isNotEmpty()) OmIconButton(Icons.Rounded.Close, "Clear search", { onQuery("") })
        if (showControls) ViewControls(prefs, onPrefs)
    }
}
