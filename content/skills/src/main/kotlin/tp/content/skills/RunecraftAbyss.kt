package tp.content.skills

import tp.plugin.AppearanceChanged
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.SharedAttrs
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits
import tp.util.CoordGrid

/** Entering a temple (docs/mechanics/runecraft.md RC1-RC2), shared by the ruins and the Abyss rifts. */
internal object TempleEntry {
    private val NEIGHBOURS = listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)

    fun enter(p: PlayerRef, t: Temple) {
        p.message("You feel a powerful force take hold of you...")
        p.teleport(landing(p, t.portals[p.random(t.portals.size)], t.altar))
    }

    /** The walkable tile next to [portal] that is nearest [altar]: the temple's arrival tile (RC2, OPEN-RC1). */
    fun landing(p: PlayerRef, portal: CoordGrid, altar: CoordGrid): CoordGrid {
        val around = NEIGHBOURS.map { (dx, dz) -> portal.translate(dx, dz) }
        return around.sortedBy { distanceSquared(it, altar) }.firstOrNull { p.isWalkable(it) } ?: portal
    }

    fun distanceSquared(a: CoordGrid, b: CoordGrid): Int {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz
    }
}

/** What blocks one of the twelve openings of the Abyss' outer ring (Wiki "Abyss"). */
private enum class AbyssObstacle(val letter: Char, val skill: Int, val verb: String) {
    BLOCKAGE('B', -1, ""),
    ROCK('R', Stats.MINING, "mine your way through the rock"),
    TENDRILS('T', Stats.WOODCUTTING, "chop your way through the tendrils"),
    BOIL('O', Stats.FIREMAKING, "burn your way through the boil"),
    EYES('E', Stats.THIEVING, "distract the eyes and sneak past"),
    GAP('G', Stats.AGILITY, "squeeze through the gap"),
    PASSAGE('P', -1, "go through the passage"),
}

/** Shared state of the Abyss content (docs/mechanics/runecraft.md RC18-RC20). */
internal object RunecraftAbyss {
    /** Account value: the player has crafted runes at Kourend's [name] altar (needed before the rift can be redirected). */
    fun riftKey(name: String): String = "runecraft.rift.$name.crafted"

    /** Account value: the [name] rift has been redirected to Kourend's altar with dark essence. */
    fun redirectKey(name: String): String = "runecraft.rift.$name.redirected"
}

/**
 * The Abyss (docs/mechanics/runecraft.md RC18-RC20): the Mage of Zamorak's teleport, the twelve openings of the outer ring with
 * their skill checks, and the rifts of the inner ring that lead to every altar without a talisman.
 */
