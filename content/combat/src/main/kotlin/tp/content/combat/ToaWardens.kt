package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.RaidRoomFrame
import tp.plugin.SharedAttrs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * The Wardens (docs/mechanics/raids/toa/wardens.md WD1-WD51). Phases 1-2 in the obelisk room (region 15184, plane 1,
 * arena x 3797-3819, z 5144-5166; cache collision), phase 3 in the warped room (region 15696, plane 0; the Warden at the
 * south apex, the arena fanning north). The fight starts from raids-toa once every member has spoken to Osmumten
 * ("Begin", WD1), which spawns the active obelisk (11751).
 * - P1 (WD8-WD15): the obelisk (260 scaled) feeds both Wardens along the west / east troughs; a player on a trough absorbs
 *   orbs (3 damage each) and cuts that Warden's charge; a charged Warden alternates UFOs and a Charged Shot; at the
 *   obelisk's death the more-disrupted Warden falls and the other powers up.
 * - P2 (WD16-WD30): the Warden's 140 HP is its shield (100% accurate melee); a full bar ejects the core (21 / 29 / 37
 *   ticks by HP), hits on the core count five times on the Warden's real health (4,500 scaled, WD27 reading); Divine
 *   Projectile above 55%, Imprisonment below; the obelisk's Converging Beam / Windmill / Lightning Skull set; at 0 the
 *   other Warden is restored.
 * - P3 (WD31-WD46): typeless slams right, left, both (6 ticks, Overclocked 5 / 4, Insanity 3); siphons at 80 / 60 / 40
 *   / 20% (melee only, HP by players alive); phantoms in batches 2-3; below 5% the enrage: 20% heal, Defence 180,
 *   lightning, the floor rows torn away.
 */
