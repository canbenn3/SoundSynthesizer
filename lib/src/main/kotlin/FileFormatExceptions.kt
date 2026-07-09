package synthesizer

/** Base exception for all song-file parsing and format errors in the Synthesizer library. */
open class IllegalFileFormatException(message: String) : IllegalArgumentException(message)

/** Thrown when a song file path does not exist or cannot be read. */
class SongFileNotFoundException(message: String) : IllegalFileFormatException(message)

/** Thrown when the first line of a song file is missing or malformed. */
class ImproperFileHeaderException(message: String) : IllegalFileFormatException(message)

/** Thrown when a channel line header is missing or malformed. */
class ImproperChannelHeaderException(message: String) : IllegalFileFormatException(message)

/** Thrown when note or measure text cannot be parsed or validated. */
class ImproperNoteException(message: String) : IllegalFileFormatException(message)
