package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LootEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.WorldRef
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.util.CoordGrid

/**
 * One ascension rock tile the cache places in the Fractured Archive lobby: the inactive loc it ships with (`tools/cache-dump map 64 86`),
 * its tile and its angle. The active loc is the same variant 3 ids lower.
 */
internal class AscensionSite(val inactive: Int, val at: CoordGrid, val angle: Int) {
    val active: Int get() = inactive - ACTIVE_OFFSET

    companion object {
        /** Cache loc ids: inactive 63269..63271 pair with active 63266..63268 (`TFA_GATHERING_ROCK_*01..03`). */
        const val ACTIVE_OFFSET = Locs.TFA_GATHERING_ROCK_INACTIVE01 - Locs.TFA_GATHERING_ROCK_ACTIVE01
    }
}

/** A sprite NPC and the tick it goes away ([taken] once someone looked at it). */
internal class SpriteSlot(val npc: NpcRef, var dueTick: Long, var taken: Boolean)

/** The rocks' shared state, one per world (docs/mechanics/skills/ascension-rocks.md AR2). */
internal class AscensionWorld(var step: Int, var rotateAt: Long) {
    val sprites = ArrayList<SpriteSlot>()
}

/** What Mining.kt asks of the ascension rocks (sprite countdown, pet roll) and the shared constants. */
internal object AscensionRocks {
    val WORLD = AttrKey<AscensionWorld>("ascension.world")

    /** Mining ticks left until this player's next sprite. */
    private val SPRITE_LEFT = AttrKey<Int>("ascension.sprite")

    /** Wiki "Ascension rocks": three rocks are active at a time. */
    const val ACTIVE_COUNT = 3

    /** Wiki "Ascension rocks": "they deplete every 295 ticks"; the Wiki's money-making guide says 177 s, the same 295 ticks. */
    const val ROTATE_TICKS = 295L

    /** Wiki "Sprite": "A sprite will spawn every 290-310 ticks (~3min) while actively mining an ascension rock." */
    private const val SPRITE_MIN_TICKS = 290
    private const val SPRITE_MAX_TICKS = 310

    /** Wiki "Ascension rocks" (3 Nov 2026 news post): "a very rare (1 in 68,120) chance to obtain the Rock Golem". */
    const val ROCK_GOLEM_ONE_IN = 68_120

    /** Wiki "Sprite": "they will disappear after 30 seconds" = 50 ticks. */
    const val SPRITE_LIFETIME_TICKS = 50L

    /**
     * Ticks the sprite stays after "Look-at" so its explode animation can play (OPEN-AR4: the animation length is not read from the
     * cache; cosmetic only).
     */
    const val SPRITE_SHATTER_TICKS = 2L

    /** Tiles the sprite may wander from where it appears (Wiki "Sprite": "mostly stationary, they can wander around"; OPEN-AR5). */
    const val SPRITE_WANDER = 1

    /** The sites in ring order: the Wiki map's pin order, then the cache-only tile (OPEN-AR2). */
    val SITES: List<AscensionSite> =
        listOf(
            AscensionSite(Locs.TFA_GATHERING_ROCK_INACTIVE03, CoordGrid(4114, 5539, 0), 1),
            AscensionSite(Locs.TFA_GATHERING_ROCK_INACTIVE01, CoordGrid(4111, 5538, 0), 0),
            AscensionSite(Locs.TFA_GATHERING_ROCK_INACTIVE03, CoordGrid(4110, 5536, 0), 1),
            AscensionSite(Locs.TFA_GATHERING_ROCK_INACTIVE03, CoordGrid(4111, 5535, 0), 0),
            AscensionSite(Locs.TFA_GATHERING_ROCK_INACTIVE01, CoordGrid(4113, 5533, 0), 3),
            AscensionSite(Locs.TFA_GATHERING_ROCK_INACTIVE02, CoordGrid(4115, 5532, 0), 1),
            AscensionSite(Locs.TFA_GATHERING_ROCK_INACTIVE03, CoordGrid(4117, 5534, 0), 3),
            AscensionSite(Locs.TFA_GATHERING_ROCK_INACTIVE01, CoordGrid(4116, 5535, 0), 0),
            AscensionSite(Locs.TFA_GATHERING_ROCK_INACTIVE02, CoordGrid(4116, 5537, 0), 2),
            AscensionSite(Locs.TFA_GATHERING_ROCK_INACTIVE02, CoordGrid(4113, 5538, 0), 2),
        )

    /** Cache shape 10 (centrepiece) of every ascension rock loc. */
    const val SHAPE = 10

    /** The site at [slot] (0 until [ACTIVE_COUNT]) of rotation [step]: the rotation walks the ring three sites at a time (OPEN-AR3). */
    fun site(step: Int, slot: Int): AscensionSite = SITES[(step * ACTIVE_COUNT + slot) % SITES.size]

    /** Called on every roll of an ascension rock: counts [pickTicks] of mining toward the next sprite and spawns it when due. */
    fun onRoll(p: PlayerRef, pickTicks: Int) {
        val left = (p[SPRITE_LEFT] ?: drawDelay(p)) - pickTicks
        if (left > 0) {
            p[SPRITE_LEFT] = left
            return
        }
        p[SPRITE_LEFT] = drawDelay(p)
        val state = p.worldAttr(WORLD) ?: return
        val at = spawnTile(p)
        val npc = p.spawnNpc(Npcs.TFA_GATHERING_SPRITE, at, SPRITE_WANDER)
        state.sprites += SpriteSlot(npc, p.tick + SPRITE_LIFETIME_TICKS, false)
    }