@Suppress("LargeClass") // one fight in three phases sharing one state; split by phase would scatter the shared transitions
internal object ToaWardens : Boss(
    Npcs.TOA_WARDENS_P1_OBELISK_NPC,
    Npcs.TOA_WARDENS_P2_OBELISK_NPC,
    Npcs.TOA_WARDEN_ELIDINIS_PHASE2_MAGE,
    Npcs.TOA_WARDEN_ELIDINIS_PHASE2_RANGE,
    Npcs.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED,
    Npcs.TOA_WARDEN_TUMEKEN_PHASE2_MAGE,
    Npcs.TOA_WARDEN_TUMEKEN_PHASE2_RANGE,
    Npcs.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED,
    Npcs.TOA_WARDEN_ELIDINIS_PHASE3,
    Npcs.TOA_WARDEN_TUMEKEN_PHASE3,
    Npcs.TOA_WARDEN_ELIDINIS_PHASE3_CHARGING,
    Npcs.TOA_WARDEN_TUMEKEN_PHASE3_CHARGING,
    Npcs.TOA_WARDEN_ELIDINIS_PHASE1,
    Npcs.TOA_WARDEN_TUMEKEN_PHASE1,
    Npcs.TOA_WARDEN_ELIDINIS_PHASE3_INACTIVE,
    Npcs.TOA_WARDEN_TUMEKEN_PHASE3_INACTIVE,
) {
    private enum class Phase { ONE, TWO, THREE }

    /** The whole fight's state, kept on the obelisk (P1 / P2) and handed to the phase 3 Warden. */
    private class State {
        var phase = Phase.ONE
        lateinit var obelisk: NpcRef
        var elidinis: NpcRef? = null
        var tumeken: NpcRef? = null
        var fought: NpcRef? = null
        var foughtElidinis = true
        val energy = IntArray(2)
        val disrupted = IntArray(2)
        val discharges = IntArray(2)
        var orbTick = 0

        // phase 2
        var realHp = 0
        var realMax = 0
        var exposedFor = 0
        var core: NpcRef? = null
        var attacks = 0
        var obeliskSet = 0
        var obeliskTimer = 0
        var mageForm = true

        // phase 3
        var p3: NpcRef? = null
        var p3Frame: RaidRoomFrame? = null
        var slam = 0
        var slamTimer = 0
        var siphonAt = 0
        var siphonsLeft = -1
        var siphonBatch = 0
        var enraged = false
        var rowsGone = 0
        var rowTimer = 0
        var lightningTimer = 0
        var phantomTimer = 0
        var phantom: NpcRef? = null
    }

    private val STATE = AttrKey<State>("toa_wardens")

    private fun state(npc: NpcRef): State? = npc[STATE] ?: npc[Bosses.OWNER]?.get(STATE)

    // ---- phase 1

    override fun spawned(npc: NpcRef) {
        if (npc.type != Npcs.TOA_WARDENS_P1_OBELISK_NPC || npc[STATE] != null) return
        val s = State()
        s.obelisk = npc
        npc[STATE] = s
        npc.frozenUntil = Long.MAX_VALUE
        s.elidinis = Bosses.summon(npc, Npcs.TOA_WARDEN_ELIDINIS_PHASE1, ToaKit.at(npc, ELIDINIS_P1), huntRange = 0, inheritRun = true)
        s.tumeken = Bosses.summon(npc, Npcs.TOA_WARDEN_TUMEKEN_PHASE1, ToaKit.at(npc, TUMEKEN_P1), huntRange = 0, inheritRun = true)
        for (w in listOfNotNull(s.elidinis, s.tumeken)) {
            w[STATE] = s
            w.frozenUntil = Long.MAX_VALUE
        }
        for (p in ToaKit.raiders(npc)) p.message("Challenge started: The Wardens.")
        ToaKit.loop(npc) { tick(npc, s) }
    }

    override fun retaliates(npc: NpcRef): Boolean = false

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc) ?: return
        when (s.phase) {
            Phase.TWO -> if (npc == s.fought && s.core == null) p2Attack(npc, s, victim, distance)
            else -> Unit
        }
    }

    /** P2 shield: every hit lands (WD22, "100% accuracy"); the core: melee only (WD24). */
    override fun meleeAlwaysHits(npc: NpcRef): Boolean = npc.type in P2_FORMS

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc) ?: return damage
        return when {
            npc.type in P3_CHARGING -> 0

            npc.type == Npcs.TOA_WARDENS_P2_OBELISK_NPC -> 0

            npc.type in EXPOSED -> 0

            // WD8: the charging phase 1 Wardens are not attackable; WD15: nor is the fallen one (no ops).
            npc.type in INERT -> 0

            else -> damage
        }.also { if (s.phase == Phase.ONE && npc != s.obelisk) return 0 }
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc) ?: return
        when {
            npc == s.obelisk && s.phase == Phase.ONE -> {
                floorDefence(npc, OBELISK_DRAIN)
            }

            npc.type in P3_FORMS -> {
                floorDefence(npc, if (s.enraged) P3_DRAIN_ENRAGED else P3_DRAIN)
                p3Thresholds(npc, s)
            }
        }
    }

    private fun floorDefence(npc: NpcRef, cap: Int) {
        val floor = npc.baseLevel(NpcStat.DEFENCE) - cap
        if (npc.level(NpcStat.DEFENCE) < floor) npc.setLevel(NpcStat.DEFENCE, floor)
    }

    override fun onLethal(npc: NpcRef): Boolean {
        val s = state(npc) ?: return false
        return when {
            npc == s.obelisk && s.phase == Phase.ONE -> {
                endPhaseOne(npc, s)
                true
            }

            // P2: the shield is spent -> the core comes out (WD23).
            npc.type in P2_FORMS -> {
                eject(npc, s)
                true
            }

            // WD43: below 5% the enrage heals 20% once (the 0-HP hit before it counts as reaching it).
            npc.type in P3_FORMS && !s.enraged -> {
                enrage(npc, s)
                true
            }

            else -> {
                false
            }
        }
    }

    private fun tick(npc: NpcRef, s: State) {
        when (s.phase) {
            Phase.ONE -> phaseOne(npc, s)
            Phase.TWO -> phaseTwo(npc, s)
            Phase.THREE -> s.p3?.let { phaseThree(it, s) }
        }
    }

    /**
     * WD8-WD12 (OPEN-WD2 energy model): each Warden gains 1 energy a tick (2 with Ancient Haste); an orb every 2 ticks
     * on each trough; a player on a trough absorbs it (3 damage) and that Warden's gain is cut to a third.
     */
    private fun phaseOne(npc: NpcRef, s: State) {
        val haste = if (ToaKit.has(npc, ANCIENT_HASTE)) 2 else 1
        val orb = ++s.orbTick % ORB_EVERY == 0
        for (side in 0..1) {
            val blockers = ToaKit.raiders(npc).filter { onTrough(npc, it, side) }
            if (orb && blockers.isNotEmpty()) {
                for (p in blockers) ToaKit.areaHit(npc, p, ORB_ABSORB)
                s.disrupted[side]++
            }
            s.energy[side] += if (blockers.isEmpty()) haste * ENERGY_SCALE else haste
            if (s.energy[side] >= CHARGED * ENERGY_SCALE) {
                s.energy[side] = 0
                discharge(npc, s, side)
            }
        }
    }

    /** West trough feeds Elidinis (side 0), east Tumeken (side 1) (WD9; troughs along z 5154 between them and the obelisk). */
    private fun onTrough(npc: NpcRef, p: PlayerRef, side: Int): Boolean {
        val t = ToaKit.template(npc, p.coord)
        if (t.z !in TROUGH_Z - 1..TROUGH_Z + 1) return false
        return if (side == 0) t.x in ARENA_MIN_X..OBELISK_X - 1 else t.x in OBELISK_X + OBELISK_SIZE..ARENA_MAX_X
    }

    /** WD11: UFOs first, then Charged Shot, alternating. */
    private fun discharge(npc: NpcRef, s: State, side: Int) {
        val warden = (if (side == 0) s.elidinis else s.tumeken) ?: return
        warden.anim(Seqs.NPC_WARDENS_RELEASE01)
        if (s.discharges[side]++ % 2 == 0) ufos(npc) else chargedShot(npc, side == 0)
    }

    /** WD13 (OPEN-WD4): pyramids over random tiles; their 3x3 hurts one tick after the animation shows. */
    private fun ufos(npc: NpcRef) {
        repeat(UFO_COUNT) {
            val at = BossCombat.randomTile(npc, ToaKit.at(npc, ARENA_CENTRE), ARENA_RADIUS)
            npc.addLoc(if (it % 2 == 0) Locs.TOA_WARDENS_PYRAMID01 else Locs.TOA_WARDENS_PYRAMID02, at, CENTREPIECE, 0, UFO_LIFE)
            npc.queueHit(UFO_DELAY, strong = false) {
                if (!npc.isInWorld) return@queueHit
                for (p in ToaKit.on(npc, at, 1)) ToaKit.hitRolled(npc, p, UFO_MAX, pathScaled = false)
            }
        }
    }

    /**
     * WD14: Elidinis' small blue ball splashes onto players next to its target (stand apart; capped 30, 45 solo);
     * Tumeken's large orange ball is split between players on the target tile (stand together; about 7 when shared, 30
     * solo). OPEN-WD3: 2237 Elidinis, 2238 Tumeken. Lands 5 ticks later (3 s).
     */
    private fun chargedShot(npc: NpcRef, elidinis: Boolean) {
        val raiders = ToaKit.raiders(npc)
        val target = raiders.randomPick(npc) ?: return
        for (p in raiders) p.message("A large ball of energy is shot your way...")
        BossCombat.projectile(npc, target, if (elidinis) SHOT_ELIDINIS else SHOT_TUMEKEN, SHOT_DELAY)
        val tile = target.coord
        val solo = raiders.size <= 1
        npc.queueHit(SHOT_DELAY, strong = false) {
            if (!npc.isInWorld) return@queueHit
            if (elidinis) {
                val cap = if (solo) ELIDINIS_SOLO_CAP else ELIDINIS_CAP
                for (p in ToaKit.on(npc, tile, 1)) {
                    val clipped = ToaKit.on(npc, p.coord, 1).size - 1
                    ToaKit.areaHit(npc, p, minOf(cap, ToaKit.dmg(npc, ELIDINIS_BASE, pathScaled = false) * (1 + clipped)))
                }
            } else {
                val on = ToaKit.on(npc, tile)
                val cap = if (solo) TUMEKEN_SOLO_CAP else TUMEKEN_CAP
                val total = ToaKit.dmg(npc, TUMEKEN_BASE, pathScaled = false)
                for (p in on) ToaKit.areaHit(npc, p, minOf(if (on.size > 1) cap else TUMEKEN_SOLO_CAP.coerceAtLeast(cap), total / on.size))
            }
        }
    }

    /** WD15: the more-disrupted Warden falls (OPEN-WD1 tie: Tumeken falls), the other powers up as phase 2. */
    private fun endPhaseOne(npc: NpcRef, s: State) {
        npc.anim(Seqs.FX_OBELISK_WARDENS02_DEATH)
        val tumekenFalls = s.disrupted[1] >= s.disrupted[0]
        s.foughtElidinis = tumekenFalls
        val falling = if (tumekenFalls) s.tumeken else s.elidinis
        val rising = if (tumekenFalls) s.elidinis else s.tumeken
        val fallen = if (tumekenFalls) Npcs.TOA_WARDEN_TUMEKEN_PHASE3_INACTIVE else Npcs.TOA_WARDEN_ELIDINIS_PHASE3_INACTIVE
        falling?.transform(fallen, restoreStats = false)
        for (p in ToaKit.raiders(npc)) {
            p.message(if (tumekenFalls) TUMEKEN_FALLS else ELIDINIS_FALLS)
        }
        npc.transform(Npcs.TOA_WARDENS_P2_OBELISK_NPC, restoreStats = false)
        npc.hitpoints = npc.maxHitpoints
        rising ?: return
        // WD16: Tumeken starts praying melee + magic, Elidinis melee + ranged.
        s.mageForm = !tumekenFalls
        rising.transform(p2Form(s), restoreStats = true)
        rising[SharedAttrs.RAID_SCALER]?.invoke(rising)
        rising.frozenUntil = 0
        rising.huntRange = HUNT
        s.fought = rising
        s.realMax = realMaxHp(rising)
        s.realHp = s.realMax
        s.phase = Phase.TWO
    }

    private fun p2Form(s: State): Int =
        if (s.foughtElidinis) {
            if (s.mageForm) Npcs.TOA_WARDEN_ELIDINIS_PHASE2_MAGE else Npcs.TOA_WARDEN_ELIDINIS_PHASE2_RANGE
        } else {
            if (s.mageForm) Npcs.TOA_WARDEN_TUMEKEN_PHASE2_MAGE else Npcs.TOA_WARDEN_TUMEKEN_PHASE2_RANGE
        }

    /** WD27 reading: the real health pool is the ejected form's (4,500 base, DPS calculator), scaled like any Warden. */
    private fun realMaxHp(w: NpcRef): Int {
        val raid = ToaKit.raidLevel(w)
        val base = EJECTED_BASE + EJECTED_BASE * (raid / CORE_LEVEL_DIVISOR) / PERCENT
        val party = ToaKit.raiders(w).size.coerceAtLeast(1)
        val factor = if (party >= 2) PARTY_EARLY * (if (party >= 3) 2 else 1) + if (party >= 4) PARTY_LATE * (party - 3) else 0 else 0
        return base * (PARTY_DIVISOR + factor) / PARTY_DIVISOR
    }

    // ---- phase 2

    /**
     * WD17 / WD18 (OPEN-WD7): Tumeken melees in reach, both use ranged / magic otherwise (prayer read at impact); a special
     * every 5th attack: Divine Projectile above 55%, Imprisonment below (WD19 / WD20). Acceleration: one tick faster.
     */
    private fun p2Attack(npc: NpcRef, s: State, victim: EntityRef, distance: Boolean) {
        val speed = (if (s.foughtElidinis) ELIDINIS_SPEED else TUMEKEN_SPEED) - if (ToaKit.has(npc, ACCELERATION)) 1 else 0
        npc.setAttackClock(npc.tick + speed)
        val max = ToaKit.dmg(npc, if (s.foughtElidinis) ELIDINIS_MAX else TUMEKEN_MAX, pathScaled = false)
        if (++s.attacks % P2_SPECIAL_EVERY == 0) {
            npc.anim(Seqs.NPC_WARDENS_MAGIC01)
            if (s.realHp * PERCENT > s.realMax * SPECIAL_SPLIT) divine(npc) else imprison(npc, victim)
            return
        }
        if (!distance && !s.foughtElidinis) {
            npc.anim(Seqs.NPC_WARDENS_MELEE01)
            ToaKit.strike(npc, victim, AttackType.CRUSH, max, 0)
            return
        }
        val magic = npc.random(2) == 0
        npc.anim(if (magic) Seqs.NPC_WARDENS_MAGIC01 else Seqs.NPC_WARDENS_RANGED01)
        BossCombat.projectile(npc, victim, if (magic) SpotAnims.TOA_WARDENS_MAGIC_IMPACT else WARDEN_RANGED, P2_PROJ_DELAY)
        val type = if (magic) AttackType.MAGIC else AttackType.RANGED
        val raw = if (BossCombat.rolls(npc, victim, type)) BossCombat.roll(npc, 1, max) else 0
        npc.queueHit(P2_PROJ_DELAY, strong = false) {
            CombatSystem.queueHit(victim, npc, ToaKit.afterPrayer(npc, victim, type.style, raw), CombatSystem.hitStyle(type.style), 0)
        }
    }

    /** WD19 (OPEN-WD8): overheads off, a melee / ranged / magic projectile at everyone; the matching prayer at arrival blocks it. */
    private fun divine(npc: NpcRef) {
        val styles =
            listOf(
                CombatStyle.MELEE to SpotAnims.TOA_WARDENS_PRAYER_MELEE_TRAVEL,
                CombatStyle.RANGED to SpotAnims.TOA_WARDENS_PRAYER_RANGED_TRAVEL,
                CombatStyle.MAGIC to SpotAnims.TOA_WARDENS_PRAYER_MAGIC_TRAVEL,
            )
        val (style, gfx) = styles[npc.random(styles.size)]
        for (p in ToaKit.raiders(npc)) {
            p.disableProtection(DIVINE_DISABLE)
            BossCombat.projectile(npc, p, gfx, DIVINE_DELAY)
            npc.queueHit(DIVINE_DELAY, strong = false) {
                if (state(npc)?.core != null) return@queueHit
                val dmg = if (p.protects(style)) 0 else BossCombat.roll(npc, 1, ToaKit.dmg(npc, DIVINE_MAX, pathScaled = false))
                CombatSystem.queueHit(p, npc, dmg, HitStyle.TYPELESS, 0)
            }
        }
    }

    /** WD20 (OPEN-WD8): a dark projectile at the target's tile; still there on arrival -> entombed (no moving or attacking). */
    private fun imprison(npc: NpcRef, victim: EntityRef) {
        val p = victim as? PlayerRef ?: return
        val tile = p.coord
        BossCombat.projectile(npc, p, SpotAnims.TOA_WARDENS_ENTOMB_TRAVEL, ENTOMB_DELAY)
        npc.queueHit(ENTOMB_DELAY, strong = false) {
            if (p.coord != tile) {
                npc.spotAnimAt(tile, SpotAnims.TOA_WARDENS_ENTOMB_MISS)
                return@queueHit
            }
            p.anim(Seqs.TOA_WARDENS_ENTOMB_LOOP)
            ToaKit.stun(p, ENTOMB_TICKS)
            p.stopInteraction()
            ToaKit.hitRolled(npc, p, ENTOMB_MAX, pathScaled = false)
        }
    }

    /** WD23 / WD25: the core comes out for 21 / 29 / 37 ticks by the Warden's health; everything of P2 stops (WD29). */
    private fun eject(npc: NpcRef, s: State) {
        npc.anim(Seqs.NPC_WARDENS_KNEEL01)
        // WD26: a "down" (the points stop after the third).
        ToaKit.signal(npc, null, "toa.wardens.down")
        npc.stopInteraction()
        val exposed = if (s.foughtElidinis) Npcs.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED else Npcs.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED
        npc.transform(exposed, restoreStats = false)
        npc.hitpoints = 1
        val pct = s.realHp * PERCENT / maxOf(1, s.realMax)
        s.exposedFor =
            when {
                pct >= EXPOSE_HIGH -> EXPOSE_LONG_HIGH
                pct >= EXPOSE_MID -> EXPOSE_LONG_MID
                else -> EXPOSE_LONG_LOW
            }
        val coreType = if (s.foughtElidinis) Npcs.TOA_WARDEN_ELIDINIS_CORE else Npcs.TOA_WARDEN_TUMEKEN_CORE
        val coreAt = CoordGrid(npc.coord.x + 2, npc.coord.z - 2, npc.coord.level)
        s.core = Bosses.summon(npc, coreType, coreAt, huntRange = 0, inheritRun = true)
        s.core?.let { it[STATE] = s }
    }

    /** WD24: a hit on the core counts five times on the Warden's real health; at 0 phase 2 ends (WD28). */
    fun coreHit(core: NpcRef, damage: Int) {
        val s = state(core) ?: return
        s.realHp -= damage * CORE_MULTIPLIER
        s.fought?.let { w -> CombatSystem.queueHit(w, null, minOf(w.hitpoints, damage * CORE_MULTIPLIER), HitStyle.TYPELESS, 0) }
        if (s.realHp <= 0) endPhaseTwo(s)
    }

    private fun phaseTwo(npc: NpcRef, s: State) {
        val w = s.fought ?: return
        val core = s.core
        if (core != null) {
            if (--s.exposedFor <= 0) {
                // WD25: the core goes back, the Warden stands up with a full shield; the prayer pair flips (OPEN-WD7).
                core.despawn()
                s.core = null
                s.mageForm = !s.mageForm
                w.anim(Seqs.NPC_WARDENS_STANDUP01)
                w.transform(p2Form(s), restoreStats = true)
                w[SharedAttrs.RAID_SCALER]?.invoke(w)
            }
            return
        }
        obeliskAttacks(npc, s)
    }

    /** WD21 (OPEN-WD9): the set beam -> windmill -> skull, one every 12 ticks (Acceleration 9). */
    private fun obeliskAttacks(npc: NpcRef, s: State) {
        val every = if (ToaKit.has(npc, ACCELERATION)) OBELISK_EVERY_FAST else OBELISK_EVERY
        if (++s.obeliskTimer < every) return
        s.obeliskTimer = 0
        when (s.obeliskSet++ % OBELISK_SET) {
            0 -> beam(npc, s)
            1 -> windmill(npc, s)
            else -> skulls(npc, s)
        }
    }

    private fun obeliskHit(npc: NpcRef, p: PlayerRef) {
        val pen = ToaKit.has(npc, PENETRATION)
        val base = if (pen) OBELISK_MAX * PEN_PERCENT / PERCENT else OBELISK_MAX
        val dmg = BossCombat.roll(npc, 1, ToaKit.dmg(npc, base, pathScaled = false))
        ToaKit.areaHit(npc, p, dmg)
        if (pen) p.disableProtection(PEN_DISABLE)
    }

    /** Converging Beam: warning 2199, strike 2235 a tick later, on the obelisk's column and row (OPEN-WD9 geometry). */
    private fun beam(npc: NpcRef, s: State) {
        npc.anim(Seqs.FX_OBELISK_WARDENS02_BEAM)
        val tiles = lineTiles(npc) { dx, dz -> dx == 0 || dz == 0 }
        for (t in tiles) npc.spotAnimAt(t, SpotAnims.WARDENS_DARKNESS_WARN)
        npc.queueHit(1, strong = false) {
            if (s.core != null) return@queueHit
            for (t in tiles) {
                npc.spotAnimAt(t, SpotAnims.SPOTANIM_WARDENS01_DRONESTRIKE01_FIRE01)
                for (p in ToaKit.on(npc, t)) obeliskHit(npc, p)
            }
        }
    }

    /** Windmill Beam: the diagonals then the cardinals (OPEN-WD9 direction), 2200 / 2234 on the struck tiles at once. */
    private fun windmill(npc: NpcRef, s: State) {
        npc.anim(Seqs.FX_OBELISK_WARDENS02_SWEEP)
        repeat(2) { step ->
            npc.queueHit(step * WINDMILL_GAP, strong = false) {
                if (s.core != null) return@queueHit
                val tiles = lineTiles(npc) { dx, dz -> if (step == 0) kotlin.math.abs(dx) == kotlin.math.abs(dz) else dx == 0 || dz == 0 }
                for (t in tiles) {
                    npc.spotAnimAt(t, SpotAnims.WARDENS_BURN_WARN)
                    for (p in ToaKit.on(npc, t)) obeliskHit(npc, p)
                }
            }
        }
    }

    /** Lightning Skull: skulls land and strike a 7x7 except the two tiles in each cardinal direction (and the skull's own tile). */
    private fun skulls(npc: NpcRef, s: State) {
        npc.anim(Seqs.FX_OBELISK_WARDENS02_BOMB)
        repeat(SKULLS) {
            val c = BossCombat.randomTile(npc, ToaKit.at(npc, ARENA_CENTRE), ARENA_RADIUS)
            npc.spotAnimAt(c, SpotAnims.WARDENS_LIGHTNING_WARN)
            npc.queueHit(SKULL_DELAY, strong = false) {
                if (s.core != null) return@queueHit
                for (p in ToaKit.on(npc, c, SKULL_RADIUS)) {
                    val dx = p.coord.x - c.x
                    val dz = p.coord.z - c.z
                    val safe = (dx == 0 && kotlin.math.abs(dz) <= 2) || (dz == 0 && kotlin.math.abs(dx) <= 2)
                    if (!safe) obeliskHit(npc, p)
                }
            }
        }
    }

    private fun lineTiles(npc: NpcRef, keep: (Int, Int) -> Boolean): List<CoordGrid> {
        val c = ToaKit.at(npc, CoordGrid(OBELISK_X + 1, TROUGH_Z, ARENA_LEVEL))
        val out = ArrayList<CoordGrid>()
        for (dx in -ARENA_RADIUS..ARENA_RADIUS) {
            for (dz in -ARENA_RADIUS..ARENA_RADIUS) {
                // The obelisk's own 3x3 is not a floor tile.
                if (keep(dx, dz) && (kotlin.math.abs(dx) > 1 || kotlin.math.abs(dz) > 1)) {
                    out += CoordGrid(c.x + dx, c.z + dz, c.level)
                }
            }
        }
        return out
    }

    /** WD28: "X's Warden uses the last of its power to restore Y's Warden!", the obelisk explodes, the room warps (phase 3). */
    private fun endPhaseTwo(s: State) {
        if (s.phase != Phase.TWO) return
        s.phase = Phase.THREE
        val w = s.fought ?: return
        s.core?.despawn()
        s.core = null
        w.anim(Seqs.NPC_WARDENS_DEATH01)
        val (dead, restored) = if (s.foughtElidinis) "Elidinis'" to "Tumeken's" else "Tumeken's" to "Elidinis'"
        val raiders = ToaKit.raiders(s.obelisk)
        for (p in raiders) p.message("$dead Warden uses the last of its power to restore $restored Warden!")
        s.obelisk.anim(Seqs.FX_OBELISK_WARDENS02_DEATH)
        s.obelisk.spotAnim(SpotAnims.OBELISK_EXPLODE_ENERGY)
        // WD32: the warped room (region 15696, two squares east): its frame is the obelisk's shifted by 128 tiles.
        val f0 = CoxKit.frame(s.obelisk) ?: return
        val origin = CoordGrid(f0.origin.x + P3_SHIFT, f0.origin.z, 0)
        val f3 = RaidRoomFrame(origin, 0, f0.size, CoordGrid(f0.template.x + P3_SHIFT, f0.template.z, 0))
        s.p3Frame = f3
        val type = if (s.foughtElidinis) Npcs.TOA_WARDEN_TUMEKEN_PHASE3 else Npcs.TOA_WARDEN_ELIDINIS_PHASE3
        val p3 = Bosses.summon(s.obelisk, type, tile(f3, P3_WARDEN), huntRange = 0, inheritRun = true, scale = true)
        p3[SharedAttrs.RAID_ROOM] = f3
        p3[STATE] = s
        p3.frozenUntil = Long.MAX_VALUE
        s.p3 = p3
        s.siphonAt = 0
        s.slamTimer = -P3_START_DELAY
        for (p in raiders) p.teleport(tile(f3, P3_ARRIVAL))
        for (m in Bosses.minions(s.obelisk)) if (m != p3) m.despawn()
    }

    /** A phase 3 summon (siphon, phantom, Amascut): the obelisk's minion, framed by the phase 3 square, not the obelisk's. */
    private fun p3Summon(s: State, type: Int, at: CoordGrid): NpcRef {
        val n = Bosses.summon(s.obelisk, type, at, huntRange = 0, inheritRun = true)
        s.p3Frame?.let { n[SharedAttrs.RAID_ROOM] = it }
        return n
    }

    private fun tile(f: RaidRoomFrame, t: CoordGrid): CoordGrid =
        CoordGrid(f.origin.x + t.x - f.template.x, f.origin.z + t.z - f.template.z, t.level)

    // ---- phase 3

    private fun phaseThree(w: NpcRef, s: State) {
        if (!w.isInWorld || w.isDying) return
        if (s.siphonsLeft >= 0) {
            siphonTick(w, s)
        } else if (!s.enraged) {
            slamTick(w, s)
        } else {
            enrageTick(w, s)
        }
        phantomTick(w, s)
    }

    /** WD33 / WD34 (Main's reading, base 6): right (east), left (west), both; Overclocked 5, Overclocked 2 4, Insanity 3. */
    private fun slamTick(w: NpcRef, s: State) {
        val interval =
            when {
                ToaKit.has(w, INSANITY) -> SLAM_INSANITY
                ToaKit.has(w, OVERCLOCKED_2) -> SLAM_OC2
                ToaKit.has(w, OVERCLOCKED) -> SLAM_OC
                else -> SLAM_BASE
            }
        if (++s.slamTimer < interval) return
        s.slamTimer = 0
        val kind = s.slam++ % SLAM_KINDS
        w.anim(
            when (kind) {
                0 -> Seqs.NPC_WARDENS_ATTACKRIGHT01
                1 -> Seqs.NPC_WARDENS_ATTACKLEFT01
                else -> Seqs.NPC_WARDENS_ATTACKCENTER01
            },
        )
        val centreX = w.coord.x + WARDEN_SIZE / 2
        for (p in ToaKit.raiders(w)) {
            val dx = p.coord.x - centreX
            val hit =
                when (kind) {
                    0 -> dx >= 0
                    1 -> dx <= 0
                    else -> kotlin.math.abs(dx) > 1
                }
            if (!hit) continue
            w.spotAnimAt(p.coord, UNDERTILE[npcRandom(w)])
            ToaKit.areaHit(w, p, BossCombat.roll(w, 1, ToaKit.dmg(w, P3_MAX, pathScaled = false)))
        }
    }

    private fun npcRandom(w: NpcRef): Int = w.random(UNDERTILE.size)

    /** WD36: at 80 / 60 / 40 / 20% the siphons (each threshold once); WD43 below 5% the enrage. */
    private fun p3Thresholds(w: NpcRef, s: State) {
        if (w.type in P3_CHARGING) return
        val pm = w.hitpoints * PER_MILLE / maxOf(1, w.maxHitpoints)
        if (!s.enraged && pm < ENRAGE_AT) {
            enrage(w, s)
            return
        }
        if (s.siphonAt < SIPHON_AT.size && pm <= SIPHON_AT[s.siphonAt]) {
            s.siphonAt++
            siphons(w, s)
        }
    }

    private val SIPHON_DEADLINE = AttrKey<Long>("toa_wardens_siphon_deadline")

    /** WD37 / WD40 (OPEN-WD11): siphons (HP 1 / 2 / 3 / 4 by players alive), a window of 20 ticks (+3 in groups, Insanity -5). */
    @Suppress("CyclomaticComplexMethod") // the siphon HP bands, layout, window and the phantom pairing in one place
    private fun siphons(w: NpcRef, s: State) {
        val charging = if (s.foughtElidinis) Npcs.TOA_WARDEN_TUMEKEN_PHASE3_CHARGING else Npcs.TOA_WARDEN_ELIDINIS_PHASE3_CHARGING
        w.transform(charging, restoreStats = false)
        w.anim(Seqs.NPC_WARDENS_CHARGE01)
        val alive = ToaKit.raiders(w).size
        val hp =
            when {
                alive >= 8 -> 4
                alive >= 6 -> 3
                alive >= 4 -> 2
                else -> 1
            }
        val layout = if (alive <= 1) SIPHONS_SOLO else SIPHONS_GROUP
        for (t in layout) {
            val at = tile(s.p3Frame ?: return, t)
            for (p in ToaKit.on(w, at)) p.teleport(BossCombat.randomTile(w, at, 2))
            val sip = p3Summon(s, Npcs.TOA_WARDENS_ENERGY, at)
            sip[STATE] = s
            sip.overrideStats(IntArray(STATS) { i -> if (i == HITPOINTS) hp else sip.baseLevel(i) })
            sip.hitpoints = hp
        }
        s.siphonBatch++
        s.siphonsLeft = layout.size
        val window = SIPHON_WINDOW + (if (alive > 1) SIPHON_GROUP_BONUS else 0) - if (ToaKit.has(w, INSANITY)) SIPHON_INSANITY_CUT else 0
        w[SIPHON_DEADLINE] = w.tick + window
        // WD41: phantoms in the 2nd and 3rd batches (Elidinis: Akkha then Kephri; Tumeken: Zebak then Ba-Ba).
        if (s.siphonBatch in 2..3) {
            s.phantom?.despawn()
            val type =
                when {
                    !s.foughtElidinis && s.siphonBatch == 2 -> Npcs.TOA_WARDENS_AKKHA
                    !s.foughtElidinis -> Npcs.TOA_WARDENS_KEPHRI
                    s.siphonBatch == 2 -> Npcs.TOA_WARDENS_ZEBAK
                    else -> Npcs.TOA_WARDENS_BABA
                }
            val spot = tile(s.p3Frame ?: return, PHANTOM_SPOT)
            s.phantom = p3Summon(s, type, spot).also { it.frozenUntil = Long.MAX_VALUE }
        }
    }

    /** A siphon killed (reversed, WD38). */
    fun siphonDied(siphon: NpcRef) {
        val s = state(siphon) ?: return
        if (s.siphonsLeft > 0) s.siphonsLeft--
    }

    /** WD38 / WD39: all reversed in time -> they hit the Warden (OPEN-WD11: 5% of max each); otherwise the shockwave. */
    private fun siphonTick(w: NpcRef, s: State) {
        val deadline = w[SIPHON_DEADLINE] ?: return
        val left = Bosses.minions(s.obelisk, Npcs.TOA_WARDENS_ENERGY)
        if (left.isEmpty()) {
            val n =
                when (ToaKit.raiders(w).size) {
                    1 -> SIPHONS_SOLO.size
                    else -> SIPHONS_GROUP.size
                }
            CombatSystem.queueHit(w, null, minOf(w.hitpoints - 1, w.maxHitpoints * SIPHON_HIT_PERCENT * n / PERCENT), HitStyle.TYPELESS, 0)
            endSiphons(w, s, reset = !ToaKit.has(w, INSANITY))
            return
        }
        if (w.tick < deadline) return
        // WD39: a shockwave from the centre by raid level and siphons left; "skull skip": far from the centre is safe.
        val centre = tile(s.p3Frame ?: return, P3_ARRIVAL)
        for (p in ToaKit.raiders(w)) {
            if (maxOf(kotlin.math.abs(p.coord.x - centre.x), kotlin.math.abs(p.coord.z - centre.z)) >= SKIP_DISTANCE) continue
            ToaKit.areaHit(w, p, ToaKit.dmg(w, SHOCKWAVE_PER_SIPHON * left.size, pathScaled = false))
        }
        for (sip in left) sip.despawn()
        endSiphons(w, s, reset = false)
    }

    private fun endSiphons(w: NpcRef, s: State, reset: Boolean) {
        s.siphonsLeft = -1
        if (reset) s.slam = 0
        w.transform(if (s.foughtElidinis) Npcs.TOA_WARDEN_TUMEKEN_PHASE3 else Npcs.TOA_WARDEN_ELIDINIS_PHASE3, restoreStats = false)
    }

    /** WD43: heal 20% once, Defence 180 (floor 120), slams stop; lightning and the floor rows (WD44) begin. */
    private fun enrage(w: NpcRef, s: State) {
        s.enraged = true
        w.anim(Seqs.NPC_WARDENS_ENRAGING01)
        w.hitpoints = minOf(w.maxHitpoints, w.hitpoints + w.maxHitpoints * ENRAGE_HEAL / PERCENT)
        w.setLevel(NpcStat.DEFENCE, ENRAGE_DEFENCE)
        val spot = tile(s.p3Frame ?: return, AMASCUT_SPOT)
        p3Summon(s, Npcs.TOA_AMASCUT_P3_ENRAGE, spot).frozenUntil = Long.MAX_VALUE
    }

    /** WD43 / WD44 (OPEN-WD13): a strike every 2 ticks (Insanity 1), a row torn away every 10 ticks (Insanity 6) up to the Warden's row. */
    private fun enrageTick(w: NpcRef, s: State) {
        val f = s.p3Frame ?: return
        val strikeEvery = if (ToaKit.has(w, INSANITY)) 1 else LIGHTNING_EVERY
        if (++s.lightningTimer >= strikeEvery) {
            s.lightningTimer = 0
            val target = ToaKit.raiders(w).randomPick(w)
            if (target != null) {
                val tile = target.coord
                w.spotAnimAt(tile, SpotAnims.WARDENS_LIGHTNING)
                w.queueHit(LIGHTNING_DELAY, strong = false) {
                    for (p in ToaKit.on(w, tile)) ToaKit.hitRolled(w, p, LIGHTNING_MAX, pathScaled = false)
                }
            }
        }
        val rowEvery = if (ToaKit.has(w, INSANITY)) ROW_EVERY_INSANITY else ROW_EVERY
        if (++s.rowTimer < rowEvery) return
        s.rowTimer = 0
        val row = P3_NORTH_Z - s.rowsGone
        if (row <= P3_WARDEN_ROW) return
        s.rowsGone++
        // The furthest row goes; players on it are moved one row in (never into unwalkable terrain, 1 Sep 2022).
        for (p in ToaKit.raiders(w)) {
            val t = CoordGrid(f.template.x + p.coord.x - f.origin.x, f.template.z + p.coord.z - f.origin.z, 0)
            if (t.z >= row) p.teleport(CoordGrid(p.coord.x, p.coord.z - (t.z - row + 1), p.coord.level))
        }
    }

    /** WD41 (OPEN-WD12): the phantom attacks a random player every 5 ticks (Insanity in the enrage: 3) with its boss's attack. */
    private fun phantomTick(w: NpcRef, s: State) {
        val ph = s.phantom ?: return
        if (!ph.isInWorld) return
        val every = if (s.enraged && ToaKit.has(w, INSANITY)) PHANTOM_FAST else PHANTOM_EVERY
        if (++s.phantomTimer < every) return
        s.phantomTimer = 0
        val p = ToaKit.raiders(w).randomPick(w) ?: return
        when (ph.type) {
            Npcs.TOA_WARDENS_AKKHA -> {
                val magic = (s.phantomTimer + ph.tick / every) % (2 * AKKHA_SWITCH) >= AKKHA_SWITCH
                BossCombat.projectile(ph, p, if (magic) AKKHA_MAGIC else AKKHA_RANGED, PHANTOM_DELAY)
                val style = if (magic) CombatStyle.MAGIC else CombatStyle.RANGED
                val dmg = ToaKit.afterPrayer(ph, p, style, BossCombat.roll(ph, 1, ToaKit.dmg(ph, PHANTOM_MAX, pathScaled = false)))
                CombatSystem.queueHit(p, ph, dmg, CombatSystem.hitStyle(style), PHANTOM_DELAY)
            }

            Npcs.TOA_WARDENS_ZEBAK -> {
                val magic = ph.random(2) == 0
                BossCombat.projectile(ph, p, if (magic) ZEBAK_MAGIC else ZEBAK_RANGED, PHANTOM_DELAY)
                val style = if (magic) CombatStyle.MAGIC else CombatStyle.RANGED
                val raw = BossCombat.roll(ph, 1, ToaKit.dmg(ph, PHANTOM_MAX, pathScaled = false))
                ph.queueHit(PHANTOM_DELAY, strong = false) {
                    CombatSystem.queueHit(p, ph, ToaKit.afterPrayer(ph, p, style, raw), CombatSystem.hitStyle(style), 0)
                }
            }

            Npcs.TOA_WARDENS_BABA -> {
                val tile = p.coord
                ph.spotAnimAt(tile, SpotAnims.TOA_BABA_ROCK_FALL)
                ph.queueHit(BABA_ROCK_DELAY, strong = false) {
                    for (v in ToaKit.on(ph, tile)) ToaKit.hitRolled(ph, v, PHANTOM_MAX, pathScaled = false)
                }
            }

            else -> {
                val tile = p.coord
                val r = if (ToaKit.has(ph, AERIAL_ASSAULT)) 1 else 0
                ph.queueHit(KEPHRI_DELAY, strong = false) {
                    ph.spotAnimAt(tile, KEPHRI_IMPACT)
                    for (v in ToaKit.on(ph, tile, r)) ToaKit.hitRolled(ph, v, PHANTOM_MAX, pathScaled = false)
                }
            }
        }
    }

    /** WD46 / Data: the book "The wardens" from the Warden killed last (if not read), to the top damage dealer. */
    override fun died(npc: NpcRef, killer: EntityRef?) {
        val s = state(npc) ?: return
        if (npc != s.p3) return
        // The Clutch: the final blow by a player left alone in the arena.
        val alive = ToaKit.raiders(npc)
        if (killer is PlayerRef && alive.size == 1 && alive.first() == killer) ToaKit.signal(npc, killer, "toa.wardens.finished")
        s.phantom?.despawn()
        // The Warden is itself the obelisk's minion: it stays for its own death (the DeathEvent ends the room).
        for (m in Bosses.minions(s.obelisk)) if (m != npc) m.despawn()
        val top = Bosses.topDamager(npc) ?: return
        if (top.varbit(Varbits.TOA_BOOK_WARDENS) == 0) ToaKit.giveOrDrop(top, Objs.TOA_BOOK_WARDENS, "wardens drop")
    }

    private fun List<PlayerRef>.randomPick(npc: NpcRef): PlayerRef? = if (isEmpty()) null else this[npc.random(size)]

    // ---- data (templates: P1 / P2 region 15184 base (3776, 5120) plane 1; P3 region 15696 base (3904, 5120) plane 0)

    private const val ARENA_LEVEL = 1
    private const val ARENA_MIN_X = 3797
    private const val ARENA_MAX_X = 3819
    private val ARENA_CENTRE = CoordGrid(3808, 5155, ARENA_LEVEL)
    private const val ARENA_RADIUS = 10
    private const val OBELISK_X = 3807
    private const val OBELISK_SIZE = 3
    private const val TROUGH_Z = 5154

    /** Wiki pins (centres): Elidinis (3794, 5154), Tumeken (3823, 5154); size 5. */
    private val ELIDINIS_P1 = CoordGrid(3792, 5152, ARENA_LEVEL)
    private val TUMEKEN_P1 = CoordGrid(3821, 5152, ARENA_LEVEL)

    /** P3: the Warden at the warped room's south apex, the party north of it (cache collision; OPEN-WD14). */
    private const val P3_SHIFT = 128
    private val P3_WARDEN = CoordGrid(3934, 5150, 0)
    private val P3_ARRIVAL = CoordGrid(3936, 5160, 0)
    private val PHANTOM_SPOT = CoordGrid(3929, 5150, 0)
    private val AMASCUT_SPOT = CoordGrid(3933, 5138, 0)
    private const val P3_NORTH_Z = 5165
    private const val P3_WARDEN_ROW = 5157
    private const val P3_START_DELAY = 5
    private const val WARDEN_SIZE = 5

    /** OPEN-WD11 siphon layouts (solo 3, group 5) in front of the Warden. */
    private val SIPHONS_SOLO = listOf(CoordGrid(3931, 5160, 0), CoordGrid(3936, 5162, 0), CoordGrid(3941, 5160, 0))
    private val SIPHONS_GROUP = SIPHONS_SOLO + listOf(CoordGrid(3928, 5163, 0), CoordGrid(3944, 5163, 0))

    /** OPEN-WD2: a Warden charges after 30 energy; an orb every 2 ticks; absorbing costs 3 (WD9). */
    private const val CHARGED = 30
    private const val ENERGY_SCALE = 3
    private const val ORB_EVERY = 2
    private const val ORB_ABSORB = 3

    /** OPEN-WD4: four pyramids, 3x3, 15 base; their window starts 2 ticks after they appear and they go after 4. */
    private const val UFO_COUNT = 4
    private const val UFO_DELAY = 2
    private const val UFO_LIFE = 4
    private const val UFO_MAX = 15

    /** WD14: lands 5 ticks later; caps 30 / 45 (Elidinis) and ~7 / 30 (Tumeken); OPEN-WD3 ids and base values. */
    private const val SHOT_ELIDINIS = 2237
    private const val SHOT_TUMEKEN = 2238
    private const val SHOT_DELAY = 5
    private const val ELIDINIS_CAP = 30
    private const val ELIDINIS_SOLO_CAP = 45
    private const val TUMEKEN_CAP = 7
    private const val TUMEKEN_SOLO_CAP = 30
    private const val ELIDINIS_BASE = 25
    private const val TUMEKEN_BASE = 30

    /** WD17: speeds 7 (Elidinis) / 8 (Tumeken), max 20 / 22; OPEN-WD7 projectile delay 3, special every 5th, ranged 2241. */
    private const val ELIDINIS_SPEED = 7
    private const val TUMEKEN_SPEED = 8
    private const val ELIDINIS_MAX = 20
    private const val TUMEKEN_MAX = 22
    private const val P2_PROJ_DELAY = 3
    private const val P2_SPECIAL_EVERY = 5
    private const val WARDEN_RANGED = 2241
    private const val SPECIAL_SPLIT = 55

    /** OPEN-WD8: Divine Projectile (overheads off 3 ticks, lands in 3, 40 base); Imprisonment (lands in 3, 6 ticks, 10). */
    private const val DIVINE_DISABLE = 3
    private const val DIVINE_DELAY = 3
    private const val DIVINE_MAX = 40
    private const val ENTOMB_DELAY = 3
    private const val ENTOMB_TICKS = 6
    private const val ENTOMB_MAX = 10

    /** WD24 / WD25: core x5; 21 / 29 / 37 ticks at 80-100 / 60-80 / below 60%. */
    private const val CORE_MULTIPLIER = 5
    private const val EXPOSE_HIGH = 80
    private const val EXPOSE_MID = 60
    private const val EXPOSE_LONG_HIGH = 21
    private const val EXPOSE_LONG_MID = 29
    private const val EXPOSE_LONG_LOW = 37

    /** WD27 reading: 4,500 base; the cores' raid-level rate is a quarter (trunc(RL / 10) %, TA31). */
    private const val EJECTED_BASE = 4500
    private const val CORE_LEVEL_DIVISOR = 10
    private const val PARTY_EARLY = 9
    private const val PARTY_LATE = 6
    private const val PARTY_DIVISOR = 10

    /** WD21 (OPEN-WD9): one obelisk attack every 12 ticks (Acceleration 9), 25 base; Penetration +25% and overheads off 3 ticks. */
    private const val OBELISK_EVERY = 12
    private const val OBELISK_EVERY_FAST = 9
    private const val OBELISK_SET = 3
    private const val OBELISK_MAX = 25
    private const val PEN_PERCENT = 125
    private const val PEN_DISABLE = 3
    private const val WINDMILL_GAP = 2
    private const val SKULLS = 3
    private const val SKULL_DELAY = 3
    private const val SKULL_RADIUS = 3

    /** WD34 (Main): 6 / 5 / 4 / 3 ticks a slam; WD31 max 26; the four strike graphics 2220-2223. */
    private const val SLAM_BASE = 6
    private const val SLAM_OC = 5
    private const val SLAM_OC2 = 4
    private const val SLAM_INSANITY = 3
    private const val SLAM_KINDS = 3
    private const val P3_MAX = 26
    private val UNDERTILE =
        intArrayOf(
            SpotAnims.SPOTANIM_WARDENS01_UNDERTILE01_EXPLODE01,
            SpotAnims.SPOTANIM_WARDENS01_UNDERTILE01_EXPLODE02,
            SpotAnims.SPOTANIM_WARDENS01_UNDERTILE01_EXPLODE03,
            SpotAnims.SPOTANIM_WARDENS01_UNDERTILE01_EXPLODE04,
        )

    /** WD36: 80 / 60 / 40 / 20%; WD43 below 5%: +20%, Defence 180. */
    private val SIPHON_AT = intArrayOf(800, 600, 400, 200)
    private const val ENRAGE_AT = 50
    private const val ENRAGE_HEAL = 20
    private const val ENRAGE_DEFENCE = 180
    private const val PER_MILLE = 1000

    /** OPEN-WD11: 20-tick window (+3 groups, Insanity -5); 5% of max a reversed siphon; 15 a siphon left; skip from 8 tiles. */
    private const val SIPHON_WINDOW = 20
    private const val SIPHON_GROUP_BONUS = 3
    private const val SIPHON_INSANITY_CUT = 5
    private const val SIPHON_HIT_PERCENT = 5
    private const val SHOCKWAVE_PER_SIPHON = 15
    private const val SKIP_DISTANCE = 8

    /** OPEN-WD13: lightning every 2 ticks (Insanity 1), lands on its creation tick + 1, 20 base; a row every 10 ticks (Insanity 6). */
    private const val LIGHTNING_EVERY = 2
    private const val LIGHTNING_DELAY = 1
    private const val LIGHTNING_MAX = 20
    private const val ROW_EVERY = 10
    private const val ROW_EVERY_INSANITY = 6

    /** WD41 / OPEN-WD12: a phantom attack every 5 ticks (3); Akkha switches every 3; MT ids and delays. */
    private const val PHANTOM_EVERY = 5
    private const val PHANTOM_FAST = 3
    private const val PHANTOM_DELAY = 3
    private const val PHANTOM_MAX = 20
    private const val AKKHA_SWITCH = 3
    private const val AKKHA_MAGIC = 2253
    private const val AKKHA_RANGED = 2255
    private const val ZEBAK_MAGIC = 2181
    private const val ZEBAK_RANGED = 2187
    private const val BABA_ROCK_DELAY = 6
    private const val KEPHRI_DELAY = 3
    private const val KEPHRI_IMPACT = 2156

    /** WD10 / WD31 / WD43 drains: obelisk -40, P3 -30, enraged -60. */
    private const val OBELISK_DRAIN = 40
    private const val P3_DRAIN = 30
    private const val P3_DRAIN_ENRAGED = 60

    private const val PERCENT = 100
    private const val HUNT = 24
    private const val STATS = 6
    private const val HITPOINTS = 3
    private const val CENTREPIECE = 10

    private val P2_FORMS =
        setOf(
            Npcs.TOA_WARDEN_ELIDINIS_PHASE2_MAGE,
            Npcs.TOA_WARDEN_ELIDINIS_PHASE2_RANGE,
            Npcs.TOA_WARDEN_TUMEKEN_PHASE2_MAGE,
            Npcs.TOA_WARDEN_TUMEKEN_PHASE2_RANGE,
        )
    private val INERT =
        setOf(
            Npcs.TOA_WARDEN_ELIDINIS_PHASE1,
            Npcs.TOA_WARDEN_TUMEKEN_PHASE1,
            Npcs.TOA_WARDEN_ELIDINIS_PHASE3_INACTIVE,
            Npcs.TOA_WARDEN_TUMEKEN_PHASE3_INACTIVE,
        )
    private val EXPOSED = setOf(Npcs.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED, Npcs.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED)
    private val P3_FORMS = setOf(Npcs.TOA_WARDEN_ELIDINIS_PHASE3, Npcs.TOA_WARDEN_TUMEKEN_PHASE3)
    private val P3_CHARGING = setOf(Npcs.TOA_WARDEN_ELIDINIS_PHASE3_CHARGING, Npcs.TOA_WARDEN_TUMEKEN_PHASE3_CHARGING)

    private const val TUMEKEN_FALLS = "As Tumeken's Warden falls, Elidinis' Warden powers up!"
    private const val ELIDINIS_FALLS = "As Elidinis' Warden falls, Tumeken's Warden powers up!"
    private const val ANCIENT_HASTE = "Ancient Haste"
    private const val ACCELERATION = "Acceleration"
    private const val PENETRATION = "Penetration"
    private const val OVERCLOCKED = "Overclocked"
    private const val OVERCLOCKED_2 = "Overclocked 2"
    private const val INSANITY = "Insanity"
    private const val AERIAL_ASSAULT = "Aerial Assault"
}

/** The Wardens' cores (WD24: hits count five times on the Warden; the core itself takes nothing) and the siphons (WD37). */
internal object ToaWardensParts : Boss(Npcs.TOA_WARDEN_TUMEKEN_CORE, Npcs.TOA_WARDEN_ELIDINIS_CORE, Npcs.TOA_WARDENS_ENERGY) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) = Unit

    override fun retaliates(npc: NpcRef): Boolean = false

    /** WD24: melee always lands on the core; WD37: on a siphon too. */
    override fun meleeAlwaysHits(npc: NpcRef): Boolean = true

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        if (npc.type == Npcs.TOA_WARDENS_ENERGY) return if (style == HitStyle.MELEE) damage else 0
        if (style == HitStyle.MELEE) ToaWardens.coreHit(npc, damage)
        return 0
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        if (npc.type == Npcs.TOA_WARDENS_ENERGY) ToaWardens.siphonDied(npc)
    }
}
