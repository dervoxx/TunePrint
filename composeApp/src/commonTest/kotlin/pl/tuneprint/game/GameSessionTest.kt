package pl.tuneprint.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSessionTest {
    private fun session() = GameSession(
        listOf(
            GameTrack("one", "Pierwszy", "Artysta", "maja"),
            GameTrack("two", "Drugi", "Artysta", "kuba"),
            GameTrack("three", "Trzeci", "Artysta", "ola"),
        ),
        random = Random(42),
    )

    @Test
    fun scoreFallsFrom100To25AndClampsOutsideTheRound() {
        assertEquals(100, GameSession.pointsFor(-1))
        assertEquals(100, GameSession.pointsFor(0))
        assertEquals(63, GameSession.pointsFor(7_500))
        assertEquals(25, GameSession.pointsFor(15_000))
        assertEquals(25, GameSession.pointsFor(20_000))
        val points = (0L..15_000L step 100).map(GameSession::pointsFor)
        assertTrue(points.zipWithNext().all { (first, second) -> first >= second })
    }

    @Test
    fun countdownHidesResultsAndStartsAFullLengthRound() {
        val game = session()
        game.start()
        assertEquals(3, game.snapshot.countdown)
        game.advance(1_000)
        assertEquals(2, game.snapshot.countdown)
        game.advance(1_000)
        assertEquals(1, game.snapshot.countdown)
        game.answer("maja")
        assertNull(game.snapshot.selectedOwnerId)
        game.advance(1_000)
        assertEquals(GamePhase.ROUND, game.snapshot.phase)
        assertEquals(15_000L, game.snapshot.remainingMs)
        assertNull(game.snapshot.result)
    }

    @Test
    fun answerIsImmutableAndDoesNotRevealOrEndOtherPlayersRound() {
        val game = session()
        game.start()
        game.advance(3_000)
        game.answer("maja")
        game.answer("kuba")
        assertEquals("maja", game.snapshot.selectedOwnerId)
        assertEquals(GamePhase.ROUND, game.snapshot.phase)
        assertNull(game.snapshot.result)
        game.advance(12_000)
        assertEquals(GamePhase.RESULTS, game.snapshot.phase)
        val result = assertNotNull(game.snapshot.result)
        assertEquals(100, result.answers.first().points)
        assertTrue(result.answers.drop(1).any { it.correct })
        assertTrue(result.answers.drop(1).any { !it.correct })
    }

    @Test
    fun timeoutCountsMissingAnswerAsZeroAndRejectsLateAnswer() {
        val game = session()
        game.start()
        game.advance(3_000)
        game.advance(15_000)
        game.answer("maja")
        val answer = assertNotNull(game.snapshot.result).answers.first()
        assertFalse(answer.correct)
        assertEquals(0, answer.points)
        assertNull(answer.selectedOwnerId)
    }

    @Test
    fun readyWaitsForBotsThenSkipsIntermissionAndClearsRoundState() {
        val game = session()
        game.start()
        game.advance(3_000)
        game.advance(15_000)
        game.ready()
        assertEquals(GamePhase.RESULTS, game.snapshot.phase)
        game.advance(10_000)
        assertEquals(GamePhase.COUNTDOWN, game.snapshot.phase)
        assertEquals(2, game.snapshot.roundNumber)
        assertNull(game.snapshot.result)
        assertNull(game.snapshot.selectedOwnerId)
        assertTrue(game.snapshot.readyPlayerIds.isEmpty())
        assertTrue(game.snapshot.answeredPlayerIds.isEmpty())
    }

    @Test
    fun allThreeRoundsFinishWithFinalResultsBeforeRanking() {
        val game = session()
        game.start()
        repeat(3) { round ->
            game.advance(3_000)
            game.answer(listOf("maja", "kuba", "ola")[round])
            game.advance(12_000)
            assertEquals(GamePhase.RESULTS, game.snapshot.phase)
            assertEquals(round + 1, game.snapshot.roundNumber)
            game.advance(30_000)
        }
        assertEquals(GamePhase.FINISHED, game.snapshot.phase)
        assertEquals(300, game.snapshot.ranking.first { it.player.id == "you" }.points)
        assertEquals(3, game.snapshot.ranking.first { it.player.id == "you" }.correctAnswers)
    }

    @Test
    fun timeOnlyMovesWhenCallerPassesForegroundTime() {
        val game = session()
        game.start()
        game.advance(3_000)
        game.advance(1_000)
        val beforePause = game.snapshot
        game.advance(0)
        assertEquals(beforePause, game.snapshot)
        assertFailsWith<IllegalArgumentException> { game.advance(-1) }
    }
}
