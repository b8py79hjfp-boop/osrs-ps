package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.symbols.Npcs
import tp.symbols.SpotAnims

/**
 * The Dagon'hai, TFA room 4 west (docs/mechanics/raids/tfa/dagonhai.md DH1-DH6): the Elder and two Monks share one
 * shield pool. Members take turns to channel (a magic hit interrupts it and stuns the channeller; an uninterrupted
 * channel fills the ritual bar by 25%, and a full bar empowers all three) and to read their tomes (Zamorak, Bandos,
 * Saradomin). Each shield break restores the shield; the fifth explodes the cauldron: one monk dies and the others
 * stand stunned at 7 HP.
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
        return t
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val t = trio(npc)
        if (npc.tick < (npc[STUNNED_UNTIL] ?: 0L) || npc.type in UNSHIELDED) return
        resolveChannel(npc, t)
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
            BossCombat.strike(npc, p, if (magic) AttackType.MAGIC else AttackType.RANGED, max, BossCombat.delay(npc, p, style))
        }
    }

    /** DH3 / DH4: a channel ("I'll feed the flames!" ...); OPEN-DH3: it lasts 6 ticks; OPEN-DH5: any magic hit interrupts. */
    private fun channel(npc: NpcRef, t: Trio) {
        npc.say(CHANTS[npc.random(CHANTS.size)])
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

    /** DH5: Zamorak (~25 + fireballs), Bandos (up to 55 + shockwave), Saradomin (heals 10-20). */
    private fun tome(npc: NpcRef, t: Trio, victim: EntityRef) {
        when (t.tome++ % TOMES) {
            0 -> {
                BossCombat.strike(npc, victim, AttackType.MAGIC, ZAMORAK_MAX, 2, accurate = true)
                for (p in TfaKit.arena(npc)) if (p.distanceTo(victim) in 1..1) CombatSystem.queueHit(p, npc, FIREBALL, HitStyle.MAGIC, 2)
            }

            1 -> {
                BossCombat.strike(npc, victim, AttackType.CRUSH, BANDOS_MAX, 1)
            }

            else -> {
                npc.spotAnim(SpotAnims.TFA_DAGONHAI_SARADOMIN_HEAL_IMPACT)
                val heal = SARA_MIN + npc.random(SARA_MAX - SARA_MIN + 1)
                for (m in t.members) m.hitpoints = minOf(m.maxHitpoints, m.hitpoints + heal)
            }
        }
    }

    /** DH4: a channelling member hit by magic is interrupted and stunned; a stunned member takes 15% more damage. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val t = trio(npc)
        if (style == HitStyle.MAGIC && t.channeller == npc) {
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
            return true
        }
        // The cauldron explodes: one monk dies, the rest are stunned at 7 HP (the unshielded types).
        val monk = t.members.lastOrNull { it.type != Npcs.TFA_DAGONHAI_LEADER }
        for (m in t.members) {
            if (!m.isInWorld) continue
            m.transform(UNSHIELDED_OF.getValue(m.type), restoreStats = true)
            m[STUNNED_UNTIL] = m.tick + EXPLOSION_STUN
            if (m == monk) m.hitpoints = 0
        }
        monk?.let { CombatSystem.applyHit(it, null, 1, HitStyle.TYPELESS) }
        return true
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
    private const val CHANNEL_TICKS = 6
    private const val RITUAL_STEP = 25
    private const val RITUAL_FULL = 100
    private const val RITUAL_LOSS = 20
    private const val TOMES = 3
    private const val ZAMORAK_MAX = 25
    private const val FIREBALL = 10
    private const val BANDOS_MAX = 55
    private const val SARA_MIN = 10
    private const val SARA_MAX = 20
    private const val STUN_TICKS = 5
    private const val STUN_BONUS_NUM = 115
    private const val STUN_BONUS_DEN = 100
    private const val BREAKS = 5
    private const val EXPLOSION_STUN = 20
}
