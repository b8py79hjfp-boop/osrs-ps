/*
 * Adapted from RS Mod `engine/game/src/main/kotlin/org/rsmod/game/map/collision/CollisionFlagMapExtensions.kt`
 * (https://github.com/rsmod/rsmod, commit fa13b3f). Copyright (c) 2025 RS Mod. ISC License:
 *
 * Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby
 * granted, provided that the above copyright notice and this permission notice appear in all copies.
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE.
 *
 * Changes: table-driven wall cases instead of nested whens; coordinates as (x, z, level) ints. Semantics unchanged.
 */
package tp.cache.map

import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import org.rsmod.routefinder.loc.LocShapeConstants as Shape

/**
 * Applies or removes one loc's collision. Rules and tile offsets: docs/mechanics/collision.md.
 * Used for the static base map at boot and (Phase 3) for dynamic loc changes.
 */
object LocCollision {
    /** Loc angles: 0 west, 1 north, 2 east, 3 south (rsmod-pathfinder `LocAngleConstants`). */
    private const val ANGLE_NORTH = 1
    private const val ANGLE_SOUTH = 3

    /** blockWalk value that makes ground decor (shape 22) block: client checks `blockWalk == 1`. */
    private const val GROUND_DECOR_BLOCKING = 1

    @Suppress("LongParameterList")
    fun toggle(
        flags: CollisionFlagMap,
        x: Int,
        z: Int,
        level: Int,
        shape: Int,
        angle: Int,
        width: Int,
        length: Int,
        blockWalk: Int,
        blockRange: Boolean,
        breakRouteFinding: Boolean,
        add: Boolean,
    ) {
        if (blockWalk == 0) return
        val rotated = angle == ANGLE_NORTH || angle == ANGLE_SOUTH
        val w = if (rotated) length else width
        val l = if (rotated) width else length
        when (shape) {
            Shape.GROUND_DECOR -> {
                if (blockWalk == GROUND_DECOR_BLOCKING) apply(flags, x, z, level, CollisionFlag.GROUND_DECOR, add)
            }

            Shape.CENTREPIECE_STRAIGHT, Shape.CENTREPIECE_DIAGONAL, Shape.WALL_DIAGONAL -> {
                ground(flags, x, z, level, w, l, blockRange, breakRouteFinding, add)
            }

            Shape.WALL_STRAIGHT, Shape.WALL_DIAGONAL_CORNER, Shape.WALL_L, Shape.WALL_SQUARE_CORNER -> {
                wall(flags, x, z, level, shape, angle, blockRange, add)
            }

            else -> {
                if (shape >= Shape.ROOF_STRAIGHT) ground(flags, x, z, level, w, l, blockRange, breakRouteFinding, add)
            }
            // Wall decor (4-8): no collision.
        }
    }

    @Suppress("LongParameterList")
    private fun ground(
        flags: CollisionFlagMap,
        x: Int,
        z: Int,
        level: Int,
        width: Int,
        length: Int,
        blockRange: Boolean,
        breakRouteFinding: Boolean,
        add: Boolean,
    ) {
        var mask = CollisionFlag.LOC
        if (blockRange) mask = mask or CollisionFlag.LOC_PROJ_BLOCKER
        if (breakRouteFinding) mask = mask or CollisionFlag.LOC_ROUTE_BLOCKER
        for (dx in 0 until width) {
            for (dz in 0 until length) apply(flags, x + dx, z + dz, level, mask, add)
        }
    }

    /**
     * One wall edge: [flag] on the loc tile, [opposite] on the neighbour at ([dx], [dz]); the projectile variants are
     * added when the loc blocks range.
     */
    private class Edge(val flag: Int, val proj: Int, val dx: Int, val dz: Int, val opposite: Int, val oppositeProj: Int)

