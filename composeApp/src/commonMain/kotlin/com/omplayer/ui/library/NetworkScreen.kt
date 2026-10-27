package com.omplayer.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omplayer.model.MediaItem
import com.omplayer.theme.OmSpace
import com.omplayer.theme.OmTheme
import com.omplayer.ui.components.OmButton
import com.omplayer.ui.components.OmText
import com.omplayer.ui.components.OmTextField
import com.omplayer.ui.components.SectionHeader
import com.omplayer.ui.components.pressable

private val UrlRegex = Regex("^(https?|rtsp|rtmp)://[^\\s/?#]+[^\\s]*$", RegexOption.IGNORE_CASE)

internal fun isValidStreamUrl(s: String) = UrlRegex.matches(s.trim())

internal fun streamTitle(url: String): String {
    val rest = url.substringAfter("://").substringBefore('#').substringBefore('?')
    val host = rest.substringBefore('/')
    val last = rest.substringAfter('/', "").trimEnd('/').substringAfterLast('/')
    return last.ifBlank { host }
}

@Composable
internal fun NetworkScreen(
    recent: List<String>,
    onRecent: (List<String>) -> Unit,
    onPlay: (MediaItem, List<MediaItem>) -> Unit,
) {
    val c = OmTheme.colors
    var url by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val trimmed = url.trim()
    val error = when {
        !attempted -> null
        trimmed.isEmpty() -> "Enter a stream URL."
        !isValidStreamUrl(trimmed) -> "Use a full address starting with http://, https://, rtsp:// or rtmp://."
        else -> null
    }

    fun play(target: String) {
        val t = target.trim()
        val item = MediaItem(id = t, uri = t, title = streamTitle(t), folderId = "network")
        onRecent((listOf(t) + recent.filter { it != t }).take(8))
        onPlay(item, listOf(item))
    }

    fun submit() {
        attempted = true
        if (isValidStreamUrl(trimmed)) play(trimmed)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = contentPad()).padding(top = OmSpace.s4, bottom = OmSpace.s8),
    ) {
        OmTextField(
            value = url, onValueChange = { url = it },
            label = "Stream URL", placeholder = "https://example.com/video.m3u8",
            error = error, leading = Icons.Rounded.Link, keyboardType = KeyboardType.Uri,
            onSubmit = ::submit, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(OmSpace.s3))
        OmText("Supports http, https, rtsp and rtmp streams.", color = c.text3, size = 13.sp)
        Spacer(Modifier.height(OmSpace.s4))
        OmButton("Play", ::submit, icon = Icons.Rounded.PlayArrow, modifier = Modifier.fillMaxWidth())

        if (recent.isNotEmpty()) {
            SectionHeader("Recent")
            recent.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).pressable({ url = r; play(r) }).padding(vertical = OmSpace.s2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OmSpace.s3),
                ) {
                    Icon(Icons.Rounded.History, null, tint = c.text3, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        OmText(streamTitle(r), weight = androidx.compose.ui.text.font.FontWeight.Medium)
                        OmText(r, color = c.text3, size = 12.sp)
                    }
                }
            }
        }
    }
}
