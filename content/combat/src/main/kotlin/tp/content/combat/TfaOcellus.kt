package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Ocellus, the TFA final boss (docs/mechanics/raids/tfa/ocellus.md OC1-OC4), one NPC through four phases, timed from
 * the RuneLite plugins (runelite-timings.md, LU = luuj/fracturedarchive FinalBoss.java):
 * - P1 (16674): volleys of water (magic) and fire (ranged) every 5 ticks from the corners; at each corner a slow ground
 *   attack sends seven tendrils 2 ticks apart, each going off 5 ticks after it appears; at 66% and 25% he teleports to
 *   the centre for four quick ground attacks of two tendrils each (6 and 8 ticks), then "Embrace... annihilation..." or
 *   "Death... awaits..." by the tendrils mitigated.
 * - P2 (16675, the Blade): every 5 ticks the portals about to fire light up 4 ticks ahead; one portal per inner wall
 *   stays dark (orange) and their lanes cross in a 2x2 safe area; three fixed sequences of six volleys; the Blade
 *   stores damage and releases it at each quadrant's end; half of it comes off P3's pool.
 * - P3 (16677): two basic attacks (4 ticks) and a sweep (5) make a combo; two combos, then a special: Prayer and an
 *   "orange" special in turn (Untether first, then Untether / Insolence alternating, Insolence right after a quadrant
 *   move); three quadrant moves ("Run!", 15 ticks), each followed by the void marker; the four censers.
 * - P4 (the last quadrant): a 21-tick ranged deflect then a 19-tick melee deflect, each opening with a void chain (6
 *   explosions 2 ticks apart, 3 ticks after the telegraph) and closing with a soul-split blast.
 */
@Suppress("LargeClass") // one boss through four phases; the capture pass splits the phases into their own files
internal object TfaOcellus : Boss(Npcs.TFA_OCELLUS_P1, Npcs.TFA_OCELLUS_P2, Npcs.TFA_OCELLUS_P3) {
    private enum class Phase { P1, P2, P3, P4 }

    private enum class Special { PRAYER, UNTETHER, INSOLENCE }

    private class State(val centre: CoordGrid, val size: Int) {
        var phase = Phase.P1
        var attacks = 0
        var corner = 0
        var centreBlasts = 0
        var stored = 0
        var bladeDamage = 0
        var volleys = 0
        var sequence = 0
        var bladeQuadrants = 0
        var basics = 0
        var combos = 0
        var specials = 0
        var orangeUntether = true
        var insolenceNext = false
        var moves = 0
        var quadrant = 0
        var deflect: HitStyle? = null
    }

    private val STATE = AttrKey<State>("tfa_ocellus")

    /** Censer buffs on a player: (style, until tick). */
    val CENSER_STYLE = AttrKey<HitStyle>("tfa_censer_style")
    val CENSER_UNTIL = AttrKey<Long>("tfa_censer_until")

