package tp.content.raids.toa

import tp.content.raids.RaidRun
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Path of Scabaras (puzzles.md SC1-SC10): five puzzles (sequence, obelisks, lights, addition, matching) and the scarabs
 * that harass the raiders. The four puzzle slots start at the scene tiles LD reads (RT85 / RT92: scene base (3504, 5232)
 * from the flame barriers); the matching boards' place and the barrier that opens when everything is solved are
 * OPEN-PZ30 assumptions. The scarabs fight through the generic NPC combat; their swarm AI is a TODO for the main session.
 */
internal object ToaScabaras {
    private const val ROOM = "scabaras"
    private const val SHAPE_OBJECT = 10
    private const val SHAPE_GROUND = 22

    private enum class Kind { SEQUENCE, OBELISK, LIGHTS, ADDITION }

    private class State {
        /** Which puzzle sits in each of the four slots. */
        val kinds = ArrayList<Kind>()
        val solved = HashSet<String>()
        val lastTile = HashMap<String, Int>()

        // sequence
        var seq = IntArray(0)
        var seqShowFrom = -1L
        var seqIn = 0

        // obelisks
        var obeliskOrder = IntArray(0)
        var obeliskDone = 0
        val obelisks = ArrayList<NpcRef>()
        val rocks = ArrayList<Triple<String, CoordGrid, Long>>()

        // lights
        val lights = BooleanArray(LIGHTS)

        // addition
        var values = IntArray(0)
        val lit = BooleanArray(ADD_CELLS)
        var target = 0

        // matching
        var boardA = IntArray(0)
        var boardB = IntArray(0)
        val matched = BooleanArray(SYMBOLS)
        var pickBoard = -1
        var pickIndex = -1

        // scarabs
        val scarabs = ArrayList<NpcRef>()
        var nextScarab = 0L
    }

    private val store = RunStore<State>("toa_scabaras")

    fun register(scope: ContentScope) {
        with(scope) {
            onOpLoc(Locs.TOA_SCABARAS_SIMONSAYS_BUTTON, 1) { pressButton(player) }
            onOpNpc(Npcs.TOA_SCABARAS_GUESSER_OBELISK, 2) { obelisk -> hitObelisk(player, obelisk) }
            onOpLoc(Locs.TOA_SCABARAS_LIGHTSOUT_TILE_OFF, OP_FLIP) { loc -> flip(player, loc) }
            onOpLoc(Locs.TOA_SCABARAS_TOTALTILES_TABLET, 1) { readTablet(player) }
            for (i in 0 until SYMBOLS) onOpLoc(Locs.TOA_SCABARAS_MEMORYGAME_BUTTON1 + i, 1) { loc -> activate(player, loc, i) }
        }
    }

