package tp.content.diaries

import tp.plugin.NpcRef
import tp.plugin.PlayerScript
import tp.plugin.chatNpc
import tp.plugin.chatPlayer
import tp.plugin.choice
import tp.plugin.mesbox

/**
 * The Wiki transcripts (`Transcript:<taskmaster>`) of the twelve taskmasters (docs/mechanics/achievement-diaries.md OPEN-AD12),
 * written to `diary-dialogue.tsv` by tools/wiki-import/achievement_diaries.py in the node format of `npc-dialogue.tsv`
 * (`N`/`P`/`M` next text, `C` title count (text target)..., `E`). A `tree <area> <kind>` row starts a tree: `standard` (the
 * "Standard dialogue" section) or a tier name (the "<Tier> tasks completed" section, the claim). Two message boxes are
 * markers the importer left in place of the Wiki's action templates: [CLAIM] where the reward is handed over and the [REPLACE]
 * prefix of the box where the tier item is given again.
 */
internal class DiaryDialogue private constructor(private val trees: Map<String, Array<Node>>) {
    sealed interface Node {
        class Line(val player: Boolean, val next: Int, val text: String) : Node

        class Message(val next: Int, val text: String) : Node

        class Menu(val title: String, val options: Array<String>, val targets: IntArray) : Node

        data object End : Node
    }

    /** What the conversation can do to the player's diary (claim / replacement) and what it needs to know. */
    interface Actions {
        /** Hands over the claimable tier's reward; false when there is no room. */
        fun claim(): Boolean

        /** Gives the tier item again: a line to say instead of the Wiki's message box when it could not be given. */
        fun replace(): String?

        /** Whether the player has claimed any tier (the "another item" option is offered only then). */
        fun hasClaimed(): Boolean
    }

    fun has(area: Int, kind: String): Boolean = trees.containsKey("$area:$kind")

    suspend fun PlayerScript.play(area: Int, kind: String, npc: NpcRef, actions: Actions) {
        val tree = trees["$area:$kind"] ?: return
        var at = 0
        while (at in tree.indices) {
            at =
                when (val node = tree[at]) {
                    is Node.Line -> {
                        val text = node.text.replace(NAME_TOKEN, player.username)
                        if (node.player) chatPlayer(text) else chatNpc(npc, text)
                        node.next
                    }

                    is Node.Message -> {
                        message(node, npc, actions)
                    }

                    is Node.Menu -> {
                        menu(node, actions)
                    }

                    Node.End -> {
                        END
                    }
                }
        }
    }

    private suspend fun PlayerScript.message(node: Node.Message, npc: NpcRef, actions: Actions): Int {
        when {
            node.text == CLAIM -> {
                if (!actions.claim()) {
                    chatNpc(npc, NO_ROOM)
                    return END
                }
            }

            node.text.startsWith(REPLACE) -> {
                val refusal = actions.replace()
                if (refusal == null) mesbox(node.text.removePrefix(REPLACE)) else chatNpc(npc, refusal)
            }

            else -> {
                mesbox(node.text)
            }
        }
        return node.next
    }

    private suspend fun PlayerScript.menu(node: Node.Menu, actions: Actions): Int {
        val offered = node.options.indices.filter { actions.hasClaimed() || !ANOTHER_OPTION.containsMatchIn(node.options[it]) }
        if (offered.size < MIN_OPTIONS) return END
        val pick = choice(node.title, *offered.map { node.options[it] }.toTypedArray())
        return if (pick == 0) END else node.targets[offered[pick - 1]]
    }

    companion object {
        /** The importer's marker for the player's name. */
        private const val NAME_TOKEN = "{name}"
        const val CLAIM = "@@CLAIM@@"
        const val REPLACE = "@@REPLACE@@"
        private val ANOTHER_OPTION = Regex("^Can I have (another|some more)")
        private const val NO_ROOM = "You haven't got room for your reward. Make space in your backpack and come back."
        private const val END = -1
        private const val MIN_OPTIONS = 2
        private const val RESOURCE = "diary-dialogue.tsv"
        private const val MENU_FIRST = 3

        fun load(): DiaryDialogue {
            val text = DiaryDialogue::class.java.getResource(RESOURCE)?.readText() ?: error("$RESOURCE missing")
            val trees = HashMap<String, Array<Node>>()
            var key: String? = null
            val rows = ArrayList<List<String>>()

            fun finish() {
                val k = key ?: return
                trees[k] = Array(rows.size) { node(rows[it], rows.size) }
                rows.clear()
            }
            for (line in text.lines()) {
                if (line.isEmpty() || line.startsWith('#')) continue
                val c = line.split('\t')
                if (c[0] == "tree") {
                    finish()
                    key = "${c[1]}:${c[2]}"
                } else {
                    rows += c
                }
            }
            finish()
            return DiaryDialogue(trees)
        }

        private fun node(c: List<String>, size: Int): Node {
            fun index(s: String?): Int = s?.toIntOrNull()?.takeIf { it in 0 until size } ?: END
            return when (c[0]) {
                "N", "P" -> Node.Line(c[0] == "P", index(c.getOrNull(1)), c.getOrElse(2) { "" })
                "M" -> Node.Message(index(c.getOrNull(1)), c.getOrElse(2) { "" })
                "C" -> menuNode(c, size)
                else -> Node.End
            }
        }

        private fun menuNode(c: List<String>, size: Int): Node {
            val count = c.getOrNull(2)?.toIntOrNull() ?: return Node.End
            if (count < MIN_OPTIONS || c.size < MENU_FIRST + count * 2) return Node.End
            val options = Array(count) { c[MENU_FIRST + it * 2] }
            val targets = IntArray(count) { c[MENU_FIRST + it * 2 + 1].toIntOrNull()?.takeIf { t -> t in 0 until size } ?: END }
            return Node.Menu(c[1], options, targets)
        }
    }
}
