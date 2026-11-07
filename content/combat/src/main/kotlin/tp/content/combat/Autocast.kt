package tp.content.combat

import tp.plugin.ButtonClick
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * Autocast (docs/mechanics/combat-magic.md CM14). The combat tab's spell buttons open the autocast list (interface 201,
 * in place of the combat tab). Its onload script (client script 235 -> 2098) lists the autocast ids 1..58 of cache enum
 * `autocast_spells` (1986) that client script 243 allows for the layout key the server puts in varp 664: -1 standard,
 * 4675 Ancient, 9013 Arceuus, or a special staff's own obj id. A spell click is button 201:1 with the autocast id as
 * its slot (0 = Cancel). The choice lives in varbits `autocast_spell` (276), `autocast_set` (275) and
 * `autocast_defmode` (2668); `com_mode` is 4 (5 defensive) while autocasting (RuneLite `AttackStylesPlugin`).
 */
internal object Autocast {
    fun register(scope: tp.plugin.ContentScope) {
        scope.onButton(Components.Combat_Interface.AUTOCAST_NORMAL) { open(this, defensive = false) }
        scope.onButton(Components.Combat_Interface.AUTOCAST_DEFENSIVE) { open(this, defensive = true) }
        scope.onButton(Components.Autocast.SPELLS) { click -> choose(this, click) }
    }

    /** The spell [player] autocasts now, or null. */
    fun spellOf(player: PlayerRef): CombatSpell? {
        if (player.varbit(Varbits.AUTOCAST_SET) == 0) return null
        return spellFor(player, player.varbit(Varbits.AUTOCAST_SPELL))
    }

    fun defensive(player: PlayerRef): Boolean = player.varbit(Varbits.AUTOCAST_DEFMODE) != 0

    /** Autocast id -> spell obj (enum 1986) -> spellbook button (obj param 596) -> our spell. */
    private fun spellFor(player: PlayerRef, id: Int): CombatSpell? {
        if (id <= 0) return null
        val obj = player.enumInts(AUTOCAST_ENUM)[id] ?: return null
        val component = player.objParam(obj, SPELL_BUTTON_PARAM, -1)
        return CombatSpell.of(component)?.takeIf { it.autocast }
    }

    /** CM14: weapon changed: keep the choice only if the new weapon can still autocast it. */
    fun onWeaponChange(player: PlayerRef) {
        if (player.varbit(Varbits.AUTOCAST_SET) == 0) return
        if (allowed(player, player.varbit(Varbits.AUTOCAST_SPELL))) {
            player.setVarp(Varps.COM_MODE, if (defensive(player)) COM_DEFENSIVE else COM_AUTOCAST)
        } else {
            clear(player)
        }
    }

    /** A stance button or a lost spell: autocast off, back to the weapon's first stance. */
    fun clear(player: PlayerRef) {
        if (player.varbit(Varbits.AUTOCAST_SET) == 0 && player.varbit(Varbits.AUTOCAST_SPELL) == 0) return
        player.setVarbit(Varbits.AUTOCAST_SET, 0)
        player.setVarbit(Varbits.AUTOCAST_SPELL, 0)
        player.setVarbit(Varbits.AUTOCAST_DEFMODE, 0)
        val mode = player.varp(Varps.COM_MODE)
        if (player.weaponCategory().slots.getOrNull(mode) == null) {
            player.setVarp(Varps.COM_MODE, player.weaponCategory().slots.indexOfFirst { it != null })
        }
    }

    private fun open(player: PlayerRef, defensive: Boolean) {
        if (!canAutocast(player)) return
        player[PICKING_DEFENSIVE] = defensive
        player.setVarp(LAYOUT_VARP, layoutKey(player))
        player.openOverlay(Components.Toplevel.SIDE0, Interfaces.AUTOCAST)
    }

    private fun choose(player: PlayerRef, click: ButtonClick) {
        val defensive = player[PICKING_DEFENSIVE] ?: false
        player[PICKING_DEFENSIVE] = null
        player.openOverlay(Components.Toplevel.SIDE0, Interfaces.COMBAT_INTERFACE)
        val id = click.slot
        if (id <= 0) return // Cancel
        val spell = spellFor(player, id)
        if (spell == null || !allowed(player, id)) return // not listed for this weapon: a hostile or stale click
        if (player.level(Stat.MAGIC) < spell.level) {
            player.message("You need a Magic level of ${spell.level} to cast this spell.")
            return
        }
        player.setVarbit(Varbits.AUTOCAST_SPELL, id)
        player.setVarbit(Varbits.AUTOCAST_DEFMODE, if (defensive) 1 else 0)
        player.setVarbit(Varbits.AUTOCAST_SET, 1)
        player.setVarp(Varps.COM_MODE, if (defensive) COM_DEFENSIVE else COM_AUTOCAST)
    }

