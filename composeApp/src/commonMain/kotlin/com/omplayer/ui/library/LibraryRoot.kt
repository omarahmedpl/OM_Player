package com.omplayer.ui.library

import com.omplayer.theme.OmMotion
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.omplayer.data.withEdit
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
    /** Online music: only offered by editions that have sources (plus). */
    data object Discover : Dest
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
    onOpenAudioPlayer: () -> Unit = {},
) {
    val deps = LocalAppDeps.current
    val repo = deps.repository
    val c = OmTheme.colors
    val desktop = deps.platform.formFactor == FormFactor.Desktop
    val state by repo.state.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { repo.refresh() }

    var dest by remember { mutableStateOf<Dest>(if (desktop) Dest.Home else Dest.Videos) }
    LaunchedEffect(deps.platform) {
        deps.platform.openDiscoverRequests.collect {
            if (deps.sources.isNotEmpty()) dest = Dest.Discover
        }
    }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var prefs by remember { mutableStateOf(ListPrefs()) }
    var recentUrls by remember { mutableStateOf(emptyList<String>()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<MediaItem?>(null) }

    val edits by deps.userData.edits.collectAsState()
    // Titles, artists and covers the user edited are applied here, so every list, queue and player shows them.
    val folders = remember(state, edits) {
        (state as? LibraryState.Ready)?.folders.orEmpty().let { fs ->
            if (edits.isEmpty()) fs else fs.map { f -> f.copy(items = f.items.map { it.withEdit(edits[it.id]) }) }
        }
    }
    val allItems = remember(folders) { folders.flatMap { it.items } }
    val searching = query.isNotBlank()

    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var playlistTarget by remember { mutableStateOf<List<MediaItem>?>(null) }
    var editTarget by remember { mutableStateOf<MediaItem?>(null) }
    val selecting = selectedIds.isNotEmpty()
    val scrollStore = remember { ScrollStore() }
    val actions = remember(onPlay, selectedIds, desktop) {
        ItemActions(
            onPlay, { info = it }, { notice = it },
            selected = selectedIds, selectable = !desktop,
            toggle = { m -> selectedIds = if (m.id in selectedIds) selectedIds - m.id else selectedIds + m.id },
            addToPlaylist = { playlistTarget = it },
            onEdit = { editTarget = it },
        )
    }
    val selectedItems = remember(selectedIds, allItems) { allItems.filter { it.id in selectedIds } }

    /** Everything the current screen lists as items, for "select all". */
    fun visibleItems(): List<MediaItem> = when {
        searching -> allItems.filter { it.title.contains(query.trim(), ignoreCase = true) }
        dest is Dest.Music -> allItems.filter { it.isAudio }
        dest is Dest.Folder -> folders.firstOrNull { it.id == (dest as Dest.Folder).id }?.videos().orEmpty()
        else -> allItems.filter { !it.isAudio }
    }

    fun shareSelected() {
        val ok = deps.platform.shareMedia(
            selectedItems.map { it.uri }, audioOnly = selectedItems.all { it.isAudio }, videoOnly = selectedItems.none { it.isAudio },
        )
        if (!ok) notice = "Sharing isn't available here"
    }

    fun deleteSelected() {
        val doomed = selectedItems
        confirmDelete = false
        deps.platform.deleteMedia(doomed.map { it.uri }) { n ->
            if (n > 0) {
                val playing = deps.engine.state.value.item?.id
                if (playing != null && doomed.any { it.id == playing }) deps.engine.stop()
                selectedIds = emptySet()
                notice = if (n == 1) "Deleted 1 file" else "Deleted $n files"
                scope.launch { repo.refresh() }
            } else notice = "Nothing was deleted"
        }
    }

    fun goBack(): Boolean = when {
        selecting -> { selectedIds = emptySet(); true }
        searching -> { query = ""; true }
        searchOpen -> { searchOpen = false; true }
        dest is Dest.Folder -> { dest = (dest as Dest.Folder).from; true }
        else -> false
    }

    // System Back (Android): clear search, close search, leave folder. At the top level the system handles it.
    PlatformBackHandler(enabled = !desktop && (selecting || searching || searchOpen || dest is Dest.Folder)) { goBack() }

    val focus = remember { FocusRequester() }
    LaunchedEffect(desktop) { if (desktop) runCatching { focus.requestFocus() } }

    CompositionLocalProvider(LocalScrollStore provides scrollStore) {
    Box(
        Modifier.fillMaxSize().background(c.bg)
            .then(if (desktop) Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { e ->
                e.type == KeyEventType.KeyDown && e.key == Key.Escape && goBack()
            } else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)),
    ) {
        val body: @Composable () -> Unit = {
          AnimatedContent(
            targetState = dest,
            contentKey = { it::class },
            transitionSpec = {
                (fadeIn(tween(OmMotion.Fast, easing = OmMotion.EaseOut)) +
                    slideInVertically(tween(OmMotion.Base, easing = OmMotion.EaseOut)) { 18 })
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "dest",
          ) { shownDest ->
            CompositionLocalProvider(LocalScrollKey provides scrollKey(shownDest, query)) {
            Body(
                dest = shownDest, state = state, folders = folders, allItems = allItems, query = query, prefs = prefs,
                actions = actions, settings = settings, onSettingsChange = onSettingsChange, recentUrls = recentUrls,
                onRecentUrls = { recentUrls = it }, onPlay = onPlay,
                onOpenFolder = { selectedIds = emptySet(); dest = Dest.Folder(it.id, dest) },
                onRefresh = { },
                repoAccess = { repo.requestAccess() },
            )
            }
          }
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
                if (selecting) SelectionBar(
                    count = selectedIds.size,
                    onClose = { selectedIds = emptySet() },
                    onSelectAll = { selectedIds = visibleItems().map { it.id }.toSet() },
                    onShare = ::shareSelected,
                    onAddToPlaylist = {
                        val audioOnly = selectedItems.filter { it.isAudio }
                        if (audioOnly.isEmpty()) notice = "Playlists hold music. Select audio files." else playlistTarget = audioOnly
                    },
                    onDelete = { confirmDelete = true },
                ) else PhoneTopBar(
                    title = title, canBack = canBack, searchOpen = searchOpen, query = query,
                    onQuery = { query = it }, onOpenSearch = { searchOpen = true },
                    onCloseSearch = { searchOpen = false; query = "" }, onBack = { goBack() },
                    showControls = dest.hasListControls(), prefs = prefs, onPrefs = { prefs = it },
                )
                Box(Modifier.weight(1f).fillMaxWidth()) { body() }
                com.omplayer.ui.player.MiniPlayer(onOpenAudioPlayer)
                BottomNav(dest) { dest = it; query = ""; searchOpen = false; selectedIds = emptySet() }
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
        editTarget?.let { t ->
            EditSongDialog(t, onDone = { msg -> editTarget = null; notice = msg }, onDismiss = { editTarget = null })
        }
        playlistTarget?.let { target ->
            PlaylistPickerDialog(target, onDone = { msg -> playlistTarget = null; selectedIds = emptySet(); notice = msg }, onDismiss = { playlistTarget = null })
        }
        if (confirmDelete) ConfirmDeleteDialog(selectedIds.size, ::deleteSelected, { confirmDelete = false })
        info?.let { item ->
            InfoDialog(item, folders.firstOrNull { it.id == item.folderId }?.path, onDismiss = { info = null })
        }
    }
    }
}

private fun scrollKey(dest: Dest, query: String): String = when {
    query.isNotBlank() -> "search"
    dest is Dest.Folder -> "folder:${dest.id}"
    else -> dest::class.simpleName.orEmpty()
}

private fun Dest.hasListControls() = this is Dest.Home || this is Dest.Videos || this is Dest.Music || this is Dest.Folders || this is Dest.Folder

private fun titleFor(dest: Dest, folders: List<MediaFolder>) = when (dest) {
    Dest.Home -> "Home"
    Dest.Videos -> "Videos"
    Dest.Music -> "Music"
    Dest.Discover -> "Discover"
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
    // Settings, Network and Discover don't depend on library state.
    when (dest) {
        Dest.Settings -> { SettingsScreen(settings, onSettingsChange); return }
        Dest.Network -> { NetworkScreen(recentUrls, onRecentUrls, onPlay); return }
        Dest.Discover -> { DiscoverHome(actions); return }
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
                Dest.Music -> MusicHome(allItems, folders, prefs, actions)
                is Dest.Folder -> folders.firstOrNull { it.id == dest.id }?.let { FolderContent(it, prefs, actions) }
                else -> {}
            }
        }
    }
}

