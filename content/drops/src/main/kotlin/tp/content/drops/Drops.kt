package tp.content.drops

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.util.CoordGrid

/**
 * NPC drops (docs/mechanics/drops.md D1-D12): when an NPC dies, its Wiki drop table is rolled for the player who gets the
 * loot ([DeathEvent.lootFor]) with the world RNG, and the items are created on the ground at the NPC's tile, private to
 * that player first (ground-items.md G1-G3, D12). Player deaths are not NPC drops and are ignored.
 */
public class Drops : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart { check(DropData.npcCount > 0) { "npc-drops.tsv is empty" } }
        events.on<DeathEvent> { e ->
            val npc = e.entity as? NpcRef ?: return@on
            // OPEN-D9: no player to give the loot to (killed by another NPC, say), no drop.
            val killer = e.lootFor ?: return@on
            val drops = DropData.of(npc.type) ?: return@on
            DropRoll(npc, killer).run(drops)
        }
    }
}

/** An obj stack waiting to be created on the ground. */
private class Loot(val obj: Int, val count: Int)

/** Rolls one NPC death: the drop kinds in the Wiki's order (D2-D7), then creates the ground objs in one transaction. */
private class DropRoll(private val npc: NpcRef, private val killer: PlayerRef) {
    private val loot = ArrayList<Loot>()
    private val ring: Boolean by lazy { wearsRingOfWealth() }

    fun run(d: NpcDrops) {
        d.always.forEach { resolve(it, 0) }
        // D3: a pre-roll hit replaces the main roll; otherwise the main table is rolled.
        val preHit = d.pre.firstOrNull { hit(it.chance) }
        if (preHit != null) {
            resolve(preHit, 0)
        } else {
            repeat(d.main.maxOfOrNull { it.rolls } ?: 0) { pick(d.main)?.let { resolve(it, 0) } }
            d.independent.filter { hit(it.chance) }.forEach { resolve(it, 0) }
        }
        // D7: secondary drops are one more roll beside the main one.
        pick(d.secondary)?.let { resolve(it, 0) }
        var longBone = false
        for (line in d.tertiary) {
            if (!hit(line.chance)) continue
            val name = if (line.obj >= 0) killer.objName(line.obj) else ""
            // D6: the curved bone is only rolled when the long bone was not given.
            if (name == CURVED_BONE && longBone) continue
            if (name == LONG_BONE) longBone = true
            resolve(line, 0)
        }
        create()
    }

    /** Whether a roll of [chance] succeeds: uniform in [0, [RESOLUTION]) against the chance scaled to it. */
    private fun hit(chance: Double): Boolean = npc.random(RESOLUTION) < chance * RESOLUTION

    /**
     * One roll over [lines] whose chances are exclusive (D2): the line whose cumulative range holds the roll, or null for
     * the empty remainder. A worn ring of wealth removes the empty slots of the gem and mega-rare tables (D9), so the
     * roll then spans only what is left.
     */
    private fun pick(lines: List<DropLine>): DropLine? {
        if (lines.isEmpty()) return null
        val removed = ring && lines.any { it.ringRemoves }
        var total = 0.0
        for (l in lines) if (!(removed && l.ringRemoves)) total += l.chance
        val span = if (removed) total else maxOf(total, 1.0)
        val target = npc.random(RESOLUTION).toDouble() / RESOLUTION * span
        var cumulative = 0.0
        for (l in lines) {
            if (removed && l.ringRemoves) continue
            cumulative += l.chance
            if (target < cumulative) return l
        }
        return null
    }

    private fun resolve(line: DropLine, depth: Int) {
        val table = line.table
        if (table != null) {
            if (depth < MAX_TABLE_DEPTH) DropData.table(table)?.let { pick(it) }?.let { resolve(it, depth + 1) }
            return
        }
        if (line.obj == DropData.NOTHING) return
        // D10: underground monsters in the chaos talisman region drop the chaos talisman instead of the nature one.
        val obj = if (line.undergroundObj >= 0 && inChaosRegion(npc.coord)) line.undergroundObj else line.obj
        add(obj, quantity(line), line.noted)
    }

    /** Every listed integer is equally likely (D4); a single value costs no random draw. */
    private fun quantity(line: DropLine): Int {
        val r = line.ranges
        var total = 0
        for (i in r.indices step 2) total += r[i + 1] - r[i] + 1
        if (total == 1) return r[0]
        var pick = npc.random(total)
        for (i in r.indices step 2) {
            val size = r[i + 1] - r[i] + 1
            if (pick < size) return r[i] + pick
            pick -= size
        }
        return r[0]
    }

    private fun add(obj: Int, count: Int, noted: Boolean) {
        val id = if (noted) killer.noted(obj).takeIf { it >= 0 } ?: obj else obj
        // D12: an obj on the ground is one stack; unstackable objs drop one by one.
        if (killer.objStackable(id) || count == 1) {
            loot += Loot(id, count)
        } else {
            repeat(count) { loot += Loot(id, 1) }
        }
    }

    private fun create() {
        if (loot.isEmpty()) return
        val at = npc.coord
        killer.transaction("npc drop") { loot.all { createOnGround(it.obj, it.count, at, killer.username) } }
    }

    private fun wearsRingOfWealth(): Boolean {
        val worn = killer.inv(Invs.WORN).obj(RING_SLOT)
        return worn >= 0 && killer.objName(worn).startsWith(RING_OF_WEALTH, ignoreCase = true)
    }
}

/** Probability resolution of a drop roll: 2^30 steps (every Wiki chance is a fraction with a far smaller denominator). */
private const val RESOLUTION = 1 shl 30

/** Shared tables nest at most gem -> mega-rare (and rare -> gem -> mega-rare); this stops a malformed cycle. */
private const val MAX_TABLE_DEPTH = 4

/** Worn-inventory slot of the ring (cache wear position 12). */
private const val RING_SLOT = 12

/** Cache name prefix of every ring of wealth (charged, imbued, uncharged); Wiki "Gem drop table": ring removes empties. */
private const val RING_OF_WEALTH = "Ring of wealth"

/** Cache names of the two bone tertiaries (Wiki "Drop table": curved bone only if no long bone). */
private const val LONG_BONE = "Long bone"
private const val CURVED_BONE = "Curved bone"

/** Region where the gem table gives the chaos talisman (Wiki "Gem drop table#Talisman" map polygon 1920,9474 to 3903,11263). */
private fun inChaosRegion(at: CoordGrid): Boolean = at.x in CHAOS_X && at.z in CHAOS_Z

private val CHAOS_X = 1920..3903
private val CHAOS_Z = 9474..11263
