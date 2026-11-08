package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Kephri, the Path of Scabaras boss (docs/mechanics/raids/toa/kephri.md KP1-KP15). The arena is x 3544-3559, z
 * 5401-5415 (cache collision), entered from the west; she sits in the middle.
 * - KP1: three shield depletions, two swarm intermissions, then the final phase on her real health (11721, 80 base).
 *   Her shield is her hitpoints while shielded (11719, 150 base).
 * - KP2: a fireball every attack (speed 6; faster at path levels 2 / 4), a shadow first (1447 / 1446 / 2111: 4 / 3 / 2
 *   ticks, MT); 1x1, 3x3 with Aerial Assault (plus bomber scarabs); Protect from Magic lets ~34% through.
 * - KP3 / KP4 (shielded): Dung Strike (party order, two players with Blowing Mud) and Mass Incubation (light eggs
 *   explode, dark eggs hatch Agile Scarabs; Lively Larvae four dark eggs).
 * - KP5-KP9: on depletion she is dazed (11720), calls her children (Spitting; then Soldier + Arcane; More Overlords one
 *   more) and swarms from six edge points heal the shield (28 / 24, the first 18 / 14 reach her; spawn pace 4 / 3 / 2 / 1).
 * - KP10 / KP11: the final phase resets her Defence to base; drains stop 20 below base; Medic! keeps swarms coming.
 */
internal object ToaKephri : Boss(Npcs.TOA_KEPHRI_BOSS_SHIELDED, Npcs.TOA_KEPHRI_BOSS_WEAK, Npcs.TOA_KEPHRI_BOSS_ENRAGE) {
    private class State {
        var depletions = 0
        var down = false
        var attacks = 0
        var dungRobin = 0
        var swarmsLeft = 0
        var swarmIndex = 0
        var swarmTimer = 0
        var swarmPoint = 0
        var healing = 0
        var healed = 0
        var medicTimer = 0
        val eggs = HashMap<NpcRef, Int>()
    }

    private val STATE = AttrKey<State>("toa_kephri")

    private fun state(npc: NpcRef): State = npc[STATE] ?: State().also { npc[STATE] = it }

    override fun spawned(npc: NpcRef) {
        if (npc.type != Npcs.TOA_KEPHRI_BOSS_SHIELDED || npc[STATE] != null) return
        val s = state(npc)
        npc.huntRange = HUNT
        npc.frozenUntil = Long.MAX_VALUE
        npc.spotAnim(SpotAnims.KEPHRI_SHIELD_ALIVE)
        ToaKit.loop(npc) { tick(npc, s) }
    }

    override fun retaliates(npc: NpcRef): Boolean = false

    /** KP1: dazed she takes nothing. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int = if (state(npc).down) 0 else damage

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        // KP11: drains stop 20 below base.
        val floor = npc.baseLevel(NpcStat.DEFENCE) - DEFENCE_DRAIN_CAP
        if (npc.level(NpcStat.DEFENCE) < floor) npc.setLevel(NpcStat.DEFENCE, floor)
    }

    /** KP1: a depleted shield dazes her (twice); the third depletion starts the final phase. */
    override fun onLethal(npc: NpcRef): Boolean {
        val s = state(npc)
        if (npc.type == Npcs.TOA_KEPHRI_BOSS_ENRAGE) return false
        s.depletions++
        npc.spotAnim(SpotAnims.KEPHRI_SHIELD_DEATH)
        if (s.depletions >= DEPLETIONS) {
            finalPhase(npc, s)
            return true
        }
        s.down = true
        npc.hitpoints = 1
        npc.transform(Npcs.TOA_KEPHRI_BOSS_WEAK, restoreStats = false)
        npc.anim(Seqs.NPC_KEPHRI_STUNNED)
        children(npc, s.depletions)
        s.swarmsLeft = if (s.depletions == 1) SWARMS_FIRST else SWARMS_SECOND
        s.healing = if (s.depletions == 1) HEALING_FIRST else HEALING_SECOND
        s.swarmIndex = 0
        s.swarmTimer = 0
        s.healed = 0
        s.swarmPoint = npc.random(EDGES.size)
        return true
    }