// ---------- phone chrome ----------

/** Replaces the top bar while items are selected (audio and video alike). */
@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onShare: () -> Unit, onAddToPlaylist: () -> Unit, onDelete: () -> Unit) {
    val c = OmTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(c.surface1).padding(horizontal = OmSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OmIconButton(Icons.Rounded.Close, "Cancel selection", onClose)
        OmText("$count selected", size = 18.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = OmSpace.s2))
        OmIconButton(Icons.Rounded.SelectAll, "Select all", onSelectAll)
        OmIconButton(Icons.Rounded.PlaylistAdd, "Add selected to playlist", onAddToPlaylist)
        OmIconButton(Icons.Rounded.Share, "Share selected", onShare)
        OmIconButton(Icons.Rounded.Delete, "Delete selected", onDelete, tint = c.danger)
    }
}

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
                if (showControls) {
                    OmIconButton(Icons.Rounded.Search, "Search", onOpenSearch)
                    ViewControls(prefs, onPrefs)
                }
            }
            else -> {
                if (title == "Videos") OmWordmark(modifier = Modifier.padding(start = OmSpace.s2).weight(1f))
                else OmText(title, size = 22.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = OmSpace.s2).weight(1f))
                if (showControls) {
                    OmIconButton(Icons.Rounded.Search, "Search", onOpenSearch)
                    ViewControls(prefs, onPrefs)
                }
            }
        }
    }
}

@Composable
private fun BottomNav(dest: Dest, onSelect: (Dest) -> Unit) {
    val c = OmTheme.colors
    val items = buildList {
        add(NavItem(Dest.Videos, "Videos", Icons.Rounded.VideoLibrary))
        add(NavItem(Dest.Music, "Music", Icons.Rounded.MusicNote))
        // Discover is its own destination, and only exists where the edition has online sources.
        if (LocalAppDeps.current.sources.isNotEmpty()) add(NavItem(Dest.Discover, "Discover", Icons.Rounded.Explore))
        add(NavItem(Dest.Network, "Network", Icons.Rounded.Public))
        add(NavItem(Dest.Settings, "Settings", Icons.Rounded.Settings))
    }
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
