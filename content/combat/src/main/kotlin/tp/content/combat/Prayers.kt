package tp.content.combat

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.plugin.WorldRef
import tp.symbols.Varbits

/**
 * One prayer's effects (docs/mechanics/prayers.md, OSRS Wiki "Prayer" table). Percent boosts; [slots] are the conflict
 * groups it occupies (Wiki "Conflicting prayers"): D defence, S strength, A attack, O overhead; ranged/magic offence
 * prayers occupy both S and A; Chivalry/Piety/Rigour/Augury occupy D, S and A.
 */
internal class PrayerEffect(
    val drain: Int,
    val slots: String = "",
    val attack: Int = 0,
    val strength: Int = 0,
    val defence: Int = 0,
    val rangedAttack: Int = 0,
    val rangedStrength: Int = 0,
    val magic: Int = 0,
    val magicDamage: Int = 0,
    val protects: CombatStyle? = null,
    val headIcon: Int = -1,
)

/** A prayer from the cache (enum 4956 → obj params) joined with its effects. */
internal class PrayerDef(val bit: Int, val component: Int, val name: String, val level: Int, val effect: PrayerEffect)

@Suppress("MagicNumber") // Wiki table values
internal object PrayerTable {
    /**
     * Effects by the cache prayer name (param 1752). Drain effects: Wiki "Prayer" table. Head icons: RuneLite
     * `HeadIcon` (MELEE 0, RANGED 1, MAGIC 2, RETRIBUTION 3, SMITE 4, REDEMPTION 5).
     */
    val effects: Map<String, PrayerEffect> =
        mapOf(
            "Thick Skin" to PrayerEffect(1, "D", defence = 5),
            "Burst of Strength" to PrayerEffect(1, "S", strength = 5),
            "Clarity of Thought" to PrayerEffect(1, "A", attack = 5),
            "Sharp Eye" to PrayerEffect(1, "SA", rangedAttack = 5, rangedStrength = 5),
            "Mystic Will" to PrayerEffect(1, "SA", magic = 5),
            "Rock Skin" to PrayerEffect(6, "D", defence = 10),
            "Superhuman Strength" to PrayerEffect(6, "S", strength = 10),
            "Improved Reflexes" to PrayerEffect(6, "A", attack = 10),
            "Rapid Restore" to PrayerEffect(1),
            "Rapid Heal" to PrayerEffect(2),
            "Protect Item" to PrayerEffect(2),
            "Hawk Eye" to PrayerEffect(6, "SA", rangedAttack = 10, rangedStrength = 10),
            "Mystic Lore" to PrayerEffect(6, "SA", magic = 10, magicDamage = 1),
            "Steel Skin" to PrayerEffect(12, "D", defence = 15),
            "Ultimate Strength" to PrayerEffect(12, "S", strength = 15),
            "Incredible Reflexes" to PrayerEffect(12, "A", attack = 15),
            "Protect from Magic" to PrayerEffect(12, "O", protects = CombatStyle.MAGIC, headIcon = 2),
            "Protect from Missiles" to PrayerEffect(12, "O", protects = CombatStyle.RANGED, headIcon = 1),
            "Protect from Melee" to PrayerEffect(12, "O", protects = CombatStyle.MELEE, headIcon = 0),
            "Eagle Eye" to PrayerEffect(12, "SA", rangedAttack = 15, rangedStrength = 15),
            "Mystic Might" to PrayerEffect(12, "SA", magic = 15, magicDamage = 2),
            "Retribution" to PrayerEffect(3, "O", headIcon = 3),
            "Redemption" to PrayerEffect(6, "O", headIcon = 5),
            "Smite" to PrayerEffect(18, "O", headIcon = 4),
            "Preserve" to PrayerEffect(2),
            "Chivalry" to PrayerEffect(12, "DSA", attack = 15, strength = 18, defence = 20),
            "Piety" to PrayerEffect(24, "DSA", attack = 20, strength = 23, defence = 25),
            "Rigour" to PrayerEffect(24, "DSA", rangedAttack = 20, rangedStrength = 23, defence = 25),
            "Augury" to PrayerEffect(24, "DSA", magic = 25, defence = 25, magicDamage = 4),
            "Deadeye" to PrayerEffect(12, "SA", rangedAttack = 18, rangedStrength = 18, defence = 5),
            "Mystic Vigour" to PrayerEffect(12, "SA", magic = 18, defence = 5, magicDamage = 3),
        )

    /** Cache enum of prayer objs (key = prayer id), verified in cache 2757 (combat-data.md). */
    const val PRAYER_ENUM = 4956

    /** Prayer obj params (cache 2757): bit in varp 83 / varbit prayer_allactive, component, name, level. */
    const val PARAM_BIT = 630
    const val PARAM_COMPONENT = 1751
    const val PARAM_NAME = 1752
    const val PARAM_LEVEL = 1753

    var prayers: List<PrayerDef> = emptyList()
        private set

