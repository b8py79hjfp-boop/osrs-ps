package tp.content.quests

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.Event
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.symbols.DbRows
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.util.CoordGrid

// Helpers of the "B" quest batch (Pirate's Treasure, Prince Ali Rescue, Shield of Arrav, Dragon Slayer I, Below Ice Mountain,
// The Corsair Curse, Misthalin Mystery, X Marks the Spot). docs/mechanics/quests.md QU8.

/** The quests of the "B" batch (rows of `tp.symbols.DbRows`). */
internal object QuestsB {
    val PIRATES_TREASURE: Quest by lazy { QuestBook.row(DbRows.QUEST_PIRATESTREASURE) }
    val PRINCE_ALI_RESCUE: Quest by lazy { QuestBook.row(DbRows.QUEST_PRINCEALIRESCUE) }
    val SHIELD_OF_ARRAV: Quest by lazy { QuestBook.row(DbRows.QUEST_SHIELDOFARRAV) }
    val DRAGON_SLAYER: Quest by lazy { QuestBook.row(DbRows.QUEST_DRAGONSLAYER1) }
    val BELOW_ICE_MOUNTAIN: Quest by lazy { QuestBook.row(DbRows.QUEST_BELOWICEMOUNTAIN) }
    val CORSAIR_CURSE: Quest by lazy { QuestBook.row(DbRows.QUEST_CORSAIRCURSE) }
    val MISTHALIN_MYSTERY: Quest by lazy { QuestBook.row(DbRows.QUEST_MISTHALINMYSTERY) }
    val X_MARKS_THE_SPOT: Quest by lazy { QuestBook.row(DbRows.QUEST_XMARKSTHESPOT) }
    val RUM_DEAL: Quest by lazy { QuestBook.row(DbRows.QUEST_RUMDEAL) }

    /** The quests of this batch, for `::quest`-style listings. */
    val ALL: List<Quest> by lazy {
        listOf(
            PIRATES_TREASURE,
            PRINCE_ALI_RESCUE,
            SHIELD_OF_ARRAV,
            DRAGON_SLAYER,
            BELOW_ICE_MOUNTAIN,
            CORSAIR_CURSE,
            MISTHALIN_MYSTERY,
            X_MARKS_THE_SPOT,
        )
    }
}

/** True when any worn slot holds [obj]. */
internal fun PlayerRef.wears(obj: Int): Boolean {
    val worn = inv(Invs.WORN)
    return (0 until worn.size).any { worn.obj(it) == obj }
}

/** True when the backpack holds at least one of any of [objs]. */
internal fun PlayerRef.hasAny(vararg objs: Int): Boolean = objs.any { has(it) }

/** True when the quest of the cache name [name] (db table `Quest`, column 2) is finished; false for an unknown name. */
internal fun PlayerRef.questDone(name: String): Boolean =
    QuestBook.all.firstOrNull { it.name == name }?.state(this) == QuestState.FINISHED

/** True when the quest [q] is at [stage] or later. */
internal fun PlayerRef.atLeast(q: Quest, stage: Int): Boolean = q.stage(this) >= stage

/**
 * The tile on the far side of the wall loc [door] from [p] (docs/mechanics/quest-pirates-treasure.md OPEN-QUB1). Walls block
 * the edge named by their angle: 0 west, 1 north, 2 east, 3 south of the loc's tile.
 */
internal fun farSide(p: PlayerRef, door: LocRef): CoordGrid {
    val c = door.coord
    val (a, b) =
        when (door.angle and ANGLE_MASK) {
            0 -> c.translate(-1, 0) to c
            1 -> c to c.translate(0, 1)
            2 -> c to c.translate(1, 0)
            else -> c.translate(0, -1) to c
        }
    return if (p.coord == a) {
        b
    } else if (p.coord == b) {
        a
    } else if (p.coord.chebyshev(a) > p.coord.chebyshev(b)) {
        a
    } else {
        b
    }
}

/**
 * Passes a door that a quest condition guards (the generic door script cannot be asked first, OPEN-QUB1): the player is moved
 * to the other side of the wall loc. The door itself does not swing.
 */
internal fun PlayerRef.passDoor(door: LocRef) {
    teleport(farSide(this, door))
}

private const val ANGLE_MASK = 3

/** Published when a player digs with a spade ([QuestDigging]); a quest that owns the spot sets [handled]. */
internal class DigEvent(val player: PlayerRef, val at: CoordGrid) : Event {
    var handled: Boolean = false
}

/**
 * The spade's "Dig" for quest dig spots (Pirate's Treasure, X Marks the Spot treasure clues): plays the dig animation and
 * publishes a [DigEvent]; nobody claiming the spot gives "nothing of interest" (OPEN-QUB2: wording not published for this
 * server's generic dig).
 */
public class QuestDigging : ContentPlugin {
    override fun ContentScope.register() {
        onItemOp(Objs.SPADE, "Dig") { _: ButtonClick ->
            val p = this
            p.startScript {
                p.message("You dig a hole in the ground...")
                p.anim(Seqs.HUMAN_DIG)
                delay(DIG_TICKS)
                val dig = DigEvent(p, p.coord)
                p.events.publish(dig)
                if (!dig.handled) p.message("You find nothing of interest.")
            }
        }
    }

    private companion object {
        /** Ticks the dig animation (seq 830) plays before the result: two game ticks (OPEN-QUB2). */
        const val DIG_TICKS = 2
    }
}
