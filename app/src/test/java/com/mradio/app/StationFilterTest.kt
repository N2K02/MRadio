package com.mradio.app

import org.junit.Assert.assertEquals
import org.junit.Test

class StationFilterTest {
    private val list = listOf(
        Station("1", "Zulu", listOf("https://example.com/z"), tags = "Rock, Dance"),
        Station("2", "Alpha", listOf("https://example.com/a"), tags = "Jazz"),
        Station("3", "Echo", listOf("https://example.com/e"), tags = "rock"),
    )

    @Test fun genreMatchesCaseAndWhitespace() {
        assertEquals(listOf("1", "3"), StationFilter.apply(list, genre = "ROCK").map { it.id })
    }

    @Test fun hideBrokenAndSortWithoutMutatingInput() {
        val filtered = StationFilter.apply(list, workingOnly = true, brokenIds = setOf("1"), sortByName = true)
        assertEquals(listOf("2", "3"), filtered.map { it.id })
        assertEquals("1", list.first().id)
    }
}
