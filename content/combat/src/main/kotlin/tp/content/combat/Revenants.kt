package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Revenants (bosses.md RV1–RV5): they attack with the style the player is weakest to (magic by default), their magic
 * freezes and hits others on the target's tile, they heal 20 below half health (15-tick cooldown, 8–25 heals per
 * life), and a charged bracelet of ethereum takes 75 % off each attack and makes them tolerant.
 */
internal object Revenants : Boss(
    Npcs.WILD_CAVE_IMP,
    Npcs.WILD_CAVE_GOBLIN,
    Npcs.WILD_CAVE_PYREFIEND,
    Npcs.WILD_CAVE_HOBGOBLIN,
    Npcs.WILD_CAVE_CYCLOPS,
    Npcs.WILD_CAVE_HELLHOUND,
    Npcs.WILD_CAVE_DEMON,
    Npcs.WILD_CAVE_ORK,
    Npcs.WILD_CAVE_DARKBEAST,
    Npcs.WILD_CAVE_KNIGHT,
    Npcs.WILD_CAVE_DRAGON,
) {
    private val HEALS_LEFT = AttrKey<Int>("revenant_heals")
    private val HEAL_READY = AttrKey<Long>("revenant_heal_ready")

    override val ignoresTolerance: Boolean = false

    override fun spawned(npc: NpcRef) {
        // RV3: "a total of 8-25 times ... with equal chance of each integer".
        npc[HEALS_LEFT] = MIN_HEALS + npc.random(MAX_HEALS - MIN_HEALS + 1)
    }

    /** RV4: "Revenants are also made tolerant when a charged bracelet is worn." */
    override fun hunts(npc: NpcRef, player: PlayerRef): Boolean = !Ethereum.charged(player)

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        heal(npc)
        val type = choose(npc, victim, distance)
        CombatSystem.announceAttack(npc, victim, type.style)
        val def = NpcCombatTable.of(npc.type)
        val max = def?.maxFor(type.style) ?: 0
        val delay = BossCombat.delay(npc, victim, type.style)
        if (type == AttackType.MAGIC) BossCombat.projectile(npc, victim, SpotAnims.REVENANT_MAGIC_TRAVEL, delay)
        val hit = BossCombat.rolls(npc, victim, type)
        val raw = if (hit) BossCombat.roll(npc, 1, max) else 0
        val damage = Ethereum.absorb(victim, BossCombat.afterPrayer(victim, type.style, raw, PrayerRule.BLOCKS))
        CombatSystem.queueHit(victim, npc, damage, CombatSystem.hitStyle(type.style), delay) {
            if (type != AttackType.MAGIC || !hit) return@queueHit
            victim.spotAnim(SpotAnims.REVENANT_MAGIC_IMPACT)
            // RV2: "can momentarily freeze the player ... hits up to nine other players on the same tile".
            Freezes.freeze(victim, npc, FREEZE_TICKS)
            for (other in BossCombat.playersOn(npc, victim.coord).filter { it != victim }.take(MAX_SPLASH)) {
                CombatSystem.applyHit(
                    other,
                    npc,
                    Ethereum.absorb(other, BossCombat.afterPrayer(other, CombatStyle.MAGIC, raw, PrayerRule.BLOCKS)),
                    HitStyle.MAGIC,
                )
                Freezes.freeze(other, npc, FREEZE_TICKS)
            }
        }
    }

    /** RV3: "If they're attacking a player, and are on <50% health, and aren't subject to their cool-down, they'll try healing by 20." */
    private fun heal(npc: NpcRef) {
        val left = npc[HEALS_LEFT] ?: 0
        val now = npc.tick
        if (left <= 0 || npc.hitpoints * 2 >= npc.maxHitpoints || (npc[HEAL_READY] ?: 0L) > now) return
        npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + HEAL)
        npc[HEALS_LEFT] = left - 1
        npc[HEAL_READY] = now + HEAL_COOLDOWN
    }

    /**
     * RV1: "By default, all revenants attack with Magic, but can quickly adapt based on the player's defensive bonuses
     * and prayers". OPEN-RV1: the unprotected style with the lowest defence bonus; melee only in reach.
     */
    private fun choose(npc: NpcRef, victim: EntityRef, distance: Boolean): AttackType {
        if (victim !is PlayerRef) return AttackType.MAGIC
        val melee = NpcCombatTable.of(npc.type)?.meleeTypes?.firstOrNull() ?: AttackType.CRUSH
        val options =
            buildList {
                add(AttackType.MAGIC)
                add(AttackType.RANGED)
                if (!distance) add(melee)
            }.filter { !victim.protects(it.style) }
        if (options.isEmpty()) return AttackType.MAGIC
        return options.minBy { victim.bonus(it.defenceParam) }
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Maledictus.onRevenantKilled(npc)
    }

    private const val MIN_HEALS = 8
    private const val MAX_HEALS = 25
    private const val HEAL = 20
    private const val HEAL_COOLDOWN = 15

    /** OPEN-RV2: "freeze the player for several seconds"; 8 ticks. */
    private const val FREEZE_TICKS = 8
    private const val MAX_SPLASH = 9
}

