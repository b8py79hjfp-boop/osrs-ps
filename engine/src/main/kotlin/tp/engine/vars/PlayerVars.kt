package tp.engine.vars

import tp.cache.config.TypeList
import tp.cache.config.varbit.VarbitType
import tp.protocol.api.ClientSession
import tp.protocol.api.ServerMessage

/**
 * A player's varps. Every varp in the cache is client-visible (server-only vars aren't in the cache), so each change
 * is transmitted: changes are collected and sent once at the end of the tick, in the info pass (brief §5.5).
 * Varbits are bit ranges of a base varp (`VarbitType.baseVar/startBit/endBit`, client `Varbit` semantics).
 */
class PlayerVars internal constructor(varpCount: Int, private val varbits: TypeList<VarbitType>) {
    private val values = IntArray(varpCount)
    private val dirtyFlag = BooleanArray(varpCount)
    private val dirty = IntArray(varpCount)
    private var dirtyCount = 0

    operator fun get(varp: Int): Int = values[varp]

    operator fun set(varp: Int, value: Int) {
        if (values[varp] == value) return
        values[varp] = value
        if (!dirtyFlag[varp]) {
            dirtyFlag[varp] = true
            dirty[dirtyCount++] = varp
        }
    }

    fun getBit(varbit: Int): Int {
        val type = varbits.getValue(varbit)
        return (values[type.baseVar] ushr type.startBit) and mask(type)
    }

    fun setBit(varbit: Int, value: Int) {
        val type = varbits.getValue(varbit)
        val mask = mask(type)
        require(value and mask.inv() == 0) { "varbit $varbit: $value does not fit in ${type.endBit - type.startBit + 1} bits" }
        val base = values[type.baseVar]
        set(type.baseVar, (base and (mask shl type.startBit).inv()) or (value shl type.startBit))
    }

    internal fun hash(): Long = values.contentHashCode().toLong()

    /** Login: every non-zero varp, after the client was told to reset (VarpReset). */
    internal fun sendAll(session: ClientSession) {
        session.send(ServerMessage.VarpReset)
        for (i in values.indices) if (values[i] != 0) session.send(ServerMessage.Varp(i, values[i]))
        clearDirty()
    }

    internal fun flush(session: ClientSession) {
        for (i in 0 until dirtyCount) session.send(ServerMessage.Varp(dirty[i], values[dirty[i]]))
        clearDirty()
    }

    private fun clearDirty() {
        for (i in 0 until dirtyCount) dirtyFlag[dirty[i]] = false
        dirtyCount = 0
    }

    private companion object {
        const val INT_BITS = 32

        fun mask(type: VarbitType): Int {
            val bits = type.endBit - type.startBit + 1
            return if (bits >= INT_BITS) -1 else (1 shl bits) - 1
        }
    }
}
