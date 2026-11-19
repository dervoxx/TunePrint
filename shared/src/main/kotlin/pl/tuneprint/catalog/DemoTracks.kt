package pl.tuneprint.catalog

data class DemoTrack(val title: String, val artist: String = "")

object DemoTracks {
    val byKey = mapOf(
        "demo-01" to DemoTrack("Frascati", "Taco Hemingway"),
        "demo-02" to DemoTrack("Fiji", "Taco Hemingway"),
        "demo-03" to DemoTrack("Madagaskar", "Quebonafide"),
        "demo-04" to DemoTrack("Zakochałem się pod apteką", "Taco Hemingway"),
        "demo-05" to DemoTrack("FPS", "Bedoes 2115 / Lanek"),
        "demo-06" to DemoTrack("Opowieści z Doliny Smoków", "Bedoes 2115 / Lanek"),
        "demo-07" to DemoTrack("Brzydkie Rzeczy", "Bedoes 2115 / Lanek / DJ Johny"),
        "demo-08" to DemoTrack("Nienawidzę Warszawy", "Otsochodzi / lohleq"),
        "demo-09" to DemoTrack("da da da(żyje dla rapu)", "spazma"),
        "demo-10" to DemoTrack("Halo halo z aresztu"),
    )
}
