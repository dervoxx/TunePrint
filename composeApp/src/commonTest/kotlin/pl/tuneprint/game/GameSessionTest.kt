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
    @Test
    fun customThreeAndEightPlayerRostersCanFinishAGame() {
        for (count in listOf(3, 8)) {
            val participants = listOf(Player("you", "Olek")) +
                (1 until count).map { Player("player$it", "Gracz $it") }
            val game = GameSession(
                listOf(GameTrack("one", "Utwór", "Artysta", setOf("you"))),
                participants = participants,
            )
            assertEquals(participants, game.snapshot.players)
            game.start()
            game.advance(3_000)
            game.answer(setOf("you"))
            game.advance(15_000)
            assertEquals(GamePhase.FINISHED, game.snapshot.phase)
            assertEquals(count, assertNotNull(game.snapshot.result).answers.size)
            assertEquals(count, game.snapshot.ranking.size)
        }
    }

    @Test
    fun customRostersRequireThreeToEightUniquePlayersWithLocalFirst() {
        val track = GameTrack("one", "Utwór", "Artysta", setOf("you"))
        val local = Player("you", "Olek")
        val other = Player("other", "Inny")
        val third = Player("third", "Trzeci")
        for (invalid in listOf(
            listOf(local, other),
            listOf(local, other, other),
            listOf(other, local, third),
            listOf(local) + (1..8).map { Player("player$it", "Gracz $it") },
        )) {
            assertFailsWith<IllegalArgumentException> { GameSession(listOf(track), participants = invalid) }
        }
    }

    private fun grade(owners: Set<String>, selected: Set<String>, elapsedMs: Long = 0): PlayerResult {
        val game = GameSession(listOf(GameTrack("shared", "Wspólny", "Artysta", owners)))
        game.start()
        game.advance(3_000)
        game.advance(elapsedMs)
        game.answer(selected)
        game.advance(15_000)
        return assertNotNull(game.snapshot.result).answers.first()
    }

    @Test
    fun partialCreditDependsOnOwnerCoverageAndWrongSelectionPenalty() {
        val owners = setOf("you", "kacper")
        val half = grade(owners, setOf("you"))
        assertFalse(half.correct)
        assertEquals(0.5, half.accuracy)
        assertEquals(50, half.points)
        assertEquals(0, grade(owners, setOf("you", "kamil")).points)
        assertEquals(0, grade(owners, setOf("you", "kacper", "kamil", "ziobro")).points)
        val twoOfThree = grade(setOf("you", "kacper", "kamil"), setOf("you", "kacper"))
        assertEquals(67, twoOfThree.points)
        assertEquals(2.0 / 3.0, twoOfThree.accuracy)
    }

    @Test
    fun completenessOutweighsSpeedAndAccuracyIsClamped() {
        assertTrue(GameSession.pointsFor(15_000) > GameSession.pointsFor(0, 0.5))
        assertEquals(30, GameSession.pointsFor(15_000, 0.5))
        assertEquals(40, GameSession.pointsFor(15_000, 2.0 / 3.0))
        assertEquals(0, GameSession.pointsFor(0, -1.0))
        assertEquals(100, GameSession.pointsFor(0, 2.0))
    }

    @Test
    fun completeOwnerSetIsCorrectAndPartialSetsEarnReducedPoints() {
        val owners = setOf("you", "kacper")
        for (choice in listOf(owners, setOf("you"), setOf("kacper"), owners + "kamil")) {
            val game = GameSession(listOf(GameTrack("shared", "Wspólny", "Artysta", owners)))
            game.start()
            assertNull(game.snapshot.result)
            game.advance(3_000)
            game.answer(choice)
            assertNull(game.snapshot.result)
            game.advance(15_000)
            val result = assertNotNull(game.snapshot.result)
            assertEquals(owners, result.owners.map { it.id }.toSet())
            assertEquals(choice == owners, result.answers.first().correct)
            assertEquals(if (choice == owners) 100 else 50, result.answers.first().points)
        }
    }

    @Test
    fun draftCanToggleSeveralOwnersAndSubmitOnce() {
        val game = GameSession(listOf(GameTrack("shared", "Wspólny", "Artysta", setOf("you", "kacper"))))
        game.start()
        game.advance(3_000)
        game.toggleOwner("you")
        game.toggleOwner("kamil")
        game.toggleOwner("kamil")
        game.toggleOwner("kacper")
        assertEquals(setOf("you", "kacper"), game.snapshot.selectedOwnerIds)
        assertFalse("you" in game.snapshot.answeredPlayerIds)
        game.advance(2_000)
        game.submitAnswer()
        game.toggleOwner("kacper")
        game.answer(setOf("kamil"))
        assertTrue("you" in game.snapshot.answeredPlayerIds)
        assertEquals(setOf("you", "kacper"), game.snapshot.selectedOwnerIds)
        game.advance(13_000)
        assertEquals(95, assertNotNull(game.snapshot.result).answers.first().points)
    }

    @Test
    fun nonemptyDraftIsAutomaticallySubmittedAtDeadlineFor60Points() {
        val game = GameSession(listOf(GameTrack("shared", "Wspólny", "Artysta", setOf("you", "kacper"))))
        game.start()
        game.advance(3_000)
        game.toggleOwner("you")
        game.toggleOwner("kacper")
        game.advance(15_000)
        val answer = assertNotNull(game.snapshot.result).answers.first()
        assertTrue(answer.correct)
        assertEquals(60, answer.points)
        assertEquals(15_000L, answer.elapsedMs)
        assertEquals(setOf("you", "kacper"), answer.selectedOwnerIds)
    }

    @Test
    fun emptyAndUnknownFinalAnswersAreIgnored() {
        val game = session()
        game.start()
        game.advance(3_000)
        game.submitAnswer()
        game.answer(setOf("unknown"))
        assertFalse("you" in game.snapshot.answeredPlayerIds)
        assertTrue(game.snapshot.selectedOwnerIds.isEmpty())
    }

    @Test
    fun localNicknameIsTrimmedAndDefaultsToOlek() {
        val tracks = listOf(GameTrack("one", "Utwór", "Artysta", setOf("you")))
        assertEquals("Olek", GameSession(tracks).players.first().name)
        assertEquals("Nowy nick", GameSession(tracks, localName = "  Nowy nick  ").players.first().name)
        assertEquals("Olek", GameSession(tracks, localName = "  ").players.first().name)
    }

    @Test
    fun botsSubmitBothCorrectAndIncorrectSetsWhenEveryoneOwnsTrack() {
        val owners = setOf("you", "kacper", "kamil", "ziobro")
        val game = GameSession(listOf(GameTrack("everyone", "Wspólny", "Artysta", owners)))
        game.start()
        game.advance(3_000)
        game.answer(owners)
        game.advance(15_000)
        val answers = assertNotNull(game.snapshot.result).answers
        assertTrue(answers.first().correct)
        assertTrue(answers.drop(1).any { it.correct })
        assertTrue(answers.drop(1).any { !it.correct })
    }

    @Test
    fun gamesCanHaveOneOrNineRoundsWithoutRepeatedTracks() {
        for (count in listOf(1, 9)) {
            val tracks = (1..count).map { GameTrack("track$it", "Utwór $it", "Artysta", setOf("you")) }
            val game = GameSession(tracks)
            game.start()
            val played = mutableSetOf<String>()
            repeat(count) { index ->
                assertEquals(index + 1, game.snapshot.roundNumber)
                assertEquals(count, game.snapshot.totalRounds)
                assertTrue(played.add(assertNotNull(game.snapshot.audioKey)))
                game.advance(3_000)
                game.advance(15_000)
                if (index == count - 1) {
                    assertEquals(GamePhase.FINISHED, game.snapshot.phase)
                } else {
                    assertEquals(GamePhase.RESULTS, game.snapshot.phase)
                    game.advance(30_000)
                }
            }
            assertEquals(GamePhase.FINISHED, game.snapshot.phase)
            assertEquals(count, played.size)
        }
    }

    @Test
    fun tracksRequireKnownOwnersAndUniqueAudioKeys() {
        val valid = GameTrack("track", "Utwór", "Artysta", setOf("you"))
        assertFailsWith<IllegalArgumentException> { GameSession(emptyList()) }
        assertFailsWith<IllegalArgumentException> { GameSession(listOf(valid, valid)) }
        assertFailsWith<IllegalArgumentException> { GameSession(listOf(valid.copy(ownerIds = emptySet()))) }
        assertFailsWith<IllegalArgumentException> { GameSession(listOf(valid.copy(ownerIds = setOf("unknown")))) }
    }

    private fun session() = GameSession(
        listOf(
            GameTrack("one", "Pierwszy", "Artysta", setOf("kacper")),
            GameTrack("two", "Drugi", "Artysta", setOf("kamil")),
            GameTrack("three", "Trzeci", "Artysta", setOf("ziobro")),
        ),
        random = Random(42),
    )

    @Test
    fun everySessionUsesTheSameFixedPlayers() {
        val expected = listOf(
            Player("you", "Olek"),
            Player("kacper", "Kacper"),
            Player("kamil", "Kamil"),
            Player("ziobro", "Ziobro"),
        )
        val first = session()
        first.start()
        first.advance(3_000)
        first.advance(15_000)
        assertEquals(expected, first.snapshot.players)
        assertEquals(expected, session().snapshot.players)
        assertEquals(expected.map { it.id }.toSet(), first.snapshot.ranking.map { it.player.id }.toSet())
    }

    @Test
    fun scoreFallsFrom100To60AndClampsOutsideTheRound() {
        assertEquals(100, GameSession.pointsFor(-1))
        assertEquals(100, GameSession.pointsFor(0))
        assertEquals(80, GameSession.pointsFor(7_500))
        assertEquals(60, GameSession.pointsFor(15_000))
        assertEquals(60, GameSession.pointsFor(20_000))
        val points = (0L..15_000L step 100).map { GameSession.pointsFor(it) }
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
        game.answer(setOf("kacper"))
        assertTrue(game.snapshot.selectedOwnerIds.isEmpty())
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
        game.answer(setOf("kacper"))
        game.answer(setOf("kamil"))
        assertEquals(setOf("kacper"), game.snapshot.selectedOwnerIds)
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
        game.answer(setOf("kacper"))
        val answer = assertNotNull(game.snapshot.result).answers.first()
        assertFalse(answer.correct)
        assertEquals(0, answer.points)
        assertNull(answer.selectedOwnerIds)
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
        assertTrue(game.snapshot.selectedOwnerIds.isEmpty())
        assertTrue(game.snapshot.readyPlayerIds.isEmpty())
        assertTrue(game.snapshot.answeredPlayerIds.isEmpty())
    }

    @Test
    fun lastRoundImmediatelyFinishesWhileEarlierRoundsKeepTheirIntermission() {
        val game = session()
        game.start()
        repeat(3) { round ->
            game.advance(3_000)
            game.answer(setOf(listOf("kacper", "kamil", "ziobro")[round]))
            game.advance(12_000)
            assertEquals(if (round == 2) GamePhase.FINISHED else GamePhase.RESULTS, game.snapshot.phase)
            assertEquals(round + 1, game.snapshot.roundNumber)
            if (round < 2) game.advance(30_000)
        }
        assertEquals(GamePhase.FINISHED, game.snapshot.phase)
        assertEquals(300, game.snapshot.ranking.first { it.player.id == "you" }.points)
        assertEquals(3, game.snapshot.ranking.first { it.player.id == "you" }.correctAnswers)
    }

    @Test
    fun finalResultNeedsNoReadinessOrWaitAndRemainsAvailable() {
        val game = GameSession(listOf(GameTrack("last", "Ostatni", "Artysta", setOf("you"))))
        game.start()
        game.advance(3_000)
        game.answer(setOf("you"))
        game.advance(12_000)
        assertEquals(GamePhase.FINISHED, game.snapshot.phase)
        assertEquals("Ostatni", assertNotNull(game.snapshot.result).title)
        assertEquals(0L, game.snapshot.remainingMs)
        assertTrue(game.snapshot.readyPlayerIds.isEmpty())
        val finalSnapshot = game.snapshot
        game.ready()
        game.advance(30_000)
        assertEquals(finalSnapshot, game.snapshot)
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
