package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Varps

/**
 * Poison and venom (docs/mechanics/poison.md). State uses the client's encoding of varp `poison` (102), read by the
 * HP orb: 1..999,999 = poison severity (hit = ⌈severity / 5⌉, −1 per hit), ≥ 1,000,000 = venom (hit =
 * min(20, 2 × (value − 999,997)), +1 per hit), negative = immunity counting up to 0 once per interval (below −38 it
 * also covers venom). Players keep it in the varp (saved with the account); NPCs in an attr.
 */
internal object Poison {
    /** PO2: "once every 30 game ticks (18 seconds)" (Poison page). */
    const val INTERVAL = 30

    /** PO1/RuneLite `PoisonPlugin.VENOM_THRESHOLD`. */
    const val VENOM = 1_000_000

    /** RuneLite `TimersAndBuffsPlugin.VENOM_VALUE_CUTOFF`: immunity values below −38 also block venom (PO7). */
    const val VENOM_IMMUNE_BELOW = -38

    /** PO4: venom "capping at 20 damage per hit" (Venom page). */
    private const val VENOM_CAP = 20

    /** PO3: starting severity per point of starting damage (⌊(severity + 4) / 5⌋ = damage at severity 5 × damage). */
    const val SEVERITY_PER_DAMAGE = 5

    /** PO5: a venomous attack on a venom-immune, poison-susceptible target poisons at severity 26 (Poison page table). */
    const val VENOM_AS_POISON = 26

    // cache hitmark types (RS Mod `hitmark.sym`: 65 poison_damage_lit, 66 poison_damage_tint, 5 venom)
    private const val HITMARK_POISON_LIT = 65
    private const val HITMARK_POISON_TINT = 66
    private const val HITMARK_VENOM = 5

    private val TIMER = TimerId("poison")
    private val NPC_STATE = AttrKey<Int>("npc_poison")
    private val NPC_TICKING = AttrKey<Boolean>("npc_poison_ticking")

    fun state(e: EntityRef): Int =
        when (e) {
            is PlayerRef -> e.varp(Varps.POISON)
            is NpcRef -> e[NPC_STATE] ?: 0
        }

    private fun setState(e: EntityRef, value: Int) {
        when (e) {
            is PlayerRef -> e.setVarp(Varps.POISON, value)
            is NpcRef -> e[NPC_STATE] = value.takeIf { it != 0 }
        }
    }

    fun isVenomed(e: EntityRef): Boolean = state(e) >= VENOM

    private fun poisonImmune(e: EntityRef): Boolean =
        when (e) {
            is PlayerRef -> state(e) < 0 || Toxic.wearsSerpentineHelm(e) || DonatorImmunity.active(e)
            is NpcRef -> NpcCombatTable.of(e.type)?.poisonImmune == true
        }

    private fun venomImmune(e: EntityRef): Boolean =
        when (e) {
            is PlayerRef -> state(e) < VENOM_IMMUNE_BELOW || Toxic.wearsSerpentineHelm(e) || DonatorImmunity.active(e)
            is NpcRef -> NpcCombatTable.of(e.type)?.venomImmune == true
        }

    /** PO3: poison [target] at [severity] unless immune or already venomed; a stronger poison replaces a weaker one. */
    fun poison(target: EntityRef, severity: Int) {
        if (severity <= 0 || target.isDying || poisonImmune(target)) return
        val now = state(target)
        if (now >= VENOM || now >= severity) return
        setState(target, severity)
        if (target is PlayerRef) target.message("You have been poisoned!")
        start(target)
    }

    /** PO4/PO5: envenom [target]; a venom-immune but poison-susceptible target is poisoned instead. */
    fun envenom(target: EntityRef) {
        if (target.isDying) return
        if (venomImmune(target)) {
            poison(target, VENOM_AS_POISON)
            return
        }
        if (isVenomed(target)) return
        setState(target, VENOM)
        if (target is PlayerRef) target.message("You have been envenomed!")
        start(target)
    }

    /**
     * PO6 antipoison family: venom becomes poison at its current damage (no immunity); otherwise poison is cured and
     * immunity set to [immunity] intervals (negative state). [antiVenom] cures venom outright.
     */
    fun cure(player: PlayerRef, immunity: Int, antiVenom: Boolean) {
        val now = state(player)
        if (now >= VENOM && !antiVenom) {
            setState(player, hitDamage(now) * SEVERITY_PER_DAMAGE)
            return
        }
        setState(player, minOf(if (now < 0) now else 0, -immunity))
        start(player)
    }

    /** Clears poison, venom and immunity (death). */
    fun clear(e: EntityRef) {
        setState(e, 0)
        if (e is PlayerRef) e.stopTimer(TIMER)
    }

    /** Restarts the player's poison timer at login when the varp holds poison, venom or immunity. */
    fun onLogin(player: PlayerRef) {
        if (state(player) != 0) start(player)
    }

    fun hitDamage(state: Int): Int =
        if (state >= VENOM) minOf(VENOM_CAP, 2 * (state - VENOM + 3)) else (state + SEVERITY_PER_DAMAGE - 1) / SEVERITY_PER_DAMAGE

    private fun start(e: EntityRef) {
        when (e) {
            // T6: toxins are normal timers (they pause while a modal is open or the player is delayed).
            is PlayerRef -> {
                if (!e.hasTimer(TIMER)) e.timer(TIMER, INTERVAL, soft = false) { tick(this) }
            }

            is NpcRef -> {
                if (e[NPC_TICKING] != true) {
                    e[NPC_TICKING] = true
                    npcTick(e)
                }
            }
        }
    }