    fun load(world: WorldRef) {
        prayers =
            world.enumInts(PRAYER_ENUM).values.mapNotNull { obj ->
                val name = world.objParamString(obj, PARAM_NAME) ?: return@mapNotNull null
                PrayerDef(
                    bit = world.objParamInt(obj, PARAM_BIT) ?: return@mapNotNull null,
                    component = world.objParamInt(obj, PARAM_COMPONENT) ?: return@mapNotNull null,
                    name = name,
                    level = world.objParamInt(obj, PARAM_LEVEL) ?: 1,
                    effect = effects[name] ?: PrayerEffect(0),
                )
            }
    }
}

private val PRAYER_DRAIN = TimerId("prayer_drain")

/** Active prayers of [this] (bits of varbit prayer_allactive). */
internal fun PlayerRef.activePrayers(): List<PrayerDef> {
    val bits = varbit(Varbits.PRAYER_ALLACTIVE)
    if (bits == 0) return emptyList()
    return PrayerTable.prayers.filter { bits and (1 shl it.bit) != 0 }
}

/** Sum of an effect over active prayers (only one per conflict group can be active). */
internal fun PlayerRef.prayerPercent(of: (PrayerEffect) -> Int): Int = PERCENT_BASE + activePrayers().sumOf { of(it.effect) }

private const val PERCENT_BASE = 100

internal fun PlayerRef.protects(style: CombatStyle): Boolean = activePrayers().any { it.effect.protects == style }

internal fun PlayerRef.headIcon(): Int = activePrayers().firstOrNull { it.effect.headIcon >= 0 }?.effect?.headIcon ?: -1

internal fun PlayerRef.deactivateAllPrayers() {
    setVarbit(Varbits.PRAYER_ALLACTIVE, 0)
    stopTimer(PRAYER_DRAIN)
    Appearance.refresh(this)
}

/** Prayer book clicks, activation, conflicts and the drain timer (prayers.md P1–P5). */
public class Prayers : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart {
            PrayerTable.load(this)
            for (prayer in PrayerTable.prayers) {
                registerButton(prayer)
            }
        }
    }

    private fun ContentScope.registerButton(prayer: PrayerDef) {
        onButton(prayer.component) { toggle(prayer) }
    }

    private fun PlayerRef.toggle(prayer: PrayerDef) {
        val bits = varbit(Varbits.PRAYER_ALLACTIVE)
        val mask = 1 shl prayer.bit
        if (bits and mask != 0) {
            setVarbit(Varbits.PRAYER_ALLACTIVE, bits and mask.inv())
            if (bits and mask.inv() == 0) stopTimer(PRAYER_DRAIN)
            Appearance.refresh(this)
            return
        }
        if (baseLevel(Stat.PRAYER) < prayer.level) {
            message("You need a Prayer level of ${prayer.level} to use ${prayer.name}.")
            return
        }
        if (level(Stat.PRAYER) <= 0) {
            message("You have run out of prayer points, you can recharge at an altar.")
            return
        }
        var next = bits or mask
        for (other in PrayerTable.prayers) {
            if (other !== prayer && other.effect.slots.any { it in prayer.effect.slots }) next = next and (1 shl other.bit).inv()
        }
        setVarbit(Varbits.PRAYER_ALLACTIVE, next)
        // P3: not drained on the tick it is activated.
        val activated = this[Attr.PRAYER_ACTIVATED] ?: LongArray(MAX_PRAYERS).also { this[Attr.PRAYER_ACTIVATED] = it }
        activated[prayer.bit] = tick
        if (!hasTimer(PRAYER_DRAIN)) timer(PRAYER_DRAIN, 1, soft = true) { drain() }
        Appearance.refresh(this)
    }

    /** P2/P3: add drain effects of prayers active before this tick; lose a point per resistance exceeded. */
    private fun PlayerRef.drain() {
        val active = activePrayers()
        if (active.isEmpty()) {
            stopTimer(PRAYER_DRAIN)
            return
        }
        val activated = this[Attr.PRAYER_ACTIVATED]
        val effect = active.filter { (activated?.get(it.bit) ?: 0L) < tick }.sumOf { it.effect.drain }
        var counter = (this[Attr.PRAYER_DRAIN_COUNTER] ?: 0) + effect
        val resistance = BASE_RESISTANCE + 2 * bonus(Param.PRAYER)
        var points = level(Stat.PRAYER)
        while (counter > resistance && points > 0) {
            counter -= resistance
            points--
        }
        this[Attr.PRAYER_DRAIN_COUNTER] = counter
        if (points != level(Stat.PRAYER)) setLevel(Stat.PRAYER, points)
        if (points <= 0) {
            deactivateAllPrayers()
            message("You have run out of prayer points, you can recharge at an altar.")
        }
    }

    private companion object {
        /** P2: base prayer drain resistance 60 (+2 per prayer bonus). */
        const val BASE_RESISTANCE = 60
        const val MAX_PRAYERS = 32
    }
}
