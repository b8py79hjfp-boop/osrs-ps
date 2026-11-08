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
import tp.util.CoordGrid

/**
 * The Nylocas room, ToB room 3 (docs/mechanics/raids/tob/nylocas.md NY1-NY13; wave delays, cap and lanes from
 * runelite-timings.md; lifetimes, bite rate and Vasilias rhythm from capture-values.md, D-080). The four supports are
 * the room's NPCs; the south-west one runs the waves.
 * - 31 fixed waves (the wiki chart: lane, size, style, aggressive, flicker) on the room's 4-tick clock with the natural
 *   delays 4,4,4,4,16,...; a wave due while the room is at its cap (12, from wave 20 24; Hard 15 before 20; a Prinkipas
 *   counts 3) slips 4 ticks. Hard: a Prinkipas with waves 10, 20 and 30, the next wave 16 ticks later.
 * - Wave Nylocas walk a tile a tick to the nearest support and bite it every 3 ticks, or (aggressive ones) to the party;
 *   they explode at the end of their life (small 52, large 55 ticks) on everyone within 2; only their own style hurts
 *   them, a wrong-style attacker is immune from then on (Hard: and takes the hit back). A large one splits into two
 *   small ones 7 ticks after it dies.
 * - A support at 0 HP collapses: rocks on every raider 3 ticks later; all four down = the party dies.
 * - 16 ticks after the last wave Nylocas dies (on the 4-tick clock) Vasilias drops in the middle.
 */
internal object TobNylocasRoom : Boss(Npcs.TOB_NYLOCAS_SUPPORT, TobNylo.ENTRY_PILLAR, TobNylo.HARD_PILLAR) {
    private class Room(val mode: Int, val origin: CoordGrid) {
        var started = -1L
        var wave = 0
        var nextWave = 0L
        var lastWaveDone = -1L
        var vasilias = false
        var collapsed = 0
    }

