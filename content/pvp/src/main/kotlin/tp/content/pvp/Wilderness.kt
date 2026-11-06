package tp.content.pvp

import tp.plugin.AttackCheck
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.PolygonAreas
import tp.plugin.TimerId
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Wilderness levels and combat brackets (docs/mechanics/wilderness.md; OSRS Wiki "Wilderness": "Players within combat
 * level range of each other can attack each other ... calculated by taking the combat level and adding or subtracting
 * the current Wilderness level").
 */
public class Wilderness : ContentPlugin {
    override fun ContentScope.register() {
        onLogin { timer(AREA_CHECK, 1, soft = true) { updateArea() } }
        events.on<AttackCheck> { check ->
            val attacker = check.attacker as? PlayerRef ?: return@on
            val target = check.target as? PlayerRef ?: return@on
            val a = level(attacker.coord, attacker.isPvpWorld)
            val b = level(target.coord, target.isPvpWorld)
            when {
                a <= 0 -> {
                    check.deny("You can't attack players outside the Wilderness.")
                }

                b <= 0 -> {
                    check.deny("That player is not in the Wilderness.")
                }

                kotlin.math.abs(attacker.combatLevel - target.combatLevel) > minOf(a, b) -> {
                    check.deny("The difference between your Combat level and the Combat level of your target is too great.")
                }

                else -> {
                    check.allow()
                }
            }
        }
    }

    /** Border crossings run through the area queue (brief §8: "single/multi-way (area queue on borders)"). */
    private fun PlayerRef.updateArea() {
        val inWild = level(coord, isPvpWorld) > 0
        if (this[IN_WILD] != inWild) {
            this[IN_WILD] = inWild
            this[tp.plugin.SharedAttrs.IN_WILDERNESS] = inWild
            areaQueue { playerOp(ATTACK_OP, if (inWild) "Attack" else null, top = true) }
        }
        val inMulti = coord in MULTI
        if (this[IN_MULTI] != inMulti) {
            this[IN_MULTI] = inMulti
            this[tp.plugin.SharedAttrs.IN_MULTI] = inMulti
            areaQueue { setVarbit(Varbits.MULTIWAY_INDICATOR, if (inMulti) 1 else 0) }
        }
        // W13: the singles-plus icon (varbit `singleway_plus_indicator`, set on area entry/exit like RS Mod's script).
        val inSinglesPlus = !inMulti && isSinglesPlus(coord)
        if (this[IN_SINGLES_PLUS] != inSinglesPlus) {
            this[IN_SINGLES_PLUS] = inSinglesPlus
            areaQueue { setVarbit(Varbits.SINGLEWAY_PLUS_INDICATOR, if (inSinglesPlus) 1 else 0) }
        }
    }

    public companion object {
        private val AREA_CHECK = TimerId("wilderness_area")
        private val IN_WILD = tp.plugin.AttrKey<Boolean>("in_wilderness")
        private val IN_MULTI = tp.plugin.AttrKey<Boolean>("in_multi")
        private val IN_SINGLES_PLUS = tp.plugin.AttrKey<Boolean>("in_singles_plus")

        /**
         * W12 PvP-world safe zones, packed tiles (`pvp-safe-zones.tsv`, derived from the cache by
         * `cache-dump pvp-safe-zones`: bank interiors and our respawn point; geometry OPEN-W2).
         */
        private val SAFE: Set<Long> = loadSafeZones()

        /** Tile key: level above x above z, 20 bits per coordinate. */
        private const val LEVEL_SHIFT = 40
        private const val X_SHIFT = 20

        private fun pack(level: Int, x: Int, z: Int): Long = (level.toLong() shl LEVEL_SHIFT) or (x.toLong() shl X_SHIFT) or z.toLong()

        private fun loadSafeZones(): Set<Long> {
            val text = Wilderness::class.java.getResource("pvp-safe-zones.tsv")?.readText() ?: error("pvp-safe-zones.tsv missing")
            val out = HashSet<Long>()
            for (line in text.lineSequence().drop(1)) {
                val c = line.split('\t')
                if (c.size < 4) continue
                val level = c[0].toInt()
                val z = c[3].toInt()
                for (x in c[1].toInt()..c[2].toInt()) out += pack(level, x, z)
            }
            return out
        }

        /** W12: inside a PvP-world safe zone. */
        public fun isSafeZone(c: CoordGrid): Boolean = pack(c.level, c.x, c.z) in SAFE

        /**
         * W13 singles-plus areas (OSRS Wiki "Singles-plus combat"): the Revenant Caves ("entirely singles-plus"; cache
         * map squares 49-50 x 157-159, the caves' squares) and the Wilderness Agility Course (its walled yard from the
         * cache collision map, x 2988..3008, z 3931..3966). OPEN-M1: the Artio, Calvar'ion and Spindel lairs have no
         * published coordinates.
         */
        public fun isSinglesPlus(c: CoordGrid): Boolean =
            (c.x in REV_MIN_X..REV_MAX_X && c.z in REV_MIN_Z..REV_MAX_Z) ||
                (c.level == 0 && c.x in AGIL_MIN_X..AGIL_MAX_X && c.z in AGIL_MIN_Z..AGIL_MAX_Z)

        private const val REV_MIN_X = 3136
        private const val REV_MAX_X = 3263
        private const val REV_MIN_Z = 10048
        private const val REV_MAX_Z = 10239
        private const val AGIL_MIN_X = 2988
        private const val AGIL_MAX_X = 3008
        private const val AGIL_MIN_Z = 3931
        private const val AGIL_MAX_Z = 3966

        /** Multi-combat polygons (RS Mod `multiway.json`, ISC; data/PROVENANCE.md). */
        public val MULTI: PolygonAreas =
            PolygonAreas.fromJson(
                Wilderness::class.java.getResource("multiway.json")?.readText() ?: error("multiway.json missing"),
            )

        /** PvP worlds: outside the Wilderness counts as level 15 (Wiki "Wilderness"), except in safe zones (W12). */
        private const val PVP_WORLD_LEVEL = 15

        /** Player op slot for "Attack" (matches content/combat `Combat.ATTACK_OP`). */
        private const val ATTACK_OP = 2

        // OPEN-W1: main Wilderness bounds and the level formula are not stated by the Wiki (the client computes the level
        // overlay in a clientscript). Implemented: x 2944..3391, z 3520..3967, level = (z - 3520) / 8 + 1, the first
        // two rows (z 3520..3521) safe ("The first two steps ... of the Wilderness are 'safe' zones").
        private const val MIN_X = 2944
        private const val MAX_X = 3391
        private const val MIN_Z = 3520
        private const val MAX_Z = 3967
        private const val SAFE_ROWS = 2
        private const val ROWS_PER_LEVEL = 8

        /** Wilderness level at [c] (+15 on PvP worlds), 0 outside or in the safe rows. */
        public fun level(c: CoordGrid, pvpWorld: Boolean = false): Int {
            val inBounds = c.level == 0 && c.x in MIN_X..MAX_X && c.z in MIN_Z + SAFE_ROWS..MAX_Z
            val wild = if (inBounds) (c.z - MIN_Z) / ROWS_PER_LEVEL + 1 else 0
            if (!pvpWorld) return wild
            return if (wild == 0 && isSafeZone(c)) 0 else wild + PVP_WORLD_LEVEL
        }
    }
}