    fun start(run: RaidRun, any: EntityRef) {
        val s = State()
        // OPEN-PZ30: the four slots hold the four stepping / shooting puzzles in an order drawn once per room.
        val kinds = Kind.entries.toMutableList()
        while (kinds.isNotEmpty()) s.kinds += kinds.removeAt(run.rng.next(kinds.size, "toa scabaras slot"))
        s.nextScarab = any.tick + ToaConfig.scabarasScarabTicks
        store[any, run] = s
        for ((slot, kind) in s.kinds.withIndex()) {
            when (kind) {
                Kind.SEQUENCE -> {
                    val b = slotAt(run, slot, -1, 1)
                    any.addLoc(Locs.TOA_SCABARAS_SIMONSAYS_BUTTON, b, SHAPE_OBJECT, 0)
                    for (i in 0 until PLATES) plate(run, any, slot, i, Locs.TOA_SCABARAS_SIMONSAYS_TILE_UP)
                }

                Kind.OBELISK -> {
                    s.obeliskOrder = shuffled(run, OBELISKS, "toa scabaras obelisk")
                    for (k in 0 until OBELISKS) {
                        s.obelisks +=
                            ToaRooms.spawn(any, run, ROOM, Npcs.TOA_SCABARAS_GUESSER_OBELISK, slotTemplate(slot, PLATE_GAP * k, 1))
                    }
                }

                Kind.LIGHTS -> {
                    lightsStart(run, s)
                    for (i in 0 until LIGHTS) {
                        any.addLoc(Locs.TOA_SCABARAS_LIGHTSOUT_TILE_OFF, lightTile(run, slot, i), SHAPE_GROUND, 0)
                        showLight(run, any, s, slot, i)
                    }
                }

                Kind.ADDITION -> {
                    s.values = shuffled(run, ADD_CELLS, "toa scabaras addition").map { it % VALUES + 1 }.toIntArray()
                    any.addLoc(Locs.TOA_SCABARAS_TOTALTILES_TABLET, slotAt(run, slot, -1, 1), SHAPE_OBJECT, 0)
                    for (c in 0 until ADD_CELLS) {
                        any.addLoc(Locs.TOA_SCABARAS_TOTALTILES_TILE1 + s.values[c] - 1, addTile(run, slot, c), SHAPE_GROUND, 0)
                    }
                }
            }
        }
        matchingStart(run, any, s)
        for (y in BARRIER_Z) any.addLoc(Locs.TOA_SCABARAS_BARRIER_ACTIVE, run.at(CoordGrid(BARRIER_X, y)), SHAPE_OBJECT, 0)
    }

    fun tick(run: RaidRun, any: EntityRef) {
        val s = store[any, run] ?: return
        if (ROOM_DONE in s.solved) return
        val now = any.tick
        val players = ToaPuzzles.alive(run, any)
        sequenceShow(run, any, s, now)
        for (p in players) steps(run, any, s, p)
        rockTick(s, players, now)
        s.scarabs.removeAll { !it.isInWorld }
        if (now >= s.nextScarab) {
            s.nextScarab = now + ToaConfig.scabarasScarabTicks
            if (s.scarabs.size < ToaConfig.scabarasScarabMax + run.scaling.partySize) spawnScarab(run, any, s)
        }
    }

    /** SC2: scarabs appear at the spawn holes of the first rooms (OPEN-PZ31: rate and cap). */
    private fun spawnScarab(run: RaidRun, any: EntityRef, s: State) {
        val hole = HOLES[run.rng.next(HOLES.size, "toa scabaras scarab")]
        val npc = ToaRooms.spawn(any, run, ROOM, Npcs.TOA_SCABARAS_SCARAB, hole)
        npc.huntRange = SCARAB_HUNT
        s.scarabs += npc
    }

    // ---- geometry

    private fun slotTemplate(slot: Int, dx: Int, dz: Int) = CoordGrid(SLOTS[slot].x + dx, SLOTS[slot].z + dz)

    private fun slotAt(run: RaidRun, slot: Int, dx: Int, dz: Int) = run.at(slotTemplate(slot, dx, dz))

    /** LD: a 3 x 3 field of plates two tiles apart, going east and south from the slot's start tile. */
    private fun plateTile(run: RaidRun, slot: Int, i: Int): CoordGrid = slotAt(run, slot, PLATE_GAP * (i % 3), -PLATE_GAP * (i / 3))

    private fun plate(run: RaidRun, any: EntityRef, slot: Int, i: Int, loc: Int) = any.addLoc(loc, plateTile(run, slot, i), SHAPE_GROUND, 0)

    /** The lights field is the 3 x 3 without the middle plate: index 0..7 in reading order. */
    private fun lightTile(run: RaidRun, slot: Int, i: Int): CoordGrid = plateTile(run, slot, if (i >= MIDDLE) i + 1 else i)

    /** LD: the addition field is 5 x 5 tiles, adjacent, going east and south from the slot's start tile. */
    private fun addTile(run: RaidRun, slot: Int, c: Int): CoordGrid = slotAt(run, slot, c % ADD_SIDE, -(c / ADD_SIDE))

