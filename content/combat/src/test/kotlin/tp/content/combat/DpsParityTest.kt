package tp.content.combat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tp.cache.config.Configs
import tp.cache.js5.Js5Cache
import tp.util.Revision
import java.nio.file.Path

/**
 * Phase 4 done-when: our max hits and attack rolls equal the Wiki DPS calculator's for a recorded gear matrix
 * (tools/dps-matrix). Player-side values only; NPC defence data arrives with wiki-import.
 */
class DpsParityTest {
    private val configs: Configs by lazy {
        val root = System.getProperty("tp.repoRoot") ?: "."
        Js5Cache.open(Path.of(root, ".data", "cache", Revision.NUMBER.toString(), "disk")).use { Configs.load(it) }
    }

    private val gear: Map<String, Map<String, Int>> =
        mapOf(
            "unarmed" to emptyMap(),
            "whip" to mapOf("weapon" to 4151),
            "dscim" to mapOf("weapon" to 4587),
            "ags" to mapOf("weapon" to 11802),
            "gmaul" to mapOf("weapon" to 4153),
            "claws" to mapOf("weapon" to 13652),
            "dds" to mapOf("weapon" to 1215),
            "whipRune" to mapOf("weapon" to 4151, "head" to 1163, "body" to 1127, "legs" to 1079, "shield" to 1201),
            "whipMax" to
                mapOf(
                    "weapon" to 4151,
                    "head" to 10828,
                    "cape" to 6570,
                    "neck" to 6585,
                    "body" to 10551,
                    "legs" to 4087,
                    "hands" to 7462,
                    "feet" to 11840,
                    "ring" to 6737,
                    "shield" to 12954,
                ),
            "rcb" to mapOf("weapon" to 9185, "ammo" to 9144),
            "msb" to mapOf("weapon" to 861, "ammo" to 892),
            "rcbMax" to
                mapOf(
                    "weapon" to 9185,
                    "ammo" to 9144,
                    "head" to 12931,
                    "cape" to 10499,
                    "neck" to 6585,
                    "body" to 2503,
                    "legs" to 2497,
                    "hands" to 7462,
                    "feet" to 6328,
                ),
            "staff" to mapOf("weapon" to 4675),
            "staffOccult" to mapOf("weapon" to 4675, "neck" to 12002),
        )

    private val prayers: Map<String, List<String>> =
        mapOf(
            "none" to emptyList(),
            "piety" to listOf("Piety"),
            "us_ir" to listOf("Ultimate Strength", "Incredible Reflexes"),
            "burst" to listOf("Burst of Strength", "Clarity of Thought"),
            "rigour" to listOf("Rigour"),
            "eagle" to listOf("Eagle Eye"),
            "sharp" to listOf("Sharp Eye"),
            "augury" to listOf("Augury"),
            "might" to listOf("Mystic Might"),
        )

    private val spells = mapOf("Ice Barrage" to 30, "Ice Blitz" to 26, "Ice Burst" to 22, "Ice Rush" to 16)

    private fun bonus(items: Map<String, Int>, param: Int): Int = items.values.sumOf { configs.objs[it]?.params?.int(param) ?: 0 }

    private fun percent(names: List<String>, of: (PrayerEffect) -> Int): Int = 100 + names.sumOf { of(PrayerTable.effects.getValue(it)) }

    private fun stanceOf(name: String): Stance =
        when (name) {
            "Accurate" -> Stance.ACCURATE
            "Aggressive" -> Stance.AGGRESSIVE
            "Defensive" -> Stance.DEFENSIVE
            "Controlled" -> Stance.CONTROLLED
            "Rapid" -> Stance.RAPID
            "Longrange" -> Stance.LONGRANGE
            else -> error("stance $name")
        }

