package tp.content.diaries

import tp.plugin.DeathEvent
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs

/**
 * An NPC-kill task (docs/mechanics/achievement-diaries.md AD7). [parts] are the kills the task asks for, each a list of NPC
 * names any of which counts; the task completes when every part has been killed (one part for "kill a X"). [label] is a
 * word of the Wiki task text, checked at world start so a renumbered Wiki page cannot silently move a hook to the wrong task.
 */
internal class KillHook(
    val area: Int,
    val tier: Int,
    val index: Int,
    val label: String,
    val parts: List<List<String>>,
    /** The Wiki text says "in the Wilderness": the killer must stand in the Wilderness ([SharedAttrs.IN_WILDERNESS]). */
    val wilderness: Boolean = false,
) {
    val id: TaskId = TaskId.of(area, tier, index)
}

internal object DiaryHooks {
    /** Cache enum 595 indices (client script 4073's order). */
    private const val KARAMJA = 0
    private const val ARDOUGNE = 1
    private const val FALADOR = 2
    private const val FREMENNIK = 3
    private const val KANDARIN = 4
    private const val DESERT = 5
    private const val LUMBRIDGE = 6
    private const val MORYTANIA = 7
    private const val WILDERNESS = 9
    private const val WESTERN = 10
    private const val KOUREND = 11

    private const val EASY = 0
    private const val MEDIUM = 1
    private const val HARD = 2
    private const val ELITE = 3

    private fun one(vararg names: String): List<List<String>> = listOf(names.toList())

    private fun kill(area: Int, tier: Int, index: Int, label: String, vararg names: String): KillHook =
        KillHook(area, tier, index, label, one(*names))

    private fun wild(area: Int, tier: Int, index: Int, label: String, vararg names: String): KillHook =
        KillHook(area, tier, index, label, one(*names), wilderness = true)

    /**
     * The tasks whose trigger is observable as a [DeathEvent] of an NPC the content already spawns. Location, weapon and
     * equipment conditions of the Wiki text are not checked unless the table says so (OPEN-AD4): the NPC names are unique to the
     * task's place except where the mechanics doc lists them.
     */
    val KILLS: List<KillHook> =
        listOf(
            kill(KARAMJA, EASY, 10, "jogre", "Jogre"),
            kill(KARAMJA, HARD, 2, "Ket-Zek", "Ket-Zek"),
            kill(KARAMJA, HARD, 6, "deathwing", "Death wing"),
            kill(ARDOUGNE, MEDIUM, 10, "swordchick", "Swordchick"),
            kill(FALADOR, MEDIUM, 5, "Mogre", "Mogre"),
            kill(FALADOR, HARD, 3, "Giant Mole", "Giant Mole"),
            kill(FALADOR, HARD, 4, "Skeletal Wyvern", "Skeletal Wyvern"),
            kill(FREMENNIK, MEDIUM, 1, "Brine rat", "Brine rat"),
            KillHook(
                FREMENNIK,
                ELITE,
                1,
                "Dagannoth Kings",
                listOf(listOf("Dagannoth Rex"), listOf("Dagannoth Prime"), listOf("Dagannoth Supreme")),
            ),
            KillHook(
                FREMENNIK,
                ELITE,
                5,
                "generals",
                listOf(listOf("General Graardor"), listOf("Kree'arra"), listOf("Commander Zilyana"), listOf("K'ril Tsutsaroth")),
            ),
            kill(KANDARIN, HARD, 7, "Shadow Hound", "Shadow hound"),
            kill(KANDARIN, HARD, 8, "Mithril Dragon", "Mithril dragon"),
            kill(DESERT, EASY, 5, "Vulture", "Vulture"),
            kill(DESERT, HARD, 4, "Kalphite Queen", "Kalphite Queen"),
            kill(LUMBRIDGE, EASY, 2, "Cave bug", "Cave bug"),
            kill(MORYTANIA, EASY, 4, "Banshee", "Banshee"),
            kill(MORYTANIA, EASY, 7, "Ghoul", "Ghoul"),
            kill(MORYTANIA, MEDIUM, 5, "Terror Dog", "Terror dog"),
            kill(MORYTANIA, MEDIUM, 9, "Fever Spider", "Fever spider"),
            kill(MORYTANIA, HARD, 6, "Cave Horror", "Cave horror"),
            wild(WILDERNESS, EASY, 5, "Mammoth", "Mammoth"),
            wild(WILDERNESS, EASY, 6, "Earth Warrior", "Earth warrior"),
            wild(WILDERNESS, MEDIUM, 5, "Green Dragon", "Green dragon"),
            wild(WILDERNESS, MEDIUM, 6, "Ankou", "Ankou"),
            wild(WILDERNESS, MEDIUM, 8, "Bloodveld", "Bloodveld"),
            wild(WILDERNESS, HARD, 5, "Lava Dragon", "Lava dragon"),
            wild(WILDERNESS, HARD, 6, "Chaos Elemental", "Chaos Elemental"),
            KillHook(
                WILDERNESS,
                HARD,
                7,
                "Crazy Arc",
                listOf(listOf("Crazy archaeologist"), listOf("Chaos Fanatic"), listOf("Scorpia")),
                wilderness = true,
            ),
            wild(WILDERNESS, HARD, 9, "Spiritual warrior", "Spiritual warrior"),
            KillHook(
                WILDERNESS,
                ELITE,
                1,
                "Callisto",
                // Wiki note: the lesser versions (Artio, Spindel, Calvar'ion) count in place of the greater ones.
                listOf(listOf("Callisto", "Artio"), listOf("Venenatis", "Spindel"), listOf("Vet'ion", "Calvar'ion")),
                wilderness = true,
            ),
            wild(WILDERNESS, ELITE, 6, "spiritual mage", "Spiritual mage"),
            kill(WESTERN, EASY, 11, "Terrorbird", "Terrorbird"),
            kill(WESTERN, HARD, 11, "Zulrah", "Zulrah"),
            kill(WESTERN, ELITE, 2, "Thermonuclear", "Thermonuclear smoke devil"),
            kill(KOUREND, EASY, 2, "Sandcrab", "Sand crab"),
            kill(KOUREND, MEDIUM, 2, "Lizardman", "Lizardman"),
            kill(KOUREND, HARD, 9, "Wyrm", "Wyrm"),
            kill(KOUREND, ELITE, 3, "Skotizo", "Skotizo"),
            kill(KOUREND, ELITE, 5, "Hydra", "Hydra"),
        )

