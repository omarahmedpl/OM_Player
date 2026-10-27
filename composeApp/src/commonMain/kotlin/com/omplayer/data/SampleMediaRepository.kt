package com.omplayer.data

import com.omplayer.model.MediaFolder
import com.omplayer.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Fictional sample library for previews and for running the UI before a platform repository exists. */
class SampleMediaRepository : MediaRepository {
    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    override val state: StateFlow<LibraryState> = _state

    override suspend fun refresh() {
        fun v(folder: String, n: Int, title: String, min: Int, h: Int) = MediaItem(
            id = "$folder-$n", uri = "sample://$folder/$n", title = title, folderId = folder,
            durationMs = min * 60_000L, sizeBytes = min * 18_000_000L, width = h * 16 / 9, height = h,
            dateAddedMs = 1_790_000_000_000L - n * 86_400_000L,
        )
        _state.value = LibraryState.Ready(
            listOf(
                MediaFolder("movies", "Movies", "/Movies", listOf(
                    v("movies", 0, "The Glass Orchard", 91, 2160), v("movies", 1, "Harbor Lights", 108, 1080),
                    v("movies", 2, "The Quiet Orbit", 97, 1080),
                )),
                MediaFolder("camera", "Camera", "/DCIM/Camera", listOf(
                    v("camera", 0, "Beach sunset timelapse", 1, 2160), v("camera", 1, "Dog vs. sprinkler", 1, 1080),
                )),
                MediaFolder("downloads", "Downloads", "/Download", listOf(
                    v("downloads", 0, "Saltwater Diaries — Ep. 4", 45, 1080), v("downloads", 1, "Northern Drift S01E02", 52, 720),
                )),
            )
        )
    }

    override fun requestAccess() {}
}
