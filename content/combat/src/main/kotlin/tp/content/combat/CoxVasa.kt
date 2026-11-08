package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/** Player attr: prayers cannot be switched until this tick (Vasa's teleport stun). */
internal val PRAYER_LOCK_KEY = AttrKey<Long>("prayer_lock_until")

/**
 * Vasa Nistirio, CoX combat room (docs/mechanics/raids/cox/vasa.md VA1-VA5; RuneLite timing: the crystal countdown 67).
 * - VA1: a pile of rocks until a raider comes near; then he opens with the teleport attack.
 * - VA2: teleport: every raider lands next to him (half the group) or at the walls, stunned with prayers off; the blast hits
 *   the raiders next to him for their total HP minus 5 shared between them (Protect from Magic: nothing).
 * - VA3: between teleports, boulders (3 x 3 at the raider's tile at the throw) at every raider within 8; a raider under him
 *   takes up to 8 a tick.
 * - VA4: then he walks to a lit corner crystal and siphons it, invulnerable; the 66-67 tick timer runs while a crystal is
 *   lit; destroying the crystal first negates the heal, a full siphon heals him in full for what he drained and restores
 *   (10% + 1) Defence; then back to the middle and teleport again.
 */
internal object CoxVasa : Boss(Npcs.RAIDS_VASANISTIRIO_DORMANT, Npcs.RAIDS_VASANISTIRIO_WALKING, Npcs.RAIDS_VASANISTIRIO_HEALING) {
    private enum class Phase { DORMANT, TELEPORT, WALKING, SIPHON, RETURN }

    private class State {
        var phase = Phase.DORMANT
        var timer = SIPHON_TICKS
        var phaseTick = 0
        var crystal: NpcRef? = null
        var lastCrystal: NpcRef? = null
        var middle: CoordGrid? = null
        var nextBoulder = 0L
        var crystalsDisabled = 0
    }

    private val STATE = AttrKey<State>("cox_vasa")

    private fun state(npc: NpcRef): State = npc[STATE] ?: State().also { npc[STATE] = it }

    override fun spawned(npc: NpcRef) {
        if (npc.type != Npcs.RAIDS_VASANISTIRIO_DORMANT) return
        npc.huntRange = 0
        npc.anim(Seqs.VASA_DORMANT)
        val s = state(npc)
        s.middle = npc.coord
        CoxKit.loop(npc) { tick(npc, s) }
    }

    override fun retaliates(npc: NpcRef): Boolean = false

