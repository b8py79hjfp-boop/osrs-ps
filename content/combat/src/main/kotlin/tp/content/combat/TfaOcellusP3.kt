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
 * Ocellus P3 (16677; OC3), from the live-game captures (capture-values.md):
 * - Rotation: triple (basic +0, basic +3, slash +6; next +10), triple, then the big attack: the spin and Insolence by
 *   turns, Untether in Insolence's place when HP is within 8% of the next quarter; a volley replaces a triple when the
 *   tank is not beside him. Basics 1..7 through Protect from Melee, else 12..19; the slash 25..28 and Insolence's three
 *   superior slashes 39..41 at a random raider's tile; the spin hits 15..27 on each loop and the out.
 * - Tank: the 2nd raider in HUD order, the next each segment, wrapping.
 * - The quadrant falls at <= Q + 1% (Q = 75 / 50 / 25), at once unless he is spinning or unleashing; three moves, the
 *   last quadrant is P4.
 * - Void bombs: one per standing quadrant, two batches a segment on non-tanks' tiles (6..11), each leaving a patch
 *   (6..7 every 2 ticks, into P4). One vine a segment, with the first triple after its first spin, never on the tank.
 * - Rifts (44 HP): one kind a segment, Augmenter, Void Rift, healing in turn, max(1, living / 2) of them.
 */
internal object TfaOcellusP3 {
    private enum class Big { SPIN, INSOLENCE, UNTETHER }

    private val SPINNING = AttrKey<Long>("tfa_ocellus_spin_until")

    fun start(npc: NpcRef, s: OcellusState) {
        for (b in Bosses.minions(npc, Npcs.TFA_OCELLUS_P2_SWORD_HITBOX)) b.despawn()
        TfaOcellus.become(npc, s, TfaOcellus.P3, Npcs.TFA_OCELLUS_P3, freshPool = false)
        npc.teleport(TfaOcellus.quadrantSpot(s, s.quadrant))
        beginSegment(npc, s, FIRST_BOMBS)
        var step = 0
        TfaOcellus.loop(npc, s) {
            patchDamage(npc, s)
            if (s.pendingMove && npc.tick >= s.busyUntil) move(npc, s)
            if (npc.tick < s.busyUntil || s.phase != TfaOcellus.P3) return@loop
            val tank = tank(npc, s) ?: return@loop
            if (step % ROTATION < 2) {
                triple(npc, s, tank)
            } else {
                big(npc, s)
            }
            step++
        }
    }

    /** Capture (pre-28 Oct): the tank is the 2nd raider in HUD order, the next each segment, wrapping over the living. */
    fun tank(npc: NpcRef, s: OcellusState): PlayerRef? {
        val players = TfaKit.arena(npc)
        val order = npc[SharedAttrs.RAID_ORDER] ?: return players.minByOrNull { TfaKit.gap(npc, it) }
        val living = order.mapNotNull { n -> players.firstOrNull { it.username.equals(n, ignoreCase = true) } }
        if (living.isEmpty()) return players.firstOrNull()
        return living[(1 + s.segment) % living.size]
    }

    private fun beginSegment(npc: NpcRef, s: OcellusState, bombsAfter: Int) {
        s.spun = false
        s.vined = false
        s.batches = 0
        npc.queueHit(bombsAfter, strong = false) { if (s.phase == TfaOcellus.P3) bombs(npc, s) }
        rifts(npc, s)
    }

    // ---- the rotation

    private fun triple(npc: NpcRef, s: OcellusState, tank: PlayerRef) {
        if (TfaKit.gap(npc, tank) > 1) {
            hybridVolley(npc)
            s.busyUntil = npc.tick + HYBRID_LENGTH
            return
        }
        if (s.spun && !s.vined && !nearQuarter(npc, s, VINE_GUARD)) {
            s.vined = true
            vine(npc, s, tank)
        }
        basic(npc, tank, 0)
        basic(npc, tank, BASIC_GAP)
        npc.queueHit(SLASH_AT, strong = false) {
            if (npc.isInWorld &&
                s.phase == TfaOcellus.P3
            ) {
                slash(npc, SLASH_MIN, SLASH_MAX, Seqs.NPC_OCELLUS_PHASE3_MELEE_SLASH)
            }
        }
        s.busyUntil = npc.tick + TRIPLE_LENGTH
    }

