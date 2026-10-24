package com.mradio.app

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MRadioShareLinksTest {
    @Test fun encodesWithoutExposingStreamUrlAndDecodesExactly() {
        val stream = "https://stream.example.com/live?token=abc&quality=hi"
        val link = MRadioShareLinks.forStream(stream)
        assertTrue(link.startsWith("https://n2k02.github.io/mradio/r/?d=1"))
        assertFalse(link.contains("stream.example.com"))
        assertTrue(MRadioShareLinks.isSharePage(link))
        val token = URI(link).rawQuery.substringAfter("d=")
        assertEquals(stream, MRadioShareLinks.decodeToken(token))
    }

    @Test fun httpAndHttpsLinksWithNonAsciiCharactersSurviveRoundTrip() {
        for (stream in listOf(
            "http://radio.example.com:8000/live",
            "https://radio.example.com/live.m3u8?token=x%2By%3Dz&quality=hi",
            "https://radio.example.com/r%C3%A9union/stream.mp3"
        )) {
            val token = URI(MRadioShareLinks.forStream(stream)).rawQuery.substringAfter("d=")
            assertEquals(stream, MRadioShareLinks.decodeToken(token))
        }
    }

    @Test fun rejectsCorruptedOrUnexpectedTokens() {
        assertNull(MRadioShareLinks.decodeToken(null))
        assertNull(MRadioShareLinks.decodeToken("2abc"))
        assertNull(MRadioShareLinks.decodeToken("1%%%"))
        assertNull(MRadioShareLinks.decodeToken("1AA"))
        assertNull(MRadioShareLinks.decodeToken("1" + "A".repeat(4096)))
    }

    @Test fun acceptsOnlyOurOwnRadioSharePage() {
        assertTrue(MRadioShareLinks.isSharePage("https://n2k02.github.io/mradio/r/?d=abc"))
        assertFalse(MRadioShareLinks.isSharePage("https://n2k02.github.io/mradio/"))
        assertFalse(MRadioShareLinks.isSharePage("https://n2k02.github.io.evil.org/mradio/r/?d=abc"))
        assertFalse(MRadioShareLinks.isSharePage("http://n2k02.github.io/mradio/r/?d=abc"))
        assertFalse(MRadioShareLinks.isSharePage(null))
    }
}
