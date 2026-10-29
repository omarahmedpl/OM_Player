package com.omplayer.ui.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioPlayerGestureTest {
    @Test
    fun axisLocksOnlyAfterTouchSlopAndFollowsDominantMotion() {
        assertNull(audioSwipeAxis(3f, 4f, 6f))
        assertEquals(AudioSwipeAxis.Horizontal, audioSwipeAxis(12f, 4f, 6f))
        assertEquals(AudioSwipeAxis.Vertical, audioSwipeAxis(4f, 12f, 6f))
    }

    @Test
    fun horizontalDirectionChangesTrackOnlyPastGestureThreshold() {
        assertNull(audioTrackDirection(-55f, 56f))
        assertEquals(AudioTrackDirection.Next, audioTrackDirection(-56f, 56f))
        assertEquals(AudioTrackDirection.Previous, audioTrackDirection(56f, 56f))
    }
}
