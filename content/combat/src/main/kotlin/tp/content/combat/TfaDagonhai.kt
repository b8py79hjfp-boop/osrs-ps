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

/**
 * The Dagon'hai, TFA room 4 west (docs/mechanics/raids/tfa/dagonhai.md DH1-DH6; the cycle and numbers from the
 * live-game captures (capture-values.md), timings from RuneLite (D-080)).
 *
 * The Elder and two Monks share one shield (640 at two, x1.45 ...). A fixed 50-tick cycle runs the ritual: t0 a Monk
 * channels (18 ticks; a spell of the chant's element interrupts it and stuns 11 ticks; a completed channel is ritual
 * +25); t13 the other Monk searches ("What have we here?"), t16 calls the god, t20 the book flies to the cauldron along
 * the floor, t21 it strikes: a raider on its lane catches it and takes the god's effect, else it hits the cauldron
 * (ritual +25); t23 the cauldron moves; t25 the other Monk channels. Gods in turn Zamorak, Bandos, Saradomin. At ritual
 * 100 the trio wipe the room with bolts. The Elder attacks every 5 ticks. A shield break: all three healed, the cauldron
 * (1,000) loses 200, two rifts let out six imps; the fifth break leaves all three stunned at 7 HP (3 Nov).
 */
internal object TfaDagonhai : Boss(
    Npcs.TFA_DAGONHAI_LEADER,
    Npcs.TFA_DAGONHAI_MINION_1,
    Npcs.TFA_DAGONHAI_MINION_2,
    Npcs.TFA_DAGONHAI_LEADER_UNSHIELDED,
    Npcs.TFA_DAGONHAI_MINION_1_UNSHIELDED,
    Npcs.TFA_DAGONHAI_MINION_2_UNSHIELDED,
) {
    /** The trio's shared state, held by every member. */
    private class Trio(val size: Int, val centre: CoordGrid) {
        var breaks = 0
        var ritual = 0
        var enragedAt = -1L
        var god = 0
        var cycles = 0
        var channeller: NpcRef? = null
        var channelElement = 0
        var focusStep = 0
        var focus: NpcRef? = null
        var cauldron = CAULDRON_HP
        val members = ArrayList<NpcRef>(MEMBERS)
        val monks: List<NpcRef> get() = members.filter { it.type != Npcs.TFA_DAGONHAI_LEADER }
    }

    private val TRIO = AttrKey<Trio>("tfa_dagonhai")
    private val STUNNED_UNTIL = AttrKey<Long>("tfa_dagonhai_stun")

    private fun trio(npc: NpcRef): Trio {
        npc[TRIO]?.let { return it }
        val members = npc.npcsWithin(npc.coord, SCAN).filter { it.type in SHIELDED }
        val leader = members.firstOrNull { it.type == Npcs.TFA_DAGONHAI_LEADER } ?: npc
        val t = Trio(TfaKit.size(npc), leader.spawn)
        t.members += members
        val shield = SHIELD_HP[t.size - MIN_SCALE]
        for (m in members) {
            m[TRIO] = t
            TfaKit.setPool(m, shield)
        }
        // DH1: the cauldron (the Dagon'hai focus NPC) moves around the room's centre.
        t.focus = Bosses.summon(leader, Npcs.TFA_DAGONHAI_FOCUS, focusTile(t, 0), huntRange = 0)
        cycle(leader, t)
        return t
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    /** Capture: the Elder attacks every 5 ticks while free, ranged or magic, at a raider and anyone beside them; lands +5. */
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val t = trio(npc)
        if (npc.type != Npcs.TFA_DAGONHAI_LEADER || npc.type in UNSHIELDED) return
        if (npc.tick < (npc[STUNNED_UNTIL] ?: 0L) || t.channeller == npc) return
        val magic = npc.random(2) == 0
        npc.anim(if (magic) Seqs.NPC_DAGONHAI_ATTACK_MAGIC_01 else Seqs.NPC_DAGONHAI_ATTACK_RANGED_01)
        CombatSystem.announceAttack(npc, victim, if (magic) CombatStyle.MAGIC else CombatStyle.RANGED)
        val style = if (magic) CombatStyle.MAGIC else CombatStyle.RANGED
        for (p in TfaKit.arena(npc)) {
            if (p != victim && p.distanceTo(victim) > 1) continue
            val dmg = if (p.protects(style)) 0 else BossCombat.roll(npc, 0, ELDER_MAX)
            CombatSystem.queueHit(p, npc, dmg, CombatSystem.hitStyle(style), ELDER_HIT_DELAY)
        }
        npc.setAttackClock(npc.tick + ELDER_SPEED)
    }

    // ---- the 50-tick ritual cycle

    private fun cycle(leader: NpcRef, t: Trio) {
        val monks = t.monks
        if (monks.isEmpty() || !leader.isInWorld) return
        val first = monks[t.cycles % monks.size]
        val second = monks[(t.cycles + 1) % monks.size]
        t.cycles++
        at(leader, 0) { channel(first, t) }
        at(leader, SEARCH_AT) { second.say("What have we here?") }
        at(leader, CALL_AT) { book(second, t) }
        at(leader, CAULDRON_MOVE_AT) { moveCauldron(leader, t) }
        at(leader, SECOND_CHANNEL_AT) { channel(first.takeIf { it != second } ?: second, t) }
        at(leader, CYCLE) { cycle(leader, t) }
    }

    private fun at(leader: NpcRef, ticks: Int, action: () -> Unit) {
        leader.queueHit(ticks, strong = false) { if (leader.isInWorld && leader.type !in UNSHIELDED) action() }
    }

    /** Capture / RuneLite: a channel completes 18 ticks after the yell (ritual +25) unless that element interrupts it. */
    private fun channel(monk: NpcRef, t: Trio) {
        if (!monk.isInWorld || monk.tick < (monk[STUNNED_UNTIL] ?: 0L)) return
        t.channelElement = monk.random(CHANTS.size)
        monk.say(CHANTS[t.channelElement])
        monk.spotAnim(SpotAnims.TFA_DAGONHAI_DEMONIC_STRENGTH_TELEGRAPH)
        t.channeller = monk
        monk.queueHit(CHANNEL_TICKS, strong = false) {
            if (t.channeller != monk || !monk.isInWorld) return@queueHit
            t.channeller = null
            ritual(monk, t, RITUAL_STEP)
        }
    }

    /**
     * Capture: the god (in turn Zamorak, Bandos, Saradomin) is called at t16 ("Zamorak empowers!" ...), the book flies
     * at t20 along the floor from the Monk to the cauldron and strikes at t21: the first raider on its lane catches it
     * and takes the god's effect; uncaught, it hits the cauldron (ritual +25, "Curse you!" when caught).
     */
    private fun book(monk: NpcRef, t: Trio) {
        if (!monk.isInWorld) return
        val god = t.god++ % TOMES
        monk.say(GOD_YELLS[god])
        val cauldron = t.focus?.coord ?: t.centre
        val lane = TfaKit.line(monk.coord, cauldron)
        monk.queueHit(BOOK_STRIKE - CALL_AT, strong = false) {
            if (!monk.isInWorld) return@queueHit
            val catcher = TfaKit.arena(monk).firstOrNull { TfaKit.on(it, lane) }
            if (catcher == null) {
                ritual(monk, t, RITUAL_STEP)
            } else {
                monk.say("Curse you!")
                godEffect(monk, god, catcher)
            }
        }
    }

    private fun godEffect(monk: NpcRef, god: Int, catcher: PlayerRef) {
        when (god) {
            0 -> zamorak(monk, catcher)
            1 -> bandos(monk, catcher)
            else -> saradomin(monk, catcher)
        }
    }

    /**
     * Capture: Zamorak: the catcher takes 23..27, then 3 every 4 ticks five times; fire bolts at every raider's tile
     * (OPEN-DH1: 10 to anyone still there 3-5 ticks later, RuneLite's glyph delay).
     */
    private fun zamorak(monk: NpcRef, catcher: PlayerRef) {
        CombatSystem.applyHit(catcher, monk, BossCombat.roll(monk, ZAM_MIN, ZAM_MAX), HitStyle.MAGIC)
        for (k in 1..ZAM_BURNS) {
            catcher.queueHit(k * ZAM_BURN_EVERY, strong = false) {
                if (!catcher.isDying) CombatSystem.applyHit(catcher, monk, ZAM_BURN, HitStyle.TYPELESS)
            }
        }
        for (p in TfaKit.arena(monk)) {
            val tile = p.coord
            monk.spotAnimAt(tile, SpotAnims.TFA_DAGONHAI_DEMONIC_STRENGTH_TELEGRAPH)
            monk.queueHit(GLYPH_MIN + monk.random(GLYPH_SPREAD), strong = false) {
                if (monk.isInWorld && p.coord == tile) CombatSystem.applyHit(p, monk, GLYPH, HitStyle.MAGIC)
            }
        }
    }

    /** Capture: Bandos: the catcher takes 51; a square ring from the catcher spreads a tile a tick to 8 out, 23..29. */
    private fun bandos(monk: NpcRef, catcher: PlayerRef) {
        CombatSystem.applyHit(catcher, monk, BANDOS_CATCH, HitStyle.MELEE)
        val origin = catcher.coord

        fun ring(p: PlayerRef) = maxOf(kotlin.math.abs(p.coord.x - origin.x), kotlin.math.abs(p.coord.z - origin.z))
        for (p in TfaKit.arena(monk)) {
            val d = ring(p)
            if (p == catcher || d > RING_REACH) continue
            monk.queueHit(maxOf(1, d), strong = false) {
                if (monk.isInWorld &&
                    ring(p) == d
                ) {
                    CombatSystem.applyHit(p, monk, BossCombat.roll(monk, RING_MIN, RING_MAX), HitStyle.MELEE)
                }
            }
        }
    }

    /**
     * Capture: Saradomin: 27 strike tiles (0..13; RuneLite: ~4 ticks after the telegraph) and 6 healing tiles (~5
     * ticks; they heal the trio); the catcher is healed 13.
     */
    private fun saradomin(monk: NpcRef, catcher: PlayerRef) {
        catcher.hitpoints = minOf(catcher.maxHitpoints, catcher.hitpoints + SARA_CATCH_HEAL)
        val trio = monk[TRIO]
        val centre = trio?.centre ?: monk.coord
        repeat(SARA_STRIKES) {
            val tile = BossCombat.randomTile(monk, centre, SARA_SPREAD)
            monk.spotAnimAt(tile, SpotAnims.TFA_DAGONHAI_SARADOMIN_DAMAGE_TELEGRAPH_150)
            monk.queueHit(BLUE_DELAY, strong = false) {
                monk.spotAnimAt(tile, SpotAnims.TFA_DAGONHAI_SARADOMIN_DAMAGE_LIGHTNING)
                for (p in TfaKit.arena(monk)) {
                    if (p.coord ==
                        tile
                    ) {
                        CombatSystem.applyHit(p, monk, BossCombat.roll(monk, 0, SARA_MAX), HitStyle.MAGIC)
                    }
                }
            }
        }
        repeat(SARA_HEALS) {
            val tile = BossCombat.randomTile(monk, centre, SARA_SPREAD)
            monk.spotAnimAt(tile, SpotAnims.TFA_DAGONHAI_SARADOMIN_HEAL_TELEGRAPH_150)
            monk.queueHit(YELLOW_DELAY, strong = false) {
                monk.spotAnimAt(tile, SpotAnims.TFA_DAGONHAI_SARADOMIN_HEAL_LIGHTNING)
                for (m in trio?.members.orEmpty()) {
                    if (m.isInWorld &&
                        TfaKit.under(m, tile)
                    ) {
                        m.hitpoints = minOf(m.maxHitpoints, m.hitpoints + SARA_HEAL)
                    }
                }
            }
        }
    }

    /** Ritual +[step]; at 100 the enrage: from E+6, every 5 ticks, two bolts of 15..22 at every raider. */
    private fun ritual(npc: NpcRef, t: Trio, step: Int) {
        if (t.enragedAt >= 0) return
        t.ritual += step
        npc.say("The ritual grows stronger!")
        if (t.ritual < RITUAL_FULL) return
        t.enragedAt = npc.tick
        for (m in t.members) m.spotAnim(SpotAnims.TFA_DAGONHAI_DEMONIC_STRENGTH_TELEGRAPH)
        enrageBolts(npc, t, ENRAGE_FIRST)
    }

    private fun enrageBolts(npc: NpcRef, t: Trio, delay: Int) {
        npc.queueHit(delay, strong = false) {
            if (!npc.isInWorld || npc.type in UNSHIELDED) return@queueHit
            for (p in TfaKit.arena(npc)) {
                repeat(2) { CombatSystem.applyHit(p, npc, BossCombat.roll(npc, BOLT_MIN, BOLT_MAX), HitStyle.MAGIC) }
            }
            enrageBolts(npc, t, ENRAGE_EVERY)
        }
    }

    /** DH4: a channelling Monk hit by its element's spell is interrupted ("Argh!") and stunned 11 ticks (capture). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val t = trio(npc)
        // A splash counts: it still lands as a 0.
        if (style == HitStyle.MAGIC && t.channeller == npc && elementOf(source) == CHANT_ELEMENTS[t.channelElement]) {
            t.channeller = null
            npc[STUNNED_UNTIL] = npc.tick + STUN_TICKS
            npc.say("Argh!")
        }
        val stunned = npc.tick < (npc[STUNNED_UNTIL] ?: 0L)
        return if (stunned) damage * STUN_BONUS_NUM / STUN_BONUS_DEN else damage
    }

    /** One pool: every member shows the same hitpoints. */
    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val t = trio(npc)
        for (m in t.members) if (m != npc && m.isInWorld && m.type in SHIELDED) m.hitpoints = npc.hitpoints
    }

    /**
     * Capture: each shield break heals all three to full and costs the cauldron 200 (of 1,000), ritual -20% (DH6); two
     * rifts open at one site and let out imps; the fifth break is the kill: all three stand at 7 HP, stunned.
     */
    override fun onLethal(npc: NpcRef): Boolean {
        val t = trio(npc)
        if (npc.type in UNSHIELDED) return false
        t.breaks++
        t.cauldron -= CAULDRON_PER_BREAK
        t.ritual = maxOf(0, t.ritual - RITUAL_LOSS)
        if (t.breaks < BREAKS) {
            val shield = SHIELD_HP[t.size - MIN_SCALE]
            for (m in t.members) if (m.isInWorld) m.hitpoints = shield
            npc.say("Zamorak, restore us!")
            rifts(npc, t)
            return true
        }
        for (m in t.members) {
            if (!m.isInWorld) continue
            m.transform(UNSHIELDED_OF.getValue(m.type), restoreStats = true)
            m[STUNNED_UNTIL] = m.tick + EXPLOSION_STUN
        }
        t.focus?.let {
            it.spotAnim(SpotAnims.TFA_DAGONHAI_SARADOMIN_DAMAGE_LIGHTNING)
            it.despawn()
        }
        return true
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
    }

    /** The standard-spellbook element of [source]'s last combat spell (Wind / Water / Earth / Fire ...), or -1. */
    private fun elementOf(source: EntityRef?): Int {
        val name = (source as? PlayerRef)?.get(Attr.LAST_SPELL) ?: return -1
        return SPELL_WORDS.indexOfFirst { name.startsWith(it) }
    }

    /** DH1: the cauldron steps round a radius-4 ring (t23 of the cycle); a player in its way is shoved 2 tiles for up to 9. */
    private fun moveCauldron(npc: NpcRef, t: Trio) {
        val focus = t.focus?.takeIf { it.isInWorld } ?: return
        t.focusStep = (t.focusStep + 1) % FOCUS_RING.size
        val to = focusTile(t, t.focusStep)
        focus.teleport(to)
        for (p in TfaKit.arena(npc)) {
            if (!TfaKit.under(focus, p.coord)) continue
            val dx = Integer.signum(p.coord.x - to.x)
            val dz = Integer.signum(p.coord.z - to.z)
            p.teleport(CoordGrid(p.coord.x + dx * SHOVE, p.coord.z + dz * SHOVE, p.coord.level))
            CombatSystem.applyHit(p, focus, BossCombat.roll(npc, 0, SHOVE_MAX), HitStyle.TYPELESS)
        }
    }

    private fun focusTile(t: Trio, step: Int): CoordGrid {
        val (dx, dz) = FOCUS_RING[step]
        return CoordGrid(t.centre.x + dx, t.centre.z + dz, t.centre.level)
    }

    /**
     * Capture: two rifts (16651, 20 HP) at one site (RuneLite: the corners); imps two at a time at +8, +11 and +14;
     * the rifts close at +19.
     */
    private fun rifts(npc: NpcRef, t: Trio) {
        val (dx, dz) = PORTAL_CORNERS[t.breaks % PORTAL_CORNERS.size]
        val site = CoordGrid(t.centre.x + dx, t.centre.z + dz, t.centre.level)
        val rifts =
            List(RIFTS) { k -> Bosses.summon(npc, Npcs.TFA_DAGONHAI_RIFT, CoordGrid(site.x + k * 2, site.z, site.level), huntRange = 0) }
        for (r in rifts) TfaKit.setPool(r, RIFT_HP)
        for (wave in IMP_WAVES) {
            npc.queueHit(wave, strong = false) {
                for (r in rifts) if (r.isInWorld) Bosses.summon(npc, Npcs.TFA_DAGONHAI_DEMON, BossCombat.randomTile(npc, r.coord, 1))
            }
        }
        npc.queueHit(RIFTS_GONE, strong = false) { for (r in rifts) if (r.isInWorld) r.despawn() }
    }

    private val SHIELDED = setOf(Npcs.TFA_DAGONHAI_LEADER, Npcs.TFA_DAGONHAI_MINION_1, Npcs.TFA_DAGONHAI_MINION_2)
    private val UNSHIELDED =
        setOf(Npcs.TFA_DAGONHAI_LEADER_UNSHIELDED, Npcs.TFA_DAGONHAI_MINION_1_UNSHIELDED, Npcs.TFA_DAGONHAI_MINION_2_UNSHIELDED)
    private val UNSHIELDED_OF =
        mapOf(
            Npcs.TFA_DAGONHAI_LEADER to Npcs.TFA_DAGONHAI_LEADER_UNSHIELDED,
            Npcs.TFA_DAGONHAI_MINION_1 to Npcs.TFA_DAGONHAI_MINION_1_UNSHIELDED,
            Npcs.TFA_DAGONHAI_MINION_2 to Npcs.TFA_DAGONHAI_MINION_2_UNSHIELDED,
        )

    /** DH4: the element each chant asks for (fire, water, air, earth), as indices into [SPELL_WORDS]. */
    private val CHANT_ELEMENTS = intArrayOf(3, 1, 0, 2)
    private val SPELL_WORDS = listOf("Wind", "Water", "Earth", "Fire")
    private val CHANTS = listOf("I'll feed the flames!", "I'll guide the flow!", "I'll focus the winds!", "I'll strengthen the bond!")

    /** RuneLite (LU DagonhaiTrio.java:186-221): the god yells, Zamorak / Bandos / Saradomin. */
    private val GOD_YELLS = listOf("Zamorak empowers!", "Bandosian strength!", "Saradominist zeal!")
    private val FOCUS_RING = listOf(0 to 4, 3 to 3, 4 to 0, 3 to -3, 0 to -4, -3 to -3, -4 to 0, -3 to 3)
    private val PORTAL_CORNERS = listOf(-7 to 7, 7 to -7, 7 to 7, -7 to -7)
    private val IMP_WAVES = intArrayOf(8, 11, 14)

    /** Capture: the shared shield is 640 at two and 930 at three (x1.45, then +0.42 a raider, rounded to 10). */
    private val SHIELD_HP = intArrayOf(640, 930, 1200, 1470, 1730, 2000, 2270)

    private const val MIN_SCALE = 2
    private const val MEMBERS = 3
    private const val SCAN = 20
    private const val HUNT = 20
    private const val ELDER_MAX = 30
    private const val ELDER_SPEED = 5
    private const val ELDER_HIT_DELAY = 5
    private const val CYCLE = 50
    private const val SEARCH_AT = 13
    private const val CALL_AT = 16
    private const val BOOK_STRIKE = 21
    private const val CAULDRON_MOVE_AT = 23
    private const val SECOND_CHANNEL_AT = 25
    private const val CHANNEL_TICKS = 18
    private const val TOMES = 3
    private const val RITUAL_STEP = 25
    private const val RITUAL_FULL = 100
    private const val RITUAL_LOSS = 20
    private const val ENRAGE_FIRST = 6
    private const val ENRAGE_EVERY = 5
    private const val BOLT_MIN = 15
    private const val BOLT_MAX = 22
    private const val ZAM_MIN = 23
    private const val ZAM_MAX = 27
    private const val ZAM_BURNS = 5
    private const val ZAM_BURN_EVERY = 4
    private const val ZAM_BURN = 3
    private const val GLYPH = 10
    private const val GLYPH_MIN = 3
    private const val GLYPH_SPREAD = 3
    private const val BANDOS_CATCH = 51
    private const val RING_REACH = 8
    private const val RING_MIN = 23
    private const val RING_MAX = 29
    private const val SARA_CATCH_HEAL = 13
    private const val SARA_STRIKES = 27
    private const val SARA_HEALS = 6
    private const val SARA_SPREAD = 6
    private const val SARA_MAX = 13
    /** OPEN-DH8: what a Saradomin healing tile restores to a trio member (the captures record only the catcher 13). */
    private const val SARA_HEAL = 20
    private const val BLUE_DELAY = 4
    private const val YELLOW_DELAY = 5
    private const val STUN_TICKS = 11
    private const val STUN_BONUS_NUM = 115
    private const val STUN_BONUS_DEN = 100
    private const val BREAKS = 5
    private const val CAULDRON_HP = 1000
    private const val CAULDRON_PER_BREAK = 200
    private const val EXPLOSION_STUN = 20
    private const val SHOVE = 2
    private const val SHOVE_MAX = 9
    private const val RIFTS = 2
    private const val RIFT_HP = 20
    private const val RIFTS_GONE = 19
}

/** Capture: an imp (16650, 44 HP) swings every 4 ticks beside its raider (0..39) and leaves after two swings. */
internal object TfaDagonhaiImp : Boss(Npcs.TFA_DAGONHAI_DEMON) {
    private val SWINGS = AttrKey<Int>("tfa_imp_swings")

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        if (distance) return
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        val dmg = if (victim is PlayerRef && victim.protects(CombatStyle.MELEE)) 0 else BossCombat.roll(npc, 0, MAX_HIT)
        CombatSystem.queueHit(victim, npc, dmg, HitStyle.MELEE, 0)
        val n = (npc[SWINGS] ?: 0) + 1
        npc[SWINGS] = n
        npc.setAttackClock(npc.tick + SWING_EVERY)
        if (n >= SWINGS_THEN_GONE) npc.queueHit(1) { npc.despawn() }
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
        TfaKit.setPool(npc, HP)
    }

    private const val MAX_HIT = 39
    private const val SWING_EVERY = 4
    private const val SWINGS_THEN_GONE = 2
    private const val HUNT = 16
    private const val HP = 44
}
