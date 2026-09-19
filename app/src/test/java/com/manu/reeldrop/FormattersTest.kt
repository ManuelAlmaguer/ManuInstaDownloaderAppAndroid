package com.manu.reeldrop

import com.manu.reeldrop.core.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {

    @Test
    fun `bytes are formatted with the right unit`() {
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("1.0 KB", Formatters.bytes(1024))
        assertEquals("1.50 MB", Formatters.bytes(1024L * 1024 * 3 / 2))
        assertEquals("—", Formatters.bytes(0))
    }

    @Test
    fun `eta is human readable`() {
        assertEquals("45s", Formatters.eta(45))
        assertEquals("2m 05s", Formatters.eta(125))
        assertEquals("1h 01m", Formatters.eta(3660))
        assertEquals("—", Formatters.eta(null))
    }

    @Test
    fun `yt-dlp speed strings are parsed to bytes per second`() {
        assertEquals(1024L * 1024, Formatters.parseSpeedToBps("1.00MiB/s"))
        assertEquals(512L * 1024, Formatters.parseSpeedToBps(" 512.0KiB/s"))
        assertEquals(0L, Formatters.parseSpeedToBps("N/A"))
    }

    @Test
    fun `yt-dlp eta strings are parsed to seconds`() {
        assertEquals(3725L, Formatters.parseEtaToSeconds("01:02:05"))
        assertEquals(125L, Formatters.parseEtaToSeconds("02:05"))
        assertNull(Formatters.parseEtaToSeconds("Unknown"))
    }
}