    /** Capture: triple swings: Protect from Melee 1..7, unprayed 12 and 19 seen (OPEN-OC12: 12..19). */
    private fun basic(npc: NpcRef, tank: PlayerRef, after: Int) {
        npc.queueHit(after, strong = false) {
            if (!npc.isInWorld || tank.isDying) return@queueHit
            npc.anim(Seqs.NPC_OCELLUS_PHASE3_MELEE_BASIC)
            CombatSystem.announceAttack(npc, tank, CombatStyle.MELEE)
            val raw =
                if (tank.protects(
                        CombatStyle.MELEE,
                    )
                ) {
                    BossCombat.roll(npc, 1, PRAYED_MAX)
                } else {
                    BossCombat.roll(npc, BASIC_MIN, BASIC_MAX)
                }
            val dmg = if (TfaOcellusRift.augmenting(npc)) raw * (TfaOcellus.PERCENT + AUGMENT) / TfaOcellus.PERCENT else raw
            CombatSystem.queueHit(tank, npc, dmg, HitStyle.MELEE, 0)
        }
    }

    /** Capture: a slash at a random raider's tile, landing a tick later (OPEN-OC12). */
    private fun slash(npc: NpcRef, min: Int, max: Int, seq: Int) {
        val players = TfaKit.arena(npc)
        val p = players.getOrNull(npc.random(maxOf(1, players.size))) ?: return
        val at = p.coord
        npc.anim(seq)
        npc.queueHit(1, strong = false) {
            for (v in TfaKit.arena(npc)) if (v.coord == at) CombatSystem.applyHit(v, npc, BossCombat.roll(npc, min, max), HitStyle.MELEE)
        }
    }

    /** OC3 / RuneLite: the hybrid (seq 14844, 7 ticks) at everyone when the tank is out of reach (OPEN-OC12: 20). */
    private fun hybridVolley(npc: NpcRef) {
        npc.anim(Seqs.NPC_OCELLUS_PHASE3_MELEE_HYBRID)
        val magic = npc.random(2) == 0
        val style = if (magic) CombatStyle.MAGIC else CombatStyle.RANGED
        npc.spotAnim(if (magic) SpotAnims.VFX_OCELLUS_PHASE3_MELEE_HYBRID_MAGIC else SpotAnims.VFX_OCELLUS_PHASE3_MELEE_HYBRID_RANGED)
        for (p in TfaKit.arena(
            npc,
        )) {
            BossCombat.strike(npc, p, if (magic) AttackType.MAGIC else AttackType.RANGED, VOID_SHOT, BossCombat.delay(npc, p, style))
        }
    }

    private fun nearQuarter(npc: NpcRef, s: OcellusState, margin: Int): Boolean {
        if (s.moves >= QUARTERS.size) return false
        val pct = npc.hitpoints * TfaOcellus.PERCENT / maxOf(1, npc.maxHitpoints)
        return pct <= QUARTERS[s.moves] + margin
    }

    private fun big(npc: NpcRef, s: OcellusState) {
        val kind =
            when {
                s.spinNext -> Big.SPIN
                nearQuarter(npc, s, UNTETHER_GUARD) -> Big.UNTETHER
                else -> Big.INSOLENCE
            }
        s.spinNext = !s.spinNext
        s.bigs++
        when (kind) {
            Big.SPIN -> spin(npc, s)
            Big.INSOLENCE -> insolence(npc, s)
            Big.UNTETHER -> untether(npc, s)
        }
        if (s.batches == 1) npc.queueHit(1, strong = false) { if (s.phase == TfaOcellus.P3) bombs(npc, s) }
    }

