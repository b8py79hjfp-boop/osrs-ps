package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * Salamanders (docs/mechanics/gear-effects.md GE90-GE92; Wiki "Salamander"): Scorch (slash), Flare (ranged) and Blaze
 * (magic, trained as defensive casting). Every attack burns one of the salamander's own tar from the ammo slot. Blaze's
 * max hit is ⌊(Magic × (s + 64) + 320) / 640⌋ with the salamander's magic strength s (Wiki DPS calculator), then the
 * usual magic damage chain; it rolls magic accuracy like a spell (+9).
 */
internal object Salamanders {
    class Kind(val tar: Int, val magicStrength: Int)

    /** Salamander -> its tar and magic strength (Wiki "Salamander" fuel table; calculator magic strengths). */
    private val KINDS: Map<Int, Kind> =
        mapOf(
            Objs.GREEN_SALAMANDER to Kind(Objs.SALAMANDER_TAR_GREEN, SWAMP),
            Objs.ORANGE_SALAMANDER to Kind(Objs.SALAMANDER_TAR_ORANGE, ORANGE),
            Objs.RED_SALAMANDER to Kind(Objs.SALAMANDER_TAR_RED, RED),
            Objs.BLACK_SALAMANDER to Kind(Objs.SALAMANDER_TAR_BLACK, BLACK),
            Objs.MOUNTAIN_SALAMANDER to Kind(Objs.SALAMANDER_TAR_MOUNTAIN, TECU),
        )

    fun of(obj: Int): Kind? = KINDS[obj]

    /** Burns one tar; false (with the message, OPEN-GE16: wording) when the right tar isn't in the ammo slot. */
    fun useTar(player: PlayerRef): Boolean {
        val kind = of(player.worn(Wear.WEAPON)) ?: return true
        val ammo = player.worn(Wear.AMMO)
        if (ammo != kind.tar) {
            player.message("You need ${player.objName(kind.tar)} to use your salamander.")
            return false
        }
        return player.transaction("salamander tar") { removeAt(Invs.WORN, Wear.AMMO, ammo, 1) }
    }

    /** Blaze: one magic hit at [target], landing after [delay] ticks. */
    fun blaze(player: PlayerRef, target: EntityRef, delay: Int) {
        val kind = of(player.worn(Wear.WEAPON)) ?: return
        val c = GearContext.ctx(player, target, AttackType.MAGIC)
        val attackRoll = Attacks.attackRoll(player, Stance.SPELL, AttackType.MAGIC, target = target)
        val defenceRoll = PostRoll.brimstone(player, Attacks.defenceRoll(target, AttackType.MAGIC))
        val base = (player.level(Stat.MAGIC) * (kind.magicStrength + BONUS_BASE) + HALF) / DIVISOR
        val tenths = player.bonus(Param.MAGIC_DAMAGE) + player.activePrayers().sumOf { it.effect.magicDamage } * TENTHS
        var max = GearEffects.magicMax(c, base, tenths, null, 0, elementalAmulet = false)
        if (target is PlayerRef && target.protects(CombatStyle.MAGIC)) max = Formulas.protectedMaxHit(max)
        val hit = Formulas.hits(attackRoll, defenceRoll, player::random)
        val damage = if (hit) minOf(Formulas.DAMAGE_CAP, PostRoll.damage(player, 0, max)) else 0
        CombatVisuals.block(target, delay)
        CombatSystem.queueHit(target, player, damage, tp.plugin.HitStyle.MAGIC, delay)
        // Wiki "Module:CombatStyles": Blaze is a defensive magic style (Magic and Defence experience).
        if (damage > 0) {
            player.addXp(Stat.MAGIC, damage * 4.0 / 3)
            player.addXp(Stat.DEFENCE, damage.toDouble())
            player.addXp(Stat.HITPOINTS, damage * 4.0 / 3)
        }
    }

    private const val SWAMP = 56
    private const val ORANGE = 59
    private const val RED = 77
    private const val BLACK = 92
    private const val TECU = 104
    private const val BONUS_BASE = 64
    private const val HALF = 320
    private const val DIVISOR = 640
    private const val TENTHS = 10
}
