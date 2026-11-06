/*
 * Door, double-door and gate translations adapted from RS Mod (ISC licence, https://github.com/rsmod/rsmod):
 * content/generic/generic-locs `DoorTranslations`, `DoorScript`, `DoubleDoorScript`, `GateTranslations`, `PicketGate`.
 * Copyright (c) RS Mod contributors. Permission to use, copy, modify, and/or distribute this software for any purpose
 * with or without fee is hereby granted, provided that the above copyright notice and this permission notice appear
 * in all copies.
 */
package tp.content.world

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.util.CoordGrid

/**
 * Generic doors, double doors, gates and other open/close locs (docs/mechanics/doors-and-climbing.md DC1–DC4). Pairs
 * come from `doors.tsv` (`cache-dump loc-links`); a changed door reverts after 500 ticks (RS Mod `DoorConstants`).
 * Content-specific handlers for the same loc and op win ([ContentScope.onOpLocFallback]).
 */
public class Doors : ContentPlugin {
    private enum class Kind { SINGLE, LEFT, RIGHT, GATE_LEFT, GATE_RIGHT }

    private class Pair(val closed: Int, val opened: Int, val kind: Kind)

    override fun ContentScope.register() {
        for (pair in PAIRS.values.distinct()) {
            onOpLocFallback(pair.closed, OPEN_OPS.getValue(pair.closed)) { loc -> change(player, loc, pair, open = true) }
            onOpLocFallback(pair.opened, CLOSE_OPS.getValue(pair.opened)) { loc -> change(player, loc, pair, open = false) }
        }
    }

    private fun change(player: PlayerRef, loc: LocRef, pair: Pair, open: Boolean) {
        val wall = loc.shape == WALL || loc.shape == WALL_DIAGONAL
        when {
            // DC4: a non-wall loc (chest, cupboard, trapdoor) swaps in place.
            !wall -> swap(player, loc, if (open) pair.opened else pair.closed, loc.coord, loc.angle)

            pair.kind == Kind.LEFT || pair.kind == Kind.RIGHT -> doubleDoor(player, loc, pair, open)

            (pair.kind == Kind.GATE_LEFT || pair.kind == Kind.GATE_RIGHT) && loc.shape == WALL -> gate(player, loc, pair, open)

            else -> single(player, loc, pair, open)
        }
    }

    private fun swap(player: PlayerRef, loc: LocRef, id: Int, to: CoordGrid, angle: Int) {
        player.deleteLoc(loc.coord, loc.shape, DURATION)
        player.addLoc(id, to, loc.shape, angle and ANGLE_MASK, DURATION)
    }

    /** DC1 (RS Mod `DoorScript`): the door turns a quarter and moves into the opening. */
    private fun single(player: PlayerRef, loc: LocRef, pair: Pair, open: Boolean) {
        val to = if (open) translateOpen(loc) else translateClose(loc)
        // A diagonal door swinging onto the player's tile moves them aside first.
        if (loc.shape == WALL_DIAGONAL && player.coord == to) stepAway(loc, to, open)?.let(player::teleport)
        swap(player, loc, if (open) pair.opened else pair.closed, to, loc.angle + if (open) 1 else -1)
    }

    private fun stepAway(loc: LocRef, to: CoordGrid, open: Boolean): CoordGrid? =
        if (open) {
            when (loc.angle) {
                WEST -> to.offset(-1, 0)
                NORTH -> to.offset(0, 1)
                EAST -> to.offset(1, 0)
                else -> to.offset(0, -1)
            }
        } else {
            when (loc.angle) {
                WEST -> to.offset(1, 1)
                NORTH -> to.offset(1, -1)
                EAST -> to.offset(-1, -1)
                else -> to.offset(-1, 1)
            }
        }

    /** DC2 (RS Mod `DoubleDoorScript`): both halves open outwards together. */
    private fun doubleDoor(player: PlayerRef, loc: LocRef, pair: Pair, open: Boolean) {
        val left = pair.kind == Kind.LEFT
        val partnerAt =
            when {
                open && left -> translateClose(loc)
                open -> translateCloseOpposite(loc)
                else -> translateOpenOpposite(loc)
            }
        val partner = player.locAt(partnerAt, loc.shape)?.takeIf { p -> matches(p, if (left) Kind.RIGHT else Kind.LEFT, open) }
        moveHalf(player, loc, pair, open, left)
        if (partner != null) moveHalf(player, partner, PAIRS.getValue(partner.id), open, !left)
    }

    private fun moveHalf(player: PlayerRef, loc: LocRef, pair: Pair, open: Boolean, left: Boolean) {
        val to =
            when {
                open -> translateOpen(loc)
                left -> translateCloseOpposite(loc)
                else -> translateClose(loc)
            }
        val turn = if (open == left) THREE_QUARTERS else 1
        swap(player, loc, if (open) pair.opened else pair.closed, to, loc.angle + turn)
    }

