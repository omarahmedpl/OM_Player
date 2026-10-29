@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.omplayer

import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.omplayer.data.FileSystemMediaRepository
import com.omplayer.data.JsonFileProgressStore
import com.omplayer.model.MediaItem
import com.omplayer.player.FakePlayerEngine
import com.omplayer.player.PlayerEngine
import com.omplayer.player.VlcjPlayerEngine
import java.io.File
import java.net.URI
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** The Playhead mark: rose arc (75% of a circle from 12 o'clock, clockwise) with a white dot at its end. */
private object PlayheadPainter : Painter() {
    override val intrinsicSize = Size(256f, 256f)
    override fun DrawScope.onDraw() {
        val stroke = size.minDimension * 0.14f
        val r = (size.minDimension - stroke) / 2f * 0.9f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawArc(
            color = Color(0xFFE11D48), startAngle = -90f, sweepAngle = 270f, useCenter = false,
            topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val a = (-90.0 + 270.0) * PI / 180.0
        drawCircle(Color.White, radius = stroke * 0.55f, center = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()))
    }
}

private fun mediaItemFor(f: File): MediaItem? {
    val ext = f.extension.lowercase()
    val audio = ext in FileSystemMediaRepository.AUDIO
    if (!f.isFile || (!audio && ext !in FileSystemMediaRepository.VIDEO)) return null
    val dir = f.absoluteFile.parentFile?.absolutePath ?: ""
    return MediaItem(
        id = f.absolutePath, uri = f.absolutePath, title = f.nameWithoutExtension, folderId = dir,
        sizeBytes = f.length(), dateAddedMs = f.lastModified(), isAudio = audio,
    )
}

fun main(args: Array<String>) {
    val openChannel = Channel<MediaItem>(Channel.UNLIMITED)
    args.firstOrNull()?.let { mediaItemFor(File(it))?.let(openChannel::trySend) }

    application {
        val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
        var vlcOk by remember { mutableStateOf(true) }
        var acknowledged by remember { mutableStateOf(false) }
        val engine: PlayerEngine = remember {
            runCatching { VlcjPlayerEngine() }.getOrElse { vlcOk = false; FakePlayerEngine() }
        }
        val platform = remember { DesktopPlatformActions(windowState) }
        val deps = remember {
            AppDeps(
                repository = FileSystemMediaRepository(),
                progress = JsonFileProgressStore(),
                engine = engine,
                platform = platform,
                userData = com.omplayer.data.UserLibrary(com.omplayer.data.FileTextStorage()),
                lyrics = com.omplayer.data.LrcLibLyrics(),
            )
        }
        val openRequests = remember { openChannel.receiveAsFlow() }
        val exit = { engine.release(); exitApplication() }

        if (!vlcOk && !acknowledged) {
            Window(onCloseRequest = ::exitApplication, title = "OM Player", icon = PlayheadPainter,
                state = rememberWindowState(size = DpSize(480.dp, 260.dp))) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                    Text("Install VLC 3.x to play videos")
                    Text("OM Player uses VLC's playback engine (libVLC), which was not found on this computer.")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { acknowledged = true }) { Text("Continue without playback") }
                        OutlinedButton(onClick = ::exitApplication) { Text("Quit") }
                    }
                }
            }
        } else {
            val dropTarget = remember {
                object : DragAndDropTarget {
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        val data = event.dragData() as? DragData.FilesList ?: return false
                        var any = false
                        data.readFiles().forEach { u ->
                            val f = runCatching { File(URI(u)) }.getOrNull() ?: return@forEach
                            mediaItemFor(f)?.let { openChannel.trySend(it); any = true }
                        }
                        return any
                    }
                }
            }
            Window(
                onCloseRequest = exit,
                title = "OM Player",
                icon = PlayheadPainter,
                state = windowState,
                // Fallback: Esc leaves fullscreen even when no player control has focus (switching placement can drop
                // Compose focus on Windows). onKeyEvent bubbles, so the player still closes an open panel first.
                onKeyEvent = { e ->
                    e.type == KeyEventType.KeyDown && e.key == Key.Escape && platform.exitFullscreenIfActive()
                },
            ) {
                window.minimumSize = java.awt.Dimension(960, 600)
                platform.window = window
                Box(
                    Modifier.fillMaxSize().dragAndDropTarget(
                        shouldStartDragAndDrop = { it.dragData() is DragData.FilesList },
                        target = dropTarget,
                    ),
                ) { App(deps, openRequests) }
            }
        }
    }
}
