package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.Teleported
import tp.plugin.TimerId
import tp.plugin.WorldRef
import tp.plugin.chatNpc
import tp.plugin.choice
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits
import tp.util.CoordGrid
import java.util.PriorityQueue

/** One ore vein tile from `motherlode-veins.tsv`: the depleted loc the cache places there (the active one is 4 ids lower). */
internal class VeinSite(val depleted: Int, val at: CoordGrid, val angle: Int, val upper: Boolean) {
    val active: Int get() = depleted - ACTIVE_OFFSET

    companion object {
        /** Cache loc ids: depleted vein 26665..26668 pairs with the ore vein 26661..26664. */
        const val ACTIVE_OFFSET = 4
    }
}

/** A vein waiting to respawn at [dueTick]. */
internal class Respawn(val dueTick: Long, val site: VeinSite) : Comparable<Respawn> {
    override fun compareTo(other: Respawn): Int = dueTick.compareTo(other.dueTick)
}

/**
 * The Motherlode Mine's shared state, one per world (docs/mechanics/motherlode.md). Nothing here runs by itself: the players
 * inside the mine advance it every tick ([Motherlode.advance]), and a returning player finds it caught up.
 */
internal class MotherlodeWorld(now: Long, val sites: Map<Int, VeinSite>) {
    /** Tick each active vein is due to deplete, by packed coord; set by the first pay-dirt mined from it. */
    val deadline = HashMap<Int, Long>()
    val respawns = PriorityQueue<Respawn>()
    val strutBroken = BooleanArray(2)
    val strutBreakAt = LongArray(2)
    var lastAdvance = -1L

    init {
        for (i in strutBreakAt.indices) strutBreakAt[i] = now + Motherlode.breakDelay(null)
    }

    fun schedule(tick: Long, site: VeinSite) {
        respawns.add(Respawn(tick, site))
    }

    fun due(tick: Long): VeinSite? = respawns.peek()?.takeIf { it.dueTick <= tick }?.let { respawns.poll().site }
}

/** What Mining.kt asks of the mine, plus the shared pieces (docs/mechanics/motherlode.md ML1-ML4). */
internal object Motherlode {
    const val COAL = 0
    const val GOLD = 1
    const val MITHRIL = 2
    const val ADAMANT = 3
    const val RUNE = 4
    const val NUGGET = 5
    const val KINDS = 6

    private const val BITS = 8
    private const val MASK = 0xFF

    /** Saved values (all per player). */
    const val SACK_KEY = "motherlode.sack"
    const val TRAY_KEY = "motherlode.tray"
    const val UPPER_KEY = "motherlode.upper"
    const val HOPPER_KEY = "motherlode.hopper"
    const val BIGGER_KEY = "motherlode.bigger"

    val WORLD = AttrKey<MotherlodeWorld>("motherlode.world")

    /** Ore counts packed 8 bits each (capacity 189 < 256). */
    fun count(packed: Long, kind: Int): Int = ((packed shr (kind * BITS)) and MASK.toLong()).toInt()

    fun withCount(packed: Long, kind: Int, value: Int): Long =
        (packed and (MASK.toLong() shl (kind * BITS)).inv()) or (value.toLong() shl (kind * BITS))

    fun total(packed: Long): Int = (0 until KINDS).sumOf { count(packed, it) }

    fun capacity(p: PlayerRef): Int =
        if ((p.saved(BIGGER_KEY) ?: 0L) != 0L) MotherlodeData.SACK_CAPACITY_UPGRADED else MotherlodeData.SACK_CAPACITY

    /** Pay-dirt in the sack and in the tray, which counts against the capacity (Wiki "Hopper": overflow is not deposited). */
    fun held(p: PlayerRef): Int = total(p.saved(SACK_KEY) ?: 0L) + total(p.saved(TRAY_KEY) ?: 0L)

    fun breakDelay(p: PlayerRef?): Int {
        val rounds = MotherlodeData.STRUT_BREAK_ROUNDS
        val pick = p?.random(rounds.last - rounds.first + 1) ?: 0
        return (rounds.first + pick) * MotherlodeData.STRUT_ROUND_TICKS
    }

    fun world(p: PlayerRef): MotherlodeWorld =
        p.worldAttr(WORLD) ?: MotherlodeWorld(p.tick, MotherlodeVeins.load()).also {
            for (i in it.strutBreakAt.indices) it.strutBreakAt[i] = p.tick + breakDelay(p)
            p.setWorldAttr(WORLD, it)
        }

