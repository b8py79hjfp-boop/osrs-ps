package tp.content.magic

import tp.plugin.AttackCheck
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.ItemOnItem
import tp.plugin.LogoutEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SpellRunes
import tp.plugin.Stats
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Basic, Adept, Expert and Master Reanimation (docs/mechanics/magic-utility.md RE1-RE5): an ensouled head in the backpack
 * becomes a reanimated creature that only its caster fights; killing it gives Prayer xp.
 */
public class Reanimation : ContentPlugin {
    /** An ensouled head (cache name), the spell tier that reanimates it, the creature it makes and the Prayer xp of its kill. */
    private class Head(val name: String, val spell: UtilitySpell, val npc: Int, val prayerXp: Double)

    private var heads: Map<Int, Head> = emptyMap()
    private var creatures: Map<Int, Double> = emptyMap()

    override fun ContentScope.register() {
        val bus = events
        onWorldStart { load(this) }
        for (spell in SPELLS) onButtonT(spell.component) { event -> cast(spell, event) }
        // RE3: only the caster may fight the creature, and it fights nobody else.
        bus.on<AttackCheck> { check ->
            val attacker = check.attacker
            val target = check.target
            val owner = (attacker as? NpcRef)?.get(OWNER) ?: (target as? NpcRef)?.get(OWNER)
            val other = if (attacker is NpcRef) target else attacker
            if (owner != null && other != owner) check.deny(NOT_YOURS)
        }
        // RE4: the kill gives Prayer xp to the caster.
        bus.on<DeathEvent> { e ->
            val npc = e.entity as? NpcRef ?: return@on
            val owner = npc[OWNER] ?: return@on
            owner[CREATURE] = null
            creatures[npc.type]?.let { xp -> owner.addXp(Stats.PRAYER, xp) }
        }
        bus.on<LogoutEvent> { e -> e.player[CREATURE]?.takeIf { it.isInWorld }?.despawn() }
    }

    private fun load(world: WorldRef) {
        val map = HashMap<Int, Head>()
        for ((spell, rows) in TIERS) {
            for ((name, npc, xp) in rows) {
                val obj = ItemNames.id(world, "Ensouled $name head")
                if (obj >= 0) map[obj] = Head(name, spell, npc, xp)
            }
        }
        heads = map
        creatures = map.values.associate { it.npc to it.prayerXp }
    }

    private fun PlayerRef.cast(spell: UtilitySpell, event: ItemOnItem) {
        val slot = Casting.targetSlot(this, event) ?: return
        if (!Casting.ready(this, spell) { cast(spell, event) }) return
        val head = heads[event.toObj] ?: return message(NOT_A_HEAD)
        if (head.spell !== spell) return message(WRONG_SPELL)
        // RE2: the head must be reanimated by the Dark Altar (the drop-place rule is OPEN-MU12).
        if (!nearDarkAltar(coord)) return message(TOO_FAR)
        if (this[CREATURE]?.isInWorld == true) return message(FINISH_LAST)
        val tile = freeTile() ?: return message(NO_ROOM)
        val done =
            transaction("reanimation") {
                SpellRunes.take(this, this@cast, spell.runes) && removeAt(Invs.INV, slot, event.toObj, 1)
            }
        if (!done) return message(Casting.NO_RUNES)
        Casting.finish(this, spell)
        spotAnimAt(tile, SpotAnims.ARCEUUS_NECROMANCY_SPAWNING)
        val npc = spawnNpc(head.npc, tile, 0)
        npc[OWNER] = this
        this[CREATURE] = npc
    }

    /** A walkable tile next to the caster for the creature, or null. */
    private fun PlayerRef.freeTile(): CoordGrid? {
        val here = coord
        for (dz in -1..1) {
            for (dx in -1..1) {
                val tile = CoordGrid(here.x + dx, here.z + dz, here.level)
                if (tile != here && isWalkable(tile)) return tile
            }
        }
        return null
    }

    private fun nearDarkAltar(c: CoordGrid): Boolean = c.level == 0 && c.x in ALTAR_X && c.z in ALTAR_Z

