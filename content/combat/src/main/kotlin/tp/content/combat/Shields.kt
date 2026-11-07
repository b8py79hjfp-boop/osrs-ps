package tp.content.combat

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * Shields with their own attacks (docs/mechanics/special-attacks.md SP57-SP60): the Rondache's Shield Slam charges
 * and the dragonfire shield / ward and ancient wyvern shield operate blasts. Charges are kept per player (an obj has
 * no state here; the shields only trade uncharged, as D-076 for the blowpipe).
 */
public class Shields : ContentPlugin {
    override fun ContentScope.register() {
        for ((charged, uncharged) in DRAGONFIRE) {
            onItemOp(charged, "Inspect") { message("The shield has ${dragonfireCharges(this)} charges.") }
            onItemOp(charged, "Empty") { click -> empty(this, click.slot, charged, uncharged) }
        }
        onButton(Components.Wornitems.SLOT5, OP_WORN_FIRST) { operate(this) }
        onButton(Components.Wornitems.SLOT5, OP_WORN_SECOND) { check(this) }
    }

    internal companion object {
        /** (charged, uncharged): dragonfire shield, dragonfire ward, ancient wyvern shield. */
        private val DRAGONFIRE =
            listOf(
                Objs.DRAGONFIRE_SHIELD to Objs.DRAGONFIRE_SHIELD_UNCHARGED,
                Objs.DRAGONFIRE_WARD to Objs.DRAGONFIRE_WARD_UNCHARGED,
            )

        /** Wiki "Dragonfire shield": at most 50 charges, one per adult dragon's breath blocked. */
        private const val DRAGONFIRE_CAPACITY = 50L

        /** Wiki "Rondache": 2 charge points per attack, at most 20, once every 2 ticks; Shield Slam needs 10. */
        const val SLAM_MIN = 10
        private const val RONDACHE_CAP = 20
        private const val RONDACHE_PER_HIT = 2
        private const val RONDACHE_INTERVAL = 2L

        /** Wiki: "Activate" / "Operate" is the first worn op, "Check" the second (button ops 2 and 3). */
        private const val OP_WORN_FIRST = 2
        private const val OP_WORN_SECOND = 3

        /** Wiki "Dragonfire shield": range 10, "about 1 minute and 56 seconds" (Mod Ash: "192 ticks, I think"). */
        private const val OPERATE_RANGE = 10
        private const val DRAGONFIRE_COOLDOWN = 192L

        /** Wiki "Ancient wyvern shield": "once every two minutes" (OPEN-SP13: ticks), up to 15 damage. */
        private const val WYVERN_COOLDOWN = 200L
        private const val DRAGONFIRE_MAX = 25
        private const val WYVERN_MAX = 15

        /** Wiki: against a player, an anti-dragon shield caps the blast at 3 (antifire is OPEN-SP14). */
        private const val SHIELDED_MAX = 3

        private const val DRAGONFIRE_KEY = "combat.dragonfire_shield_charges"
        private const val WYVERN_KEY = "combat.wyvern_shield_charges"
        private const val RONDACHE_KEY = "combat.rondache_charges"
        private val RONDACHE_LAST = tp.plugin.AttrKey<Long>("rondache_last_charge")
        private val OPERATE_READY = tp.plugin.AttrKey<Long>("shield_operate_ready")

        fun dragonfireCharges(p: PlayerRef): Long = p.saved(DRAGONFIRE_KEY) ?: 0L

        fun rondacheCharges(p: PlayerRef): Int = (p.saved(RONDACHE_KEY) ?: 0L).toInt()

        fun useRondache(p: PlayerRef) = p.setSaved(RONDACHE_KEY, null)

        /** A breath of dragonfire at [p]: a worn dragonfire shield / ward absorbs one charge (Wiki). */
        fun absorbDragonfire(p: PlayerRef) {
            val worn = p.worn(Wear.SHIELD)
            val pair = DRAGONFIRE.firstOrNull { worn == it.first || worn == it.second } ?: return
            val now = dragonfireCharges(p)
            if (now >= DRAGONFIRE_CAPACITY) return p.message("Your dragonfire shield is already fully charged.")
            if (worn == pair.second &&
                !p.transaction("charge dragonfire shield") { replaceAt(Invs.WORN, Wear.SHIELD, worn, pair.first) }
            ) {
                return
            }
            p.setSaved(DRAGONFIRE_KEY, now + 1)
            p.message("Your dragonfire shield glows more brightly.")
        }

        /**
         * Rondache (SP57): its +2 flat armour / magic resistance takes up to 2 off a hit that protection prayers didn't
         * already stop, charging that many points (at most 2 a hit, once every 2 ticks, up to 20).
         */
        fun rondache(target: EntityRef, damage: Int, style: HitStyle): Int {
            if (target !is PlayerRef || damage <= 0 || style == HitStyle.TYPELESS) return damage
            if (target.worn(Wear.SHIELD) != Objs.RONDACHE) return damage
            val absorbed = minOf(RONDACHE_PER_HIT, damage)
            val last = target[RONDACHE_LAST] ?: Long.MIN_VALUE
            if (target.tick - last >= RONDACHE_INTERVAL) {
                target[RONDACHE_LAST] = target.tick
                target.setSaved(RONDACHE_KEY, minOf(RONDACHE_CAP, rondacheCharges(target) + absorbed).toLong())
            }
            return damage - absorbed
        }

        private fun check(p: PlayerRef) {
            when (p.worn(Wear.SHIELD)) {
                Objs.RONDACHE -> p.message("Your rondache has ${rondacheCharges(p)} charges.")
                Objs.WYVERN_SHIELD -> p.message("The shield has ${p.saved(WYVERN_KEY) ?: 0L} charges.")
                in DRAGONFIRE.map { it.first } -> p.message("The shield has ${dragonfireCharges(p)} charges.")
            }
        }

        private fun empty(p: PlayerRef, slot: Int, charged: Int, uncharged: Int) {
            if (!p.transaction("empty dragonfire shield") { replaceAt(Invs.INV, slot, charged, uncharged) }) return
            p.setSaved(DRAGONFIRE_KEY, null)
            p.message("You vent the shield's remaining charges harmlessly into the air.")
        }

        /**
         * SP58-SP60: one blast at the current target within 10 tiles, one charge, then the cooldown: dragonfire up to 25
         * (ward and shield), icy breath up to 15 (wyvern). Accuracy rolls the user's Defence against magic defence
         * (OPEN-SP15: the attack roll's bonus). Not against dragons and wyverns, which are immune.
         */
        private fun operate(p: PlayerRef) {
            val shield = p.worn(Wear.SHIELD)
            val wyvern = shield == Objs.WYVERN_SHIELD
            if (!wyvern && shield !in DRAGONFIRE.map { it.first }) return
            val key = if (wyvern) WYVERN_KEY else DRAGONFIRE_KEY
            val target = operateTarget(p, key) ?: return
            p.setSaved(key, (p.saved(key) ?: 0L) - 1)
            blast(p, target, shield, wyvern)
        }

        /** The current target if the shield can fire at it now (charges, cooldown, range, immunity), else null with the message. */
        private fun operateTarget(p: PlayerRef, key: String): EntityRef? {
            val target = p.interactionTarget
            val npc = target as? NpcRef
            val immune =
                npc != null &&
                    (GearEffects.DRAGON in NpcCombatTable.of(npc.type)?.attributes.orEmpty() || "wyvern" in npc.displayName.lowercase())
            val refusal =
                when {
                    (p.saved(key) ?: 0L) <= 0 -> "Your shield has no charges."
                    (p[OPERATE_READY] ?: 0L) > p.tick -> "Your shield is still on cooldown from its last use."
                    target == null -> "You need a target to do that."
                    p.distanceTo(target) > OPERATE_RANGE -> "You are too far away."
                    immune -> "That creature is immune to dragonfire."
                    else -> null
                }
            if (refusal != null) {
                p.message(refusal)
                return null
            }
            return target?.takeIf { CombatSystem.canFight(p, it, HitStyle.MAGIC, quiet = false) }
        }

        private fun blast(p: PlayerRef, target: EntityRef, shield: Int, wyvern: Boolean) {
            p[OPERATE_READY] = p.tick + if (wyvern) WYVERN_COOLDOWN else DRAGONFIRE_COOLDOWN
            CombatSystem.announceAttack(p, target, CombatStyle.MAGIC)
            val eff = Formulas.effectiveLevel(p.level(Stat.DEFENCE), p.prayerPercent { it.defence }, 0) + 1
            val attackRoll = eff * (p.bonus(Param.ATTACK_MAGIC) + BONUS_BASE)
            val hit = Formulas.hits(attackRoll, Attacks.defenceRoll(target, AttackType.MAGIC), p::random)
            var max = if (wyvern) WYVERN_MAX else DRAGONFIRE_MAX
            if (target is PlayerRef && Dragonfire.hasShield(target)) max = minOf(max, SHIELDED_MAX)
            if (target is PlayerRef && Dragonfire.antifire(target) == 2) max = 0
            val delay = Formulas.magicDelay(p.distanceTo(target))
            // Visuals: the shield's row in the special table (operate anim, launch, projectile, impact).
            Specials.of(shield)?.let { row ->
                if (row.anim >= 0) p.anim(row.anim)
                if (row.spot >= 0) p.spotAnim(row.spot)
                if (row.projectile >= 0) ProjType.MAGIC.launch(p, target, row.projectile, delay)
                if (row.targetSpot >= 0) target.spotAnim(row.targetSpot, delay = target.ticksUntilHit(delay) * CYCLES_PER_TICK)
            }
            val damage = if (hit && max > 0) p.random(max + 1) else 0
            CombatSystem.queueHit(target, p, damage, HitStyle.MAGIC, delay)
            if (p[SharedAttrs.IN_WILDERNESS] == true && target is PlayerRef) p.message("Your shield fires a blast of dragonfire!")
        }

        private const val BONUS_BASE = 64
        private const val CYCLES_PER_TICK = 30
    }
}
