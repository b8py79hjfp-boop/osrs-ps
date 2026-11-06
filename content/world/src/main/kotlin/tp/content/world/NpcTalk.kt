package tp.content.world

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerScript
import tp.plugin.chatNpc
import tp.plugin.chatPlayer
import tp.plugin.choice
import tp.plugin.mesbox

/**
 * "Talk-to" on ordinary NPCs plays their Wiki transcript (`npc-dialogue.tsv`, written by
 * tools/wiki-import/npc_dialogue.py; CC BY-NC-SA 3.0, data/PROVENANCE.md; docs/mechanics/npc-dialogue.md). A transcript
 * is a graph of NPC lines, player lines, message boxes and option menus walked with the dialogue steps. Conditions and
 * actions are not modelled: the importer already chose the default branch and ended the conversation at every action.
 * Specific handlers win: [ContentScope.onOpNpcOption] only applies to NPC types with no id-specific or named handler for
 * the op (bankers, shops, quest NPCs).
 */
public class NpcTalk : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpcOption("Talk-to") { npc ->
            val tree = TREES[npc.type]
            if (tree != null) play(tree, npc)
        }
    }

    private sealed interface Node {
        class Line(val player: Boolean, val next: Int, val text: String) : Node

        class Message(val next: Int, val text: String) : Node

        class Menu(val title: String, val options: Array<String>, val targets: IntArray) : Node

        data object End : Node
    }

    private class Tree(val nodes: Array<Node>)

    private suspend fun PlayerScript.play(tree: Tree, npc: NpcRef) {
        var at = 0
        while (at in tree.nodes.indices) {
            at =
                when (val node = tree.nodes[at]) {
                    is Node.Line -> {
                        val text = node.text.replace(NAME_TOKEN, player.username)
                        if (node.player) chatPlayer(text) else chatNpc(npc, text)
                        node.next
                    }

                    is Node.Message -> {
                        mesbox(node.text.replace(NAME_TOKEN, player.username))
                        node.next
                    }

                    is Node.Menu -> {
                        val pick = choice(node.title, *node.options)
                        if (pick == 0) END else node.targets[pick - 1]
                    }

                    Node.End -> {
                        END
                    }
                }
        }
    }

    private companion object {
        /** The importer's marker for the player's name (`[player name]` in the Wiki transcripts). */
        const val NAME_TOKEN = "{name}"

        /** `next` / `target` of "no node": the conversation ends. */
        const val END = -1

        /** The chat menu title the client shows by default (Dialogue.kt `choice`). */
        const val DEFAULT_TITLE = "Select an Option"

        /** Chat menus take 2..5 options (Dialogue.kt `choice`). */
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 5

        /** Columns of a node row: kind, then next, then text (`N`/`P`/`M`); menus: title, count, then text/target pairs. */
        const val NEXT = 1
        const val TEXT = 2
        const val MENU_COUNT = 2
        const val MENU_FIRST = 3

        /** Columns of a `tree` row: marker, tree number, comma-separated NPC ids. */
        const val TREE_IDS = 2

        val TREES: Map<Int, Tree> = load()

        fun load(): Map<Int, Tree> {
            val text = NpcTalk::class.java.getResource("npc-dialogue.tsv")?.readText() ?: error("npc-dialogue.tsv missing")
            val byNpc = HashMap<Int, Tree>()
            var ids = emptyList<Int>()
            val rows = ArrayList<List<String>>()
            for (line in text.lineSequence()) {
                if (line.isEmpty() || line.startsWith('#')) continue
                val c = line.split('\t')
                if (c[0] != "tree") {
                    rows += c
                    continue
                }
                finish(byNpc, ids, rows)
                ids = c.getOrElse(TREE_IDS) { "" }.split(',').mapNotNull { it.toIntOrNull() }
            }
            finish(byNpc, ids, rows)
            return byNpc
        }

        fun finish(into: MutableMap<Int, Tree>, ids: List<Int>, rows: MutableList<List<String>>) {
            if (rows.isNotEmpty()) {
                val tree = Tree(Array(rows.size) { node(rows[it], rows.size) })
                for (id in ids) into[id] = tree
            }
            rows.clear()
        }

        /** A node of a tree of [size] nodes; targets outside it end the conversation. */
        fun node(c: List<String>, size: Int): Node {
            fun index(s: String?): Int = s?.toIntOrNull()?.takeIf { it in 0 until size } ?: END
            return when (c[0]) {
                "N", "P" -> Node.Line(c[0] == "P", index(c.getOrNull(NEXT)), c.getOrElse(TEXT) { "" })
                "M" -> Node.Message(index(c.getOrNull(NEXT)), c.getOrElse(TEXT) { "" })
                "C" -> menu(c, size) ?: Node.End
                else -> Node.End
            }
        }

        fun menu(c: List<String>, size: Int): Node.Menu? {
            val count = c.getOrNull(MENU_COUNT)?.toIntOrNull() ?: return null
            if (count !in MIN_OPTIONS..MAX_OPTIONS || c.size < MENU_FIRST + count * 2) return null
            val options = Array(count) { c[MENU_FIRST + it * 2] }
            val targets =
                IntArray(count) { c[MENU_FIRST + it * 2 + 1].toIntOrNull()?.takeIf { t -> t in 0 until size } ?: END }
            val title = c.getOrElse(1) { DEFAULT_TITLE }
            return Node.Menu(if (title.equals(DEFAULT_TITLE, ignoreCase = true)) DEFAULT_TITLE else title, options, targets)
        }
    }
}