    @Test
    fun `matches the wiki dps calculator`() {
        val text = javaClass.getResource("/dps-matrix.json")?.readText() ?: error("matrix missing")
        val rows = Json.parseToJsonElement(text).jsonArray
        val failures = ArrayList<String>()
        for (row in rows) {
            val o = row.jsonObject
            val id = o.getValue("id").jsonPrimitive.content
            val wantMax = o.getValue("maxHit").jsonPrimitive.int
            val wantRoll = o.getValue("maxAttackRoll").jsonPrimitive.int
            val (gotMax, gotRoll) = compute(id.split('|'), failures)
            if (gotMax != wantMax || gotRoll != wantRoll) failures += "$id: max $gotMax/$wantMax roll $gotRoll/$wantRoll"
        }
        assertTrue(failures.isEmpty(), "${failures.size}/${rows.size} mismatches:\n" + failures.take(40).joinToString("\n"))
    }

    private fun compute(f: List<String>, failures: MutableList<String>): Pair<Int, Int> {
        val items = gear.getValue(f[1])
        return when (f[0]) {
            "melee", "ranged" -> {
                val names = prayers.getValue(f[2])
                val level = f[3].toInt()
                val styleIndex = f[4].toInt()
                val stance = stanceOf(f[6]).let { if (f[0] == "ranged" && it == Stance.ACCURATE) Stance.ACCURATE_RANGED else it }
                val weapon = items["weapon"]
                val category = if (weapon == null) Weapons.UNARMED else Weapons.of(configs.objs.getValue(weapon).category)
                val ours = category.slots.filterNotNull().getOrNull(styleIndex)
                val type = if (f[0] == "ranged") AttackType.RANGED else AttackType.valueOf(f[5].uppercase())
                if (ours == null || ours.first != stance || ours.second != type) failures += "${f.joinToString("|")}: style table has $ours"
                if (f[0] == "melee") {
                    val effAtk = Formulas.effectiveLevel(level, percent(names) { it.attack }, stance.attackBonus)
                    val effStr = Formulas.effectiveLevel(level, percent(names) { it.strength }, stance.strengthBonus, strength = true)
                    Formulas.maxHit(effStr, bonus(items, Param.MELEE_STRENGTH)) to Formulas.roll(effAtk, bonus(items, type.attackParam))
                } else {
                    val effAtk = Formulas.effectiveLevel(level, percent(names) { it.rangedAttack }, stance.rangedBonus)
                    val effStr = Formulas.effectiveLevel(level, percent(names) { it.rangedStrength }, stance.rangedBonus, strength = true)
                    Formulas.maxHit(effStr, bonus(items, Param.RANGED_STRENGTH)) to Formulas.roll(effAtk, bonus(items, Param.ATTACK_RANGED))
                }
            }

            else -> {
                val names = prayers.getValue(f[3])
                val level = f[4].toInt()
                val eff = Formulas.effectiveLevel(level, percent(names) { it.magic }, Stance.SPELL.magicBonus)
                val damagePercent = bonus(items, Param.MAGIC_DAMAGE) / 10 + names.sumOf { PrayerTable.effects.getValue(it).magicDamage }
                Formulas.magicMaxHit(spells.getValue(f[2]), damagePercent) to Formulas.roll(eff, bonus(items, Param.ATTACK_MAGIC))
            }
        }
    }