    /**
     * RuneLite (LU FinalBoss.java:794-801) / wiki: the spin (Void Spin; seq 14869 in, 14870 loop): an overhead still on 6
     * ticks in is turned off; capture: then hits of 15..27 on each loop and on the out, blocked by Protect from Melee if
     * back on in time (OPEN-OC7: two loops 3 ticks apart and the out).
     */
    private fun spin(npc: NpcRef, s: OcellusState) {
        s.spun = true
        npc.anim(Seqs.NPC_OCELLUS_PHASE3_PRAYER_IN)
        npc[SPINNING] = npc.tick + SPIN_LENGTH
        npc.queueHit(PRAYER_CHECK, strong = false) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            for (p in TfaKit.arena(npc)) if (praying(p)) p.deactivateAllPrayers()
        }
        for ((k, at) in SPIN_HITS.withIndex()) {
            npc.queueHit(at, strong = false) {
                if (!npc.isInWorld || s.phase != TfaOcellus.P3) return@queueHit
                if (k <
                    SPIN_HITS.size - 1
                ) {
                    npc.anim(Seqs.NPC_OCELLUS_PHASE3_PRAYER_LOOP)
                } else {
                    npc.spotAnim(SpotAnims.VFX_OCELLUS_PHASE3_MELEE_PRAYER_OUT)
                }
                for (p in TfaKit.arena(npc)) {
                    if (!p.protects(
                            CombatStyle.MELEE,
                        )
                    ) {
                        CombatSystem.applyHit(p, npc, BossCombat.roll(npc, SPIN_MIN, SPIN_MAX), HitStyle.MELEE)
                    }
                }
            }
        }
        s.busyUntil = npc.tick + SPIN_LENGTH
    }

    private fun praying(p: PlayerRef) = p.protects(CombatStyle.MELEE) || p.protects(CombatStyle.RANGED) || p.protects(CombatStyle.MAGIC)

    /** RuneLite (LU FinalBoss.java:1583-1595): "Insolence..." 2 ticks before three superior slashes 5 apart; capture 39..41. */
    private fun insolence(npc: NpcRef, s: OcellusState) {
        npc.say("Insolence...")
        for (k in 0 until INSOLENCE_SLAMS) {
            npc.queueHit(INSOLENCE_LEAD + k * INSOLENCE_GAP, strong = false) {
                if (!npc.isInWorld || s.phase != TfaOcellus.P3) return@queueHit
                val players = TfaKit.arena(npc)
                players.getOrNull(npc.random(maxOf(1, players.size)))?.let {
                    npc.addLoc(INSOLENCE_LOCS[k], it.coord, TfaOcellus.FLOOR_SHAPE, 0, INSOLENCE_GAP)
                }
                slash(npc, INSOLENCE_MIN, INSOLENCE_MAX, Seqs.NPC_OCELLUS_PHASE3_MELEE_SUPERIOR_SLASH)
            }
        }
        s.busyUntil = npc.tick + INSOLENCE_LEAD + INSOLENCE_SLAMS * INSOLENCE_GAP
    }

    /**
     * Capture: Untether: pools on every raider at U+6, 10, 14, 18, landing +4 (23..29, nothing through Protect from
     * Magic); U+29 35..42 to everyone not under him, U+30 34..41 to those under (18..21 if already hit at U+29).
     */
    private fun untether(npc: NpcRef, s: OcellusState) {
        npc.say("Untether...")
        npc[SPINNING] = npc.tick + UNTETHER_LENGTH
        for (at in POOL_TICKS) {
            npc.queueHit(at, strong = false) {
                if (!npc.isInWorld || s.phase != TfaOcellus.P3) return@queueHit
                for (p in TfaKit.arena(npc)) {
                    val tile = p.coord
                    npc.addLoc(Locs.TFA_OCELLUS_VOID_DEGEN_AOE, tile, TfaOcellus.FLOOR_SHAPE, 0, POOL_LAND + 1)
                    npc.queueHit(POOL_LAND, strong = false) {
                        for (v in TfaKit.arena(npc)) {
                            if (v.coord == tile &&
                                !v.protects(CombatStyle.MAGIC)
                            ) {
                                CombatSystem.applyHit(v, npc, BossCombat.roll(npc, POOL_MIN, POOL_MAX), HitStyle.MAGIC)
                            }
                        }
                    }
                }
            }
        }
        val hitOut = HashSet<PlayerRef>()
        npc.queueHit(ERUPT_OUT, strong = false) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            for (p in TfaKit.arena(npc)) {
                if (TfaKit.gap(npc, p) > 0) {
                    hitOut += p
                    CombatSystem.applyHit(p, npc, BossCombat.roll(npc, OUT_MIN, OUT_MAX), HitStyle.TYPELESS)
                }
            }
        }
        npc.queueHit(ERUPT_IN, strong = false) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            for (p in TfaKit.arena(npc)) {
                if (TfaKit.gap(npc, p) > 0) continue
                val dmg = if (p in hitOut) BossCombat.roll(npc, IN_AGAIN_MIN, IN_AGAIN_MAX) else BossCombat.roll(npc, IN_MIN, IN_MAX)
                CombatSystem.applyHit(p, npc, dmg, HitStyle.TYPELESS)
            }
        }
        s.busyUntil = npc.tick + UNTETHER_LENGTH
    }

    // ---- void bombs, vines and rifts

    /**
     * Capture: a batch of void bombs, one per standing quadrant, on non-tanks' tiles (6..11 on the tile); RuneLite (LU
     * FinalBoss.java:803-847): marked (spotanim 4302), dropped once the raider has stood 2 ticks, going off 2 ticks later.
     */
    private fun bombs(npc: NpcRef, s: OcellusState) {
        if (!npc.isInWorld || s.batches >= BATCHES) return
        s.batches++
        val tank = tank(npc, s)
        val targets = TfaKit.arena(npc).filter { it != tank }.ifEmpty { TfaKit.arena(npc) }
        if (targets.isEmpty()) return
        repeat(TfaOcellus.QUADRANTS.size - s.moves) { k ->
            val p = targets[k % targets.size]
            p.spotAnim(SpotAnims.VFX_OCELLUS_QUADRANT_BOMB_SPAWN_01)
            follow(npc, s, p, p.coord, 0, VOID_MIN_DROP)
        }
    }

    private fun follow(npc: NpcRef, s: OcellusState, p: PlayerRef, last: CoordGrid, still: Int, wait: Int) {
        npc.queueHit(1, strong = false) {
            if (!npc.isInWorld || p.isDying) return@queueHit
            val now = p.coord
            val stillNow = if (now == last) still + 1 else 0
            if (wait > 1 || stillNow < VOID_STOP) {
                follow(npc, s, p, now, stillNow, wait - 1)
                return@queueHit
            }
            npc.addLoc(Locs.TFA_OCELLUS_VOID_DEGEN_BOMB, now, TfaOcellus.FLOOR_SHAPE, 0, VOID_FUSE + 1)
            npc.queueHit(VOID_FUSE, strong = false) {
                npc.spotAnimAt(now, SpotAnims.VFX_OCELLUS_QUADRANT_BOMB_IMPACT_01)
                for (v in TfaKit.arena(npc)) {
                    if (v.coord ==
                        now
                    ) {
                        CombatSystem.applyHit(v, npc, BossCombat.roll(npc, BOMB_MIN, BOMB_MAX), HitStyle.TYPELESS)
                    }
                }
                s.patches += now
                npc.addLoc(Locs.TFA_OCELLUS_VOID_DEGEN_AOE, now, TfaOcellus.FLOOR_SHAPE, 0)
            }
        }
    }

    /** Capture: a patch deals 6..7 every 2 ticks to a raider on it; patches stay into P4. */
    fun patchDamage(npc: NpcRef, s: OcellusState) {
        if (npc.tick % PATCH_EVERY != 0L || s.patches.isEmpty()) return
        for (p in TfaKit.arena(npc)) {
            if (p.coord in
                s.patches
            ) {
                CombatSystem.applyHit(p, npc, BossCombat.roll(npc, PATCH_MIN, PATCH_MAX), HitStyle.TYPELESS)
            }
        }
    }

    /** OC3 (wiki, fact-tagged): the Void Vine (16681) roots a non-tank; OPEN-OC16: 50 unless broken within 8 ticks. */
    private fun vine(npc: NpcRef, s: OcellusState, tank: PlayerRef) {
        val p = TfaKit.arena(npc).filter { it != tank }.randomOrNull(npc) ?: return
        val vine = Bosses.summon(npc, Npcs.TFA_OCELLUS_P3_VOID_VINE, p.coord, huntRange = 0)
        p.frozenUntil = maxOf(p.frozenUntil, npc.tick + VINE_TICKS)
        npc.queueHit(VINE_TICKS, strong = false) {
            if (!vine.isInWorld || vine.isDying || s.phase < TfaOcellus.P3) return@queueHit
            vine.despawn()
            if (!p.isDying) CombatSystem.applyHit(p, npc, VINE_DAMAGE, HitStyle.TYPELESS)
        }
    }

    private fun List<PlayerRef>.randomOrNull(npc: NpcRef): PlayerRef? = if (isEmpty()) null else this[npc.random(size)]

    /** Capture: rifts (44 HP), one kind a segment in turn (Augmenter, Void Rift, healing), max(1, living / 2). */
    private fun rifts(npc: NpcRef, s: OcellusState) {
        val kind = RIFT_KINDS[s.segment % RIFT_KINDS.size]
        spawnRifts(npc, kind, maxOf(1, TfaKit.arena(npc).size / 2))
    }

    fun spawnRifts(npc: NpcRef, kind: Int, count: Int) {
        repeat(count) {
            val at = BossCombat.randomTile(npc, npc.coord, RIFT_SPREAD)
            val rift = Bosses.summon(npc, kind, at, huntRange = 0)
            TfaKit.setPool(rift, RIFT_HP)
        }
    }

    // ---- quadrant moves

    /** Capture: the quadrant falls at <= Q + 1%, at once unless he is spinning or unleashing (then when it ends). */
    fun checkQuadrant(npc: NpcRef, s: OcellusState) {
        if (s.pendingMove || s.moving || s.moves >= QUARTERS.size) return
        val pct = npc.hitpoints * TfaOcellus.PERCENT / maxOf(1, npc.maxHitpoints)
        if (pct > QUARTERS[s.moves] + 1) return
        s.pendingMove = true
        if (npc.tick >= (npc[SPINNING] ?: 0L)) {
            s.busyUntil = npc.tick
            move(npc, s)
        }
    }

    /** RuneLite (LU FinalBoss.java:803-847): "Run!", the destruction 15 ticks later (OPEN: 50 within 3), then the next quadrant. */
    private fun move(npc: NpcRef, s: OcellusState) {
        s.pendingMove = false
        s.moving = true
        s.moves++
        npc.say("Run!")
        npc.anim(Seqs.NPC_OCELLUS_QUADRANT_DESTROY_IN)
        val at = npc.coord
        s.busyUntil = npc.tick + RUN_COUNTDOWN + 1
        npc.queueHit(RUN_COUNTDOWN, strong = false) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            for (p in TfaKit.arena(npc)) {
                if (maxOf(kotlin.math.abs(p.coord.x - at.x), kotlin.math.abs(p.coord.z - at.z)) <= DESTRUCTION_RADIUS) {
                    CombatSystem.applyHit(p, npc, DESTRUCTION_DAMAGE, HitStyle.TYPELESS)
                }
            }
            s.quadrant = (s.quadrant + 1) % TfaOcellus.QUADRANTS.size
            npc.teleport(TfaOcellus.quadrantSpot(s, s.quadrant))
            s.segment++
            s.moving = false
            if (s.moves >= QUARTERS.size) {
                TfaOcellusP4.start(npc, s)
            } else {
                beginSegment(npc, s, BOMBS_AFTER_MOVE)
            }
        }
    }

    private val QUARTERS = intArrayOf(75, 50, 25)
    private val RIFT_KINDS =
        intArrayOf(Npcs.TFA_OCELLUS_P3_BOMBPORTAL_BUFF, Npcs.TFA_OCELLUS_P3_BOMBPORTAL_BASIC, Npcs.TFA_OCELLUS_P3_BOMBPORTAL_HEAL)
    private val INSOLENCE_LOCS =
        intArrayOf(
            Locs.TFA_OCELLUS_VOID_DEGEN_AOE_INSOLENCE_1,
            Locs.TFA_OCELLUS_VOID_DEGEN_AOE_INSOLENCE_4,
            Locs.TFA_OCELLUS_VOID_DEGEN_AOE_INSOLENCE_7,
        )
    private val SPIN_HITS = intArrayOf(7, 10, 13)
    private val POOL_TICKS = intArrayOf(6, 10, 14, 18)

    private const val ROTATION = 3

    /** OPEN-OC17 (wiki, fact-tagged): the Augmenter's +20% melee. */
    private const val AUGMENT = 20
    private const val BASIC_GAP = 3
    private const val SLASH_AT = 6
    private const val TRIPLE_LENGTH = 10
    private const val HYBRID_LENGTH = 7
    private const val PRAYED_MAX = 7
    private const val BASIC_MIN = 12
    private const val BASIC_MAX = 19
    private const val SLASH_MIN = 25
    private const val SLASH_MAX = 28
    private const val VOID_SHOT = 20
    private const val VINE_GUARD = 8
    private const val UNTETHER_GUARD = 8
    private const val PRAYER_CHECK = 6
    private const val SPIN_LENGTH = 16
    private const val SPIN_MIN = 15
    private const val SPIN_MAX = 27
    private const val INSOLENCE_SLAMS = 3
    private const val INSOLENCE_LEAD = 2
    private const val INSOLENCE_GAP = 5
    private const val INSOLENCE_MIN = 39
    private const val INSOLENCE_MAX = 41
    private const val POOL_LAND = 4
    private const val POOL_MIN = 23
    private const val POOL_MAX = 29
    private const val ERUPT_OUT = 29
    private const val ERUPT_IN = 30
    private const val OUT_MIN = 35
    private const val OUT_MAX = 42
    private const val IN_MIN = 34
    private const val IN_MAX = 41
    private const val IN_AGAIN_MIN = 18
    private const val IN_AGAIN_MAX = 21
    private const val UNTETHER_LENGTH = 33
    private const val BATCHES = 2

    /** RuneLite (LU): the first void 15 ticks into P3, then 18 after each move's line. */
    private const val FIRST_BOMBS = 15
    private const val BOMBS_AFTER_MOVE = 3
    private const val VOID_MIN_DROP = 3
    private const val VOID_STOP = 2
    private const val VOID_FUSE = 2
    private const val BOMB_MIN = 6
    private const val BOMB_MAX = 11
    private const val PATCH_EVERY = 2
    private const val PATCH_MIN = 6
    private const val PATCH_MAX = 7
    private const val VINE_TICKS = 8
    private const val VINE_DAMAGE = 50
    private const val RIFT_SPREAD = 5
    private const val RIFT_HP = 44
    private const val RUN_COUNTDOWN = 15
    private const val DESTRUCTION_RADIUS = 3
    private const val DESTRUCTION_DAMAGE = 50
}