    private val ROOM = AttrKey<Room>("tob_nylocas_room")
    private val CONTROLLER = AttrKey<NpcRef>("tob_nylocas_controller")

    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
        // The four supports spawn on the same tick: the south-west one (smallest x, then z) runs the room.
        npc.queueHit(1, strong = false) {
            val pillars = npc.npcsWithin(npc.coord, PILLAR_SCAN).filter { it.type == npc.type }
            val sw = pillars.minWith(compareBy<NpcRef>({ it.coord.x }, { it.coord.z }))
            for (p in pillars) p[CONTROLLER] = sw
            if (sw == npc) {
                npc[ROOM] = Room(TobModes.of(npc.type), npc.coord)
                loop(npc)
            }
        }
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) = Unit

    /** Players cannot damage the supports; only the Nylocas' bites do (TobNylo). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int = if (source is PlayerRef) 0 else damage

    /** The room controller of [pillar]'s room (the south-west support), or null. */
    fun controllerOf(pillar: NpcRef): NpcRef? = pillar[CONTROLLER]

    private fun at(r: Room, off: Pair<Int, Int>) = CoordGrid(r.origin.x + off.first, r.origin.z + off.second, r.origin.level)

    private fun loop(npc: NpcRef) {
        npc.queueHit(1, strong = false) {
            val r = npc[ROOM] ?: return@queueHit
            if (!npc.isInWorld && r.collapsed >= PILLARS) return@queueHit
            tick(npc, r)
            loop(npc)
        }
    }

    private fun raiders(npc: NpcRef): List<PlayerRef> = TfaKit.arena(npc, ROOM_RADIUS)

    private fun tick(npc: NpcRef, r: Room) {
        val platform = at(r, PLATFORM_CENTRE)
        if (r.started < 0) {
            // TB8: the waves start once a raider is on the platform (past the barrier).
            if (raiders(npc).none {
                    kotlin.math.abs(it.coord.x - platform.x) <= PLATFORM &&
                        kotlin.math.abs(it.coord.z - platform.z) <= PLATFORM
                }
            ) {
                return
            }
            r.started = npc.tick
            r.nextWave = npc.tick
        }
        if (r.vasilias) return
        val clock = (npc.tick - r.started) % CLOCK == 0L
        if (r.wave < WAVES.size) {
            if (clock && npc.tick >= r.nextWave) {
                if (alive(npc) >= cap(r)) {
                    r.nextWave += CLOCK
                } else {
                    spawnWave(npc, r)
                }
            }
            return
        }
        if (alive(npc) > 0) return
        if (r.lastWaveDone < 0) r.lastWaveDone = npc.tick
        if (clock && npc.tick - r.lastWaveDone >= BOSS_DELAY) {
            r.vasilias = true
            val v = Bosses.summon(npc, TobNylo.vasiliasSpawn(r.mode), at(r, VASILIAS_SW), huntRange = 0, inheritRun = true)
            TobModes.scale(v)
        }
    }

    /** RuneLite: alive Nylocas count toward the cap, a Prinkipas as 3. */
    private fun alive(npc: NpcRef): Int =
        Bosses.minions(npc).sumOf {
            when {
                it.type in TobNylo.PRINKIPAS -> PRINKIPAS_WEIGHT
                it.type in TobNylo.WAVE_IDS -> 1
                else -> 0
            }
        }

    private fun cap(r: Room): Int =
        when {
            r.wave >= LATE_WAVE - 1 -> LATE_CAP
            r.mode == TobModes.HARD -> HARD_EARLY_CAP
            else -> EARLY_CAP
        }

    private fun spawnWave(npc: NpcRef, r: Room) {
        val spec = WAVES[r.wave]
        r.wave++
        for ((lane, tokens) in spec.split('|').withIndex()) {
            if (tokens == "-") continue
            for ((k, token) in tokens.split('+').withIndex()) {
                val big = token[0] == 'L'
                val off = if (big) BIG_TILES[lane] else SMALL_TILES[lane][k % 2]
                TobNylo.spawn(npc, r.mode, at(r, off), token)
            }
        }
        val prince = r.mode == TobModes.HARD && r.wave in PRINCE_WAVES
        if (prince) {
            val p = Bosses.summon(npc, TobNylo.PRINKIPAS_SPAWN, at(r, PRINCE_SW), huntRange = 0, inheritRun = true)
            TobModes.scale(p)
        }
        r.nextWave = npc.tick + if (prince) PRINCE_DELAY else DELAYS.getOrElse(r.wave - 1) { CLOCK }
    }

    /** NY7 / capture: a support at 0 HP collapses; rocks land on every raider 3 ticks later; four down wipes the party. */
    fun collapse(pillar: NpcRef) {
        val controller = controllerOf(pillar) ?: return
        val r = controller[ROOM] ?: return
        r.collapsed++
        pillar.anim(Seqs.NYLOCAS_PILLAR_COLLAPSE)
        pillar.addLoc(Locs.TOB_NYLOCAS_SUPPORT_DEAD, pillar.coord, WALL_SHAPE, 0)
        val all = r.collapsed >= PILLARS
        pillar.queueHit(ROCKS_LAND, strong = false) {
            for (p in TfaKit.arena(pillar, ROOM_RADIUS)) {
                val dmg = if (all) p.hitpoints else TobModes.damage(r.mode, BossCombat.roll(pillar, 0, ROCKS_MAX))
                CombatSystem.applyHit(p, pillar, dmg, HitStyle.TYPELESS)
            }
            pillar.despawn()
        }
    }

    /**
     * The wiki wave chart (Strat/Ny), lanes East | South | West, spawns joined by `+`: size S / L, style i melee, t
     * ranged, h magic, `!` aggressive, `~abc` the flicker order (NY4).
     */
    private val WAVES =
        listOf(
            "Si|Sh|St!",
            "St|Si!|Sh",
            "Sh!|St|Si",
            "Si|Lh|St",
            "Sh|Si|Lt",
            "Li|St|Sh",
            "Li|Sh+Lt!|-",
            "St|Si|Lh!",
            "Sh|-|Lt!+Si",
            "St!+Lt|St+St|St+St!",
            "Sh!+Sh|Sh+Sh|Lh!",
            "Si!+Si|Li|Si+Si!",
            "Li!|St+Si|St+Sh!",
            "Lt!|Sh+St|Sh+Si!",
            "Sh!+St|Lh|Si+St!",
            "St~hit|St~iht|St~tht",
            "Lh~hih|Lh~hih|Lh~hih",
            "Lt~tht|Lt~tht|Lt~tht!",
            "Lh~hih!|Lh~hih|Lh~hih",
            "Li~iti|Li~hti!|Li~iti",
            "St~tit+St~tit!|Si~ihi+Si~iti!|Sh~hit+St~hth",
            "Li~hti!|Si~thi+Si~hti|Li~iti",
            "Li~hti|Li~thi!|Si~hti+St~tht",
            "Li!|Lh!|Li~thi!",
            "Lh~hih|Lt|Li!",
            "Lh|Li~ihi|Lh!",
            "Lh~hih|Lt~iht!|Lh",
            "Sh~hih+Si~thi|Si~iti+St~hit|Si~ihi+Sh~tih!",
            "Si~hti+Sh~tih!|Li|Sh~ith!+St~tit",
            "Lh!|Si~iti+Si~hti|Lt~tit",
            "Sh~hth+St~tit|St~iht+St~hit|Sh~ith+St~tit",
        )

    /** RuneLite (BL NylocasDataTracker.java:58-60): ticks from wave w to w + 1. */
    private val DELAYS = intArrayOf(4, 4, 4, 4, 16, 4, 12, 4, 12, 8, 8, 8, 8, 8, 8, 4, 12, 8, 12, 16, 8, 12, 8, 8, 8, 4, 8, 4, 4, 4)

    /**
     * RuneLite (TQ NylocasConstants): lane tiles from the south-west support (template 3289, 4242): East (3310, 4248 /
     * 4249), big (3309, 4248); South (3295 / 3296, 4233), big (3296, 4234); West (3281, 4248 / 4249), big (3282, 4249).
     */
    private val SMALL_TILES = listOf(listOf(21 to 6, 21 to 7), listOf(6 to -9, 7 to -9), listOf(-8 to 6, -8 to 7))
    private val BIG_TILES = listOf(20 to 6, 7 to -8, -7 to 7)
    private val PRINCE_WAVES = setOf(10, 20, 30)
    private val PLATFORM_CENTRE = 6 to 6
    private val VASILIAS_SW = 5 to 4
    private val PRINCE_SW = 5 to 5

    private const val PILLARS = 4
    private const val PILLAR_SCAN = 15
    private const val ROOM_RADIUS = 20

    /** The platform (template 3290..3301 x 4243..4254) as a half-width around its centre tile. */
    private const val PLATFORM = 6
    private const val CLOCK = 4
    private const val EARLY_CAP = 12
    private const val HARD_EARLY_CAP = 15
    private const val LATE_CAP = 24
    private const val LATE_WAVE = 20
    private const val PRINKIPAS_WEIGHT = 3
    private const val PRINCE_DELAY = 16
    private const val BOSS_DELAY = 16
    private const val ROCKS_LAND = 3

    /** NY7 (Strat): "up to 50" a collapse (OPEN-NY12; Entry halves). */
    private const val ROCKS_MAX = 50
    private const val WALL_SHAPE = 10
}

