package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.RaidRoomFrame
import tp.plugin.SharedAttrs
import tp.plugin.Stats
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * The Great Olm (docs/mechanics/raids/cox/olm.md OL1-OL21; RuneLite: head attack every 4 ticks, burn 41 ticks, acid 23,
 * swap 10). The head runs the fight; the claws are its parts.
 * - OL1-OL4: three standard phases plus one per 8 raiders, each with one power (acid, flame, crystal) in a drawn order and
 *   all three in the last standard phase, then the head phase; Olm rises on the west or east wall and switches sides at
 *   every change, with falling crystals in between.
 * - OL5-OL9: the 12-step rotation (empty, standard, crystal burst, standard, empty, standard, lightning, standard, empty,
 *   standard, teleport, standard); a standard attack is a basic magic / ranged hit on everyone in the zone Olm faces (1/5
 *   style swap), a sphere, or (1/3; 1/2 in the head phase) a power attack; an empty zone turns it and skips the attack,
 *   which the next step catches up (specials never).
 * - OL16-OL19: 66% mitigation off the part's weakness; the head heals all damage before the head phase; the left claw
 *   clenches on a heavy hit (no specials for 50 ticks), turns damage into healing in the last standard phase, and a
 *   disabled claw comes back after 50 ticks there unless both are down. A phase ends when both claws are down.
 */
@Suppress("LargeClass")
internal object CoxOlm : Boss(Npcs.OLM_HEAD_SPAWNING, Npcs.OLM_HEAD) {
    enum class Power { ACID, FLAME, CRYSTAL }

    private enum class Step { EMPTY, STANDARD, BURST, LIGHTNING, TELEPORT }

    /** OL8 (Strat order). */
    private val ROTATION =
        listOf(
            Step.EMPTY,
            Step.STANDARD,
            Step.BURST,
            Step.STANDARD,
            Step.EMPTY,
            Step.STANDARD,
            Step.LIGHTNING,
            Step.STANDARD,
            Step.EMPTY,
            Step.STANDARD,
            Step.TELEPORT,
            Step.STANDARD,
        )

    class State(val frame: RaidRoomFrame, val raiders: Int) {
        var phase = 0
        val standardPhases = STANDARD_PHASES + raiders / EXTRA_PHASE_EVERY
        val powers = ArrayList<Power>()
        var west = true
        var step = 0
        var standards = 0
        var catchUp = false
        var magic = true
        var facing = MIDDLE
        var rising = 0
        var between = 0
        var crystalsLeft = 0
        var clenchedUntil = 0L
        var healingAttacks = 0
        var attacksSinceTeleport = -1
        var siphonCooldown = 0
        var finalCrystalAt = 0L
        var left: NpcRef? = null
        var right: NpcRef? = null
        val disabledAt = HashMap<Int, Long>()
        val burning = HashMap<PlayerRef, Long>()

        val finalPhase: Boolean get() = phase >= standardPhases
        val lastStandard: Boolean get() = phase == standardPhases - 1
    }

    private val STATE = AttrKey<State>("cox_olm")

    /** On a claw: the head that runs it. */
    private val HEAD = AttrKey<NpcRef>("cox_olm_head")

