package pl.tuneprint.server

import pl.tuneprint.catalog.DemoTracks

data class CatalogTrack(val key: String, val title: String, val artist: String, val ownerIds: Set<String>)

object DemoCatalog {
    val keys: Set<String> = DemoTracks.byKey.keys

    fun pool(catalogs: Map<String, Set<String>>): List<CatalogTrack> {
        val ownersByKey = mutableMapOf<String, MutableSet<String>>()
        for ((playerId, selection) in catalogs) {
            for (key in selection) {
                require(key in keys) { "Unknown demo track: $key" }
                ownersByKey.getOrPut(key) { mutableSetOf() }.add(playerId)
            }
        }
        return DemoTracks.byKey.mapNotNull { (key, track) ->
            ownersByKey[key]?.let { owners -> CatalogTrack(key, track.title, track.artist, owners) }
        }
    }
}
