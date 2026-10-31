package pl.tuneprint.audio

data class AudioTrack(
    val assetName: String,
    val title: String,
    val ownerId: String,
    val startAtMs: Int = 30_000,
    val artist: String = "",
)

object RoundTracks {
    val all = listOf(
        AudioTrack("14 - TACO HEMINGWAY - FRASCATI.mp3", "Frascati", "kacper", artist = "Taco Hemingway"),
        AudioTrack("06 Fiji.mp3", "Fiji", "kamil", artist = "Taco Hemingway"),
        AudioTrack("6. Madagaskar.mp3", "Madagaskar", "ziobro", artist = "Quebonafide"),
    )
}