/** The bracelet of ethereum (RV4): charges in `charges_bracelet_of_ethereum_quantity`. */
internal object Ethereum {
    fun charged(p: PlayerRef): Boolean =
        p.worn(Wear.HANDS) == Objs.WILD_CAVE_BRACELET_CHARGED && p.varbit(Varbits.CHARGES_BRACELET_OF_ETHEREUM_QUANTITY) > 0

    /** "its damage is reduced by 75% by a charged bracelet, with each attack consuming one charge". */
    fun absorb(victim: EntityRef, damage: Int): Int {
        if (victim !is PlayerRef || !charged(victim)) return damage
        val left = victim.varbit(Varbits.CHARGES_BRACELET_OF_ETHEREUM_QUANTITY) - 1
        victim.setVarbit(Varbits.CHARGES_BRACELET_OF_ETHEREUM_QUANTITY, maxOf(0, left))
        if (left <= 0) {
            victim.transaction("ethereum empty") {
                replaceAt(Invs.WORN, Wear.HANDS, Objs.WILD_CAVE_BRACELET_CHARGED, Objs.WILD_CAVE_BRACELET_UNCHARGED)
            }
        }
        return damage * KEPT_PERCENT / PERCENT
    }

    private const val KEPT_PERCENT = 25
    private const val PERCENT = 100
}

/**
 * The revenant maledictus (bosses.md RM1–RM5): a world-wide chance on every revenant kill in the caves, from the
 * accumulated combat levels killed; 45 minutes' cooldown after it appears; it leaves after 10 minutes. It attacks
 * every player in range, cycling ice, blood and air specials between standard ranged attacks.
 */
internal object Maledictus : Boss(Npcs.WILD_CAVE_SUPERIOR) {
    private class State(var accumulated: Int = 0, var cooldownUntil: Long = 0, var active: NpcRef? = null)

    private val STATE = AttrKey<State>("maledictus_state")
    private val COUNT = AttrKey<Int>("maledictus_attacks")

    /** RM1 spawn areas (Wiki list) with the cave part used in the hint: chambers of the named revenants (world spawns). */
    private val AREAS =
        listOf(
            CoordGrid(3175, 10190) to "northern",
            CoordGrid(3237, 10199) to "northern",
            CoordGrid(3215, 10192) to "northern",
            CoordGrid(3244, 10167) to "middle",
            CoordGrid(3251, 10146) to "middle",
            CoordGrid(3176, 10154) to "middle",
            CoordGrid(3207, 10165) to "middle",
            CoordGrid(3223, 10130) to "middle",
            CoordGrid(3214, 10094) to "southern",
            CoordGrid(3160, 10113) to "southern",
            CoordGrid(3163, 10102) to "southern",
        )

    /** The Revenant Caves (bounds of their revenant spawns, world spawns table). */
    fun inCaves(c: CoordGrid): Boolean = c.level == 0 && c.x in CAVE_MIN_X..CAVE_MAX_X && c.z in CAVE_MIN_Z..CAVE_MAX_Z

    /**
     * RM1: "calculated each kill with the total accumulated combat levels of revenants slain by all players on that
     * world, divided by a flat spawn rate. Once the maledictus appears, this chance is reset and the boss is prevented
     * from appearing for 45 minutes".
     */
    fun onRevenantKilled(rev: NpcRef) {
        if (!inCaves(rev.coord)) return
        val state = rev.worldAttr(STATE) ?: State().also { rev.setWorldAttr(STATE, it) }
        val now = rev.tick
        if (state.active?.isInWorld == true || now < state.cooldownUntil) return
        state.accumulated += maxOf(0, rev.combatLevel)
        if (rev.random(SPAWN_RATE) >= state.accumulated) return
        state.accumulated = 0
        state.cooldownUntil = now + COOLDOWN_TICKS
        val (at, part) = AREAS[rev.random(AREAS.size)]
        val boss = rev.spawnNpc(Npcs.WILD_CAVE_SUPERIOR, BossCombat.randomTile(rev, at, SPAWN_SPREAD), wanderRange = 0)
        boss[Bosses.MINION] = true
        state.active = boss
        // RM2: "all players in the cavern will be notified of its presence and given a hint as to where it appeared".
        // OPEN-RM1: the message wording isn't published.
        for (p in rev.playersInWorld()) {
            if (inCaves(
                    p.coord,
                )
            ) {
                p.message("A revenant maledictus has appeared in the $part part of the caves.")
            }
        }
        // RM2: "will remain active for 10 minutes ... before disappearing, if not killed".
        boss.queueHit(ACTIVE_TICKS) { if (boss.isInWorld && !boss.isDying) boss.despawn() }
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT_RANGE
        npc.anim(Seqs.NPC_REVENANT_SUPERIOR_SPAWN)
    }