    private fun finalPhase(npc: NpcRef, s: State) {
        s.down = false
        npc.transform(Npcs.TOA_KEPHRI_BOSS_ENRAGE, restoreStats = true)
        npc[SharedAttrs.RAID_SCALER]?.invoke(npc)
        npc.setLevel(NpcStat.DEFENCE, npc.baseLevel(NpcStat.DEFENCE))
        npc.anim(Seqs.NPC_KEPHRI_STUNNED_WAKEUP)
        // KP3: as the swarm around her dissipates, one Dung Strike on everyone.
        dungStrike(npc, ToaKit.raiders(npc))
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        if (s.down) return
        npc.setAttackClock(npc.tick + SPEED - ToaKit.step(npc))
        val shielded = npc.type == Npcs.TOA_KEPHRI_BOSS_SHIELDED
        if (shielded && ++s.attacks % SPECIAL_EVERY == 0) {
            // KP3 / KP4 (OPEN-KP8 cadence): alternately; Dung Strike instead of eggs with too many scarabs alive.
            val scarabs = Bosses.minions(npc, Npcs.TOA_KEPHRI_SCARAB_RANGEKITE).size
            if ((s.attacks / SPECIAL_EVERY) % 2 == 0 || scarabs >= MAX_SCARABS) {
                val n = if (ToaKit.has(npc, BLOWING_MUD)) 2 else 1
                val order = ToaKit.raiders(npc)
                if (order.isNotEmpty()) dungStrike(npc, List(minOf(n, order.size)) { order[(s.dungRobin++) % order.size] })
            } else {
                incubate(npc, s)
            }
            return
        }
        fireball(npc, victim)
        if (ToaKit.has(npc, AERIAL_ASSAULT) && s.attacks % BOMBER_EVERY == 0) bomber(npc)
    }

