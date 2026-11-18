package pl.tuneprint.audio

import pl.tuneprint.catalog.DemoTracks

data class AudioTrack(
    val assetName: String,
    val title: String,
    val ownerIds: Set<String>,
    val startAtMs: Int = 30_000,
    val artist: String = "",
)

object DemoAudio {
    val byKey: Map<String, AudioTrack> = mapOf(
        track("demo-01", "14 - TACO HEMINGWAY - FRASCATI.mp3", setOf("kacper", "kamil")),
        track("demo-02", "06 Fiji.mp3", setOf("kamil")),
        track("demo-03", "6. Madagaskar.mp3", setOf("ziobro", "you")),
        track("demo-04", "11 - TACO HEMINGWAY - ZAKOCHALEM SIE POD APTEKA.mp3", setOf("you", "kacper", "kamil")),
        track("demo-05", "17. FPS.mp3", setOf("kacper")),
        track("demo-06", "3. Opowieści z Doliny Smoków.mp3", setOf("kamil", "ziobro")),
        track("demo-07", "4. Brzydkie Rzeczy.mp3", setOf("you", "kacper", "ziobro")),
        track("demo-08", "7. NIENAWIDZĘ WARSZAWY.mp3", setOf("ziobro")),
        track("demo-09", "dadada.mp3", setOf("you", "kamil")),
        track("demo-10", "Halo halo z aresztu funny.webm", setOf("ziobro"), startAtMs = 0),
    )

    private fun track(
        key: String,
        assetName: String,
        offlineOwners: Set<String>,
        startAtMs: Int = 30_000,
    ): Pair<String, AudioTrack> {
        val metadata = DemoTracks.byKey.getValue(key)
        return key to AudioTrack(assetName, metadata.title, offlineOwners, startAtMs, metadata.artist)
    }
}

object RoundTracks {
    val all = DemoAudio.byKey.values.toList()
}