    /** CM14: a staff, battlestaff, wand or crozier (obj category 1) without a built-in spell (powered staves set a range). */
    private fun canAutocast(player: PlayerRef): Boolean {
        val weapon = player.worn(Wear.WEAPON)
        return weapon >= 0 && player.objCategory(weapon) == STAFF_CATEGORY && player.objParam(weapon, Param.ATTACK_RANGE, 0) <= 0
    }

    /** Whether autocast id [id] is in the list client script 243 shows for this weapon and spellbook. */
    private fun allowed(player: PlayerRef, id: Int): Boolean =
        canAutocast(player) && id in listFor(layoutKey(player), player.varbit(Varbits.SPELLBOOK))

    private fun listFor(key: Int, book: Int): Set<Int> =
        when (key) {
            Objs.BARROWS_AHRIM_WEAPON -> {
                if (book == SpellBook.ARCEUUS.varbit) ARCEUUS else STANDARD
            }

            Objs.SOS_SKULL_SCEPTRE_IMBUED -> {
                when (book) {
                    SpellBook.ARCEUUS.varbit -> ARCEUUS
                    SpellBook.STANDARD.varbit -> setOf(CRUMBLE)
                    else -> emptySet()
                }
            }

            else -> {
                LISTS[key].orEmpty()
            }
        }

    /** The varp 664 key for the wielded weapon and spellbook (client script 243's switch). */
    private fun layoutKey(player: PlayerRef): Int {
        val weapon = player.worn(Wear.WEAPON)
        val name = player.objName(weapon).lowercase()
        val book = player.varbit(Varbits.SPELLBOOK)
        if (book == SpellBook.ANCIENT.varbit && ancientCapable(player, name)) return ANCIENT_LAYOUT
        if (book == SpellBook.ARCEUUS.varbit && ARCEUUS_WEAPONS.any { name.startsWith(it) }) {
            return if (weapon == Objs.BARROWS_AHRIM_WEAPON || weapon == Objs.SOS_SKULL_SCEPTRE_IMBUED) weapon else ARCEUUS_LAYOUT
        }
        return SPECIAL_LAYOUT[weapon] ?: NO_STAFF
    }

    /** Wiki "Autocast" (Ancient Magicks table); Ahrim's staff only with the full set and the amulet of the damned. */
    private fun ancientCapable(player: PlayerRef, name: String): Boolean {
        if (ANCIENT_WEAPONS.any { name.startsWith(it) }) return true
        if (!name.startsWith(AHRIMS_STAFF)) return false
        val worn = intArrayOf(Wear.HEAD, Wear.BODY, Wear.LEGS, Wear.NECK).map { player.worn(it) }
        if (worn.any { it < 0 }) return false
        val names = worn.map { player.objName(it).lowercase() }
        return names[0].startsWith("ahrim's hood") && names[1].startsWith("ahrim's robetop") &&
            names[2].startsWith("ahrim's robeskirt") && names[3].startsWith("amulet of the damned")
    }

    private val PICKING_DEFENSIVE = tp.plugin.AttrKey<Boolean>("autocast_picking_defensive")

    /** Cache enum `autocast_spells` and obj param `spell_button` (RS Mod `MagicSpellRegistry`). */
    private const val AUTOCAST_ENUM = 1986
    private const val SPELL_BUTTON_PARAM = 596

    /** Varp 664: the layout key client script 2098 passes to 243 (gameval name `spitfire_coord`, reused). */
    private const val LAYOUT_VARP = 664
    private const val NO_STAFF = -1
    private val ANCIENT_LAYOUT = Objs.STAFF_OF_ZAROS
    private val ARCEUUS_LAYOUT = Objs.SOS_SKULL_SCEPTRE

    /** RuneLite `AttackStylesPlugin`: com_mode 4 casting, 5 defensive casting. */
    private const val COM_AUTOCAST = 4
    private const val COM_DEFENSIVE = 5
    private const val STAFF_CATEGORY = 1

