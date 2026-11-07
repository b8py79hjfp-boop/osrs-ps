package tp.tools.cachepack

/**
 * The TOML subset the pack sources use: `[[table]]` / `[table]` headers, `key = value` with basic and literal strings, integers
 * (decimal, `0x` hex, `_` separators), booleans and arrays (single or multi line) of those, plus `#` comments. Anything else
 * (inline tables, dates, floats, dotted keys) is rejected with the line number rather than guessed at.
 *
 * Why not a library: the formats are our own, small and flat, and the only TOML dependency in the catalog would need the
 * kotlinx.serialization compiler plugin for a generic tree.
 */
internal object MiniToml {
    /** One `[[name]]` / `[name]` section, or the values before the first header (name `""`). */
    class Section(val name: String, val line: Int, val values: Map<String, Any>)

    class TomlException(message: String) : RuntimeException(message)

    fun parse(text: String, source: String): List<Section> {
        val sections = ArrayList<Section>()
        var name = ""
        var startLine = 1
        var values = LinkedHashMap<String, Any>()
        val lines = text.lines()
        var i = 0

        fun flush() {
            if (name.isNotEmpty() || values.isNotEmpty()) sections += Section(name, startLine, values)
        }
        while (i < lines.size) {
            val raw = stripComment(lines[i]).trim()
            i++
            if (raw.isEmpty()) continue
            if (raw.startsWith("[")) {
                flush()
                name =
                    raw
                        .removePrefix("[[")
                        .removePrefix("[")
                        .removeSuffix("]]")
                        .removeSuffix("]")
                        .trim()
                startLine = i
                values = LinkedHashMap()
                continue
            }
            val eq = raw.indexOf('=')
            if (eq <= 0) throw TomlException("$source:$i: expected `key = value`, got `$raw`")
            val key = raw.substring(0, eq).trim()
            var valueText = raw.substring(eq + 1).trim()
            // Arrays may continue over several lines until the brackets balance.
            while (valueText.startsWith("[") && !balanced(valueText)) {
                if (i >= lines.size) throw TomlException("$source:$i: unterminated array")
                valueText += " " + stripComment(lines[i]).trim()
                i++
            }
            if (values.containsKey(key)) throw TomlException("$source:$i: duplicate key `$key`")
            values[key] = Reader(valueText, "$source:$i").value()
        }
        flush()
        return sections
    }

    private fun balanced(s: String): Boolean {
        var depth = 0
        var quote = 0.toChar()
        var escaped = false
        for (c in s) {
            if (quote != 0.toChar()) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\' && quote == '"') {
                    escaped = true
                } else if (c == quote) {
                    quote = 0.toChar()
                }
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '[') {
                depth++
            } else if (c == ']') {
                depth--
            }
        }
        return depth == 0
    }

    /** Removes a trailing `# comment` that is not inside a string. */
    private fun stripComment(line: String): String {
        var quote = 0.toChar()
        var escaped = false
        for ((i, c) in line.withIndex()) {
            if (quote != 0.toChar()) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\' && quote == '"') {
                    escaped = true
                } else if (c == quote) {
                    quote = 0.toChar()
                }
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '#') {
                return line.substring(0, i)
            }
        }
        return line
    }

    private class Reader(private val s: String, private val where: String) {
        private var pos = 0

        fun value(): Any {
            val v = readValue()
            skipSpace()
            if (pos != s.length) throw TomlException("$where: unexpected `${s.substring(pos)}`")
            return v
        }

        private fun skipSpace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun readValue(): Any {
            skipSpace()
            if (pos >= s.length) throw TomlException("$where: missing value")
            return when (s[pos]) {
                '"' -> readBasicString()
                '\'' -> readLiteralString()
                '[' -> readArray()
                else -> readScalar()
            }
        }

        private fun readArray(): List<Any> {
            pos++
            val out = ArrayList<Any>()
            while (true) {
                skipSpace()
                if (pos >= s.length) throw TomlException("$where: unterminated array")
                if (s[pos] == ']') {
                    pos++
                    return out
                }
                out += readValue()
                skipSpace()
                if (pos < s.length && s[pos] == ',') pos++
            }
        }

        private fun readLiteralString(): String {
            val end = s.indexOf('\'', pos + 1)
            if (end < 0) throw TomlException("$where: unterminated string")
            val v = s.substring(pos + 1, end)
            pos = end + 1
            return v
        }

        private fun readBasicString(): String {
            val sb = StringBuilder()
            pos++
            while (true) {
                if (pos >= s.length) throw TomlException("$where: unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> sb.append(readEscape())
                    else -> sb.append(c)
                }
            }
        }

        private fun readEscape(): Char {
            val e = s.getOrNull(pos++) ?: throw TomlException("$where: unterminated escape")
            return when (e) {
                'n' -> {
                    '\n'
                }

                't' -> {
                    '\t'
                }

                'r' -> {
                    '\r'
                }

                '"' -> {
                    '"'
                }

                '\\' -> {
                    '\\'
                }

                'u' -> {
                    val hex = s.substring(pos, minOf(pos + UNICODE_DIGITS, s.length))
                    pos += UNICODE_DIGITS
                    hex.toIntOrNull(HEX)?.toChar() ?: throw TomlException("$where: bad \\u escape")
                }

                else -> {
                    throw TomlException("$where: unsupported escape \\$e")
                }
            }
        }

        private fun endsScalar(c: Char): Boolean = c == ',' || c == ']' || c.isWhitespace()

        private fun readScalar(): Any {
            val start = pos
            while (pos < s.length && !endsScalar(s[pos])) pos++
            val token = s.substring(start, pos)
            return when {
                token == "true" -> true
                token == "false" -> false
                token.startsWith("0x") || token.startsWith("-0x") -> parseHex(token)
                else -> token.replace("_", "").toIntOrNull() ?: throw TomlException("$where: unsupported value `$token`")
            }
        }

        private fun parseHex(token: String): Int {
            val negative = token.startsWith("-")
            val digits = token.removePrefix("-").removePrefix("0x").replace("_", "")
            val v = digits.toLongOrNull(HEX) ?: throw TomlException("$where: bad hex `$token`")
            return (if (negative) -v else v).toInt()
        }
    }

    private const val HEX = 16
    private const val UNICODE_DIGITS = 4
}
