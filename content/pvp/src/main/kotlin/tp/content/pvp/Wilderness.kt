package tp.content.pvp

import tp.plugin.AttackCheck
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.PolygonAreas
import tp.plugin.SharedAttrs
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Wilderness levels and combat brackets (docs/mechanics/wilderness.md; OSRS Wiki "Wilderness": "Players within combat
 * level range of each other can attack each other ... calculated by taking the combat level and adding or subtracting
 * the current Wilderness level").
 */
public class Wilderness : ContentPlugin {
    override fun ContentScope.register() {
        // The multi-combat map for other modules (combat's multi-target attacks): a world attr, set once per world.
        onLogin { if (worldAttr(tp.plugin.SharedAttrs.MULTI_AREA) == null) setWorldAttr(tp.plugin.SharedAttrs.MULTI_AREA, ::isMulti) }
        onLogin { timer(AREA_CHECK, 1, soft = true) { updateArea() } }
        // W17: varp `map_flags_cached` bit 2 tells the client this is a PvP world (its range text adds 15).
        onLogin { if (isPvpWorld) setVarp(tp.symbols.Varps.MAP_FLAGS_CACHED, varp(tp.symbols.Varps.MAP_FLAGS_CACHED) or PVP_WORLD_FLAG) }
        events.on<tp.plugin.HitEvent> { hit ->
            val target = hit.target as? PlayerRef ?: return@on
            val source = hit.source as? PlayerRef ?: return@on
            target[LAST_HIT_BY_PLAYER] = source
        }
        events.on<AttackCheck> { check ->
            val attacker = check.attacker as? PlayerRef ?: return@on
            val target = check.target as? PlayerRef ?: return@on
            // Duels are the Duel Arena's call (duel-arena.md).
            if (attacker[SharedAttrs.DUEL_OPPONENT] != null || target[SharedAttrs.DUEL_OPPONENT] != null) return@on
            val a = level(attacker.coord, attacker.isPvpWorld)
            val b = level(target.coord, target.isPvpWorld)
            when {
                // W15: Ferox Enclave is safe; only a teleblocked player there may be attacked, and may hit back.
                feroxDenies(attacker, target) -> {
                    check.deny("You can't attack players in Ferox Enclave.")
                }

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

    private fun feroxDenies(attacker: PlayerRef, target: PlayerRef): Boolean {
        if (attacker.isPvpWorld) return false
        val inFerox = isFerox(attacker.coord) || isFerox(target.coord)
        if (!inFerox) return false

        fun teleblocked(p: PlayerRef) = (p[SharedAttrs.TELEBLOCK_UNTIL] ?: 0L) > p.tick
        if (teleblocked(target)) return false
        // The teleblocked player retaliating against whoever hit them.
        return !(teleblocked(attacker) && attacker[LAST_HIT_BY_PLAYER] == target)
    }

    /** Border crossings run through the area queue (brief §8: "single/multi-way (area queue on borders)"). */
    private fun PlayerRef.updateArea() {
        // W16: WILDERNESS_LEVEL is the real level (teleport limits, death rules); +15 on PvP worlds is for brackets only.
        val wildLevel = realLevel(coord)
        val inWild = level(coord, isPvpWorld) > 0
        if ((this[tp.plugin.SharedAttrs.WILDERNESS_LEVEL] ?: 0) != wildLevel) this[tp.plugin.SharedAttrs.WILDERNESS_LEVEL] = wildLevel
        if (this[IN_WILD] != inWild) {
            this[IN_WILD] = inWild
            this[tp.plugin.SharedAttrs.IN_WILDERNESS] = inWild
            areaQueue {
                playerOp(ATTACK_OP, if (inWild) "Attack" else null, top = true)
                // W17: the client's `inside_wilderness` (K/D panel, RuneLite) and, on PvP worlds, `pvp_area_client`.
                setVarbit(Varbits.INSIDE_WILDERNESS, if (realLevel(coord) > 0) 1 else 0)
                if (isPvpWorld) setVarbit(Varbits.PVP_AREA_CLIENT, if (inWild) 1 else 0)
                // W19: pvp_icons (Wilderness level, attack range, PK skull prevention) is open only where players
                // can fight (OpenRune `WildernessAreaScript`: opened on entry, closed on exit).
                if (inWild) {
                    openOverlay(
                        Components.Toplevel.PVP_ICONS,
                        Interfaces.PVP_ICONS,
                    )
                } else {
                    closeOverlay(Components.Toplevel.PVP_ICONS)
                }
            }
        }
        val combat = combatLevel
        if (this[SENT_COMBAT] != combat) {
            this[SENT_COMBAT] = combat
            // W17: `combatlevel_transmit`, the base of the pvp_icons attack-range text.
            setVarbit(Varbits.COMBATLEVEL_TRANSMIT, combat)
        }
        val inMulti = isMulti(coord)
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

        /** Who last hit this player (set from HitEvent), for Ferox retaliation. */
        private val LAST_HIT_BY_PLAYER = tp.plugin.AttrKey<PlayerRef>("wild_last_hit_by_player")
        private val SENT_COMBAT = tp.plugin.AttrKey<Int>("wild_sent_combat_level")

        /** W15: Ferox Enclave's safe tiles (plane 0 flood fill from the cache; upper floors: its wall ring, OPEN-F1). */
        private val FEROX: Set<Long> =
            (Wilderness::class.java.getResource("ferox-safe.tsv")?.readText() ?: error("ferox-safe.tsv missing"))
                .lineSequence()
                .drop(1)
                .filter { it.isNotBlank() }
                .flatMap { line ->
                    val c = line.split('\t')
                    (c[1].toInt()..c[2].toInt()).asSequence().map { x -> pack(0, x, c[3].toInt()) }
                }.toHashSet()

        @Suppress("MagicNumber") // Ferox wall ring, docs/research/wilderness-zones.md §6
        public fun isFerox(c: CoordGrid): Boolean =
            if (c.level == 0) pack(0, c.x, c.z) in FEROX else c.level <= 2 && c.x in 3123..3155 && c.z in 3617..3646

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
            Lairs.singlesPlusAt(c) ||
                (c.x in REV_MIN_X..REV_MAX_X && c.z in REV_MIN_Z..REV_MAX_Z) ||
                (c.level == 0 && c.x in AGIL_MIN_X..AGIL_MAX_X && c.z in AGIL_MIN_Z..AGIL_MAX_Z) ||
                // OPEN-Z2: the Agility Course's underground pit (the Wiki: "The area is now Singles-plus combat").
                (c.x in AGIL_PIT_MIN_X..AGIL_PIT_MAX_X && c.z in AGIL_PIT_MIN_Z..AGIL_PIT_MAX_Z)

        private const val REV_MIN_X = 3136
        private const val REV_MAX_X = 3263
        private const val REV_MIN_Z = 10048
        private const val REV_MAX_Z = 10239
        private const val AGIL_MIN_X = 2988
        private const val AGIL_MAX_X = 3008
        private const val AGIL_MIN_Z = 3931
        private const val AGIL_MAX_Z = 3966
        private const val AGIL_PIT_MIN_X = 2992
        private const val AGIL_PIT_MAX_X = 3006
        private const val AGIL_PIT_MIN_Z = 10340
        private const val AGIL_PIT_MAX_Z = 10364

        /** Multi-combat polygons (RS Mod `multiway.json`, ISC; data/PROVENANCE.md). */
        public val MULTI: PolygonAreas =
            PolygonAreas.fromJson(
                Wilderness::class.java.getResource("multiway.json")?.readText() ?: error("multiway.json missing"),
            )

        /** PvP worlds: outside the Wilderness counts as level 15 (Wiki "Wilderness"), except in safe zones (W12). */
        private const val PVP_WORLD_LEVEL = 15

        /** Client procs 1138 / 6941: bit 2 of `map_flags_cached` = PvP world. */
        private const val PVP_WORLD_FLAG = 1 shl 2

        /** Player op slot for "Attack" (matches content/combat `Combat.ATTACK_OP`). */
        private const val ATTACK_OP = 2

        /** Wilderness level at [c] (+15 on PvP worlds), 0 outside or in the safe rows. */
        public fun level(c: CoordGrid, pvpWorld: Boolean = false): Int {
            val wild = realLevel(c)
            if (!pvpWorld) return wild
            return if (wild == 0 && isSafeZone(c)) 0 else wild + PVP_WORLD_LEVEL
        }

        /** The Wilderness level by clientscript 384 at [c], without the PvP-world bonus (plugin-api [WildernessArea]). */
        public fun realLevel(c: CoordGrid): Int = tp.plugin.WildernessArea.level(c)

        /** Multi-combat: the RS Mod polygons, the multi boss lairs and the Escape Caves. */
        public fun isMulti(c: CoordGrid): Boolean = c in MULTI || Lairs.multiAt(c)
    }
}