public class RunecraftAbyssContent : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.RCU_ZAMMY_MAGE1, OP_TELEPORT) { enterAbyss(player) }
        onWorldStart { spawnNpc(Npcs.RCU_ZAMMY_MAGE1, MAGE_AT, MAGE_WANDER) }
        for ((k, loc) in OPENINGS.withIndex()) onOpLoc(loc, OP_OPENING) { cross(k) }
        for ((loc, name) in RIFTS) {
            onOpLoc(loc, OP_RIFT) { exitThrough(player, name) }
        }
        onOpLoc(Locs.ABYSS_EXIT_TO_BLOOD_PARENT, OP_RIFT) { exitThrough(player, "blood") }
        onOpLoc(Locs.ABYSS_EXIT_TO_BLOOD_PARENT, OP_RIFT_KOUREND) { exitKourend(player, "blood") }
        for (rift in listOf(Locs.ABYSS_EXIT_TO_BLOOD_PARENT, Locs.ABYSS_EXIT_TO_SOUL)) {
            val name = if (rift == Locs.ABYSS_EXIT_TO_SOUL) "soul" else "blood"
            for (essence in listOf(Objs.ARCEUUS_ESSENCE_BLOCK_DARK, Objs.BIGBLANKRUNE)) onUseOnLoc(essence, rift) { redirect(player, name) }
        }
        onOpLoc(Locs.ABYSS_EXIT_TO_SOUL, OP_RIFT) { exitKourend(player, "soul") }
    }

    // ---- the Mage of Zamorak

    private fun enterAbyss(p: PlayerRef) {
        if (!RunecraftQuests.ENTER_THE_ABYSS.done(p)) return p.message(NOTHING)
        // Wiki "Abyss": all Prayer points are drained and a skull is given, unless an abyssal bracelet is worn.
        p.setLevel(Stats.PRAYER, 0)
        if (!wearsBracelet(p)) skull(p)
        p.stopTimer(UNSKULL)
        val config = p.random(OPENINGS.size)
        p.setVarbit(Varbits.RCU_ABYSSAL_GENERATOR, config)
        p.teleport(crossing(p, config).outside)
    }

    private fun wearsBracelet(p: PlayerRef): Boolean {
        val gloves = p.inv(Invs.WORN).obj(HANDS_SLOT)
        return gloves >= 0 && "abyssal bracelet" in p.objName(gloves).lowercase()
    }

    private fun skull(p: PlayerRef) {
        p[SharedAttrs.SKULL_ICON] = SKULL_ICON
        p.events.publish(AppearanceChanged(p))
    }

    // ---- the outer ring

    private class Crossing(val outside: CoordGrid, val inside: CoordGrid)

    /**
     * The tiles on either side of opening [k]: its loc is 3x2 and the thin side is the way through; "outside" is the side away
     * from the middle of the ring (OPEN-RC18: derived from the loc's placement and rotation, no source gives the tiles).
     */
    private fun crossing(p: PlayerRef, k: Int): Crossing {
        val at = RING[k]
        val odd = (p.locAt(at, SCENERY_SHAPE)?.angle ?: 0) % 2 == 1
        val width = if (odd) THIN else THICK
        val length = if (odd) THICK else THIN
        val crossing =
            if (!odd) {
                val up = 2 * at.z + length - 1 >= 2 * CENTRE.z
                Crossing(
                    CoordGrid(at.x + 1, if (up) at.z + length else at.z - 1, 0),
                    CoordGrid(at.x + 1, if (up) at.z - 1 else at.z + length, 0),
                )
            } else {
                val right = 2 * at.x + width - 1 >= 2 * CENTRE.x
                Crossing(
                    CoordGrid(if (right) at.x + width else at.x - 1, at.z + 1, 0),
                    CoordGrid(if (right) at.x - 1 else at.x + width, at.z + 1, 0),
                )
            }
        return Crossing(standable(p, crossing.outside), standable(p, crossing.inside))
    }

    private fun standable(p: PlayerRef, tile: CoordGrid): CoordGrid = if (p.isWalkable(tile)) tile else RcTiles.beside(p, tile)

    /** RC19: the opening's obstacle at the player's configuration, tried from whichever side the player is on. */
    private suspend fun PlayerScript.cross(k: Int) {
        val p = player
        val config = p.varbit(Varbits.RCU_ABYSSAL_GENERATOR).coerceIn(0, OPENINGS.size - 1)
        val obstacle = AbyssObstacle.entries.first { it.letter == ROWS[k][config] }
        if (obstacle == AbyssObstacle.BLOCKAGE) return p.message("You cannot get through here.")
        if (obstacle.skill >= 0 && !hasTool(p, obstacle)) return
        val sides = crossing(p, k)
        val target =
            if (TempleEntry.distanceSquared(p.coord, sides.outside) <=
                TempleEntry.distanceSquared(p.coord, sides.inside)
            ) {
                sides.inside
            } else {
                sides.outside
            }
        delay(1)
        // Wiki "Abyss": "Each attempt has a level+1 percent chance of success"; the passage is guaranteed.
        val success = obstacle == AbyssObstacle.PASSAGE || p.random(PERCENT) < p.level(obstacle.skill) + 1
        if (!success) return p.message("You fail to ${obstacle.verb}.")
        p.message("You ${obstacle.verb}.")
        if (obstacle.skill >= 0) p.addXp(obstacle.skill, OBSTACLE_XP)
        p.teleport(target)
    }

    /** Wiki "Abyss": rocks need a pickaxe, tendrils an axe, boils a tinderbox. */
    private fun hasTool(p: PlayerRef, obstacle: AbyssObstacle): Boolean {
        val have =
            when (obstacle) {
                AbyssObstacle.ROCK -> anyNamed(p) { it.endsWith("pickaxe") }
                AbyssObstacle.TENDRILS -> anyNamed(p) { it.endsWith(" axe") }
                AbyssObstacle.BOIL -> p.inv(Invs.INV).total(Objs.TINDERBOX) > 0
                else -> true
            }
        if (!have) p.message("You need a ${TOOL_NAMES.getValue(obstacle)} to ${obstacle.verb.substringBefore(" your")} through this.")
        return have
    }

    private fun anyNamed(p: PlayerRef, test: (String) -> Boolean): Boolean {
        val worn = p.inv(Invs.WORN).obj(Skilling.WEAPON_SLOT)
        if (worn >= 0 && test(p.objName(worn).lowercase())) return true
        val inv = p.inv(Invs.INV)
        return (0 until inv.size).any { inv.obj(it) >= 0 && test(p.objName(inv.obj(it)).lowercase()) }
    }

    // ---- the inner ring

    private fun exitThrough(p: PlayerRef, name: String) {
        val temple = RunecraftData.TEMPLES.first { it.name == name }
        val gate = RIFT_QUESTS[name]
        if (gate != null && !gate.done(p)) return p.message("You need to have completed ${gate.name} to enter this rift.")
        leave(p)
        TempleEntry.enter(p, temple)
    }

    /** The rifts to Kourend's altars work once dark essence has redirected them (Wiki "Blood Altar (Kourend)"). */
    private fun exitKourend(p: PlayerRef, name: String) {
        if (p.saved(RunecraftAbyss.redirectKey(name)) == null) return p.message(NOTHING)
        leave(p)
        p.teleport(
            TempleEntry.landing(
                p,
                if (name ==
                    "soul"
                ) {
                    KOUREND_SOUL
                } else {
                    KOUREND_BLOOD
                },
                if (name == "soul") KOUREND_SOUL else KOUREND_BLOOD,
            ),
        )
    }

    private fun redirect(p: PlayerRef, name: String) {
        if (p.saved(RunecraftAbyss.riftKey(name)) == null) return p.message(NOTHING)
        p.setSaved(RunecraftAbyss.redirectKey(name), 1L)
        if (name == "blood") p.setVarbit(Varbits.ABYSS_BLOOD_RIFT_LAST_USED, 1)
        p.message(
            "The power of the Dark Altar redirects the rift to the place in Arceuus where the energy of the true $name altar is focused.",
        )
    }

    /** Wiki "Skull (status)": the Abyss skull "disappears 10 minutes after exiting the Abyss". */
    private fun leave(p: PlayerRef) {
        if (p[SharedAttrs.SKULL_ICON] == null) return
        p.timer(UNSKULL, UNSKULL_TICKS, soft = true) {
            this[SharedAttrs.SKULL_ICON] = null
            events.publish(AppearanceChanged(this))
            stopTimer(UNSKULL)
        }
    }

    private companion object {
        /** Cache npc `rcu_zammy_mage1` (3228, a multi-npc on varp 492): child 2581 has op 4 "Teleport". */
        const val OP_TELEPORT = 4

        /** Loc op 1 of the openings ("Mine", "Chop", ...) and the rifts ("Exit-through"); op 2 of the blood rift is the Kourend one. */
        const val OP_OPENING = 1
        const val OP_RIFT = 1
        const val OP_RIFT_KOUREND = 2

        const val NOTHING = "Nothing interesting happens."
        const val PERCENT = 100

        /** Wiki "Abyss": passing an obstacle gives 25 experience in its skill. */
        const val OBSTACLE_XP = 25.0

        /** Worn slot of the gloves / bracelet (cache wear position 9). */
        const val HANDS_SLOT = 9

        /** Skull icon 0 is the plain skull (content/pvp). */
        const val SKULL_ICON = 0

        /** Wiki "Skull (status)": 10 minutes (1,000 ticks) after leaving the Abyss. */
        const val UNSKULL_TICKS = 1000
        val UNSKULL = TimerId("runecraft.abyss_skull")

        /** Scenery loc shape (cache shape 10). The openings are 3x2: the thin side is the way through. */
        const val SCENERY_SHAPE = 10
        const val THICK = 3
        const val THIN = 2

        /** Wiki "Mage of Zamorak" infobox map (3107, 3555): he roams the south Wilderness mine; the range is ours (OPEN-RC18). */
        val MAGE_AT = CoordGrid(3107, 3555, 0)
        const val MAGE_WANDER = 4

        /** Wiki "Abyss" map pin centre (3039, 4832): the middle of the two rings (the Dark Mage stands at (3039, 4834)). */
        val CENTRE = CoordGrid(3039, 4832, 0)

        /** Where Kourend's altars are (`cache-dump find-locs`): the rifts leave the player beside them. */
        val KOUREND_BLOOD = CoordGrid(1715, 3828, 0)
        val KOUREND_SOUL = CoordGrid(1813, 3853, 0)

        private val TOOL_NAMES = mapOf(AbyssObstacle.ROCK to "pickaxe", AbyssObstacle.TENDRILS to "axe", AbyssObstacle.BOIL to "tinderbox")

        /**
         * The twelve opening locs `rcu_outer_multi1..12`, clockwise from the south, and where each is placed (`cache-dump
         * find-locs`). Each is a multi-loc on varbit 625 (`rcu_abyssal_generator`): value 0..11 is the ring's configuration.
         */
        val OPENINGS =
            listOf(
                Locs.RCU_OUTER_MULTI1,
                Locs.RCU_OUTER_MULTI2,
                Locs.RCU_OUTER_MULTI3,
                Locs.RCU_OUTER_MULTI4,
                Locs.RCU_OUTER_MULTI5,
                Locs.RCU_OUTER_MULTI6,
                Locs.RCU_OUTER_MULTI7,
                Locs.RCU_OUTER_MULTI8,
                Locs.RCU_OUTER_MULTI9,
                Locs.RCU_OUTER_MULTI10,
                Locs.RCU_OUTER_MULTI11,
                Locs.RCU_OUTER_MULTI12,
            )

        @Suppress("MagicNumber")
        val RING =
            listOf(
                CoordGrid(3041, 4811, 0),
                CoordGrid(3026, 4813, 0),
                CoordGrid(3018, 4821, 0),
                CoordGrid(3018, 4833, 0),
                CoordGrid(3021, 4842, 0),
                CoordGrid(3028, 4849, 0),
                CoordGrid(3038, 4853, 0),
                CoordGrid(3049, 4849, 0),
                CoordGrid(3058, 4839, 0),
                CoordGrid(3060, 4830, 0),
                CoordGrid(3057, 4821, 0),
                CoordGrid(3049, 4813, 0),
            )

        /**
         * What opening [k] shows in configuration `c`: `ROWS[k][c]`, the first twelve children of each multi-loc (cache
         * configs 26187-26192, 26208, 26250-26253, 26574). B blockage, R rock, T tendrils, O boil, E eyes, G gap, P passage.
         * In configuration c the blockage is opening c and the passage opening c + 6.
         */
        val ROWS =
            listOf(
                "BRTOEGPGEOTR",
                "RBRTOERPREOT",
                "TRBRTOTRPRTO",
                "OTRBRTOTRPRT",
                "EOTRBREOTRPR",
                "GEOTRBGEOTRP",
                "PGEOTRBGEOTR",
                "GPGEOTRBGEOT",
                "EGPGEOEGBGEO",
                "OEGPGEOEGBGE",
                "TOEGPGTOEGBG",
                "RTOEGPRTOEGB",
            )

        /** Cache locs `abyss_exit_to_<altar>` ("Fire rift", ...): the altar each leads to. Blood and soul are handled apart. */
        val RIFTS =
            mapOf(
                Locs.ABYSS_EXIT_TO_FIRE to "fire",
                Locs.ABYSS_EXIT_TO_EARTH to "earth",
                Locs.ABYSS_EXIT_TO_BODY to "body",
                Locs.ABYSS_EXIT_TO_COSMIC to "cosmic",
                Locs.ABYSS_EXIT_TO_NATURE to "nature",
                Locs.ABYSS_EXIT_TO_CHAOS to "chaos",
                Locs.ABYSS_EXIT_TO_LAW to "law",
                Locs.ABYSS_EXIT_TO_DEATH to "death",
                Locs.ABYSS_EXIT_TO_WATER to "water",
                Locs.ABYSS_EXIT_TO_AIR to "air",
                Locs.ABYSS_EXIT_TO_MIND to "mind",
            )

        /** Wiki "Abyss", Inner ring: the quests the cosmic, law, death and blood rifts need. */
        val RIFT_QUESTS =
            mapOf(
                "cosmic" to RunecraftQuests.LOST_CITY,
                "law" to RunecraftQuests.TROLL_STRONGHOLD,
                "death" to RunecraftQuests.MOURNINGS_END_II,
                "blood" to RunecraftQuests.SINS_OF_THE_FATHER,
            )
    }
}
