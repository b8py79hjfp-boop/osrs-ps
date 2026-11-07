package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.plugin.mesbox
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Hunters' Rumours (docs/mechanics/hunter.md HU40 .. HU42): a Guild Hunter of the Hunter Guild assigns a creature, the catches of
 * that creature drop a rare creature part (a fixed chance per hunting method plus a "pity" count), and the part is handed back for
 * Hunter xp and a hunters' loot sack. State is per player in `saved`.
 */
public class HunterRumours : ContentPlugin {
    override fun ContentScope.register() {
        for (hunter in GUILD_HUNTERS.keys) {
            onOpNpc(hunter, OP_TALK) { talk(player, hunter) }
            onOpNpc(hunter, OP_RUMOUR) { talk(player, hunter) }
        }
    }

    private suspend fun PlayerScript.talk(p: PlayerRef, hunter: Int) {
        val tier = GUILD_HUNTERS.getValue(hunter)
        val key = KEY_ACTIVE + hunter
        val active = p.saved(key)?.toInt() ?: 0
        if (active == 0) return assign(p, hunter, tier)
        val creature = ASSIGNABLE[active - 1].creature
        val part = HunterData.BY_NAME.getValue(creature).part
        if (p.saved(KEY_PART_FROM) == active.toLong() && p.inv(Invs.INV).total(part) > 0) return handIn(p, hunter, tier, part)
        val pick = choice("You are hunting a $creature.", "Carry on.", "Cancel the rumour.")
        if (pick == 2) p.setSaved(key, null)
    }

    private suspend fun PlayerScript.assign(p: PlayerRef, hunter: Int, tier: Tier) {
        val level = p.baseLevel(Stats.HUNTER)
        if (level < tier.level) return mesbox("You need a Hunter level of ${tier.level} (not boostable) for these rumours.")
        // The same creature cannot be held from two Guild Hunters at once (Wiki "Hunters' Rumours").
        val taken = GUILD_HUNTERS.keys.mapNotNull { p.saved(KEY_ACTIVE + it)?.toInt() }.toSet()
        val options =
            ASSIGNABLE.indices.filter { i ->
                hunter in ASSIGNABLE[i].hunters && (i + 1) !in taken && HunterData.BY_NAME.getValue(ASSIGNABLE[i].creature).level <= level
            }
        if (options.isEmpty()) return mesbox("I have no rumours for you right now.")
        val pick = options[p.random(options.size)]
        p.setSaved(KEY_ACTIVE + hunter, (pick + 1).toLong())
        mesbox(
            "There are rumours of a rare ${ASSIGNABLE[pick].creature}. Hunt one until you find a rare piece, then bring it to me.",
        )
    }

    private suspend fun PlayerScript.handIn(p: PlayerRef, hunter: Int, tier: Tier, part: Int) {
        val sack = SACKS[tier.sack]
        val given = p.transaction("hunter rumour") { remove(Invs.INV, part, 1) && add(Invs.INV, sack, 1) }
        if (!given) return mesbox("You don't have enough inventory space.")
        p.setSaved(KEY_ACTIVE + hunter, null)
        p.setSaved(KEY_PART_FROM, null)
        p.setSaved(KEY_DONE, (p.saved(KEY_DONE) ?: 0L) + 1)
        p.addXp(Stats.HUNTER, (p.baseLevel(Stats.HUNTER) + XP_LEVEL_OFFSET) * tier.xpModifier.toDouble())
        mesbox("Well done. Here is your reward.")
    }

    /** A rumour tier: its Hunter level, the xp modifier and the loot sack index (Wiki "Hunters' Rumours", Rewards). */
    private class Tier(val level: Int, val xpModifier: Int, val sack: Int)

    /** One creature a Guild Hunter can assign, with the Guild Hunters (npc ids) that can (Wiki table, Yes columns). */
    private class Assignable(val creature: String, val hunters: Set<Int>)

    /** How a hunting method drops the rare part: 1 in [rate], and for sure after [pity] catches ([pityOutfit] in the guild outfit). */
    private class Method(val rate: Int, val pity: Int, val pityOutfit: Int)

