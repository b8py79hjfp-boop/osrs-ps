package tp.cache.config.obj

import tp.cache.config.ConfigDecoder
import tp.cache.config.EntityOps
import tp.cache.config.ParamMap
import tp.cache.config.TypeList
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/**
 * Item ("obj") config, archive 2 group 10. Server-relevant fields only; model/render opcodes are consumed and dropped.
 * Defaults are the client's `ObjType` defaults. Mutable only inside this module (decoder + [ObjTypes.link]).
 *
 * THIS FILE IS THE PATTERN for every other config decoder (Phase 1 plan): `var x; internal set` fields with client
 * defaults, a stateless decoder object with one `when (opcode)` loop, strict on unknown opcodes, KDoc citing the
 * reference loader.
 */
class ObjType internal constructor(val id: Int) {
    var name: String = "null"
        internal set
    var cost: Int = 1
        internal set

    /** 0 = not stackable, 1 = stackable (op 11), 2 = always stacks (op 160). */
    var stackable: Int = 0
        internal set
    var members: Boolean = false
        internal set
    var tradeable: Boolean = true
        internal set
    var geTradeable: Boolean = false
        internal set

    /** Primary wear slot (op 13), or -1. */
    var wearPos1: Int = -1
        internal set

    /** Secondary slot hidden by this item (op 14), or -1. */
    var wearPos2: Int = -1
        internal set

    /** Tertiary slot hidden by this item (op 27), or -1. */
    var wearPos3: Int = -1
        internal set
    var weight: Int = 0
        internal set
    var team: Int = 0
        internal set
    var category: Int = -1
        internal set
    val groundOps: EntityOps = EntityOps()

    /** Inventory ops; client default is `[null, null, "Take", null, "Drop"]` (op 35-39 override). */
    val iops: Array<String?> = arrayOfNulls(INV_OP_COUNT)
    var iopSubOps: Map<Int, Map<Int, String>> = emptyMap()
        internal set
    var shiftClickDropIndex: Int = -2
        internal set

    /** Noted form link: [certLink] is the other form's id, [certTemplate] != -1 marks this as the note. */
    var certLink: Int = -1
        internal set
    var certTemplate: Int = -1
        internal set
    var boughtLink: Int = -1
        internal set
    var boughtTemplate: Int = -1
        internal set
    var placeholderLink: Int = -1
        internal set
    var placeholderTemplate: Int = -1
        internal set
    var countObj: IntArray? = null
        internal set
    var countCo: IntArray? = null
        internal set
    var params: ParamMap = ParamMap.EMPTY
        internal set

    val isNoted: Boolean get() = certTemplate != -1
    val isPlaceholder: Boolean get() = placeholderTemplate != -1
    val isStackable: Boolean get() = stackable != 0

    companion object {
        /** Five inventory op slots (iop1..iop5). */
        const val INV_OP_COUNT = 5

        /** Sub-op ids per iop (RuneLite `ItemLoader` opcode 43 bound). */
        const val SUB_OP_COUNT = 20
    }
}

/** Decoder for [ObjType]. Opcode table: RuneLite 1.13.6 `cache/.../loaders/ItemLoader.java`. */
object ObjTypeDecoder : ConfigDecoder<ObjType> {
    override val name: String = "obj"

    private const val OP_GROUND_FIRST = 30
    private const val OP_GROUND_END = 35
    private const val OP_IOP_FIRST = 35
    private const val OP_IOP_END = 40
    private const val OP_COUNT_FIRST = 100
    private const val OP_COUNT_END = 110
    private const val COUNT_SLOTS = 10

    /** Bytes consumed per opcode with no server-relevant payload: opcode -> size. */
    private val SKIP: Map<Int, Int> =
        mapOf(
            1 to 2,
            4 to 2,
            5 to 2,
            6 to 2,
            7 to 2,
            8 to 2,
            24 to 2,
            26 to 2,
            44 to 4,
            46 to 4,
            47 to 4,
            49 to 4,
            50 to 4,
            51 to 4,
            52 to 4,
            53 to 4,
            54 to 4,
            78 to 2,
            79 to 2,
            90 to 2,
            91 to 2,
            92 to 2,
            93 to 2,
            95 to 2,
            99 to 2,
            110 to 2,
            111 to 2,
            112 to 2,
            113 to 1,
            114 to 1,
            23 to 3,
            25 to 3,
            45 to 5,
            48 to 5,
        )

