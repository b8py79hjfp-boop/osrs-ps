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
 * The Archive Guardian, TFA room 1 (docs/mechanics/raids/tfa/archive-guardian.md AG0-AG6). Phase 1 flies behind a
 * shield (the flying HP pool) with rock throws, a step-under slam and mushrooms; breaking the shield (damage or the
 * four Inert Guardian batteries) grounds it for 34 ticks of tendrils; at 33% of its grounded HP it flies again for
 * phase 2 (mushrooms and the laser). Defence falls and Magic rises with damage taken (Strat).
 */
internal object ArchiveGuardian : Boss(Npcs.TFA_GUARDIAN, Npcs.TFA_GUARDIAN_UNSHIELDED) {
    private enum class Phase { SHIELDED, GROUNDED, FINAL }

    private class State(val size: Int) {
        var phase = Phase.SHIELDED
        val groundMax = GROUNDED_HP[index(size)]
        var groundHp = groundMax
        var attacks = 0
        var groundedAt = -1L
        var damage = 0
        var lowestDefence = BASE_DEFENCE
    }

    private val STATE = AttrKey<State>("tfa_guardian")

    /** AG2: the battery only the laser can charge. */
    val LASER_ONLY = AttrKey<Boolean>("tfa_guardian_laser_battery")

    private fun state(npc: NpcRef): State =
        npc[STATE] ?: State(npc[SharedAttrs.RAID_SCALE] ?: MIN_SCALE).also {
            npc[STATE] = it
            setPool(npc, FLYING_HP[index(it.size)])
            batteries(npc)
            // AG0: it targets the first player in orb order (the party leader should start the fight).
            TfaKit.firstInOrbOrder(npc)?.let { first -> NpcCombat.engage(npc, first) }
        }

    /**
     * AG2: four Inert Guardian batteries on their bases (cache: battery bases at x 4114 / 4139, z 5591 / 5604 on every
     * nexus level, i.e. these offsets from the Guardian's south-west tile (4125, 5597)). OPEN-AG8: they are taken to be
     * chargeable (active) whenever the shield is up; the north-east one is the laser-only battery.
     */
    private fun batteries(npc: NpcRef) {
        Bosses.dismissMinions(npc)
        for ((k, offset) in BATTERY_OFFSETS.withIndex()) {
            val at = CoordGrid(npc.coord.x + offset.first, npc.coord.z + offset.second, npc.coord.level)
            val battery = Bosses.summon(npc, Npcs.TFA_GUARDIAN_BATTERY_ACTIVE, at, huntRange = 0)
            if (k == LASER_BATTERY) battery[LASER_ONLY] = true
        }
    }

    private fun index(size: Int) = size.coerceIn(MIN_SCALE, MAX_SCALE) - MIN_SCALE

    /** Gives the NPC a fresh pool of [hp] (the HP bar shows the current pool: shield or grounded HP). */
    private fun setPool(npc: NpcRef, hp: Int) {
        val levels = IntArray(NPC_STATS) { npc.baseLevel(it) }
        levels[NPC_HITPOINTS] = hp
        npc.overrideStats(levels)
        npc.hitpoints = hp
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        when (s.phase) {
            Phase.GROUNDED -> {
                grounded(npc, s)
            }

            Phase.SHIELDED -> {
                s.attacks++
                when {
                    s.attacks % MUSHROOM_EVERY == 0 -> mushrooms(npc)
                    under(npc, victim) -> slam(npc, victim)
                    else -> rock(npc, victim)
                }
                tendrilsBehind(npc, victim)
            }

            Phase.FINAL -> {
                s.attacks++
                if (s.attacks % 2 == 0) mushrooms(npc) else laser(npc)
            }
        }
    }

    // ---- phase 1 attacks (AG1)

