package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * The Archive Guardian, TFA room 1 (docs/mechanics/raids/tfa/archive-guardian.md AG0-AG6; timings from RuneLite
 * (runelite-timings.md), numbers and rotations from the live-game captures (capture-values.md), precedence per D-080).
 * - Flying, shielded: one 5-tick attack clock; four ranged rocks then the mushroom special; a slam on anyone under its
 *   5x5 when an attack comes due. Three batteries (the south-east one is inert) each take 40% of the shield when
 *   destroyed.
 * - Shield at 0: 3 ticks later it crashes (grounded 1,100 HP pool): 35 ticks the first time, 40 later (LU); vines from
 *   the landing +5, then every 3 ticks, one per living raider; under 34% HP it lifts 3 ticks later and enrages.
 * - Enrage: every 20 ticks the blast (one roll of 42-49 split between the raiders, x1.5 off Protect from Magic) and,
 *   5 ticks later, the next battery overloads.
 */
internal object ArchiveGuardian : Boss(Npcs.TFA_GUARDIAN, Npcs.TFA_GUARDIAN_UNSHIELDED) {
    private enum class Phase { SHIELDED, GROUNDED, FINAL }

    private class State(val size: Int) {
        var phase = Phase.SHIELDED
        val groundMax = GROUNDED_HP[index(size)]
        var groundHp = groundMax
        var slots = 0
        var groundedAt = -1L
        var downs = 0
        var damage = 0
        var lowestDefence = BASE_DEFENCE
        var overloads = 0

        /** The 3 ticks between the shield breaking and the crash: it takes no damage (the grounded pool is not up yet). */
        var crashing = false

        /** A lift is queued: the grounded slots stop and a second lift cannot be queued. */
        var lifting = false
        val mushrooms = ArrayList<Mushroom>()
    }

    /** A mushroom (loc 62578 -> 62579 active -> 62580 burst): it stays all fight until it bursts. */
    private class Mushroom(val at: CoordGrid, val activeAt: Long) {
        var armedAt = -1L
    }

    private val STATE = AttrKey<State>("tfa_guardian")

    /** The inert south-east battery's NPC is the INACTIVE type; the other three are active (capture: SE ops 0 all fight). */
    private fun state(npc: NpcRef): State =
        npc[STATE] ?: State(npc[SharedAttrs.RAID_SCALE] ?: MIN_SCALE).also {
            npc[STATE] = it
            setPool(npc, FLYING_HP[index(it.size)])
            batteries(npc)
            mushroomLoop(npc, it)
            // AG0: it targets the first player in orb order (the party leader should start the fight).
            TfaKit.firstInOrbOrder(npc)?.let { first -> NpcCombat.engage(npc, first) }
        }

    private fun index(size: Int) = size.coerceIn(MIN_SCALE, MAX_SCALE) - MIN_SCALE

    /** Gives the NPC a fresh pool of [hp] (the HP bar shows the current pool: shield or grounded HP). */
    private fun setPool(npc: NpcRef, hp: Int) {
        val levels = IntArray(NPC_STATS) { npc.baseLevel(it) }
        levels[NPC_HITPOINTS] = hp
        npc.overrideStats(levels)
        npc.hitpoints = hp
        // A new pool keeps the Defence it was drained to and the Magic it gained (drift).
        npc[STATE]?.let { s ->
            npc.setLevel(NpcStat.DEFENCE, s.lowestDefence)
            npc.setLevel(NpcStat.MAGIC, (BASE_MAGIC + MAGIC_SWING * minOf(1.0, s.damage.toDouble() / s.groundMax)).toInt())
        }
    }

