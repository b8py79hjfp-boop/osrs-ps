package tp.cache.export

import tp.cache.config.Configs
import java.nio.file.Files
import java.nio.file.Path

/**
 * RuneLite's static data files, built from our cache, for the release client's `{api}/static/...` requests
 * (client/runelite/ENDPOINTS.md):
 * - `item/stats.ids.min.json`: map obj id -> `ItemStats` (`equipable`, `weight` in kg, `ge_limit`, and `equipment`
 *   with RuneLite's `ItemEquipmentStats` field names). Bonuses come from the obj params in docs/mechanics/combat-data.md.
 * - `npcs/npcs.min.json`: map npc id -> `NpcInfo` (`hitpoints`, cache NPC stat 3).
 * GE buy limits are not in the cache, so `ge_limit` is 0 (RuneLite then shows no limit).
 */
object RuneLiteStatic {
    fun write(configs: Configs, dir: Path) {
        Files.createDirectories(dir.resolve("item"))
        Files.createDirectories(dir.resolve("npcs"))
        Files.writeString(dir.resolve("item/stats.ids.min.json"), itemStats(configs))
        Files.writeString(dir.resolve("npcs/npcs.min.json"), npcStats(configs))
    }

    fun itemStats(configs: Configs): String =
        buildString {
            append('{')
            var first = true
            for (obj in configs.objs) {
                if (obj.name == "null") continue
                val equipable = obj.wearPos1 >= 0
                if (!equipable && obj.weight == 0) continue
                if (!first) append(',')
                first = false
                append('"').append(obj.id).append("\":{\"equipable\":").append(equipable)
                append(",\"weight\":").append(obj.weight / GRAMS_PER_KG)
                append(",\"ge_limit\":0")
                if (equipable) {
                    val p = obj.params

                    fun b(param: Int) = p.int(param) ?: 0
                    append(",\"equipment\":{\"slot\":").append(obj.wearPos1)
                    append(",\"is2h\":").append(obj.wearPos2 == SHIELD_SLOT)
                    for ((i, field) in BONUS_FIELDS.withIndex()) append(",\"").append(field).append("\":").append(b(i))
                    append(",\"str\":").append(b(MELEE_STR))
                    append(",\"rstr\":").append(b(RANGED_STR))
                    append(",\"mdmg\":").append(b(MAGIC_DMG) / TENTHS)
                    append(",\"prayer\":").append(b(PRAYER))
                    append(",\"aspeed\":").append(p.int(ATTACK_SPEED) ?: 0)
                    append('}')
                }
                append('}')
            }
            append('}')
        }

    fun npcStats(configs: Configs): String =
        configs.npcs
            .filter { it.name != "null" }
            .joinToString(",", prefix = "{", postfix = "}") { "\"${it.id}\":{\"hitpoints\":${it.stats[HITPOINTS_STAT]}}" }

    /** Params 0-9: stab/slash/crush/magic/ranged attack, then the same defences (combat-data.md). */
    private val BONUS_FIELDS = listOf("astab", "aslash", "acrush", "amagic", "arange", "dstab", "dslash", "dcrush", "dmagic", "drange")
    private const val MELEE_STR = 10
    private const val PRAYER = 11
    private const val RANGED_STR = 12
    private const val ATTACK_SPEED = 14
    private const val MAGIC_DMG = 299

    /** Magic damage param is in tenths of a percent; RuneLite's `mdmg` is percent. */
    private const val TENTHS = 10.0

    /** Cache weight is grams; RuneLite's is kg. */
    private const val GRAMS_PER_KG = 1000.0

    /** A two-handed weapon also occupies the shield wear slot (wearpos2 = 5). */
    private const val SHIELD_SLOT = 5

    /** NpcType.stats order: attack, defence, strength, hitpoints, ranged, magic. */
    private const val HITPOINTS_STAT = 3
}
