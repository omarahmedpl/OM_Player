package com.omplayer.player

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioQueueOrderTest {
    @Test
    fun queueDisplayUsesShuffleTraversalIndexes() {
        assertEquals(listOf(2, 0, 3, 1), queueDisplayOrder(4, listOf(2, 0, 3, 1)))
    }

    @Test
    fun queueDisplayKeepsMissingIndexesAndDropsInvalidDuplicates() {
        assertEquals(listOf(2, 0, 1, 3), queueDisplayOrder(4, listOf(2, 0, 2, 8, -1)))
    }

    @Test
    fun queueDisplayUsesNaturalOrderWhenTraversalIsEmpty() {
        assertEquals(listOf(0, 1, 2), queueDisplayOrder(3, emptyList()))
    }
}
