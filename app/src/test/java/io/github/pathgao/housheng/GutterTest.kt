package io.github.pathgao.housheng

import org.junit.Assert.assertEquals
import org.junit.Test

class GutterTest {
    @Test fun phonesKeepNarrowMarginAndWideScreensCapContentWidth() {
        assertEquals(16, gutter(360))
        assertEquals(48, gutter(800))
        assertEquals(48, gutter(914))
        assertEquals(840, 1280 - 2 * gutter(1280))
    }
}
