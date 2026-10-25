package com.mradio.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedStreamIdentityTest {
    private val first = Station(
        id = "saved:one",
        name = "Test Radio",
        streams = listOf("https://stream.example.org/live", "https://backup.example.org/live?key=abc"),
    )
    private val second = Station(
        id = "saved:two",
        name = "Another Radio",
        streams = listOf("https://other.example.org/live"),
    )

    @Test fun existingFavouriteIsFoundByPrimaryOrFallbackStream() {
        assertEquals(first, SharedStreamIdentity.match(listOf(first, second), "https://stream.example.org/live"))
        assertEquals(first, SharedStreamIdentity.match(listOf(first), "https://backup.example.org/live?key=abc"))
    }

    @Test fun sameStreamUsingCasedHostAndDefaultPortIsNotDuplicated() {
        assertTrue(SharedStreamIdentity.matches(
            "HTTPS://STREAM.EXAMPLE.ORG:443/live#player",
            "https://stream.example.org/live"
        ))
    }

    @Test fun differentTokensAndPathsAreNotMistakenForFavourites() {
        assertFalse(SharedStreamIdentity.matches(
            "https://backup.example.org/live?key=abc",
            "https://backup.example.org/live?key=xyz"
        ))
        assertNull(SharedStreamIdentity.match(listOf(first), "https://stream.example.org/another"))
    }

    @Test fun invalidAndCredentialBearingLinksAreNotMatches() {
        assertNull(SharedStreamIdentity.normalized("javascript:alert(1)"))
        assertNull(SharedStreamIdentity.normalized("https://user:pass@stream.example.org/live"))
        assertNull(SharedStreamIdentity.match(listOf(first), "not a URL"))
    }

    @Test fun onlyFavouritesAreConsideredWhenMatchingSavedStation() {
        val favourites = listOf(first)
        assertNull(SharedStreamIdentity.match(favourites, second.streams.single()))
        assertEquals(second, SharedStreamIdentity.match(listOf(first, second), second.streams.single()))
    }
}
