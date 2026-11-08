package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Akkha, the Path of Het boss (docs/mechanics/raids/toa/akkha.md AK1-AK16). The arena is a circle on plane 1 centred
 * about (3680, 5407) (cache collision); its quadrants are NW fire (burn), NE darkness, SE lightning, SW ice (Strat).
 * - AK2 / AK3: one style at a time (melee -> ranged -> magic) with the matching overhead; a swap after a special or
 *   after 60 s without one (OPEN-AK19: since the last swap).
 * - AK4: a special every 7 attacks (6 with Stay Vigilant, which also swaps only if he hit since the last special):
 *   Memory Blast, Trailing Orbs, Detonate (groups only); Double Trouble two at once (OPEN-AK9 order).
 * - AK5 / AK6: melee on the target and the players next to it (Protect from Melee lets ~25% through); ranged and magic
 *   on everyone, damage fixed at launch (prayer ~15% through); Keep Back adds a melee hit on players next to him.
 * - AK7: quadrant effects: burn (5 a hit), darkness (-3 combat stats, rapid small hits), lightning (protection off),
 *   ice (attacks one tick slower).
 * - AK11: at 80 / 60 / 40 / 20% he is immune and four shadows appear in the quadrants; killing one makes him damageable
 *   in that quadrant only; a living shadow's hourglass fills and its slam envelops its quadrant.
 * - AK12: at 0 HP the final stand: 20% back, only melee hurts, unstable orbs (cap 25, Protect from Magic -25% first),
 *   a jump to another corner after 3 hits (+1 per extra player).
 * - AK13: Defence drain stops at 70.
 */
internal object ToaAkkha : Boss(*(Npcs.AKKHA_SPAWN..Npcs.AKKHA_ENRAGE_DUMMY).toList().toIntArray()) {
    private enum class Style(val form: Int, val type: AttackType) {
        MELEE(Npcs.AKKHA_MELEE, AttackType.SLASH),
        RANGED(Npcs.AKKHA_RANGE, AttackType.RANGED),
        MAGIC(Npcs.AKKHA_MAGE, AttackType.MAGIC),
    }

    /** Quadrant effects (AK7) and their graphics 2256-2259; orb NPCs 11800-11803. */
    private enum class Quadrant(val gfx: Int, val orb: Int) {
        BURN(SpotAnims.AKKHA_BURN, Npcs.AKKHA_TRAIL_ORB_BURN),
        DARKNESS(SpotAnims.AKKHA_DARKNESS, Npcs.AKKHA_TRAIL_ORB_DARKNESS),
        LIGHTNING(SpotAnims.AKKHA_LIGHTNING, Npcs.AKKHA_TRAIL_ORB_LIGHTNING),
        ICE(SpotAnims.AKKHA_FREEZE, Npcs.AKKHA_TRAIL_ORB_FREEZE),
    }

    private class State {
        var style = Style.MELEE
        var attacks = 0
        var hitSinceSpecial = false
        var lastSwap = 0L
        var thresholds = 0
        var immune = false
        var openQuadrant: Quadrant? = null
        var vanished = 0
        var memory = ArrayList<Quadrant>()
        var memoryTick = 0
        var orbTicks = 0
        var detonateIn = -1
        val marked = ArrayList<PlayerRef>()
        var final = false
        var finalHits = 0
        var orbEvery = 0
        val shadowCharge = HashMap<NpcRef, Int>()
        val burns = HashMap<PlayerRef, Int>()
        val dark = HashMap<PlayerRef, Int>()
    }

    private val STATE = AttrKey<State>("toa_akkha")

    private fun state(npc: NpcRef): State = npc[STATE] ?: State().also { npc[STATE] = it }

    override fun spawned(npc: NpcRef) {
        if (npc.type != Npcs.AKKHA_SPAWN || npc[STATE] != null) return
        val s = state(npc)
        npc.huntRange = HUNT
        npc.anim(Seqs.NPC_AKKHA_SPAWN_SPEAR)
        s.lastSwap = npc.tick
        npc.transform(Style.MELEE.form, restoreStats = false)
        ToaKit.loop(npc) { tick(npc, s) }
    }