    private fun rock(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.NPC_ARCHIVE_GUARDIAN_RANGE_ATTACK01)
        npc.spotAnim(SpotAnims.VFX_GUARDIAN_RANGED_SPOTANIM01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.RANGED)
        val delay = BossCombat.delay(npc, victim, CombatStyle.RANGED)
        BossCombat.projectile(npc, victim, SpotAnims.VFX_GUARDIAN_RANGED_PROJANIM01, delay)
        BossCombat.strike(npc, victim, AttackType.RANGED, MAX_HIT, delay) {
            victim.spotAnim(SpotAnims.VFX_GUARDIAN_RANGED_HITANIM01)
        }
    }

    /**
     * AG1: "tendrils behind it deal typeless damage": players on the far side of the Guardian from its target, within 2
     * tiles of its body, are lashed (OPEN-AG9: 0-10 with each attack).
     */
    private fun tendrilsBehind(npc: NpcRef, victim: EntityRef) {
        val cx = npc.coord.x + npc.size / 2
        val cz = npc.coord.z + npc.size / 2
        val vx = Integer.signum(victim.coord.x - cx)
        val vz = Integer.signum(victim.coord.z - cz)
        for (p in arenaPlayers(npc)) {
            if (p == victim || TfaKit.gap(npc, p) > BEHIND_REACH) continue
            val behind = (vx != 0 && Integer.signum(p.coord.x - cx) == -vx) || (vz != 0 && Integer.signum(p.coord.z - cz) == -vz)
            if (behind) CombatSystem.applyHit(p, npc, BossCombat.roll(npc, 0, BEHIND_MAX), HitStyle.TYPELESS)
        }
    }

    /** AG1: "melee on a player standing underneath". */
    private fun slam(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.NPC_ARCHIVE_GUARDIAN_MELEE_ATTACK01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        BossCombat.strike(npc, victim, AttackType.CRUSH, MAX_HIT, 0)
    }

    private fun under(npc: NpcRef, e: EntityRef): Boolean =
        e.coord.level == npc.coord.level && e.coord.x - npc.coord.x in 0 until npc.size && e.coord.z - npc.coord.z in 0 until npc.size

    // ---- grounded (AG3)

    private fun ground(npc: NpcRef, s: State) {
        s.phase = Phase.GROUNDED
        s.groundedAt = npc.tick
        npc.anim(Seqs.NPC_ARCHIVE_GUARDIAN_IDLE_TO_GROUND01)
        npc.spotAnim(SpotAnims.VFX_GUARDIAN_IDLETOGROUND_SPOTANIM01)
        npc.transform(Npcs.TFA_GUARDIAN_UNSHIELDED, restoreStats = false)
        setPool(npc, s.groundMax)
        npc.hitpoints = s.groundHp
        // AG3: tendrils start 6 ticks after the crash, then every 3 ticks.
        npc.setAttackClock(npc.tick + TENDRIL_DELAY)
    }

    private fun grounded(npc: NpcRef, s: State) {
        // OPEN-AG5: the 34 ticks count from the crash.
        if (npc.tick - s.groundedAt >= GROUNDED_TICKS) {
            fly(npc, s, Phase.SHIELDED)
            return
        }
        for (p in arenaPlayers(npc)) tendril(npc, p)
        npc.setAttackClock(npc.tick + TENDRIL_EVERY)
    }

    /**
     * AG3: a tendril rises on the player's side opposite the boss and hits three times in a 3x3 (moderate melee;
     * Protect from Melee caps it at 11).
     */
    private fun tendril(npc: NpcRef, p: PlayerRef) {
        val dx = Integer.signum(p.coord.x - (npc.coord.x + npc.size / 2))
        val dz = Integer.signum(p.coord.z - (npc.coord.z + npc.size / 2))
        val at = CoordGrid(p.coord.x + dx, p.coord.z + dz, p.coord.level)
        val vine = Bosses.summon(npc, Npcs.ARCHIVE_GUARDIAN_TENTACLE, at, huntRange = 0)
        vine.anim(Seqs.NPC_GUARDIAN_ARCHIVE_TENTACLE_SPAWN01)
        for (i in 1..TENDRIL_HITS) {
            vine.queueHit(i) {
                if (!vine.isInWorld) return@queueHit
                vine.anim(Seqs.NPC_GUARDIAN_ARCHIVE_TENTACLE_ATTACK01)
                for (v in BossCombat.playersOn(npc, at, radius = 1)) {
                    val raw = BossCombat.roll(npc, 0, TENDRIL_MAX)
                    val dmg = if (v.protects(CombatStyle.MELEE)) minOf(raw, TENDRIL_PRAYED_CAP) else raw
                    CombatSystem.applyHit(v, npc, dmg, HitStyle.MELEE)
                }
                if (i == TENDRIL_HITS) vine.despawn()
            }
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
            // AG4: phase 2 fights on with the grounded HP; shield breaks now cost HP.
            npc.spotAnim(SpotAnims.VFX_GUARDIAN_ENRAGED_PHASE_SPOTANIM01)
            setPool(npc, s.groundMax)
            npc.hitpoints = s.groundHp
        }
        npc.setAttackClock(npc.tick + ATTACK_SPEED)
    }

    // ---- phase 2 laser (AG4) and mushrooms (AG5)

    /**
     * AG4: a magic laser at a random player, "fatal with more than 2 players alive unless reduced"; Protect from Magic
     * reduces it slightly. OPEN-AG4: modelled as [LASER_PER_ALIVE] per living player, Protect from Magic -[LASER_PRAYER_PERCENT]%.
     */
    private fun laser(npc: NpcRef) {
        val players = arenaPlayers(npc)
        val target = players.getOrNull(npc.random(maxOf(1, players.size))) ?: return
        npc.anim(Seqs.NPC_ARCHIVE_GUARDIAN_SPECIAL_ATTACK01)
        npc.spotAnim(SpotAnims.VFX_GUARDIAN_SPECIAL_ATTACK_SPOTANIM01)
        CombatSystem.announceAttack(npc, target, CombatStyle.MAGIC)
        var damage = LASER_PER_ALIVE * players.size
        if (target.protects(CombatStyle.MAGIC)) damage = damage * (PERCENT - LASER_PRAYER_PERCENT) / PERCENT
        // AG2: one battery can only be charged by the laser: a target standing by it lures the beam into it, and the
        // battery takes the blast (OPEN-AG4: the player then takes 10).
        val lure =
            Bosses.minions(npc, Npcs.TFA_GUARDIAN_BATTERY_ACTIVE).firstOrNull {
                it[LASER_ONLY] == true &&
                    TfaKit.gap(it, target) <= LURE_RANGE
            }
        if (lure != null) {
            CombatSystem.queueHit(lure, npc, lure.hitpoints, HitStyle.MAGIC, LASER_DELAY)
            damage = LASER_LURED
        }
        CombatSystem.queueHit(target, npc, damage, HitStyle.MAGIC, LASER_DELAY)
    }

    /**
     * AG5: two mushrooms near every player; 5 ticks later each explodes: 10 damage, poison and run off within 1 tile,
     * 3-4 damage within 2 tiles. OPEN-AG6: drowsiness and the prayer drain are modelled as a 2-tick bind and -10 prayer.
     */
    private fun mushrooms(npc: NpcRef) {
        npc.anim(Seqs.NPC_ARCHIVE_GUARDIAN_SWOOSH_ATTACK01)
        val tiles = arenaPlayers(npc).flatMap { p -> List(MUSHROOMS_PER_PLAYER) { BossCombat.randomTile(npc, p.coord, MUSHROOM_SPREAD) } }
        for (t in tiles) npc.addLoc(Locs.TFA_GUARDIAN_MUSHROOM_SPAWN, t, LOC_SHAPE, 0, MUSHROOM_TICKS)
        npc.queueHit(MUSHROOM_TICKS) {
            if (!npc.isInWorld) return@queueHit
            for (t in tiles) {
                npc.addLoc(Locs.TFA_GUARDIAN_MUSHROOM_DESPAWN, t, LOC_SHAPE, 0, MUSHROOM_FADE_TICKS)
                for (p in BossCombat.playersOn(npc, t, radius = MUSHROOM_OUTER)) {
                    val inner = maxOf(kotlin.math.abs(p.coord.x - t.x), kotlin.math.abs(p.coord.z - t.z)) <= MUSHROOM_INNER
                    if (inner) {
                        CombatSystem.applyHit(p, npc, MUSHROOM_INNER_DAMAGE, HitStyle.TYPELESS)
                        if (!Toxic.wearsSerpentineHelm(p)) {
                            Poison.poison(p, MUSHROOM_POISON)
                            p.runMode = false
                            // AG5 / OPEN-AG6: the spores also sap prayer (10) and leave the player drowsy (bound 2 ticks).
                            BossCombat.drainPrayer(p, SPORE_PRAYER)
                            p.frozenUntil = maxOf(p.frozenUntil, p.tick + DROWSY_TICKS)
                        }
                    } else {
                        CombatSystem.applyHit(p, npc, BossCombat.roll(npc, MUSHROOM_OUTER_MIN, MUSHROOM_OUTER_MAX), HitStyle.TYPELESS)
                    }
                }
            }
        }
    }

    // ---- damage

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        if (s.phase == Phase.GROUNDED && npc.hitpoints * THIRDS <= s.groundMax) fly(npc, s, Phase.FINAL)
    }

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        s.damage += damage
        drift(npc, s)
        return damage
    }

    /** Shield gone in phase 1: it crashes (the shield pool reaching 0 does not kill it). */
    override fun onLethal(npc: NpcRef): Boolean {
        val s = state(npc)
        return when (s.phase) {
            Phase.SHIELDED -> {
                ground(npc, s)
                true
            }

            Phase.GROUNDED -> {
                // Grounded damage past a third goes on into phase 2 with 1 HP rather than skipping the phase.
                s.groundHp = 1
                fly(npc, s, Phase.FINAL)
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

    /** AG2: a charged battery breaks about 40% of the shield (OPEN-AG3); in phase 2 it costs HP instead. */
    fun batteryCharged(npc: NpcRef) {
        val s = state(npc)
        val cut =
            when (s.phase) {
                Phase.SHIELDED -> FLYING_HP[index(s.size)] * BATTERY_PERCENT / PERCENT
                Phase.FINAL -> s.groundMax * BATTERY_PERCENT / PERCENT
                Phase.GROUNDED -> 0
            }
        if (cut > 0) CombatSystem.applyHit(npc, null, cut, HitStyle.TYPELESS)
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
    }

    private fun arenaPlayers(npc: NpcRef): List<PlayerRef> = npc.playersWithin(ARENA_RADIUS).filter { !it.isDying }

    private val BATTERY_OFFSETS = listOf(-11 to -6, 14 to -6, -11 to 7, 14 to 7)

    /** archive-guardian.md data: HP by players 1-2 .. 8, flying (shield) and grounded. */
    private val FLYING_HP = intArrayOf(550, 800, 1030, 1260, 1490, 1720, 1950)
    private val GROUNDED_HP = intArrayOf(1100, 1600, 2060, 2520, 2980, 3440, 3910)

    private const val MIN_SCALE = 2
    private const val MAX_SCALE = 8
    private const val NPC_STATS = 6
    private const val NPC_HITPOINTS = 3

    /** Data: max hit 25; attack speed 5. */
    private const val MAX_HIT = 25
    private const val ATTACK_SPEED = 5

    /** AG1: mushrooms "about every four attacks" (OPEN-AG2). */
    private const val MUSHROOM_EVERY = 4

    /** AG3 timings. */
    private const val TENDRIL_DELAY = 6
    private const val TENDRIL_EVERY = 3
    private const val GROUNDED_TICKS = 34
    private const val TENDRIL_HITS = 3

    /** OPEN-AG7: "moderate" tendril damage; Protect from Melee caps it at 11 (AG3). */
    private const val TENDRIL_MAX = 20
    private const val TENDRIL_PRAYED_CAP = 11

    /** OPEN-AG4: the laser model. */
    private const val LASER_PER_ALIVE = 35
    private const val LASER_PRAYER_PERCENT = 20
    private const val LASER_DELAY = 2
    private const val BEHIND_REACH = 2
    private const val BEHIND_MAX = 10
    private const val SPORE_PRAYER = 10
    private const val DROWSY_TICKS = 2
    private const val LASER_LURED = 10
    private const val LURE_RANGE = 2
    private const val LASER_BATTERY = 3

    /** AG5 numbers. */
    private const val MUSHROOMS_PER_PLAYER = 2
    private const val MUSHROOM_SPREAD = 2
    private const val MUSHROOM_TICKS = 5
    private const val MUSHROOM_FADE_TICKS = 2
    private const val MUSHROOM_INNER = 1
    private const val MUSHROOM_OUTER = 2
    private const val MUSHROOM_INNER_DAMAGE = 10
    private const val MUSHROOM_OUTER_MIN = 3
    private const val MUSHROOM_OUTER_MAX = 4

    /** OPEN-AG6: the poison's starting damage (normal poison, 4). */
    private const val MUSHROOM_POISON = 4

    /** Ground decoration shape (centrepiece, 10) for the mushroom locs. */
    private const val LOC_SHAPE = 10

    /** Strat's drift and TFA10's 60% cap. */
    private const val BASE_DEFENCE = 260
    private const val DEFENCE_SWING = 130
    private const val DEFENCE_FLOOR = 156
    private const val BASE_MAGIC = 250
    private const val MAGIC_SWING = 125

    /** AG2 / OPEN-AG3: about 40% per battery. */
    private const val BATTERY_PERCENT = 40
    private const val PERCENT = 100
    private const val THIRDS = 3

    /** The nexus arena around the Guardian. */
    private const val ARENA_RADIUS = 16
}

/** AG2: an active battery; destroying it ("charging") breaks part of its Guardian's shield. */
internal object GuardianBattery : Boss(Npcs.TFA_GUARDIAN_BATTERY_ACTIVE) {
    /** AG2: "one hit only by the laser": players' hits do nothing to it. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int =
        if (npc[ArchiveGuardian.LASER_ONLY] == true && source is PlayerRef) 0 else damage

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        // Batteries never attack (cache: no combat level, only the "Attack" op on them).
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        val owner = npc[Bosses.OWNER] ?: return
        if (owner.isInWorld) ArchiveGuardian.batteryCharged(owner)
    }
}