    /** VA2 / VA4: not attackable while charging the teleport or siphoning. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val p = state(npc).phase
        return if (p == Phase.DORMANT || p == Phase.TELEPORT || p == Phase.SIPHON) 0 else damage
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        npc.setAttackClock(npc.tick + IDLE)
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        for (c in crystals(npc)) c.despawn()
    }

    private fun tick(npc: NpcRef, s: State) {
        val raiders = CoxKit.raiders(npc)
        s.phaseTick++
        stomp(npc, raiders)
        when (s.phase) {
            Phase.DORMANT -> {
                if (raiders.any { npc.distanceTo(it) <= NOTICE }) {
                    npc.anim(Seqs.VASA_SPAWN)
                    npc.transform(Npcs.RAIDS_VASANISTIRIO_WALKING, restoreStats = false)
                    startTeleport(npc, s)
                }
            }

            Phase.TELEPORT -> {
                if (s.phaseTick == TELEPORT_BLAST) blast(npc, raiders)
                if (s.phaseTick >= TELEPORT_END) chooseCrystal(npc, s)
            }

            Phase.WALKING -> {
                boulders(npc, s, raiders)
                val c = s.crystal
                if (c == null || !c.isInWorld || c.isDying) {
                    crystalLost(npc, s)
                } else if (npc.distanceTo(c) <= 1) {
                    s.phase = Phase.SIPHON
                    s.phaseTick = 0
                    npc.transform(Npcs.RAIDS_VASANISTIRIO_HEALING, restoreStats = false)
                    npc.anim(Seqs.VASA_HEAL_SPAWN)
                } else {
                    npc.walkTo(c.coord)
                }
            }

            Phase.SIPHON -> {
                siphon(npc, s)
            }

            Phase.RETURN -> {
                boulders(npc, s, raiders)
                val mid = s.middle ?: npc.spawn
                if (npc.coord == mid) startTeleport(npc, s) else npc.walkTo(mid)
            }
        }
    }

    private fun startTeleport(npc: NpcRef, s: State) {
        s.phase = Phase.TELEPORT
        s.phaseTick = 0
        npc.anim(Seqs.VASA_STUN_SPAWN)
        val raiders = CoxKit.raiders(npc)
        // VA2: a lone raider lands next to him; a group is split in half (OPEN-VA4: the odd one goes next to him).
        val next = (raiders.size + 1) / 2
        for ((k, p) in raiders.shuffled(npc).withIndex()) {
            val to = if (k < next) BossCombat.randomTile(npc, npc.coord, NEXT_TO) else wall(npc, p)
            p.teleport(to)
            // VA2: stunned and unable to change prayers until the blast (an active Protect from Magic still saves them).
            p.frozenUntil = maxOf(p.frozenUntil, p.tick + TELEPORT_BLAST)
            p[PRAYER_LOCK] = p.tick + TELEPORT_BLAST
            p[NEXT_TO_VASA] = if (k < next) npc.tick else null
        }
    }

    /** VA2: the blast: (total HP of those next to him - 5), shared between them; Protect from Magic takes nothing. */
    private fun blast(npc: NpcRef, raiders: List<PlayerRef>) {
        val near = raiders.filter { npc.distanceTo(it) <= NEXT_TO + 1 }
        if (near.isEmpty()) return
        val total = maxOf(0, near.sumOf { it.hitpoints } - BLAST_SPARE)
        val share = total / near.size
        for (p in near) {
            p.spotAnim(SpotAnims.RAIDS_VASANISTIRIO_MAGIC_IMPACT)
            if (!p.protects(CombatStyle.MAGIC)) BossCombat.areaHit(npc, p, share)
        }
    }

    private fun wall(npc: NpcRef, p: PlayerRef): CoordGrid {
        val f = CoxKit.frame(npc) ?: return p.coord
        // The far edges of the room: the walkable tile nearest a random corner.
        val far = f.size - 1 - WALL_IN
        val corner = f.tile(if (npc.random(2) == 0) WALL_IN else far, if (npc.random(2) == 0) WALL_IN else far)
        return BossCombat.randomTile(npc, corner, WALL_SEARCH)
    }

    /** VA4: a lit crystal other than the last one (unless he just teleported). */
    private fun chooseCrystal(npc: NpcRef, s: State) {
        val all = crystals(npc)
        val pick = CoxKit.pick(npc, all.filter { it != s.lastCrystal }.ifEmpty { all })
        s.crystal = pick
        s.phaseTick = 0
        if (pick == null) {
            // No crystal left: he fights from the middle (teleport again after a boulder cycle).
            s.phase = Phase.RETURN
            return
        }
        s.phase = Phase.WALKING
        pick.spotAnim(SpotAnims.RAIDS_VASANISTIRIO_MAGIC_TRAVEL)
        npc.anim(Seqs.VASA_WALK)
    }

    /** VA4: the timer counts down while a crystal is lit; at zero the siphon is complete. */
    private fun siphon(npc: NpcRef, s: State) {
        val c = s.crystal
        if (c == null || !c.isInWorld || c.isDying) {
            crystalLost(npc, s)
            return
        }
        // Strat: each crystal disabled speeds up the drain (OPEN-VA6: by one tick per tick per crystal).
        s.timer -= 1 + s.crystalsDisabled
        if (s.timer > 0) return
        // 29 Nov 2023: he heals only on a full siphon, in full for the amount siphoned; Strat: (10% + 1) Defence back.
        npc.hitpoints = npc.maxHitpoints
        val base = npc.baseLevel(NpcStat.DEFENCE)
        npc.setLevel(NpcStat.DEFENCE, minOf(base, npc.level(NpcStat.DEFENCE) + base / TEN + 1))
        npc.anim(Seqs.VASA_HEALED)
        s.timer = SIPHON_TICKS
        s.lastCrystal = c
        back(npc, s)
    }

