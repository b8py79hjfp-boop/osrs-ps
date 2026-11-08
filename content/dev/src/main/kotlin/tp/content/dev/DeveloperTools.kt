package tp.content.dev

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.commands.Category
import tp.plugin.commands.CommandBuilder
import tp.plugin.commands.CommandContext
import tp.plugin.commands.CommandResult
import tp.plugin.commands.NameKind
import tp.plugin.commands.ParseContext
import tp.plugin.commands.showList
import tp.symbols.Components
import tp.util.CoordGrid
import tp.util.Rank
import tp.util.commands.Impact

/**
 * The non-economy developer commands of commands brief §6: visual debugging, interface and variable tools, cache search,
 * tick and trace, temporary NPC spawns. Skipped for lack of an API: `::sound`, `::pnpc`/`::unpnpc`, `::debug pid|info`.
 */
public class DeveloperTools : ContentPlugin {
    override fun ContentScope.register() {
        visual()
        interfaces()
        variables()
        tools()
        npcs()
    }

    private fun ContentScope.dev(
        name: String,
        impact: Impact,
        description: String,
        example: String,
        block: CommandBuilder.() -> Unit,
    ) {
        command(name, Rank.DEVELOPER, Category.DEVELOPMENT, impact) {
            description(description)
            example(example)
            block()
        }
    }

    private fun ContentScope.visual() {
        dev("anim", Impact.DEBUG_VISUAL, "Plays animation (seq) <id> on you; -1 stops it.", "::anim 863 2") {
            val id = int("id", min = NO_ID)
            val delay = int("delay", optional = true, min = 0)
            signature(id, delay) { ctx ->
                ctx.player.anim(ctx[id], ctx.opt(delay) ?: 0)
                ctx.done("Animation ${ctx[id]}.")
            }
        }
        dev("gfx", Impact.DEBUG_VISUAL, "Plays spot animation <id> on you, optionally raised by height and delayed.", "::gfx 246 100 0") {
            val id = int("id", min = 0)
            val height = int("height", optional = true, min = 0)
            val delay = int("delay", optional = true, min = 0)
            signature(id, height, delay) { ctx ->
                ctx.player.spotAnim(ctx[id], ctx.opt(height) ?: 0, ctx.opt(delay) ?: 0)
                ctx.done("Spot animation ${ctx[id]}.")
            }
        }
        dev("song", Impact.DEBUG_VISUAL, "Plays music track <id> (cache music id).", "::song 76") {
            val id = int("id", min = 0)
            signature(id) { ctx ->
                ctx.player.playMusic(ctx[id])
                ctx.done("Music track ${ctx[id]}.")
            }
        }
        dev("jingle", Impact.DEBUG_VISUAL, "Plays jingle <id> over the music.", "::jingle 90") {
            val id = int("id", min = 0)
            signature(id) { ctx ->
                ctx.player.jingle(ctx[id])
                ctx.done("Jingle ${ctx[id]}.")
            }
        }
    }

    private fun ContentScope.interfaces() {
        // Opens as the main modal, like the legacy ::if (interface layout checks against the real client).
        dev("if", Impact.OPERATIONS, "Opens interface <id> as the main modal.", "::if 12") {
            val id = int("id", min = 0)
            signature(id) { ctx ->
                ctx.player.openModal(Components.Toplevel.MAINMODAL, ctx[id])
                ctx.done("Opened interface ${ctx[id]}.")
            }
        }
        dev("closeif", Impact.OPERATIONS, "Closes the modal interface.", "::closeif") {
            signature { ctx ->
                ctx.player.closeModal()
                ctx.done("Modal closed.")
            }
        }
        dev(
            "cs2",
            Impact.OPERATIONS,
            "Runs client script <id>; numeric arguments are sent as ints, the rest as strings.",
            "::cs2 1004 1 2",
        ) {
            val id = int("id", min = 0)
            val args = text("args", optional = true)
            signature(id, args) { ctx ->
                val values =
                    ctx
                        .opt(args)
                        .orEmpty()
                        .split(' ')
                        .filter { it.isNotEmpty() }
                        .map<String, Any> { it.toIntOrNull() ?: it }
                ctx.player.clientScript(ctx[id], *values.toTypedArray())
                ctx.done("Ran client script ${ctx[id]} with ${values.size} argument(s).")
            }
        }
    }

