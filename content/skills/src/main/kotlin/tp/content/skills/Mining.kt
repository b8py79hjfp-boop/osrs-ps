package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs

/**
 * Mining (docs/mechanics/mining.md). It shares Woodcutting's tick timeline: the click arms the action clock, the swing
 * starts the next tick, and a roll lands every pickaxe cycle ([Pickaxe.ticks]); the roll is [PlayerRef.statRandom] with
 * the rock's low/high rates, and the pickaxe only decides how often it happens (Wiki "Pickaxe"). A mined rock turns
 * into its empty rock for the respawn time.
 */
public class Mining : ContentPlugin {
    /** Pickaxe -> mining level it needs (cache param `levelrequire`). */
    private var pickLevels: Map<Int, Int> = emptyMap()

    override fun ContentScope.register() {
        onWorldStart {
            pickLevels = PICKAXES.keys.associateWith { objParamInt(it, Skilling.PARAM_LEVEL_REQUIRE) ?: 1 }
            MiningEnhancers.resolve(this)
        }
        for ((id, placed) in ROCKS) onOpLoc(id, OP_MINE) { loc -> mine(loc, placed) }
        for (id in EMPTY_ROCKS) onOpLoc(id, OP_MINE) { player.message("There is currently no ore available in this rock.") }
    }

    private fun PlayerScript.mine(loc: LocRef, placed: RockLoc) {
        val p = player
        val rock = placed.rock
        if (p.locAt(loc.coord, loc.shape)?.id != loc.id) return Skilling.stop(p)
        val clock = Skilling.clock(p, Skilling.locKey(loc.id, loc.coord.packed))
        if (!canMine(p, rock)) return Skilling.stop(p)
        val pickObj = Skilling.bestTool(p, Stats.MINING, pickLevels)
        if (pickObj < 0) {
            p.message("You need a pickaxe to mine this rock.")
            p.message("You do not have a pickaxe which you have the Mining level to use.")
            return Skilling.stop(p)
        }
        keepInteraction()
        val pick = PICKAXES.getValue(pickObj)
        val tick = p.tick
        if (clock.fresh && clock.actionDelay < tick) {
            // Same arming tick as woodcutting (RS Mod `Woodcutting.attempt`), OPEN-MN2: the swing comes on the next tick.
            clock.actionDelay = tick + cycleDelay(p, pick)
            clock.animDelay = tick + pick.ticks - 1
            return
        }
        if (clock.stage == 0) {
            clock.stage = 1
            p.anim(pick.anim)
            p.message("You swing your pick at the rock.", Skilling.SPAM)
        }
        swing(p, loc, placed, pick, clock)
    }

    private fun canMine(p: PlayerRef, rock: Rock): Boolean {
        if (p.level(Stats.MINING) < rock.level) {
            p.message("You need a Mining level of ${rock.level} to mine this rock.")
            return false
        }
        if (rock.kind != RockKind.ROCKFALL && p.inv(Invs.INV).freeSlots == 0) {
            p.message("Your inventory is too full to hold any more ${rock.noun}.")
            return false
        }
        return true
    }

    /** Ticks from a roll's re-arming to the next roll: the pickaxe cycle less the rolling tick itself, one less when it is fast. */
    private fun cycleDelay(p: PlayerRef, pick: Pickaxe): Int {
        val fast = pick.fastOneIn > 0 && p.random(pick.fastOneIn) == 0
        return (pick.ticks - 1 - if (fast) 1 else 0).coerceAtLeast(0)
    }

    private fun swing(p: PlayerRef, loc: LocRef, placed: RockLoc, pick: Pickaxe, clock: SkillClock) {
        val tick = p.tick
        if (clock.animDelay <= tick) {
            clock.animDelay = tick + pick.ticks
            p.anim(pick.anim)
        }
        if (clock.actionDelay < tick) {
            clock.actionDelay = tick + cycleDelay(p, pick)
        } else if (clock.actionDelay == tick) {
            if (placed.rock.kind == RockKind.ASCENSION) AscensionRocks.onRoll(p, pick.ticks)
            attempt(p, loc, placed)
        }
    }

