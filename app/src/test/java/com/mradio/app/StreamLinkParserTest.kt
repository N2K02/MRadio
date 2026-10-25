package com.mradio.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamLinkParserTest {
    @Test fun sharedHttpsStreamIsNotTruncatedAtLetterS() {
        assertEquals(
            "https://stream.zeno.fm/stream123",
            StreamLinkParser.httpUrlFromText("Listen now: https://stream.zeno.fm/stream123"),
        )
    }

    @Test fun retainsQueryParametersAndStopsAtWhitespace() {
        assertEquals(
            "https://radio.example.com/live.m3u8?token=abc&quality=high",
            StreamLinkParser.httpUrlFromText(
                "Radio: https://radio.example.com/live.m3u8?token=abc&quality=high next",
            ),
        )
    }

    @Test fun trimsPunctuationAfterSharedLinks() {
        assertEquals(
            "http://radio.example.com:8000/live",
            StreamLinkParser.httpUrlFromText("Play (http://radio.example.com:8000/live)."),
        )
    }

    @Test fun extractsEmbeddedMRadioDeepLink() {
        assertEquals(
            "mradio://open?url=https%3A%2F%2Fstream.zeno.fm%2Flive",
            StreamLinkParser.deepLinkFromSharedText(
                "Open in mRadio: mradio://open?url=https%3A%2F%2Fstream.zeno.fm%2Flive",
            ),
        )
    }

    @Test fun ignoresPlainTextWithoutLinks() {
        assertNull(StreamLinkParser.httpUrlFromText("Not a stream"))
        assertNull(StreamLinkParser.deepLinkFromSharedText("Nothing to open"))
    }
}