    /** DC3 (RS Mod `PicketGate`): both halves swing; the right half moves two tiles. */
    private fun gate(player: PlayerRef, loc: LocRef, pair: Pair, open: Boolean) {
        val left = pair.kind == Kind.GATE_LEFT
        val (px, pz) = gatePair(loc.angle)
        val partnerAt = if (left) loc.coord.offset(px, pz) else loc.coord.offset(-px, -pz)
        val partner = player.locAt(partnerAt, loc.shape)?.takeIf { p -> matches(p, if (left) Kind.GATE_RIGHT else Kind.GATE_LEFT, open) }
        val leftLoc = if (left) loc else partner
        val rightLoc = if (left) partner else loc
        leftLoc?.let { l ->
            val (dx, dz) = gateLeft(if (open) l.angle else l.angle + THREE_QUARTERS)
            swap(
                player,
                l,
                if (open) PAIRS.getValue(l.id).opened else PAIRS.getValue(l.id).closed,
                l.coord.offset(dx, dz),
                l.angle + if (open) THREE_QUARTERS else 1,
            )
        }
        rightLoc?.let { r ->
            val (dx, dz) = gateRight(if (open) r.angle else r.angle + THREE_QUARTERS)
            swap(
                player,
                r,
                if (open) PAIRS.getValue(r.id).opened else PAIRS.getValue(r.id).closed,
                r.coord.offset(dx, dz),
                r.angle + if (open) THREE_QUARTERS else 1,
            )
        }
    }

    /** Whether [loc] is the other half of kind [kind], in the state being changed (closed when opening). */
    private fun matches(loc: LocRef, kind: Kind, open: Boolean): Boolean {
        val p = PAIRS[loc.id] ?: return false
        return p.kind == kind && loc.id == if (open) p.closed else p.opened
    }

    private companion object {
        /** RS Mod `DoorConstants.DURATION`: changed doors revert after 500 ticks. */
        const val DURATION = 500
        const val WALL = 0
        const val WALL_DIAGONAL = 9
        const val ANGLE_MASK = 3
        const val THREE_QUARTERS = 3
        const val WEST = 0
        const val NORTH = 1
        const val EAST = 2

        val PAIRS: Map<Int, Pair>
        val OPEN_OPS = HashMap<Int, Int>()
        val CLOSE_OPS = HashMap<Int, Int>()

        init {
            val map = HashMap<Int, Pair>()
            val text =
                Doors::class.java
                    .getResourceAsStream("doors.tsv")
                    ?.bufferedReader()
                    ?.readLines()
                    .orEmpty()
            for (line in text.drop(1)) {
                val f = line.split('\t')
                val pair = Pair(f[0].toInt(), f[1].toInt(), Kind.valueOf(f[2].uppercase()))
                map[pair.closed] = pair
                map[pair.opened] = pair
                OPEN_OPS[pair.closed] = f[3].toInt()
                CLOSE_OPS[pair.opened] = f[4].toInt()
            }
            PAIRS = map
        }

        fun CoordGrid.offset(dx: Int, dz: Int) = CoordGrid(x + dx, z + dz, level)

        /** RS Mod `DoorTranslations.translateOpen`. */
        fun translateOpen(l: LocRef): CoordGrid =
            if (l.shape == WALL) l.coord.wallStep(l.angle) else l.coord.diagonalStep(l.angle)

        fun translateOpenOpposite(l: LocRef): CoordGrid =
            if (l.shape == WALL) l.coord.wallStep(l.angle + 2) else l.coord.diagonalStep(l.angle + 2)

        /** RS Mod `DoorTranslations.translateClose`. */
        fun translateClose(l: LocRef): CoordGrid =
            if (l.shape == WALL) l.coord.diagonalStep(l.angle) else l.coord.closeDiagonal(l.angle)

        fun translateCloseOpposite(l: LocRef): CoordGrid =
            if (l.shape == WALL) l.coord.diagonalStep(l.angle + 2) else l.coord.closeDiagonal(l.angle + 2)

        /** West x-1, north z+1, east x+1, south z-1. */
        fun CoordGrid.wallStep(angle: Int): CoordGrid =
            when (angle and ANGLE_MASK) {
                WEST -> offset(-1, 0)
                NORTH -> offset(0, 1)
                EAST -> offset(1, 0)
                else -> offset(0, -1)
            }

        /** West z+1, north x+1, east z-1, south x-1. */
        fun CoordGrid.diagonalStep(angle: Int): CoordGrid =
            when (angle and ANGLE_MASK) {
                WEST -> offset(0, 1)
                NORTH -> offset(1, 0)
                EAST -> offset(0, -1)
                else -> offset(-1, 0)
            }

        /** West x+1, north z-1, east x-1, south z+1. */
        fun CoordGrid.closeDiagonal(angle: Int): CoordGrid =
            when (angle and ANGLE_MASK) {
                WEST -> offset(1, 0)
                NORTH -> offset(0, -1)
                EAST -> offset(-1, 0)
                else -> offset(0, 1)
            }

        /** RS Mod `GateTranslations.leftGateRightPair`. */
        fun gatePair(angle: Int): kotlin.Pair<Int, Int> =
            when (angle and ANGLE_MASK) {
                WEST -> 0 to 1
                NORTH -> 1 to 0
                EAST -> 0 to -1
                else -> -1 to 0
            }

        /** RS Mod `GateTranslations.leftGateOpen`. */
        fun gateLeft(angle: Int): kotlin.Pair<Int, Int> =
            when (angle and ANGLE_MASK) {
                WEST -> -1 to 0
                NORTH -> 0 to 1
                EAST -> 1 to 0
                else -> 0 to -1
            }

        /** RS Mod `GateTranslations.rightGateOpen`. */
        @Suppress("MagicNumber") // RS Mod's translation table
        fun gateRight(angle: Int): kotlin.Pair<Int, Int> =
            when (angle and ANGLE_MASK) {
                WEST -> -2 to -1
                NORTH -> -1 to 2
                EAST -> 2 to 1
                else -> 1 to -2
            }
    }
}