    private fun state(npc: NpcRef): State =
        npc[STATE] ?: State(CoordGrid(npc.spawn.x + 1, npc.spawn.z + 1, npc.spawn.level), TfaKit.size(npc)).also {
            npc[STATE] = it
            TfaKit.setPool(npc, P1_HP[it.size - MIN_SCALE])
            placeCensers(npc, it)
            // ocellus.md: varbit TFA_OCELLUS_MET reveals his name and his stats category.
            for (p in TfaKit.arena(npc)) p.setVarbit(Varbits.TFA_OCELLUS_MET, 1)
        }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        s.attacks++
        when (s.phase) {
            Phase.P1 -> p1(npc, s, victim)

            Phase.P2 -> Unit

            // P2 runs on its own 5-tick timer (p2Loop): the Blade has no attack speed.
            Phase.P3 -> p3(npc, s, victim)

            Phase.P4 -> Unit // P4 runs on its own deflect cycle (p4Cycle).
        }
    }

    /**
     * OC3 censers (locs 62693-62700, cache: "Interact", each with an inactive form): one in each quadrant, shuffled every
     * fight; the fight starts in the quadrant diagonally opposite the Bandosian Censer (RuneLite LU FinalBoss.java:117-122).
     * OPEN-OC14: each sits 4 tiles beyond its quadrant's landing spot, away from the centre.
     */
    private fun placeCensers(npc: NpcRef, s: State) {
        val order = CENSERS.toMutableList()
        val placed = IntArray(QUADRANTS.size)
        for (q in QUADRANTS.indices) placed[q] = order.removeAt(npc.random(order.size))
        for ((q, id) in placed.withIndex()) {
            val (dx, dz) = QUADRANTS[q]
            val at =
                CoordGrid(
                    s.centre.x + dx + Integer.signum(dx) * CENSER_OUT,
                    s.centre.z + dz + Integer.signum(dz) * CENSER_OUT,
                    s.centre.level,
                )
            npc.addLoc(id, at, WALL_SHAPE, 0)
        }
        val bandos = placed.indexOf(Locs.TFA_OCELLUS_ARTIFACT_DAMAGE_MELEE)
        s.quadrant = (bandos + 2) % QUADRANTS.size
    }

    /**
     * A censer used (OC3): Bandosian = melee, Armadylean = ranged (OPEN-OC14: +10% damage for 100 ticks), Zarosian = +50%
     * special energy, Saradomin = +40 Hitpoints and +40 Prayer; the censer goes inactive for the rest of the fight.
     */
    fun useCenser(p: PlayerRef, id: Int, at: CoordGrid) {
        when (id) {
            Locs.TFA_OCELLUS_ARTIFACT_DAMAGE_MELEE -> {
                buff(p, HitStyle.MELEE)
            }

            Locs.TFA_OCELLUS_ARTIFACT_DAMAGE_RANGED -> {
                buff(p, HitStyle.RANGED)
            }

            Locs.TFA_OCELLUS_ARTIFACT_SPEC -> {
                TfaKit.addSpecEnergy(p, ZAROS_SPEC)
            }

            Locs.TFA_OCELLUS_ARTIFACT_HEAL -> {
                p.hitpoints = minOf(p.maxHitpoints, p.hitpoints + SARA_RESTORE)
                p.setLevel(Stat.PRAYER, minOf(p.baseLevel(Stat.PRAYER), p.level(Stat.PRAYER) + SARA_RESTORE))
            }
        }
        p.addLoc(id + 1, at, WALL_SHAPE, 0)
    }

    private fun buff(p: PlayerRef, style: HitStyle) {
        p[CENSER_STYLE] = style
        p[CENSER_UNTIL] = p.tick + CENSER_TICKS
    }

    // ---- P1 (OC1)

    private fun p1(npc: NpcRef, s: State, victim: EntityRef) {
        if (s.attacks % CASTS_PER_CORNER == 0) {
            cornerTendrils(npc, s)
            return
        }
        volley(npc)
        npc.setAttackClock(npc.tick + VOLLEY_SPEED)
        if (victim is PlayerRef && TfaKit.gap(npc, victim) <= SLAM_RANGE) {
            for (p in TfaKit.arena(npc)) if (TfaKit.gap(npc, p) <= SLAM_RANGE) BossCombat.strike(npc, p, AttackType.CRUSH, SLAM_MAX, 1)
        }
    }

    /** RuneLite (LU FinalBoss.java:405-410): one cast fires water (magic, spotanim 4228) and fire (ranged, 4231) shots. */
    private fun volley(npc: NpcRef) {
        for (p in TfaKit.arena(npc)) {
            val magic = npc.random(2) == 0
            val style = if (magic) CombatStyle.MAGIC else CombatStyle.RANGED
            npc.spotAnim(if (magic) SpotAnims.VFX_OCELLUS_WATER_ATTACK01_SPOTANIM else SpotAnims.VFX_OCELLUS_FIRE_ATTACK01_SPOTANIM)
            val delay = BossCombat.delay(npc, p, style)
            BossCombat.projectile(npc, p, SpotAnims.TFA_OCELLUS_SF_PROJ, delay)
            BossCombat.strike(npc, p, if (magic) AttackType.MAGIC else AttackType.RANGED, MAX_HIT, delay)
        }
    }

    /**
     * RuneLite (LU FinalBoss.java:437-442): at a corner the slow ground attack sends seven tendrils one after another, 2
     * ticks apart, each going off 5 ticks after it appears; corner attacks are 8 ticks apart. A player on a tendril
     * mitigates it (takes a little damage); an unmitigated one heals him (OPEN-OC10: 5 and 20).
     */
    private fun cornerTendrils(npc: NpcRef, s: State) {
        val (dx, dz) = CORNERS[s.corner++ % CORNERS.size]
        npc.teleport(CoordGrid(s.centre.x + dx, s.centre.z + dz, s.centre.level))
        npc.anim(Seqs.NPC_OCELLUS_PHASE1_CHARGE)
        for (k in 0 until CORNER_TENDRILS) {
            npc.queueHit(k * TENDRIL_GAP) {
                if (!npc.isInWorld) return@queueHit
                val players = TfaKit.arena(npc)
                val at = players.getOrNull(npc.random(maxOf(1, players.size)))?.coord ?: return@queueHit
                tendril(npc, at, TENDRIL_FUSE)
            }
        }
        npc.setAttackClock(npc.tick + CORNER_ATTACK_SPEED)
    }

    private fun tendril(npc: NpcRef, at: CoordGrid, fuse: Int, mitigated: MutableList<Boolean>? = null) {
        npc.spotAnimAt(at, SpotAnims.TFA_PORTAL)
        npc.queueHit(fuse) {
            if (!npc.isInWorld) return@queueHit
            val standing = TfaKit.arena(npc).filter { it.coord.x == at.x && it.coord.z == at.z }
            if (standing.isEmpty()) {
                npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + TENDRIL_HEAL)
            } else {
                standing.forEach { CombatSystem.applyHit(it, npc, TENDRIL_HIT, HitStyle.TYPELESS) }
            }
            mitigated?.add(standing.isNotEmpty())
        }
    }

    /**
     * RuneLite (LU FinalBoss.java:415-434): at the centre (teleport-in seq 14813) four quick ground attacks (seq 14809),
     * each spawning two tendrils on neighbouring sides, the first going off 6 ticks after appearing and the second 8;
     * then the finisher (seq 14810): "Embrace... annihilation..." (80+) or "Death... awaits..." (17-18) by how many were
     * mitigated (OPEN-OC11: annihilation when fewer than half).
     */
    private fun centreBlast(npc: NpcRef, s: State) {
        s.centreBlasts++
        npc.teleport(CoordGrid(s.centre.x - 1, s.centre.z - 1, s.centre.level))
        npc.anim(Seqs.NPC_OCELLUS_PHASE1_TELEPORT_IN)
        val mitigated = ArrayList<Boolean>()
        for (k in 0 until CENTRE_ATTACKS) {
            npc.queueHit(1 + k * CENTRE_ATTACK_GAP) {
                if (!npc.isInWorld) return@queueHit
                npc.anim(Seqs.NPC_OCELLUS_PHASE1_CHARGE)
                val (a, b) = SIDE_PAIRS[k % SIDE_PAIRS.size]
                tendril(npc, CoordGrid(s.centre.x + a.first, s.centre.z + a.second, s.centre.level), FIRST_FUSE, mitigated)
                tendril(npc, CoordGrid(s.centre.x + b.first, s.centre.z + b.second, s.centre.level), SECOND_FUSE, mitigated)
            }
        }
        val finish = 1 + (CENTRE_ATTACKS - 1) * CENTRE_ATTACK_GAP + SECOND_FUSE + 1
        npc.setAttackClock(npc.tick + finish + 1)
        npc.queueHit(finish) {
            if (!npc.isInWorld) return@queueHit
            npc.anim(Seqs.NPC_OCELLUS_PHASE1_EXPEND)
            val annihilate = mitigated.count { it } * 2 < mitigated.size
            npc.say(if (annihilate) "Embrace... annihilation..." else "Death... awaits...")
            for (p in TfaKit.arena(npc)) CombatSystem.applyHit(p, npc, if (annihilate) ANNIHILATION else DEATH_AWAITS, HitStyle.TYPELESS)
        }
    }

    // ---- P2 (OC2)

    private fun p2Loop(npc: NpcRef, s: State) {
        npc.queueHit(P2_SET_TICKS) {
            if (!npc.isInWorld || s.phase != Phase.P2) return@queueHit
            portals(npc, s)
            p2Loop(npc, s)
        }
    }

    /**
     * RuneLite (LU FinalBoss.java:1059-1190): each inner wall of the party's quadrant has six portals, 2 tiles apart from
     * the corner; a lane is a portal's tile and the next one. Every 5 ticks the portals about to fire light up (loc
     * 62685) 4 ticks ahead; one portal per inner wall stays dark (orange) and the orange lanes cross in a 2x2 safe area.
     * The orange portals follow one of three fixed sequences of six volleys ({row, column}). Purple lanes hit for 20
     * (OPEN-OC5). After six volleys the quadrant ends and the Blade releases its stored damage.
     */
    private fun portals(npc: NpcRef, s: State) {
        val players = TfaKit.arena(npc)
        if (players.isEmpty()) return
        if (s.volleys % VOLLEYS_PER_SEQUENCE == 0) s.sequence = npc.random(SEQUENCES.size)
        val (orangeRow, orangeCol) = SEQUENCES[s.sequence][s.volleys % VOLLEYS_PER_SEQUENCE]
        val qx = Integer.signum(players[0].coord.x - s.centre.x).let { if (it == 0) 1 else it }
        val qz = Integer.signum(players[0].coord.z - s.centre.z).let { if (it == 0) 1 else it }
        // Light the walls' firing portals.
        for (k in 0 until PORTALS_PER_WALL) {
            if (k !=
                orangeRow
            ) {
                npc.addLoc(
                    Locs.TFA_OCELLUS_QUADRANT_WALL_ACTIVE,
                    CoordGrid(s.centre.x, s.centre.z + qz * (1 + k * 2), s.centre.level),
                    WALL_SHAPE,
                    0,
                    PORTAL_TELEGRAPH,
                )
            }
            if (k !=
                orangeCol
            ) {
                npc.addLoc(
                    Locs.TFA_OCELLUS_QUADRANT_WALL_ACTIVE,
                    CoordGrid(s.centre.x + qx * (1 + k * 2), s.centre.z, s.centre.level),
                    WALL_SHAPE,
                    0,
                    PORTAL_TELEGRAPH,
                )
            }
        }
        npc.queueHit(PORTAL_TELEGRAPH) {
            if (!npc.isInWorld) return@queueHit
            for (p in TfaKit.arena(npc)) {
                val row = (kotlin.math.abs(p.coord.z - s.centre.z) - 1) / 2
                val col = (kotlin.math.abs(p.coord.x - s.centre.x) - 1) / 2
                val safe = row == orangeRow && col == orangeCol
                if (!safe) CombatSystem.applyHit(p, npc, PORTAL_DAMAGE, HitStyle.TYPELESS)
            }
        }
        s.volleys++
        if (s.volleys % VOLLEYS_PER_SEQUENCE == 0) releaseBlade(npc, s)
    }

    /** OC2 / OPEN-OC5: stored damage lands on the Blade at the quadrant's end, x0.8 for each earlier quadrant. */
    private fun releaseBlade(npc: NpcRef, s: State) {
        var factor = 1.0
        repeat(s.bladeQuadrants) { factor *= DIMINISH }
        s.bladeQuadrants++
        val release = (s.stored * factor).toInt()
        s.bladeDamage += release
        s.stored = 0
        npc.spotAnim(SpotAnims.TFA_ARENA_SWORD_DROP_EXPLOSION)
        if (release > 0) CombatSystem.applyHit(npc, null, release, HitStyle.TYPELESS)
    }

    // ---- P3 (OC3)

    private fun p3(npc: NpcRef, s: State, target: EntityRef) {
        // OC3 (28 Oct): the quadrant's tank is the leader first, then orb order, skipping dead players.
        val victim: EntityRef = TfaKit.firstInOrbOrder(npc) ?: target
        if (s.basics < BASICS_PER_COMBO) {
            s.basics++
            basic(npc, victim)
            return
        }
        s.basics = 0
        sweep(npc, victim)
        s.combos++
        voidPools(npc, victim)
        if (s.combos % COMBOS_PER_SPECIAL == 0 && s.moves < MOVES) special(npc, s, victim)
    }

    /** RuneLite (LU FinalBoss.java:99-107): a basic melee (seq 14842) is a 4-tick attack; hybrid (14844, 7) when out of reach. */
    private fun basic(npc: NpcRef, victim: EntityRef) {
        if (victim is PlayerRef && TfaKit.gap(npc, victim) > 1) {
            // OC3: a spread void shot (20+) when the tank is unreachable; the hybrid's style shows on him (4328-4330).
            npc.anim(Seqs.NPC_OCELLUS_PHASE3_MELEE_HYBRID)
            val style = if (npc.random(2) == 0) CombatStyle.MAGIC else CombatStyle.RANGED
            npc.spotAnim(
                if (style ==
                    CombatStyle.MAGIC
                ) {
                    SpotAnims.VFX_OCELLUS_PHASE3_MELEE_HYBRID_MAGIC
                } else {
                    SpotAnims.VFX_OCELLUS_PHASE3_MELEE_HYBRID_RANGED
                },
            )
            for (p in TfaKit.arena(npc)) {
                BossCombat.strike(
                    npc,
                    p,
                    if (style ==
                        CombatStyle.MAGIC
                    ) {
                        AttackType.MAGIC
                    } else {
                        AttackType.RANGED
                    },
                    VOID_SHOT,
                    BossCombat.delay(npc, p, style),
                )
            }
            npc.setAttackClock(npc.tick + HYBRID_SPEED)
            return
        }
        npc.anim(Seqs.NPC_OCELLUS_PHASE3_MELEE_BASIC)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        BossCombat.strike(npc, victim, AttackType.SLASH, P3_MELEE_MAX, 0)
        npc.setAttackClock(npc.tick + BASIC_SPEED)
    }

    /** RuneLite (LU): the sweep (seq 14846, 5 ticks) at the tank and the players next to them (OPEN-OC12: 25). */
    private fun sweep(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.NPC_OCELLUS_PHASE3_MELEE_SLASH)
        for (p in TfaKit.arena(npc)) if (p == victim || p.distanceTo(victim) <= 1) BossCombat.strike(npc, p, AttackType.SLASH, WAVE_MAX, 1)
        npc.setAttackClock(npc.tick + SWEEP_SPEED)
    }

    /**
     * RuneLite (LU FinalBoss.java:164-169, 817-829): Prayer and an "orange" special in turn, starting with Prayer; the
     * orange ones start with Untether and alternate with Insolence, except that right after a quadrant move it is
     * Insolence; no specials after the third move.
     */
    private fun special(npc: NpcRef, s: State, victim: EntityRef) {
        val special =
            if (s.specials++ % 2 == 0) {
                Special.PRAYER
            } else if (s.insolenceNext) {
                s.insolenceNext = false
                Special.INSOLENCE
            } else {
                val u = s.orangeUntether
                s.orangeUntether = !u
                if (u) Special.UNTETHER else Special.INSOLENCE
            }
        when (special) {
            Special.PRAYER -> prayerSpecial(npc, s)
            Special.UNTETHER -> untether(npc)
            Special.INSOLENCE -> insolence(npc, victim)
        }
    }

    /**
     * RuneLite (LU FinalBoss.java:794-801, 1477-1488): the first hit lands 6 ticks after PRAYER_IN (seq 14869): an
     * overhead still on loses it (OPEN-OC7: and takes 20). Later in P3 he holds it (14870): a second hit ~3 ticks on
     * punishes an overhead that is off again.
     */
    private fun prayerSpecial(npc: NpcRef, s: State) {
        npc.anim(Seqs.NPC_OCELLUS_PHASE3_PRAYER_IN)
        npc[VOID_SPIN] = npc.tick
        npc.queueHit(PRAYER_FIRST) {
            if (!npc.isInWorld) return@queueHit
            for (p in TfaKit.arena(npc)) {
                if (praying(p)) {
                    p.deactivateAllPrayers()
                    CombatSystem.applyHit(p, npc, PRAYER_PUNISH, HitStyle.TYPELESS)
                }
            }
        }
        if (s.moves >= 1) {
            npc.queueHit(PRAYER_FIRST + 1) { if (npc.isInWorld) npc.anim(Seqs.NPC_OCELLUS_PHASE3_PRAYER_LOOP) }
            npc.queueHit(PRAYER_FIRST + PRAYER_HOLD) {
                if (!npc.isInWorld) return@queueHit
                npc.spotAnim(SpotAnims.VFX_OCELLUS_PHASE3_MELEE_PRAYER_OUT)
                for (p in TfaKit.arena(npc)) if (!praying(p)) CombatSystem.applyHit(p, npc, PRAYER_PUNISH, HitStyle.TYPELESS)
            }
        }
        npc.setAttackClock(npc.tick + PRAYER_FIRST + PRAYER_HOLD + 1)
    }

    private fun praying(p: PlayerRef) = p.protects(CombatStyle.MELEE) || p.protects(CombatStyle.RANGED) || p.protects(CombatStyle.MAGIC)

    /**
     * RuneLite (LU FinalBoss.java:141-146): Untether: four floor waves 4 ticks apart, then the arena-wide hit when he
     * lands 12 ticks after the fourth (OPEN-OC13: 40 beyond 2 tiles of him, inside the purple lines is safe).
     */
    private fun untether(npc: NpcRef) {
        npc.say("Untether...")
        for (w in 1..UNTETHER_WAVES) {
            npc.queueHit(w * UNTETHER_GAP) {
                if (!npc.isInWorld) return@queueHit
                for (p in TfaKit.arena(npc)) if (TfaKit.gap(npc, p) == w) CombatSystem.applyHit(p, npc, UNTETHER_WAVE, HitStyle.TYPELESS)
            }
        }
        val land = UNTETHER_WAVES * UNTETHER_GAP + UNTETHER_LAND
        npc.queueHit(land) {
            if (!npc.isInWorld) return@queueHit
            for (p in TfaKit.arena(npc)) {
                if (TfaKit.gap(npc, p) >
                    UNTETHER_SAFE
                ) {
                    CombatSystem.applyHit(p, npc, UNTETHER_DAMAGE, HitStyle.TYPELESS)
                }
            }
        }
        npc.setAttackClock(npc.tick + land + 1)
    }

    /**
     * RuneLite (LU FinalBoss.java:89-104, 1583-1595): "Insolence..." 2 ticks before the first of three slams (seq 14848),
     * 5 ticks apart, each at a player and the tiles around them (locs 62703-62705; OPEN: 25).
     */
    private fun insolence(npc: NpcRef, victim: EntityRef) {
        npc.say("Insolence...")
        for (k in 0 until INSOLENCE_SLAMS) {
            npc.queueHit(INSOLENCE_LEAD + k * INSOLENCE_GAP) {
                if (!npc.isInWorld) return@queueHit
                npc.anim(Seqs.NPC_OCELLUS_PHASE3_MELEE_SUPERIOR_SLASH)
                val players = TfaKit.arena(npc)
                val p = players.getOrNull(npc.random(maxOf(1, players.size))) ?: victim
                npc.addLoc(INSOLENCE_LOCS[k], p.coord, FLOOR_SHAPE, 0, INSOLENCE_GAP)
                for (v in players) if (v.distanceTo(p) <= 1) CombatSystem.applyHit(v, npc, WAVE_MAX, HitStyle.TYPELESS)
            }
        }
        npc.setAttackClock(npc.tick + INSOLENCE_LEAD + INSOLENCE_SLAMS * INSOLENCE_GAP)
    }

    /** OC3: void pools under everyone but the tank (OPEN-OC15: 10 if still there 2 ticks later). */
    private fun voidPools(npc: NpcRef, tank: EntityRef) {
        for (p in TfaKit.arena(npc)) {
            if (p == tank) continue
            val at = p.coord
            npc.addLoc(Locs.TFA_OCELLUS_VOID_DEGEN_AOE, at, FLOOR_SHAPE, 0, POOL_DELAY + 1)
            npc.queueHit(POOL_DELAY) { if (npc.isInWorld && p.coord == at) CombatSystem.applyHit(p, npc, POOL_DAMAGE, HitStyle.TYPELESS) }
        }
    }

    /**
     * RuneLite (LU FinalBoss.java:803-847): a quadrant move: "Run!", the destruction 15 ticks later (anyone still near him
     * takes 50, OPEN), he lands on the next quadrant spot; 18 ticks after the line the void marks a player (spotanim
     * 4302), drops at the earliest 3 ticks later once the player has stopped 2 ticks, and explodes 2 ticks after
     * dropping (none on the move into the last quadrant). The QM3Nov Void Spin gap delays a move that overlaps it.
     */
    private fun quadrantMove(npc: NpcRef, s: State) {
        s.moves++
        s.insolenceNext = true
        npc.say("Run!")
        npc.anim(Seqs.NPC_OCELLUS_QUADRANT_DESTROY_IN)
        val spin = npc[VOID_SPIN] ?: -1L
        val wait = RUN_COUNTDOWN + if (spin >= 0 && npc.tick - spin <= PRAYER_FIRST + 1) SPIN_GAP else 0
        val at = npc.coord
        npc.queueHit(wait) {
            if (!npc.isInWorld) return@queueHit
            for (p in TfaKit.arena(npc)) {
                if (maxOf(kotlin.math.abs(p.coord.x - at.x), kotlin.math.abs(p.coord.z - at.z)) <= DESTRUCTION_RADIUS) {
                    CombatSystem.applyHit(p, npc, DESTRUCTION_DAMAGE, HitStyle.TYPELESS)
                }
            }
            s.quadrant = (s.quadrant + 1) % QUADRANTS.size
            val (dx, dz) = QUADRANTS[s.quadrant]
            npc.teleport(CoordGrid(s.centre.x + dx, s.centre.z + dz, s.centre.level))
        }
        if (s.moves < MOVES) voidMarker(npc, wait + VOID_MARK_AFTER_MOVE - RUN_COUNTDOWN)
    }

    private fun voidMarker(npc: NpcRef, after: Int) {
        npc.queueHit(after) {
            if (!npc.isInWorld) return@queueHit
            val players = TfaKit.arena(npc)
            val p = players.getOrNull(npc.random(maxOf(1, players.size))) ?: return@queueHit
            p.spotAnim(SpotAnims.VFX_OCELLUS_QUADRANT_BOMB_SPAWN_01)
            follow(npc, p, p.coord, 0, VOID_MIN_DROP)
        }
    }

    /** Watches [p] until they have stood still 2 ticks (no earlier than [wait] ticks), then drops the void there. */
    private fun follow(npc: NpcRef, p: PlayerRef, last: CoordGrid, still: Int, wait: Int) {
        npc.queueHit(1) {
            if (!npc.isInWorld || p.isDying) return@queueHit
            val now = p.coord
            val stillNow = if (now == last) still + 1 else 0
            if (wait <= 1 && stillNow >= VOID_STOP) {
                npc.addLoc(Locs.TFA_OCELLUS_VOID_DEGEN_BOMB, now, FLOOR_SHAPE, 0, VOID_FUSE + 1)
                npc.queueHit(VOID_FUSE) {
                    npc.spotAnimAt(now, SpotAnims.VFX_OCELLUS_QUADRANT_BOMB_IDLE_01)
                    for (v in TfaKit.arena(npc)) {
                        if (v.coord.x - now.x in -1..1 &&
                            v.coord.z - now.z in -1..1
                        ) {
                            CombatSystem.applyHit(v, npc, VOID_DAMAGE, HitStyle.TYPELESS)
                        }
                    }
                }
            } else {
                follow(npc, p, now, stillNow, wait - 1)
            }
        }
    }

    // ---- P4 (OC4)

    /**
     * RuneLite (LU FinalBoss.java:352-360, 677-733): a ranged deflect for 21 ticks, then a melee deflect for 19, each
     * opening with a void chain: the telegraph (spotanim 4340) on every player, the soul-fragment effect 3 ticks before
     * it, then six explosions (4339) 2 ticks apart chasing them, the first 3 ticks after the telegraph; a dodged explosion
     * cuts the player's next attack cooldown by 3. Each deflect ends with a soul-split blast (40+ unprayed, 6-7 prayed).
     * OPEN-OC8: the trainer (TR) puts the melee deflect first; the plugin order is used.
     */
    private fun p4Cycle(npc: NpcRef, s: State, ranged: Boolean) {
        if (!npc.isInWorld || s.phase != Phase.P4) return
        s.deflect = if (ranged) HitStyle.RANGED else HitStyle.MELEE
        npc.anim(Seqs.NPC_OCELLUS_PHASE3_DEFLECT_IN)
        for (p in TfaKit.arena(npc)) p.spotAnim(SpotAnims.VFX_OCELLUS_SOUL_FRAGMENT_PLAYER_IDLE)
        npc.queueHit(FRAGMENT_LEAD) { voidChain(npc) }
        val length = if (ranged) RANGED_DEFLECT else MELEE_DEFLECT
        npc.queueHit(length) {
            if (!npc.isInWorld || s.phase != Phase.P4) return@queueHit
            soulSplit(npc, if (ranged) CombatStyle.RANGED else CombatStyle.MAGIC)
            p4Cycle(npc, s, !ranged)
        }
    }

    private fun voidChain(npc: NpcRef) {
        if (!npc.isInWorld) return
        for (p in TfaKit.arena(npc)) p.spotAnim(SpotAnims.VFX_VOID_CHAIN_PLAYER_TELEGRAPH)
        for (k in 0 until CHAIN_EXPLOSIONS) {
            npc.queueHit(CHAIN_LEAD + k * CHAIN_GAP) {
                if (!npc.isInWorld) return@queueHit
                for (p in TfaKit.arena(npc)) {
                    val t = p.coord
                    npc.spotAnimAt(t, SpotAnims.VFX_VOID_CHAIN_EXPLOSION)
                    npc.queueHit(1) {
                        if (p.coord == t) {
                            CombatSystem.applyHit(p, npc, EXPLOSION, HitStyle.TYPELESS)
                        } else {
                            p.setAttackClock(maxOf(p.tick, p.attackClock() - DODGE_COOLDOWN_CUT))
                        }
                    }
                }
            }
        }
    }

    /** OC4: soul-split void blast, 40+ unprayed, 6-7 prayed; it heals him a quarter of what it deals. */
    private fun soulSplit(npc: NpcRef, style: CombatStyle) {
        for (p in TfaKit.arena(npc)) {
            val dmg =
                if (p.protects(
                        style,
                    )
                ) {
                    BossCombat.roll(npc, SOUL_PRAYED_MIN, SOUL_PRAYED_MAX)
                } else {
                    BossCombat.roll(npc, SOUL_MIN, SOUL_MAX)
                }
            CombatSystem.queueHit(p, npc, dmg, CombatSystem.hitStyle(style), BossCombat.delay(npc, p, style)) {
                npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + dmg / SOUL_SPLIT)
            }
        }
    }

    // ---- damage and phase changes

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        var d = censer(source, damage, style)
        if (s.phase == Phase.P2 && source != null) {
            // OC2: the Blade stores players' damage until its quadrant ends.
            s.stored += d
            return 0
        }
        if (s.phase == Phase.P3) return opposite(npc, source, d)
        if (s.phase != Phase.P4) return d
        if (style == s.deflect) d /= 2
        // OC4: slow weapons do less (33% less for 3t, 50% for 4t, 60% for 5t+).
        val speed = (source as? PlayerRef)?.attackSpeed() ?: return d
        return when {
            speed >= SLOW_5 -> d * (PERCENT - CUT_5) / PERCENT
            speed == SLOW_4 -> d * (PERCENT - CUT_4) / PERCENT
            speed == SLOW_3 -> d * (PERCENT - CUT_3) / PERCENT
            else -> d
        }
    }

    /** A Bandosian (melee) or Armadylean (ranged) censer's buff (OPEN-OC14: +10% damage of that style for 100 ticks). */
    private fun censer(source: EntityRef?, damage: Int, style: HitStyle): Int {
        val p = source as? PlayerRef ?: return damage
        val until = p[CENSER_UNTIL] ?: return damage
        return if (p.tick < until && p[CENSER_STYLE] == style) damage * (PERCENT + CENSER_BONUS) / PERCENT else damage
    }

    /** OC3: +10% damage from attacks made from directly opposite the tank. */
    private fun opposite(npc: NpcRef, source: EntityRef?, damage: Int): Int {
        val p = source as? PlayerRef ?: return damage
        val tank = TfaKit.firstInOrbOrder(npc) ?: return damage
        if (p == tank) return damage
        val cx = npc.coord.x + npc.size / 2
        val cz = npc.coord.z + npc.size / 2
        val opposite =
            Integer.signum(p.coord.x - cx) == -Integer.signum(tank.coord.x - cx) &&
                Integer.signum(p.coord.z - cz) == -Integer.signum(tank.coord.z - cz)
        return if (opposite) damage * (PERCENT + OPPOSITE_BONUS) / PERCENT else damage
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        val pct = npc.hitpoints * PERCENT / maxOf(1, npc.maxHitpoints)
        if (s.phase == Phase.P1 && s.centreBlasts < CENTRE_AT.size && pct <= CENTRE_AT[s.centreBlasts]) centreBlast(npc, s)
        // OC3 / RuneLite: three quadrant moves (at 75 / 50 / 25%); the last quadrant is P4.
        if (s.phase == Phase.P3 && s.moves < MOVES && pct <= QUADRANT_ENDS[s.moves]) {
            quadrantMove(npc, s)
            if (s.moves == MOVES) startP4(npc, s)
        }
    }

    private fun startP4(npc: NpcRef, s: State) {
        s.phase = Phase.P4
        npc.say("Enough!")
        // OC4: "the blue-wind orb takes half his current HP (rounded down)".
        npc.spotAnim(SpotAnims.TFA_WAVE_DUST)
        CombatSystem.applyHit(npc, null, npc.hitpoints / 2, HitStyle.TYPELESS)
        npc.queueHit(RUN_COUNTDOWN + 1) { p4Cycle(npc, s, ranged = true) }
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
    }

    override fun onLethal(npc: NpcRef): Boolean {
        val s = state(npc)
        return when (s.phase) {
            Phase.P1 -> {
                // OC2: the Blade (P2), with defence restored (the phase's own stats).
                s.phase = Phase.P2
                npc.transform(Npcs.TFA_OCELLUS_P2, restoreStats = true)
                npc.teleport(CoordGrid(s.centre.x - 1, s.centre.z - 1, s.centre.level))
                TfaKit.setPool(npc, P2_HP)
                p2Loop(npc, s)
                true
            }

            Phase.P2 -> {
                // OPEN-OC4: P3 starts when the Blade's pool is gone; half the damage it took comes off P3's pool.
                s.phase = Phase.P3
                npc.transform(Npcs.TFA_OCELLUS_P3, restoreStats = true)
                TfaKit.setPool(npc, REMAINDER_HP[s.size - MIN_SCALE])
                npc.hitpoints = maxOf(1, npc.maxHitpoints - s.bladeDamage / 2)
                npc.say("This... is enough")
                // RuneLite (LU): the fight's start quadrant; the first void marks a player 15 ticks after the line.
                val (dx, dz) = QUADRANTS[s.quadrant]
                npc.teleport(CoordGrid(s.centre.x + dx, s.centre.z + dz, s.centre.level))
                voidMarker(npc, FIRST_VOID)
                true
            }

            else -> {
                false
            }
        }
    }

    /** RuneLite (LU FinalBoss.java:1182-1190): the three orange sequences, {row, column} per volley. */
    private val SEQUENCES =
        listOf(
            listOf(1 to 1, 3 to 1, 1 to 3, 1 to 1, 2 to 2, 4 to 1),
            listOf(2 to 2, 2 to 3, 1 to 4, 2 to 2, 2 to 4, 3 to 3),
            listOf(1 to 0, 1 to 3, 1 to 5, 3 to 3, 2 to 2, 3 to 0),
        )

    /** RuneLite (LU FinalBoss.java:1820-1864): quadrant landing spots (south-west tile) from the arena centre. */
    private val QUADRANTS = listOf(-6 to -6, 6 to -6, 6 to 6, -6 to 6)
    private val CORNERS = listOf(-8 to -8, 8 to -8, 8 to 8, -8 to 8)

    /** P1 centre: the two tendrils of each quick ground attack, on neighbouring sides. */
    private val SIDE_PAIRS =
        listOf(
            (0 to 3) to (3 to 0),
            (3 to 0) to (0 to -3),
            (0 to -3) to (-3 to 0),
            (-3 to 0) to (0 to 3),
        )
    private val INSOLENCE_LOCS =
        intArrayOf(
            Locs.TFA_OCELLUS_VOID_DEGEN_AOE_INSOLENCE_1,
            Locs.TFA_OCELLUS_VOID_DEGEN_AOE_INSOLENCE_4,
            Locs.TFA_OCELLUS_VOID_DEGEN_AOE_INSOLENCE_7,
        )
    private val VOID_SPIN = AttrKey<Long>("tfa_ocellus_void_spin")

    /** The four active censers (cache gamevals `TFA_OCELLUS_ARTIFACT_*`; the inactive form is id + 1). */
    val CENSERS =
        intArrayOf(
            Locs.TFA_OCELLUS_ARTIFACT_DAMAGE_MELEE,
            Locs.TFA_OCELLUS_ARTIFACT_DAMAGE_RANGED,
            Locs.TFA_OCELLUS_ARTIFACT_SPEC,
            Locs.TFA_OCELLUS_ARTIFACT_HEAL,
        )

    /** ocellus.md: P1 HP and the "remainder" (P3) HP for 2..8 players; OPEN-OC2: P2 is a fixed 1,000 pool. */
    private val P1_HP = intArrayOf(2500, 3630, 4680, 5730, 6780, 7830, 8880)
    private val REMAINDER_HP = intArrayOf(5250, 7610, 9820, 12020, 14230, 16430, 18640)
    private const val P2_HP = 1000

    /** OC1: centre blasts at 66% and 25%; OC3: quadrant moves at 75 / 50 / 25%. */
    private val CENTRE_AT = intArrayOf(66, 25)
    private val QUADRANT_ENDS = intArrayOf(75, 50, 25)

    private const val MIN_SCALE = 2
    private const val HUNT = 24
    private const val MAX_HIT = 24
    private const val VOLLEY_SPEED = 5
    private const val CASTS_PER_CORNER = 4
    private const val SLAM_RANGE = 2
    private const val SLAM_MAX = 35
    private const val CORNER_TENDRILS = 7
    private const val TENDRIL_GAP = 2
    private const val TENDRIL_FUSE = 5
    private const val CORNER_ATTACK_SPEED = 8
    private const val TENDRIL_HIT = 5
    private const val TENDRIL_HEAL = 20
    private const val CENTRE_ATTACKS = 4
    private const val CENTRE_ATTACK_GAP = 2
    private const val FIRST_FUSE = 6
    private const val SECOND_FUSE = 8
    private const val ANNIHILATION = 80
    private const val DEATH_AWAITS = 17
    private const val P2_SET_TICKS = 5
    private const val PORTAL_TELEGRAPH = 4
    private const val PORTALS_PER_WALL = 6
    private const val VOLLEYS_PER_SEQUENCE = 6
    private const val PORTAL_DAMAGE = 20
    private const val WALL_SHAPE = 10
    private const val FLOOR_SHAPE = 22
    private const val DIMINISH = 0.8
    private const val BASICS_PER_COMBO = 2
    private const val COMBOS_PER_SPECIAL = 2
    private const val BASIC_SPEED = 4
    private const val SWEEP_SPEED = 5
    private const val HYBRID_SPEED = 7
    private const val VOID_SHOT = 20
    private const val P3_MELEE_MAX = 30
    private const val WAVE_MAX = 25
    private const val PRAYER_FIRST = 6
    private const val PRAYER_HOLD = 3
    private const val PRAYER_PUNISH = 20
    private const val UNTETHER_WAVES = 4
    private const val UNTETHER_GAP = 4
    private const val UNTETHER_LAND = 12
    private const val UNTETHER_WAVE = 15
    private const val UNTETHER_SAFE = 2
    private const val UNTETHER_DAMAGE = 40
    private const val INSOLENCE_SLAMS = 3
    private const val INSOLENCE_LEAD = 2
    private const val INSOLENCE_GAP = 5
    private const val POOL_DELAY = 2
    private const val POOL_DAMAGE = 10
    private const val MOVES = 3
    private const val RUN_COUNTDOWN = 15
    private const val SPIN_GAP = 3
    private const val DESTRUCTION_RADIUS = 3
    private const val DESTRUCTION_DAMAGE = 50
    private const val VOID_MARK_AFTER_MOVE = 18
    private const val FIRST_VOID = 15
    private const val VOID_MIN_DROP = 3
    private const val VOID_STOP = 2
    private const val VOID_FUSE = 2
    private const val VOID_DAMAGE = 30
    private const val RANGED_DEFLECT = 21
    private const val MELEE_DEFLECT = 19
    private const val FRAGMENT_LEAD = 3
    private const val CHAIN_LEAD = 3
    private const val CHAIN_GAP = 2
    private const val CHAIN_EXPLOSIONS = 6
    private const val EXPLOSION = 15
    private const val DODGE_COOLDOWN_CUT = 3
    private const val SOUL_MIN = 40
    private const val SOUL_MAX = 45
    private const val SOUL_PRAYED_MIN = 6
    private const val SOUL_PRAYED_MAX = 7
    private const val SOUL_SPLIT = 4
    private const val PERCENT = 100
    private const val OPPOSITE_BONUS = 10
    private const val CENSER_BONUS = 10
    private const val CENSER_TICKS = 100
    private const val CENSER_OUT = 4
    private const val ZAROS_SPEC = 500
    private const val SARA_RESTORE = 40
    private const val SLOW_3 = 3
    private const val SLOW_4 = 4
    private const val SLOW_5 = 5
    private const val CUT_3 = 33
    private const val CUT_4 = 50
    private const val CUT_5 = 60
}