    override fun decode(id: Int, buf: ReadBuf): ObjType {
        val t = ObjType(id)
        // Client default inventory ops: only op5 "Drop" (RuneLite `ItemLoader`: interfaceOptions = {null, null, null, null, "Drop"}).
        val iops: Array<String?> = arrayOf(null, null, null, null, "Drop")
        var iopSubOps: MutableMap<Int, MutableMap<Int, String>>? = null
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            val skip = SKIP[opcode]
            if (skip != null) {
                buf.skip(skip)
                continue
            }
            when (opcode) {
                2 -> {
                    t.name = buf.gjstr()
                }

                3, 9 -> {
                    buf.gjstr()
                }

                // examine (client-side since rev ~180), op 9 unknown string
                11 -> {
                    t.stackable = 1
                }

                12 -> {
                    t.cost = buf.g4()
                }

                13 -> {
                    t.wearPos1 = buf.g1s()
                }

                14 -> {
                    t.wearPos2 = buf.g1s()
                }

                15 -> {
                    t.tradeable = false
                }

                16 -> {
                    t.members = true
                }

                27 -> {
                    t.wearPos3 = buf.g1s()
                }

                in OP_GROUND_FIRST until OP_GROUND_END -> {
                    t.groundOps.decodeOp(buf, opcode - OP_GROUND_FIRST)
                }

                in OP_IOP_FIRST until OP_IOP_END -> {
                    iops[opcode - OP_IOP_FIRST] = buf.gjstr()
                }

                40, 41 -> {
                    buf.skip(buf.g1() * 4)
                }

                // recolour / retexture pairs
                42 -> {
                    t.shiftClickDropIndex = buf.g1s()
                }

                43 -> {
                    val op = buf.g1()
                    val subs = (iopSubOps ?: HashMap<Int, MutableMap<Int, String>>().also { iopSubOps = it })
                    while (true) {
                        val sub = buf.g1() - 1
                        if (sub == -1) break
                        val text = buf.gjstr()
                        if (op < ObjType.INV_OP_COUNT && sub < ObjType.SUB_OP_COUNT) subs.getOrPut(op) { HashMap() }[sub] = text
                    }
                }

                65 -> {
                    t.geTradeable = true
                }

                75 -> {
                    t.weight = buf.g2s()
                }

                94 -> {
                    t.category = buf.g2()
                }

                97 -> {
                    t.certLink = buf.g2()
                }

                98 -> {
                    t.certTemplate = buf.g2()
                }

                in OP_COUNT_FIRST until OP_COUNT_END -> {
                    val objs = t.countObj ?: IntArray(COUNT_SLOTS).also { t.countObj = it }
                    val cos = t.countCo ?: IntArray(COUNT_SLOTS).also { t.countCo = it }
                    objs[opcode - OP_COUNT_FIRST] = buf.g2()
                    cos[opcode - OP_COUNT_FIRST] = buf.g2()
                }

                115 -> {
                    t.team = buf.g1()
                }

                139 -> {
                    t.boughtLink = buf.g2()
                }

                140 -> {
                    t.boughtTemplate = buf.g2()
                }

                148 -> {
                    t.placeholderLink = buf.g2()
                }

                149 -> {
                    t.placeholderTemplate = buf.g2()
                }

                160 -> {
                    t.stackable = 2
                }

                161 -> {
                    buf.skip(buf.g2() * 2)
                }

                // holding whitelist (client render)
                200 -> {
                    t.groundOps.decodeSubOp(buf)
                }

                201 -> {
                    t.groundOps.decodeConditionalOp(buf)
                }

                202 -> {
                    t.groundOps.decodeConditionalSubOp(buf)
                }

                249 -> {
                    t.params = ParamMap.decode(buf)
                }

                251 -> {
                    // flag with no payload, no server use (RuneLite: "bronzeman")
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        System.arraycopy(iops, 0, t.iops, 0, ObjType.INV_OP_COUNT)
        iopSubOps?.let { t.iopSubOps = it }
        // Client `ObjType.postDecode`: stackable items weigh nothing.
        if (t.stackable == 1) t.weight = 0
        return t
    }
}

object ObjTypes {
    /**
     * Resolves note / bought / placeholder forms from their link + template, after every obj is decoded.
     * Server-relevant fields only. Source: RuneLite `ItemDefinition.linkNote/linkBought/linkPlaceholder`
     * (mirrors client `ObjType.genCert/genBought/genPlaceholder`).
     */
    fun link(types: TypeList<ObjType>) {
        for (t in types) {
            if (t.certTemplate != -1) {
                val real = types[t.certLink] ?: continue
                t.name = real.name
                t.members = real.members
                t.cost = real.cost
                t.stackable = 1
            }
            if (t.boughtTemplate != -1) {
                val real = types[t.boughtLink] ?: continue
                t.name = real.name
                t.members = real.members
                t.stackable = real.stackable
                t.team = real.team
                t.cost = 0
            }
            if (t.placeholderTemplate != -1) {
                val real = types[t.placeholderLink] ?: continue
                val template = types[t.placeholderTemplate]
                t.name = real.name
                t.stackable = template?.stackable ?: t.stackable
                t.cost = 0
                t.members = false
                t.tradeable = false
                t.geTradeable = false
            }
        }
    }
}
