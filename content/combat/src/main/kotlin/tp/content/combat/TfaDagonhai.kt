package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * The Dagon'hai, TFA room 4 west (docs/mechanics/raids/tfa/dagonhai.md DH1-DH6): the Elder and two Monks share one
 * shield pool. Members take turns to channel (a magic hit interrupts it and stuns the channeller; an uninterrupted
 * channel fills the ritual bar by 25%, and a full bar empowers all three) and to read their tomes (Zamorak, Bandos,
 * Saradomin). Each shield break restores the shield; the fifth explodes the cauldron and all three stand stunned at 7 HP
 * (3 Nov fix: the explosion kills none of them).
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
    private class Trio(val size: Int) {
        var breaks = 0
        var ritual = 0
        var empoweredUntil = -1L
        var turn = 0
        var tome = 0
        var channeller: NpcRef? = null
        var channelEnds = -1L
        var channelElement = 0
        var focusStep = 0
        var focus: NpcRef? = null
        val members = ArrayList<NpcRef>(MEMBERS)
    }

    private val TRIO = AttrKey<Trio>("tfa_dagonhai")
    private val STUNNED_UNTIL = AttrKey<Long>("tfa_dagonhai_stun")

    private fun trio(npc: NpcRef): Trio {
        npc[TRIO]?.let { return it }
        val members = npc.npcsWithin(npc.coord, SCAN).filter { it.type in SHIELDED }
        val t = Trio(TfaKit.size(npc))
        t.members += members
        val shield = SHIELD_HP[t.size - MIN_SCALE]
        for (m in members) {
            m[TRIO] = t
            TfaKit.setPool(m, shield)
        }
        // DH1: the cauldron (the Dagon'hai focus NPC) circles the room's centre.
        t.focus = Bosses.summon(npc, Npcs.TFA_DAGONHAI_FOCUS, focusTile(npc.spawn, 0), huntRange = 0)
        return t
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val t = trio(npc)
        if (npc.tick < (npc[STUNNED_UNTIL] ?: 0L) || npc.type in UNSHIELDED) return
        resolveChannel(npc, t)
        if (npc.type == Npcs.TFA_DAGONHAI_LEADER) moveCauldron(npc, t)
        // DH5: the order cycles one tome read and one channel per member (Monk N tome, Monk S channel, Elder channel, ...).
        if (t.channeller == null && t.members.indexOf(npc) == t.turn % t.members.size) {
            t.turn++
            if (t.turn % 2 == 0) channel(npc, t) else tome(npc, t, victim)
            return
        }
        val empowered = npc.tick < t.empoweredUntil
        val max =
            (if (npc.type == Npcs.TFA_DAGONHAI_LEADER) ELDER_MAX else MONK_MAX) * (if (empowered) EMPOWER_NUM else 1) /
                (if (empowered) EMPOWER_DEN else 1)
        val magic = npc.random(2) == 0
        CombatSystem.announceAttack(npc, victim, if (magic) CombatStyle.MAGIC else CombatStyle.RANGED)
        val elder = npc.type == Npcs.TFA_DAGONHAI_LEADER
        // RuneLite (LU DagonhaiTrio.java:136-142): the Elder attacks every 5 ticks, ranged (seq 14593) or magic (14597),
        // hitting 5 ticks after the animation (thrown 2 ticks in, 3 ticks to land).
        if (elder) {
            npc.anim(if (magic) Seqs.NPC_DAGONHAI_ATTACK_MAGIC_01 else Seqs.NPC_DAGONHAI_ATTACK_RANGED_01)
            npc.setAttackClock(npc.tick + ELDER_SPEED)
        }
        // DH2: the Elder's attacks hit the target and every player within 3 tiles of them.
        val targets =
            if (npc.type ==
                Npcs.TFA_DAGONHAI_LEADER
            ) {
                TfaKit.arena(npc).filter { it.distanceTo(victim) <= SPLASH }
            } else {
                listOf(victim)
            }
        for (p in targets) {
            val style = if (magic) CombatStyle.MAGIC else CombatStyle.RANGED
            val delay = if (elder) ELDER_HIT_DELAY else BossCombat.delay(npc, p, style)
            BossCombat.strike(npc, p, if (magic) AttackType.MAGIC else AttackType.RANGED, max, delay)
        }
    }

    /** DH3 / DH4: a channel ("I'll feed the flames!" ...); RuneLite (LU DagonhaiTrio.java:68-75): it completes 18 ticks after the yell. */
    private fun channel(npc: NpcRef, t: Trio) {
        t.channelElement = npc.random(CHANTS.size)
        npc.say(CHANTS[t.channelElement])
        npc.spotAnim(SpotAnims.TFA_DAGONHAI_DEMONIC_STRENGTH_TELEGRAPH)
        t.channeller = npc
        t.channelEnds = npc.tick + CHANNEL_TICKS
    }

    private fun resolveChannel(npc: NpcRef, t: Trio) {
        val c = t.channeller ?: return
        if (npc.tick < t.channelEnds) return
        t.channeller = null
        t.ritual += RITUAL_STEP
        if (t.ritual >= RITUAL_FULL) {
            t.ritual = 0
            t.empoweredUntil = npc.tick + EMPOWER_TICKS
            for (m in t.members) m.spotAnim(SpotAnims.TFA_DAGONHAI_DEMONIC_STRENGTH_TELEGRAPH)
        }
        c.say("The ritual grows stronger!")
    }

    /**
     * DH5 tomes (RuneLite LU DagonhaiTrio.java:68-78, 186-221; DagonhaiTrioOverlay.java): "What have we here?" warns, the
     * god is called 3 ticks later; the tome travels from the reader to the cauldron along the floor, and a player on
     * that path within 4 ticks of the call blocks it ("Curse you!"); otherwise it completes 8 ticks after the call.
     */
    private fun tome(npc: NpcRef, t: Trio, victim: EntityRef) {
        val god = t.tome++ % TOMES
        npc.say("What have we here?")
        npc.queueHit(TOME_CALL) {
            if (!npc.isInWorld) return@queueHit
            npc.say(GOD_YELLS[god])
            val cauldron = t.focus?.coord ?: npc.spawn
            val path = TfaKit.line(npc.coord, cauldron)
            var blocked = false
            for (k in 0..TOME_BLOCK) {
                npc.queueHit(k) {
                    if (!blocked && TfaKit.arena(npc).any { TfaKit.on(it, path) }) {
                        blocked = true
                        npc.say("Curse you!")
                    }
                }
            }
            npc.queueHit(TOME_COMPLETE) {
                if (npc.isInWorld && !blocked) tomeEffect(npc, t, god, victim, cauldron)
            }
        }
    }

    private fun tomeEffect(npc: NpcRef, t: Trio, god: Int, victim: EntityRef, cauldron: tp.util.CoordGrid) {
        when (god) {
            // Zamorak: ~25 to the target and flame glyphs that erupt 3-5 ticks after they appear (LU overlay:167).
            0 -> {
                BossCombat.strike(npc, victim, AttackType.MAGIC, ZAMORAK_MAX, 2, accurate = true)
                for (p in TfaKit.arena(npc)) {
                    val glyph = p.coord
                    npc.spotAnimAt(glyph, SpotAnims.TFA_DAGONHAI_DEMONIC_STRENGTH_TELEGRAPH)
                    npc.queueHit(GLYPH_MIN + npc.random(GLYPH_SPREAD)) {
                        if (npc.isInWorld && p.coord == glyph) CombatSystem.applyHit(p, npc, FIREBALL, HitStyle.MAGIC)
                    }
                }
            }

            // Bandos: up to 55 to the target and a shockwave ring spreading one tile per tick from the cauldron
            // (LU overlay:104-106; OPEN-DH7: 15 per player it reaches).
            1 -> {
                BossCombat.strike(npc, victim, AttackType.CRUSH, BANDOS_MAX, 1)
                for (p in TfaKit.arena(npc)) {
                    if (p == victim) continue
                    val d = maxOf(kotlin.math.abs(p.coord.x - cauldron.x), kotlin.math.abs(p.coord.z - cauldron.z))
                    npc.queueHit(maxOf(1, d)) {
                        val now = maxOf(kotlin.math.abs(p.coord.x - cauldron.x), kotlin.math.abs(p.coord.z - cauldron.z))
                        if (npc.isInWorld && now == d) CombatSystem.applyHit(p, npc, SHOCKWAVE, HitStyle.MELEE)
                    }
                }
            }

            // Saradomin: blue lightning (hurts, ~4 ticks after its telegraph) on players and yellow (heals, ~5 ticks) on
            // the trio (LU overlay:189-190); heals 10-20, or 50 when empowered (DH5; OPEN-DH8: blue 20).
            else -> {
                val empowered = npc.tick < t.empoweredUntil
                val heal = if (empowered) SARA_EMPOWERED else SARA_MIN + npc.random(SARA_MAX - SARA_MIN + 1)
                for (m in t.members) {
                    m.spotAnim(SpotAnims.TFA_DAGONHAI_SARADOMIN_HEAL_TELEGRAPH_150)
                    m.queueHit(YELLOW_DELAY) {
                        m.spotAnim(SpotAnims.TFA_DAGONHAI_SARADOMIN_HEAL_LIGHTNING)
                        if (m.isInWorld) m.hitpoints = minOf(m.maxHitpoints, m.hitpoints + heal)
                    }
                }
                if (empowered) {
                    for (p in TfaKit.arena(npc)) {
                        val at = p.coord
                        npc.spotAnimAt(at, SpotAnims.TFA_DAGONHAI_SARADOMIN_DAMAGE_TELEGRAPH_150)
                        npc.queueHit(BLUE_DELAY) {
                            npc.spotAnimAt(at, SpotAnims.TFA_DAGONHAI_SARADOMIN_DAMAGE_LIGHTNING)
                            if (npc.isInWorld && p.coord == at) CombatSystem.applyHit(p, npc, SARA_LIGHTNING, HitStyle.MAGIC)
                        }
                    }
                }
            }
        }
    }

    /** DH4: a channelling member hit by magic is interrupted and stunned; a stunned member takes 15% more damage. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val t = trio(npc)
        // DH4: only that element's spell interrupts (a splash counts: it still lands as a 0).
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

    /** DH6: a shield break restores the shield (ritual -20%); the fifth explodes the cauldron. */
    override fun onLethal(npc: NpcRef): Boolean {
        val t = trio(npc)
        if (npc.type in UNSHIELDED) return false
        t.breaks++
        t.ritual = maxOf(0, t.ritual - RITUAL_LOSS)
        if (t.breaks < BREAKS) {
            val shield = SHIELD_HP[t.size - MIN_SCALE]
            for (m in t.members) if (m.isInWorld) m.hitpoints = shield
            npc.say("Zamorak, restore us!")
            portals(npc, t)
            return true
        }
        // The cauldron explodes; all three stand stunned at 7 HP (the unshielded types). 3 Nov: it no longer kills any
        // of them ("you should have the satisfaction of dealing those last hits").
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

    /** The standard-spellbook element of [source]'s last combat spell (Wind / Water / Earth / Fire ...), or -1. */
    private fun elementOf(source: EntityRef?): Int {
        val name = (source as? tp.plugin.PlayerRef)?.get(Attr.LAST_SPELL) ?: return -1
        return SPELL_WORDS.indexOfFirst { name.startsWith(it) }
    }

    /** DH1: the cauldron steps around a radius-4 ring; a player in its way is shoved 2 tiles for up to 9. */
    private fun moveCauldron(npc: NpcRef, t: Trio) {
        val focus = t.focus?.takeIf { it.isInWorld } ?: return
        t.focusStep = (t.focusStep + 1) % FOCUS_RING.size
        val to = focusTile(npc.spawn, t.focusStep)
        focus.teleport(to)
        for (p in TfaKit.arena(npc)) {
            if (!TfaKit.under(focus, p.coord)) continue
            val dx = Integer.signum(p.coord.x - to.x)
            val dz = Integer.signum(p.coord.z - to.z)
            p.teleport(tp.util.CoordGrid(p.coord.x + dx * SHOVE, p.coord.z + dz * SHOVE, p.coord.level))
            CombatSystem.applyHit(p, focus, BossCombat.roll(npc, 0, SHOVE_MAX), HitStyle.TYPELESS)
        }
    }

    private fun focusTile(centre: tp.util.CoordGrid, step: Int): tp.util.CoordGrid {
        val (dx, dz) = FOCUS_RING[step]
        return tp.util.CoordGrid(centre.x + dx, centre.z + dz, centre.level)
    }

    /**
     * DH6 / OPEN-DH4: after a break, imp portals open: one at party size 4 or less, else two corners; three imps each
     * for the first three breaks, two for the fourth (Strat's two readings combined).
     */
    private fun portals(npc: NpcRef, t: Trio) {
        val count = if (t.size <= SMALL_PARTY) 1 else 2
        val imps = if (t.breaks < BREAKS - 1) IMPS_EARLY else IMPS_LATE
        for (k in 0 until count) {
            val (dx, dz) = PORTAL_CORNERS[k]
            val at = tp.util.CoordGrid(npc.spawn.x + dx, npc.spawn.z + dz, npc.spawn.level)
            val portal = Bosses.summon(npc, Npcs.TFA_DAGONHAI_RIFT, at, huntRange = 0)
            portal.queueHit(PORTAL_OPEN) {
                repeat(imps) { Bosses.summon(npc, Npcs.TFA_DAGONHAI_DEMON, BossCombat.randomTile(npc, at, 1)) }
                portal.despawn()
            }
        }
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
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

    /** Standard-spellbook combat spell names start with these (air spells are "Wind ..."). */
    private val SPELL_WORDS = listOf("Wind", "Water", "Earth", "Fire")
    private val FOCUS_RING = listOf(0 to 4, 3 to 3, 4 to 0, 3 to -3, 0 to -4, -3 to -3, -4 to 0, -3 to 3)
    private val PORTAL_CORNERS = listOf(-7 to 7, 7 to -7)

    /** RuneLite (LU DagonhaiTrio.java:186-221): the god yells, Zamorak / Bandos / Saradomin. */
    private val GOD_YELLS = listOf("Zamorak empowers!", "Bandosian strength!", "Saradominist zeal!")

    /** DH4 chants. */
    private val CHANTS = listOf("I'll feed the flames!", "I'll guide the flow!", "I'll focus the winds!", "I'll strengthen the bond!")

    /** OPEN-DH2: the shared shield is 640 at 1-2 players (cache); larger sizes follow Ezekiel's published ratios. */
    private val SHIELD_HP = intArrayOf(640, 928, 1197, 1466, 1734, 2003, 2272)

    private const val MIN_SCALE = 2
    private const val MEMBERS = 3
    private const val SCAN = 20
    private const val HUNT = 20
    private const val SPLASH = 3

    /** OPEN-DH1: max hits (Elder 30, Monks 25); empowered x1.5 for 30 ticks. */
    private const val ELDER_MAX = 30
    private const val MONK_MAX = 25
    private const val EMPOWER_NUM = 3
    private const val EMPOWER_DEN = 2
    private const val EMPOWER_TICKS = 30
    private const val CHANNEL_TICKS = 18
    private const val TOME_CALL = 3
    private const val TOME_BLOCK = 4
    private const val TOME_COMPLETE = 8
    private const val GLYPH_MIN = 3
    private const val GLYPH_SPREAD = 3
    private const val BLUE_DELAY = 4
    private const val YELLOW_DELAY = 5
    private const val ELDER_SPEED = 5
    private const val ELDER_HIT_DELAY = 5
    private const val RITUAL_STEP = 25
    private const val RITUAL_FULL = 100
    private const val RITUAL_LOSS = 20
    private const val TOMES = 3
    private const val ZAMORAK_MAX = 25
    private const val FIREBALL = 10
    private const val BANDOS_MAX = 55
    private const val SARA_MIN = 10
    private const val SARA_MAX = 20
    private const val SARA_EMPOWERED = 50
    private const val SARA_LIGHTNING = 20
    private const val SHOCKWAVE = 15
    private const val STUN_TICKS = 5
    private const val STUN_BONUS_NUM = 115
    private const val STUN_BONUS_DEN = 100
    private const val BREAKS = 5
    private const val EXPLOSION_STUN = 20
    private const val SHOVE = 2
    private const val SHOVE_MAX = 9
    private const val SMALL_PARTY = 4
    private const val IMPS_EARLY = 3
    private const val IMPS_LATE = 2
    private const val PORTAL_OPEN = 4
}

/** DH6: an imp from the portals; "players always max-hit imps" (here: any hit kills one). OPEN-DH6: melee up to 8. */
internal object TfaDagonhaiImp : Boss(Npcs.TFA_DAGONHAI_DEMON) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        BossCombat.strike(npc, victim, AttackType.STAB, MAX_HIT, 0)
    }

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int =
        if (source is tp.plugin.PlayerRef) maxOf(damage, npc.hitpoints) else damage

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    private const val MAX_HIT = 8
    private const val HUNT = 16
}