    private fun shuffled(run: RaidRun, n: Int, label: String): IntArray {
        val a = IntArray(n) { it }
        for (i in n - 1 downTo 1) {
            val j = run.rng.next(i + 1, label)
            val t = a[i]
            a[i] = a[j]
            a[j] = t
        }
        return a
    }

    private fun slotOf(s: State, kind: Kind): Int = s.kinds.indexOf(kind)

    // ---- steps (SC3, SC5, SC6)

    private fun steps(run: RaidRun, any: EntityRef, s: State, p: PlayerRef) {
        val name = p.username.lowercase()
        val here = ToaPuzzles.key(p.coord)
        if (s.lastTile.put(name, here) == here) return
        val seqSlot = slotOf(s, Kind.SEQUENCE)
        val lightSlot = slotOf(s, Kind.LIGHTS)
        val addSlot = slotOf(s, Kind.ADDITION)
        if ("seq" !in s.solved && s.seq.isNotEmpty() && any.tick >= s.seqShowFrom + seqDuration(s)) {
            for (i in 0 until PLATES) if (plateTile(run, seqSlot, i) == p.coord) stepSequence(run, any, s, p, seqSlot, i)
        }
        if ("lights" !in s.solved) {
            for (i in 0 until LIGHTS) if (lightTile(run, lightSlot, i) == p.coord) pressLight(run, any, s, lightSlot, i, neighbours = true)
        }
        if ("addition" !in s.solved && s.target > 0) {
            for (c in 0 until ADD_CELLS) if (addTile(run, addSlot, c) == p.coord) stepAddition(run, any, s, p, addSlot, c)
        }
    }

    // ---- SC3 sequence

    private fun seqDuration(s: State): Long = s.seq.size.toLong() * ToaConfig.scabarasSeqStepTicks

    private fun pressButton(player: PlayerRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        if ("seq" in s.solved) return
        // SC3: pressing again gives another order (OPEN-PZ32: it replaces the old one).
        var prev = -1
        s.seq =
            IntArray(SEQ_LENGTH) {
                var v: Int
                do {
                    v = player.random(PLATES)
                } while (v == prev)
                prev = v
                v
            }
        s.seqShowFrom = player.tick + 1
        s.seqIn = 0
    }

    private fun sequenceShow(run: RaidRun, any: EntityRef, s: State, now: Long) {
        if (s.seq.isEmpty() || "seq" in s.solved) return
        val slot = slotOf(s, Kind.SEQUENCE)
        val step = ToaConfig.scabarasSeqStepTicks
        val elapsed = now - s.seqShowFrom
        if (elapsed < 0 || elapsed >= seqDuration(s)) return
        val k = (elapsed / step).toInt()
        if (elapsed % step == 0L) plate(run, any, slot, s.seq[k], Locs.TOA_SCABARAS_SIMONSAYS_TILE_DOWN)
        if (elapsed % step == ToaConfig.scabarasSeqLitTicks) plate(run, any, slot, s.seq[k], Locs.TOA_SCABARAS_SIMONSAYS_TILE_UP)
    }

    private fun stepSequence(run: RaidRun, any: EntityRef, s: State, p: PlayerRef, slot: Int, i: Int) {
        if (i == s.seq[s.seqIn]) {
            plate(run, any, slot, i, Locs.TOA_SCABARAS_SIMONSAYS_TILE_DOWN_PLAYER)
            s.seqIn++
            if (s.seqIn >= s.seq.size) solve(run, any, s, "seq")
            return
        }
        // SC3: a wrong plate inflicts damage and the order is lost.
        p.spotAnim(SpotAnims.MM_ROOFCOLLAPSE)
        ToaPuzzles.hit(p, ToaConfig.scabarasSeqDamage)
        for (k in 0 until PLATES) plate(run, any, slot, k, Locs.TOA_SCABARAS_SIMONSAYS_TILE_UP)
        s.seqIn = 0
    }

