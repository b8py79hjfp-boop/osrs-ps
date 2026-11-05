// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.param

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/** Param definition, archive 2 group 11: the type and default of a param key used in [tp.cache.config.ParamMap]. */
class ParamType internal constructor(val id: Int) {
    /** Script var type as a char key (op 1, e.g. 'i' int, 's' string), or -1 when unset. */
    var typeChar: Int = -1
        internal set

    /** Script var type as a numeric id (op 8, RuneLite `ScriptVarType.forId`), or -1 when unset. */
    var typeId: Int = -1
        internal set
    var defaultInt: Int = 0
        internal set
    var defaultLong: Long = 0L
        internal set
    var defaultString: String? = null
        internal set

    /** False when op 4 is present (RuneLite `members=false`, RS Mod `autoDisable=false`). */
    var autoDisable: Boolean = true
        internal set
}

/** Decoder for [ParamType]. Opcode table: RuneLite 1.13.6 `ParamLoader.java`; RS Mod `ParamTypeDecoder` agrees on 1/2/4/5. */
object ParamTypeDecoder : ConfigDecoder<ParamType> {
    override val name: String = "param"

    override fun decode(id: Int, buf: ReadBuf): ParamType {
        val t = ParamType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                1 -> {
                    t.typeChar = buf.g1()
                }

                2 -> {
                    t.defaultInt = buf.g4()
                }

                4 -> {
                    t.autoDisable = false
                }

                5 -> {
                    t.defaultString = buf.gjstr()
                }

                7 -> {
                    t.defaultLong = buf.g8()
                }

                8 -> {
                    t.typeId = buf.g1()
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }
}