/** Every wave Nylocas id: Normal 8342-8353, Entry 10774-10785, Hard 10791-10802 (client contract 4). */
private val NYLO_WAVE_IDS: Set<Int> =
    (Npcs.TOB_NYLOCAS_INCOMING_MELEE until Npcs.TOB_NYLOCAS_INCOMING_MELEE + 12).toSet() + (10774 until 10786) + (10791 until 10803)

/** Vasilias spawning / melee / magic / ranged in Normal, Entry, Hard, and Hard's Prinkipas (client contract 4). */
private val VASILIAS_IDS: IntArray =
    intArrayOf(
        Npcs.NYLOCAS_BOSS_SPAWNING,
        Npcs.NYLOCAS_BOSS_MELEE,
        Npcs.NYLOCAS_BOSS_MAGIC,
        Npcs.NYLOCAS_BOSS_RANGED,
        Npcs.NYLOCAS_BOSS_SPAWNING_STORY,
        10787,
        10788,
        10789,
        Npcs.NYLOCAS_BOSS_SPAWNING_HARD,
        10808,
        10809,
        10810,
        10803,
        10804,
        10805,
        10806,
    )

/** A wave Nylocas (small or large, three styles, three modes). */
internal object TobNylo : Boss(*NYLO_WAVE_IDS.toIntArray()) {
    private class Nylo(val style: Int, val big: Boolean, val aggro: Boolean, val flicker: String?, val born: Long, val mode: Int) {
        val immune = HashSet<String>()
        var styleNow = style
        var nextBite = 0L
    }

