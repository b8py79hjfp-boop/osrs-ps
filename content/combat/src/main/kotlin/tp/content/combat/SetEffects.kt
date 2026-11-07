package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.HitEvent
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.SpotAnims

/**
 * Set and weapon effects that act when a hit lands or when the wearer is hit (docs/mechanics/gear-effects.md
 * GE70-GE80): the Barrows sets (Guthan's heal, Torag's and Karil's PvP drains, Ahrim's Strength drain, Dharok's
 * damned recoil, Torag's damned Defence), Soulreaper axe stacks, the blood moon set's quicker attacks and burns
 * (Eclipse atlatl with the eclipse moon set).
 */
public class SetEffects : ContentPlugin {
    override fun ContentScope.register() {
        events.on<HitEvent> { e -> onWearerHit(e) }
    }

    internal companion object {
        private val k = GearEffects

        /** A melee / ranged / magic hit by [player] landed on [target] for [damage] (an accurate hit). */
        @Suppress("CyclomaticComplexMethod") // GE70-GE76 rule list, one branch per set
        fun onLanded(player: PlayerRef, target: EntityRef, style: CombatStyle, damage: Int) {
            val kit = GearContext.kit(player)
            with(k) {
                when (style) {
                    CombatStyle.MELEE -> {
                        // GE70 Wiki "Guthan the Infested's equipment": a quarter of successful attacks heal the damage;
                        // with the amulet of the damned up to 10 over the base Hitpoints.
                        if (kit.guthan() && damage > 0 && player.random(QUARTER) == 0) {
                            val cap = player.maxHitpoints + if (kit.damned()) DAMNED_GUTHAN_OVERHEAL else 0
                            player.hitpoints = maxOf(player.hitpoints, minOf(cap, player.hitpoints + damage))
                            target.spotAnim(GUTHAN_SPOT)
                        }
                        // GE71 Wiki "Torag the Corrupted's equipment": against players, a quarter of successful hits
                        // take 20% run energy (OPEN-GE7: 20% of the maximum, 2,000 of 10,000).
                        if (kit.toragSet() && target is PlayerRef && player.random(QUARTER) == 0) {
                            target.runEnergy = maxOf(0, target.runEnergy - TORAG_ENERGY)
                            target.spotAnim(TORAG_SPOT)
                        }
                    }

                    CombatStyle.RANGED -> {
                        // GE72 Wiki "Karil the Tainted's equipment": against players, a quarter of hits lower Agility by 20%.
                        if (kit.karilSet() && target is PlayerRef && player.random(QUARTER) == 0) {
                            target.setLevel(Stat.AGILITY, target.level(Stat.AGILITY) - target.level(Stat.AGILITY) / KARIL_DIVISOR)
                            target.spotAnim(KARIL_SPOT)
                        }
                        // GE76 Wiki "Eclipse atlatl": with the eclipse moon set, 20% of successful hits burn.
                        if (kit.has("eclipse atlatl") && kit.all(*ECLIPSE_SET) && player.random(PERCENT) < ECLIPSE_BURN_CHANCE) {
                            Burns.burn(target, player)
                        }
                    }

                    CombatStyle.MAGIC -> {
                        // GE73 Wiki "Ahrim the Blighted's equipment": a quarter of (non-splashing) spells lower the
                        // target's Strength by 5.
                        if (kit.ahrimSet() && player.random(QUARTER) == 0) {
                            when (target) {
                                is PlayerRef -> target.setLevel(Stat.STRENGTH, maxOf(0, target.level(Stat.STRENGTH) - AHRIM_DRAIN))
                                is NpcRef -> target.setLevel(NpcStat.STRENGTH, target.level(NpcStat.STRENGTH) - AHRIM_DRAIN)
                            }
                            target.spotAnim(AHRIM_SPOT)
                        }
                    }
                }
            }
        }

        /** GE77 Soulreaper axe: every attack (hit or miss) adds a stack after its damage, up to 5; idle, one decays per 50 ticks. */
        fun afterAttack(player: PlayerRef) {
            if (!GearContext.kit(player).has("soulreaper axe")) return
            player[Attacks.SOULREAPER_STACKS] = minOf(GearEffects.MAX_SOULREAPER, (player[Attacks.SOULREAPER_STACKS] ?: 0) + 1)
            player.timer(SOULREAPER_DECAY, SOULREAPER_DECAY_TICKS, soft = true) {
                val left = (this[Attacks.SOULREAPER_STACKS] ?: 0) - 1
                this[Attacks.SOULREAPER_STACKS] = left.takeIf { it > 0 }
                if (left <= 0) stopTimer(SOULREAPER_DECAY)
            }
        }

        /**
         * GE78 Wiki "Blood moon armour": with the full set and the dual macuahuitl, a roll of 32 or less on 0..99 makes
         * the next attack a tick sooner; when both splats hit, the lower of two rolls counts.
         */
        fun bloodMoon(player: PlayerRef, splats: List<PostRoll.Splat>) {
            val kit = GearContext.kit(player)
            if (!kit.has("dual macuahuitl") || !kit.all(*BLOOD_MOON_SET)) return
            var roll = player.random(PERCENT)
            if (splats.size == 2 && splats.all { it.accurate }) roll = minOf(roll, player.random(PERCENT))
            if (roll <= BLOOD_MOON_QUICK) player.setAttackClock(player.attackClock() - 1)
        }

        /** Defence level of a player wearing Torag's set with the amulet of the damned (GE75, OPEN-GE8: 1% per 1% HP missing). */
        fun toragDefence(target: PlayerRef, level: Int): Int {
            val kit = GearContext.kit(target)
            if (!with(k) { kit.toragDamned() }) return level
            val missingPercent = (target.maxHitpoints - target.hitpoints).coerceAtLeast(0) * PERCENT / maxOf(1, target.maxHitpoints)
            return level * (PERCENT + missingPercent) / PERCENT
        }

        /** GE74 Wiki "Amulet of the damned": Dharok's set recoils 15% of a hit taken a quarter of the time. */
        private fun onWearerHit(e: HitEvent) {
            val wearer = e.target as? PlayerRef ?: return
            val source = e.source ?: return
            if (e.damage <= 0 || e.style == HitStyle.TYPELESS) return
            if (!with(k) { GearContext.kit(wearer).dharokDamned() } || wearer.random(QUARTER) != 0) return
            val recoil = e.damage * DHAROK_RECOIL / PERCENT
            if (recoil > 0) CombatSystem.applyHit(source, wearer, recoil, HitStyle.TYPELESS)
        }

        private const val QUARTER = 4
        private const val PERCENT = 100
        private const val DAMNED_GUTHAN_OVERHEAL = 10
        private const val TORAG_ENERGY = 2000
        private const val KARIL_DIVISOR = 5
        private const val AHRIM_DRAIN = 5
        private const val DHAROK_RECOIL = 15
        private const val ECLIPSE_BURN_CHANCE = 20
        private const val BLOOD_MOON_QUICK = 32
        private const val SOULREAPER_DECAY_TICKS = 50
        private val SOULREAPER_DECAY = TimerId("soulreaper_decay")

        private const val GUTHAN_SPOT = SpotAnims.BARROWS_GUTHAN_EFFECT
        private const val TORAG_SPOT = SpotAnims.BARROWS_TORAG_EFFECT
        private const val AHRIM_SPOT = SpotAnims.BARROWS_AHIRM_BLIGHTED_AURA
        private const val KARIL_SPOT = SpotAnims.BARROWS_KARIL_TAINTED_SHOT

        private val ECLIPSE_SET = arrayOf("eclipse moon helm", "eclipse moon chestplate", "eclipse moon tassets")
        private val BLOOD_MOON_SET = arrayOf("blood moon helm", "blood moon chestplate", "blood moon tassets")
    }
}

/**
 * Burns (Wiki "Burn"): 10 damage over 40 ticks, 1 every 4 ticks, at most 5 burns at once (further ones are lost).
 * Typeless: protection prayers don't reduce it.
 */
internal object Burns {
    private val ACTIVE = AttrKey<MutableList<Long>>("burns_until")

    fun burn(target: EntityRef, source: EntityRef) {
        val now = target.tick
        val active = (target[ACTIVE] ?: mutableListOf<Long>().also { target[ACTIVE] = it }).apply { removeAll { it <= now } }
        if (active.size >= MAX_STACKS) return
        active += now + DURATION
        for (i in 1..TICKS) CombatSystem.queueHit(target, source, 1, HitStyle.TYPELESS, i * INTERVAL)
    }

    private const val MAX_STACKS = 5
    private const val INTERVAL = 4
    private const val TICKS = 10
    private const val DURATION = (INTERVAL * TICKS).toLong()
}