    fun state(head: NpcRef): State? = head[STATE]

    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
        if (npc.type != Npcs.OLM_HEAD_SPAWNING || npc[STATE] != null) return
        // raids-cox spawns the head and both claws together and tags them after the spawn: start a tick later.
        npc.queueHit(1, strong = false) {
            if (npc[STATE] != null || !npc.isInWorld) return@queueHit
            val frame = CoxKit.frame(npc) ?: return@queueHit
            val claws = CoxKit.roomNpcs(npc, setOf(Npcs.OLM_HAND_LEFT_SPAWNING, Npcs.OLM_HAND_RIGHT_SPAWNING))
            val left = claws.firstOrNull { it.type == Npcs.OLM_HAND_LEFT_SPAWNING } ?: return@queueHit
            val right = claws.firstOrNull { it.type == Npcs.OLM_HAND_RIGHT_SPAWNING } ?: return@queueHit
            begin(npc, left, right, frame, npc[SharedAttrs.RAID_SCALE] ?: 1)
        }
    }

    /**
     * Starts the fight (raids-cox, when the first raider passes the barrier): [frame] is the Olm square; the claws are
     * spawned by the room with the head and handed over here.
     */
    fun begin(head: NpcRef, left: NpcRef, right: NpcRef, frame: RaidRoomFrame, raiders: Int) {
        val s = State(frame, raiders)
        head[STATE] = s
        s.left = left
        s.right = right
        left[HEAD] = head
        right[HEAD] = head
        // OL2 / OPEN-OL4: the first side and the power order are drawn.
        s.west = head.random(2) == 0
        val order = Power.entries.toMutableList()
        for (i in order.indices.reversed()) {
            val j = head.random(i + 1)
            order[i] = order[j].also { order[j] = order[i] }
        }
        for (p in 0 until s.standardPhases) s.powers += order[p % order.size]
        startPhase(head, s)
        CoxKit.loop(head) { tick(head, s) }
    }

    override fun retaliates(npc: NpcRef): Boolean = false

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        npc.setAttackClock(npc.tick + IDLE)
    }

    /** OL16: the head takes 66% less from non-ranged and heals everything before the head phase. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = npc[STATE] ?: return 0
        if (!s.finalPhase || s.rising > 0) return 0
        return if (style == HitStyle.RANGED) damage else damage * KEEP / PERCENT
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        val s = npc[STATE] ?: return
        npc.anim(Seqs.OLM_HEAD_DEATH_FRONT)
        setLocs(npc, s, alive = false)
    }

    // ---- phases

    private fun startPhase(head: NpcRef, s: State) {
        s.step = 0
        s.standards = 0
        s.catchUp = false
        s.healingAttacks = 0
        s.attacksSinceTeleport = -1
        s.disabledAt.clear()
        s.rising = RISE_TICKS
        val headAt = place(s, HEAD_W, HEAD_E)
        head.teleport(headAt)
        head.transform(Npcs.OLM_HEAD_SPAWNING, restoreStats = true)
        head[SharedAttrs.RAID_SCALER]?.invoke(head)
        head.anim(if (s.finalPhase) Seqs.OLM_HEAD_SPAWN_ENRAGED else Seqs.OLM_HEAD_SPAWN)
        if (!s.finalPhase) {
            for ((hand, spawning) in listOf(s.left to Npcs.OLM_HAND_LEFT_SPAWNING, s.right to Npcs.OLM_HAND_RIGHT_SPAWNING)) {
                hand ?: continue
                hand.teleport(if (hand == s.left) place(s, LEFT_W, LEFT_E) else place(s, RIGHT_W, RIGHT_E))
                // Data: each part's stats reset when a new phase starts.
                hand.transform(spawning, restoreStats = true)
                hand[SharedAttrs.RAID_SCALER]?.invoke(hand)
                hand[SharedAttrs.RAID_NO_POINTS] = null
                hand.anim(if (hand == s.left) Seqs.OLM_HAND_LEFT_SPAWN else Seqs.OLM_HAND_RIGHT_SPAWN)
            }
            val power = if (s.lastStandard) null else s.powers[s.phase]
            val words = mapOf(Power.ACID to "acid", Power.FLAME to "flame", Power.CRYSTAL to "crystal")
            for (p in CoxKit.raiders(head)) {
                if (power != null) p.message("The Great Olm rises with the power of ${words.getValue(power)}.")
            }
        } else {
            s.left?.despawn()
            s.right?.despawn()
            for (p in CoxKit.raiders(head)) p.message(FINAL_STAND)
        }
        setLocs(head, s, alive = true)
    }

    /** The visible parts are locs swapped into the cave placeholders of the side Olm is on (client-contract 6.6). */
    private fun setLocs(head: NpcRef, s: State, alive: Boolean) {
        val parts =
            listOf(
                Triple(place(s, HEAD_W, HEAD_E), Locs.OLM_HEAD, Locs.OLM_HEAD_CAVE),
                Triple(place(s, LEFT_W, LEFT_E), Locs.OLM_HAND_LEFT, Locs.OLM_HAND_LEFT_CAVE),
                Triple(place(s, RIGHT_W, RIGHT_E), Locs.OLM_HAND_RIGHT, Locs.OLM_HAND_RIGHT_CAVE),
            )
        val angle = if (s.west) WEST_ANGLE else EAST_ANGLE
        for ((k, part) in parts.withIndex()) {
            val (at, live, cave) = part
            val show = alive && (k == 0 || !s.finalPhase)
            head.addLoc(if (show) live else cave, at, CENTREPIECE, angle)
        }
    }

    private fun place(s: State, west: Pair<Int, Int>, east: Pair<Int, Int>): CoordGrid {
        val (x, z) = if (s.west) west else east
        return s.frame.tile(x, z)
    }

    private fun tick(head: NpcRef, s: State) {
        if (s.rising > 0) {
            if (--s.rising == 0) rise(head, s)
            return
        }
        if (s.between > 0) {
            transition(head, s)
            return
        }
        claws(head, s)
        if (s.finalPhase && head.tick >= s.finalCrystalAt) {
            // OL21: crystals fall one at a time on a random raider until Olm dies (OPEN-OL20: every 6 ticks).
            s.finalCrystalAt = head.tick + FINAL_CRYSTAL_EVERY
            CoxKit.pick(head, CoxKit.raiders(head))?.let { fallingCrystal(head, it.coord) }
        }
        if (head.tick % ATTACK_SPEED == 0L) stepRotation(head, s)
    }

    private fun rise(head: NpcRef, s: State) {
        head.transform(Npcs.OLM_HEAD, restoreStats = false)
        if (!s.finalPhase) {
            s.left?.transform(Npcs.OLM_HAND_LEFT, restoreStats = false)
            s.right?.transform(Npcs.OLM_HAND_RIGHT, restoreStats = false)
        }
    }

    /** OL18 / OL19: claw state each tick (recovery in the last standard phase, the clench). */
    private fun claws(head: NpcRef, s: State) {
        if (s.finalPhase) return
        val left = s.left ?: return
        val right = s.right ?: return
        val leftDown = left.type == Npcs.OLM_HAND_LEFT_DYING
        val rightDown = right.type == Npcs.OLM_HAND_RIGHT_DYING
        if (leftDown && rightDown) {
            endPhase(head, s)
            return
        }
        if (s.lastStandard) {
            for ((hand, live) in listOf(left to Npcs.OLM_HAND_LEFT, right to Npcs.OLM_HAND_RIGHT)) {
                val at = s.disabledAt[hand.index] ?: continue
                if (head.tick - at < RECOVER_TICKS) continue
                // OL19: Olm regains the claw with full health; damage on it no longer earns points.
                s.disabledAt.remove(hand.index)
                hand.transform(live, restoreStats = false)
                hand.hitpoints = hand.maxHitpoints
                hand[SharedAttrs.RAID_NO_POINTS] = true
            }
        }
        if (head.tick == s.clenchedUntil) left.anim(Seqs.OLM_HAND_LEFT_UNSTUNNED)
    }

    /** OL18: a claw at 0 HP is disabled (its dying form) instead of dying. */
    fun clawLethal(hand: NpcRef): Boolean {
        val head = hand[HEAD] ?: return false
        val s = head[STATE] ?: return false
        hand.hitpoints = 0
        val left = hand == s.left
        hand.anim(if (left) Seqs.OLM_HAND_LEFT_DEATH else Seqs.OLM_HAND_RIGHT_DEATH)
        hand.transform(if (left) Npcs.OLM_HAND_LEFT_DYING else Npcs.OLM_HAND_RIGHT_DYING, restoreStats = false)
        s.disabledAt[hand.index] = hand.tick
        // OL17: the clench mechanic ends for the phase once the mage claw is disabled.
        if (!left) s.clenchedUntil = 0
        return true
    }

    /** OL16 / OL17 / OL19: a claw's incoming damage. */
    fun clawIncoming(hand: NpcRef, damage: Int, style: HitStyle): Int {
        val head = hand[HEAD] ?: return 0
        val s = head[STATE] ?: return 0
        if (s.rising > 0 || hand.type == Npcs.OLM_HAND_LEFT_DYING || hand.type == Npcs.OLM_HAND_RIGHT_DYING) return 0
        val left = hand == s.left
        val strong = if (left) HitStyle.MELEE else HitStyle.MAGIC
        var dmg = if (style == strong) damage else damage * KEEP / PERCENT
        if (left) {
            if (hand.tick < s.clenchedUntil) return 0
            if (s.healingAttacks > 0) {
                // OL19: while the infinity symbol shows, damage heals the claw instead.
                hand.hitpoints = minOf(hand.maxHitpoints, hand.hitpoints + dmg)
                return 0
            }
            val rightUp = s.right?.type == Npcs.OLM_HAND_RIGHT
            // OL17: one hit of 1/20 of its health or more clenches it for 50 ticks (not in the last standard phase).
            if (!s.lastStandard && rightUp && dmg * CLENCH_FRACTION >= hand.maxHitpoints) {
                s.clenchedUntil = hand.tick + CLENCH_TICKS
                hand.anim(Seqs.OLM_HAND_LEFT_STUNNED)
                dmg = 0
            }
        }
        return dmg
    }

    private fun endPhase(head: NpcRef, s: State) {
        setLocs(head, s, alive = false)
        // OL20: Olm retreats; 7-8 targeted crystals fall before it rises on the other side.
        s.between = BETWEEN_TICKS
        s.crystalsLeft = TRANSITION_CRYSTALS_MIN + head.random(TRANSITION_CRYSTALS_MAX - TRANSITION_CRYSTALS_MIN + 1)
    }

    private fun transition(head: NpcRef, s: State) {
        s.between--
        if (s.crystalsLeft > 0 && s.between % TRANSITION_CRYSTAL_EVERY == 0) {
            s.crystalsLeft--
            CoxKit.pick(head, CoxKit.raiders(head))?.let { transitionCrystal(head, it.coord) }
        }
        if (s.between > 0) return
        s.phase++
        s.west = !s.west
        startPhase(head, s)
    }

    // ---- rotation

    private fun stepRotation(head: NpcRef, s: State) {
        val step = ROTATION[s.step]
        s.step = (s.step + 1) % ROTATION.size
        val caught = s.catchUp
        s.catchUp = false
        if (caught) standard(head, s, catchingUp = true)
        if (s.healingAttacks > 0) s.healingAttacks--
        if (s.attacksSinceTeleport >= 0) {
            s.attacksSinceTeleport++
            // OL19: the healing claw comes two attacks after the teleport and lasts two attacks.
            if (s.lastStandard && s.attacksSinceTeleport == HEAL_AFTER) {
                s.healingAttacks = HEAL_ATTACKS
                s.left?.anim(Seqs.OLM_HAND_LEFT_CAST_HEAL)
            }
        }
        if (s.siphonCooldown > 0) s.siphonCooldown--
        when (step) {
            Step.EMPTY -> Unit
            Step.STANDARD -> standard(head, s, catchingUp = false)
            else -> special(head, s, step)
        }
    }

    /** OL7 / OL8: an empty zone turns Olm to the busiest one and skips the attack (caught up next step). */
    private fun standard(head: NpcRef, s: State, catchingUp: Boolean) {
        val raiders = CoxKit.raiders(head)
        if (raiders.isEmpty()) return
        val counts = IntArray(ZONES)
        for (p in raiders) counts[zoneOf(s, p.coord)]++
        if (counts[s.facing] == 0) {
            s.facing = counts.indices.maxBy { counts[it] }
            if (!catchingUp) s.catchUp = true
            return
        }
        s.standards++
        val targets = raiders.filter { zoneOf(s, it.coord) == s.facing }
        val powerOdds = if (s.finalPhase) FINAL_POWER_ODDS else POWER_ODDS
        if (head.random(powerOdds) == 0 && power(head, s, raiders)) return
        if (head.random(SPHERE_ODDS) == 0) {
            sphere(head, CoxKit.pick(head, raiders) ?: return)
            return
        }
        basic(head, s, targets)
    }

    /** OL5: magic or ranged on everyone Olm faces; 1/5 style swap after each attack; prayer takes about 75-80% off. */
    private fun basic(head: NpcRef, s: State, targets: List<PlayerRef>) {
        val style = if (s.magic) CombatStyle.MAGIC else CombatStyle.RANGED
        val max = CoxKit.scaledMax(head, if (s.magic) NpcStat.MAGIC else NpcStat.RANGED, BASE_LEVEL, phaseMax(s))
        for (p in targets) {
            val delay = BossCombat.delay(head, p, style)
            BossCombat.projectile(head, p, if (s.magic) SpotAnims.OLM_FIREBREATH_TRAVEL else SpotAnims.OLM_GENERIC_RANGE_PROJ, delay)
            val hit = BossCombat.rolls(head, p, if (s.magic) AttackType.MAGIC else AttackType.RANGED)
            val raw = if (hit) BossCombat.roll(head, 0, max) else 0
            val dmg = if (p.protects(style)) raw * PRAYED_KEEP / PERCENT else raw
            CombatSystem.queueHit(p, head, dmg, CombatSystem.hitStyle(style), delay)
        }
        if (head.random(SWAP_ODDS) == 0) s.magic = !s.magic
    }

    /** Infobox head max: 27 (phases 1-2), 28 (the last standard phase), 29 (the head phase), at a maxed solo. */
    private fun phaseMax(s: State): Int =
        when {
            s.finalPhase -> MAX_FINAL
            s.lastStandard -> MAX_LAST
            else -> MAX_EARLY
        }

    /**
     * OL6: a sphere (red melee, green ranged, purple magic) at a raider: about half the raider's HP unless the matching
     * prayer is up when it lands; a protection prayer active at the launch is switched off and half the Prayer goes.
     */
    private fun sphere(head: NpcRef, p: PlayerRef) {
        val styles =
            listOf(
                CombatStyle.MELEE to SpotAnims.OLM_WEAK_MELEE_PROJ,
                CombatStyle.RANGED to SpotAnims.OLM_WEAK_RANGE_PROJ,
                CombatStyle.MAGIC to SpotAnims.OLM_WEAK_MAGE_PROJ,
            )
        val (style, gfx) = styles[head.random(styles.size)]
        if (CombatStyle.entries.any { p.protects(it) }) {
            p.deactivateAllPrayers()
            p.setLevel(Stats.PRAYER, p.level(Stats.PRAYER) / 2)
        }
        BossCombat.projectile(head, p, gfx, SPHERE_FLIGHT)
        head.queueHit(SPHERE_FLIGHT, strong = false) {
            if (!head.isInWorld || p.isDying) return@queueHit
            if (!p.protects(style)) BossCombat.areaHit(head, p, p.hitpoints / 2)
        }
    }

    // ---- powers (OL9-OL12)

    private fun power(head: NpcRef, s: State, raiders: List<PlayerRef>): Boolean {
        val options = if (s.lastStandard || s.finalPhase) Power.entries else listOf(s.powers[s.phase])
        val target = CoxKit.pick(head, raiders) ?: return false
        if (s.finalPhase && s.siphonCooldown == 0 && head.random(2) == 0) {
            lifeSiphon(head, s)
            return true
        }
        when (options[head.random(options.size)]) {
            Power.ACID -> if (head.random(2) == 0) acidSpray(head, s) else acidDrip(head, target)
            Power.FLAME -> if (head.random(2) == 0) deepBurn(head, s, target) else fireWall(head, s, target)
            Power.CRYSTAL -> if (head.random(2) == 0) crystalMark(head, target) else crystalBombs(head, s)
        }
        return true
    }

    /** OL10: acid pools over the chamber: 3-6 a tick and poison from 2 to anyone on one (OPEN-OL10: count, lifetime). */
    private fun acidSpray(head: NpcRef, s: State) {
        head.anim(Seqs.OLM_HEAD_ATTACK_ACID_FRONT)
        repeat(ACID_POOLS) { pool(head, randomArenaTile(head, s)) }
    }

    /** OL10: one raider drips acid for 23 ticks (RuneLite); every tile they stand on becomes a pool. */
    private fun acidDrip(head: NpcRef, p: PlayerRef) {
        p.message(ACID_DRIP)
        repeat(ACID_DRIP_TICKS) { k -> head.queueHit(k + 1, strong = false) { if (head.isInWorld && !p.isDying) pool(head, p.coord) } }
    }

    private fun pool(head: NpcRef, at: CoordGrid) {
        head.addLoc(Locs.OLM_ACID_POOL, at, CENTREPIECE, 0, ACID_POOL_TICKS)
        repeat(ACID_POOL_TICKS) { k ->
            head.queueHit(k + 1, strong = false) {
                if (!head.isInWorld) return@queueHit
                for (p in CoxKit.inArea(head, at, 0)) {
                    BossCombat.areaHit(head, p, BossCombat.roll(head, ACID_MIN, ACID_MAX))
                    Poison.poison(p, ACID_POISON)
                }
            }
        }
    }

    /**
     * OL11 Deep Burn: 6 hits of 5 that drain every combat stat by 2 (RuneLite: 41 ticks); next to a raider who is not
     * burning it spreads, and the host's burn starts over.
     */
    private fun deepBurn(head: NpcRef, s: State, p: PlayerRef) {
        BossCombat.projectile(head, p, SpotAnims.OLM_BURNWITHME_TRAVEL, BURN_FLIGHT)
        head.queueHit(BURN_FLIGHT, strong = false) { if (head.isInWorld) burn(head, s, p) }
    }

    private fun burn(head: NpcRef, s: State, p: PlayerRef) {
        if (p.isDying) return
        p.say(BURN_WITH_ME)
        val token = head.tick
        s.burning[p] = token
        p.spotAnim(SpotAnims.OLM_BURNWITHME_PL_SPOT)
        repeat(BURN_HITS) { k ->
            head.queueHit((k + 1) * BURN_EVERY, strong = false) {
                if (!head.isInWorld || p.isDying || s.burning[p] != token) return@queueHit
                BossCombat.areaHit(head, p, BURN_DAMAGE)
                for (stat in BURN_STATS) p.setLevel(stat, maxOf(0, p.level(stat) - BURN_DRAIN))
                for (other in CoxKit.raiders(head)) {
                    if (other == p || s.burning.containsKey(other)) continue
                    if (maxOf(kotlin.math.abs(other.coord.x - p.coord.x), kotlin.math.abs(other.coord.z - p.coord.z)) <= 1) {
                        other.say(BURN_WITH_YOU)
                        burn(head, s, other)
                        burn(head, s, p)
                    }
                }
                if (k == BURN_HITS - 1) s.burning.remove(p)
            }
        }
    }

    /**
     * OL11 Fire Wall: two walls of fire either side of a raider across the chamber; 8-10 to anyone on a wall tile; after
     * about 8 ticks 50-65 to anyone still between them unless a segment was doused (raids-cox: Douse).
     */
    private fun fireWall(head: NpcRef, s: State, p: PlayerRef) {
        val z = p.coord.z
        val walls = ArrayList<NpcRef>()
        for (dz in intArrayOf(-WALL_GAP, WALL_GAP)) {
            for (t in arenaRow(s, z + dz)) {
                if (!head.isWalkable(t)) continue
                for (v in CoxKit.inArea(head, t, 0)) BossCombat.areaHit(head, v, BossCombat.roll(head, WALL_ON_MIN, WALL_ON_MAX))
                walls += Bosses.summon(head, Npcs.OLM_FIREWALL_NPC, t, huntRange = 0)
            }
        }
        if (walls.isEmpty()) return
        head.queueHit(WALL_TICKS, strong = false) {
            if (!head.isInWorld) return@queueHit
            val doused = walls.any { !it.isInWorld }
            for (w in walls) if (w.isInWorld) w.despawn()
            if (doused) return@queueHit
            for (v in CoxKit.raiders(head)) {
                if (v.coord.z in z - WALL_GAP + 1 until z + WALL_GAP) BossCombat.areaHit(head, v, BossCombat.roll(head, TRAP_MIN, TRAP_MAX))
            }
        }
    }

    /** OL12 Falling Crystals: a marked raider; 11 crystals fall on their tile (1 x 1, 15-20). */
    private fun crystalMark(head: NpcRef, p: PlayerRef) {
        p.message(CRYSTAL_TARGET)
        repeat(MARK_CRYSTALS) { k ->
            head.queueHit((k + 1) * MARK_EVERY, strong = false) {
                if (!head.isInWorld || p.isDying) return@queueHit
                val at = p.coord
                head.spotAnimAt(at, SpotAnims.OLM_CRYSTALROCK_FALLING)
                head.queueHit(FALL_DELAY, strong = false) {
                    for (v in CoxKit.inArea(head, at, 0)) BossCombat.areaHit(head, v, BossCombat.roll(head, MARK_MIN, MARK_MAX))
                }
            }
        }
    }

    /** OL12 Crystal Bombs: two (small groups) or three; 60 on top, 15 less per tile out to 15 (radius 4). */
    private fun crystalBombs(head: NpcRef, s: State) {
        val n = if (s.raiders <= SMALL_GROUP) 2 else MAX_BOMBS
        repeat(n) {
            val at = randomArenaTile(head, s)
            head.addLoc(Locs.OLM_CRYSTAL_BOMB, at, CENTREPIECE, 0, BOMB_TICKS)
            head.queueHit(BOMB_TICKS, strong = false) {
                if (!head.isInWorld) return@queueHit
                head.spotAnimAt(at, SpotAnims.OLM_CRYSTAL_BOMB_EXPLODE)
                for (v in CoxKit.inArea(head, at, BOMB_RADIUS)) {
                    val d = maxOf(kotlin.math.abs(v.coord.x - at.x), kotlin.math.abs(v.coord.z - at.z))
                    BossCombat.areaHit(head, v, maxOf(BOMB_RIM, BOMB_TOP - BOMB_STEP * d))
                }
            }
        }
    }

    /**
     * OL21 Life Siphon: two blue pools on random tiles; after 10 ticks every raider not on one takes up to 18 and Olm heals
     * five times the total (cooldown 10 attacks).
     */
    private fun lifeSiphon(head: NpcRef, s: State) {
        s.siphonCooldown = SIPHON_COOLDOWN
        val pools = List(2) { randomArenaTile(head, s) }
        for (t in pools) head.spotAnimAt(t, SpotAnims.OLM_HEALME_SPOTANIM)
        head.queueHit(SIPHON_TICKS, strong = false) {
            if (!head.isInWorld) return@queueHit
            var total = 0
            for (p in CoxKit.raiders(head)) {
                if (p.coord in pools) continue
                val dmg = BossCombat.roll(head, 1, SIPHON_MAX)
                total += dmg
                BossCombat.areaHit(head, p, dmg)
            }
            head.hitpoints = minOf(head.maxHitpoints, head.hitpoints + total * SIPHON_HEAL)
        }
    }

    // ---- specials (OL13-OL15)

    private fun special(head: NpcRef, s: State, step: Step) {
        // Specials need two standard attacks first, an unclenched left claw, and are gone in the head phase.
        if (s.finalPhase || s.standards < 2 || head.tick < s.clenchedUntil) return
        val left = s.left
        if (left == null || left.type != Npcs.OLM_HAND_LEFT) return
        val raiders = CoxKit.raiders(head)
        when (step) {
            Step.BURST -> {
                left.anim(Seqs.OLM_HAND_LEFT_CAST_STUN)
                crystalBurst(head, raiders)
            }

            Step.LIGHTNING -> {
                left.anim(Seqs.OLM_HAND_LEFT_CAST_EARTHSHOCK)
                lightning(head, s, raiders)
            }

            else -> {
                left.anim(Seqs.OLM_HAND_LEFT_CAST_EARTHQUAKE)
                teleport(head, raiders)
                s.attacksSinceTeleport = 0
            }
        }
    }

    /** OL13: a seedling under every raider bursts a few ticks later: heavy damage and a shove (OPEN-OL14). */
    private fun crystalBurst(head: NpcRef, raiders: List<PlayerRef>) {
        for (p in raiders) {
            val at = p.coord
            head.addLoc(Locs.OLM_CRYSTAL_ATTACK_SMALL, at, CENTREPIECE, 0, BURST_TICKS)
            head.queueHit(BURST_TICKS, strong = false) {
                if (!head.isInWorld) return@queueHit
                head.spotAnimAt(at, SpotAnims.OLM_CRYSTALWAVE_SHATTER)
                for (v in CoxKit.inArea(head, at, 0)) {
                    BossCombat.areaHit(head, v, BossCombat.roll(head, BURST_MIN, BURST_MAX))
                    v.teleport(BossCombat.randomTile(head, at, 1))
                }
            }
        }
    }

    /** OL14: bolts down the north and / or south of the chamber: overheads off, moderate damage, bound (OPEN-OL15). */
    private fun lightning(head: NpcRef, s: State, raiders: List<PlayerRef>) {
        val (minZ, maxZ) = arenaZ(s)
        val north = head.random(2) == 0
        val both = head.random(2) == 0
        val northLane = north || both
        val southLane = !north || both
        for (p in raiders) {
            val struck = (northLane && p.coord.z > maxZ - LANE) || (southLane && p.coord.z < minZ + LANE)
            if (!struck) continue
            p.spotAnim(SpotAnims.OLM_SHOCKWAVE)
            p.disableProtection(LIGHTNING_BIND)
            p.frozenUntil = maxOf(p.frozenUntil, p.tick + LIGHTNING_BIND)
            BossCombat.areaHit(head, p, BossCombat.roll(head, LIGHTNING_MIN, LIGHTNING_MAX))
        }
    }

    /**
     * OL15: up to six raiders are paired and swapped after 10 ticks (RuneLite), 5 per tile apart up to 50; a lone raider
     * goes to a random tile within 10 unless standing on it.
     */
    private fun teleport(head: NpcRef, raiders: List<PlayerRef>) {
        val chosen = raiders.take(MAX_SWAP).let { if (it.size % 2 == 1 && it.size > 1) it.dropLast(1) else it }
        if (chosen.size == 1) {
            val p = chosen[0]
            val to = BossCombat.randomTile(head, p.coord, SOLO_SWAP_RANGE)
            head.spotAnimAt(to, SpotAnims.OLM_PLAYERSWAP_0)
            head.queueHit(SWAP_TICKS, strong = false) {
                if (!head.isInWorld || p.isDying) return@queueHit
                if (p.coord != to) swap(head, p, to)
            }
            return
        }
        for (k in chosen.indices step 2) {
            val a = chosen[k]
            val b = chosen[k + 1]
            for (who in listOf(a, b)) {
                who.spotAnim(SWAP_GFX[(k / 2) % SWAP_GFX.size])
                who.message("You have been paired with ${(if (who == a) b else a).username}! The magical power will enact soon...")
            }
            head.queueHit(SWAP_TICKS, strong = false) {
                if (!head.isInWorld) return@queueHit
                val at = a.coord
                swap(head, a, b.coord)
                swap(head, b, at)
            }
        }
    }

    private fun swap(head: NpcRef, p: PlayerRef, to: CoordGrid) {
        val d = maxOf(kotlin.math.abs(p.coord.x - to.x), kotlin.math.abs(p.coord.z - to.z))
        p.teleport(to)
        if (d > 0) BossCombat.areaHit(head, p, minOf(SWAP_MAX, SWAP_STEP * d))
    }

    // ---- falling crystals

    /** OL20: 3 x 3: 20-25 under it, 12-16 beside it (Strat). */
    private fun transitionCrystal(head: NpcRef, at: CoordGrid) {
        head.spotAnimAt(at, SpotAnims.OLM_CRYSTALROCK_FALLING)
        head.queueHit(FALL_DELAY, strong = false) {
            for (v in CoxKit.inArea(head, at)) {
                val under = v.coord.x == at.x && v.coord.z == at.z
                BossCombat.areaHit(
                    head,
                    v,
                    if (under) BossCombat.roll(head, UNDER_MIN, UNDER_MAX) else BossCombat.roll(head, BESIDE_MIN, BESIDE_MAX),
                )
            }
        }
    }

    private fun fallingCrystal(head: NpcRef, at: CoordGrid) = transitionCrystal(head, at)

    // ---- arena geometry (template-local in the Olm square, client-contract 6.6)

    private fun arenaZ(s: State): Pair<Int, Int> = s.frame.tile(0, ARENA_MIN_Z).z to s.frame.tile(0, ARENA_MAX_Z).z

    private fun zoneOf(s: State, c: CoordGrid): Int {
        val (lz, _) = s.frame.local(c).let { it.second to it.first }
        val third =
            when {
                lz < ARENA_MIN_Z + ZONE_SPAN -> 0
                lz < ARENA_MIN_Z + 2 * ZONE_SPAN -> MIDDLE
                else -> 2
            }
        // Olm on the west wall looks east: its left is the north; on the east wall, the south.
        return if (s.west) 2 - third else third
    }

    private fun arenaRow(s: State, z: Int): List<CoordGrid> =
        (ARENA_MIN_X..ARENA_MAX_X).map {
            CoordGrid(s.frame.tile(it, 0).x, z, s.frame.origin.level)
        }

    private fun randomArenaTile(head: NpcRef, s: State): CoordGrid {
        repeat(RANDOM_TRIES) {
            val t =
                s.frame.tile(
                    ARENA_MIN_X + head.random(ARENA_MAX_X - ARENA_MIN_X + 1),
                    ARENA_MIN_Z + head.random(ARENA_MAX_Z - ARENA_MIN_Z + 1),
                )
            if (head.isWalkable(t)) return t
        }
        return s.frame.tile((ARENA_MIN_X + ARENA_MAX_X) / 2, (ARENA_MIN_Z + ARENA_MAX_Z) / 2)
    }

    private val BURN_STATS = intArrayOf(Stats.ATTACK, Stats.STRENGTH, Stats.DEFENCE, Stats.RANGED, Stats.MAGIC)
    private val SWAP_GFX =
        intArrayOf(SpotAnims.OLM_PLAYERSWAP_0, SpotAnims.OLM_PLAYERSWAP_1, SpotAnims.OLM_PLAYERSWAP_2, SpotAnims.OLM_PLAYERSWAP_3)

    /** Client-contract 6.6, template-local in square (50, 89): head (3220 / 3238, 5738), claws west / east. */
    private val HEAD_W = 20 to 42
    private val HEAD_E = 38 to 42
    private val LEFT_W = 20 to 47
    private val LEFT_E = 38 to 37
    private val RIGHT_W = 20 to 37
    private val RIGHT_E = 38 to 47

    /** The floor between the caves (OPEN-OL7: the arena's exact extent and zones from the template's free tiles). */
    private const val ARENA_MIN_X = 25
    private const val ARENA_MAX_X = 37
    private const val ARENA_MIN_Z = 33
    private const val ARENA_MAX_Z = 52
    private const val ZONE_SPAN = 7
    private const val ZONES = 3
    private const val MIDDLE = 1
    private const val WEST_ANGLE = 3
    private const val EAST_ANGLE = 1
    private const val CENTREPIECE = 10
    private const val RANDOM_TRIES = 20
    private const val IDLE = 10

    /** OL1: three standard phases, one more per 8 raiders (OPEN-OL1). */
    private const val STANDARD_PHASES = 3
    private const val EXTRA_PHASE_EVERY = 8

    /** Infobox attack speed 4; OL5 1/5 swap; OL9 1/3 (head phase 1/2) power; OPEN-OL6 sphere odds. */
    private const val ATTACK_SPEED = 4
    private const val SWAP_ODDS = 5
    private const val POWER_ODDS = 3
    private const val FINAL_POWER_ODDS = 2
    private const val SPHERE_ODDS = 4
    private const val SPHERE_FLIGHT = 4
    private const val PRAYED_KEEP = 22
    private const val KEEP = 34
    private const val PERCENT = 100
    private const val BASE_LEVEL = 250
    private const val MAX_EARLY = 27
    private const val MAX_LAST = 28
    private const val MAX_FINAL = 29

    /** OL17: a hit of 1/20 of its health clenches the left claw for about 30 s. */
    private const val CLENCH_FRACTION = 20
    private const val CLENCH_TICKS = 50L
    private const val RECOVER_TICKS = 50L
    private const val HEAL_AFTER = 2
    private const val HEAL_ATTACKS = 2

    /** OPEN-OL4 / OL20: the rise time, the time underground and the 7-8 crystals between phases. */
    private const val RISE_TICKS = 5
    private const val BETWEEN_TICKS = 20
    private const val TRANSITION_CRYSTALS_MIN = 7
    private const val TRANSITION_CRYSTALS_MAX = 8
    private const val TRANSITION_CRYSTAL_EVERY = 2
    private const val FALL_DELAY = 3
    private const val UNDER_MIN = 20
    private const val UNDER_MAX = 25
    private const val BESIDE_MIN = 12
    private const val BESIDE_MAX = 16
    private const val FINAL_CRYSTAL_EVERY = 6L

    /** OL10: 3-6 a tick, poison from 2; RuneLite acid 23 ticks; OPEN-OL10 pool count and lifetime. */
    private const val ACID_POOLS = 6
    private const val ACID_POOL_TICKS = 20
    private const val ACID_DRIP_TICKS = 23
    private const val ACID_MIN = 3
    private const val ACID_MAX = 6
    private const val ACID_POISON = 2

    /** OL11: six hits of 5, -2 to every combat stat; RuneLite 41 ticks (about 7 apart). */
    private const val BURN_HITS = 6
    private const val BURN_EVERY = 7
    private const val BURN_DAMAGE = 5
    private const val BURN_DRAIN = 2
    private const val BURN_FLIGHT = 3

    /** OL11 fire wall: 8-10 on a wall tile, 50-65 between them after about 8 ticks. */
    private const val WALL_GAP = 2
    private const val WALL_ON_MIN = 8
    private const val WALL_ON_MAX = 10
    private const val WALL_TICKS = 8
    private const val TRAP_MIN = 50
    private const val TRAP_MAX = 65

    /** OL12: 11 crystals of 15-20 (Page); bombs 60 on top, 15 per tile less, radius 4; OPEN-OL13 their delay. */
    private const val MARK_CRYSTALS = 11
    private const val MARK_EVERY = 1
    private const val MARK_MIN = 15
    private const val MARK_MAX = 20
    private const val SMALL_GROUP = 3
    private const val MAX_BOMBS = 3
    private const val BOMB_TICKS = 8
    private const val BOMB_RADIUS = 4
    private const val BOMB_TOP = 60
    private const val BOMB_STEP = 15
    private const val BOMB_RIM = 15

    /** OL21 Life Siphon: 10 ticks, up to 18, five times the total healed, 10-attack cooldown. */
    private const val SIPHON_TICKS = 10
    private const val SIPHON_MAX = 18
    private const val SIPHON_HEAL = 5
    private const val SIPHON_COOLDOWN = 10

    /** OPEN-OL14 / OPEN-OL15: burst and lightning damage, delay, lanes, bind. */
    private const val BURST_TICKS = 3
    private const val BURST_MIN = 20
    private const val BURST_MAX = 40
    private const val LANE = 2
    private const val LIGHTNING_MIN = 10
    private const val LIGHTNING_MAX = 20
    private const val LIGHTNING_BIND = 5

    /** OL15: up to six raiders; 5 per tile apart, 50 at most; a solo portal within 10; RuneLite 10 ticks. */
    private const val MAX_SWAP = 6
    private const val SWAP_STEP = 5
    private const val SWAP_MAX = 50
    private const val SOLO_SWAP_RANGE = 10
    private const val SWAP_TICKS = 10

    /** RuneLite (XK / SP) message constants. */
    private const val FINAL_STAND = "The Great Olm is giving its all. This is its final stand."
    private const val ACID_DRIP = "The Great Olm has smothered you in acid. It starts to drip off slowly."
    private const val CRYSTAL_TARGET = "The Great Olm has chosen you as its target - watch out!"
    private const val BURN_WITH_ME = "Burn with me!"
    private const val BURN_WITH_YOU = "I will burn with you."
}

/** The Olm's claws (olm.md OL16-OL19): their damage and disabling go through the head's fight. */
internal object CoxOlmClaw : Boss(Npcs.OLM_HAND_LEFT_SPAWNING, Npcs.OLM_HAND_LEFT, Npcs.OLM_HAND_RIGHT_SPAWNING, Npcs.OLM_HAND_RIGHT) {
    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
    }

    override fun retaliates(npc: NpcRef): Boolean = false

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        npc.setAttackClock(npc.tick + IDLE)
    }

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int = CoxOlm.clawIncoming(npc, damage, style)

    override fun onLethal(npc: NpcRef): Boolean = CoxOlm.clawLethal(npc)

    private const val IDLE = 10
}
