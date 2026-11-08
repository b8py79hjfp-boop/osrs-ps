package tp.util

/** OSRS text tags in user-typed text are shown, never interpreted (commands brief §8: no fake crowns, colours or line breaks). */
object Tags {
    fun escape(text: String): String =
        buildString(text.length) {
            for (c in text) {
                when (c) {
                    '<' -> append("<lt>")
                    '>' -> append("<gt>")
                    else -> append(c)
                }
            }
        }
}