    /** One interval: damage and step the state toward 0 (immunity counts up). */
    private fun tick(e: EntityRef) {
        val s = state(e)
        when {
            s == 0 -> {
                if (e is PlayerRef) e.stopTimer(TIMER)
            }

            s < 0 -> {
                setState(e, s + 1)
            }

            else -> {
                val venom = s >= VENOM
                setState(e, if (venom) s + 1 else s - 1)
                damage(e, hitDamage(s), venom)
            }
        }
    }

    /** NPC poison runs on a re-queued script; it ends when cured or when the NPC leaves combat (PO8). */
    private fun npcTick(npc: NpcRef) {
        npc.queueHit(INTERVAL, strong = false) {
            if (!npc.isInWorld || npc.target == null || state(npc) <= 0) {
                npc[NPC_STATE] = null
                npc[NPC_TICKING] = null
                return@queueHit
            }
            tick(npc)
            npcTick(npc)
        }
    }

    private fun damage(e: EntityRef, amount: Int, venom: Boolean) {
        if (e.isDying || amount <= 0) return
        val lit = if (venom) HITMARK_VENOM else HITMARK_POISON_LIT
        val tint = if (venom) HITMARK_VENOM else HITMARK_POISON_TINT
        CombatSystem.applyHit(e, null, amount, HitStyle.TYPELESS, mark = CombatSystem.Hitmark(lit, tint))
    }
}

/**
 * Who poisons or envenoms on a hit (poison.md PO9–PO12): poisoned weapons by obj name suffix, the venomous weapons,
 * the serpentine helm against NPCs, and NPCs from the Wiki table.
 */
internal object Toxic {
    /** PO9: "(p)" 20, "(p+)" 25, "(p++)" 30 severity (Poison page weapon table). */
    private val WEAPON_POISON = listOf("(p++)" to 30, "(p+)" to 25, "(p)" to 20)

    /** PO9: "Poisons applied with ranged weapons have their Severity lowered by 14". */
    private const val RANGED_SEVERITY_PENALTY = 14

    /** PO9: "1/4 for melee, 1/8 for ranged" (Mod Ash via the Poison page). */
    private const val MELEE_CHANCE = 4
    private const val RANGED_CHANCE = 8

    /** PO10: 25% base chance for the toxic blowpipe, toxic staff of the dead and trident of the swamp (Venom page). */
    private val VENOM_WEAPONS = setOf("Toxic blowpipe", "Toxic staff of the dead", "Trident of the swamp")
    private const val VENOM_CHANCE = 4

    /** PO11: serpentine helm vs NPCs: 1/6 with a non-poisoned melee weapon, 1/2 with a poisoned one (helm page). */
    private const val HELM_CHANCE = 6
    private const val HELM_POISONED_CHANCE = 2

    /** OPEN-PO1: an NPC's chance to poison per attack is not on the Wiki; uses the melee weapon chance (1/4). */
    private const val NPC_CHANCE = 4

    fun wearsSerpentineHelm(player: PlayerRef): Boolean {
        val helm = player.worn(Wear.HEAD)
        return helm >= 0 && player.objName(helm).startsWith("Serpentine helm")
    }

    private fun severityOf(name: String): Int = WEAPON_POISON.firstOrNull { name.endsWith(it.first) }?.second ?: 0

    /**
     * A player's hit that dealt damage landed on [target] (PO9: "Damage must be non-zero"). Venomous weapons first,
     * then the serpentine helm, then weapon poison.
     */
    fun onPlayerHit(player: PlayerRef, target: EntityRef, style: CombatStyle, damage: Int) {
        if (damage <= 0) return
        val weapon = player.worn(Wear.WEAPON)
        val weaponName = if (weapon >= 0) player.objName(weapon) else ""
        val helm = target is NpcRef && wearsSerpentineHelm(player)
        if (weaponName in VENOM_WEAPONS) {
            if (helm || player.random(VENOM_CHANCE) == 0) Poison.envenom(target)
            return
        }
        val source =
            when (style) {
                CombatStyle.MELEE -> weaponName
                CombatStyle.RANGED -> player.worn(Wear.AMMO).takeIf { it >= 0 && usesAmmo(player) }?.let(player::objName) ?: weaponName
                CombatStyle.MAGIC -> return
            }
        val severity = severityOf(source)
        if (helm && style == CombatStyle.MELEE) {
            val chance = if (severity > 0) HELM_POISONED_CHANCE else HELM_CHANCE
            if (player.random(chance) == 0) {
                Poison.envenom(target)
                return
            }
        }
        if (severity <= 0) return
        if (style == CombatStyle.MELEE) {
            if (player.random(MELEE_CHANCE) == 0) Poison.poison(target, severity)
        } else if (player.random(RANGED_CHANCE) == 0) {
            Poison.poison(target, severity - RANGED_SEVERITY_PENALTY)
        }
    }

    private fun usesAmmo(player: PlayerRef): Boolean {
        val c = player.weaponCategory()
        return c === Weapons.BOW || c === Weapons.CROSSBOW
    }

    /** PO12: poisonous NPCs may poison "regardless of whether or not they inflict any damage, even if protection prayers are used". */
    fun onNpcAttack(npc: NpcRef, def: NpcCombatDef, victim: EntityRef) {
        if (def.poison <= 0 && !def.venomous) return
        if (npc.random(NPC_CHANCE) != 0) return
        if (def.venomous) Poison.envenom(victim) else Poison.poison(victim, def.poison * Poison.SEVERITY_PER_DAMAGE)
    }
}
