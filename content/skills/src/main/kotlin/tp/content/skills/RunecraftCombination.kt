package tp.content.skills

import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits

/**
 * One combination rune (docs/mechanics/runecraft.md RC7): made at the altar of [first] or [second] from the other element's
 * runes, pure essence and that element's talisman (or Magic Imbue). [xpLow] and [xpHigh] are the Wiki's "Exp (Low)" and
 * "Exp (High)" per rune made (OPEN-RC9: which altar gives which).
 */
internal class Combo(
    val name: String,
    val rune: Int,
    val level: Int,
    val first: String,
    val second: String,
    val xpLow: Double,
    val xpHigh: Double,
)

internal object RunecraftCombos {
    /** Wiki "Combination rune" (levels) and "Runecraft" (Combination runes table: experience low / high). */
    @Suppress("MagicNumber")
    val ALL: List<Combo> =
        listOf(
            Combo("mist", Objs.MISTRUNE, 6, "air", "water", 8.0, 8.5),
            Combo("dust", Objs.DUSTRUNE, 10, "air", "earth", 8.3, 9.0),
            Combo("mud", Objs.MUDRUNE, 13, "water", "earth", 9.3, 9.5),
            Combo("smoke", Objs.SMOKERUNE, 15, "air", "fire", 8.5, 9.5),
            Combo("steam", Objs.STEAMRUNE, 19, "water", "fire", 9.3, 10.0),
            Combo("lava", Objs.LAVARUNE, 23, "earth", "fire", 10.0, 10.5),
        )

    /** The combination of the elements [a] and [b] (either order), or null. */
    fun of(a: String, b: String): Combo? = ALL.firstOrNull { (it.first == a && it.second == b) || (it.first == b && it.second == a) }

    /** The elemental altars where combination runes are made (Wiki "Combination rune": the four elements). */
    val ELEMENTS: List<String> = listOf("air", "water", "earth", "fire")
}

/** Combination runes (runecraft.md RC7-RC9): the binding necklace, Magic Imbue and the crafting itself. */
internal object Combinations {
    /** Worn slot of the amulet / necklace (cache wear position 2). */
    private const val NECK_SLOT = 2

    /** Wiki "Binding necklace": "16 uses". */
    const val NECKLACE_CHARGES = 16L

    /** Wiki "Combination rune": "The chance of successfully binding each individual rune together is only 50%". */
    private const val FAIL_ONE_IN = 2

    /** Account value: charges left on the player's binding necklace (Wiki: "charges are stored per player"); absent = full. */
    private const val CHARGES_KEY = "runecraft.binding_charges"

    /** Registers the combination uses on the altar of [t]: another element's talisman or runes used on it. */
    fun register(scope: ContentScope, t: Temple, craft: (PlayerRef, Temple, Temple) -> Unit) {
        if (t.name !in RunecraftCombos.ELEMENTS) return
        for (other in RunecraftData.TEMPLES) {
            if (other === t || other.name !in RunecraftCombos.ELEMENTS) continue
            scope.onUseOnLoc(other.talisman, t.altarLoc) { craft(player, t, other) }
            scope.onUseOnLoc(other.rune, t.altarLoc) { craft(player, t, other) }
        }
    }

    fun charges(p: PlayerRef): Long = p.saved(CHARGES_KEY) ?: NECKLACE_CHARGES

    /** Wiki "Combination rune" / "Binding necklace": 100% with a worn binding necklace, one charge per batch crafted. */
    fun wearsNecklace(p: PlayerRef): Boolean = p.inv(Invs.WORN).obj(NECK_SLOT) == Objs.MAGIC_EMERALD_NECKLACE

    /** Uses one charge; the necklace crumbles at 0 and the next one starts full again (Wiki "Binding necklace"). */
    fun useCharge(p: PlayerRef) {
        val left = charges(p) - 1
        if (left > 0) return p.setSaved(CHARGES_KEY, left)
        p.setSaved(CHARGES_KEY, null)
        p.transaction("binding necklace") { removeAt(Invs.WORN, NECK_SLOT, Objs.MAGIC_EMERALD_NECKLACE, 1) }
        p.message("Your binding necklace crumbles to dust.")
    }

    /** Whether Magic Imbue is running (content/magic sets the cache varbit `magic_imbue_active`). */
    fun imbued(p: PlayerRef): Boolean = p.varbit(Varbits.MAGIC_IMBUE_ACTIVE) == 1

    /**
     * RC7: at the altar [at], [other]'s runes (or its talisman) were used on it. Needs pure essence, as many [other] runes,
     * the combination's level, and [other]'s talisman or Magic Imbue; each rune binds with 50% unless a binding necklace is
     * worn; the talisman is used up whatever the outcome; failed runes are lost without xp.
     */
    fun craft(p: PlayerRef, at: Temple, other: Temple, nothing: String) {
        val combo = RunecraftCombos.of(at.name, other.name) ?: return p.message(nothing)
        val inv = p.inv(Invs.INV)
        val hasTalisman = inv.total(other.talisman) > 0
        if (!hasTalisman && !imbued(p)) return p.message(nothing)
        if (p.level(Stats.RUNECRAFT) < combo.level) {
            return p.message("You need a Runecraft level of ${combo.level} to craft ${combo.name} runes.")
        }
        val pure = inv.total(RunecraftData.PURE_ESSENCE).toInt()
        val runes = inv.total(other.rune).toInt()
        if (pure == 0) return p.message("You have no pure essence to bind.")
        if (runes == 0) return p.message("You have no ${other.name} runes to combine.")
        val used = minOf(pure, runes)
        val necklace = wearsNecklace(p)
        var made = 0
        for (i in 0 until used) if (necklace || p.random(FAIL_ONE_IN) == 0) made++
        val done =
            p.transaction("runecraft combination") {
                remove(Invs.INV, RunecraftData.PURE_ESSENCE, used) &&
                    remove(Invs.INV, other.rune, used) &&
                    (!hasTalisman || remove(Invs.INV, other.talisman, 1)) &&
                    (made == 0 || add(Invs.INV, combo.rune, made))
            }
        if (!done) return p.message("You don't have enough inventory space.")
        if (necklace) useCharge(p)
        p.anim(Seqs.HUMAN_RUNECRAFT)
        p.spotAnim(SpotAnims.RUNECRAFTING)
        p.message("You bind the temple's power into ${combo.name} runes.")
        if (made > 0) p.addXp(Stats.RUNECRAFT, made * if (at.xp >= other.xp) combo.xpHigh else combo.xpLow)
    }
}