    /** Whether the sack (with the tray) is full: no more pay-dirt can be mined (Wiki "Motherlode Mine" > Pay-dirt). */
    fun sackFull(p: PlayerRef): Boolean {
        if (held(p) < capacity(p)) return false
        p.message("Some ore is ready to be collected from the sack. It's getting full.")
        return true
    }

    /**
     * Called after a pay-dirt was mined from the vein at [loc]: the first ore starts its timer; once it is up the vein is
     * depleted (a vein nobody is mining still gives one more ore, ML1). Returns whether it depleted.
     */
    fun veinDepletes(p: PlayerRef, loc: LocRef, placed: RockLoc): Boolean {
        val w = world(p)
        val key = loc.coord.packed
        val site = w.sites[key] ?: return true.also { p.addLoc(placed.empty, loc.coord, loc.shape, loc.angle, placed.rock.respawn) }
        val now = p.tick
        val due =
            w.deadline.getOrPut(key) {
                val range = if (site.upper) MotherlodeData.UPPER_VEIN_TICKS else MotherlodeData.LOWER_VEIN_TICKS
                now + range.first + p.random(range.last - range.first + 1)
            }
        if (now < due) return false
        w.deadline.remove(key)
        p.addLoc(site.depleted, site.at, 0, site.angle)
        w.schedule(now + MotherlodeData.VEIN_RESPAWN_TICKS, site)
        return true
    }

    /** Applies the due vein respawns and strut breaks of [w] using [p] to change locs (once per tick). */
    fun advance(p: PlayerRef, w: MotherlodeWorld) {
        val now = p.tick
        if (w.lastAdvance == now) return
        w.lastAdvance = now
        while (true) {
            val site = w.due(now) ?: break
            p.addLoc(site.active, site.at, 0, site.angle)
        }
        for (i in 0..1) {
            if (!w.strutBroken[i] && now >= w.strutBreakAt[i]) setStrut(p, w, i, broken = true)
        }
    }

    /** Both wheels broken stops the water (Wiki "Strut"): the tray does not move. */
    fun flowing(w: MotherlodeWorld): Boolean = !(w.strutBroken[0] && w.strutBroken[1])

    fun setStrut(p: PlayerRef, w: MotherlodeWorld, i: Int, broken: Boolean) {
        w.strutBroken[i] = broken
        MotherlodeData.let { d ->
            if (broken) {
                p.deleteLoc(d.STRUT_FIXED_AT[i], STRUT_SHAPE)
                p.addLoc(Locs.MOTHERLODE_WHEEL_STRUT_BROKEN, d.STRUT_BROKEN_AT[i], STRUT_SHAPE, 0)
                p.addLoc(Locs.MOTHERLODE_WHEEL_BROKEN, d.WHEEL_AT[i], STRUT_SHAPE, WHEEL_ANGLE)
            } else {
                p.deleteLoc(d.STRUT_BROKEN_AT[i], STRUT_SHAPE)
                p.addLoc(Locs.MOTHERLODE_WHEEL_STRUT_FIXED, d.STRUT_FIXED_AT[i], STRUT_SHAPE, WHEEL_ANGLE)
                p.addLoc(Locs.MOTHERLODE_WHEEL_FIXED, d.WHEEL_AT[i], STRUT_SHAPE, WHEEL_ANGLE)
            }
        }
    }

    /** Cache shape 10 (centrepiece) of the strut and wheel locs, and the angle 2 the cache places them at. */
    private const val STRUT_SHAPE = 10
    private const val WHEEL_ANGLE = 2
}

/** Loads the vein tiles baked from the cache (`tools/cache-dump map 58 88`, every `Depleted vein` loc 26665..26668). */
internal object MotherlodeVeins {
    private const val COLUMNS = 5

    fun load(): Map<Int, VeinSite> {
        val text = MotherlodeVeins::class.java.getResource("motherlode-veins.tsv")?.readText() ?: error("motherlode-veins.tsv missing")
        val out = HashMap<Int, VeinSite>()
        for (line in text.lineSequence().drop(1)) {
            val c = line.split('\t')
            if (c.size < COLUMNS) continue
            val at = CoordGrid(c[1].toInt(), c[2].toInt(), 0)
            out[at.packed] = VeinSite(c[0].toInt(), at, c[3].toInt(), c[4] == "1")
        }
        return out
    }
}

/**
 * The Motherlode Mine (docs/mechanics/motherlode.md): ore veins (mined through [Mining]), the hopper, the water wheels and
 * their struts, the sack, the upper floor ladder, Prospector Percy's upgrades and the HUD.
 */
