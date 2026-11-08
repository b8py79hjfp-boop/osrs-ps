package tp.content.drops

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.commands.Category
import tp.plugin.commands.showList
import tp.util.Rank
import tp.util.commands.Impact
import kotlin.math.roundToLong

/** `::drops <npc>` (commands brief §6): the Wiki drop table with rates, in the list view. */
public class DropsCommand : ContentPlugin {
    override fun ContentScope.register() {
        command("drops", Rank.PLAYER, Category.INFO, Impact.INFO) {
            description("Shows an NPC's drop table with rates.")
            example("::drops abyssal demon", "::drops 415")
            val npc = npc("npc")
            signature(npc) { ctx ->
                val p = ctx.player
                val id = ctx[npc]
                val drops = DropData.of(id) ?: return@signature ctx.fail("${p.npcName(id)} has no drop table.")
                p.showList("${p.npcName(id)} drops", rows(p, drops))
                ctx.done()
            }
        }
    }

    private fun rows(p: PlayerRef, d: NpcDrops): List<String> =
        buildList {
            for ((label, lines) in listOf("Always" to d.always, "Pre-roll" to d.pre, "Main" to d.main + d.independent)) {
                for (line in lines) add("$label: ${describe(p, line)}")
            }
            for (line in d.secondary) add("Secondary: ${describe(p, line)}")
            for (line in d.tertiary) add("Tertiary: ${describe(p, line)}")
        }

    private fun describe(p: PlayerRef, line: DropLine): String {
        val what =
            when {
                line.table != null -> "${line.table} table"
                line.obj == DropData.NOTHING -> "Nothing"
                else -> p.objName(line.obj) + if (line.noted) " (noted)" else ""
            }
        val r = line.ranges
        val qty =
            if (r.size == 2 &&
                r[0] == r[1]
            ) {
                "${r[0]}"
            } else {
                r.toList().chunked(2).joinToString(",") { (a, b) -> if (a == b) "$a" else "$a-$b" }
            }
        return "$what x$qty, ${rate(line.chance)}${if (line.rolls > 1) " (${line.rolls} rolls)" else ""}"
    }

    /** "Always", or 1/N rounded the way the Wiki shows it. */
    private fun rate(chance: Double): String =
        when {
            chance >= 1.0 -> "always"

            chance <= 0.0 -> "never"

            else -> "1/${(1.0 / chance).let {
                if (it < DECIMALS_BELOW) {
                    "%.2f"
                        .format(
                            it,
                        ).trimEnd('0')
                        .trimEnd('.')
                } else {
                    it.roundToLong().toString()
                }
            }}"
        }

    private companion object {
        /** Rates rarer than 1/10 show whole numbers (1/128); commoner ones keep two decimals (1/2.5), as the Wiki does. */
        const val DECIMALS_BELOW = 10.0
    }
}