    /** KP2: shadow then fireball; the tile (3x3 with Aerial Assault) takes it; Protect from Magic ~34% through. */
    private fun fireball(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.NPC_KEPHRI_ATTACK_RANGED)
        val step = ToaKit.step(npc)
        val tile = victim.coord
        npc.spotAnimAt(tile, SHADOWS[step])
        BossCombat.projectile(npc, victim, FIREBALL, SHADOW_TICKS[step])
        landBomb(npc, tile, SHADOW_TICKS[step])
    }

    /** Aerial Assault's bomber scarab (2147): lands like the fireball on a random player. */
    private fun bomber(npc: NpcRef) {
        val p = ToaKit.raiders(npc).let { if (it.isEmpty()) null else it[npc.random(it.size)] } ?: return
        val step = ToaKit.step(npc)
        npc.spotAnimAt(p.coord, SHADOWS[step])
        BossCombat.projectile(npc, p, SpotAnims.KEPHRI_KAMIKAZE_SCARAB, SHADOW_TICKS[step])
        landBomb(npc, p.coord, SHADOW_TICKS[step])
    }

    private fun landBomb(npc: NpcRef, tile: CoordGrid, delay: Int) {
        val aerial = ToaKit.has(npc, AERIAL_ASSAULT)
        val r = if (aerial) 1 else 0
        npc.queueHit(delay, strong = false) {
            // MT: no damage while she is down.
            if (!npc.isInWorld || state(npc).down) return@queueHit
            for (dx in -r..r) for (dz in -r..r) npc.spotAnimAt(CoordGrid(tile.x + dx, tile.z + dz, tile.level), IMPACT)
            // KP13: one hit per player even where tiles overlap.
            for (p in ToaKit.on(npc, tile, r)) {
                val base = if (aerial) FIREBALL_MAX * AERIAL_PERCENT / PERCENT else FIREBALL_MAX
                val raw = BossCombat.roll(npc, 1, ToaKit.dmg(npc, base))
                CombatSystem.queueHit(p, npc, ToaKit.reduced(npc, p, CombatStyle.MAGIC, raw, PRAYER_KEEP), HitStyle.MAGIC, 0)
            }
        }
    }

    /** KP3: flies warn, the target is thrown to the rear (stun 3 ticks), dung falls along the way (light damage, shoved). */
    private fun dungStrike(npc: NpcRef, targets: List<PlayerRef>) {
        for (p in targets) {
            p.spotAnim(SpotAnims.KEPHRI_INDICATE_PLAYER_FOR_DUNG)
            npc.queueHit(DUNG_WARN, strong = false) {
                if (!npc.isInWorld || p.isDying) return@queueHit
                p.message("Kephri throws you back!")
                val rear = ToaKit.at(npc, CoordGrid(ARENA_MIN_X, ToaKit.template(npc, p.coord).z))
                val row = p.coord.z
                p.teleport(rear)
                ToaKit.stun(p, DUNG_STUN)
                for (x in rear.x + 1..ToaKit.atX(npc, ARENA_MAX_X)) {
                    val t = CoordGrid(x, row, p.coord.level)
                    if (!npc.isWalkable(t)) continue
                    npc.spotAnimAt(t, SpotAnims.KEPHRI_DUNG_DROP)
                    npc.addLoc(Locs.KEPHRI_DUNG, t, CENTREPIECE, 0)
                    for (v in ToaKit.on(npc, t)) {
                        ToaKit.areaHit(npc, v, BossCombat.roll(npc, 1, DUNG_MAX))
                        val free = BossCombat.randomTile(npc, v.coord, DUNG_SHOVE)
                        if (free == v.coord) ToaKit.areaHit(npc, v, v.hitpoints) else v.teleport(free)
                    }
                }
            }
        }
    }

    /** KP4: light eggs explode 3x3, dark eggs hatch Agile Scarabs (2, Lively Larvae 4); OPEN-KP9 counts and fuse. */
    private fun incubate(npc: NpcRef, s: State) {
        npc.anim(Seqs.NPC_KEPHRI_ATTACK_SPECIAL_EGGS)
        val dark = if (ToaKit.has(npc, LIVELY_LARVAE)) DARK_EGGS_LIVELY else DARK_EGGS
        repeat(LIGHT_EGGS + dark) { i ->
            val at = BossCombat.randomTile(npc, ToaKit.at(npc, CENTRE), ARENA_RADIUS)
            val egg =
                Bosses.summon(
                    npc,
                    if (i <
                        dark
                    ) {
                        Npcs.KEPHRI_EGG_HATCH
                    } else {
                        Npcs.KEPHRI_EGG_EXPLODE
                    },
                    at,
                    huntRange = 0,
                    inheritRun = true,
                )
            s.eggs[egg] = EGG_FUSE
        }
    }

    /** KP5: Spitting at the first break; Soldier + Arcane at the second; More Overlords one more (Soldier / Spitting). */
    private fun children(npc: NpcRef, depletion: Int) {
        val more = ToaKit.has(npc, MORE_OVERLORDS) && ToaKit.raiders(npc).size > 1
        val list =
            if (depletion == 1) {
                listOfNotNull(Npcs.TOA_KEPHRI_GUARDIAN_RANGED, Npcs.TOA_KEPHRI_GUARDIAN_MELEE.takeIf { more })
            } else {
                listOfNotNull(
                    Npcs.TOA_KEPHRI_GUARDIAN_MELEE,
                    Npcs.TOA_KEPHRI_GUARDIAN_MAGE,
                    Npcs.TOA_KEPHRI_GUARDIAN_RANGED.takeIf { more },
                )
            }
        for (type in list) {
            // KP5: Spitting north-east of her, Soldier east, Arcane south-east.
            val off =
                when (type) {
                    Npcs.TOA_KEPHRI_GUARDIAN_RANGED -> NE
                    Npcs.TOA_KEPHRI_GUARDIAN_MELEE -> E
                    else -> SE
                }
            Bosses.summon(
                npc,
                type,
                CoordGrid(npc.coord.x + off.first, npc.coord.z + off.second, npc.coord.level),
                huntRange = HUNT,
                inheritRun = true,
                scale = true,
            )
        }
    }

    private fun tick(npc: NpcRef, s: State) {
        swarms(npc, s)
        eggs(npc, s)
        medic(npc, s)
    }

    /** KP6: swarms from six edge points clockwise (sometimes skipping one), pace 4 x4 / 3 x4 / 2 x4 / 1; the first N heal. */
    private fun swarms(npc: NpcRef, s: State) {
        moveSwarms(npc, s)
        if (!s.down) return
        if (s.swarmsLeft > 0) {
            val pace =
                when {
                    s.swarmIndex < PACE_STEP -> 4
                    s.swarmIndex < 2 * PACE_STEP -> 3
                    s.swarmIndex < 3 * PACE_STEP -> 2
                    else -> 1
                }
            if (++s.swarmTimer >= pace) {
                s.swarmTimer = 0
                val group = if (ToaKit.pathLevel(npc) >= 2 && npc.random(GROUP_ODDS) == 0) GROUP_SIZE else 1
                repeat(minOf(group, s.swarmsLeft)) { spawnSwarm(npc, s, s.swarmIndex < s.healing) }
                s.swarmPoint = (s.swarmPoint + 1 + if (npc.random(SKIP_ODDS) == 0) 1 else 0) % EDGES.size
            }
            return
        }
        if (Bosses.minions(npc, Npcs.TOA_KEPHRI_SHIELD_SCARAB).isEmpty()) wake(npc, s)
    }

    private val HEALS = AttrKey<Boolean>("toa_kephri_swarm_heals")

    private fun spawnSwarm(npc: NpcRef, s: State, heals: Boolean) {
        s.swarmsLeft--
        s.swarmIndex++
        val sw = Bosses.summon(npc, Npcs.TOA_KEPHRI_SHIELD_SCARAB, ToaKit.at(npc, EDGES[s.swarmPoint]), huntRange = 0, inheritRun = true)
        sw[HEALS] = heals
    }

    private fun moveSwarms(npc: NpcRef, s: State) {
        val slow = !s.down
        for (sw in Bosses.minions(npc, Npcs.TOA_KEPHRI_SHIELD_SCARAB)) {
            // Medic! swarms outside the shield phases move 50% slower.
            if (slow && npc.tick % 2 == 0L) continue
            if (sw.distanceTo(npc) <= 1) {
                sw.anim(SWARM_LEAK)
                if (sw[HEALS] == true) heal(npc, s)
                sw.despawn()
            } else {
                sw.walkTo(npc.coord)
            }
        }
    }

    /** KP6: about 10% of the shield a swarm (less for larger teams), capped about 15% above max; on real health she heals. */
    private fun heal(npc: NpcRef, s: State) {
        val team = ToaKit.raiders(npc).size.coerceAtLeast(1)
        val amount = maxOf(1, npc.maxHitpoints * SWARM_HEAL_PERCENT / PERCENT / (1 + (team - 1) / TEAM_DIVISOR))
        if (s.down) {
            s.healed += amount
        } else {
            npc.hitpoints = minOf(npc.maxHitpoints * OVERCHARGE / PERCENT, npc.hitpoints + amount)
        }
    }

    /** The intermission ends: she wakes shielded with what the swarms brought (at least 1). */
    private fun wake(npc: NpcRef, s: State) {
        s.down = false
        npc.transform(Npcs.TOA_KEPHRI_BOSS_SHIELDED, restoreStats = false)
        npc.anim(Seqs.NPC_KEPHRI_STUNNED_WAKEUP)
        npc.spotAnim(SpotAnims.KEPHRI_SHIELD_REFORM)
        npc.hitpoints = s.healed.coerceIn(1, npc.maxHitpoints * OVERCHARGE / PERCENT)
    }

    /** A Soldier Scarab's swarm while she is down (~every 8 ticks, 5-7 charges, by its HP); on real health it heals her (KP8). */
    fun soldierSwarm(owner: NpcRef, soldier: NpcRef) {
        val s = state(owner)
        val amount = maxOf(1, soldier.hitpoints / SOLDIER_HEAL_DIV)
        if (s.down) s.healed += amount else owner.hitpoints = minOf(owner.maxHitpoints, owner.hitpoints + amount)
        BossCombat.projectile(soldier, owner, SpotAnims.TOA_KEPHRI_MELEE_HEAL, 2)
    }

    fun isDown(owner: NpcRef): Boolean = state(owner).down

    /** KP4: eggs; light ones explode in a 3x3, dark ones hatch an Agile Scarab (unless dung landed on it: then it dies). */
    private fun eggs(npc: NpcRef, s: State) {
        for ((egg, left) in s.eggs.entries.toList()) {
            if (!egg.isInWorld || egg.isDying) {
                s.eggs.remove(egg)
                continue
            }
            if (left > 1) {
                s.eggs[egg] = left - 1
                continue
            }
            s.eggs.remove(egg)
            if (egg.type == Npcs.KEPHRI_EGG_EXPLODE) {
                npc.spotAnimAt(egg.coord, IMPACT)
                for (p in ToaKit.on(npc, egg.coord, 1)) ToaKit.hitRolled(npc, p, EGG_MAX)
            } else {
                Bosses.summon(npc, Npcs.TOA_KEPHRI_SCARAB_RANGEKITE, egg.coord, huntRange = HUNT, inheritRun = true)
            }
            egg.despawn()
        }
    }

    /** KP6 Medic!: swarms keep coming from the edges outside the shield phases (OPEN-KP11: one every 10 ticks), 50% slower. */
    private fun medic(npc: NpcRef, s: State) {
        if (s.down || !ToaKit.has(npc, MEDIC)) return
        if (++s.medicTimer < MEDIC_EVERY) return
        s.medicTimer = 0
        val sw =
            Bosses.summon(
                npc,
                Npcs.TOA_KEPHRI_SHIELD_SCARAB,
                ToaKit.at(npc, EDGES[npc.random(EDGES.size)]),
                huntRange = 0,
                inheritRun = true,
            )
        sw[HEALS] = true
    }

    /** KP12: Scabaras' capture (if not read) and Scarab dung to the top damage dealer. */
    override fun died(npc: NpcRef, killer: EntityRef?) {
        npc.anim(Seqs.NPC_KEPHRI_DEATH)
        Bosses.dismissMinions(npc)
        val top = Bosses.topDamager(npc) ?: return
        ToaKit.giveOrDrop(top, Objs.TOA_KEPHRI_POO, "kephri drop")
        if (top.varbit(Varbits.TOA_BOOK_KEPHRI) == 0) ToaKit.giveOrDrop(top, Objs.TOA_BOOK_KEPHRI, "kephri drop")
    }

    // ---- data (template: region 14164, base (3520, 5376); the arena from the cache collision)

    private const val ARENA_MIN_X = 3544
    private const val ARENA_MAX_X = 3559
    private val CENTRE = CoordGrid(3551, 5408)
    private const val ARENA_RADIUS = 7

    /** KP6: six edge points NE, SE, S, SW, NW, N (clockwise order). */
    private val EDGES =
        listOf(
            CoordGrid(3559, 5415),
            CoordGrid(3559, 5401),
            CoordGrid(3551, 5401),
            CoordGrid(3544, 5401),
            CoordGrid(3544, 5415),
            CoordGrid(3551, 5415),
        )
    private val NE = 5 to 4
    private val E = 6 to 1
    private val SE = 5 to -2

    /** KP1: three depletions. KP6: 28 / 24 swarms, 18 / 14 heal; pace steps of 4. */
    private const val DEPLETIONS = 3
    private const val SWARMS_FIRST = 28
    private const val SWARMS_SECOND = 24
    private const val HEALING_FIRST = 18
    private const val HEALING_SECOND = 14
    private const val PACE_STEP = 4
    private const val GROUP_ODDS = 3
    private const val GROUP_SIZE = 3
    private const val SKIP_ODDS = 4
    private const val SWARM_HEAL_PERCENT = 10
    private const val TEAM_DIVISOR = 4
    private const val OVERCHARGE = 115
    private const val SOLDIER_HEAL_DIV = 4

    /** KP2: speed 6; shadows 1447 / 1446 / 2111 -> 4 / 3 / 2 ticks (MT) by path step; 24 base; ~66% blocked; Aerial Assault +50% (OPEN). */
    private const val SPEED = 6
    private val SHADOWS = intArrayOf(1447, 1446, 2111)
    private val SHADOW_TICKS = intArrayOf(4, 3, 2)
    private const val FIREBALL = 2266
    private const val IMPACT = 2156
    private const val FIREBALL_MAX = 24
    private const val AERIAL_PERCENT = 150
    private const val BOMBER_EVERY = 3
    private const val PRAYER_KEEP = 34
    private const val PERCENT = 100

    /** KP3 / KP4 (OPEN-KP8 / OPEN-KP9): a special every 4th attack; warning 2 ticks; stun 2 s = 3 ticks; light 1-6; shove within 10. */
    private const val SPECIAL_EVERY = 4
    private const val MAX_SCARABS = 4
    private const val DUNG_WARN = 2
    private const val DUNG_STUN = 3
    private const val DUNG_MAX = 6
    private const val DUNG_SHOVE = 10
    private const val LIGHT_EGGS = 6
    private const val DARK_EGGS = 2
    private const val DARK_EGGS_LIVELY = 4
    private const val EGG_FUSE = 10
    private const val EGG_MAX = 15

    private const val MEDIC_EVERY = 10

    /** TA plugin: a swarm reaching her plays 9607. */
    private const val SWARM_LEAK = Seqs.NPC_SWARM01_SCARAB01_INTEGRATE01
    private const val DEFENCE_DRAIN_CAP = 20
    private const val HUNT = 24
    private const val CENTREPIECE = 10

    private const val AERIAL_ASSAULT = "Aerial Assault"
    private const val BLOWING_MUD = "Blowing Mud"
    private const val LIVELY_LARVAE = "Lively Larvae"
    private const val MORE_OVERLORDS = "More Overlords"
    private const val MEDIC = "Medic!"
}