    private val NYLO = AttrKey<Nylo>("tob_nylo")

    fun spawn(controller: NpcRef, mode: Int, at: CoordGrid, token: String) {
        val big = token[0] == 'L'
        val style = STYLE_CHARS.indexOf(token[1])
        val aggro = token.endsWith("!")
        val flicker = token.substringAfter('~', "").trimEnd('!').takeIf { it.length == FLICKER_STEPS }
        val first = if (flicker != null) STYLE_CHARS.indexOf(flicker[0]) else style
        val n = Bosses.summon(controller, id(mode, big, first, fighting = aggro), at, huntRange = 0, inheritRun = true)
        TobModes.scale(n)
        n[NYLO] = Nylo(style, big, aggro, flicker, n.tick, mode).also { it.styleNow = first }
        live(n, controller)
    }

    /** Client contract 4: incoming melee / ranged / magic, big +3, fighting +6, per mode. */
    fun id(mode: Int, big: Boolean, style: Int, fighting: Boolean): Int {
        val base =
            when (mode) {
                TobModes.ENTRY -> ENTRY_BASE
                TobModes.HARD -> HARD_BASE
                else -> Npcs.TOB_NYLOCAS_INCOMING_MELEE
            }
        return base + (if (big) BIG_OFFSET else 0) + style + if (fighting) FIGHTING_OFFSET else 0
    }

    private fun live(n: NpcRef, controller: NpcRef) {
        n.queueHit(1, strong = false) {
            val s = n[NYLO] ?: return@queueHit
            if (!n.isInWorld) return@queueHit
            val age = (n.tick - s.born).toInt()
            if (age >= if (s.big) BIG_LIFE else SMALL_LIFE) {
                explode(n, s)
                return@queueHit
            }
            flicker(n, s, age)
            act(n, s, controller)
            live(n, controller)
        }
    }

    /** NY4 / OPEN-NY7: from wave 16 some swap style twice on the way in, 4 and 8 ticks after spawning. */
    private fun flicker(n: NpcRef, s: Nylo, age: Int) {
        val f = s.flicker ?: return
        val step = age / FLICKER_EVERY
        if (age % FLICKER_EVERY != 0 || step !in 1 until FLICKER_STEPS) return
        s.styleNow = STYLE_CHARS.indexOf(f[step])
        val hp = n.hitpoints
        n.transform(id(s.mode, s.big, s.styleNow, s.aggro), restoreStats = false)
        n.hitpoints = hp
    }

    private fun act(n: NpcRef, s: Nylo, controller: NpcRef) {
        if (s.aggro) {
            val target = TfaKit.arena(n).minByOrNull { TfaKit.gap(n, it) } ?: return
            val reach = if (s.styleNow == MELEE) 1 else THROW_REACH
            if (TfaKit.gap(n, target) > reach) {
                n.walkTo(target.coord)
            } else if (n.tick >= s.nextBite) {
                s.nextBite = n.tick + ATTACK_SPEED
                hitRaider(n, s, target)
            }
            return
        }
        val pillars =
            n.npcsWithin(n.coord, PILLAR_SEEK).filter {
                it.type in PILLAR_IDS && it.hitpoints > 0 &&
                    TobNylocasRoom.controllerOf(it) == controller
            }
        val pillar = pillars.minByOrNull { TfaKit.gap(it, n) } ?: return
        if (TfaKit.gap(pillar, n) > 1) {
            n.walkTo(pillar.coord)
        } else if (!n.isFrozen && n.tick >= s.nextBite) {
            // Capture: a bite every 3 ticks per Nylocas (no splat; OPEN-NY12: 1 a small one, 2 a large one).
            s.nextBite = n.tick + ATTACK_SPEED
            val bite = if (s.big) 2 else 1
            pillar.hitpoints = maxOf(0, pillar.hitpoints - bite)
            if (pillar.hitpoints == 0) TobNylocasRoom.collapse(pillar)
        }
    }

