package tp.content.magic

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.ItemOnItem
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * Lvl-1 to Lvl-7 Enchant (docs/mechanics/magic-utility.md EN1-EN3): turns one gem jewellery piece of the spell's gem into
 * its enchanted form.
 */
public class EnchantSpells : ContentPlugin {
    /** One spell and its (unenchanted, enchanted) cache names. */
    private class Enchant(val spell: UtilitySpell, val items: List<Pair<String, String>>)

    private var results: Map<Int, Map<Int, Int>> = emptyMap()

    override fun ContentScope.register() {
        onWorldStart {
            results = ENCHANTS.associate { e -> e.spell.component to resolve(this, e) }
        }
        for (enchant in ENCHANTS) onButtonT(enchant.spell.component) { event -> cast(enchant.spell, event) }
    }

    private fun resolve(world: WorldRef, enchant: Enchant): Map<Int, Int> {
        val map = HashMap<Int, Int>()
        for ((from, to) in enchant.items) {
            val fromId = ItemNames.id(world, from)
            val toId = ItemNames.id(world, to)
            if (fromId >= 0 && toId >= 0) map[fromId] = toId
        }
        return map
    }

    private fun PlayerRef.cast(spell: UtilitySpell, event: ItemOnItem) {
        val slot = Casting.targetSlot(this, event) ?: return
        if (!Casting.ready(this, spell) { cast(spell, event) }) return
        // EN2: only the spell's own gem jewellery can be enchanted by it.
        val enchanted = results[spell.component]?.get(event.toObj) ?: return message(WRONG_SPELL)
        val done =
            transaction("enchant jewellery") {
                SpellRunes.take(this, this@cast, spell.runes) && replaceAt(Invs.INV, slot, event.toObj, enchanted)
            }
        if (!done) return message(Casting.NO_RUNES)
        Casting.finish(this, spell)
    }