    // ---- SC4 obelisks

    private fun hitObelisk(player: PlayerRef, obelisk: NpcRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        if ("obelisk" in s.solved || s.obelisks.isEmpty()) return
        val k = s.obelisks.indexOf(obelisk)
        if (k < 0 || obelisk.type != Npcs.TOA_SCABARAS_GUESSER_OBELISK) return
        player.anim(Seqs.TOA_OBELISK01_GLOW)
        if (k == s.obeliskOrder[s.obeliskDone]) {
            obelisk.transform(Npcs.TOA_SCABARAS_GUESSER_OBELISK_HIT, restoreStats = false)
            s.obeliskDone++
            if (s.obeliskDone >= OBELISKS) solve(run, player, s, "obelisk")
            return
        }
        // SC4: a wrong one resets the lit obelisks and drops rocks (OPEN-PZ33: aimed at each raider's tile).
        for (o in s.obelisks) {
            if (o.isInWorld &&
                o.type != Npcs.TOA_SCABARAS_GUESSER_OBELISK
            ) {
                o.transform(Npcs.TOA_SCABARAS_GUESSER_OBELISK, false)
            }
        }
        s.obeliskDone = 0
        for (p in ToaPuzzles.alive(run, player)) {
            s.rocks += Triple(p.username.lowercase(), p.coord, player.tick + ToaConfig.scabarasRockDelayTicks)
            player.spotAnimAt(p.coord, SpotAnims.MM_ROOFCOLLAPSE)
        }
    }

    private fun rockTick(s: State, players: List<PlayerRef>, now: Long) {
        if (s.rocks.isEmpty()) return
        val it = s.rocks.iterator()
        while (it.hasNext()) {
            val (name, at, due) = it.next()
            if (now < due) continue
            it.remove()
            players
                .firstOrNull { p -> p.username.equals(name, ignoreCase = true) && p.coord == at }
                ?.let { p -> ToaPuzzles.hit(p, ToaConfig.scabarasRockDamage) }
        }
    }

    // ---- SC5 lights

    /** OPEN-PZ34: start from all lit and apply random presses, so the board is always solvable. */
    private fun lightsStart(run: RaidRun, s: State) {
        s.lights.fill(true)
        do {
            repeat(LIGHT_SCRAMBLE) { toggleLight(s, run.rng.next(LIGHTS, "toa scabaras lights"), neighbours = true) }
        } while (s.lights.all { it })
    }

    private fun neighbours(i: Int): List<Int> {
        val pos = if (i >= MIDDLE) i + 1 else i
        val cx = pos % 3
        val cz = pos / 3
        val out = ArrayList<Int>(4)
        for ((dx, dz) in ORTHO) {
            val nx = cx + dx
            val nz = cz + dz
            if (nx !in 0..2 || nz !in 0..2) continue
            val np = nz * 3 + nx
            if (np == MIDDLE) continue
            out += if (np > MIDDLE) np - 1 else np
        }
        return out
    }

    /** SC5: a press toggles the plate and its orthogonal neighbours. */
    private fun toggleLight(s: State, i: Int, neighbours: Boolean) {
        s.lights[i] = !s.lights[i]
        if (neighbours) for (n in neighbours(i)) s.lights[n] = !s.lights[n]
    }

    private fun showLight(run: RaidRun, any: EntityRef, s: State, slot: Int, i: Int) {
        val at = lightTile(run, slot, i)
        if (s.lights[i]) any.addLoc(Locs.TOA_SCABARAS_FX01, at, SHAPE_OBJECT, 0) else any.deleteLoc(at, SHAPE_OBJECT)
    }