    private fun hitRaider(n: NpcRef, s: Nylo, p: PlayerRef) {
        val max =
            when {
                s.mode == TobModes.ENTRY -> if (s.big) ENTRY_BIG_MAX else ENTRY_SMALL_MAX
                s.big -> BIG_MAX
                else -> SMALL_MAX
            }
        val type = ATTACK_TYPES[s.styleNow]
        n.anim(ATTACK_SEQS[s.styleNow])
        // Capture: thrown from at most 2 tiles, 10 cycles a tile.
        val delay = if (s.styleNow == MELEE) 0 else maxOf(1, TfaKit.gap(n, p) * FLIGHT_PER_TILE / CYCLES_PER_TICK + 1)
        if (s.styleNow != MELEE) BossCombat.projectile(n, p, SpotAnims.TOB_NYLOCAS_RANGEDPROJECTILE_SIZE1, delay)
        BossCombat.strike(n, p, type, max, delay)
    }

    /** NY6: 50+ ticks of life end in an explosion on everyone within 2 (small 18, large 21; Entry about 8). */
    private fun explode(n: NpcRef, s: Nylo) {
        n.anim(DETONATE_SEQS[s.styleNow])
        val max =
            if (s.mode == TobModes.ENTRY) {
                ENTRY_EXPLODE_MAX
            } else if (s.big) {
                BIG_EXPLODE_MAX
            } else {
                SMALL_EXPLODE_MAX
            }
        for (p in TfaKit.arena(n)) {
            if (TfaKit.gap(n, p) <=
                EXPLODE_REACH
            ) {
                CombatSystem.applyHit(p, n, BossCombat.roll(n, 1, max), HitStyle.TYPELESS)
            }
        }
        n.despawn()
    }

    /** NY3: only its own style hurts it; a wrong-style attacker is immune from then on (Hard: the hit comes back). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = npc[NYLO] ?: return damage
        val p = source as? PlayerRef ?: return damage
        if (p.username in s.immune) return 0
        if (style == STYLE_HITS[s.styleNow]) return damage
        s.immune += p.username
        if (s.mode == TobModes.HARD && damage > 0) CombatSystem.applyHit(p, npc, minOf(damage, npc.hitpoints), HitStyle.TYPELESS)
        return 0
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) = Unit

    /** NY5 / capture: a large one leaves two small ones of any style 7 ticks after it dies. */
    override fun died(npc: NpcRef, killer: EntityRef?) {
        val s = npc[NYLO] ?: return
        if (!s.big) return
        val controller = npc[Bosses.OWNER] ?: return
        val at = npc.coord
        controller.queueHit(SPLIT_AFTER, strong = false) {
            if (!controller.isInWorld) return@queueHit
            repeat(2) { k ->
                val style = STYLE_CHARS[controller.random(STYLE_CHARS.length)]
                spawn(controller, s.mode, CoordGrid(at.x + k, at.z, at.level), "S$style")
            }
        }
    }

    // ---- Vasilias / Prinkipas ids

    fun vasiliasSpawn(mode: Int): Int =
        when (mode) {
            TobModes.ENTRY -> Npcs.NYLOCAS_BOSS_SPAWNING_STORY
            TobModes.HARD -> Npcs.NYLOCAS_BOSS_SPAWNING_HARD
            else -> Npcs.NYLOCAS_BOSS_SPAWNING
        }

    /** Client contract 4: Prinkipas spawning 10803, melee 10804, magic 10805, ranged 10806. */
    const val PRINKIPAS_SPAWN = 10803
    val PRINKIPAS = setOf(10803, 10804, 10805, 10806)

    private const val ENTRY_BASE = 10774
    private const val HARD_BASE = 10791
    const val ENTRY_PILLAR = 10790
    const val HARD_PILLAR = 10811
    private const val BIG_OFFSET = 3
    private const val FIGHTING_OFFSET = 6

    val WAVE_IDS: Set<Int> get() = NYLO_WAVE_IDS
    private val PILLAR_IDS = setOf(Npcs.TOB_NYLOCAS_SUPPORT, ENTRY_PILLAR, HARD_PILLAR)