    /** Name key: lower case letters and digits only, so "Ket-Zek", "K'ril Tsutsaroth" and "Death wing" compare loosely. */
    fun key(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** NPC name key to the (hook, part) pairs it can complete. */
    fun index(): Map<String, List<Pair<KillHook, Int>>> {
        val out = HashMap<String, MutableList<Pair<KillHook, Int>>>()
        for (hook in KILLS) {
            for ((part, names) in hook.parts.withIndex()) {
                for (name in names) out.getOrPut(key(name)) { ArrayList() } += hook to part
            }
        }
        return out
    }

    /** Fails the world start when a hook's label is not in the task text it points at (the Wiki data changed). */
    fun verify(data: DiaryData) {
        for (hook in KILLS) {
            val task = checkNotNull(data.task(hook.id)) { "diary hook ${hook.id.raw} has no task" }
            check(task.text.contains(hook.label, ignoreCase = true)) {
                "diary hook ${hook.id.raw} expects '${hook.label}' in '${task.text}'"
            }
        }
    }

    /** Applies a kill to the hook entries [matches]; completes the task once every part has been killed. */
    fun apply(diaries: Diaries, player: PlayerRef, matches: List<Pair<KillHook, Int>>) {
        for ((hook, part) in matches) {
            if (hook.wilderness && player[SharedAttrs.IN_WILDERNESS] != true) continue
            if (hook.parts.size == 1) {
                diaries.complete(player, hook.id)
                continue
            }
            val key = Diaries.partsKey(hook.id)
            val parts = (player.saved(key) ?: 0L) or (1L shl part)
            player.setSaved(key, parts)
            if (parts == (1L shl hook.parts.size) - 1) diaries.complete(player, hook.id)
        }
    }

    /** The player a kill is credited to: the one the loot is made for, else the killer (AD7). */
    fun creditedTo(event: DeathEvent): PlayerRef? = event.lootFor ?: event.killer as? PlayerRef
}
