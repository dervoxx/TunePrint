package pl.tuneprint.audio

data class AudioTrack(
    val assetName: String,
    val title: String,
    val ownerIds: Set<String>,
    val startAtMs: Int = 30_000,
    val artist: String = "",
)

object RoundTracks {
    val all = listOf(
        AudioTrack(
            "14 - TACO HEMINGWAY - FRASCATI.mp3", "Frascati", setOf("kacper", "kamil"),
            artist = "Taco Hemingway",
        ),
        AudioTrack("06 Fiji.mp3", "Fiji", setOf("kamil"), artist = "Taco Hemingway"),
        AudioTrack("6. Madagaskar.mp3", "Madagaskar", setOf("ziobro", "you"), artist = "Quebonafide"),
        AudioTrack(
            "11 - TACO HEMINGWAY - ZAKOCHALEM SIE POD APTEKA.mp3", "Zakochałem się pod apteką",
            setOf("you", "kacper", "kamil"), artist = "Taco Hemingway",
        ),
        AudioTrack("17. FPS.mp3", "FPS", setOf("kacper"), artist = "Bedoes 2115 / Lanek"),
        AudioTrack(
            "3. Opowieści z Doliny Smoków.mp3", "Opowieści z Doliny Smoków", setOf("kamil", "ziobro"),
            artist = "Bedoes 2115 / Lanek",
        ),
        AudioTrack(
            "4. Brzydkie Rzeczy.mp3", "Brzydkie Rzeczy", setOf("you", "kacper", "ziobro"),
            artist = "Bedoes 2115 / Lanek / DJ Johny",
        ),
        AudioTrack(
            "7. NIENAWIDZĘ WARSZAWY.mp3", "Nienawidzę Warszawy", setOf("ziobro"),
            artist = "Otsochodzi / lohleq",
        ),
        AudioTrack("dadada.mp3", "da da da(żyje dla rapu)", setOf("you", "kamil"), artist = "spazma"),
        AudioTrack("Halo halo z aresztu funny.webm", "Halo halo z aresztu", setOf("ziobro"), startAtMs = 0),
    )
}
