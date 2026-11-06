package tp.client.launcher

/**
 * A small strict JSON reader for the manifest (objects, arrays, strings, numbers, booleans, null). The launcher ships
 * on a minimal runtime with no libraries, and the manifest is only trusted after its signature verifies, so a tiny
 * parser is enough.
 */
internal object MiniJson {
    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.ws()
        require(p.i == text.length) { "trailing data at ${p.i}" }
        return v
    }

    fun quote(s: String): String =
        buildString {
            append('"')
            for (c in s) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c < ' ' -> append(String.format("\\u%04x", c.code))
                    else -> append(c)
                }
            }
            append('"')
        }

    private class Parser(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            ws()
            require(i < s.length) { "unexpected end" }
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> num()
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)) { "bad literal at $i" }
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (s[i] == '}') return m.also { i++ }
            while (true) {
                ws()
                val k = str()
                ws()
                require(s[i] == ':') { "expected : at $i" }
                i++
                m[k] = value()
                ws()
                if (s[i] == ',') {
                    i++
                    continue
                }
                require(s[i] == '}') { "expected } at $i" }
                i++
                return m
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            ws()
            if (s[i] == ']') return l.also { i++ }
            while (true) {
                l += value()
                ws()
                if (s[i] == ',') {
                    i++
                    continue
                }
                require(s[i] == ']') { "expected ] at $i" }
                i++
                return l
            }
        }

        private fun str(): String {
            require(s[i] == '"') { "expected string at $i" }
            i++
            val b = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> {
                        return b.toString()
                    }

                    '\\' -> {
                        when (val e = s[i++]) {
                            'n' -> {
                                b.append('\n')
                            }

                            't' -> {
                                b.append('\t')
                            }

                            'r' -> {
                                b.append('\r')
                            }

                            'b' -> {
                                b.append('\b')
                            }

                            'f' -> {
                                b.append('\u000c')
                            }

                            'u' -> {
                                b.append(s.substring(i, i + HEX_DIGITS).toInt(HEX).toChar())
                                i += HEX_DIGITS
                            }

                            else -> {
                                b.append(e)
                            }
                        }
                    }

                    else -> {
                        b.append(c)
                    }
                }
            }
        }

        private fun num(): Number {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(start, i)
            return if (t.any { it in ".eE" }) t.toDouble() else t.toLong()
        }
    }

    private const val HEX = 16
    private const val HEX_DIGITS = 4
}
