package pl.tuneprint.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DemoCatalogTest {
    @Test
    fun exposesTenStableKeys() {
        val expected = (1..10).map { "demo-" + it.toString().padStart(2, '0') }
        assertEquals(expected.toSet(), DemoCatalog.keys)
    }

    @Test
    fun mergesOnlySelectionsInThisPool() {
        val pool = DemoCatalog.pool(
            mapOf(
                "alice" to setOf("demo-10", "demo-02"),
                "bob" to setOf("demo-10"),
                "charlie" to emptySet(),
            ),
        )
        assertEquals(
            listOf(
                CatalogTrack("demo-02", "Fiji", "Taco Hemingway", setOf("alice")),
                CatalogTrack("demo-10", "Halo halo z aresztu", "", setOf("alice", "bob")),
            ),
            pool,
        )
        assertEquals(setOf("bob"), DemoCatalog.pool(mapOf("bob" to setOf("demo-10"))).single().ownerIds)
    }

    @Test
    fun emptySelectionsYieldNoTracks() {
        assertEquals(emptyList(), DemoCatalog.pool(emptyMap()))
        assertEquals(emptyList(), DemoCatalog.pool(mapOf("alice" to emptySet())))
    }

    @Test
    fun rejectsUnknownKeysAlongsideValidOnes() {
        assertFailsWith<IllegalArgumentException> {
            DemoCatalog.pool(mapOf("alice" to setOf("demo-01"), "bob" to setOf("demo-11")))
        }
    }
}
