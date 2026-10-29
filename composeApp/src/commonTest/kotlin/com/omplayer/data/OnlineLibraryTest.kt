package com.omplayer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnlineLibraryTest {
    @Test fun favouritingIsAToggle() {
        val lib = OnlineLibrary(MemoryTextStorage())
        assertFalse(lib.isFavorite("ytdlp:a"))

        lib.toggleFavorite("ytdlp:a")
        assertTrue(lib.isFavorite("ytdlp:a"))

        lib.toggleFavorite("ytdlp:a")
        assertFalse(lib.isFavorite("ytdlp:a"))
    }

    @Test fun favouritesSurviveARestart() {
        val storage = MemoryTextStorage()
        OnlineLibrary(storage).toggleFavorite("ytdlp:a")

        assertTrue(OnlineLibrary(storage).isFavorite("ytdlp:a"))
    }

    @Test fun aPlaylistKeepsItsOrderAndIgnoresRepeats() {
        val lib = OnlineLibrary(MemoryTextStorage())
        val list = lib.createPlaylist("Chill")

        lib.addToPlaylist(list.id, listOf("ytdlp:a", "ytdlp:b"))
        lib.addToPlaylist(list.id, listOf("ytdlp:a"))

        assertEquals(listOf("ytdlp:a", "ytdlp:b"), lib.playlists.value.single().keys)
    }

    @Test fun playlistsSurviveARestart() {
        val storage = MemoryTextStorage()
        val lib = OnlineLibrary(storage)
        val list = lib.createPlaylist("Chill")
        lib.addToPlaylist(list.id, listOf("ytdlp:a"))

        val back = OnlineLibrary(storage)
        assertEquals("Chill", back.playlists.value.single().name)
        assertEquals(listOf("ytdlp:a"), back.playlists.value.single().keys)
    }

    @Test fun creatingAPlaylistWithTheSameNameReturnsTheExistingOne() {
        val lib = OnlineLibrary(MemoryTextStorage())
        val first = lib.createPlaylist("Chill")
        val second = lib.createPlaylist("chill")

        assertEquals(first.id, second.id)
        assertEquals(1, lib.playlists.value.size)
    }

    @Test fun removingATrackFromAPlaylistLeavesTheRest() {
        val lib = OnlineLibrary(MemoryTextStorage())
        val list = lib.createPlaylist("Chill")
        lib.addToPlaylist(list.id, listOf("ytdlp:a", "ytdlp:b"))

        lib.removeFromPlaylist(list.id, "ytdlp:a")

        assertEquals(listOf("ytdlp:b"), lib.playlists.value.single().keys)
    }

    @Test fun deletingAPlaylistRemovesOnlyThatOne() {
        val lib = OnlineLibrary(MemoryTextStorage())
        val a = lib.createPlaylist("A")
        lib.createPlaylist("B")

        lib.deletePlaylist(a.id)

        assertEquals(listOf("B"), lib.playlists.value.map { it.name })
    }

    @Test fun forgettingATrackClearsItFromFavouritesAndPlaylists() {
        val lib = OnlineLibrary(MemoryTextStorage())
        val list = lib.createPlaylist("Chill")
        lib.toggleFavorite("ytdlp:a")
        lib.addToPlaylist(list.id, listOf("ytdlp:a", "ytdlp:b"))

        lib.forget("ytdlp:a")

        assertFalse(lib.isFavorite("ytdlp:a"))
        assertEquals(listOf("ytdlp:b"), lib.playlists.value.single().keys)
    }

    @Test fun theOnlineListsAreSeparateFromTheDeviceLists() {
        // Separate storages, exactly as the app wires them (om_online_library vs the device library).
        val online = OnlineLibrary(MemoryTextStorage())
        val device = UserLibrary(MemoryTextStorage())

        online.toggleFavorite("ytdlp:a")
        device.toggleFavorite("song-1")

        assertTrue(online.isFavorite("ytdlp:a"))
        assertFalse(online.isFavorite("song-1"))
        assertTrue(device.favorites.value.contains("song-1"))
        assertFalse(device.favorites.value.contains("ytdlp:a"))
    }
}
