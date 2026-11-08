package tp.plugin

import tp.util.CoordGrid

// Zone ops one player sees and nobody else does (a raid's per-player hints and multiloc forms). Look only: world state,
// collision and every other viewer stay as they are.

/** Plays seq [seq] on [loc] for this player only (a LOC_ANIM sent to them alone: per-player multilocs). */
public fun PlayerRef.privateLocAnim(loc: LocRef, seq: Int) {
    p.world.locAnim(loc.coord, loc.shape, loc.angle, seq, p.username)
}

/**
 * Shows loc [id] in place of [loc] to this player only (a per-player multiloc's form the list cannot express); a zone
 * rebuild shows the real loc again.
 */
public fun PlayerRef.privateLocChange(loc: LocRef, id: Int) {
    p.world.privateLocChange(loc.coord, id, loc.shape, loc.angle, p.username)
}

/** A spot anim on tile [at] that only this player sees (a MAP_ANIM sent to them alone). */
public fun PlayerRef.privateSpotAnimAt(at: CoordGrid, id: Int, height: Int = 0, delay: Int = 0) {
    p.world.mapAnim(at, id, height, delay, p.username)
}