    internal companion object {
        const val KEY_ACTIVE = "hunter.rumour."
        const val KEY_PART_FROM = "hunter.rumour.partFrom"
        const val KEY_PITY = "hunter.rumour.pity"
        const val KEY_DONE = "hunter.rumour.done"
        const val OP_TALK = 1
        const val OP_RUMOUR = 3

        /** Wiki: experience = (Hunter level + 5) * modifier. */
        const val XP_LEVEL_OFFSET = 5

        private const val NOVICE_LEVEL = 46
        private const val ADEPT_LEVEL = 57
        private const val EXPERT_LEVEL = 72
        private const val MASTER_LEVEL = 91
        private const val NOVICE_XP = 50
        private const val EXPERT_XP = 55
        private const val MASTER_XP = 60

        private val NOVICE = Tier(NOVICE_LEVEL, NOVICE_XP, 0)
        private val ADEPT = Tier(ADEPT_LEVEL, NOVICE_XP, 1)
        private val EXPERT = Tier(EXPERT_LEVEL, EXPERT_XP, 2)
        private val MASTER = Tier(MASTER_LEVEL, MASTER_XP, 3)

        /** The Guild Hunters (cache npcs 13121 .. 13126, op "Rumour"). */
        private val GILMAN = Npcs.HG_GILMAN
        private val GUILD_HUNTERS: Map<Int, Tier> =
            mapOf(
                GILMAN to NOVICE,
                GILMAN + 1 to ADEPT,
                GILMAN + 2 to ADEPT,
                GILMAN + 3 to EXPERT,
                GILMAN + 4 to EXPERT,
                GILMAN + 5 to MASTER,
            )

        /** Loot sacks by tier: basic, adept, expert, master. */
        private val SACKS = intArrayOf(Objs.HG_LOOTSACK_T0, Objs.HG_LOOTSACK_T1, Objs.HG_LOOTSACK_T2, Objs.HG_LOOTSACK_T3)

        private val OUTFIT = intArrayOf(Objs.HG_HUNTER_HOOD, Objs.HG_HUNTER_TOP, Objs.HG_HUNTER_LEGS, Objs.HG_HUNTER_BOOTS)

        /** Rare-part methods (Wiki "Hunters' Rumours", Drop rates). */
        private val METHODS =
            mapOf(
                TrapKind.BIRD to Method(20, 40, 38),
                TrapKind.BOX to Method(50, 100, 94),
                TrapKind.NET to Method(40, 80, 76),
                TrapKind.DEADFALL to Method(15, 30, 28),
                TrapKind.FALCON to Method(10, 20, 18),
                TrapKind.NETTRAP to Method(25, 50, 46),
            )

        private fun hunters(vararg offsets: Int): Set<Int> = offsets.map { Npcs.HG_GILMAN + it }.toSet()

        /** The creatures of the Wiki table that this server can catch, with the hunters that assign them (offset 0 Gilman .. 5 Wolf). */
        private val ASSIGNABLE =
            listOf(
                Assignable("Tropical wagtail", hunters(0)),
                Assignable("Wild kebbit", hunters(0)),
                Assignable("Swamp lizard (Hunter)", hunters(0, 2)),
                Assignable("Barb-tailed kebbit", hunters(0)),
                Assignable("Prickly kebbit", hunters(0)),
                Assignable("Spotted kebbit", hunters(0, 1, 2)),
                Assignable("Black warlock", hunters(0, 2)),
                Assignable("Orange salamander (Hunter)", hunters(0, 1, 2, 3)),
                Assignable("Sabre-toothed kebbit", hunters(0, 1, 2, 3, 4)),
                Assignable("Chinchompa (Hunter)", hunters(0, 2, 3, 4)),
                Assignable("Dark kebbit", hunters(0, 2, 3, 4)),
                Assignable("Pyre fox", hunters(0, 1, 2)),
                Assignable("Red salamander (Hunter)", hunters(0, 1, 3, 4, 5)),
                Assignable("Carnivorous chinchompa", hunters(0, 1, 2, 3, 4, 5)),
                Assignable("Dashing kebbit", hunters(0, 3, 4, 5)),
                Assignable("Tecu salamander (Hunter)", hunters(0, 3, 5)),
            )

        /** Every rare part, so that holding one blocks the next (Wiki: "Having a rare part in your inventory prevents..."). */
        private val PARTS: Set<Int> by lazy {
            HunterData.CREATURES
                .map { it.part }
                .filter { it >= 0 }
                .toSet()
        }

        /**
         * A catch of [creature] by [p]: while the creature is under a rumour of [p], the rare part drops with the method's chance or
         * once the pity count is reached (Wiki "Hunters' Rumours", Drop rates).
         */
        fun caught(p: PlayerRef, creature: Creature) {
            val index = ASSIGNABLE.indexOfFirst { it.creature == creature.name }
            if (index < 0 || creature.part < 0) return
            if (GUILD_HUNTERS.keys.none { p.saved(KEY_ACTIVE + it) == (index + 1).toLong() }) return
            val method = METHODS[creature.kind] ?: return
            val pity = (p.saved(KEY_PITY) ?: 0L) + 1
            p.setSaved(KEY_PITY, pity)
            val inv = p.inv(Invs.INV)
            if (PARTS.any { inv.total(it) > 0 }) return
            val threshold = if (OUTFIT.all { wears(p, it) }) method.pityOutfit else method.pity
            val pityDrop = pity >= threshold
            if (!pityDrop && p.random(method.rate) != 0) return
            val given = p.transaction("hunter rumour part") { add(Invs.INV, creature.part, 1) }
            if (!given) {
                // A full backpack: the part falls to the ground, but a pity part is destroyed (Wiki, 7 April 2024).
                if (pityDrop) return
                p.transaction("hunter rumour part drop") { createOnGround(creature.part, 1, p.coord, p.username) }
            }
            p.setSaved(KEY_PITY, null)
            p.setSaved(KEY_PART_FROM, (index + 1).toLong())
            p.message("<col=00a000>You find a rare piece of the creature! You should take it back to the Hunter Guild.</col>")
        }

        private fun wears(p: PlayerRef, obj: Int): Boolean = p.inv(Invs.WORN).total(obj) > 0
    }
}