    private val WEST =
        Edge(
            CollisionFlag.WALL_WEST,
            CollisionFlag.WALL_WEST_PROJ_BLOCKER,
            -1,
            0,
            CollisionFlag.WALL_EAST,
            CollisionFlag.WALL_EAST_PROJ_BLOCKER,
        )
    private val NORTH =
        Edge(
            CollisionFlag.WALL_NORTH,
            CollisionFlag.WALL_NORTH_PROJ_BLOCKER,
            0,
            1,
            CollisionFlag.WALL_SOUTH,
            CollisionFlag.WALL_SOUTH_PROJ_BLOCKER,
        )
    private val EAST =
        Edge(
            CollisionFlag.WALL_EAST,
            CollisionFlag.WALL_EAST_PROJ_BLOCKER,
            1,
            0,
            CollisionFlag.WALL_WEST,
            CollisionFlag.WALL_WEST_PROJ_BLOCKER,
        )
    private val SOUTH =
        Edge(
            CollisionFlag.WALL_SOUTH,
            CollisionFlag.WALL_SOUTH_PROJ_BLOCKER,
            0,
            -1,
            CollisionFlag.WALL_NORTH,
            CollisionFlag.WALL_NORTH_PROJ_BLOCKER,
        )
    private val NORTH_WEST =
        Edge(
            CollisionFlag.WALL_NORTH_WEST,
            CollisionFlag.WALL_NORTH_WEST_PROJ_BLOCKER,
            -1,
            1,
            CollisionFlag.WALL_SOUTH_EAST,
            CollisionFlag.WALL_SOUTH_EAST_PROJ_BLOCKER,
        )
    private val NORTH_EAST =
        Edge(
            CollisionFlag.WALL_NORTH_EAST,
            CollisionFlag.WALL_NORTH_EAST_PROJ_BLOCKER,
            1,
            1,
            CollisionFlag.WALL_SOUTH_WEST,
            CollisionFlag.WALL_SOUTH_WEST_PROJ_BLOCKER,
        )
    private val SOUTH_EAST =
        Edge(
            CollisionFlag.WALL_SOUTH_EAST,
            CollisionFlag.WALL_SOUTH_EAST_PROJ_BLOCKER,
            1,
            -1,
            CollisionFlag.WALL_NORTH_WEST,
            CollisionFlag.WALL_NORTH_WEST_PROJ_BLOCKER,
        )
    private val SOUTH_WEST =
        Edge(
            CollisionFlag.WALL_SOUTH_WEST,
            CollisionFlag.WALL_SOUTH_WEST_PROJ_BLOCKER,
            -1,
            -1,
            CollisionFlag.WALL_NORTH_EAST,
            CollisionFlag.WALL_NORTH_EAST_PROJ_BLOCKER,
        )

    /** Edges per angle (0 west, 1 north, 2 east, 3 south). */
    private val STRAIGHT = arrayOf(arrayOf(WEST), arrayOf(NORTH), arrayOf(EAST), arrayOf(SOUTH))
    private val CORNER = arrayOf(arrayOf(NORTH_WEST), arrayOf(NORTH_EAST), arrayOf(SOUTH_EAST), arrayOf(SOUTH_WEST))
    private val L_SHAPE = arrayOf(arrayOf(WEST, NORTH), arrayOf(NORTH, EAST), arrayOf(EAST, SOUTH), arrayOf(SOUTH, WEST))

    @Suppress("LongParameterList")
    private fun wall(flags: CollisionFlagMap, x: Int, z: Int, level: Int, shape: Int, angle: Int, blockRange: Boolean, add: Boolean) {
        val edges =
            when (shape) {
                Shape.WALL_STRAIGHT -> STRAIGHT[angle]
                Shape.WALL_L -> L_SHAPE[angle]
                else -> CORNER[angle] // WALL_DIAGONAL_CORNER, WALL_SQUARE_CORNER
            }
        for (e in edges) {
            apply(flags, x, z, level, if (blockRange) e.flag or e.proj else e.flag, add)
            apply(flags, x + e.dx, z + e.dz, level, if (blockRange) e.opposite or e.oppositeProj else e.opposite, add)
        }
    }

    private fun apply(flags: CollisionFlagMap, x: Int, z: Int, level: Int, mask: Int, add: Boolean) {
        if (add) flags.add(x, z, level, mask) else flags.remove(x, z, level, mask)
    }
}