    private fun crystalLost(npc: NpcRef, s: State) {
        s.crystalsDisabled++
        s.lastCrystal = s.crystal
        s.crystal = null
        // Strat: disabled in time, he returns to the middle but picks another crystal at once.
        npc.transform(Npcs.RAIDS_VASANISTIRIO_WALKING, restoreStats = false)
        chooseCrystal(npc, s)
    }

    private fun back(npc: NpcRef, s: State) {
        s.phase = Phase.RETURN
        s.phaseTick = 0
        npc.transform(Npcs.RAIDS_VASANISTIRIO_WALKING, restoreStats = false)
    }

    /** VA3: boulders at every raider within 8, landing on the tile they stood on at the throw (3 x 3). */
    private fun boulders(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        if (npc.tick < s.nextBoulder) return
        s.nextBoulder = npc.tick + ATTACK_SPEED
        val targets = raiders.filter { npc.distanceTo(it) in 1..BOULDER_RANGE }
        if (targets.isEmpty()) return
        npc.anim(Seqs.VASA_ATTACK)
        for (p in targets) {
            val at = p.coord
            val delay = BossCombat.delay(npc, p, CombatStyle.RANGED)
            BossCombat.projectile(npc, p, SpotAnims.RAIDS_VASANISTIRIO_RANGE_TRAVEL, delay)
            npc.queueHit(delay, strong = false) {
                if (!npc.isInWorld) return@queueHit
                for (v in CoxKit.inArea(npc, at)) {
                    val raw = BossCombat.roll(npc, 0, CoxKit.scaledMax(npc, NpcStat.RANGED, BASE_RANGED, BOULDER_MAX))
                    // VA3: Protect from Missiles reduces accuracy and damage (OPEN-VA5: halved).
                    BossCombat.areaHit(npc, v, BossCombat.afterPrayer(v, CombatStyle.RANGED, raw, PrayerRule.HALVES))
                }
            }
        }
    }

    /** VA3: up to 8 a tick to a raider under him. */
    private fun stomp(npc: NpcRef, raiders: List<PlayerRef>) {
        for (p in raiders) if (CoxKit.under(npc, p)) BossCombat.areaHit(npc, p, BossCombat.roll(npc, 1, STOMP_MAX))
    }

    private fun crystals(npc: NpcRef): List<NpcRef> = CoxKit.roomNpcs(npc, setOf(Npcs.RAIDS_VASANISTIRIO_CRYSTAL))

    private fun <T> List<T>.shuffled(npc: NpcRef): List<T> {
        val out = toMutableList()
        for (i in out.indices.reversed()) {
            val j = npc.random(i + 1)
            val t = out[i]
            out[i] = out[j]
            out[j] = t
        }
        return out
    }

    /** Set while a raider may not change prayers (VA2); the prayer buttons honour [PRAYER_LOCK_KEY]. */
    private val PRAYER_LOCK = PRAYER_LOCK_KEY

    /** Set on a raider teleported next to Vasa (the tick), for the blast share. */
    private val NEXT_TO_VASA = AttrKey<Long>("cox_vasa_next")

    /** RuneLite (SP CoxAdditions): the crystal countdown 67 ticks (Page: 40 s, 66 or 67). */
    private const val SIPHON_TICKS = 67

    /** Infobox attack speed 3. */
    private const val ATTACK_SPEED = 3

    /** VA3: Page 8 tiles (Strat 9). */
    private const val BOULDER_RANGE = 8

    /** OPEN-VA5: the boulder damage ("heavy"); the ranged max at a maxed solo. */
    private const val BOULDER_MAX = 40
    private const val BASE_RANGED = 230

    /** VA3: up to 8 a tick under him. */
    private const val STOMP_MAX = 8

    /** VA2: current HP minus 5. */
    private const val BLAST_SPARE = 5

    /** OPEN-VA3 / OPEN-VA4: the trigger range, charge length and landing spots. */
    private const val NOTICE = 8
    private const val TELEPORT_BLAST = 4
    private const val TELEPORT_END = 6
    private const val NEXT_TO = 2
    private const val WALL_IN = 3
    private const val WALL_SEARCH = 3
    private const val IDLE = 10
    private const val TEN = 10
}
