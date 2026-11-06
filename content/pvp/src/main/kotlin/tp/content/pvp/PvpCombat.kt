package tp.content.pvp

import tp.plugin.AppearanceChanged
import tp.plugin.AttackCheck
import tp.plugin.AttackEvent
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.EntityRef
import tp.plugin.EventBus
import tp.plugin.HitEvent
import tp.plugin.HitStyle
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * Wilderness PvP rules (docs/mechanics/wilderness.md): single-way PJ timers, skulls, the combat logout lock, Tele Block,
 * Vengeance and the ring of recoil. Sources: OSRS Wiki "PJ timer", "Skull (status)", "Logout button", "Tele Block",
 * "Vengeance", "Ring of recoil".
 */
public class PvpCombat : ContentPlugin {
    override fun ContentScope.register() {
        val bus = events
        bus.on<AttackCheck> { check -> singleWay(check) }
        bus.on<AttackEvent> { e -> onAttack(bus, e) }
        bus.on<HitEvent> { e -> onHit(e) }
        bus.on<DeathEvent> { e -> onDeath(e) }
        onButton(Components.Logout.LOGOUT) { requestLogout() }
        onButton(Components.Magic_Spellbook.VENGEANCE) { castVengeance() }
        onApPlayerT(Components.Magic_Spellbook.TELEPORT_BLOCK) { target ->
            if (currentApRange != SPELL_RANGE) {
                apRange(SPELL_RANGE)
            } else {
                teleBlock(player, target)
            }
        }
    }

    // ---- PJ timers (single-way)

    /** PJ: "both players start a longer PJ timer of 20 ticks" in single-way Wilderness PvP. OPEN-M1: multi areas. */
    private fun singleWay(check: AttackCheck) {
        val attacker = check.attacker as? PlayerRef ?: return
        val target = check.target as? PlayerRef ?: return
        val now = attacker.tick
        val targetFoe = target[FIGHTING]
        if (targetFoe != null && targetFoe != attacker && (target[FIGHTING_UNTIL] ?: 0L) > now) {
            check.deny("Someone else is fighting that player.")
            return
        }
        val ownFoe = attacker[HIT_BY]
        if (ownFoe != null && ownFoe != target && (attacker[HIT_BY_UNTIL] ?: 0L) > now) check.deny("You are already under attack!")
    }

    private fun onAttack(bus: EventBus, e: AttackEvent) {
        val attacker = e.attacker as? PlayerRef ?: return
        val target = e.target as? PlayerRef ?: return
        val now = attacker.tick
        for ((a, b) in listOf(attacker to target, target to attacker)) {
            a[FIGHTING] = b
            a[FIGHTING_UNTIL] = now + PJ_TICKS
        }
        target[HIT_BY] = attacker
        target[HIT_BY_UNTIL] = now + PJ_TICKS
        // Skull: attacking a player who hasn't attacked you (OPEN-SK1: "unprovoked" = not attacked by them in the last 20 min).
        val provokedAt = attacker[AGGRESSORS]?.get(target.username) ?: Long.MIN_VALUE
        target[AGGRESSORS] = (target[AGGRESSORS] ?: emptyMap()) + (attacker.username to now)
        if (now - provokedAt > AGGRESSOR_MEMORY) skull(bus, attacker)
    }

    private fun skull(bus: EventBus, player: PlayerRef) {
        player[SKULLED_UNTIL] = player.tick + SKULL_TICKS
        if (player[SharedAttrs.SKULL_ICON] != SKULL_ICON) {
            player[SharedAttrs.SKULL_ICON] = SKULL_ICON
            bus.publish(AppearanceChanged(player))
        }
        player.timer(SKULL_TIMER, SKULL_CHECK, soft = true) {
            if ((this[SKULLED_UNTIL] ?: 0L) <= tick && (this[FIGHTING_UNTIL] ?: 0L) <= tick) {
                this[SharedAttrs.SKULL_ICON] = null
                stopTimer(SKULL_TIMER)
                bus.publish(AppearanceChanged(this))
            }
        }
    }

    // ---- logout lock

    /** "players cannot log out for 16 ticks after being attacked" (PJ timer page, Logout button page). */
    private fun PlayerRef.requestLogout() {
        val lastHit = this[LAST_HIT_TICK] ?: Long.MIN_VALUE
        if (tick - lastHit < LOGOUT_LOCK) {
            message("You can't log out until 10 seconds after the end of combat.")
            return
        }
        logout()
    }

    // ---- hits: vengeance, recoil, logout lock clock

    private fun onHit(e: HitEvent) {
        val target = e.target
        target[LAST_HIT_TICK] = target.tick
        val source = e.source ?: return
        if (e.damage <= 0 || e.style == HitStyle.TYPELESS || target !is PlayerRef) return
        // Vengeance: "Reflects 75% of the next damage, rounded down" (Vengeance page).
        if (target[VENGEANCE] == true) {
            target[VENGEANCE] = null
            target.say("Taste vengeance!")
            val reflected = e.damage * VENG_PERCENT / PERCENT
            source.queueHit(0, strong = false) { applyTypeless(source, target, reflected) }
        }
        // Ring of recoil: ⌊10% + 1⌋ of damage received, 40 damage per ring (Ring of recoil page), normal queue (brief §8).
        if (target.inv(Invs.WORN).obj(RING_SLOT) == Objs.RING_OF_RECOIL) {
            val charges = target[RECOIL_CHARGES] ?: RECOIL_TOTAL
            val reflected = minOf(charges, e.damage / RECOIL_DIVISOR + 1)
            val left = charges - reflected
            target[RECOIL_CHARGES] = if (left <= 0) null else left
            if (left <= 0) {
                target.transaction("recoil shatter") { removeAt(Invs.WORN, RING_SLOT, Objs.RING_OF_RECOIL, 1) }
                target.message("Your Ring of Recoil has shattered.")
            }
            source.queueHit(0, strong = false) { applyTypeless(source, target, reflected) }
        }
    }