    private companion object {
        /** The creature's caster, set on the NPC. */
        val OWNER = AttrKey<PlayerRef>("reanimated_owner")

        /** The caster's living creature. */
        val CREATURE = AttrKey<NpcRef>("reanimated_creature")

        /** Wiki "Reanimation spells": the area of the Dark Altar's range of influence (polygon 1664,3871 to 1746,3897). */
        val ALTAR_X = 1664..1746
        val ALTAR_Z = 3871..3897

        /** Wiki "Reanimation spells": the messages. */
        const val TOO_FAR =
            "That creature cannot be reanimated so far from where it died, but the power of the crystals by the dark altar " +
                "will increase the potency of the spell."
        const val NO_ROOM = "The creature wouldn't have room to re-animate there."
        const val FINISH_LAST = "You should finish off your last one first."

        /** OPEN-MU4: no Wiki text for these refusals. */
        const val NOT_A_HEAD = "You can't reanimate that."
        const val WRONG_SPELL = "You need a different reanimation spell for that head."
        const val NOT_YOURS = "That creature is not yours to fight."

        private fun spell(component: Int, level: Int, xp: Double, vararg runes: Pair<Int, Int>) =
            UtilitySpell(
                component,
                Book.ARCEUUS,
                level,
                xp,
                runes.toList(),
                REANIMATION_TICKS,
                Seqs.ARCEUUS_NECROMANCY_PLAYERANIM,
                SpotAnims.ARCEUUS_NECROMANCY_PLAYERSPOT,
            )

        /** The Wiki infoboxes give no speed for the Arceuus utility spells: 4 ticks like the Arceuus teleports (OPEN-MU2). */
        const val REANIMATION_TICKS = 4

        /** Wiki "Basic / Adept / Expert / Master Reanimation": level, xp and runes. */
        @Suppress("MagicNumber")
        val BASIC = spell(Components.Magic_Spellbook.REANIMATION_BASIC, 16, 32.0, Objs.BODYRUNE to 4, Objs.NATURERUNE to 2)

        @Suppress("MagicNumber")
        val ADEPT =
            spell(Components.Magic_Spellbook.REANIMATION_ADEPT, 41, 80.0, Objs.BODYRUNE to 4, Objs.NATURERUNE to 3, Objs.SOULRUNE to 1)

        @Suppress("MagicNumber")
        val EXPERT =
            spell(Components.Magic_Spellbook.REANIMATION_EXPERT, 72, 138.0, Objs.NATURERUNE to 3, Objs.BLOODRUNE to 1, Objs.SOULRUNE to 2)

        @Suppress("MagicNumber")
        val MASTER =
            spell(Components.Magic_Spellbook.REANIMATION_MASTER, 90, 170.0, Objs.NATURERUNE to 4, Objs.BLOODRUNE to 2, Objs.SOULRUNE to 4)

        val SPELLS: List<UtilitySpell> = listOf(BASIC, ADEPT, EXPERT, MASTER)

        /**
         * Wiki "Reanimation spells" and the four spell pages: head, creature (cache npc `arceuus_reanimated_*`) and Prayer xp of
         * the kill. Heads are cache names "Ensouled <name> head".
         */
        @Suppress("MagicNumber")
        val TIERS: List<Pair<UtilitySpell, List<Triple<String, Int, Double>>>> =
            listOf(
                BASIC to
                    listOf(
                        Triple("goblin", Npcs.ARCEUUS_REANIMATED_GOBLIN, 130.0),
                        Triple("monkey", Npcs.ARCEUUS_REANIMATED_MONKEY, 182.0),
                        Triple("imp", Npcs.ARCEUUS_REANIMATED_IMP, 286.0),
                        Triple("minotaur", Npcs.ARCEUUS_REANIMATED_MINOTAUR, 364.0),
                        Triple("scorpion", Npcs.ARCEUUS_REANIMATED_SCORPION, 454.0),
                        Triple("bear", Npcs.ARCEUUS_REANIMATED_BEAR, 480.0),
                        Triple("unicorn", Npcs.ARCEUUS_REANIMATED_UNICORN, 494.0),
                    ),
                ADEPT to
                    listOf(
                        Triple("dog", Npcs.ARCEUUS_REANIMATED_DOG, 520.0),
                        Triple("chaos druid", Npcs.ARCEUUS_REANIMATED_CHAOSDRUID, 584.0),
                        Triple("giant", Npcs.ARCEUUS_REANIMATED_GIANT, 650.0),
                        Triple("ogre", Npcs.ARCEUUS_REANIMATED_OGRE, 716.0),
                        Triple("elf", Npcs.ARCEUUS_REANIMATED_ELF, 754.0),
                        Triple("troll", Npcs.ARCEUUS_REANIMATED_TROLL, 780.0),
                        Triple("horror", Npcs.ARCEUUS_REANIMATED_HORROR, 832.0),
                    ),
                EXPERT to
                    listOf(
                        Triple("kalphite", Npcs.ARCEUUS_REANIMATED_KALPHITE, 884.0),
                        Triple("dagannoth", Npcs.ARCEUUS_REANIMATED_DAGANNOTH, 936.0),
                        Triple("bloodveld", Npcs.ARCEUUS_REANIMATED_BLOODVELD, 1040.0),
                        Triple("tzhaar", Npcs.ARCEUUS_REANIMATED_TZHAAR, 1104.0),
                        Triple("demon", Npcs.ARCEUUS_REANIMATED_DEMON, 1170.0),
                        Triple("hellhound", Npcs.ARCEUUS_REANIMATED_HELLHOUND, 1200.0),
                    ),
                MASTER to
                    listOf(
                        Triple("aviansie", Npcs.ARCEUUS_REANIMATED_AVIANSIE, 1234.0),
                        Triple("abyssal", Npcs.ARCEUUS_REANIMATED_ABYSSAL, 1300.0),
                        Triple("dragon", Npcs.ARCEUUS_REANIMATED_DRAGON, 1560.0),
                    ),
            )
    }
}