    /**
     * AG2 / capture: the four battery bases (4114 / 4139, 5592 / 5605) as offsets from the Guardian's south-west tile
     * (4125, 5597); the south-east one (4139, 5592) is inert; the others are 180 HP solo (capture; OPEN-AG8: +55 a
     * raider beyond two for the "180..400 by party").
     */
    private fun batteries(npc: NpcRef) {
        Bosses.dismissMinions(npc)
        val s = npc[STATE]
        for ((k, offset) in BATTERY_OFFSETS.withIndex()) {
            // The bases are fixed tiles: from its spawn, wherever it stands now.
            val at = CoordGrid(npc.spawn.x + offset.first, npc.spawn.z + offset.second, npc.spawn.level)
            val type = if (k == INERT_BATTERY) Npcs.TFA_GUARDIAN_BATTERY_INACTIVE else Npcs.TFA_GUARDIAN_BATTERY_ACTIVE
            val battery = Bosses.summon(npc, type, at, huntRange = 0)
            if (type == Npcs.TFA_GUARDIAN_BATTERY_ACTIVE &&
                s != null
            ) {
                TfaKit.setPool(battery, BATTERY_HP + BATTERY_PER_RAIDER * (s.size - MIN_SCALE))
            }
        }
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        when (s.phase) {
            Phase.GROUNDED -> {
                grounded(npc, s)
            }

            Phase.SHIELDED -> {
                s.slots++
                // Capture: the special every 5th slot, four ranged between; a slam when anyone is under its 5x5.
                when {
                    s.slots % SPECIAL_EVERY == 0 -> mushrooms(npc, s)
                    TfaKit.arena(npc).any { TfaKit.under(npc, it.coord) } -> slam(npc)
                    else -> rock(npc, victim)
                }
            }

            Phase.FINAL -> {
                // Between enrage blasts it keeps throwing rocks (OPEN-AG10: the capture records the 20-tick cycle only).
                if (TfaKit.arena(npc).any { TfaKit.under(npc, it.coord) }) slam(npc) else rock(npc, victim)
            }
        }
    }

    // ---- shielded attacks (AG1)