public class MotherlodeMine : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart { activateVeins(this) }
        onLogin {
            syncVarbits(this)
            if (MotherlodeData.inMine(coord)) enter(this)
        }
        onLogout { leave(this) }
        events.on<Teleported> { e -> if (MotherlodeData.inMine(e.to)) enter(e.player) else leave(e.player) }
        onOpLoc(Locs.MOTHERLODE_HOPPER, OP_FIRST) { loc -> deposit(player, loc) }
        onOpLoc(Locs.MOTHERLODE_SACK_EMPTY, OP_FIRST) { search(player) }
        onOpLoc(Locs.MOTHERLODE_SACK_FULL, OP_FIRST) { search(player) }
        onOpLoc(Locs.MOTHERLODE_WHEEL_STRUT_BROKEN, OP_FIRST) { loc -> repair(loc) }
        onUseOnLoc(Objs.HAMMER, Locs.MOTHERLODE_WHEEL_STRUT_BROKEN) { loc -> repair(loc) }
        onUseOnLoc(Objs.IMCANDO_HAMMER, Locs.MOTHERLODE_WHEEL_STRUT_BROKEN) { loc -> repair(loc) }
        onOpLoc(Locs.MOTHERLODE_LADDER_BOTTOM_ACTIVE, OP_FIRST) { climbUp(player) }
        onOpLoc(Locs.MOTHERLODE_LADDER_TOP_ACTIVE, OP_FIRST) { climbDown(player) }
        onOpNpc(Npcs.MOTHERLODE_PERCY, OP_FIRST) { npc -> talkToPercy(npc) }
    }

    // ---- world start

    /** The cache places every vein depleted and both strut states; veins start active and the machine intact. */
    private fun activateVeins(world: WorldRef) {
        for (site in MotherlodeVeins.load().values) world.addLoc(site.active, site.at, 0, site.angle)
        for (i in 0..1) {
            world.deleteLoc(MotherlodeData.STRUT_BROKEN_AT[i], STRUT_SHAPE)
            world.addLoc(Locs.MOTHERLODE_WHEEL_FIXED, MotherlodeData.WHEEL_AT[i], STRUT_SHAPE, WHEEL_ANGLE)
        }
    }

    // ---- inside the mine: HUD and the per-tick drive

    private fun syncVarbits(p: PlayerRef) {
        p.setVarbit(Varbits.MOTHERLODE_BIGGERSACK, if ((p.saved(Motherlode.BIGGER_KEY) ?: 0L) != 0L) 1 else 0)
        p.setVarbit(Varbits.MOTHERLODE_IN_RESTRICTED_AREA, if ((p.saved(Motherlode.UPPER_KEY) ?: 0L) != 0L) 0 else 1)
        p.setVarbit(Varbits.MOTHERLODE_SACK_TRANSMIT, Motherlode.total(p.saved(Motherlode.SACK_KEY) ?: 0L))
    }

    private fun enter(p: PlayerRef) {
        if (p.hasTimer(TICK_TIMER)) return
        p[ARRIVE_AT] = p.tick + MotherlodeData.FLOW_TICKS
        p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.MOTHERLODE_HUD)
        syncVarbits(p)
        p.timer(TICK_TIMER, 1, soft = true) { tick() }
    }

    private fun leave(p: PlayerRef) {
        if (!p.hasTimer(TICK_TIMER)) return
        p.stopTimer(TICK_TIMER)
        p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
    }

    private fun PlayerRef.tick() {
        if (!MotherlodeData.inMine(coord)) return leave(this)
        val w = Motherlode.world(this)
        Motherlode.advance(this, w)
        val tray = saved(Motherlode.TRAY_KEY) ?: 0L
        if (tray != 0L && Motherlode.flowing(w) && tick >= (this[ARRIVE_AT] ?: 0L)) deliver(this, tray)
    }

    // ---- hopper, tray and sack

    private fun deposit(p: PlayerRef, loc: LocRef) {
        enter(p)
        val upper = loc.coord == MotherlodeData.UPPER_HOPPER
        if (upper && (p.saved(Motherlode.HOPPER_KEY) ?: 0L) == 0L) {
            return p.message("You need to ask Prospector Percy for access to the upper hopper first.")
        }
        val inv = p.inv(Invs.INV)
        val have = inv.total(Objs.PAYDIRT).toInt()
        if (have == 0) return p.message("You don't have any pay-dirt to deposit.")
        val room = Motherlode.capacity(p) - Motherlode.held(p)
        if (room <= 0) {
            return p.message("Some ore is ready to be collected from the sack. It's getting full.")
        }
        val n = minOf(have, room)
        if (!p.transaction("motherlode deposit") { remove(Invs.INV, Objs.PAYDIRT, n) }) return
        p.setSaved(Motherlode.TRAY_KEY, rollOres(p, n, p.saved(Motherlode.TRAY_KEY) ?: 0L))
        p[ARRIVE_AT] = p.tick + MotherlodeData.FLOW_TICKS
        if (n < have) p.message("The sack cannot hold any more pay-dirt.")
    }

    /**
     * Rolls the ore of [n] pay-dirt into [tray] with the player's current Mining level (OPEN-ML3: the Wiki says the ore is fixed
     * when the pay-dirt is mined; the stack of identical pay-dirt cannot carry it). A nugget first (1 in 32, at most 3 a
     * deposit), then runite, adamantite, mithril, gold and coal for what is left (Wiki "Motherlode Mine" cascade).
     */
    private fun rollOres(p: PlayerRef, n: Int, tray: Long): Long {
        var packed = tray
        var nuggets = 0
        val level = p.level(Stats.MINING)
        val diary = p.varbit(Varbits.FALADOR_DIARY_ELITE_COMPLETE) != 0
        repeat(n) {
            var kind = COAL_KIND
            if (nuggets < MotherlodeData.NUGGETS_PER_DEPOSIT && p.random(MotherlodeData.NUGGET_ONE_IN) == 0) {
                kind = Motherlode.NUGGET
                nuggets++
            } else {
                for (k in Motherlode.RUNE downTo Motherlode.GOLD) {
                    val ore = MotherlodeData.ORES[k]
                    if (level < ore.level) continue
                    // The diary adds percentage points to the chance as a second independent roll (OPEN-ML7: approximation).
                    if (p.statRandom(Stats.MINING, ore.low, ore.high) || (diary && p.random(PERCENT) < ore.diaryPoints)) {
                        kind = k
                        break
                    }
                }
            }
            packed = Motherlode.withCount(packed, kind, Motherlode.count(packed, kind) + 1)
        }
        return packed
    }

    /** The tray reaches the sack: its ores are added and the bonus xp is given (Wiki "Pay-dirt": bonus xp when it reaches the sack). */
    private fun deliver(p: PlayerRef, tray: Long) {
        var sack = p.saved(Motherlode.SACK_KEY) ?: 0L
        var xp = 0.0
        for (k in 0 until Motherlode.KINDS) {
            val c = Motherlode.count(tray, k)
            sack = Motherlode.withCount(sack, k, Motherlode.count(sack, k) + c)
            xp += c * MotherlodeData.ORES[k].bonusXp
        }
        p.setSaved(Motherlode.SACK_KEY, sack)
        p.setSaved(Motherlode.TRAY_KEY, null)
        p.setVarbit(Varbits.MOTHERLODE_SACK_TRANSMIT, Motherlode.total(sack))
        if (xp > 0) MiningOutfit.addXp(p, xp)
    }

    /** Search: ores leave the sack in tier order, a random amount of each tier at a time, until the backpack is full (Mod Ash). */
    private fun search(p: PlayerRef) {
        enter(p)
        var sack = p.saved(Motherlode.SACK_KEY) ?: 0L
        if (Motherlode.total(sack) == 0) return p.message("The sack is empty.")
        var moved = true
        while (moved && Motherlode.total(sack) > 0) {
            moved = false
            for (k in intArrayOf(
                Motherlode.NUGGET,
                Motherlode.COAL,
                Motherlode.GOLD,
                Motherlode.MITHRIL,
                Motherlode.ADAMANT,
                Motherlode.RUNE,
            )) {
                val left = Motherlode.count(sack, k)
                if (left == 0) continue
                val obj = MotherlodeData.ORES[k].obj
                val free = if (k == Motherlode.NUGGET) left else minOf(left, p.inv(Invs.INV).freeSlots)
                if (free == 0) continue
                val take = minOf(free, 1 + p.random(left))
                if (!p.transaction("motherlode sack") { add(Invs.INV, obj, take) }) continue
                sack = Motherlode.withCount(sack, k, left - take)
                moved = true
            }
        }
        p.setSaved(Motherlode.SACK_KEY, if (sack == 0L) null else sack)
        p.setVarbit(Varbits.MOTHERLODE_SACK_TRANSMIT, Motherlode.total(sack))
        if (Motherlode.total(sack) > 0) p.message("Your inventory is too full to hold any more ore.")
    }

    // ---- struts

    /** Repair: a hammer in the backpack or worn, a roll each tick on Smithing, 1.5 x the Smithing level xp (Wiki "Strut"). */
    private suspend fun PlayerScript.repair(loc: LocRef) {
        val p = player
        enter(p)
        val w = Motherlode.world(p)
        val i = if (loc.coord.z == MotherlodeData.STRUT_BROKEN_AT[1].z) 1 else 0
        if (!w.strutBroken[i]) return
        val inv = p.inv(Invs.INV)
        val worn = p.inv(Invs.WORN)
        val hammer =
            inv.total(Objs.HAMMER) > 0 || inv.total(Objs.IMCANDO_HAMMER) > 0 ||
                worn.total(Objs.IMCANDO_HAMMER) > 0 || worn.total(Objs.IMCANDO_HAMMER_OFFHAND) > 0
        if (!hammer) return p.message("You need a hammer to fix this.")
        keepInteraction()
        if (p.tick % REPAIR_ANIM_TICKS == 0L) p.anim(Seqs.HUMAN_SMITHING)
        if (!p.statRandom(Stats.SMITHING, MotherlodeData.STRUT_LOW, MotherlodeData.STRUT_HIGH)) return
        Motherlode.setStrut(p, w, i, broken = false)
        w.strutBreakAt[i] = p.tick + Motherlode.breakDelay(p)
        p.addXp(Stats.SMITHING, p.level(Stats.SMITHING) * MotherlodeData.STRUT_XP_PER_LEVEL)
        p.message("You repair the broken strut.")
        p.anim(-1)
        p.stopInteraction()
    }

    // ---- ladder and Prospector Percy

    private fun climbUp(p: PlayerRef) {
        if ((p.saved(Motherlode.UPPER_KEY) ?: 0L) ==
            0L
        ) {
            return p.message("You need to ask Prospector Percy for access to the upper level first.")
        }
        if (p.baseLevel(Stats.MINING) < MotherlodeData.UPPER_FLOOR_LEVEL) {
            return p.message("You need a Mining level of ${MotherlodeData.UPPER_FLOOR_LEVEL} to use the upper level.")
        }
        p.teleport(MotherlodeData.UPPER_LANDING)
    }

    private fun climbDown(p: PlayerRef) = p.teleport(MotherlodeData.LOWER_LANDING)

    /** Percy's one-time purchases (Wiki "Prospector Percy" > Other items); the shop itself is the Trade option. */
    private suspend fun PlayerScript.talkToPercy(npc: NpcRef) {
        val p = player
        chatNpc(npc, "Welcome to the Motherlode Mine. I can sell you upgrades for golden nuggets, or you can trade with me.")
        when (
            choice(
                "Which upgrade?",
                "Upper level (${MotherlodeData.UPPER_FLOOR_COST} nuggets)",
                "Upper level hopper (${MotherlodeData.UPPER_HOPPER_COST} nuggets)",
                "Larger sack (${MotherlodeData.BIGGER_SACK_COST} nuggets)",
                "Never mind",
            )
        ) {
            1 -> buy(p, Motherlode.UPPER_KEY, MotherlodeData.UPPER_FLOOR_COST, MotherlodeData.UPPER_FLOOR_LEVEL)
            2 -> buy(p, Motherlode.HOPPER_KEY, MotherlodeData.UPPER_HOPPER_COST, 0)
            3 -> buy(p, Motherlode.BIGGER_KEY, MotherlodeData.BIGGER_SACK_COST, 0)
            else -> Unit
        }
    }

    private fun buy(p: PlayerRef, key: String, cost: Int, baseLevel: Int) {
        if ((p.saved(key) ?: 0L) != 0L) return p.message("You have already bought that.")
        if (p.baseLevel(Stats.MINING) < baseLevel) return p.message("You need a base Mining level of $baseLevel for that.")
        if (!p.transaction("motherlode upgrade") { remove(Invs.INV, Objs.MOTHERLODE_NUGGET, cost) }) {
            return p.message("You need $cost golden nuggets for that.")
        }
        p.setSaved(key, 1L)
        syncVarbits(p)
        p.message("Percy takes your golden nuggets. Your upgrade is unlocked.")
    }

    private companion object {
        const val OP_FIRST = 1
        val TICK_TIMER = TimerId("motherlode")
        val ARRIVE_AT = AttrKey<Long>("motherlode.arrive")

        /** Index of coal in the packed counts: what a pay-dirt is when every roll above fails (Wiki: "Coal is actually a 100% drop"). */
        const val COAL_KIND = Motherlode.COAL

        /** Percentage points are out of 100. */
        const val PERCENT = 100

        /** The repair animation is replayed this often (OPEN-ML10: the real timing is not sourced). */
        const val REPAIR_ANIM_TICKS = 3L

        const val STRUT_SHAPE = 10
        const val WHEEL_ANGLE = 2
    }
}