    /** Gear effects (docs/mechanics/gear-effects.md): [min, max] and max attack roll per passive / set (tools/dps-matrix GearMatrix). */
    @Test
    fun `gear effects match the wiki dps calculator`() {
        val text = javaClass.getResource("/gear-matrix.json")?.readText() ?: error("gear matrix missing")
        val failures = ArrayList<String>()
        val rows = Json.parseToJsonElement(text).jsonArray
        for (row in rows) {
            val o = row.jsonObject
            val id = o.getValue("id").jsonPrimitive.content
            val (min, max, roll) = gearRow(o)
            val want =
                Triple(
                    o.getValue("min").jsonPrimitive.int,
                    o.getValue("max").jsonPrimitive.int,
                    o.getValue("maxAttackRoll").jsonPrimitive.int,
                )
            if (Triple(min, max, roll) !=
                want
            ) {
                failures += "$id: got min/max/roll $min/$max/$roll, want ${want.first}/${want.second}/${want.third}"
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size}/${rows.size} mismatches:\n" + failures.joinToString("\n"))
    }

    /** One gear row through [GearEffects]: (min, max, max attack roll). */
    private fun gearRow(o: kotlinx.serialization.json.JsonObject): Triple<Int, Int, Int> {
        val ids = o.getValue("gear").jsonArray.map { it.jsonPrimitive.int }
        val kit = GearEffects.Kit(ids.map { configs.objs.getValue(it).name })
        val m = o.getValue("monster").jsonObject
        val buffs = o.getValue("buffs").jsonObject

        fun flag(name: String) = buffs[name]?.jsonPrimitive?.boolean ?: false
        val foe =
            GearEffects.Foe(
                m
                    .getValue("attributes")
                    .jsonArray
                    .map { it.jsonPrimitive.content }
                    .toSet(),
                m.getValue("size").jsonPrimitive.int,
                m.getValue("magic").jsonPrimitive.int,
                m.getValue("magicAttack").jsonPrimitive.int,
                m.getValue("name").jsonPrimitive.content,
                isPlayer = false,
                onSlayerTask = flag("onSlayerTask"),
            )
        val style = o.getValue("style").jsonPrimitive.content
        val stanceName = o.getValue("stance").jsonPrimitive.content
        val bonus = { param: Int ->
            ids.sumOf {
                val params = configs.objs[it]?.params
                (params?.int(param) ?: 0) + if (param == Param.RANGED_STRENGTH) params?.int(Param.RANGED_STRENGTH_ALT) ?: 0 else 0
            }
        }
        return when (style) {
            "ranged" -> {
                val stance = if (stanceName == "Accurate") Stance.ACCURATE_RANGED else stanceOf(stanceName)
                val c = GearEffects.Ctx(kit, foe, AttackType.RANGED, flag("inWilderness"))
                val atlatl = GearEffects.strengthScaledRanged(kit)
                val eff = Formulas.effectiveLevel(99, 100, stance.rangedBonus)
                val r = GearEffects.rangedAttackRoll(c, GearEffects.rangedAccuracyLevel(eff, kit), bonus(Param.ATTACK_RANGED))
                val effStr = Formulas.effectiveLevel(99, 100, stance.rangedBonus, strength = true)
                val mm = GearEffects.rangedMax(c, effStr, bonus(if (atlatl) Param.MELEE_STRENGTH else Param.RANGED_STRENGTH))
                Triple(mm[0], mm[1], r)
            }

            "magic" -> {
                val spell = o.getValue("spell").jsonObject
                val book = spell.getValue("spellbook").jsonPrimitive.content
                val element = spell["element"]?.takeIf { it != kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content
                val info =
                    GearEffects.SpellInfo(
                        book == "standard",
                        element,
                        demonbane = false,
                        bind = false,
                        ancient =
                            book == "ancient",
                    )
                val c = GearEffects.Ctx(kit, foe, AttackType.MAGIC, flag("inWilderness"))
                val eff = Formulas.effectiveLevel(99, 100, Stance.SPELL.magicBonus)
                val r =
                    GearEffects.magicAttackRoll(
                        c,
                        GearEffects.magicAccuracyLevel(eff, kit),
                        bonus(Param.ATTACK_MAGIC),
                        info,
                        0,
                        marked = false,
                    )
                val base = spell.getValue("max_hit").jsonPrimitive.int
                val amulet = with(GearEffects) { kit.elementalAmulet(element) }
                Triple(0, GearEffects.magicMax(c, base, bonus(Param.MAGIC_DAMAGE), info, 0, amulet), r)
            }

            else -> {
                val type = AttackType.valueOf(style.uppercase())
                val stance = stanceOf(stanceName)
                val c = GearEffects.Ctx(kit, foe, type, flag("inWilderness"))
                val eff = Formulas.effectiveLevel(99, 100, stance.attackBonus)
                val r = GearEffects.meleeAttackRoll(c, GearEffects.meleeAccuracyLevel(eff, kit), bonus(type.attackParam))
                val stacks = buffs["soulreaperStacks"]?.jsonPrimitive?.int ?: 0
                val mm = GearEffects.meleeMax(c, 99, 99, stance.strengthBonus, bonus(Param.MELEE_STRENGTH), stacks)
                Triple(mm[0], mm[1], r)
            }
        }
    }
}