    /** One mining roll: the gem table first (it replaces the ore roll), then the rock's own roll. */
    private fun attempt(p: PlayerRef, loc: LocRef, placed: RockLoc) {
        val rock = placed.rock
        if (rock.kind == RockKind.ROCKFALL) return fall(p, loc, rock)
        if (rock.kind == RockKind.VEIN && Motherlode.sackFull(p)) {
            Skilling.stop(p)
            return p.stopInteraction()
        }
        // AR4: ascension rocks take the standard gem pre-roll but not the glory's extra chance (no extra-resource enhancers).
        if (rock.gemTable && gemTable(p, rock.kind != RockKind.ASCENSION)) return
        val product = roll(p, rock) ?: return
        // MN12: Varrock armour and the Mining cape sometimes give a second ore (and its xp).
        var count = 1 + MiningEnhancers.extraOres(p, rock)
        var stored = p.transaction("mining") { add(Invs.INV, product.obj, count) }
        if (!stored && count > 1) {
            count = 1
            stored = p.transaction("mining") { add(Invs.INV, product.obj, 1) }
        }
        if (!stored) {
            p.message("Your inventory is too full to hold any more ${rock.noun}.")
            Skilling.stop(p)
            return p.stopInteraction()
        }
        if (rock.kind == RockKind.GEM) {
            p.message("You get an ${p.objName(product.obj).lowercase()}.", Skilling.SPAM)
        } else {
            p.message("You manage to ${rock.verb} some ${rock.mined}.", Skilling.SPAM)
        }
        MiningOutfit.addXp(p, product.xp * count)
        repeat(count) { Bags.autoStore(p, product.obj) }
        if (rock.kind == RockKind.ASCENSION) AscensionRocks.onShard(p)
        if (rock.kind == RockKind.VEIN) {
            if (Motherlode.veinDepletes(p, loc, placed)) {
                p.anim(-1)
                Skilling.stop(p)
                p.stopInteraction()
            }
            return
        }
        if (depletes(p, loc, rock)) {
            p.addLoc(placed.empty, loc.coord, loc.shape, loc.angle, rock.respawn)
            p.anim(-1)
            Skilling.stop(p)
            p.stopInteraction()
        }
    }

    /** A rockfall: any pickaxe clears it for 10 xp and it falls again after [Rock.respawn] ticks (ML9). */
    private fun fall(p: PlayerRef, loc: LocRef, rock: Rock) {
        MiningOutfit.addXp(p, rock.yields[0].xp)
        p.deleteLoc(loc.coord, loc.shape, rock.respawn)
        p.anim(-1)
        Skilling.stop(p)
        p.stopInteraction()
    }

    /** The product of this roll, or null when it fails. */
    private fun roll(p: PlayerRef, rock: Rock): Yield? =
        when (rock.kind) {
            RockKind.ESSENCE -> {
                rock.yields[if (p.level(Stats.MINING) >= PURE_ESSENCE_LEVEL) 1 else 0]
            }

            RockKind.GEM -> {
                val base = rock.yields[0]
                val glory = MiningEnhancers.glory(p)
                val low = if (glory) MiningEnhancers.GLORY_GEM_LOW else base.low
                val high = if (glory) MiningEnhancers.GLORY_GEM_HIGH else base.high
                if (p.statRandom(Stats.MINING, low, high)) {
                    Yield(pickWeighted(p, GEM_WEIGHTS, GEM_WEIGHT_TOTAL), base.xp, 0, 0)
                } else {
                    null
                }
            }

            RockKind.CASCADE -> {
                rock.yields.firstOrNull { p.statRandom(Stats.MINING, it.low, it.high) }
            }

            RockKind.ORE, RockKind.AMETHYST, RockKind.VEIN, RockKind.ASCENSION -> {
                rock.yields[0].takeIf { p.statRandom(Stats.MINING, it.low, it.high) }
            }

            RockKind.ROCKFALL -> {
                null
            }
        }