    private companion object {
        /** OPEN-MU4: the Wiki has no text for an item the spell doesn't work on; the game's usual refusal. */
        const val WRONG_SPELL = "This spell can't be cast on that item."

        @Suppress("LongParameterList")
        private fun spell(
            component: Int,
            level: Int,
            xp: Double,
            seq: Int,
            spot: Int,
            vararg runes: Pair<Int, Int>,
        ): UtilitySpell =
            UtilitySpell(
                component,
                Book.STANDARD,
                level,
                xp,
                runes.toList() + (Objs.COSMICRUNE to 1),
                ENCHANT_TICKS,
                seq,
                spot,
            )

        /** Wiki "Lvl-N Enchant" infoboxes: speed 3 ticks (7 on autocast). */
        const val ENCHANT_TICKS = 3

        /**
         * Level, xp and runes from each spell's Wiki infobox; item pairs from its "List of items" table (members' quest
         * items, sickles and the Digsite pendant are left out: OPEN-MU5). Visuals: cache `human_enchantamuletlvl1..3`,
         * `enchant_amulet_lvl1..3`, `enchant_amulet2_lvl4..6` (OPEN-MU3: Lvl-7 reuses Lvl-6's).
         */
        @Suppress("MagicNumber")
        val ENCHANTS: List<Enchant> =
            listOf(
                Enchant(
                    spell(
                        Components.Magic_Spellbook.ENCHANT_1,
                        7,
                        17.5,
                        Seqs.HUMAN_ENCHANTAMULETLVL1,
                        SpotAnims.ENCHANT_AMULET_LVL1,
                        Objs.WATERRUNE to 1,
                    ),
                    listOf(
                        "Sapphire ring" to "Ring of recoil",
                        "Sapphire bracelet" to "Bracelet of clay",
                        "Sapphire necklace" to "Games necklace(8)",
                        "Sapphire amulet" to "Amulet of magic",
                        "Opal ring" to "Ring of pursuit",
                        "Opal bracelet" to "Expeditious bracelet",
                        "Opal necklace" to "Dodgy necklace",
                        "Opal amulet" to "Amulet of bounty",
                    ),
                ),
                Enchant(
                    spell(
                        Components.Magic_Spellbook.ENCHANT_2,
                        27,
                        37.0,
                        Seqs.HUMAN_ENCHANTAMULETLVL2,
                        SpotAnims.ENCHANT_AMULET_LVL2,
                        Objs.AIRRUNE to 3,
                    ),
                    listOf(
                        "Emerald ring" to "Ring of dueling(8)",
                        "Emerald necklace" to "Binding necklace",
                        "Emerald bracelet" to "Castle wars bracelet(3)",
                        "Emerald amulet" to "Amulet of defence",
                        "Jade ring" to "Ring of returning(5)",
                        "Jade bracelet" to "Flamtaer bracelet",
                        "Jade necklace" to "Necklace of passage(5)",
                        "Jade amulet" to "Amulet of chemistry",
                    ),
                ),
                Enchant(
                    spell(
                        Components.Magic_Spellbook.ENCHANT_3,
                        49,
                        59.0,
                        Seqs.HUMAN_ENCHANTAMULETLVL3,
                        SpotAnims.ENCHANT_AMULET_LVL3,
                        Objs.FIRERUNE to 5,
                    ),
                    listOf(
                        "Ruby ring" to "Ring of forging",
                        "Ruby bracelet" to "Inoculation bracelet",
                        "Ruby amulet" to "Amulet of strength",
                        "Topaz ring" to "Efaritay's aid",
                        "Topaz bracelet" to "Bracelet of slaughter",
                        "Topaz necklace" to "Necklace of faith",
                        "Topaz amulet" to "Burning amulet(5)",
                    ),
                ),
                Enchant(
                    spell(
                        Components.Magic_Spellbook.ENCHANT_4,
                        57,
                        67.0,
                        Seqs.HUMAN_ENCHANTAMULETLVL2,
                        SpotAnims.ENCHANT_AMULET2_LVL4,
                        Objs.EARTHRUNE to 10,
                    ),
                    listOf(
                        "Diamond ring" to "Ring of life",
                        "Diamond necklace" to "Phoenix necklace",
                        "Diamond bracelet" to "Abyssal bracelet(5)",
                        "Diamond amulet" to "Amulet of power",
                    ),
                ),
                Enchant(
                    spell(
                        Components.Magic_Spellbook.ENCHANT_5,
                        68,
                        78.0,
                        Seqs.HUMAN_ENCHANTAMULETLVL3,
                        SpotAnims.ENCHANT_AMULET2_LVL5,
                        Objs.EARTHRUNE to 15,
                        Objs.WATERRUNE to 15,
                    ),
                    listOf(
                        "Dragonstone ring" to "Ring of wealth",
                        "Dragon necklace" to "Skills necklace",
                        "Dragonstone bracelet" to "Combat bracelet",
                        "Dragonstone amulet" to "Amulet of glory",
                    ),
                ),
                Enchant(
                    spell(
                        Components.Magic_Spellbook.ENCHANT_6,
                        87,
                        97.0,
                        Seqs.HUMAN_ENCHANTAMULETLVL3,
                        SpotAnims.ENCHANT_AMULET2_LVL6,
                        Objs.EARTHRUNE to 20,
                        Objs.FIRERUNE to 20,
                    ),
                    listOf(
                        "Onyx ring" to "Ring of stone",
                        "Onyx necklace" to "Berserker necklace",
                        "Onyx bracelet" to "Regen bracelet",
                        "Onyx amulet" to "Amulet of fury",
                    ),
                ),
                Enchant(
                    spell(
                        Components.Magic_Spellbook.ENCHANT_7,
                        93,
                        110.0,
                        Seqs.HUMAN_ENCHANTAMULETLVL3,
                        SpotAnims.ENCHANT_AMULET2_LVL6,
                        Objs.BLOODRUNE to 20,
                        Objs.SOULRUNE to 20,
                    ),
                    listOf(
                        "Zenyte ring" to "Ring of suffering",
                        "Zenyte necklace" to "Necklace of anguish",
                        "Zenyte bracelet" to "Tormented bracelet",
                        "Zenyte amulet" to "Amulet of torture",
                    ),
                ),
            )
    }
}