/**
 * Kephri's children (KP7-KP9): the Spitting Scarab hits everyone within 8 tiles through prayer (max 10, poison 6
 * sometimes); the Soldier hits one target in melee (generic) and heals her every ~8 ticks while she is down (5-7
 * charges); the Arcane Scarab charges a red swarm that hits everyone (65+ unprotected, ~34% through Protect from Magic)
 * and relocates after 2 + N hits or a single hit of 39+.
 */
internal object ToaKephriChildren : Boss(Npcs.TOA_KEPHRI_GUARDIAN_MELEE, Npcs.TOA_KEPHRI_GUARDIAN_RANGED, Npcs.TOA_KEPHRI_GUARDIAN_MAGE) {
    private val TIMER = AttrKey<Int>("toa_kephri_child_timer")
    private val HITS = AttrKey<Int>("toa_kephri_arcane_hits")
    private val SPOT = AttrKey<Int>("toa_kephri_arcane_spot")
    private val CHARGES = AttrKey<Int>("toa_kephri_soldier_charges")

    override fun spawned(npc: NpcRef) {
        ToaKit.loop(npc) { tick(npc) }
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        when (npc.type) {
            Npcs.TOA_KEPHRI_GUARDIAN_RANGED -> {
                npc.anim(Seqs.NPC_SCARAB_RANGED_ATTACK)
                for (p in ToaKit.raiders(npc)) {
                    if (npc.distanceTo(p) > SPIT_RANGE) continue
                    BossCombat.projectile(npc, p, SPIT, SPIT_DELAY)
                    val dmg = BossCombat.roll(npc, 0, ToaKit.dmg(npc, SPIT_MAX))
                    CombatSystem.queueHit(p, npc, dmg, HitStyle.RANGED, SPIT_DELAY) {
                        if (dmg > 0 && npc.random(POISON_ODDS) == 0) Poison.poison(p, SPIT_POISON)
                    }
                }
            }

            Npcs.TOA_KEPHRI_GUARDIAN_MELEE -> {
                if (distance) return
                npc.anim(Seqs.NPC_SCARAB_ATTACK)
                ToaKit.strike(npc, victim, AttackType.SLASH, ToaKit.dmg(npc, SOLDIER_MAX), 0)
            }

            else -> {
                Unit
            }
        }
    }