    override fun retaliates(npc: NpcRef): Boolean = !state(npc).final && state(npc).vanished == 0

    /**
     * AK2: his overhead protects from his current style (OPEN-AK8: fully); AK11: immune while shadows guard him unless
     * he stands in the dispelled quadrant; AK12: in the final stand only melee hurts.
     */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        if (s.vanished > 0) return 0
        if (s.final) return if (style == HitStyle.MELEE) damage else 0
        if (s.immune && (s.openQuadrant == null || quadrantOf(npc, npc.coord) != s.openQuadrant)) return 0
        val guarded =
            when (s.style) {
                Style.MELEE -> style == HitStyle.MELEE
                Style.RANGED -> style == HitStyle.RANGED
                Style.MAGIC -> style == HitStyle.MAGIC
            }
        return if (guarded) 0 else damage
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        val floor = npc.baseLevel(NpcStat.DEFENCE) - DEFENCE_DRAIN_CAP
        if (npc.level(NpcStat.DEFENCE) < floor) npc.setLevel(NpcStat.DEFENCE, floor)
        if (s.final) {
            // AK12: a jump to another corner after 3 hits (+1 per extra player).
            if (source is PlayerRef && ++s.finalHits >= FINAL_HITS + (ToaKit.raiders(npc).size - 1).coerceAtLeast(0)) {
                s.finalHits = 0
                npc.teleport(ToaKit.at(npc, CORNERS[npc.random(CORNERS.size)]))
            }
            return
        }
        val permille = npc.hitpoints * PER_MILLE / maxOf(1, npc.maxHitpoints)
        if (s.thresholds < SHADOW_AT.size && permille <= SHADOW_AT[s.thresholds]) {
            s.thresholds++
            shadows(npc, s)
        }
    }

    /** AK12: at 0 HP the final stand: 20% back, merged with a corner shadow. */
    override fun onLethal(npc: NpcRef): Boolean {
        val s = state(npc)
        if (s.final) return false
        s.final = true
        s.immune = false
        Bosses.dismissMinions(npc)
        npc.hitpoints = npc.maxHitpoints * FINAL_RESTORE / PERCENT
        npc.transform(Npcs.AKKHA_ENRAGE, restoreStats = false)
        npc.stopInteraction()
        npc.teleport(ToaKit.at(npc, CORNERS[npc.random(CORNERS.size)]))
        return true
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        if (s.final || s.vanished > 0) return
        val cycle = if (ToaKit.has(npc, STAY_VIGILANT)) VIGILANT_CYCLE else SPECIAL_CYCLE
        if (++s.attacks % (cycle + 1) == 0) {
            special(npc, s)
            return
        }
        s.hitSinceSpecial = true
        when (s.style) {
            Style.MELEE -> if (!distance) melee(npc, s, victim) else ranged(npc, s, Style.RANGED)
            else -> ranged(npc, s, s.style)
        }
    }

    /** AK5: the target and its neighbours (no diagonal attack); Protect from Melee lets ~25% through. */
    private fun melee(npc: NpcRef, s: State, victim: EntityRef) {
        npc.anim(Seqs.NPC_AKKHA_ATTACK_MELEE_SWORD)
        val max = ToaKit.dmg(npc, MAX_HIT)
        for (p in ToaKit.raiders(npc)) {
            if (p != victim && p.distanceTo(victim) > 1) continue
            val hit = BossCombat.rolls(npc, p, s.style.type)
            val raw = if (hit) BossCombat.roll(npc, 1, max) else 0
            CombatSystem.queueHit(p, npc, ToaKit.reduced(npc, p, CombatStyle.MELEE, raw, MELEE_KEEP), HitStyle.MELEE, 0)
        }
    }

    /** AK6: everyone in the arena; damage fixed at launch, prayer read at launch (~15% through). Keep Back: a melee hit on players next to him. */
    private fun ranged(npc: NpcRef, s: State, style: Style) {
        npc.anim(if (style == Style.MAGIC) Seqs.NPC_AKKHA_ATTACK_MAGIC_SWORD else Seqs.NPC_AKKHA_ATTACK_RANGED_SWORD)
        val max = ToaKit.dmg(npc, MAX_HIT)
        val cs = if (style == Style.MAGIC) CombatStyle.MAGIC else CombatStyle.RANGED
        for (p in ToaKit.raiders(npc)) {
            BossCombat.projectile(npc, p, if (style == Style.MAGIC) MAGIC_TRAVEL else RANGED_TRAVEL, PROJ_DELAY)
            val raw = if (BossCombat.rolls(npc, p, style.type)) BossCombat.roll(npc, 1, max) else 0
            CombatSystem.queueHit(p, npc, ToaKit.reduced(npc, p, cs, raw, RANGED_KEEP), CombatSystem.hitStyle(cs), PROJ_DELAY)
            if (ToaKit.has(npc, KEEP_BACK) && npc.distanceTo(p) <= 1) {
                val melee = BossCombat.roll(npc, 1, max * KEEP_BACK_PERCENT / PERCENT)
                CombatSystem.queueHit(p, npc, ToaKit.reduced(npc, p, CombatStyle.MELEE, melee, MELEE_KEEP), HitStyle.MELEE, 0)
            }
        }
        if (s.style != style) Unit
    }

    /** AK3: the next style in melee -> ranged -> magic, with its overhead (the form). */
    private fun swap(npc: NpcRef, s: State) {
        s.style = Style.entries[(s.style.ordinal + 1) % Style.entries.size]
        s.lastSwap = npc.tick
        npc.transform(s.style.form, restoreStats = false)
    }

    /** AK4 / OPEN-AK9: specials in turn (Detonate only in groups); Double Trouble uses two at once. */
    private fun special(npc: NpcRef, s: State) {
        val pool = buildList {
            add(0)
            add(1)
            if (ToaKit.raiders(npc).size > 1) add(2)
        }
        val first = pool[(s.attacks / SPECIAL_CYCLE) % pool.size]
        val picks = if (ToaKit.has(npc, DOUBLE_TROUBLE)) listOf(first, pool[(pool.indexOf(first) + 1) % pool.size]).distinct() else listOf(first)
        for (k in picks) {
            when (k) {
                0 -> memoryBlast(npc, s)
                1 -> trailingOrbs(npc, s)
                else -> detonate(npc, s)
            }
        }
        // AK4: with Stay Vigilant the swap needs a standard attack since the previous special.
        if (!ToaKit.has(npc, STAY_VIGILANT) || s.hitSinceSpecial) swap(npc, s)
        s.hitSinceSpecial = false
    }

    /** AK9: he vanishes; quadrants light in an adjacent sequence; each burst hits the three dark quadrants. */
    private fun memoryBlast(npc: NpcRef, s: State) {
        npc.anim(Seqs.NPC_AKKHA_TELEPORT_DISAPPEAR_SWORD)
        for (p in ToaKit.raiders(npc)) p.message("Sections of the room start to glow...")
        s.memory.clear()
        var q = Quadrant.entries[npc.random(Quadrant.entries.size)]
        repeat(MEMORY_BASE + ToaKit.step(npc)) {
            s.memory += q
            // Always adjacent to the previous (never opposite).
            q = Quadrant.entries[(q.ordinal + if (npc.random(2) == 0) 1 else Quadrant.entries.size - 1) % Quadrant.entries.size]
        }
        s.memoryTick = 0
        s.vanished = s.memory.size * gap(npc) + 1
    }

    private fun gap(npc: NpcRef): Int = if (ToaKit.has(npc, FEELING_SPECIAL)) MEMORY_GAP_FAST else MEMORY_GAP

    private fun memoryTick(npc: NpcRef, s: State) {
        if (s.vanished <= 0) return
        s.vanished--
        s.memoryTick++
        if (s.memoryTick % gap(npc) == 0) {
            val lit = s.memory.getOrNull(s.memoryTick / gap(npc) - 1)
            if (lit != null) {
                for (q in Quadrant.entries) {
                    if (q == lit) continue
                    blastQuadrant(npc, q, ToaKit.dmg(npc, BLAST_MAX))
                }
            }
        }
        if (s.vanished == 0) npc.anim(Seqs.NPC_AKKHA_TELEPORT_APPEAR_SWORD)
    }

    /** A quadrant enveloped (Memory Blast, shadow slam): graphics on its tiles (RuneLite reads them), a heavy hit and the effect. */
    private fun blastQuadrant(npc: NpcRef, q: Quadrant, max: Int) {
        for (dx in -ARENA_RADIUS..ARENA_RADIUS) {
            for (dz in -ARENA_RADIUS..ARENA_RADIUS) {
                val t = CoordGrid(CENTRE.x + dx, CENTRE.z + dz, CENTRE.level)
                if (quadrantOfTemplate(t) != q || dx * dx + dz * dz > ARENA_RADIUS * ARENA_RADIUS) continue
                npc.spotAnimAt(ToaKit.at(npc, t), q.gfx)
            }
        }
        for (p in ToaKit.raiders(npc)) {
            if (quadrantOf(npc, p.coord) != q) continue
            ToaKit.areaHit(npc, p, BossCombat.roll(npc, 1, max))
            effect(npc, p, q)
        }
    }

    /** AK7 (OPEN-AK12 durations): burn 5 a hit every 4 ticks x5; darkness -3 combat stats; lightning protection off 8 ticks; ice +1 tick for 10. */
    private fun effect(npc: NpcRef, p: PlayerRef, q: Quadrant) {
        val s = state(npc)
        when (q) {
            Quadrant.BURN -> s.burns[p] = BURN_HITS

            Quadrant.DARKNESS -> {
                for (stat in COMBAT_STATS) p.setLevel(stat, maxOf(1, p.level(stat) - DARK_DRAIN))
                s.dark[p] = DARK_TICKS
            }

            Quadrant.LIGHTNING -> p.disableProtection(LIGHTNING_TICKS)

            Quadrant.ICE -> {
                p[ToaKit.ICE_UNTIL] = p.tick + ICE_TICKS
                npc.queueHit(ICE_TICKS, strong = false) { p.message("The ice around you melts away.") }
            }
        }
    }

    /** AK10: orbs on the tiles moving players leave (one more in front with Feeling Special?); a step on one detonates it. */
    private fun trailingOrbs(npc: NpcRef, s: State) {
        for (p in ToaKit.raiders(npc)) p.message("Magical orbs begin to materialise around you!")
        s.orbTicks = ORB_TICKS
    }

    private val LAST_TILE = AttrKey<CoordGrid>("toa_akkha_last_tile")
    private val ORB_LIFE = AttrKey<Int>("toa_akkha_orb_life")

    private fun orbsTick(npc: NpcRef, s: State) {
        if (s.orbTicks > 0) {
            s.orbTicks--
            for (p in ToaKit.raiders(npc)) {
                val last = p[LAST_TILE]
                p[LAST_TILE] = p.coord
                if (last == null || last == p.coord) continue
                val q = quadrantOf(npc, last) ?: continue
                val orb = Bosses.summon(npc, q.orb, last, huntRange = 0, inheritRun = true)
                orb[ORB_LIFE] = ORB_LIFETIME
                if (ToaKit.has(npc, FEELING_SPECIAL)) {
                    val ahead = CoordGrid(2 * p.coord.x - last.x, 2 * p.coord.z - last.z, p.coord.level)
                    Bosses.summon(npc, q.orb, ahead, huntRange = 0, inheritRun = true)[ORB_LIFE] = ORB_LIFETIME
                }
            }
            if (s.orbTicks == 0) for (p in ToaKit.raiders(npc)) p.message("The magical orbs stop materialising.")
        }
        for (orb in Bosses.minions(npc)) {
            val q = Quadrant.entries.firstOrNull { it.orb == orb.type } ?: continue
            val life = (orb[ORB_LIFE] ?: 0) - 1
            orb[ORB_LIFE] = life
            val victim = ToaKit.raiders(npc).firstOrNull { it.coord == orb.coord && it[LAST_TILE] == it.coord }
            if (victim == null && life > 0) continue
            if (victim != null) {
                npc.spotAnimAt(orb.coord, q.gfx)
                // OrbD: darkness orbs deal low rapid damage; the rest a heavy hit.
                ToaKit.areaHit(npc, victim, BossCombat.roll(npc, 1, ToaKit.dmg(npc, if (q == Quadrant.DARKNESS) DARK_ORB_MAX else ORB_MAX)), ORB_DELAY)
                effect(npc, victim, q)
            }
            orb.despawn()
        }
    }

    /** AK8 (groups): marked players detonate in a plus (an X too with Feeling Special?) after a delay; all on one tile negate it. */
    private fun detonate(npc: NpcRef, s: State) {
        s.marked.clear()
        for (p in ToaKit.raiders(npc)) {
            p.message("You have been marked for detonation!")
            s.marked += p
        }
        s.detonateIn = DETONATE_DELAY - ToaKit.step(npc)
    }

    private fun detonateTick(npc: NpcRef, s: State) {
        if (s.detonateIn < 0) return
        if (s.detonateIn-- > 0) return
        val stacked = s.marked.map { it.coord }.distinct().size == 1
        if (stacked) return
        val cross = ToaKit.has(npc, FEELING_SPECIAL) && s.marked.size > 1
        for (bomb in s.marked) {
            for (other in s.marked) {
                if (other == bomb) continue
                val dx = other.coord.x - bomb.coord.x
                val dz = other.coord.z - bomb.coord.z
                val plus = dx == 0 || dz == 0
                val x = cross && kotlin.math.abs(dx) == kotlin.math.abs(dz)
                if (plus || x) {
                    ToaKit.areaHit(npc, other, BossCombat.roll(npc, 1, ToaKit.dmg(npc, DETONATE_MAX)))
                    ToaKit.areaHit(npc, bomb, BossCombat.roll(npc, 1, ToaKit.dmg(npc, DETONATE_MAX)))
                }
            }
        }
    }

    /** AK11: immune; four shadows (or the survivors restored); one killed opens its quadrant. */
    private fun shadows(npc: NpcRef, s: State) {
        s.immune = true
        s.openQuadrant = null
        val living = Bosses.minions(npc, Npcs.AKKHA_SHADOW)
        if (living.isEmpty()) {
            for (p in ToaKit.raiders(npc)) p.message("Shadows appear throughout the room!")
            for (c in CORNERS) {
                val shadow = Bosses.summon(npc, Npcs.AKKHA_SHADOW, ToaKit.at(npc, c), huntRange = 0, inheritRun = true, scale = true)
                s.shadowCharge[shadow] = 0
            }
        } else {
            for (p in ToaKit.raiders(npc)) p.message("The shadows in the room are restored!")
            for (sh in living) {
                sh.hitpoints = sh.maxHitpoints
                s.shadowCharge[sh] = 0
            }
        }
    }

    /** A shadow died: its quadrant opens (AK11). */
    fun shadowDied(owner: NpcRef, shadow: NpcRef) {
        val s = state(owner)
        s.shadowCharge.remove(shadow)
        s.openQuadrant = quadrantOf(owner, shadow.coord)
    }

    private fun shadowTick(npc: NpcRef, s: State) {
        if (s.vanished > 0) return
        for (sh in s.shadowCharge.keys.toList()) {
            if (!sh.isInWorld || sh.isDying) {
                s.shadowCharge.remove(sh)
                continue
            }
            val c = (s.shadowCharge[sh] ?: 0) + 1
            s.shadowCharge[sh] = c
            if (c < SHADOW_CHARGE) continue
            s.shadowCharge[sh] = -SHADOW_REST
            sh.anim(Seqs.NPC_AKKHA_ATTACK_STOMP_SPEAR)
            quadrantOf(npc, sh.coord)?.let { blastQuadrant(npc, it, ToaKit.dmg(npc, BLAST_MAX)) }
        }
    }

    /** AK12: unstable orbs from his corner in a straight line; the first player in the way takes min(25, scaled), Protect from Magic -25% first. */
    private fun finalTick(npc: NpcRef, s: State) {
        if (!s.final) return
        if (++s.orbEvery >= UNSTABLE_EVERY) {
            s.orbEvery = 0
            val orb = Bosses.summon(npc, Npcs.AKKHA_ENRAGE_ORB, npc.coord, huntRange = 0, inheritRun = true)
            orb[DIRECTION] = DIRS[npc.random(DIRS.size)]
        }
        for (orb in Bosses.minions(npc, Npcs.AKKHA_ENRAGE_ORB)) {
            val (dx, dz) = orb[DIRECTION] ?: continue
            val next = CoordGrid(orb.coord.x + dx, orb.coord.z + dz, orb.coord.level)
            val t = ToaKit.template(npc, next)
            if ((t.x - CENTRE.x) * (t.x - CENTRE.x) + (t.z - CENTRE.z) * (t.z - CENTRE.z) > ARENA_RADIUS * ARENA_RADIUS) {
                orb.despawn()
                continue
            }
            orb.teleport(next)
            val p = ToaKit.raiders(npc).firstOrNull { it.coord == next } ?: continue
            npc.spotAnimAt(next, SpotAnims.AKKHA_ENRAGE_ORB_IMPACT)
            var dmg = BossCombat.roll(npc, 1, ToaKit.dmg(npc, UNSTABLE_BASE))
            if (p.protects(CombatStyle.MAGIC)) dmg = dmg * (PERCENT - UNSTABLE_PRAYER) / PERCENT
            ToaKit.areaHit(npc, p, minOf(UNSTABLE_CAP, dmg))
            orb.despawn()
        }
    }

    private val DIRECTION = AttrKey<Pair<Int, Int>>("toa_akkha_orb_dir")

    private fun tick(npc: NpcRef, s: State) {
        memoryTick(npc, s)
        orbsTick(npc, s)
        detonateTick(npc, s)
        shadowTick(npc, s)
        finalTick(npc, s)
        effectsTick(npc, s)
        // AK3: a swap after 60 s without one (OPEN-AK19).
        if (!s.final && npc.tick - s.lastSwap >= SWAP_TIMER) swap(npc, s)
    }

    private fun effectsTick(npc: NpcRef, s: State) {
        if (npc.tick % BURN_EVERY == 0L) {
            for ((p, left) in s.burns.entries.toList()) {
                if (left <= 0 || p.isDying) {
                    s.burns.remove(p)
                    continue
                }
                s.burns[p] = left - 1
                ToaKit.areaHit(npc, p, BURN_DAMAGE)
                // AK7: the burn can spread to an adjacent unaffected player.
                ToaKit.raiders(npc).firstOrNull { it != p && it !in s.burns && it.distanceTo(p) <= 1 }?.let { s.burns[it] = BURN_HITS }
            }
        }
        for ((p, left) in s.dark.entries.toList()) {
            if (left <= 0 || p.isDying) {
                s.dark.remove(p)
                continue
            }
            s.dark[p] = left - 1
            if (left % 2 == 0) ToaKit.areaHit(npc, p, BossCombat.roll(npc, 1, DARK_TICK_MAX))
        }
    }

    private fun quadrantOf(npc: NpcRef, tile: CoordGrid): Quadrant? = quadrantOfTemplate(ToaKit.template(npc, tile))

    /** NW burn, NE darkness, SE lightning, SW ice (Strat). */
    private fun quadrantOfTemplate(t: CoordGrid): Quadrant? {
        val north = t.z >= CENTRE.z
        val east = t.x >= CENTRE.x
        return when {
            north && !east -> Quadrant.BURN
            north && east -> Quadrant.DARKNESS
            !north && east -> Quadrant.LIGHTNING
            else -> Quadrant.ICE
        }
    }

    /** AK14: Het's capture (if not read) and the Eldritch ashes to the top damage dealer. */
    override fun died(npc: NpcRef, killer: EntityRef?) {
        npc.anim(Seqs.NPC_AKKHA_DEATH_SWORD)
        Bosses.dismissMinions(npc)
        val top = Bosses.topDamager(npc) ?: return
        ToaKit.giveOrDrop(top, Objs.TOA_AKKHA_ASHES, "akkha drop")
        if (top.varbit(Varbits.TOA_BOOK_AKKHA) == 0) ToaKit.giveOrDrop(top, Objs.TOA_BOOK_AKKHA, "akkha drop")
    }

    // ---- data (template: region 14676, base (3648, 5376); the arena circle on plane 1 from the cache collision)

    private val CENTRE = CoordGrid(3680, 5407, 1)
    private const val ARENA_RADIUS = 10

    /** OPEN-AK15: the shadows' corners, one per quadrant. */
    private val CORNERS = listOf(CoordGrid(3673, 5413, 1), CoordGrid(3686, 5413, 1), CoordGrid(3686, 5400, 1), CoordGrid(3673, 5400, 1))

    private val DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val COMBAT_STATS = intArrayOf(Stat.ATTACK, Stat.STRENGTH, Stat.DEFENCE, Stat.RANGED, Stat.MAGIC)

    /** AK4: a special every 7 standard attacks (6 with Stay Vigilant); AK3 the 60 s timer = 100 ticks. */
    private const val SPECIAL_CYCLE = 7
    private const val VIGILANT_CYCLE = 6
    private const val SWAP_TIMER = 100L

    /** OPEN-AK2: 55 (the infobox max) for all styles; AK5 / AK6: ~25% / ~15% through prayer; Keep Back slightly below melee (OPEN: 80%). */
    private const val MAX_HIT = 55
    private const val MELEE_KEEP = 25
    private const val RANGED_KEEP = 15
    private const val KEEP_BACK_PERCENT = 80
    private const val PROJ_DELAY = 3
    private const val MAGIC_TRAVEL = 2253
    private const val RANGED_TRAVEL = 2255

    /** AK9: 4 ticks between bursts (2 with Feeling Special?), OPEN-AK6: 4 bursts plus one per two path levels. */
    private const val MEMORY_BASE = 4
    private const val MEMORY_GAP = 4
    private const val MEMORY_GAP_FAST = 2

    /** OPEN-AK12 / AK14: quadrant and orb damage (heavy), durations. */
    private const val BLAST_MAX = 30
    private const val ORB_MAX = 25
    private const val DARK_ORB_MAX = 5
    private const val ORB_TICKS = 20
    private const val ORB_LIFETIME = 15
    private const val ORB_DELAY = 1
    private const val BURN_HITS = 5
    private const val BURN_EVERY = 4L
    private const val BURN_DAMAGE = 5
    private const val DARK_DRAIN = 3
    private const val DARK_TICKS = 8
    private const val DARK_TICK_MAX = 3
    private const val LIGHTNING_TICKS = 8
    private const val ICE_TICKS = 10

    /** OPEN-AK13: Detonate after 6 ticks (one faster per path step), heavy. */
    private const val DETONATE_DELAY = 6
    private const val DETONATE_MAX = 30

    /** AK11: 80 / 60 / 40 / 20%; OPEN-AK15 charge 30 ticks, rest 10. */
    private val SHADOW_AT = intArrayOf(800, 600, 400, 200)
    private const val SHADOW_CHARGE = 30
    private const val SHADOW_REST = 10
    private const val PER_MILLE = 1000

    /** AK12: 20% restored; orbs capped at 25, Protect from Magic -25%; OPEN-AK16 an orb every 3 ticks, base 20; 3 hits to jump. */
    private const val FINAL_RESTORE = 20
    private const val UNSTABLE_CAP = 25
    private const val UNSTABLE_PRAYER = 25
    private const val UNSTABLE_EVERY = 3
    private const val UNSTABLE_BASE = 20
    private const val FINAL_HITS = 3
    private const val PERCENT = 100

    /** AK13: drain stops at 70 (from 80). */
    private const val DEFENCE_DRAIN_CAP = 10
    private const val HUNT = 24

    private const val STAY_VIGILANT = "Stay Vigilant"
    private const val DOUBLE_TROUBLE = "Double Trouble"
    private const val KEEP_BACK = "Keep Back"
    private const val FEELING_SPECIAL = "Feeling Special?"
}

/** Akkha's shadows (AK11): no attacks of their own; a death opens its quadrant. */
internal object ToaAkkhaShadow : Boss(Npcs.AKKHA_SHADOW) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {}

    override fun retaliates(npc: NpcRef): Boolean = false

    override fun died(npc: NpcRef, killer: EntityRef?) {
        npc[Bosses.OWNER]?.let { ToaAkkha.shadowDied(it, npc) }
    }
}
