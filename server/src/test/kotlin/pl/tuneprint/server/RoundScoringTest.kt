package pl.tuneprint.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoundScoringTest {
    private val players = setOf("a", "b", "c", "d")
    private val track = CatalogTrack("track1", "Łódź — Zażółć gęślą jaźń!", "Bedoes 2115 / Lanek", setOf("a", "b"))
    private val noCredit = Grade(false, 0.0, 0)

    @Test
    fun ownersEarnPartialCreditAfterWrongSelectionPenalty() {
        fun grade(vararg ids: String) = RoundScoring.grade("OWNERS", track, players, ids.toSet(), track.title, 7_500)
        assertEquals(Grade(true, 1.0, 80), grade("a", "b"))
        assertEquals(Grade(false, 0.5, 40), grade("a"))
        assertEquals(Grade(false, 0.5, 40), grade("a", "b", "c"))
        assertEquals(noCredit, grade("a", "c"))
        assertEquals(noCredit, grade("c", "d"))
        assertEquals(noCredit, grade("a", "b", "unknown"))
        assertEquals(noCredit, grade())
    }

    @Test
    fun everyoneCanOwnATrackAndFractionalPointsAreRounded() {
        val everyone = track.copy(ownerIds = players)
        assertEquals(Grade(true, 1.0, 60), RoundScoring.grade("OWNERS", everyone, players, players, null, 15_000))
        val threeOwners = track.copy(ownerIds = players - "d")
        val result = RoundScoring.grade("OWNERS", threeOwners, players, setOf("b", "c"), null, 0)
        assertEquals(Grade(false, 2.0 / 3, 67), result)
    }

    @Test
    fun titleRequiresTheWholeNormalizedTitleAndClampsTheClock() {
        fun grade(text: String?, time: Long) = RoundScoring.grade("TITLE", track, players, track.ownerIds, text, time)
        assertEquals(Grade(true, 1.0, 100), grade("  LODZ,\tZAZOLC   GESLA JAZN ", -1))
        assertEquals(Grade(true, 1.0, 60), grade(track.title, Long.MAX_VALUE))
        for (answer in listOf(null, "Łódź", track.artist, track.title + " live")) {
            assertEquals(noCredit, grade(answer, 0))
        }
    }

    @Test
    fun artistRequiresAllCreditedNamesInOrder() {
        fun grade(text: String) = RoundScoring.grade("ARTIST", track, players, emptySet(), text, 7_500)
        assertEquals(Grade(true, 1.0, 80), grade("BEDOES 2115, LANEK"))
        assertEquals(noCredit, grade("Bedoes 2115"))
        assertEquals(noCredit, grade("Lanek / Bedoes 2115"))
        assertEquals(noCredit, grade(track.title))
    }

    @Test
    fun missingMetadataExcludesOnlyTheAffectedMode() {
        for (missing in listOf("", " \t ", "?!", "\u0301")) {
            val noArtist = track.copy(artist = missing)
            assertFalse(RoundScoring.isEligible("ARTIST", noArtist, players))
            assertTrue(RoundScoring.isEligible("TITLE", noArtist, players))
            assertTrue(RoundScoring.isEligible("OWNERS", noArtist, players))
            assertEquals(noCredit, RoundScoring.grade("ARTIST", noArtist, players, emptySet(), missing, 0))

            val noTitle = track.copy(title = missing)
            assertFalse(RoundScoring.isEligible("TITLE", noTitle, players))
            assertTrue(RoundScoring.isEligible("ARTIST", noTitle, players))
            assertEquals(noCredit, RoundScoring.grade("TITLE", noTitle, players, emptySet(), "", 0))
        }
    }

    @Test
    fun invalidOwnerPoolsAndUnknownModesCannotEarnCredit() {
        for (owners in listOf(emptySet(), setOf("unknown"))) {
            val invalid = track.copy(ownerIds = owners)
            assertFalse(RoundScoring.isEligible("OWNERS", invalid, players))
            assertEquals(noCredit, RoundScoring.grade("OWNERS", invalid, players, owners, null, 0))
        }
        assertFalse(RoundScoring.isEligible("OTHER", track, players))
        assertEquals(noCredit, RoundScoring.grade("OTHER", track, players, track.ownerIds, track.title, 0))
    }
}