    private fun tick(npc: NpcRef) {
        val owner = npc[Bosses.OWNER] ?: return
        val t = (npc[TIMER] ?: 0) + 1
        npc[TIMER] = t
        when (npc.type) {
            Npcs.TOA_KEPHRI_GUARDIAN_MELEE -> {
                val charges = npc[CHARGES] ?: (SOLDIER_CHARGES_MIN + npc.random(SOLDIER_CHARGES_SPREAD))
                if (charges > 0 && t % SOLDIER_EVERY == 0) {
                    npc[CHARGES] = charges - 1
                    ToaKephri.soldierSwarm(owner, npc)
                } else if (npc[CHARGES] == null) {
                    npc[CHARGES] = charges
                }
            }

            Npcs.TOA_KEPHRI_GUARDIAN_MAGE -> {
                if (t % ARCANE_CHARGE != 0) {
                    npc.spotAnim(SpotAnims.TOA_KEPHRI_MAGE_CHARGE)
                    return
                }
                for (p in ToaKit.raiders(npc)) {
                    val raw = BossCombat.roll(npc, ARCANE_MIN, ToaKit.dmg(npc, ARCANE_MAX))
                    CombatSystem.queueHit(p, npc, ToaKit.reduced(npc, p, CombatStyle.MAGIC, raw, ARCANE_KEEP), HitStyle.MAGIC, 1)
                }
                relocate(npc)
            }

            else -> {
                Unit
            }
        }
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        if (npc.type != Npcs.TOA_KEPHRI_GUARDIAN_MAGE || source !is PlayerRef) return
        val hits = (npc[HITS] ?: 0) + 1
        npc[HITS] = hits
        if (hits >= RELOCATE_BASE + ToaKit.raiders(npc).size) relocate(npc)
    }