    private fun pressLight(run: RaidRun, any: EntityRef, s: State, slot: Int, i: Int, neighbours: Boolean) {
        toggleLight(s, i, neighbours)
        for (k in 0 until LIGHTS) showLight(run, any, s, slot, k)
        if (s.lights.all { it }) solve(run, any, s, "lights")
    }

    /** SC5: "Flip" toggles one unlit plate for unavoidable damage (OPEN-PZ34: only that plate). */
    private fun flip(player: PlayerRef, loc: LocRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        val slot = slotOf(s, Kind.LIGHTS)
        val i = (0 until LIGHTS).firstOrNull { lightTile(run, slot, it) == loc.coord } ?: return
        if ("lights" in s.solved || s.lights[i]) return
        player.anim(Seqs.TOA_FLIP_TILE)
        ToaPuzzles.hit(player, ToaConfig.scabarasFlipDamage)
        pressLight(run, player, s, slot, i, neighbours = false)
    }

    // ---- SC6 addition

    private fun readTablet(player: PlayerRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        if (listOf("seq", "obelisk", "lights", "matching").any { it !in s.solved }) {
            player.message("The tablet is unreadable until the other puzzles are complete.")
            return
        }
        if (s.target == 0) s.target = TARGET_MIN + player.random(TARGET_MAX - TARGET_MIN + 1)
        player.message("The number ${s.target} has been hastily chipped into the stone.")
    }

    private fun stepAddition(run: RaidRun, any: EntityRef, s: State, p: PlayerRef, slot: Int, c: Int) {
        s.lit[c] = !s.lit[c]
        val at = addTile(run, slot, c)
        if (s.lit[c]) any.addLoc(FX_VALUE[s.values[c] - 1], at, SHAPE_OBJECT, 0) else any.deleteLoc(at, SHAPE_OBJECT)
        val sum = (0 until ADD_CELLS).sumOf { if (s.lit[it]) s.values[it] else 0 }
        if (sum == s.target) {
            solve(run, any, s, "addition")
        } else if (sum > s.target) {
            // SC6: going over the number damages the player (OPEN-PZ35).
            ToaPuzzles.hit(p, ToaConfig.scabarasAdditionDamage)
        }
    }

    // ---- SC7 matching

    private fun matchingStart(run: RaidRun, any: EntityRef, s: State) {
        s.boardA = shuffled(run, SYMBOLS, "toa scabaras match a")
        s.boardB = shuffled(run, SYMBOLS, "toa scabaras match b")
        for (board in 0..1) {
            for (i in 0 until SYMBOLS) {
                any.addLoc(
                    Locs.TOA_SCABARAS_MEMORYGAME_BUTTON1 + i,
                    matchTile(run, board, i),
                    SHAPE_GROUND,
                    0,
                )
            }
        }
        // SC7: in a solo raid four of the nine sets (line, crook, hand, bird) are already solved.
        if (run.scaling.partySize == 1) for (sym in SOLO_SOLVED) mark(run, any, s, sym)
    }

    private fun matchTile(run: RaidRun, board: Int, i: Int): CoordGrid =
        run.at(CoordGrid(MATCH_X + board * MATCH_GAP + i % 3, MATCH_Z - i / 3))

    private fun symbolAt(s: State, board: Int, i: Int) = if (board == 0) s.boardA[i] else s.boardB[i]

