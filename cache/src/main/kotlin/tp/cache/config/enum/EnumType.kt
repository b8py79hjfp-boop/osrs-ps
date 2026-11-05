// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.enum

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/**
 * Enum config, archive 2 group 8: a key -> value map with a default. Exactly one of [intValues], [stringValues] or
 * [longValues] is non-empty (the one matching [outputType]); ids are i32 keys.
 */
class EnumType internal constructor(val id: Int) {
    /** Input (key) script var type as a char key (op 1, e.g. 'i'), or -1. */
    var inputType: Int = -1
        internal set

    /** Output (value) script var type as a char key (op 2), or -1. */
    var outputType: Int = -1
        internal set

    /** Default string (op 3); client default "null". */
    var defaultString: String = "null"
        internal set
    var defaultInt: Int = 0
        internal set
    var defaultLong: Long = 0L
        internal set
    var intValues: Map<Int, Int> = emptyMap()
        internal set
    var stringValues: Map<Int, String> = emptyMap()
        internal set
    var longValues: Map<Int, Long> = emptyMap()
        internal set

    /** Entry count of whichever value map is populated. */
    val size: Int get() = maxOf(intValues.size, stringValues.size, longValues.size)
}

/** Decoder for [EnumType]. Opcode table: RuneLite 1.13.6 `EnumLoader.java` (op 7/8 long values are RuneLite only). */
object EnumTypeDecoder : ConfigDecoder<EnumType> {
    override val name: String = "enum"

    override fun decode(id: Int, buf: ReadBuf): EnumType {
        val t = EnumType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                1 -> {
                    t.inputType = buf.g1()
                }

                2 -> {
                    t.outputType = buf.g1()
                }

                3 -> {
                    t.defaultString = buf.gjstr()
                }

                4 -> {
                    t.defaultInt = buf.g4()
                }

                5 -> {
                    val count = buf.g2()
                    val map = LinkedHashMap<Int, String>(count * 2)
                    repeat(count) { map[buf.g4()] = buf.gjstr() }
                    t.stringValues = map
                }

                6 -> {
                    val count = buf.g2()
                    val map = LinkedHashMap<Int, Int>(count * 2)
                    repeat(count) { map[buf.g4()] = buf.g4() }
                    t.intValues = map
                }

                7 -> {
                    val count = buf.g2()
                    val map = LinkedHashMap<Int, Long>(count * 2)
                    repeat(count) { map[buf.g4()] = buf.g8() }
                    t.longValues = map
                }

                8 -> {
                    t.defaultLong = buf.g8()
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }
}