    /**
     * Capture: the rock (projectile 4201, client cycles 48 -> 116) lands 4 ticks after the throw: max 41 solo unprayed,
     * Protect from Missiles takes 75% off (max 10). OPEN-AG11: in parties the max grows (44..54 at 4-5): +5 a raider
     * beyond three.
     */
    private fun rock(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.NPC_ARCHIVE_GUARDIAN_RANGE_ATTACK01)
        npc.spotAnim(SpotAnims.VFX_GUARDIAN_RANGED_SPOTANIM01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.RANGED)
        BossCombat.projectile(npc, victim, SpotAnims.VFX_GUARDIAN_RANGED_PROJANIM01, ROCK_FLIGHT)
        val max = ROCK_MAX + ROCK_PER_RAIDER * maxOf(0, TfaKit.arena(npc).size - ROCK_SCALE_FROM)
        npc.queueHit(ROCK_FLIGHT) {
            if (!npc.isInWorld || victim !is PlayerRef || victim.isDying) return@queueHit
            val raw = if (BossCombat.rolls(npc, victim, AttackType.RANGED)) BossCombat.roll(npc, 0, max) else 0
            val dmg = if (victim.protects(CombatStyle.RANGED)) minOf(ROCK_PRAYED_MAX, raw * PRAYED_KEEP / PERCENT) else raw
            victim.spotAnim(SpotAnims.VFX_GUARDIAN_RANGED_HITANIM01)
            CombatSystem.applyHit(victim, npc, dmg, HitStyle.RANGED)
        }
    }

    /** Capture: the slam (seq 14752) lands 1 tick later on whoever is still under its 5x5 (0..48; low confidence). */
    private fun slam(npc: NpcRef) {
        npc.anim(Seqs.NPC_ARCHIVE_GUARDIAN_MELEE_ATTACK01)
        npc.queueHit(1) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            for (p in TfaKit.arena(
                npc,
            )) {
                if (TfaKit.under(npc, p.coord)) CombatSystem.applyHit(p, npc, BossCombat.roll(npc, 0, SLAM_MAX), HitStyle.MELEE)
            }
        }
    }

    // ---- mushrooms (AG5)

    /**
     * Capture / RuneLite: the special (seq 14754, shared with the enrage blast) puts two mushrooms by every raider: one
     * on a tile beside where they stood the tick before, one exactly 3 tiles out; never on a wall or a live mushroom; at
     * most 39 alive; they stay all fight. Each is active 3 ticks after it appears (loc 62578 -> 62579).
     */
    private fun mushrooms(npc: NpcRef, s: State) {
        npc.anim(Seqs.NPC_ARCHIVE_GUARDIAN_ENRAGED_PHASE01)
        npc.queueHit(MUSHROOM_APPEAR) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            for (p in TfaKit.arena(npc)) {
                for (reach in intArrayOf(1, MUSHROOM_FAR)) {
                    if (s.mushrooms.size >= MAX_MUSHROOMS) return@queueHit
                    val (dx, dz) = DIRS[npc.random(DIRS.size)]
                    val at = CoordGrid(p.coord.x + dx * reach, p.coord.z + dz * reach, p.coord.level)
                    if (!npc.isWalkable(at) || s.mushrooms.any { it.at == at }) continue
                    s.mushrooms += Mushroom(at, npc.tick + MUSHROOM_ARM)
                    npc.addLoc(Locs.TFA_GUARDIAN_MUSHROOM_SPAWN, at, LOC_SHAPE, 0, MUSHROOM_ARM)
                    npc.queueHit(MUSHROOM_ARM) {
                        if (npc.isInWorld &&
                            !npc.isDying
                        ) {
                            npc.addLoc(Locs.TFA_GUARDIAN_MUSHROOM_ACTIVE, at, LOC_SHAPE, 0)
                        }
                    }
                }
            }
        }
    }

    /**
     * Capture: a mushroom bursts on T+1, where T is the first tick at or after active + 1 that a raider stands within 1
     * of it; raiders still within 1 at the burst are poisoned (10, decaying every 30 ticks) and "too drowsy to run" for
     * 17 ticks. A serpentine helm stops both (AG5).
     */
    private fun mushroomLoop(npc: NpcRef, s: State) {
        npc.queueHit(1, strong = false) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            val players = TfaKit.arena(npc)
            for (m in s.mushrooms.toList()) {
                if (npc.tick < m.activeAt + 1) continue
                if (m.armedAt < 0 && players.any { near(it.coord, m.at) }) {
                    m.armedAt = npc.tick
                } else if (m.armedAt >= 0) {
                    s.mushrooms.remove(m)
                    npc.addLoc(Locs.TFA_GUARDIAN_MUSHROOM_DESPAWN, m.at, LOC_SHAPE, 0, MUSHROOM_FADE_TICKS)
                    for (p in players) if (near(p.coord, m.at) && !Toxic.wearsSerpentineHelm(p)) spores(p)
                }
            }
            mushroomLoop(npc, s)
        }
    }

    private fun near(a: CoordGrid, b: CoordGrid) = a.level == b.level && kotlin.math.abs(a.x - b.x) <= 1 && kotlin.math.abs(a.z - b.z) <= 1

    private fun spores(p: PlayerRef) {
        Poison.poison(p, MUSHROOM_POISON)
        p.message("You're too drowsy to run!")
        for (t in 0 until DROWSY_TICKS) p.queueHit(t, strong = false) { if (p.runMode) p.runMode = false }
    }

    // ---- grounded (AG3)

    private fun ground(npc: NpcRef, s: State) {
        s.phase = Phase.GROUNDED
        s.downs++
        s.crashing = true
        npc.queueHit(CRASH_DELAY) {
            s.crashing = false
            if (!npc.isInWorld || npc.isDying) return@queueHit
            s.groundedAt = npc.tick
            npc.anim(Seqs.NPC_ARCHIVE_GUARDIAN_IDLE_TO_GROUND01)
            npc.spotAnim(SpotAnims.VFX_GUARDIAN_IDLETOGROUND_SPOTANIM01)
            npc.transform(Npcs.TFA_GUARDIAN_UNSHIELDED, restoreStats = false)
            setPool(npc, s.groundMax)
            npc.hitpoints = s.groundHp
            // Capture: a raider 1 tile from its 5x5 as it lands is smashed (24..28).
            for (p in arenaPlayers(npc)) {
                if (TfaKit.gap(npc, p) ==
                    1
                ) {
                    CombatSystem.applyHit(p, npc, BossCombat.roll(npc, SMASH_MIN, SMASH_MAX), HitStyle.MELEE)
                }
            }
            npc.setAttackClock(npc.tick + VINE_FIRST)
        }
        npc.setAttackClock(npc.tick + CRASH_DELAY + VINE_FIRST)
    }

    /** A lift 3 ticks from now, unless it has left the ground another way by then (its grounded pool emptied). */
    private fun lift(npc: NpcRef, s: State, next: Phase) {
        s.lifting = true
        npc.queueHit(LIFT_DELAY) {
            s.lifting = false
            if (npc.isInWorld && !npc.isDying && s.phase == Phase.GROUNDED) fly(npc, s, next)
        }
        npc.setAttackClock(npc.tick + LIFT_DELAY + 1)
    }

    private fun grounded(npc: NpcRef, s: State) {
        val duration = if (s.downs == 1) FIRST_GROUNDED_TICKS else LATER_GROUNDED_TICKS
        // Capture: under 34% HP a slot has no wave and it lifts 3 ticks later, enraged.
        if (s.lifting) return
        if (npc.hitpoints * PERCENT < s.groundMax * EARLY_LIFT_PERCENT) {
            lift(npc, s, Phase.FINAL)
            return
        }
        if (npc.tick - s.groundedAt >= duration) {
            lift(npc, s, Phase.SHIELDED)
            return
        }
        for (p in arenaPlayers(npc)) vine(npc, p)
        npc.setAttackClock(npc.tick + VINE_EVERY)
    }

    /**
     * Capture: one vine per living raider each wave, on the tile one step past the raider away from its middle (else
     * toward it, else the raider's tile); it attacks 3, 6 and 9 ticks after it appears and is gone at +12. Each beat
     * hits every living raider exactly 1 tile from a vine, once however many reach: melee that never misses, 17-19, or 10
     * under Protect from Melee.
     */
    private fun vine(npc: NpcRef, p: PlayerRef) {
        val dx = Integer.signum(p.coord.x - (npc.coord.x + npc.size / 2))
        val dz = Integer.signum(p.coord.z - (npc.coord.z + npc.size / 2))
        val away = CoordGrid(p.coord.x + dx, p.coord.z + dz, p.coord.level)
        val toward = CoordGrid(p.coord.x - dx, p.coord.z - dz, p.coord.level)
        val at =
            if (npc.isWalkable(away)) {
                away
            } else if (npc.isWalkable(toward)) {
                toward
            } else {
                p.coord
            }
        val vine = Bosses.summon(npc, Npcs.ARCHIVE_GUARDIAN_TENTACLE, at, huntRange = 0)
        vine.anim(Seqs.NPC_GUARDIAN_ARCHIVE_TENTACLE_SPAWN01)
        for (beat in 1..VINE_BEATS) {
            vine.queueHit(beat * VINE_BEAT_GAP) {
                if (!vine.isInWorld || !npc.isInWorld) return@queueHit
                vine.anim(Seqs.NPC_GUARDIAN_ARCHIVE_TENTACLE_ATTACK01)
                for (v in arenaPlayers(npc)) {
                    if (v.coord.level != at.level ||
                        maxOf(kotlin.math.abs(v.coord.x - at.x), kotlin.math.abs(v.coord.z - at.z)) != 1
                    ) {
                        continue
                    }
                    if (v[VINE_HIT_AT] == npc.tick) continue
                    v[VINE_HIT_AT] = npc.tick
                    val dmg = if (v.protects(CombatStyle.MELEE)) VINE_PRAYED else BossCombat.roll(npc, VINE_MIN, VINE_MAX)
                    CombatSystem.applyHit(v, npc, dmg, HitStyle.MELEE)
                }
            }
        }
        vine.queueHit(VINE_GONE) {
            vine.anim(Seqs.NPC_GUARDIAN_ARCHIVE_TENTACLE_DESPAWN01)
            vine.despawn()
        }
    }

    private fun fly(npc: NpcRef, s: State, next: Phase) {
        s.groundHp = npc.hitpoints
        s.phase = next
        npc.anim(Seqs.NPC_ARCHIVE_GUARDIAN_GROUND_TO_FLYING01)
        npc.spotAnim(SpotAnims.VFX_GUARDIAN_GROUNDTOIDLE_SPOTANIM01)
        npc.transform(Npcs.TFA_GUARDIAN, restoreStats = false)
        if (next == Phase.SHIELDED) {
            setPool(npc, FLYING_HP[index(s.size)])
            batteries(npc)
        } else {
            // AG4: phase 2 fights on with the grounded HP; the enrage cycle starts.
            npc.spotAnim(SpotAnims.VFX_GUARDIAN_ENRAGED_PHASE_SPOTANIM01)
            setPool(npc, s.groundMax)
            npc.hitpoints = s.groundHp
            enrageCycle(npc, s)
        }
        npc.setAttackClock(npc.tick + ATTACK_SPEED)
    }

    // ---- enrage (AG4)

    /**
     * Capture: every 20 ticks S: at S+5 the blast (seq 14754): one roll of 42-49 divided between the raiders in the room,
     * x1.5 for anyone off Protect from Magic; at S+10 the next battery overloads (its HP as one hit), in the order
     * (4139, 5592), (4139, 5605), (4114, 5592), (4114, 5605). RuneLite (LU): the blast lands 10 ticks after the animation
     * (the capture's S+5 is the animation; it lands at the overload beat).
     */
    private fun enrageCycle(npc: NpcRef, s: State) {
        npc.queueHit(BLAST_AT) {
            if (!npc.isInWorld || s.phase != Phase.FINAL) return@queueHit
            npc.anim(Seqs.NPC_ARCHIVE_GUARDIAN_ENRAGED_PHASE01)
            npc.spotAnim(SpotAnims.VFX_GUARDIAN_SPECIAL_ATTACK_SPOTANIM01)
            val players = arenaPlayers(npc)
            val share = BossCombat.roll(npc, BLAST_MIN, BLAST_MAX) / maxOf(1, players.size)
            for (p in players) {
                val dmg = if (p.protects(CombatStyle.MAGIC)) share else share * UNPRAYED_NUM / UNPRAYED_DEN
                CombatSystem.queueHit(p, npc, dmg, HitStyle.MAGIC, LASER_DELAY)
            }
        }
        npc.queueHit(OVERLOAD_AT) {
            if (!npc.isInWorld || s.phase != Phase.FINAL) return@queueHit
            val (dx, dz) = OVERLOAD_ORDER.getOrNull(s.overloads++) ?: return@queueHit
            val at = CoordGrid(npc.spawn.x + dx, npc.spawn.z + dz, npc.coord.level)
            Bosses.minions(npc).firstOrNull { it.coord == at }?.let { CombatSystem.applyHit(it, npc, it.hitpoints, HitStyle.TYPELESS) }
        }
        npc.queueHit(ENRAGE_CYCLE) { if (npc.isInWorld && s.phase == Phase.FINAL) enrageCycle(npc, s) }
    }

    // ---- damage

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        state(npc)
    }

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        if (s.crashing) return 0
        s.damage += damage
        drift(npc, s)
        return damage
    }

    /** Shield gone: it crashes 3 ticks later (the shield pool reaching 0 does not kill it). */
    override fun onLethal(npc: NpcRef): Boolean {
        val s = state(npc)
        return when (s.phase) {
            Phase.SHIELDED -> {
                npc.hitpoints = 1
                ground(npc, s)
                true
            }

            Phase.GROUNDED -> {
                npc.hitpoints = 1
                if (!s.crashing) fly(npc, s, Phase.FINAL)
                true
            }

            Phase.FINAL -> {
                false
            }
        }
    }

    /**
     * Strat: Defence = 260 - 130 x damage / grounded base HP and Magic = 250 + 125 x damage / grounded base HP; drains
     * keep the lower defence and the cap (tfa.md TFA10) floors it at 40% (156).
     */
    private fun drift(npc: NpcRef, s: State) {
        val frac = minOf(1.0, s.damage.toDouble() / s.groundMax)
        val formula = (BASE_DEFENCE - DEFENCE_SWING * frac).toInt()
        val current = npc.level(NpcStat.DEFENCE)
        s.lowestDefence = maxOf(DEFENCE_FLOOR, minOf(s.lowestDefence, formula, current))
        npc.setLevel(NpcStat.DEFENCE, s.lowestDefence)
        npc.setLevel(NpcStat.MAGIC, (BASE_MAGIC + MAGIC_SWING * frac).toInt())
    }

    /** Capture: a destroyed battery takes 40% of the shield's max, capped at what is left, 3 ticks later. */
    fun batteryCharged(npc: NpcRef) {
        val s = state(npc)
        if (s.phase != Phase.SHIELDED) return
        val cut = FLYING_HP[index(s.size)] * BATTERY_PERCENT / PERCENT
        npc.queueHit(BATTERY_DELAY) {
            if (npc.isInWorld && s.phase == Phase.SHIELDED) CombatSystem.applyHit(npc, null, minOf(cut, npc.hitpoints), HitStyle.TYPELESS)
        }
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
        npc[STATE]?.mushrooms?.clear()
    }

    private fun arenaPlayers(npc: NpcRef): List<PlayerRef> = npc.playersWithin(ARENA_RADIUS).filter { !it.isDying }

    private val VINE_HIT_AT = AttrKey<Long>("tfa_guardian_vine_hit")

    /** Capture: battery bases (4114/4139, 5592/5605) from the Guardian's south-west tile (4125, 5597); SE first. */
    private val BATTERY_OFFSETS = listOf(14 to -5, 14 to 8, -11 to -5, -11 to 8)
    private const val INERT_BATTERY = 0

    /** Capture: the enrage overload order, (4139,5592), (4139,5605), (4114,5592), (4114,5605). */
    private val OVERLOAD_ORDER = listOf(14 to -5, 14 to 8, -11 to -5, -11 to 8)
    private val DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to -1, 1 to -1, -1 to 1)

    /** archive-guardian.md data (capture-confirmed): HP by players 1-2 .. 8, flying (shield) and grounded. */
    private val FLYING_HP = intArrayOf(550, 800, 1030, 1260, 1490, 1720, 1950)
    private val GROUNDED_HP = intArrayOf(1100, 1600, 2060, 2520, 2980, 3440, 3910)

    private const val MIN_SCALE = 2
    private const val MAX_SCALE = 8
    private const val NPC_STATS = 6
    private const val NPC_HITPOINTS = 3
    private const val ATTACK_SPEED = 5
    private const val SPECIAL_EVERY = 5
    private const val ROCK_FLIGHT = 4
    private const val ROCK_MAX = 41
    private const val ROCK_PER_RAIDER = 5
    private const val ROCK_SCALE_FROM = 3
    private const val ROCK_PRAYED_MAX = 10
    private const val PRAYED_KEEP = 25
    private const val SLAM_MAX = 48
    private const val SMASH_MIN = 24
    private const val SMASH_MAX = 28
    private const val MUSHROOM_APPEAR = 1
    private const val MUSHROOM_FAR = 3
    private const val MAX_MUSHROOMS = 39
    private const val MUSHROOM_ARM = 3
    private const val MUSHROOM_FADE_TICKS = 2
    private const val MUSHROOM_POISON = 10
    private const val DROWSY_TICKS = 17
    private const val LOC_SHAPE = 10
    private const val BATTERY_HP = 180
    private const val BATTERY_PER_RAIDER = 55
    private const val BATTERY_PERCENT = 40
    private const val BATTERY_DELAY = 3
    private const val CRASH_DELAY = 3

    /** RuneLite (LU ArchiveGuardian.java, "set in game"): grounded 35 ticks the first time, 40 later (D-080). */
    private const val FIRST_GROUNDED_TICKS = 35
    private const val LATER_GROUNDED_TICKS = 40
    private const val VINE_FIRST = 5
    private const val VINE_EVERY = 3
    private const val VINE_BEATS = 3
    private const val VINE_BEAT_GAP = 3
    private const val VINE_GONE = 12
    private const val VINE_MIN = 17
    private const val VINE_MAX = 19
    private const val VINE_PRAYED = 10
    private const val LIFT_DELAY = 3
    private const val EARLY_LIFT_PERCENT = 34
    private const val ENRAGE_CYCLE = 20
    private const val BLAST_AT = 5
    private const val OVERLOAD_AT = 10
    private const val BLAST_MIN = 42
    private const val BLAST_MAX = 49
    private const val UNPRAYED_NUM = 3
    private const val UNPRAYED_DEN = 2
    private const val LASER_DELAY = 5
    private const val BASE_DEFENCE = 260
    private const val DEFENCE_SWING = 130
    private const val DEFENCE_FLOOR = 156
    private const val BASE_MAGIC = 250
    private const val MAGIC_SWING = 125
    private const val PERCENT = 100
    private const val ARENA_RADIUS = 16
}

/** AG2: an active battery; destroying it ("charging") breaks part of its Guardian's shield. */
internal object GuardianBattery : Boss(Npcs.TFA_GUARDIAN_BATTERY_ACTIVE) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        // Batteries never attack (cache: no combat level, only the "Attack" op on them).
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        val owner = npc[Bosses.OWNER] ?: return
        if (owner.isInWorld) ArchiveGuardian.batteryCharged(owner)
    }
}
