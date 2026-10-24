package com.mradio.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MRadioShareLinksTest {
    @Test fun producesClickableHttpsLinkWithEncodedStream() {
        val link = MRadioShareLinks.forStream("https://stream.example.com/live?token=abc&quality=hi")
        assertEquals(
            "https://n2k02.github.io/mradio/r/?u=https%3A%2F%2Fstream.example.com%2Flive%3Ftoken%3Dabc%26quality%3Dhi",
            link,
        )
        assertTrue(MRadioShareLinks.isSharePage(link))
    }

    @Test fun acceptsOnlyOurOwnRadioSharePage() {
        assertTrue(MRadioShareLinks.isSharePage("https://n2k02.github.io/mradio/r/?u=a"))
        assertFalse(MRadioShareLinks.isSharePage("https://n2k02.github.io/mradio/"))
        assertFalse(MRadioShareLinks.isSharePage("https://n2k02.github.io.evil.org/mradio/r/?u=a"))
        assertFalse(MRadioShareLinks.isSharePage("http://n2k02.github.io/mradio/r/?u=a"))
        assertFalse(MRadioShareLinks.isSharePage(null))
    }
}
