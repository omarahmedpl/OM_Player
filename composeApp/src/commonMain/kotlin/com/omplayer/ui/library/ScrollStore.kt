package com.omplayer.ui.library

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Keeps one grid scroll state per screen so coming back from a folder (or another tab) lands where you left off.
 * The library screens swap in and out of composition, which would otherwise reset their lists to the top.
 */
internal class ScrollStore {
    private val states = HashMap<String, LazyGridState>()
    fun grid(key: String): LazyGridState = states.getOrPut(key) { LazyGridState() }
}

internal val LocalScrollStore = staticCompositionLocalOf { ScrollStore() }

/** Which screen the lists below belong to, e.g. "videos" or "folder:123". */
internal val LocalScrollKey = staticCompositionLocalOf { "default" }
