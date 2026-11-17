package pl.tuneprint.net

import kotlinx.serialization.Serializable

@Serializable
data class ClientCommand(
    val type: String,
    val name: String? = null,
    val code: String? = null,
    val token: String? = null,
    val mode: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val capacity: Int? = null,
    val roundCount: Int? = null,
    val roundNumber: Int? = null,
    val showOwnerCount: Boolean? = null,
    val ownerIds: List<String>? = null,
    val catalogKeys: List<String>? = null,
)

@Serializable
data class ServerEvent(
    val type: String,
    val state: RoomState? = null,
    val rooms: List<RoomListing> = emptyList(),
    val token: String? = null,
    val playerId: String? = null,
    val message: String? = null,
)

@Serializable
data class RoomListing(val code: String, val hostName: String, val roundCount: Int)

@Serializable
data class RoomState(
    val code: String,
    val hostId: String,
    val players: List<NetPlayer>,
    val capacity: Int,
    val roundCount: Int,
    val maxRounds: Int,
    val showOwnerCount: Boolean,
    val mode: String,
    val phase: String,
    val roundNumber: Int,
    val remainingMs: Long,
    val audioKey: String?,
    val ownerCountHint: Int?,
    val answeredIds: List<String>,
    val readyIds: List<String>,
    val ranking: List<NetRank>,
    val result: NetResult?,
)

@Serializable
data class NetPlayer(val id: String, val name: String)

@Serializable
data class NetRank(val playerId: String, val points: Int, val correctAnswers: Int)

@Serializable
data class NetResult(
    val ownerIds: List<String>,
    val title: String,
    val artist: String,
    val answers: List<NetAnswer>,
)

@Serializable
data class NetAnswer(
    val playerId: String,
    val ownerIds: List<String>,
    val text: String?,
    val elapsedMs: Long?,
    val correct: Boolean,
    val accuracy: Double,
    val points: Int,
)
