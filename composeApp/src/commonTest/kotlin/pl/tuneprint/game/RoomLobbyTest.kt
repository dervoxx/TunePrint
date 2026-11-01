package pl.tuneprint.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomLobbyTest {
    @Test
    fun hostCapacityIsClampedAndCannotBeChangedOutsideLobby() {
        val room = room()
        assertEquals(4, room.snapshot.capacity)
        room.setCapacity(8)
        assertEquals(4, room.snapshot.capacity)
        room.create()
        room.setCapacity(0)
        assertEquals(3, room.snapshot.capacity)
        room.setCapacity(99)
        assertEquals(8, room.snapshot.capacity)
        room.advance(1_800)
        assertTrue(room.snapshot.canStart)
        room.start()
        room.setCapacity(3)
        assertEquals(8, room.snapshot.capacity)
        room.leave()
        assertEquals(4, room.snapshot.capacity)
    }

    @Test
    fun threePlayersCanStartWithoutFillingFourthOrEighthSeat() {
        for (capacity in listOf(4, 8)) {
            val room = room()
            room.create()
            room.setCapacity(capacity)
            room.advance(900)
            assertFalse(room.snapshot.canStart)
            room.advance(900)
            assertEquals(players.take(3), room.snapshot.players)
            assertTrue(room.snapshot.canStart)
            room.start()
            assertEquals(RoomRoute.GAME, room.snapshot.route)
            assertEquals(3, room.snapshot.players.size)
        }
    }

    @Test
    fun capacityClipsRosterAndCanRevealTheFourthPlayerAgain() {
        val room = room()
        room.create()
        room.setCapacity(3)
        room.advance(3_000)
        assertEquals(players.take(3), room.snapshot.players)
        room.setCapacity(4)
        assertEquals(players, room.snapshot.players)
        room.setCapacity(8)
        assertEquals(players, room.snapshot.players)
    }

    @Test
    fun guestsCannotChangeCapacity() {
        val room = room()
        room.openJoin()
        room.editCode("111222")
        room.join()
        room.setCapacity(8)
        assertEquals(4, room.snapshot.capacity)
        assertEquals(players, room.snapshot.players)
    }

    @Test
    fun roomsAcceptThreeAndEightPlayerRostersWithoutInventingPlayers() {
        val three = RoomLobby(players.take(3))
        three.create()
        three.advance(3_000)
        assertEquals(players.take(3), three.snapshot.players)
        assertTrue(three.snapshot.canStart)
        val eightPlayers = players + (5..8).map { Player("player$it", "Gracz $it") }
        val eight = RoomLobby(eightPlayers)
        eight.create()
        eight.setCapacity(8)
        eight.advance(8_000)
        assertEquals(eightPlayers, eight.snapshot.players)
        assertTrue(eight.snapshot.canStart)
        assertFailsWith<IllegalArgumentException> { RoomLobby(players.take(2)) }
    }

    @Test
    fun availableRoomsCanBeSelectedAndUnknownCodeShowsError() {
        val room = room()
        room.openJoin()
        assertEquals(listOf("Kacper", "Kamil", "Ziobro"), room.snapshot.availableRooms.map { it.hostName })
        room.editCode("999999")
        room.join()
        assertEquals(RoomRoute.JOIN, room.snapshot.route)
        assertEquals("Nie znaleziono pokoju", room.snapshot.joinError)
        val selected = room.snapshot.availableRooms.last()
        room.editCode(selected.code)
        assertEquals(null, room.snapshot.joinError)
        room.join()
        assertEquals(RoomRoute.LOBBY, room.snapshot.route)
        assertEquals(selected.roundCount, room.snapshot.roundCount)
        assertFalse(room.snapshot.isHost)
    }

    @Test
    fun roomListingsRespectAvailableTrackCount() {
        val room = RoomLobby(players, maxRounds = 2)
        assertTrue(room.snapshot.availableRooms.all { it.roundCount == 2 })
        room.create()
        assertFalse(room.snapshot.availableRooms.any { it.code == room.snapshot.code })
    }

    @Test
    fun hostCanChangeRoundCountOnlyInLobbyAndValuesAreClamped() {
        val room = room()
        assertEquals(3, room.snapshot.roundCount)
        assertEquals(10, room.snapshot.maxRounds)
        room.setRoundCount(8)
        assertEquals(3, room.snapshot.roundCount)
        room.create()
        assertTrue(room.snapshot.isHost)
        room.setRoundCount(0)
        assertEquals(1, room.snapshot.roundCount)
        room.setRoundCount(50)
        assertEquals(10, room.snapshot.roundCount)
        assertFalse(room.snapshot.canStart)
        room.advance(3_000)
        assertTrue(room.snapshot.canStart)
        room.setRoundCount(7)
        room.start()
        room.setRoundCount(2)
        assertEquals(7, room.snapshot.roundCount)
        room.replay()
        assertEquals(7, room.snapshot.roundCount)
        room.leave()
        assertFalse(room.snapshot.isHost)
        assertEquals(3, room.snapshot.roundCount)
    }

    @Test
    fun guestsCannotChangeRoundCount() {
        val room = room()
        room.openJoin()
        room.editCode("111222")
        room.join()
        assertFalse(room.snapshot.isHost)
        room.setRoundCount(9)
        assertEquals(3, room.snapshot.roundCount)
        assertTrue(room.snapshot.canStart)
    }

    @Test
    fun defaultRoundCountRespectsAvailableAudio() {
        val room = RoomLobby(players, maxRounds = 1)
        room.create()
        assertEquals(1, room.snapshot.roundCount)
        room.setRoundCount(9)
        assertEquals(1, room.snapshot.roundCount)
        assertFailsWith<IllegalArgumentException> { RoomLobby(players, maxRounds = 0) }
    }

    private val players = listOf(
        Player("you", "Olek"),
        Player("kacper", "Kacper"),
        Player("kamil", "Kamil"),
        Player("ziobro", "Ziobro"),
    )

    private fun room() = RoomLobby(players, Random(42))

    @Test
    fun creatingRoomGeneratesSixDigitsAndAllowsStartFromThreePlayers() {
        val room = room()
        room.create()
        assertEquals(RoomRoute.LOBBY, room.snapshot.route)
        assertTrue(room.snapshot.code.matches(Regex("[0-9]{6}")))
        assertEquals(players.take(1), room.snapshot.players)
        assertFalse(room.snapshot.canStart)
        room.start()
        assertEquals(RoomRoute.LOBBY, room.snapshot.route)
        room.advance(899)
        assertEquals(players.take(1), room.snapshot.players)
        room.advance(1)
        assertEquals(players.take(2), room.snapshot.players)
        room.advance(900)
        assertEquals(players.take(3), room.snapshot.players)
        assertTrue(room.snapshot.canStart)
        room.advance(1_200)
        assertEquals(players, room.snapshot.players)
        assertTrue(room.snapshot.canStart)
        room.start()
        assertEquals(RoomRoute.GAME, room.snapshot.route)
        assertFalse(room.snapshot.canStart)
    }

    @Test
    fun joiningRequiresSixAsciiDigitsAndKeepsEnteredCode() {
        val room = room()
        room.openJoin()
        room.editCode("12 ab3-4")
        assertEquals("1234", room.snapshot.code)
        room.join()
        assertEquals(RoomRoute.JOIN, room.snapshot.route)
        room.editCode("333444567")
        assertEquals("333444", room.snapshot.code)
        room.join()
        assertEquals(RoomRoute.LOBBY, room.snapshot.route)
        assertEquals("333444", room.snapshot.code)
        assertEquals(5, room.snapshot.roundCount)
        assertEquals(players, room.snapshot.players)
        assertTrue(room.snapshot.canStart)
    }

    @Test
    fun leavingClearsRoomAndCancelsPendingArrivals() {
        val room = room()
        room.create()
        room.advance(900)
        room.leave()
        room.advance(10_000)
        assertEquals(RoomRoute.HOME, room.snapshot.route)
        assertEquals("", room.snapshot.code)
        assertTrue(room.snapshot.players.isEmpty())
        assertFalse(room.snapshot.canStart)
        room.create()
        assertEquals(players.take(1), room.snapshot.players)
        room.advance(899)
        assertEquals(players.take(1), room.snapshot.players)
    }

    @Test
    fun replayKeepsRoomCodeAndPlayersWithoutCreatingNewRoom() {
        val room = room()
        room.create()
        room.advance(3_000)
        val code = room.snapshot.code
        room.start()
        room.advance(30_000)
        room.replay()
        assertEquals(RoomRoute.LOBBY, room.snapshot.route)
        assertEquals(code, room.snapshot.code)
        assertEquals(players, room.snapshot.players)
        assertTrue(room.snapshot.canStart)
    }

    @Test
    fun codeEditsAndCreateDoNotReplaceAnActiveRoom() {
        val room = room()
        room.create()
        val existing = room.snapshot
        room.editCode("999999")
        room.create()
        room.openJoin()
        room.join()
        assertEquals(existing, room.snapshot)
        room.advance(0)
        assertEquals(existing, room.snapshot)
        assertFailsWith<IllegalArgumentException> { room.advance(-1) }
    }
}
