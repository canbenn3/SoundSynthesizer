package synthesizer

// File used for ad-hoc testing :)
fun main(args: Array<String>) {
    val filename = args.firstOrNull() ?: "songs_and_recordings/songs/twinkle_twinkle.txt"
    Synthesizer().playFile(filename)
}