    /** Autocast ids (enum 1986 -> spell obj -> name): 1-16 strike..blast, 48-51 surges, 13-16 waves. */
    private val STANDARD: Set<Int> = (1..16).toSet() + (48..51)
    private val WAVES_AND_SURGES: Set<Int> = (13..16).toSet() + (48..51)
    private val ANCIENT: Set<Int> = (31..46).toSet()
    private val ARCEUUS: Set<Int> = (53..58).toSet()
    private const val CRUMBLE = 17
    private const val DART = 18
    private const val CLAWS = 19
    private const val FLAMES = 20
    private const val IBAN = 47
    private const val SARADOMIN = 52

    /** Client script 243: the four wild cave sceptres (Thammaron's / accursed, autocast variants). */
    private val WILD_CAVE_SCEPTRES_IDS =
        intArrayOf(
            Objs.WILD_CAVE_SCEPTRE_UNCHARGED_RECOL,
            Objs.WILD_CAVE_SCEPTRE_CHARGED_RECOL,
            Objs.WILD_CAVE_ACCURSED_UNCHARGED_RECOL,
            Objs.WILD_CAVE_ACCURSED_CHARGED_RECOL,
        )

    /** Client script 243's lists per layout key (the two that also look at the spellbook are in [listFor]). */
    private val LISTS: Map<Int, Set<Int>> by lazy {
        mapOf(
            NO_STAFF to STANDARD,
            ANCIENT_LAYOUT to ANCIENT,
            ARCEUUS_LAYOUT to ARCEUUS,
            Objs.SLAYER_STAFF to WAVES_AND_SURGES + CRUMBLE + DART,
            Objs.PEST_VOID_KNIGHT_MACE to WAVES_AND_SURGES + CRUMBLE + CLAWS,
            Objs.IBANSTAFF to STANDARD + IBAN,
            Objs.SOTD to STANDARD + CRUMBLE + DART + FLAMES,
            Objs.STAFF_OF_LIGHT to STANDARD + CRUMBLE + DART + SARADOMIN,
            Objs.STAFF_OF_BALANCE to STANDARD + CRUMBLE + DART + CLAWS,
        ) + WILD_CAVE_SCEPTRES_IDS.associateWith { STANDARD + FLAMES }
    }

    /** Weapons with their own branch in client script 243 (variants mapped to the obj it names). */
    private val SPECIAL_LAYOUT: Map<Int, Int> =
        mapOf(
            Objs.SLAYER_STAFF to Objs.SLAYER_STAFF,
            Objs.SLAYER_STAFF_ENCHANTED to Objs.SLAYER_STAFF,
            Objs.PEST_VOID_KNIGHT_MACE to Objs.PEST_VOID_KNIGHT_MACE,
            Objs.IBANSTAFF to Objs.IBANSTAFF,
            Objs.IBANSTAFF_UPGRADED to Objs.IBANSTAFF,
            Objs.SOTD to Objs.SOTD,
            Objs.TOXIC_SOTD_CHARGED to Objs.SOTD,
            Objs.STAFF_OF_LIGHT to Objs.STAFF_OF_LIGHT,
            Objs.STAFF_OF_BALANCE to Objs.STAFF_OF_BALANCE,
            Objs.BARROWS_AHRIM_WEAPON to Objs.BARROWS_AHRIM_WEAPON,
            Objs.SOS_SKULL_SCEPTRE_IMBUED to Objs.SOS_SKULL_SCEPTRE_IMBUED,
        ) + WILD_CAVE_SCEPTRES_IDS.associateWith { it }

    /** Wiki "Autocast": weapons that autocast the Ancient Magicks (name prefixes, lower case). */
    private val ANCIENT_WEAPONS =
        listOf(
            "ancient staff",
            "ancient sceptre",
            "blood ancient sceptre",
            "ice ancient sceptre",
            "smoke ancient sceptre",
            "shadow ancient sceptre",
            "thammaron's sceptre (a)",
            "accursed sceptre (a)",
            "blue moon spear",
            "master wand",
            "dragon hunter wand",
            "kodai wand",
            "nightmare staff",
            "volatile nightmare staff",
            "eldritch nightmare staff",
            "harmonised nightmare staff",
        )
    private const val AHRIMS_STAFF = "ahrim's staff"

    /** Wiki "Autocast": weapons that autocast the Arceuus spells. */
    private val ARCEUUS_WEAPONS =
        listOf(
            "skull sceptre (i)",
            "slayer's staff",
            "ahrim's staff",
            "blue moon spear",
            "staff of the dead",
            "toxic staff of the dead",
            "purging staff",
            "master wand",
            "kodai wand",
        )
}