    /** Called after a shard was mined: the Rock golem tertiary roll (Wiki "Ascension rocks" > Tertiary). */
    fun onShard(p: PlayerRef) {
        if (p.random(ROCK_GOLEM_ONE_IN) != 0) return
        // OPEN-AR6: there is no follower system, so the pet goes to the backpack (or the floor), as drops.md does for pets.
        if (!p.transaction("ascension rock golem") { addOrDrop(Invs.INV, Objs.SKILLPETMINING, 1) }) return
        p.events.publish(LootEvent(p, Objs.SKILLPETMINING, 1))
        p.message("You feel something weird sneaking into your backpack.")
    }

    private fun drawDelay(p: PlayerRef): Int = SPRITE_MIN_TICKS + p.random(SPRITE_MAX_TICKS - SPRITE_MIN_TICKS + 1)

    /** A free tile next to [p], starting at a random neighbour (OPEN-AR5: "near the player"); the player's own tile if all are blocked. */
    private fun spawnTile(p: PlayerRef): CoordGrid {
        val start = p.random(NEIGHBOURS)
        for (i in 0 until NEIGHBOURS) {
            val n = (start + i) % NEIGHBOURS
            val tile = p.coord.translate(DX[n], DZ[n])
            if (p.isWalkable(tile)) return tile
        }
        return p.coord
    }

    private const val NEIGHBOURS = 8
    private val DX = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
    private val DZ = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
}

/**
 * The Fractured Archive lobby's ascension rocks (docs/mechanics/skills/ascension-rocks.md). Mining them is [Mining]'s; this plugin
 * owns the world clock that sinks three rocks and raises three others every 295 ticks, and the "Look-at" sprites.
 */
public class AscensionMine : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart { start(this) }
        onWorldTick { drive(this) }
        onOpNpc(Npcs.TFA_GATHERING_SPRITE, OP_LOOK_AT) { npc -> lookAt(npc) }
    }

    /** The cache places every rock inactive; the first three become active (rotation 0, OPEN-AR3). */
    private fun start(world: WorldRef) {
        world.setWorldAttr(AscensionRocks.WORLD, AscensionWorld(0, world.tick + AscensionRocks.ROTATE_TICKS))
        for (slot in 0 until AscensionRocks.ACTIVE_COUNT) raise(world, AscensionRocks.site(0, slot))
    }

    private fun raise(world: WorldRef, site: AscensionSite) {
        world.addLoc(site.active, site.at, AscensionRocks.SHAPE, site.angle)
    }

    private fun sink(world: WorldRef, site: AscensionSite) {
        world.addLoc(site.inactive, site.at, AscensionRocks.SHAPE, site.angle)
    }

    /** Every tick: the world timer (it ignores players) and the sprites' lifetimes. */
    private fun drive(world: WorldRef) {
        val state = world.worldAttr(AscensionRocks.WORLD) ?: return
        val now = world.tick
        if (now >= state.rotateAt) rotate(world, state)
        val sprites = state.sprites
        var i = 0
        while (i < sprites.size) {
            val slot = sprites[i]
            if (slot.dueTick > now) {
                i++
                continue
            }
            if (slot.npc.isInWorld) slot.npc.despawn()
            sprites.removeAt(i)
        }
    }

    /** AR2: the three active rocks sink and the next three raise; the next rotation is a full period after the last one was due. */
    private fun rotate(world: WorldRef, state: AscensionWorld) {
        for (slot in 0 until AscensionRocks.ACTIVE_COUNT) sink(world, AscensionRocks.site(state.step, slot))
        state.step = (state.step + 1) % AscensionRocks.SITES.size
        for (slot in 0 until AscensionRocks.ACTIVE_COUNT) raise(world, AscensionRocks.site(state.step, slot))
        state.rotateAt += AscensionRocks.ROTATE_TICKS
    }

    /** "Look-at": the sprite shatters for 1 or 2 shards and 50..89 Mining xp (Wiki "Sprite"). Mining is not interrupted (OPEN-AR7). */
    private fun PlayerScript.lookAt(npc: NpcRef) {
        val p = player
        val state = p.worldAttr(AscensionRocks.WORLD) ?: return
        val slot = state.sprites.firstOrNull { it.npc == npc && !it.taken } ?: return
        val shards = SHARDS_MIN + p.random(SHARDS_MAX - SHARDS_MIN + 1)
        if (!p.transaction("ascension sprite") { addOrDrop(Invs.INV, Objs.ASCENSION_SHARDS, shards) }) return
        slot.taken = true
        slot.dueTick = p.tick + AscensionRocks.SPRITE_SHATTER_TICKS
        npc.anim(Seqs.TFA_GATHERING_SPRITE_EXPLODE)
        MiningOutfit.addXp(p, (XP_MIN + p.random(XP_MAX - XP_MIN + 1)).toDouble())
        p.message("The sprite shatters, giving you some ascension shards.")
    }

    private companion object {
        /** Op 1 of the sprite ("Look-at"). */
        const val OP_LOOK_AT = 1

        /** Wiki "Sprite" Drops: ascension shards 1-2, always (the distribution is uniform, OPEN-AR7). */
        const val SHARDS_MIN = 1
        const val SHARDS_MAX = 2

        /** Wiki "Sprite": "provide bonus ascension shards as well as 50-89 xp" (uniform, OPEN-AR7). */
        const val XP_MIN = 50
        const val XP_MAX = 89
    }
}