/**
 * The P3/P4 rifts (16678-16680) and the Void Vine (16681). Capture: a healing rift heals him 12 or 14 every 4 ticks
 * unless a raider stands on the line between them; its killer is healed 22; any rift not killed detonates 30 ticks after
 * it appeared for 39..41 at every raider. OPEN-OC17 (wiki, fact-tagged): the Augmenter gives him +20% melee while it
 * stands; the Void Rift fires a void shot (6..11) at a raider every 4 ticks.
 */
internal object TfaOcellusRift : Boss(
    Npcs.TFA_OCELLUS_P3_BOMBPORTAL_BASIC,
    Npcs.TFA_OCELLUS_P3_BOMBPORTAL_HEAL,
    Npcs.TFA_OCELLUS_P3_BOMBPORTAL_BUFF,
) {
    /** Its own script moves and targets it: a hit does not make it chase. */
    override fun retaliates(npc: NpcRef): Boolean = false

    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0

        fun beat() {
            npc.queueHit(BEAT, strong = false) {
                if (!npc.isInWorld || npc.isDying) return@queueHit
                val owner = npc[Bosses.OWNER] ?: return@queueHit
                pulse(npc, owner)
                beat()
            }
        }
        beat()
        // Capture: a rift not killed detonates 30 ticks after it appeared.
        npc.queueHit(DETONATE, strong = false) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            val owner = npc[Bosses.OWNER] ?: return@queueHit
            for (p in TfaKit.arena(owner)) CombatSystem.applyHit(p, owner, BossCombat.roll(npc, DET_MIN, DET_MAX), HitStyle.TYPELESS)
            npc.despawn()
        }
    }

    private fun pulse(npc: NpcRef, owner: NpcRef) {
        when (npc.type) {
            Npcs.TFA_OCELLUS_P3_BOMBPORTAL_HEAL -> {
                val line = TfaKit.line(npc.coord, owner.coord)
                if (TfaKit.arena(owner).none { TfaKit.on(it, line) }) {
                    npc.spotAnim(SpotAnims.VFX_OCELLUS_VOIDFLARE_HEALING_PROJANIM)
                    owner.hitpoints = minOf(owner.maxHitpoints, owner.hitpoints + if (npc.random(2) == 0) HEAL_LOW else HEAL_HIGH)
                }
            }

            Npcs.TFA_OCELLUS_P3_BOMBPORTAL_BASIC -> {
                val players = TfaKit.arena(owner)
                players.getOrNull(npc.random(maxOf(1, players.size)))?.let {
                    CombatSystem.queueHit(it, npc, BossCombat.roll(npc, SHOT_MIN, SHOT_MAX), HitStyle.MAGIC, 1)
                }
            }

            else -> {
                npc.spotAnim(SpotAnims.VFX_HUMAN_OCELLUS_VOIDFLARE_BUFF)
            }
        }
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        // Rifts act on their own beat.
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        if (npc.type == Npcs.TFA_OCELLUS_P3_BOMBPORTAL_HEAL &&
            killer is PlayerRef
        ) {
            killer.hitpoints = minOf(killer.maxHitpoints, killer.hitpoints + KILLER_HEAL)
        }
    }

    /** OPEN-OC17: whether an Augmenter stands beside [owner]. */
    fun augmenting(owner: NpcRef): Boolean = Bosses.minions(owner, Npcs.TFA_OCELLUS_P3_BOMBPORTAL_BUFF).isNotEmpty()

    private const val BEAT = 4
    private const val DETONATE = 30
    private const val DET_MIN = 39
    private const val DET_MAX = 41
    private const val HEAL_LOW = 12
    private const val HEAL_HIGH = 14
    private const val KILLER_HEAL = 22
    private const val SHOT_MIN = 6
    private const val SHOT_MAX = 11
}

/** The Void Vine (16681): it holds its raider; killing it frees them (OC3). */
internal object TfaOcellusVine : Boss(Npcs.TFA_OCELLUS_P3_VOID_VINE) {
    /** Its own script moves and targets it: a hit does not make it chase. */
    override fun retaliates(npc: NpcRef): Boolean = false

    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        // The vine never attacks; Ocellus's script deals its damage.
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        for (p in npc.playersWithin(1)) if (p.coord == npc.coord) p.frozenUntil = p.tick
    }
}
