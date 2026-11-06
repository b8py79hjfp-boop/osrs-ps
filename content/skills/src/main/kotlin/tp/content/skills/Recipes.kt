package tp.content.skills

import tp.plugin.Stats
import tp.symbols.Objs

/** One consumed stack of a recipe. */
internal class Ingredient(val obj: Int, val count: Int)

/**
 * A production recipe from `recipes.tsv` (tools/wiki-import/recipes.py, OSRS Wiki `{{Recipe}}` templates): [output] x
 * [count] for [level] in [stat] and [xp], one action per [ticks] ticks. [tools] must be in the backpack and are not
 * used up; [facility] is the lower-case Wiki facility ("furnace", "spinning wheel", ...) or empty; [inputs] are used up.
 * [thread] is set when the recipe also needs thread, which is used up one spool per 5 actions rather than per action
 * (it is not in [inputs]; Wiki "Thread").
 */
internal class Recipe(
    val output: Int,
    val count: Int,
    val stat: Int,
    val level: Int,
    val xp: Double,
    val ticks: Int,
    val tools: List<Int>,
    val facility: String,
    val inputs: List<Ingredient>,
    val thread: Boolean,
    val name: String,
)

internal object RecipeTable {
    /** The columns of `recipes.tsv`; the file's header names them. */
    private const val COLUMNS = 13

    /** Reads `recipes.tsv`. */
    fun load(): List<Recipe> {
        val text = RecipeTable::class.java.getResource("recipes.tsv")?.readText() ?: error("recipes.tsv missing")
        return text
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line -> parse(line.split('\t')) }
            .toList()
    }

    private fun parse(cols: List<String>): Recipe {
        require(cols.size == COLUMNS) { "recipes.tsv row has ${cols.size} columns: $cols" }
        val stat = if (cols[2] == "smithing") Stats.SMITHING else Stats.CRAFTING
        val inputs =
            cols[11].split(',').filter { it.isNotEmpty() }.map {
                val (obj, count) = it.split(':')
                Ingredient(obj.toInt(), count.toInt())
            }
        return Recipe(
            output = cols[0].toInt(),
            count = cols[1].toInt(),
            stat = stat,
            level = cols[3].toInt(),
            xp = cols[4].toDouble(),
            ticks = cols[5].toInt(),
            tools = cols[9].split(',').filter { it.isNotEmpty() }.map { it.toInt() },
            facility = cols[10],
            inputs = inputs.filter { it.obj != Objs.THREAD },
            thread = stat == Stats.CRAFTING && inputs.any { it.obj == Objs.THREAD },
            name = cols[12],
        )
    }
}