    /** Typeless damage (no protection, no further reflection), shown as a hitsplat. */
    private fun applyTypeless(target: EntityRef, source: EntityRef, damage: Int) {
        if (target.hitpoints <= 0) return
        val dealt = minOf(damage, target.hitpoints)
        target.hitpoints -= dealt
        target.hitsplat(source, dealt, HITMARK_LIT, HITMARK_LIT, HITMARK_TINT)
    }

    private fun onDeath(e: DeathEvent) {
        val dead = e.entity as? PlayerRef ?: return
        dead[FIGHTING] = null
        dead[HIT_BY] = null
        dead[VENGEANCE] = null
        dead[SKULLED_UNTIL] = null
        dead[SharedAttrs.SKULL_ICON] = null
    }

    // ---- spells

    private fun PlayerRef.castVengeance() {
        if (baseLevel(MAGIC) < VENG_LEVEL) {
            message("You need a Magic level of $VENG_LEVEL to cast this spell.")
            return
        }
        if ((this[VENG_COOLDOWN_UNTIL] ?: 0L) > tick) {
            message("You can only cast vengeance spells every 30 seconds.")
            return
        }
        val paid =
            transaction("vengeance") {
                remove(Invs.INV, Objs.ASTRALRUNE, 4) && remove(Invs.INV, Objs.DEATHRUNE, 2) &&
                    remove(Invs.INV, Objs.EARTHRUNE, 10)
            }
        if (!paid) {
            message("You do not have enough runes to cast this spell.")
            return
        }
        this[VENGEANCE] = true
        this[VENG_COOLDOWN_UNTIL] = tick + VENG_COOLDOWN
    }

    /** Tele Block: 5 minutes, halved if the target had Protect from Magic up (Tele Block page); OPEN-TB1 accuracy. */
    private fun teleBlock(caster: PlayerRef, target: PlayerRef) {
        val now = caster.tick
        if ((target[SharedAttrs.TELEBLOCK_UNTIL] ?: 0L) > now) {
            caster.message("That player is already affected by this spell.")
            return
        }
        val paid =
            caster.transaction("teleblock") {
                remove(Invs.INV, Objs.LAWRUNE, 1) && remove(Invs.INV, Objs.CHAOSRUNE, 1) &&
                    remove(Invs.INV, Objs.DEATHRUNE, 1)
            }
        if (!paid) {
            caster.message("You do not have enough runes to cast this spell.")
            return
        }
        val protectedNow = target.varbit(tp.symbols.Varbits.PRAYER_PROTECTFROMMAGIC) == 1
        target[SharedAttrs.TELEBLOCK_UNTIL] = now + if (protectedNow) TELEBLOCK_TICKS / 2 else TELEBLOCK_TICKS
        target.message("A Tele Block spell has been cast on you by ${caster.username}.")
    }

    private companion object {
        val FIGHTING = AttrKey<PlayerRef>("pvp_fighting")
        val FIGHTING_UNTIL = AttrKey<Long>("pvp_fighting_until")
        val HIT_BY = AttrKey<PlayerRef>("pvp_hit_by")
        val HIT_BY_UNTIL = AttrKey<Long>("pvp_hit_by_until")
        val AGGRESSORS = AttrKey<Map<String, Long>>("pvp_aggressors")
        val SKULLED_UNTIL = AttrKey<Long>("skulled_until")
        val LAST_HIT_TICK = AttrKey<Long>("pvp_last_hit_tick")
        val VENGEANCE = AttrKey<Boolean>("vengeance")
        val VENG_COOLDOWN_UNTIL = AttrKey<Long>("vengeance_cooldown")
        val RECOIL_CHARGES = AttrKey<Int>("recoil_charges")
        val SKULL_TIMER = TimerId("skull")

        /** PJ timer page: 20 ticks in single-way Wilderness PvP. */
        const val PJ_TICKS = 20

        /** Skull page: 30 minutes after attacking a player. */
        const val SKULL_TICKS = 3000
        const val SKULL_CHECK = 25
        const val SKULL_ICON = 0

        /** OPEN-SK1: how long being attacked lets you retaliate without a skull; using the skull length. */
        const val AGGRESSOR_MEMORY = 3000L

        /** 16 ticks after being attacked (PJ timer, Logout button pages). */
        const val LOGOUT_LOCK = 16

        const val VENG_PERCENT = 75
        const val PERCENT = 100

        /** Vengeance: 30 s cooldown = 50 ticks; Magic 94 (Vengeance page). */
        const val VENG_COOLDOWN = 50
        const val VENG_LEVEL = 94
        const val MAGIC = 6

        const val RECOIL_TOTAL = 40
        const val RECOIL_DIVISOR = 10
        const val RING_SLOT = 12

        /** 5 minutes = 500 ticks. */
        const val TELEBLOCK_TICKS = 500
        const val SPELL_RANGE = 10

        const val HITMARK_LIT = 16
        const val HITMARK_TINT = 17
    }
}
