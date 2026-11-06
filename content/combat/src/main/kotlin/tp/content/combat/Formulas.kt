package tp.content.combat

/**
 * Accuracy and max-hit formulas (docs/mechanics/combat-formulas.md F1–F22). Pure functions: levels, bonuses and
 * multipliers in, rolls out. Multipliers are fractions (num/den) to keep integer floors exact.
 */
internal object Formulas {
    private const val EFFECTIVE_BASE = 8
    private const val BONUS_BASE = 64
    private const val MAX_HIT_DIVISOR = 640
    private const val MAX_HIT_HALF = 320
    private const val PERCENT = 100

    /** F1/F4/F8/F10: ⌊(⌊level × prayer⌋ + stance + 8) × void⌋; prayer and void in percent. */
    fun effectiveLevel(level: Int, prayerPercent: Int, stance: Int, voidPercent: Int = PERCENT): Int {
        val prayed = level * prayerPercent / PERCENT
        return (prayed + stance + EFFECTIVE_BASE) * voidPercent / PERCENT
    }

    /** F5/F10/F14: ⌊effective × (bonus + 64) × gear⌋ (gear in percent). */
    fun roll(effective: Int, bonus: Int, gearPercent: Int = PERCENT): Int =
        (effective * (bonus + BONUS_BASE)).toLong().let {
            (
                it *
                    gearPercent /
                    PERCENT
            ).toInt()
        }

    /** F7/F15: NPC defence roll (level + 9) × (bonus + 64). */
    fun npcDefenceRoll(level: Int, bonus: Int): Int = (level + EFFECTIVE_BASE + 1) * (bonus + BONUS_BASE)

    /** F3/F9: ⌊(effective × (strBonus + 64) + 320) / 640⌋ (same as ⌊0.5 + e × (s + 64) / 640⌋). */
    fun maxHit(effectiveStrength: Int, strengthBonus: Int): Int =
        (effectiveStrength * (strengthBonus + BONUS_BASE) + MAX_HIT_HALF) / MAX_HIT_DIVISOR

    /** F13 (simplified to the terms we model): ⌊base × (100 + magic damage %) / 100⌋. */
    fun magicMaxHit(base: Int, damagePercent: Int): Int = base * (PERCENT + damagePercent) / PERCENT

    /** F16 (OPEN-F1): player magic defence level = ⌊0.7 × magic⌋ + ⌊0.3 × defence⌋ (prayers applied to each). */
    fun playerMagicDefenceLevel(magic: Int, magicPrayerPercent: Int, defence: Int, defencePrayerPercent: Int): Int =
        (magic * magicPrayerPercent / PERCENT) * MAGIC_SHARE / TENTHS + (defence * defencePrayerPercent / PERCENT) * DEFENCE_SHARE / TENTHS

    private const val MAGIC_SHARE = 7
    private const val DEFENCE_SHARE = 3
    private const val TENTHS = 10

    /** F17: server form of the hit chance: a = uniform[0, A], d = uniform[0, D], hit if a > d. */
    fun hits(attackRoll: Int, defenceRoll: Int, random: (Int) -> Int): Boolean {
        val a = random(attackRoll + 1)
        val d = random(defenceRoll + 1)
        return a > d
    }

    /** F18: damage on a hit, uniform [0, max]; a 0 on a successful hit becomes 1 (DM:155, MA:221; OPEN-F2 ranged). */
    fun damage(maxHit: Int, random: (Int) -> Int): Int = if (maxHit <= 0) 0 else maxOf(1, random(maxHit + 1))

    /** F19 / DM:64: protection prayers against players cap the max hit at ⌊max × 6/10⌋. */
    fun protectedMaxHit(maxHit: Int): Int = maxHit * PROTECTED_NUM / PROTECTED_DEN

    private const val PROTECTED_NUM = 6
    private const val PROTECTED_DEN = 10

    /** F20: player damage cap. */
    const val DAMAGE_CAP = 200

    /** F21: combat level from base levels. */
    @Suppress("MagicNumber") // the formula's constants (CL:37-57)
    fun combatLevel(att: Int, str: Int, def: Int, hp: Int, prayer: Int, ranged: Int, magic: Int): Int {
        val base = (def + hp + prayer / 2) / 4.0
        val melee = 13.0 / 40 * (att + str)
        val range = 13.0 / 40 * (ranged * 3 / 2)
        val mage = 13.0 / 40 * (magic * 3 / 2)
        return kotlin.math.floor(base + maxOf(melee, range, mage)).toInt()
    }

    // ---- hit delay (combat-timeline.md), d = Chebyshev distance edge to edge (formula constants: HD:54, HD:156, HD:298, HD:114)

    @Suppress("MagicNumber")
    fun bowDelay(d: Int): Int = 1 + (3 + d) / 6

    @Suppress("MagicNumber")
    fun thrownDelay(d: Int): Int = 1 + d / 6

    @Suppress("MagicNumber")
    fun magicDelay(d: Int): Int = 1 + (1 + d) / 3

    @Suppress("MagicNumber")
    fun darkBowSecondDelay(d: Int): Int = 1 + (2 + d) / 3
}
