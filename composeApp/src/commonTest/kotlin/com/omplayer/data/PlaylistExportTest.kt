package com.omplayer.data

import com.omplayer.SharedPlaylist
import com.omplayer.model.MediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlaylistExportTest {
    private val sample = listOf(
        SharedPlaylist("Road \"trip\"", listOf("tone1", "سورة 092", "Line\nBreak \\ slash")),
        SharedPlaylist("Empty", emptyList()),
    )

    @Test fun jsonRoundTripKeepsNamesAndTracks() {
        val back = PlaylistExport.parse(PlaylistExport.toJson(sample))
        assertNotNull(back)
        assertEquals(sample.map { it.name }, back.map { it.name })
        assertEquals(sample.map { it.tracks }, back.map { it.tracks })
    }

    @Test fun acceptsBarePlaylistAndRejectsOtherJson() {
        assertEquals(1, PlaylistExport.parse("""{"name":"A","tracks":["x","y"]}""")?.size)
        assertNull(PlaylistExport.parse("""{"hello":1}"""))
        assertNull(PlaylistExport.parse("not json"))
        assertNull(PlaylistExport.parse("""{"playlists":[{"name":"A","tracks":["x"]}]} trailing"""))
    }

    @Test fun importMatchesTitlesIgnoringCaseAndSkipsUnknown() {
        val lib = UserLibrary()
        val items = listOf(
            MediaItem("a1", "u1", "Tone1", "f", isAudio = true),
            MediaItem("a2", "u2", "tone2", "f", isAudio = true),
        )
        val r = lib.importPlaylists(
            listOf(SharedPlaylist("Mix", listOf("tone1", " TONE2 ", "missing")), SharedPlaylist("None", listOf("nope"))),
            items,
        )
        assertEquals(ImportResult(playlists = 1, matched = 2, total = 4), r)
        assertEquals(listOf("a1", "a2"), lib.playlists.value.single().itemIds)
        lib.importPlaylists(listOf(SharedPlaylist("Mix", listOf("tone1"))), items)
        assertEquals(listOf("Mix", "Mix (2)"), lib.playlists.value.map { it.name })
    }

    @Test fun libraryPersistsFavouritesAndPlaylists() {
        val storage = MemoryTextStorage()
        UserLibrary(storage).apply {
            toggleFavorite("a,1\tx")
            createPlaylist("Tab\tName", listOf("a1", "b,2"))
        }
        val again = UserLibrary(storage)
        assertEquals(setOf("a,1\tx"), again.favorites.value)
        assertEquals(listOf("a1", "b,2"), again.playlists.value.single().itemIds)
        assertEquals("Tab\tName", again.playlists.value.single().name)
    }
}