    /** Ids order melee, ranged, magic (Ischyros, Toxobolos, Hagios). */
    private const val STYLE_CHARS = "ith"
    private const val MELEE = 0
    private val STYLE_HITS = listOf(HitStyle.MELEE, HitStyle.RANGED, HitStyle.MAGIC)
    private val ATTACK_TYPES = listOf(AttackType.STAB, AttackType.RANGED, AttackType.MAGIC)
    private val ATTACK_SEQS = intArrayOf(Seqs.TOP_SPIDER_MELEE_ATTACK, Seqs.TOP_SPIDER_RANGED_ATTACK, Seqs.TOP_SPIDER_MAGIC_ATTACK)
    private val DETONATE_SEQS =
        intArrayOf(Seqs.TOP_SPIDER_MELEE_DEATH_DETONATE, Seqs.TOP_SPIDER_RANGED_DEATH_DETONATE, Seqs.TOP_SPIDER_MAGIC_DEATH_DETONATE)
    private const val FLICKER_STEPS = 3
    private const val FLICKER_EVERY = 4
    private const val SMALL_LIFE = 52
    private const val BIG_LIFE = 55
    private const val SPLIT_AFTER = 7
    private const val ATTACK_SPEED = 3
    private const val THROW_REACH = 2
    private const val PILLAR_SEEK = 30
    private const val FLIGHT_PER_TILE = 10
    private const val CYCLES_PER_TICK = 30
    private const val SMALL_MAX = 17
    private const val BIG_MAX = 24
    private const val ENTRY_SMALL_MAX = 5
    private const val ENTRY_BIG_MAX = 10
    private const val SMALL_EXPLODE_MAX = 18
    private const val BIG_EXPLODE_MAX = 21
    private const val ENTRY_EXPLODE_MAX = 8
    private const val EXPLODE_REACH = 2
}

/**
 * Nylocas Vasilias (and Hard's Prinkipas): NY10-NY12, capture: it lands from its spawning form, then changes form every
 * 10 ticks (Entry 15) to one of the other two styles, two attacks a form 4 ticks apart (the first 1 or 4 ticks after
 * landing); melee only beside its target. A wrong-style hit is reflected on the attacker and heals it as much. Max 70
 * (Entry 24, Hard 105; the Prinkipas 38); through the right prayer 17, melee fully blocked. Hard: the ranged attack
 * splashes everyone within 2 of the target; the magic one bounces through the rest of the party (half within 2 tiles,
 * full beyond, at most 75) unless they all stand on one tile.
 */
internal object TobVasilias : Boss(*VASILIAS_IDS) {
    private class V(val mode: Int, val spawnType: Int, val prince: Boolean) {
        var form = 0
        var formSince = 0L
        var attacksThisForm = 0
    }

    private val V_STATE = AttrKey<V>("tob_vasilias")

    override fun spawned(npc: NpcRef) {
        if (npc[V_STATE] != null) return
        val prince = npc.type in TobNylo.PRINKIPAS
        val v = V(TobModes.of(npc.type), npc.type, prince)
        npc[V_STATE] = v
        npc.huntRange = HUNT
        npc.anim(Seqs.NYLOCAS_QUEEN_SUMMON)
        npc.setAttackClock(npc.tick + LAND + if (npc.random(2) == 0) 1 else FIRST_LATE)
        npc.queueHit(LAND, strong = false) {
            if (!npc.isInWorld) return@queueHit
            setForm(npc, v, MELEE_FORM)
            loop(npc, v)
        }
    }

    private fun setForm(npc: NpcRef, v: V, form: Int) {
        v.form = form
        v.formSince = npc.tick
        v.attacksThisForm = 0
        val hp = npc.hitpoints
        npc.transform(v.spawnType + form, restoreStats = false)
        npc.hitpoints = hp
    }

