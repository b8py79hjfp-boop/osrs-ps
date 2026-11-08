package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Verzik Vitur, ToB room 6 (docs/mechanics/raids/tob/verzik.md VZ1-VZ7; timings runelite-timings.md and
 * capture-values.md, damage from the wiki, D-080), all three modes, as one script over her forms.
 * - P1 (throne): her shield caps hits at 10 melee / 3 magic or ranged except the Dawnbringer; a bolt at every raider
 *   every 14 ticks (first 18 after she rises; up to 137, Protect from Magic halves it) that a pillar between them takes
 *   instead (one hit a pillar an attack); a pillar at 0 collapses on those within 2 (up to 70, pushed 1, stunned). When
 *   the shield breaks every pillar falls; she becomes P2 17 ticks later (first attack +20).
 * - P2 (flying): every 4 ticks an urn bomb at each raider's tile (up to 44, Protect from Missiles halves; Hard leaves an
 *   acid pool 14 ticks), a slam on a raider beside her (45, thrown 3, stunned 5) or a stomp under her (82); every fifth
 *   attack the lightning ball, four bounces, the last for 48 (insulated boots 25), 15-20 to her when it passes through
 *   her. Combat Nylocas at the start and, after every third lightning, 25% again; an Athanatos every 21 attacks. At 35%
 *   the reds: two Matomenos (one with one raider left), 5 ticks in which damage heals her, then cycles of seven attacks
 *   with the blood spell 75% of the time (45 on a 3 x 3, healing her half). At 0 she becomes P3 6 ticks later.
 * - P3: attack every 7 ticks (enraged 5) at her one target (changed after 17 ticks out of melee; Hard each special):
 *   melee 63 on the target and everyone beside her (unprayable) when the target is in reach, else magic or ranged at
 *   everyone (33, 16 prayed). After four attacks a special in turn: Nylocas, webs, yellows, the green ball. At 20% she
 *   enrages ("I'm not finished with you just yet!"): a tornado for each raider (half their HP, at least 5, healing her
 *   three times as much; a new one 16 ticks after). Hard: at 5% once "You think this is over?" and 30% back.
 */
@Suppress("LargeClass") // one boss over three phases and their specials
internal object TobVerzik : Boss(*VERZIK_IDS) {
    private class State(val mode: Int, val home: CoordGrid) {
        var phase = 0
        var phaseStart = 0L
        var nextAttack = 0L
        var attacks = 0
        var lightnings = 0
        var reds = false
        var redsAt = 0L
        var redsAttacks = 0
        var summoningUntil = 0L
        var target: String? = null
        var outOfReachSince = -1L
        var specials = 0
        var invulnerableUntil = 0L
        var enraged = false
        var secondWind = false
        val tornadoDue = HashMap<String, Long>()
        val acid = HashMap<CoordGrid, Long>()
    }

    private val STATE = AttrKey<State>("tob_verzik")

    private fun state(npc: NpcRef): State = npc[STATE] ?: State(TobModes.of(npc.type), npc.spawn).also { npc[STATE] = it }

