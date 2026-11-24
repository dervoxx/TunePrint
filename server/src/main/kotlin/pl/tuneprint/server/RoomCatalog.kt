package pl.tuneprint.server

import pl.tuneprint.net.RankingPeriod

data class CatalogTrack(val key: String, val title: String, val artist: String, val ownerIds: Set<String>, val previewUrl: String = "")

internal fun roomCatalog(seats: Collection<Seat>, period: RankingPeriod = RankingPeriod.MEDIUM): List<CatalogTrack> {
    val recordings = linkedMapOf<String, Recording>()
    val owners = mutableMapOf<String, MutableSet<String>>()
    for (seat in seats) {
        for (recording in seat.catalog) {
            if (period !in recording.periods) continue
            recordings.putIfAbsent(recording.key, recording)
            owners.getOrPut(recording.key) { mutableSetOf() }.add(seat.player.id)
        }
    }
    return recordings.values.map { CatalogTrack(it.key, it.title, it.artist, owners.getValue(it.key), it.previewUrl) }
}
