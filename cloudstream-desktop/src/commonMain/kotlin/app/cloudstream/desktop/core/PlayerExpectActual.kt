/**
 * expect/actual declaration for the Player abstraction.
 * 
 * The expect block declares the Player contract that all platforms must implement.
 * The actual block for the desktop JVM is provided in core/Player.kt (the existing
 * Player object that delegates to external players like mpv/VLC or the default browser).
 * 
 * Other platforms (e.g., a future iOS port) would provide their own actual implementation
 * by supplying their own Player code that implements the same open() contract.
 */
expect object Player {

    /** Open a stream link using the configured player command or open in browser. */
    fun open(link: app.cloudstream.desktop.api.StreamLink, command: String): String
}