    /** Every mode's sitting form (Talk-to / Quick-start). */
    val INITIALS = intArrayOf(Npcs.VERZIK_INITIAL, Npcs.VERZIK_INITIAL_STORY, Npcs.VERZIK_INITIAL_HARD)

    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
    }

    /** VZ1 / TB24: talking to her (or Quick-start) begins the fight; Quick-start needs a completion and no Entry. */
    fun start(npc: NpcRef, player: PlayerRef, quick: Boolean) {
        val s = state(npc)
        if (s.phase != 0) return
        if (quick &&
            (
                s.mode == TobModes.ENTRY ||
                    player.varp(tp.symbols.Varps.TOTAL_COMPLETED_THEATREOFBLOOD) +
                    player.varp(tp.symbols.Varps.TOTAL_COMPLETED_THEATREOFBLOOD_HARD) ==
                    0
            )
        ) {
            return
        }
        s.phase = 1
        npc.anim(Seqs.VERZIK_THRONE_TRANSFORM)
        become(npc, s, P1)
        for ((dx, dz) in PILLARS) {
            Bosses.summon(npc, id(s.mode, PILLAR), CoordGrid(s.home.x + dx, s.home.z + dz, s.home.level), huntRange = 0)
        }
        s.nextAttack = npc.tick + P1_FIRST
        loop(npc, s)
    }

    private fun become(npc: NpcRef, s: State, kind: Int) {
        npc.transform(id(s.mode, kind), restoreStats = true)
        TobModes.scale(npc)
        s.phaseStart = npc.tick
    }

    /**
     * Client contract 4: her forms from the sitting form (initial, P1, P1-P2, P2, P2-P3, P3, bat, web, collapsing pillar,
     * rubble, pillar); Normal has the throne NPC (8380) before the Nylocas, so its minion ids sit one higher.
     */
    fun id(mode: Int, kind: Int): Int {
        val base =
            when (mode) {
                TobModes.ENTRY -> Npcs.VERZIK_INITIAL_STORY
                TobModes.HARD -> Npcs.VERZIK_INITIAL_HARD
                else -> Npcs.VERZIK_INITIAL
            }
        return base + kind + if (kind > PILLAR && mode == TobModes.NORMAL) 1 else 0
    }

    private fun raiders(npc: NpcRef): List<PlayerRef> = TfaKit.arena(npc, ARENA)

    private fun centre(npc: NpcRef) = CoordGrid(npc.coord.x + npc.size / 2, npc.coord.z + npc.size / 2, npc.coord.level)

    private fun loop(npc: NpcRef, s: State) {
        npc.queueHit(1, strong = false) {
            if (!npc.isInWorld) return@queueHit
            tick(npc, s)
            loop(npc, s)
        }
    }

    private fun tick(npc: NpcRef, s: State) {
        if (s.phase >= 2 && npc.level(Stats.DEFENCE) < npc.baseLevel(Stats.DEFENCE)) {
            // VZ-data: "Verzik Vitur absorbs the defence reduction!"
            npc.setLevel(Stats.DEFENCE, npc.baseLevel(Stats.DEFENCE))
        }
        acidTick(npc, s)
        if (npc.tick < s.nextAttack) {
            if (s.phase == P3_PHASE) tornadoes(npc, s)
            return
        }
        when (s.phase) {
            1 -> p1Attack(npc, s)
            2 -> p2Attack(npc, s)
            P3_PHASE -> p3Attack(npc, s)
        }
        if (s.phase == P3_PHASE) tornadoes(npc, s)
    }

    // ---- P1 (VZ2)

    private fun p1Attack(npc: NpcRef, s: State) {
        s.nextAttack = npc.tick + P1_SPEED
        npc.anim(Seqs.VERZIK_PHASE1_ATTACK_MAGIC)
        val c = centre(npc)
        val pillars = Bosses.minions(npc).filter { it.type == id(s.mode, PILLAR) }
        val struck = HashSet<NpcRef>()
        for (p in raiders(npc)) {
            val d = maxOf(kotlin.math.abs(p.coord.x - c.x), kotlin.math.abs(p.coord.z - c.z))
            val end = P1_END_BASE + P1_END_PER_TILE * d
            val cover = TfaKit.line(c, p.coord).firstNotNullOfOrNull { t -> pillars.firstOrNull { TfaKit.under(it, t) } }
            val raw = BossCombat.roll(npc, 0, TobModes.damage(s.mode, if (s.mode == TobModes.ENTRY) ENTRY_P1_MAX * 2 else P1_MAX))
            if (cover != null) {
                if (!struck.add(cover)) continue
                npc.projectile(cover, SpotAnims.VERZIK_PHASE1_PROJECTILE, PROJ_HEIGHT, PROJ_HEIGHT, P1_END_BASE, end)
                npc.queueHit(end / CYCLES_PER_TICK + 1, strong = false) { pillarHit(npc, s, cover, raw) }
                continue
            }
            npc.projectile(p, SpotAnims.VERZIK_PHASE1_PROJECTILE, PROJ_HEIGHT, PROJ_HEIGHT, P1_END_BASE, end)
            val dmg = if (p.protects(CombatStyle.MAGIC)) raw / 2 else raw
            CombatSystem.queueHit(p, npc, dmg, HitStyle.MAGIC, end / CYCLES_PER_TICK + 1)
        }
    }

    /** VZ2 / OPEN-VZ4: a pillar takes the bolt's damage; Hard drops rubble near it (35, stun). */
    private fun pillarHit(npc: NpcRef, s: State, pillar: NpcRef, damage: Int) {
        if (!pillar.isInWorld) return
        pillar.spotAnim(SpotAnims.VERZIK_PHASE1_IMPACTPILLAR)
        if (s.mode == TobModes.HARD) {
            repeat(HARD_RUBBLE) {
                val t = BossCombat.randomTile(npc, pillar.coord, HARD_RUBBLE_REACH)
                npc.spotAnimAt(t, SpotAnims.VERZIK_PHASE1_IMPACT)
                for (p in raiders(npc)) {
                    if (p.coord != t) continue
                    CombatSystem.applyHit(p, npc, BossCombat.roll(npc, 0, HARD_RUBBLE_MAX), HitStyle.TYPELESS)
                    p.frozenUntil = maxOf(p.frozenUntil, p.tick + STUN)
                }
            }
        }
        pillar.hitpoints = maxOf(0, pillar.hitpoints - damage)
        if (pillar.hitpoints == 0) collapse(npc, s, pillar)
    }

    /** VZ2 / capture: a collapsing pillar hits raiders within 2 of its centre (up to 70; Entry 50), pushed 1 and stunned. */
    private fun collapse(npc: NpcRef, s: State, pillar: NpcRef) {
        pillar.anim(Seqs.VERZIK_PILLAR_COLLAPSE)
        val c = centre(pillar)
        for (p in raiders(npc)) {
            if (maxOf(kotlin.math.abs(p.coord.x - c.x), kotlin.math.abs(p.coord.z - c.z)) > COLLAPSE_REACH) continue
            val max = if (s.mode == TobModes.ENTRY) ENTRY_COLLAPSE_MAX else COLLAPSE_MAX
            CombatSystem.applyHit(p, npc, BossCombat.roll(npc, 0, max), HitStyle.TYPELESS)
            p.teleport(CoordGrid(p.coord.x + Integer.signum(p.coord.x - c.x), p.coord.z + Integer.signum(p.coord.z - c.z), p.coord.level))
            p.frozenUntil = maxOf(p.frozenUntil, p.tick + STUN)
        }
        pillar.transform(id(s.mode, COLLAPSING), restoreStats = false)
        pillar.queueHit(PILLAR_GONE, strong = false) { if (pillar.isInWorld) pillar.despawn() }
    }

    // ---- P2 (VZ3)

    private fun startP2(npc: NpcRef, s: State) {
        s.phase = 2
        s.attacks = 0
        npc.transform(id(s.mode, P1_TO_P2), restoreStats = false)
        s.nextAttack = npc.tick + P2_FIRST_FROM_P1
        npc.queueHit(P2_FORM_AT - P1_TO_P2_AT, strong = false) {
            if (!npc.isInWorld) return@queueHit
            become(npc, s, P2)
            npc.anim(Seqs.VERZIK_PHASE2_SPAWN)
            npc.teleport(CoordGrid(s.home.x + 1, s.home.z + 1, s.home.level))
            summonNylocas(npc, s)
        }
    }

    private fun p2Attack(npc: NpcRef, s: State) {
        val pct = npc.hitpoints * PERCENT / maxOf(1, npc.maxHitpoints)
        if (!s.reds && pct <= REDS_AT) {
            reds(npc, s)
            return
        }
        if (s.reds && s.redsAttacks >= REDS_CYCLE) {
            reds(npc, s)
            return
        }
        s.nextAttack = npc.tick + P2_SPEED
        s.attacks++
        if (s.reds) {
            s.redsAttacks++
            if (npc.random(PERCENT) < BLOOD_CHANCE) {
                bloodSpell(npc, s)
                return
            }
        }
        if (s.attacks % LIGHTNING_EVERY == 0) {
            lightning(npc, s)
            return
        }
        if (!s.reds && s.attacks % ATHANATOS_EVERY == 0 && Bosses.minions(npc).none { it.type == id(s.mode, ATHANATOS) }) athanatos(npc, s)
        bombs(npc, s)
    }

    /** VZ3: an urn bomb at every raider's tile (a slam on one beside her, a stomp on one under her). */
    private fun bombs(npc: NpcRef, s: State) {
        npc.anim(Seqs.VERZIK_PHASE2_ATTACK_MAGIC)
        val c = centre(npc)
        for (p in raiders(npc)) {
            val gap = TfaKit.gap(npc, p)
            when {
                gap == 0 -> {
                    p.message("There's nothing for you there!")
                    CombatSystem.applyHit(p, npc, TobModes.damage(s.mode, BossCombat.roll(npc, 1, STOMP_MAX)), HitStyle.MELEE)
                }

                gap == 1 -> {
                    npc.anim(Seqs.VERZIK_PHASE2_ATTACK_MELEE)
                    CombatSystem.applyHit(p, npc, TobModes.damage(s.mode, BossCombat.roll(npc, 0, SLAM_MAX)), HitStyle.MELEE)
                    p.teleport(
                        CoordGrid(
                            p.coord.x + KNOCKBACK * Integer.signum(p.coord.x - c.x),
                            p.coord.z + KNOCKBACK * Integer.signum(p.coord.z - c.z),
                            p.coord.level,
                        ),
                    )
                    p.frozenUntil = maxOf(p.frozenUntil, p.tick + STUN)
                }

                else -> {
                    val tile = p.coord
                    val d = maxOf(kotlin.math.abs(tile.x - c.x), kotlin.math.abs(tile.z - c.z))
                    val end = CYCLES_PER_TICK * (2 * d / 3)
                    npc.projectile(p, SpotAnims.VERZIK_PHASE2_RANGED, PROJ_HEIGHT, 0, 0, maxOf(CYCLES_PER_TICK, end))
                    npc.queueHit(maxOf(1, end / CYCLES_PER_TICK), strong = false) { bombLands(npc, s, tile) }
                }
            }
        }
    }

    private fun bombLands(npc: NpcRef, s: State, tile: CoordGrid) {
        if (!npc.isInWorld) return
        npc.spotAnimAt(tile, SpotAnims.VERZIK_RANGED_IMPACT)
        val max = if (s.mode == TobModes.HARD) HARD_BOMB_MAX else TobModes.damage(s.mode, BOMB_MAX)
        for (p in raiders(npc)) {
            if (p.coord != tile) continue
            val raw = BossCombat.roll(npc, 0, max)
            CombatSystem.applyHit(p, npc, if (p.protects(CombatStyle.RANGED)) raw / 2 else raw, HitStyle.RANGED)
        }
        if (s.mode == TobModes.HARD) {
            s.acid[tile] = npc.tick + ACID_LIFE
            npc.addLoc(Locs.VERZIK_ACID_POOL, tile, FLOOR_SHAPE, 0, ACID_LIFE)
        }
    }

    private fun acidTick(npc: NpcRef, s: State) {
        if (s.acid.isEmpty()) return
        s.acid.entries.removeAll { it.value <= npc.tick }
        for (p in raiders(npc)) {
            if (p.coord in
                s.acid
            ) {
                CombatSystem.applyHit(p, npc, BossCombat.roll(npc, ACID_MIN, ACID_MAX), HitStyle.TYPELESS)
            }
        }
    }

    /** VZ3: the lightning ball bounces between raiders 4 times; the last takes it; she takes 15-20 when it crosses her. */
    private fun lightning(npc: NpcRef, s: State) {
        s.lightnings++
        val raiders = raiders(npc)
        if (raiders.isEmpty()) return
        npc.anim(Seqs.VERZIK_PHASE2_ATTACK_MAGIC)
        var from = centre(npc)
        var at = 0
        var last: PlayerRef = raiders[npc.random(raiders.size)]
        for (hop in 0 until LIGHTNING_HOPS) {
            val to = if (raiders.size == 1) last else raiders.filter { it != last || hop == 0 }.let { it[npc.random(it.size)] }
            val crosses = TfaKit.line(from, to.coord).any { TfaKit.under(npc, it) } && hop > 0
            at +=
                maxOf(
                    1,
                    CYCLES_PER_TICK *
                        (2 * maxOf(1, from.let { f -> maxOf(kotlin.math.abs(to.coord.x - f.x), kotlin.math.abs(to.coord.z - f.z)) }) / 3) /
                        CYCLES_PER_TICK,
                )
            if (crosses) {
                npc.queueHit(at, strong = false) {
                    if (npc.isInWorld) CombatSystem.applyHit(npc, null, BossCombat.roll(npc, SELF_MIN, SELF_MAX), HitStyle.TYPELESS)
                }
            }
            from = to.coord
            last = to
            if (raiders.size == 1) break
        }
        val victim = last
        npc.queueHit(at, strong = false) {
            if (!npc.isInWorld) return@queueHit
            victim.spotAnim(SpotAnims.VERZIK_PHASE2_LIGHTNING)
            val boots = victim.worn(Wear.FEET) == INSULATED_BOOTS
            val max = if (boots) BOOTS_MAX else TobModes.damage(s.mode, LIGHTNING_MAX)
            CombatSystem.applyHit(victim, npc, BossCombat.roll(npc, 0, max), HitStyle.TYPELESS)
        }
        val resummon = !s.reds && s.lightnings % RESUMMON_EVERY == 0
        if (resummon && npc.random(RESUMMON_ODDS) == 0 && Bosses.minions(npc).none { it.type == id(s.mode, ATHANATOS) }) {
            summonNylocas(npc, s)
        }
    }

    /** VZ3 / OPEN-VZ14: one combat Nylocas per raider, homing on a random raider; up to 46 beside it, 8 within 3. */
    private fun summonNylocas(npc: NpcRef, s: State) {
        val raiders = raiders(npc)
        for (k in raiders.indices) {
            val at = BossCombat.randomTile(npc, centre(npc), NYLO_SPREAD)
            val nylo = Bosses.summon(npc, id(s.mode, NYLO_MELEE + npc.random(NYLO_STYLES)), at, huntRange = 0, inheritRun = true)
            TobModes.scale(nylo)
            val target = raiders[npc.random(raiders.size)]
            homing(npc, s, nylo, target, 0)
        }
    }

    private fun homing(npc: NpcRef, s: State, nylo: NpcRef, target: PlayerRef, age: Int) {
        nylo.queueHit(1, strong = false) {
            if (!nylo.isInWorld || !npc.isInWorld) return@queueHit
            if (age >= NYLO_LIFE) {
                nylo.despawn()
                return@queueHit
            }
            if (TfaKit.gap(nylo, target) <= 1) {
                for (p in raiders(npc)) {
                    val g = TfaKit.gap(nylo, p)
                    val max =
                        if (g <= 1) {
                            NYLO_EXPLODE_MAX
                        } else if (g <= NYLO_FAR) {
                            NYLO_FAR_MAX
                        } else {
                            0
                        }
                    if (max > 0) CombatSystem.applyHit(p, nylo, TobModes.damage(s.mode, BossCombat.roll(npc, 0, max)), HitStyle.TYPELESS)
                }
                nylo.despawn()
                return@queueHit
            }
            nylo.walkTo(target.coord)
            homing(npc, s, nylo, target, age + 1)
        }
    }

    /** VZ3 / capture: the Athanatos lands on a raider (78; half beside), then heals her 9-10 every 4 ticks. */
    private fun athanatos(npc: NpcRef, s: State) {
        val raiders = raiders(npc)
        val p = raiders.getOrNull(npc.random(maxOf(1, raiders.size))) ?: return
        val tile = p.coord
        val c = centre(npc)
        val d = maxOf(kotlin.math.abs(tile.x - c.x), kotlin.math.abs(tile.z - c.z))
        val hatch = maxOf(1, (ATHANATOS_CYCLES * (2 * d / 3)) / CYCLES_PER_TICK)
        npc.projectile(p, SpotAnims.VERZIK_PHASE2_SPAWN_ARMOUREDTANK_PROJ, PROJ_HEIGHT, 0, 0, hatch * CYCLES_PER_TICK)
        npc.queueHit(hatch, strong = false) {
            if (!npc.isInWorld) return@queueHit
            for (v in raiders(npc)) {
                val g = maxOf(kotlin.math.abs(v.coord.x - tile.x), kotlin.math.abs(v.coord.z - tile.z))
                if (g <=
                    1
                ) {
                    CombatSystem.applyHit(
                        v,
                        npc,
                        TobModes.damage(
                            s.mode,
                            BossCombat.roll(
                                npc,
                                0,
                                if (g ==
                                    0
                                ) {
                                    ATHANATOS_MAX
                                } else {
                                    ATHANATOS_MAX / 2
                                },
                            ),
                        ),
                        HitStyle.TYPELESS,
                    )
                }
            }
            val a = Bosses.summon(npc, id(s.mode, ATHANATOS), tile, huntRange = 0, inheritRun = true)
            TobModes.scale(a)
            athanatosHeal(npc, a)
        }
    }

    private fun athanatosHeal(npc: NpcRef, a: NpcRef) {
        a.queueHit(ATHANATOS_HEAL_EVERY, strong = false) {
            if (!a.isInWorld || !npc.isInWorld) return@queueHit
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + BossCombat.roll(npc, ATHANATOS_HEAL_MIN, ATHANATOS_HEAL_MAX))
            athanatosHeal(npc, a)
        }
    }

    /** VZ3: a poison / venom weapon, a smoke spell or the serpentine helm bursts the Athanatos: up to 70 to her. */
    fun athanatosHit(a: NpcRef, p: PlayerRef) {
        val owner = a[Bosses.OWNER] ?: return
        val spell = p[Attr.LAST_SPELL]
        val poisonous = Toxic.wearsSerpentineHelm(p) || spell?.startsWith("Smoke") == true || p.worn(Wear.WEAPON) in POISON_WEAPONS
        if (!poisonous) return
        CombatSystem.applyHit(owner, p, BossCombat.roll(owner, 0, ATHANATOS_BURST_MAX), HitStyle.TYPELESS)
        a.despawn()
    }

    /** VZ3 / capture: the reds at 35% and every seven attacks after: two Matomenos, 5 ticks in which hits heal her. */
    private fun reds(npc: NpcRef, s: State) {
        s.reds = true
        s.redsAttacks = 0
        npc.anim(Seqs.VERZIK_PHASE2_HEAL)
        // She ends the last set: their HP back to her (Hard: they explode on raiders beside them).
        for (m in Bosses.minions(npc).filter { it.type == id(s.mode, MATOMENOS) }) {
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + m.hitpoints)
            if (s.mode ==
                TobModes.HARD
            ) {
                for (p in raiders(npc)) {
                    if (TfaKit.gap(m, p) <=
                        1
                    ) {
                        CombatSystem.applyHit(p, m, m.hitpoints / 2, HitStyle.TYPELESS)
                    }
                }
            }
            m.despawn()
        }
        val n = if (raiders(npc).size <= 1) 1 else 2
        for (k in 0 until n) {
            val side = if (k == 0) -MATOMENOS_SIDE else MATOMENOS_SIDE + npc.size - 1
            val m =
                Bosses.summon(
                    npc,
                    id(s.mode, MATOMENOS),
                    CoordGrid(npc.coord.x + side, npc.coord.z, npc.coord.level),
                    huntRange = 0,
                    inheritRun = true,
                )
            TobModes.scale(m)
        }
        s.summoningUntil = npc.tick + SUMMONING
        s.nextAttack = npc.tick + REDS_TO_ATTACK
    }

    /** VZ3: the blood spell on a 3 x 3 around one raider (45), healing her half the total (prayer blocks, still heals). */
    private fun bloodSpell(npc: NpcRef, s: State) {
        val raiders = raiders(npc)
        val t = raiders.getOrNull(npc.random(maxOf(1, raiders.size))) ?: return
        val tile = t.coord
        npc.anim(Seqs.VERZIK_PHASE2_ATTACK_MAGIC)
        npc.projectile(t, SpotAnims.VERZIK_PHASE2_BLOODPROJ, PROJ_HEIGHT, 0, 0, CYCLES_PER_TICK * 2)
        npc.queueHit(2, strong = false) {
            if (!npc.isInWorld) return@queueHit
            npc.spotAnimAt(tile, SpotAnims.VERZIK_PHASE2_BLOODIMPACT)
            var would = 0
            for (p in raiders(npc)) {
                if (kotlin.math.abs(p.coord.x - tile.x) > 1 || kotlin.math.abs(p.coord.z - tile.z) > 1) continue
                val raw = TobModes.damage(s.mode, BossCombat.roll(npc, 0, BLOOD_MAX))
                would += raw
                CombatSystem.applyHit(p, npc, if (p.protects(CombatStyle.MAGIC)) 0 else raw, HitStyle.MAGIC)
            }
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + would / 2)
        }
    }

    private fun startP3(npc: NpcRef, s: State) {
        s.phase = P3_PHASE
        Bosses.dismissMinions(npc)
        npc.transform(id(s.mode, P2_TO_P3), restoreStats = false)
        s.nextAttack = npc.tick + P3_FIRST_FROM_P2
        npc.queueHit(P3_FORM_AT, strong = false) {
            if (!npc.isInWorld) return@queueHit
            become(npc, s, P3)
            npc.anim(Seqs.VERZIK_PHASE3_SPAWN)
            npc.say("Behold my true nature!")
        }
    }

    // ---- P3 (VZ4, VZ5)

    private fun p3Attack(npc: NpcRef, s: State) {
        val raiders = raiders(npc)
        if (raiders.isEmpty()) {
            s.nextAttack = npc.tick + 1
            return
        }
        val target = pickTarget(npc, s, raiders)
        val speed = if (s.enraged) ENRAGED_SPEED else P3_SPEED
        s.nextAttack = npc.tick + speed
        s.attacks++
        if (s.attacks % SPECIAL_EVERY == 0) {
            val special = s.specials++ % SPECIALS
            if (s.mode == TobModes.HARD) s.target = raiders[npc.random(raiders.size)].username
            when (special) {
                0 -> {
                    summonNylocas(npc, s)
                    normalAttack(npc, s, target, raiders)
                }

                1 -> {
                    webs(npc, s, raiders)
                }

                2 -> {
                    yellows(npc, s, raiders)
                }

                else -> {
                    greenBall(npc, s, target, raiders)
                    s.nextAttack = npc.tick + GREEN_NEXT
                }
            }
            return
        }
        normalAttack(npc, s, target, raiders)
    }

    /** VZ4: one target for the phase, changed after 17 ticks out of melee reach (Hard: at each special). */
    private fun pickTarget(npc: NpcRef, s: State, raiders: List<PlayerRef>): PlayerRef {
        var t = raiders.firstOrNull { it.username == s.target }
        if (t == null) {
            t = raiders[npc.random(raiders.size)]
            s.target = t.username
            s.outOfReachSince = -1
        }
        val reach = TfaKit.gap(npc, t) == 1
        if (reach) {
            s.outOfReachSince = -1
        } else if (s.outOfReachSince < 0) {
            s.outOfReachSince = npc.tick
        } else if (npc.tick - s.outOfReachSince >= RETARGET) {
            t = raiders[npc.random(raiders.size)]
            s.target = t.username
            s.outOfReachSince = -1
        }
        return t
    }

    private fun normalAttack(npc: NpcRef, s: State, target: PlayerRef, raiders: List<PlayerRef>) {
        if (TfaKit.gap(npc, target) == 1) {
            npc.anim(Seqs.VERZIK_PHASE3_ATTACK_MELEE)
            for (p in raiders) {
                if (p == target ||
                    TfaKit.gap(npc, p) == 1
                ) {
                    CombatSystem.queueHit(p, npc, TobModes.damage(s.mode, BossCombat.roll(npc, 0, P3_MELEE_MAX)), HitStyle.MELEE, 1)
                }
            }
            return
        }
        val magic = npc.random(2) == 0
        val style = if (magic) CombatStyle.MAGIC else CombatStyle.RANGED
        npc.anim(if (magic) Seqs.VERZIK_PHASE3_ATTACK_MAGIC else Seqs.VERZIK_PHASE3_ATTACK_RANGED)
        val c = centre(npc)
        val max = TobModes.damage(s.mode, if (s.enraged) ENRAGED_MAX else P3_RANGED_MAX)
        for (p in raiders) {
            val d = maxOf(kotlin.math.abs(p.coord.x - c.x), kotlin.math.abs(p.coord.z - c.z))
            val end = minOf((if (magic) MAGIC_START else RANGED_START) + P3_PER_TILE * d, P3_END_CAP)
            npc.projectile(
                p,
                if (magic) SpotAnims.VERZIK_PHASE3_MAGEPROJ else SpotAnims.VERZIK_PHASE3_RANGEPROJ,
                PROJ_HEIGHT,
                PROJ_HEIGHT,
                0,
                end,
            )
            val raw = BossCombat.roll(npc, 0, max)
            CombatSystem.queueHit(
                p,
                npc,
                if (p.protects(style)) minOf(raw, P3_PRAYED_MAX) else raw,
                CombatSystem.hitStyle(style),
                end / CYCLES_PER_TICK,
            )
        }
    }

    /** VZ4 webs: to the middle, untouchable; three webs a volley at raiders not stacked; next attack 10 ticks after. */
    private fun webs(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        npc.anim(Seqs.VERZIK_PHASE3_ATTACK_WEBSPIN)
        npc.teleport(CoordGrid(s.home.x - 1, s.home.z - 1, s.home.level))
        s.invulnerableUntil = npc.tick + WEB_SPIN
        for (v in 0 until WEB_VOLLEYS) {
            npc.queueHit(1 + v * WEB_VOLLEY_GAP, strong = false) {
                if (!npc.isInWorld) return@queueHit
                val stacked = raiders.map { it.coord }.distinct().size == 1 && raiders.size > 1
                for (p in raiders(npc)) {
                    repeat(if (stacked) 1 else WEBS_PER_RAIDER) { k ->
                        val tile = if (k == 0) p.coord else BossCombat.randomTile(npc, p.coord, 1)
                        val d = TfaKit.gap(npc, p)
                        val land = maxOf(1, CYCLES_PER_TICK * (d / 2) / CYCLES_PER_TICK)
                        npc.projectile(p, SpotAnims.VERZIK_P3_WEB_PROJ, PROJ_HEIGHT, 0, 0, land * CYCLES_PER_TICK)
                        npc.queueHit(land, strong = false) { web(npc, s, tile) }
                    }
                }
            }
        }
        s.nextAttack = npc.tick + WEB_SPIN + WEB_NEXT
    }

    /** A web (10 HP) binds whoever stands on it; unbroken it snaps (OPEN-VZ11: 30; aranea boots once every 8 ticks). */
    private fun web(npc: NpcRef, s: State, tile: CoordGrid) {
        if (!npc.isInWorld) return
        val web = Bosses.summon(npc, id(s.mode, WEB), tile, huntRange = 0)
        val caught = raiders(npc).filter { it.coord == tile }
        for (p in caught) p.frozenUntil = maxOf(p.frozenUntil, p.tick + WEB_HOLD)
        web.queueHit(WEB_HOLD, strong = false) {
            if (!web.isInWorld) return@queueHit
            for (p in caught) {
                if (p.coord != tile) continue
                val last = p[ARANEA_AT] ?: -ARANEA_COOLDOWN.toLong()
                if (p.worn(Wear.FEET) == Objs.ARANEA_BOOTS && p.tick - last >= ARANEA_COOLDOWN) {
                    p[ARANEA_AT] = p.tick
                } else {
                    CombatSystem.applyHit(p, npc, TobModes.damage(s.mode, WEB_SNAP), HitStyle.TYPELESS)
                }
            }
            web.despawn()
        }
    }

    /** VZ4 / RuneLite: yellows: one safe pool a raider, landing 14 ticks after (Hard three waves 3 apart); 80 off a pool. */
    private fun yellows(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        npc.anim(Seqs.VERZIK_PHASE3_ATTACK_POWERBLAST)
        val waves = if (s.mode == TobModes.HARD) HARD_YELLOW_WAVES else 1
        s.invulnerableUntil = npc.tick + YELLOWS_LAND + (waves - 1) * HARD_YELLOW_GAP
        for (w in 0 until waves) {
            val pools = raiders.map { BossCombat.randomTile(npc, CoordGrid(s.home.x + 2, s.home.z + 2, s.home.level), YELLOW_SPREAD) }
            for (t in pools) npc.spotAnimAt(t, SpotAnims.VERZIK_POWERBLAST_SAFEZONE)
            npc.queueHit(YELLOWS_LAND + w * HARD_YELLOW_GAP, strong = false) {
                if (!npc.isInWorld) return@queueHit
                val now = raiders(npc)
                for (p in now) {
                    val pool = pools.firstOrNull { it == p.coord }
                    val alone = pool != null && now.count { it.coord == pool } == 1
                    if (alone) {
                        p.spotAnim(SpotAnims.VERZIK_POWERBLAST_PROTECTION)
                    } else {
                        CombatSystem.applyHit(p, npc, TobModes.damage(s.mode, BossCombat.roll(npc, 0, YELLOW_MAX)), HitStyle.TYPELESS)
                    }
                }
            }
        }
        s.nextAttack = npc.tick + if (s.mode == TobModes.HARD) HARD_YELLOWS_NEXT else YELLOWS_NEXT
    }

    /**
     * VZ4 / capture: the green ball (start 62, end 242) at the target for 75% of their Hitpoints level; it bounces to a
     * raider beside the holder, at most three times; nobody beside, or back to someone it hit = it explodes. Hard
     * (OPEN-VZ6): unbounced with two or more raiders it kills; else 89 / 74 / 49 after 1 / 2 / 3 bounces.
     */
    private fun greenBall(npc: NpcRef, s: State, target: PlayerRef, raiders: List<PlayerRef>) {
        npc.anim(Seqs.VERZIK_PHASE3_ATTACK_RANGED)
        target.message("<col=0a721f>Verzik Vitur fires a powerful projectile in your direction...</col>")
        npc.projectile(target, SpotAnims.VERZIK_ACIDBOMB_PROJANIM, PROJ_HEIGHT, PROJ_HEIGHT, GREEN_START, GREEN_END)
        npc.queueHit(GREEN_END / CYCLES_PER_TICK, strong = false) { greenLands(npc, s, target, HashSet(), raiders.size) }
    }

    private fun greenLands(npc: NpcRef, s: State, holder: PlayerRef, hit: MutableSet<String>, partySize: Int) {
        if (!npc.isInWorld || holder.isDying) return
        val bounces = hit.size
        hit += holder.username
        val next = raiders(npc).firstOrNull { it != holder && it.distanceTo(holder) <= 1 && it.username !in hit }
        if (next != null && bounces < GREEN_BOUNCES) {
            next.message("<col=0a721f>A powerful projectile bounces into your direction...</col>")
            holder.projectile(next, SpotAnims.VERZIK_ACIDBOMB_PROJANIM, PROJ_HEIGHT, PROJ_HEIGHT, 0, CYCLES_PER_TICK * GREEN_BOUNCE_TICKS)
            npc.queueHit(GREEN_BOUNCE_TICKS, strong = false) { greenLands(npc, s, next, hit, partySize) }
            return
        }
        holder.spotAnim(SpotAnims.VERZIK_ACIDBOMB_IMPACT)
        val dmg =
            if (s.mode == TobModes.HARD && partySize >= 2) {
                if (bounces == 0) holder.hitpoints else HARD_GREEN[(bounces - 1).coerceIn(0, HARD_GREEN.size - 1)]
            } else if (bounces >= GREEN_BOUNCES) {
                0
            } else {
                TobModes.damage(s.mode, holder.baseLevel(Stats.HITPOINTS) * GREEN_PERCENT / PERCENT)
            }
        if (dmg > 0) CombatSystem.applyHit(holder, npc, dmg, HitStyle.TYPELESS)
    }

    /** VZ5 / capture: a tornado per raider, a tile a tick toward its own raider; on touch half their HP (at least 5). */
    private fun tornadoes(npc: NpcRef, s: State) {
        if (!s.enraged) return
        val raiders = raiders(npc)
        for (p in raiders) {
            val tornado = Bosses.minions(npc).firstOrNull { it.type == id(s.mode, CREEPER) && it[TORNADO_FOR] == p.username }
            if (tornado == null) {
                if (npc.tick >= (s.tornadoDue[p.username] ?: 0L)) {
                    val t = Bosses.summon(npc, id(s.mode, CREEPER), centre(npc), huntRange = 0)
                    t[TORNADO_FOR] = p.username
                }
                continue
            }
            if (TfaKit.gap(tornado, p) == 0 || tornado.coord == p.coord) {
                val dmg = maxOf(TORNADO_MIN, p.hitpoints / 2)
                p.spotAnim(SpotAnims.VERZIK_P3_CREEPER_SPOT)
                CombatSystem.applyHit(p, npc, dmg, HitStyle.TYPELESS)
                npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + dmg * TORNADO_HEAL)
                tornado.despawn()
                s.tornadoDue[p.username] = npc.tick + TORNADO_NEXT
            } else {
                tornado.walkTo(p.coord)
            }
        }
    }

    // ---- damage and phase changes

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        if (s.phase == 0 || npc.tick < s.invulnerableUntil) return 0
        if (s.phase == 2 && npc.tick < s.summoningUntil && damage > 0) {
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + damage)
            return 0
        }
        if (s.phase != 1) return damage
        val p = source as? PlayerRef ?: return damage
        if (p.worn(Wear.WEAPON) == Objs.VERZIK_SPECIAL_WEAPON) return damage
        return minOf(damage, if (style == HitStyle.MELEE) P1_MELEE_CAP else P1_RANGED_CAP)
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        if (s.phase != P3_PHASE) return
        val pct = npc.hitpoints * PERCENT / maxOf(1, npc.maxHitpoints)
        if (!s.enraged && pct <= ENRAGE_AT) {
            s.enraged = true
            npc.say("I'm not finished with you just yet!")
            if (s.nextAttack - npc.tick > ENRAGED_SPEED) s.nextAttack = npc.tick + ENRAGED_SPEED
        }
        if (s.mode == TobModes.HARD && !s.secondWind && pct <= SECOND_WIND_AT) {
            s.secondWind = true
            npc.say("You think this is over?")
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + npc.maxHitpoints * SECOND_WIND_HEAL / PERCENT)
        }
    }

    override fun onLethal(npc: NpcRef): Boolean {
        val s = state(npc)
        return when (s.phase) {
            1 -> {
                npc.anim(Seqs.VERZIK_PHASE1_DEATH)
                for (pillar in Bosses.minions(npc).filter { it.type == id(s.mode, PILLAR) }) collapse(npc, s, pillar)
                npc.hitpoints = 1
                s.phase = TRANSITION
                npc.queueHit(P1_TO_P2_AT, strong = false) { if (npc.isInWorld) startP2(npc, s) }
                true
            }

            2 -> {
                npc.anim(Seqs.VERZIK_PHASE2_DEATH)
                npc.hitpoints = 1
                s.phase = TRANSITION
                npc.queueHit(P2_TO_P3_AT, strong = false) { if (npc.isInWorld) startP3(npc, s) }
                true
            }

            TRANSITION -> {
                npc.hitpoints = 1
                true
            }

            else -> {
                false
            }
        }
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) = Unit

    override fun died(npc: NpcRef, killer: EntityRef?) {
        npc.anim(Seqs.VERZIK_PHASE3_DEATH_A)
        Bosses.dismissMinions(npc)
    }

    private val TORNADO_FOR = AttrKey<String>("tob_verzik_tornado_for")
    private val ARANEA_AT = AttrKey<Long>("tob_verzik_aranea_at")

    /** VZ-data: six pillars (template x 3162 / 3174, z 4307 / 4313 / 4319) from her south-west tile (3166, 4312). */
    private val PILLARS = listOf(-4 to -5, 8 to -5, -4 to 1, 8 to 1, -4 to 7, 8 to 7)
    private val HARD_GREEN = intArrayOf(89, 74, 49)

    /** OPEN-VZ5: weapons that count as poisonous for the Athanatos (the common poison / venom weapons). */
    private val POISON_WEAPONS =
        setOf(
            Objs.TOXIC_BLOWPIPE_LOADED,
            Objs.TOXIC_BLOWPIPE_LOADED_ORNAMENT,
            Objs.TOXIC_SOTD_CHARGED,
            Objs.TOXIC_TOTS_CHARGED,
            Objs.TOXIC_TOTS_I_CHARGED,
            Objs.DRAGON_DAGGER_P,
            Objs.DRAGON_DAGGER_P_,
            Objs.DRAGON_DAGGER_P__,
            Objs.ABYSSAL_DAGGER_P,
            Objs.ABYSSAL_DAGGER_P_,
            Objs.ABYSSAL_DAGGER_P__,
        )

    // Form offsets from the sitting form.
    private const val P1 = 1
    private const val P1_TO_P2 = 2
    private const val P2 = 3
    private const val P2_TO_P3 = 4
    private const val P3 = 5
    private const val WEB = 7
    private const val COLLAPSING = 8
    private const val PILLAR = 10
    private const val NYLO_MELEE = 11
    private const val NYLO_STYLES = 3
    private const val ATHANATOS = 14
    private const val MATOMENOS = 15
    private const val CREEPER = 16

    private const val TRANSITION = 9
    private const val P3_PHASE = 3
    private const val ARENA = 20
    private const val PERCENT = 100
    private const val CYCLES_PER_TICK = 30
    private const val PROJ_HEIGHT = 80
    private const val INSULATED_BOOTS = 7159
    private const val STUN = 5
    private const val P1_FIRST = 18
    private const val P1_SPEED = 14
    private const val P1_END_BASE = 20
    private const val P1_END_PER_TILE = 15
    private const val P1_MAX = 137
    private const val ENTRY_P1_MAX = 60
    private const val P1_MELEE_CAP = 10
    private const val P1_RANGED_CAP = 3
    private const val HARD_RUBBLE = 3
    private const val HARD_RUBBLE_REACH = 3
    private const val HARD_RUBBLE_MAX = 35
    private const val COLLAPSE_REACH = 2
    private const val COLLAPSE_MAX = 70
    private const val ENTRY_COLLAPSE_MAX = 50
    private const val PILLAR_GONE = 3
    private const val P1_TO_P2_AT = 4
    private const val P2_FORM_AT = 17
    private const val P2_FIRST_FROM_P1 = 20
    private const val P2_SPEED = 4
    private const val LIGHTNING_EVERY = 5
    private const val LIGHTNING_HOPS = 4
    private const val LIGHTNING_MAX = 48
    private const val BOOTS_MAX = 25
    private const val SELF_MIN = 15
    private const val SELF_MAX = 20
    private const val RESUMMON_EVERY = 3
    private const val RESUMMON_ODDS = 4
    private const val BOMB_MAX = 44

    /** OPEN-VZ12: Hard bombs "about 20% more". */
    private const val HARD_BOMB_MAX = 53
    private const val SLAM_MAX = 45
    private const val STOMP_MAX = 82
    private const val KNOCKBACK = 3
    private const val ACID_LIFE = 14
    private const val ACID_MIN = 9
    private const val ACID_MAX = 10
    private const val NYLO_SPREAD = 4
    private const val NYLO_LIFE = 25
    private const val NYLO_FAR = 3
    private const val NYLO_EXPLODE_MAX = 46
    private const val NYLO_FAR_MAX = 8

    /** Capture (SP plugin): an Athanatos every 21 attacks (OPEN-VZ5). */
    private const val ATHANATOS_EVERY = 21
    private const val ATHANATOS_CYCLES = 90
    private const val ATHANATOS_MAX = 78
    private const val ATHANATOS_HEAL_EVERY = 4
    private const val ATHANATOS_HEAL_MIN = 9
    private const val ATHANATOS_HEAL_MAX = 10
    private const val ATHANATOS_BURST_MAX = 70
    private const val REDS_AT = 35
    private const val REDS_CYCLE = 7
    private const val REDS_TO_ATTACK = 12
    private const val SUMMONING = 5
    private const val MATOMENOS_SIDE = 2
    private const val BLOOD_CHANCE = 75
    private const val BLOOD_MAX = 45
    private const val P2_TO_P3_AT = 2
    private const val P3_FORM_AT = 4
    private const val P3_FIRST_FROM_P2 = 12
    private const val P3_SPEED = 7
    private const val ENRAGED_SPEED = 5
    private const val SPECIAL_EVERY = 5
    private const val SPECIALS = 4
    private const val RETARGET = 17
    private const val P3_MELEE_MAX = 63
    private const val P3_RANGED_MAX = 33
    private const val ENRAGED_MAX = 34
    private const val P3_PRAYED_MAX = 16
    private const val MAGIC_START = 53
    private const val RANGED_START = 62
    private const val P3_PER_TILE = 15
    private const val P3_END_CAP = 179
    private const val WEB_SPIN = 12
    private const val WEB_NEXT = 10
    private const val WEB_VOLLEYS = 3
    private const val WEB_VOLLEY_GAP = 3
    private const val WEBS_PER_RAIDER = 3
    private const val WEB_HOLD = 8
    private const val WEB_SNAP = 30
    private const val ARANEA_COOLDOWN = 8
    private const val YELLOWS_LAND = 14
    private const val HARD_YELLOW_WAVES = 3
    private const val HARD_YELLOW_GAP = 3
    private const val YELLOW_SPREAD = 6
    private const val YELLOW_MAX = 80
    private const val YELLOWS_NEXT = 21
    private const val HARD_YELLOWS_NEXT = 27
    private const val GREEN_START = 62
    private const val GREEN_END = 242
    private const val GREEN_BOUNCES = 3
    private const val GREEN_BOUNCE_TICKS = 2
    private const val GREEN_PERCENT = 75
    private const val GREEN_NEXT = 12
    private const val ENRAGE_AT = 20
    private const val TORNADO_MIN = 5
    private const val TORNADO_HEAL = 3
    private const val TORNADO_NEXT = 16
    private const val SECOND_WIND_AT = 5
    private const val SECOND_WIND_HEAL = 30
    private const val FLOOR_SHAPE = 22
}

/** Every Verzik form in the three modes (client contract 4: 8369-8375, 10830-10836, 10847-10853). */
private val VERZIK_IDS: IntArray = ((8369..8375) + (10830..10836) + (10847..10853)).toIntArray()

/** Client contract 4: the Athanatos 8384 / Entry 10844 / Hard 10861. */
private val ATHANATOS_IDS: IntArray = intArrayOf(Npcs.TOB_VERZIK_PHASE2_ARMOUREDNYLOCAS, 10844, 10861)

/** The Athanatos bursts on a poisonous hit (VZ3). */
internal object TobVerzikAthanatos : Boss(*ATHANATOS_IDS) {
    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) = Unit

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        (source as? PlayerRef)?.let { TobVerzik.athanatosHit(npc, it) }
        return damage
    }
}