    /** The button's board and slot, by its loc id and tile (the two boards are [MATCH_GAP] apart). */
    private fun activate(player: PlayerRef, loc: LocRef, i: Int) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        if ("matching" in s.solved) return
        val board =
            if (loc.coord == matchTile(run, 0, i)) {
                0
            } else if (loc.coord == matchTile(run, 1, i)) {
                1
            } else {
                return
            }
        val sym = symbolAt(s, board, i)
        if (s.matched[sym]) return
        player.anim(Seqs.TOA_FLIP_TILE)
        player.addLoc(Locs.TOA_SCABARAS_MEMORYGAME_TILE1 + sym, loc.coord, SHAPE_GROUND, 0, ToaConfig.scabarasRevealTicks)
        if (s.pickBoard < 0 || s.pickBoard == board) {
            s.pickBoard = board
            s.pickIndex = i
            return
        }
        // SC7: identical symbols on both boards match; the last puzzle cannot hurt anybody.
        if (symbolAt(s, s.pickBoard, s.pickIndex) == sym) mark(run, player, s, sym)
        s.pickBoard = -1
        s.pickIndex = -1
    }

    private fun mark(run: RaidRun, any: EntityRef, s: State, sym: Int) {
        s.matched[sym] = true
        for (board in 0..1) {
            val i = (0 until SYMBOLS).first { symbolAt(s, board, it) == sym }
            any.addLoc(FX_MATCH[sym], matchTile(run, board, i), SHAPE_OBJECT, 0)
        }
        if (s.matched.all { it }) solve(run, any, s, "matching")
    }

    // ---- SC8 completion

    private fun solve(run: RaidRun, any: EntityRef, s: State, puzzle: String) {
        s.solved += puzzle
        if (listOf("seq", "obelisk", "lights", "addition", "matching").any { it !in s.solved }) return
        s.solved += ROOM_DONE
        for (y in BARRIER_Z) any.deleteLoc(run.at(CoordGrid(BARRIER_X, y)), SHAPE_OBJECT)
        for (scarab in s.scarabs) if (scarab.isInWorld) scarab.despawn()
        ToaFlow.roomDone(any, run, ROOM)
    }

    /** Scene base (3504, 5232) + LD's slot start tiles (36, 56), (36, 44), (53, 56), (53, 44) (RT85 / RT92). */
    private val SLOTS = listOf(CoordGrid(3540, 5288), CoordGrid(3540, 5276), CoordGrid(3557, 5288), CoordGrid(3557, 5276))

    /** Data: the two spawn holes 45343 (cache map). */
    private val HOLES = listOf(CoordGrid(3548, 5277), CoordGrid(3548, 5283))

    /** OPEN-PZ30: the matching boards (3 x 3, [MATCH_GAP] apart) and the barrier before the continue door. */
    private const val MATCH_X = 3564
    private const val MATCH_Z = 5282
    private const val MATCH_GAP = 5
    private const val BARRIER_X = 3578
    private val BARRIER_Z = 5279..5281

    private const val PLATES = 9
    private const val OP_FLIP = 5

    /** LD: plates in a field are two tiles apart. */
    private const val PLATE_GAP = 2
    private const val LIGHTS = 8
    private const val MIDDLE = 4
    private const val OBELISKS = 5
    private const val SEQ_LENGTH = 5
    private const val ADD_SIDE = 5
    private const val ADD_CELLS = 25
    private const val VALUES = 9
    private const val SYMBOLS = 9
    private const val LIGHT_SCRAMBLE = 4
    private const val SCARAB_HUNT = 12
    private const val ROOM_DONE = "room"

    /** SC6: the tablet's number is between 20 and 45. */
    private const val TARGET_MIN = 20
    private const val TARGET_MAX = 45

    /** SC7 (LD): line, crook, hand and bird are already solved in a solo raid (matching order line, knives, crook, ...). */
    private val SOLO_SOLVED = intArrayOf(0, 2, 4, 6)

    private val ORTHO = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    /** SC6 (LD): lit-symbol objects for values 1..9: FX05, FX06, FX07, FX08, FX09, FX10, FX03, FX11, FX12. */
    private val FX_VALUE = intArrayOf(45388, 45389, 45390, 45391, 45392, 45393, 45386, 45394, 45395)

    /** SC7 (LD): matched markers for line, knives, crook, diamond, hand, star, bird, wiggle, boot. */
    private val FX_MATCH = intArrayOf(45388, 45389, 45386, 45391, 45392, 45387, 45393, 45394, 45395)
}