    /** KP9: a single hit of 39+ also makes it relocate. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        if (npc.type == Npcs.TOA_KEPHRI_GUARDIAN_MAGE && damage >= BIG_HIT) npc.queueHit(0, strong = false) { relocate(npc) }
        return damage
    }

    /** KP9: SE -> NW -> mid-south -> NE (offsets from Kephri); the charge disperses. */
    private fun relocate(npc: NpcRef) {
        val owner = npc[Bosses.OWNER] ?: return
        val spot = ((npc[SPOT] ?: 0) + 1) % SPOTS.size
        npc[SPOT] = spot
        npc[HITS] = 0
        npc[TIMER] = 0
        val (dx, dz) = SPOTS[spot]
        npc.teleport(CoordGrid(owner.coord.x + dx, owner.coord.z + dz, owner.coord.level))
    }

    private val SPOTS = listOf(5 to -2, -4 to 5, 1 to -4, 5 to 4)

    /** KP7: max 10, poison 6, within 8 tiles (conflict 4: 8 used); speed 3. */
    private const val SPIT_RANGE = 8
    private const val SPIT_MAX = 10
    private const val SPIT_POISON = 6
    private const val POISON_ODDS = 4
    private const val SPIT = 2152
    private const val SPIT_DELAY = 2

    /** KP8: max 17; a swarm about every 5 s (8 ticks) for 5-7 charges. */
    private const val SOLDIER_MAX = 17
    private const val SOLDIER_EVERY = 8
    private const val SOLDIER_CHARGES_MIN = 5
    private const val SOLDIER_CHARGES_SPREAD = 3

    /** KP9 / OPEN-KP12: a full charge every 16 ticks, 65-80 base, ~34% through Protect from Magic; relocate after 2 + N hits or a 39+. */
    private const val ARCANE_CHARGE = 16
    private const val ARCANE_MIN = 65
    private const val ARCANE_MAX = 80
    private const val ARCANE_KEEP = 34
    private const val RELOCATE_BASE = 2
    private const val BIG_HIT = 39
}

/** The Agile Scarab (KP4) and the shield swarms (KP6): the swarm never attacks; the agile one is the generic ranged NPC. */
internal object ToaKephriSwarm : Boss(Npcs.TOA_KEPHRI_SHIELD_SCARAB) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) = Unit

    override fun retaliates(npc: NpcRef): Boolean = false
}
