package pl.tuneprint.server

import java.text.Normalizer
import kotlin.math.roundToInt

data class Grade(val correct: Boolean, val accuracy: Double, val points: Int)

object RoundScoring {
    private const val ROUND_MS = 15_000L
    private val noCredit = Grade(false, 0.0, 0)
    private val combiningMarks = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    fun isEligible(mode: String, track: CatalogTrack, players: Set<String>): Boolean =
        when (mode) {
            "OWNERS" -> track.ownerIds.isNotEmpty() && players.containsAll(track.ownerIds)
            "TITLE" -> normalize(track.title).isNotEmpty()
            "ARTIST" -> normalize(track.artist).isNotEmpty()
            else -> false
        }

    fun grade(
        mode: String,
        track: CatalogTrack,
        players: Set<String>,
        ownerIds: Set<String>,
        text: String?,
        elapsedMs: Long,
    ): Grade {
        val accuracy = when (mode) {
            "OWNERS" -> {
                if (!isEligible(mode, track, players) || !players.containsAll(ownerIds)) return noCredit
                val hits = ownerIds.count { it in track.ownerIds }
                val nonOwners = players.size - track.ownerIds.size
                val penalty = if (nonOwners == 0) 0.0 else (ownerIds.size - hits).toDouble() / nonOwners
                (hits.toDouble() / track.ownerIds.size - penalty).coerceIn(0.0, 1.0)
            }
            "TITLE", "ARTIST" -> {
                val expected = normalize(if (mode == "TITLE") track.title else track.artist)
                if (expected.isEmpty() || expected != normalize(text.orEmpty())) return noCredit
                1.0
            }
            else -> return noCredit
        }
        val timeFraction = elapsedMs.coerceIn(0, ROUND_MS).toDouble() / ROUND_MS
        val points = (accuracy * (60 + 40 * (1 - timeFraction))).roundToInt()
        return Grade(accuracy == 1.0, accuracy, points)
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace('ł', 'l')
            .replace("'", "")
            .replace("’", "")
            .replace(separators, " ")
            .trim()
}