    /**
     * RM3: "functionally multi-combat ... will attack all players in range simultaneously ... cycle through its three
     * special attacks after every few standard attacks ... Protection prayers will reduce damage taken by half."
     */
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val n = npc[COUNT] ?: 0
        npc[COUNT] = n + 1
        val targets = npc.playersWithin(ATTACK_RANGE).filter { !it.isDying && inCaves(it.coord) }
        if (targets.isEmpty()) return
        val step = n % CYCLE
        if (step < STANDARD_ATTACKS) return standard(npc, targets)
        when ((n / CYCLE) % SPECIALS) {
            0 -> tiles(npc, targets, blood = false)
            1 -> tiles(npc, targets, blood = true)
            else -> air(npc, targets[npc.random(targets.size)])
        }
    }

    /** "Standard attack: a white, spike-shaped ranged attack." */
    private fun standard(npc: NpcRef, targets: List<PlayerRef>) {
        npc.anim(Seqs.NPC_REVENANT_SUPERIOR_ATTACK_MELEE)
        val max = NpcCombatTable.of(npc.type)?.maxFor(CombatStyle.RANGED) ?: 0
        for (p in targets) {
            val delay = BossCombat.delay(npc, p, CombatStyle.RANGED)
            CombatSystem.announceAttack(npc, p, CombatStyle.RANGED)
            BossCombat.projectile(npc, p, SpotAnims.REVENANT_SUPERIOR_STANDARD_ATTACK, delay)
            BossCombat.strike(npc, p, AttackType.RANGED, max, delay, rule = PrayerRule.HALVES)
        }
    }

    /**
     * Ice: "target players' current tiles, then freeze anyone who doesn't move, dealing damage"; blood: "deal damage
     * and heal itself for half of the total damage dealt".
     */
    private fun tiles(npc: NpcRef, targets: List<PlayerRef>, blood: Boolean) {
        npc.anim(if (blood) Seqs.NPC_REVENANT_SUPERIOR_ATTACK_HEAL else Seqs.NPC_REVENANT_SUPERIOR_ATTACK_STUN)
        val max = NpcCombatTable.of(npc.type)?.maxFor(CombatStyle.MAGIC) ?: 0
        val marked = targets.map { it.coord }
        for (t in marked) npc.spotAnimAt(t, SpotAnims.REVENANT_SUPERIOR_SPECIAL_2_IMPACT)
        npc.queueHit(TILE_DELAY) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            var total = 0
            for (t in marked) {
                for (p in BossCombat.playersOn(npc, t)) {
                    val damage = BossCombat.afterPrayer(p, CombatStyle.MAGIC, BossCombat.roll(npc, 1, max), PrayerRule.HALVES)
                    if (!blood) p.spotAnim(SpotAnims.REVENANT_SUPERIOR_BARRAGE_IMPACT)
                    CombatSystem.applyHit(p, npc, damage, HitStyle.MAGIC)
                    total += damage
                    if (!blood) Freezes.freeze(p, npc, ICE_FREEZE)
                }
            }
            if (blood) npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + total / 2)
        }
    }

    /** "a typeless area-of-effect at one player ... up to 25 damage to anyone in a 5x5 area, but less near the edges". */
    private fun air(npc: NpcRef, target: PlayerRef) {
        npc.anim(Seqs.NPC_REVENANT_SUPERIOR_ATTACK_DOT)
        val centre = target.coord
        val delay = BossCombat.delay(npc, target, CombatStyle.MAGIC)
        npc.queueHit(delay) {
            if (!npc.isInWorld) return@queueHit
            for (p in BossCombat.playersOn(npc, centre, radius = 2)) {
                val edge = maxOf(kotlin.math.abs(p.coord.x - centre.x), kotlin.math.abs(p.coord.z - centre.z)) == 2
                // OPEN-RM2: the edge falloff isn't quantified; half.
                CombatSystem.applyHit(p, npc, BossCombat.roll(npc, 0, if (edge) AIR_MAX / 2 else AIR_MAX), HitStyle.TYPELESS)
            }
        }
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        val state = npc.worldAttr(STATE) ?: return
        if (state.active == npc) state.active = null
    }

    private const val CAVE_MIN_X = 3136
    private const val CAVE_MAX_X = 3265
    private const val CAVE_MIN_Z = 10048
    private const val CAVE_MAX_Z = 10239

    /** OPEN-RM3: the "flat spawn rate" isn't published. */
    private const val SPAWN_RATE = 40_000
    private const val COOLDOWN_TICKS = 4500L
    private const val ACTIVE_TICKS = 1000
    private const val SPAWN_SPREAD = 3
    private const val HUNT_RANGE = 10
    private const val ATTACK_RANGE = 10

    /** OPEN-RM4: "after every few standard attacks"; three. */
    private const val STANDARD_ATTACKS = 3
    private const val CYCLE = STANDARD_ATTACKS + 1
    private const val SPECIALS = 3
    private const val TILE_DELAY = 3
    private const val ICE_FREEZE = 8
    private const val AIR_MAX = 25
}