    private fun ContentScope.variables() {
        dev("varp", Impact.OPERATIONS, "Shows varp <id>, or sets it to [value].", "::varp 173 1") {
            val id = int("id", min = 0)
            val value = int("value", optional = true)
            signature(id, value) { ctx ->
                val new = ctx.opt(value)
                if (new == null) {
                    ctx.done("varp ${ctx[id]} = ${ctx.player.varp(ctx[id])}")
                } else {
                    ctx.player.setVarp(ctx[id], new)
                    ctx.done("varp ${ctx[id]} set to $new.")
                }
            }
        }
        dev("varbit", Impact.OPERATIONS, "Shows varbit <id>, or sets it to [value] (must fit its bits).", "::varbit 4070 1") {
            val id = int("id", min = 0)
            val value = int("value", optional = true)
            signature(id, value) { ctx ->
                val new = ctx.opt(value)
                when {
                    new == null -> {
                        ctx.done("varbit ${ctx[id]} = ${ctx.player.varbit(ctx[id])}")
                    }

                    !ctx.player.varbitFits(ctx[id], new) -> {
                        ctx.fail("$new does not fit varbit ${ctx[id]} (or it does not exist).")
                    }

                    else -> {
                        ctx.player.setVarbit(ctx[id], new)
                        ctx.done("varbit ${ctx[id]} set to $new.")
                    }
                }
            }
        }
    }

    private fun ContentScope.tools() {
        dev(
            "find",
            Impact.OPERATIONS,
            "Searches cache names for ids; add item, npc or loc as the last word to pick the kind.",
            "::find abyssal whip item",
        ) {
            val query = text("query")
            signature(query) { ctx -> find(ctx, ctx[query]) }
        }
        dev("tick", Impact.OPERATIONS, "Shows this world's current tick.", "::tick") {
            signature { ctx -> ctx.done("Tick ${ctx.player.tick}.") }
        }
        dev("trace", Impact.OPERATIONS, "Streams your tick-inspector trace; run again to stop.", "::trace") {
            signature { ctx -> ctx.done("Tick trace: ${ctx.player.toggleTrace()}") }
        }
    }

    private fun find(ctx: CommandContext, raw: String): CommandResult {
        val words = raw.trim().split(' ').filter { it.isNotEmpty() }
        val kind = words.lastOrNull()?.let { KINDS[it.lowercase()] }
        val needle = (if (kind != null) words.dropLast(1) else words).joinToString(" ").lowercase()
        if (needle.isEmpty()) return ctx.fail("Give a name to search for.")
        val kinds = if (kind != null) listOf(kind) else NameKind.entries
        val parse = ParseContext.of(ctx.player)
        val rows = ArrayList<String>()
        for (k in kinds) {
            for ((name, ids) in parse.names(k)) {
                if (needle in name) rows += "${k.name.lowercase()} $name: ${ids.joinToString(", ")}"
                if (rows.size > MAX_ROWS) break
            }
        }
        if (rows.isEmpty()) return ctx.fail("Nothing named like '$needle'.")
        val shown = rows.sorted().take(MAX_ROWS)
        ctx.player.showList("Find '$needle'", if (rows.size > MAX_ROWS) shown + "... more, narrow the search." else shown)
        return ctx.done()
    }

    private fun ContentScope.npcs() {
        // ECONOMY: a spawned NPC can be killed for its drops, so it creates items (commands brief §3).
        dev("npc", Impact.ECONOMY, "Spawns NPC <npc> on the tile next to you until removed with ::removenpc.", "::npc man") {
            val type = npc("npc")
            signature(type) { ctx ->
                val list = ctx.player[SPAWNED] ?: ArrayList<NpcRef>().also { ctx.player[SPAWNED] = it }
                if (list.size >= MAX_SPAWNED) return@signature ctx.fail("You already have $MAX_SPAWNED spawned NPCs; ::removenpc first.")
                val at = CoordGrid(ctx.player.coord.x + 1, ctx.player.coord.z, ctx.player.coord.level)
                list += ctx.player.spawnNpc(ctx[type], at)
                ctx.done("Spawned NPC ${ctx[type]}.")
            }
        }
        dev("removenpc", Impact.OPERATIONS, "Removes the NPCs you spawned with ::npc.", "::removenpc") {
            signature { ctx ->
                val list = ctx.player[SPAWNED]
                ctx.player[SPAWNED] = null
                list?.forEach { it.despawn() }
                ctx.done("Removed ${list?.size ?: 0} NPC(s).")
            }
        }
    }

    private companion object {
        /** Animation id -1 stops the current animation (cache seq ids start at 0). */
        const val NO_ID = -1L

        /** Rows `::find` returns before it asks to narrow the search. */
        const val MAX_ROWS = 50

        /** Cap on live `::npc` spawns per developer, so a typo loop cannot flood the world. */
        const val MAX_SPAWNED = 20

        val KINDS: Map<String, NameKind> =
            mapOf("item" to NameKind.OBJ, "obj" to NameKind.OBJ, "npc" to NameKind.NPC, "loc" to NameKind.LOC)
        val SPAWNED = AttrKey<MutableList<NpcRef>>("dev_spawned_npcs")
    }
}
