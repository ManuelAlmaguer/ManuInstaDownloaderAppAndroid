package com.manu.reeldrop

import com.manu.reeldrop.util.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun `accepts instagram links`() {
        assertTrue(UrlUtils.isInstagramUrl("https://www.instagram.com/reel/Cx1y2z3/"))
        assertTrue(UrlUtils.isInstagramUrl("https://instagram.com/p/ABC123/"))
        assertTrue(UrlUtils.isInstagramUrl("http://instagr.am/reel/abc/"))
    }

    @Test
    fun `rejects other hosts`() {
        assertFalse(UrlUtils.isInstagramUrl("https://youtube.com/watch?v=1"))
        assertFalse(UrlUtils.isInstagramUrl("not a url"))
    }

    @Test
    fun `extracts url from share text`() {
        val shareText = "\"Mira este reel https://www.instagram.com/reel/Cx1y2z3/ ¡genial!\""
        assertEquals("https://www.instagram.com/reel/Cx1y2z3/", UrlUtils.extractUrl(shareText))
    }

    @Test
    fun `detects content kind and short code`() {
        assertEquals("Reel", UrlUtils.contentKind("https://instagram.com/reel/ABC123/"))
        assertEquals("Historia", UrlUtils.contentKind("https://instagram.com/stories/user/12345/"))
        assertEquals("ABC123", UrlUtils.shortCode("https://instagram.com/reel/ABC123/"))
    }
}