    private fun loop(npc: NpcRef, v: V) {
        npc.queueHit(1, strong = false) {
            if (!npc.isInWorld) return@queueHit
            val every = if (v.mode == TobModes.ENTRY) ENTRY_SWITCH else SWITCH
            if (npc.tick - v.formSince >= every) {
                val others = (MELEE_FORM..RANGED_FORM).filter { it != v.form }
                setForm(npc, v, others[npc.random(others.size)])
            }
            loop(npc, v)
        }
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val v = npc[V_STATE] ?: return
        if (npc.type == v.spawnType || v.attacksThisForm >= ATTACKS_PER_FORM) return
        if (v.form == MELEE_FORM && distance) return
        v.attacksThisForm++
        npc.setAttackClock(npc.tick + ATTACK_SPEED)
        val max =
            when {
                v.prince -> PRINCE_MAX
                v.mode == TobModes.ENTRY -> ENTRY_MAX
                v.mode == TobModes.HARD -> HARD_MAX
                else -> NORMAL_MAX
            }
        val style = FORM_STYLES[v.form - 1]
        npc.anim(FORM_SEQS[v.form - 1])
        CombatSystem.announceAttack(npc, victim, style)
        val delay = if (v.form == MELEE_FORM) 0 else BossCombat.delay(npc, victim, style)
        if (v.form != MELEE_FORM) BossCombat.projectile(npc, victim, SpotAnims.TOB_NYLOCAS_RANGEDPROJECTILE_SIZE2, delay)
        val p = victim as? PlayerRef ?: return
        val raw = BossCombat.roll(npc, 0, max)
        val dmg =
            when {
                !p.protects(style) -> raw
                v.form == MELEE_FORM -> 0
                else -> minOf(raw, PRAYED_MAX)
            }
        CombatSystem.queueHit(p, npc, dmg, CombatSystem.hitStyle(style), delay)
        if (v.mode == TobModes.HARD && !v.prince) hardSplash(npc, v, p, raw, delay)
    }

    private fun hardSplash(npc: NpcRef, v: V, target: PlayerRef, raw: Int, delay: Int) {
        val others = TfaKit.arena(npc).filter { it != target }
        when (v.form) {
            RANGED_FORM -> {
                for (o in others) {
                    if (o.distanceTo(target) <=
                        SPLASH_RADIUS
                    ) {
                        CombatSystem.queueHit(o, npc, raw, HitStyle.RANGED, delay)
                    }
                }
            }

            MAGIC_FORM -> {
                if (others.all { it.coord == target.coord }) return
                for (o in others) {
                    val d = if (o.distanceTo(target) <= SPLASH_RADIUS) raw / 2 else raw
                    CombatSystem.queueHit(o, npc, minOf(BOUNCE_CAP, d), HitStyle.TYPELESS, delay + 1)
                }
            }
        }
    }

    /** NY11: a wrong-style hit is reflected on the attacker and heals it as much. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val v = npc[V_STATE] ?: return damage
        val p = source as? PlayerRef ?: return damage
        if (npc.type == v.spawnType) return 0
        if (style == CombatSystem.hitStyle(FORM_STYLES[v.form - 1])) return damage
        if (damage > 0) {
            CombatSystem.applyHit(p, npc, damage, HitStyle.TYPELESS)
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + damage)
        }
        return 0
    }

    private val FORM_STYLES = listOf(CombatStyle.MELEE, CombatStyle.MAGIC, CombatStyle.RANGED)
    private val FORM_SEQS = intArrayOf(Seqs.TOP_SPIDER_MELEE_ATTACK, Seqs.TOP_SPIDER_MAGIC_ATTACK, Seqs.TOP_SPIDER_RANGED_ATTACK)
    private const val MELEE_FORM = 1
    private const val MAGIC_FORM = 2
    private const val RANGED_FORM = 3
    private const val HUNT = 20

    /** OPEN-NY15: ticks from its drop to landing. */
    private const val LAND = 3
    private const val FIRST_LATE = 4
    private const val SWITCH = 10
    private const val ENTRY_SWITCH = 15
    private const val ATTACKS_PER_FORM = 2
    private const val ATTACK_SPEED = 4
    private const val NORMAL_MAX = 70
    private const val ENTRY_MAX = 24
    private const val HARD_MAX = 105
    private const val PRINCE_MAX = 38
    private const val PRAYED_MAX = 17

    /** OPEN-NY9: the Hard ranged splash radius (Vasilias page 2, Hard page 3). */
    private const val SPLASH_RADIUS = 2

    /** 9 Jun 2021: the Hard magic split damage is capped at 75. */
    private const val BOUNCE_CAP = 75
}
