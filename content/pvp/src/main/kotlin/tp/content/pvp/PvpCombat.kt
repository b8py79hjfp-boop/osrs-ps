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
import tp.plugin.SpellRunes
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits

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
        bus.on<tp.plugin.commands.SkullRequest> { r ->
            // `::skull` (commands brief §6): the same skull as an unprovoked attack, 20 minutes (SKULL_TICKS).
            skull(bus, r.player)
            r.handled = true
        }
        onButton(Components.Logout.LOGOUT) { requestLogout() }
        onButton(Components.Worldswitcher.LOGOUT) { requestLogout() }
        onButton(Components.Worldswitcher.BUTTONS) { click -> requestHop(click.slot) }
        onButton(Components.Magic_Spellbook.VENGEANCE) { castVengeance() }
        onApPlayerT(Components.Magic_Spellbook.VENGEANCE_OTHER) { target ->
            if (currentApRange != VENG_OTHER_RANGE) return@onApPlayerT apRange(VENG_OTHER_RANGE)
            player.castVengeanceOther(target)
        }
        onApPlayerT(Components.Magic_Spellbook.TELEPORT_BLOCK) { target ->
            if (currentApRange != SPELL_RANGE) {
                apRange(SPELL_RANGE)
            } else {
                teleBlock(bus, player, target)
            }
        }
    }

    // ---- PJ timers (single-way)

    /**
     * PJ: "both players start a longer PJ timer of 20 ticks" in single-way Wilderness PvP (16 on PvP worlds). A player
     * fighting an NPC can't be attacked by another player in single-way combat, except in singles-plus areas (W13).
     */
    private fun singleWay(check: AttackCheck) {
        val attacker = check.attacker as? PlayerRef ?: return
        val target = check.target as? PlayerRef ?: return
        if (Wilderness.isMulti(target.coord)) return // multi-combat: no PJ protection
        val now = attacker.tick
        if ((target[NPC_FIGHT_UNTIL] ?: 0L) > now && !Wilderness.isSinglesPlus(target.coord)) {
            check.deny("Someone else is fighting that player.")
            return
        }
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
        val pj = pjTicks(attacker)
        for ((a, b) in listOf(attacker to target, target to attacker)) {
            a[FIGHTING] = b
            a[FIGHTING_UNTIL] = now + pj
        }
        target[HIT_BY] = attacker
        target[HIT_BY_UNTIL] = now + pj
        // Duels never skull (duel-arena.md DA1: a safe fight).
        if (attacker[SharedAttrs.DUEL_OPPONENT] != null) return
        // Skull: attacking a player who hasn't attacked you (OPEN-SK1: "unprovoked" = not attacked by them in the last 20 min).
        val provokedAt = attacker[AGGRESSORS]?.get(target.username) ?: Long.MIN_VALUE
        target[AGGRESSORS] = (target[AGGRESSORS] ?: emptyMap()) + (attacker.username to now)
        if (now - provokedAt > AGGRESSOR_MEMORY) skull(bus, attacker)
    }

    /** PJ timer page: 20 ticks in the Wilderness; "increased in the PvP worlds to 16 ticks". */
    private fun pjTicks(player: PlayerRef): Int = if (player.isPvpWorld) PVP_WORLD_PJ_TICKS else PJ_TICKS

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
        if (inLogoutLock()) {
            message("You can't log out until 10 seconds after the end of combat.")
            return
        }
        logout()
    }

    /** Hit within the last [LOGOUT_LOCK] ticks (never hit = no lock). */
    private fun PlayerRef.inLogoutLock(): Boolean {
        val lastHit = this[LAST_HIT_TICK] ?: return false
        return tick - lastHit < LOGOUT_LOCK
    }

    /** World hop: the same lock as logging out (docs/mechanics/world-hop.md H1). */
    private fun PlayerRef.requestHop(worldId: Int) {
        if (inLogoutLock()) {
            message("You can't switch worlds until 10 seconds after the end of combat.")
            return
        }
        if (!hop(worldId)) message("That world is not available.")
    }

    // ---- hits: vengeance, recoil, logout lock clock

    private fun onHit(e: HitEvent) {
        val target = e.target
        target[LAST_HIT_TICK] = target.tick
        val source = e.source ?: return
        // An NPC's hit keeps a player in PvM combat for the PJ window (single-way protection, W13).
        if (source is tp.plugin.NpcRef && target is PlayerRef) target[NPC_FIGHT_UNTIL] = target.tick + pjTicks(target)
        if (e.damage <= 0 || e.style == HitStyle.TYPELESS || target !is PlayerRef) return
        // Wilderness bosses: "immune to Ring of Recoil and similar effects" (bosses.md B3).
        if (source[SharedAttrs.REFLECT_IMMUNE] == true) return
        // Vengeance: "Reflects 75% of the next damage, rounded down" (Vengeance page).
        if (target[VENGEANCE] == true) {
            target[VENGEANCE] = null
            target.say("Taste vengeance!")
            val reflected = maxOf(1, e.damage * VENG_PERCENT / PERCENT) // "rounded down ... minimum of 1" (VG:27)
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
        val killer = e.killer as? PlayerRef
        // `::kdr` (commands brief §6): PvP kills and deaths, not duels (a safe fight, DA1).
        if (killer != null && killer != dead && dead[SharedAttrs.DUEL_OPPONENT] == null) PvpStats.record(killer, dead)
        dead[FIGHTING] = null
        dead[HIT_BY] = null
        dead[VENGEANCE] = null
        dead[SKULLED_UNTIL] = null
        dead[SharedAttrs.SKULL_ICON] = null
    }

    // ---- spells

    private fun PlayerRef.castVengeance() {
        if (!payVengeance(VENG_LEVEL, VENG_RUNES, VENG_XP)) return
        this[VENGEANCE] = true
    }

    /**
     * Vengeance Other (Wiki "Vengeance Other": Magic 93, astral 3 + death 2 + earth 10, 108 xp, the shared 30-second
     * cooldown): gives [target] the same reflection, if their Accept Aid setting is on and they have none yet.
     */
    private fun PlayerRef.castVengeanceOther(target: PlayerRef) {
        // Wiki: not in a single-combat part of the Wilderness (OPEN text); the player-owned-house rule is OPEN-VO1.
        if (this[SharedAttrs.IN_WILDERNESS] == true && this[SharedAttrs.IN_MULTI] != true) return message("You can't cast that here.")
        if (target.varbit(Varbits.OPTION_ACCEPTAID) == 0) return message("That player is not accepting aid.")
        if (target[VENGEANCE] == true) return message("That player already has vengeance cast on them.")
        if (!payVengeance(VENG_OTHER_LEVEL, VENG_OTHER_RUNES, VENG_OTHER_XP)) return
        face(target)
        target[VENGEANCE] = true
        target.message("You have the power of vengeance!")
    }

    /** Level, the shared cooldown (W9) and the runes (staves and combination runes count); true once paid. */
    private fun PlayerRef.payVengeance(level: Int, runes: List<Pair<Int, Int>>, xp: Double): Boolean {
        if (baseLevel(MAGIC) < level) {
            message("You need a Magic level of $level to cast this spell.")
            return false
        }
        if ((this[VENG_COOLDOWN_UNTIL] ?: 0L) > tick) {
            message("You can only cast vengeance spells every 30 seconds.")
            return false
        }
        if (!transaction("vengeance") { SpellRunes.take(this, this@payVengeance, runes) }) {
            message("You do not have enough runes to cast this spell.")
            return false
        }
        this[VENG_COOLDOWN_UNTIL] = tick + VENG_COOLDOWN
        addXp(MAGIC, xp)
        return true
    }

    /**
     * Tele Block: applied "if it successfully hits the target" (normal magic accuracy; Protect from Magic "does not affect
     * the chance that Tele Block will splash"), 5 minutes, halved if the target had Protect from Magic up (Tele Block page).
     */
    private fun teleBlock(bus: EventBus, caster: PlayerRef, target: PlayerRef) {
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
        val roll = tp.plugin.SpellAccuracyRoll(caster, target)
        bus.publish(roll)
        if (!roll.hit) return // splash: runes are used, no effect
        val protectedNow = target.varbit(tp.symbols.Varbits.PRAYER_PROTECTFROMMAGIC) == 1
        target[SharedAttrs.TELEBLOCK_UNTIL] = now + if (protectedNow) TELEBLOCK_TICKS / 2 else TELEBLOCK_TICKS
        target.message("A Tele Block spell has been cast on you by ${caster.username}.")
    }

    private companion object {
        val FIGHTING = AttrKey<PlayerRef>("pvp_fighting")
        val FIGHTING_UNTIL = AttrKey<Long>("pvp_fighting_until")
        val HIT_BY = AttrKey<PlayerRef>("pvp_hit_by")
        val HIT_BY_UNTIL = AttrKey<Long>("pvp_hit_by_until")
        val NPC_FIGHT_UNTIL = AttrKey<Long>("pvm_fight_until")
        val AGGRESSORS = AttrKey<Map<String, Long>>("pvp_aggressors")
        val SKULLED_UNTIL = AttrKey<Long>("skulled_until")
        val LAST_HIT_TICK = AttrKey<Long>("pvp_last_hit_tick")
        val VENGEANCE = AttrKey<Boolean>("vengeance")
        val VENG_COOLDOWN_UNTIL = AttrKey<Long>("vengeance_cooldown")
        val RECOIL_CHARGES = AttrKey<Int>("recoil_charges")
        val SKULL_TIMER = TimerId("skull")

        /** PJ timer page: 20 ticks in single-way Wilderness PvP. */
        const val PJ_TICKS = 20

        /** PJ timer page: "increased in the PvP worlds to 16 ticks (9.6 seconds)". */
        const val PVP_WORLD_PJ_TICKS = 16

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
        val VENG_RUNES = listOf(Objs.ASTRALRUNE to 4, Objs.DEATHRUNE to 2, Objs.EARTHRUNE to 10)

        /** Wiki "Vengeance": 112 xp. */
        const val VENG_XP = 112.0

        /** Wiki "Vengeance Other": Magic 93, astral 3 + death 2 + earth 10, 108 xp; cast like a spell at range 10. */
        const val VENG_OTHER_LEVEL = 93
        val VENG_OTHER_RUNES = listOf(Objs.ASTRALRUNE to 3, Objs.DEATHRUNE to 2, Objs.EARTHRUNE to 10)
        const val VENG_OTHER_XP = 108.0
        const val VENG_OTHER_RANGE = 10
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
