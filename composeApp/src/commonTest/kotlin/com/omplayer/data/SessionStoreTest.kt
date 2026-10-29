package com.omplayer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionStoreTest {
    @Test fun roundTripsQueueIndexAndPosition() {
        val storage = MemoryTextStorage()
        SessionStore(storage).save(SavedSession(listOf("a1", "b,2", "c\t3"), index = 1, positionMs = 13_600))
        val back = SessionStore(storage).load()
        assertNotNull(back)
        assertEquals(listOf("a1", "b,2", "c\t3"), back.ids)
        assertEquals(1, back.index)
        assertEquals(13_600L, back.positionMs)
    }

    @Test fun clearedOrBrokenSessionsLoadAsNothing() {
        val storage = MemoryTextStorage()
        val store = SessionStore(storage)
        assertNull(store.load())
        store.save(SavedSession(listOf("a"), 0, 5))
        store.clear()
        assertNull(store.load())
        storage.write("7\t10\ta,b") // index outside the queue
        assertNull(store.load())
        storage.write("not a session")
        assertNull(store.load())
    }
}
