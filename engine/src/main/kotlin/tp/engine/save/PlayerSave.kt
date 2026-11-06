package tp.engine.save

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import tp.engine.entity.Player
import tp.engine.inv.InvStack
import tp.engine.stat.PlayerStats
import tp.util.CoordGrid

/**
 * Versioned player saves (brief §12 "Versioned saves"): a JSON document with a schema version; [migrations] upgrade
 * older documents step by step on load, so a revision bump never corrupts or drops data. Built on the world thread
 * (snapshot), written by the hub off-thread.
 *
 * v1: `{"v":1,"coord":packed,"run":bool,"energy":int,"stats":[[xp,level]...],"varps":{"id":value},
 * "invs":{"id":{"stack":"NORMAL","objs":[[slot,obj,count]...]}}}`
 */
object PlayerSave {
    const val VERSION = 1

    /** Forward migrations: index i upgrades version i+1 to i+2. */
    private val migrations: List<(JsonObject) -> JsonObject> = emptyList()

    fun snapshot(player: Player): String {
        val doc =
            buildJsonObject {
                put("v", VERSION)
                put("coord", player.coord.packed)
                put("run", player.runMode)
                put("energy", player.runEnergy)
                put(
                    "stats",
                    buildJsonArray {
                        for (s in 0 until PlayerStats.COUNT) {
                            add(
                                buildJsonArray {
                                    add(kotlinx.serialization.json.JsonPrimitive(player.stats.xpTenths(s)))
                                    add(kotlinx.serialization.json.JsonPrimitive(player.stats.level(s)))
                                },
                            )
                        }
                    },
                )
                put(
                    "varps",
                    buildJsonObject {
                        player.vars.forEachNonZero { id, value -> put(id.toString(), value) }
                    },
                )
                put(
                    "invs",
                    buildJsonObject {
                        for ((id, inv) in player.invs) {
                            put(
                                id.toString(),
                                buildJsonObject {
                                    put("stack", inv.stack.name)
                                    put(
                                        "objs",
                                        buildJsonArray {
                                            for (slot in 0 until inv.size) {
                                                if (inv.isEmpty(slot)) continue
                                                add(
                                                    buildJsonArray {
                                                        add(kotlinx.serialization.json.JsonPrimitive(slot))
                                                        add(kotlinx.serialization.json.JsonPrimitive(inv.obj(slot)))
                                                        add(kotlinx.serialization.json.JsonPrimitive(inv.count(slot)))
                                                    },
                                                )
                                            }
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
            }
        return doc.toString()
    }

    /** Upgrades [text] to [VERSION]. */
    fun migrate(text: String): JsonObject {
        var doc = Json.parseToJsonElement(text).jsonObject
        var version = doc["v"]?.jsonPrimitive?.intOrNull ?: 1
        require(version <= VERSION) { "save version $version is newer than this server ($VERSION)" }
        while (version < VERSION) {
            doc = migrations[version - 1](doc)
            version++
        }
        return doc
    }

    /** Applies a save to a freshly created player before it enters the world. */
    fun apply(player: Player, text: String) {
        val doc = migrate(text)
        doc["coord"]?.jsonPrimitive?.int?.let { player.coord = CoordGrid(it) }
        doc["run"]
            ?.jsonPrimitive
            ?.content
            ?.toBooleanStrictOrNull()
            ?.let { player.runMode = it }
        doc["energy"]?.jsonPrimitive?.int?.let { player.runEnergy = it }
        doc["stats"]?.jsonArray?.forEachIndexed { s, e ->
            val pair = e.jsonArray
            if (s < PlayerStats.COUNT) player.stats.restore(s, pair[0].jsonPrimitive.int, pair[1].jsonPrimitive.int)
        }
        doc["varps"]?.jsonObject?.forEach { (id, v) -> player.vars.restore(id.toInt(), v.jsonPrimitive.int) }
        doc["invs"]?.jsonObject?.forEach { (id, o) ->
            val obj = o.jsonObject
            val stack = InvStack.valueOf(obj.getValue("stack").jsonPrimitive.content)
            val inv = player.world.inventory(player, id.toInt(), stack)
            for (e in obj.getValue("objs").jsonArray) {
                val t = e.jsonArray
                inv.restore(t[0].jsonPrimitive.int, t[1].jsonPrimitive.int, t[2].jsonPrimitive.int)
            }
        }
    }
}