    private fun pickWeighted(p: PlayerRef, table: List<Pair<Int, Int>>, total: Int): Int {
        var left = p.random(total)
        for ((obj, weight) in table) {
            if (left < weight) return obj
            left -= weight
        }
        return table.last().first
    }

    /**
     * The Mining gem drop table: a 1 in 256 roll before each attempt; if it hits, the attempt gives a gem or nothing and
     * no ore, xp or depletion (Wiki "Mining gem drop table"). Returns whether the attempt was consumed.
     */
    private fun gemTable(p: PlayerRef, gloryApplies: Boolean = true): Boolean {
        // MN13: a charged glory adds a second 3 in 256 chance when the first roll fails (Wiki "Mining gem drop table").
        val hit =
            p.random(GEM_TABLE_ONE_IN) == 0 ||
                (gloryApplies && MiningEnhancers.glory(p) && p.random(GEM_TABLE_ONE_IN) < MiningEnhancers.GLORY_EXTRA_GEM_IN_256)
        if (!hit) return false
        val gem = pickWeighted(p, PRE_ROLL_GEMS, GEM_WEIGHT_TOTAL)
        if (gem >= 0 && p.transaction("mining") { add(Invs.INV, gem, 1) }) {
            p.message("You just mined an ${p.objName(gem).lowercase()}!", Skilling.SPAM)
            Bags.autoStore(p, gem)
        }
        return true
    }

    /** Whether the rock is used up: ore rocks with their ore, essence never, amethyst after its 2 or 3 crystals. */
    private fun depletes(p: PlayerRef, loc: LocRef, rock: Rock): Boolean =
        when (rock.kind) {
            RockKind.ESSENCE, RockKind.VEIN, RockKind.ROCKFALL, RockKind.ASCENSION -> {
                false
            }

            RockKind.AMETHYST -> {
                val counters = p.worldAttr(COUNTERS) ?: HashMap<Int, IntArray>().also { p.setWorldAttr(COUNTERS, it) }
                val counter =
                    counters.getOrPut(loc.coord.packed) {
                        intArrayOf(
                            0,
                            AMETHYST_MIN_ORES + p.random(AMETHYST_MAX_ORES - AMETHYST_MIN_ORES + 1),
                        )
                    }
                counter[0]++
                (counter[0] >= counter[1]).also { if (it) counters.remove(loc.coord.packed) } && !glovesSave(p, loc, rock)
            }

            else -> {
                !glovesSave(p, loc, rock)
            }
        }

    /**
     * Mining gloves (MN14): the first N depletions of a rock are skipped, N from [MiningEnhancers.glovesSaves]. The count is kept
     * per player and rock tile (OPEN-MN8: the Wiki does not say whether it is per rock or per player).
     */
    private fun glovesSave(p: PlayerRef, loc: LocRef, rock: Rock): Boolean {
        val saves = MiningEnhancers.glovesSaves(p, rock)
        if (saves <= 0) return false
        val used = p[GLOVE_USES] ?: HashMap<Int, Int>().also { p[GLOVE_USES] = it }
        val done = used[loc.coord.packed] ?: 0
        if (done >= saves) {
            used.remove(loc.coord.packed)
            return false
        }
        used[loc.coord.packed] = done + 1
        return true
    }

    private companion object {
        /** Ores mined from an amethyst rock so far and the count it depletes at, by packed tile, per world. */
        val COUNTERS = AttrKey<HashMap<Int, IntArray>>("mining.amethyst")

        /** Depletions a player's gloves have saved so far, by packed rock tile. */
        val GLOVE_USES = AttrKey<HashMap<Int, Int>>("mining.gloves")

        /** Op 1 of every rock ("Mine"). */
        const val OP_MINE = 1
    }
}
