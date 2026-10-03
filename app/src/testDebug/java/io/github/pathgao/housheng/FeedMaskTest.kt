package io.github.pathgao.housheng

import org.junit.Assert.*
import org.junit.Test

class FeedMaskTest {
    @Test fun liveBlurWinsThenFrameBlurThenSolid() {
        assertEquals(MaskStyle.LIVE_BLUR, maskStyle(blurEnabled = true, frameCaptured = false))
        assertEquals(MaskStyle.FRAME_BLUR, maskStyle(blurEnabled = false, frameCaptured = true))
        assertEquals(MaskStyle.SOLID, maskStyle(blurEnabled = false, frameCaptured = false))
    }

    @Test fun coversAreClippedToTheScreen() {
        assertArrayEquals(intArrayOf(10, 20, 300, 400), clipToScreen(10, 20, 300, 400, 1080, 2400))
        assertArrayEquals(intArrayOf(0, 0, 50, 2400), clipToScreen(-30, -5, 50, 2600, 1080, 2400))
        assertNull(clipToScreen(1100, 10, 1300, 200, 1080, 2400))
        assertNull(clipToScreen(10, -300, 200, 0, 1080, 2400))
    }

    @Test fun blurErasesFineDetailAndKeepsSize() {
        val black = 0xff000000.toInt()
        val white = 0xffffffff.toInt()
        // A one-pixel checkerboard stands in for text strokes.
        val pixels = IntArray(12 * 9) { if ((it % 12 + it / 12) % 2 == 0) black else white }
        val blurred = gaussianBlur(pixels, 12, 9, 4)
        assertEquals(pixels.size, blurred.size)
        for ((index, pixel) in blurred.withIndex()) {
            assertEquals("alpha stays opaque", 0xff, pixel ushr 24)
            // Neighbours that differed by 255 must now differ by at most 16.
            val neighbours = listOfNotNull(blurred.getOrNull(index + 1).takeIf { index % 12 < 11 }, blurred.getOrNull(index + 12))
            for (next in neighbours) assertTrue("detail left at $index", kotlin.math.abs((pixel and 0xff) - (next and 0xff)) <= 16)
        }
    }

    @Test fun blurNeverDarkensOrBrightensAFlatImageAtItsEdges() {
        val teal = 0xff2a9d8f.toInt()
        assertArrayEquals(IntArray(5 * 3) { teal }, gaussianBlur(IntArray(5 * 3) { teal }, 5, 3, 4))
        assertArrayEquals("a single column still works", intArrayOf(teal, teal), gaussianBlur(intArrayOf(teal, teal), 1, 2, 4))
    }
}
