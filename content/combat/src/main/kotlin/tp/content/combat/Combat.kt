package tp.content.combat

import tp.plugin.AttackCheck
import tp.plugin.AttackEvent
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.EntityRef
import tp.plugin.EventBus
import tp.plugin.HitEvent
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.NpcScript
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Seqs
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * Melee and ranged combat for players and NPCs (docs/mechanics/combat-timeline.md, combat-formulas.md): attack
 * interactions, the attack clock, hits queued on the defender as strong scripts, application (HP, hitsplat, head bar,
 * retaliation, death). Magic lives in [Magic]; specials in [Specials].
 */
public class Combat : ContentPlugin {
    override fun ContentScope.register() {
        CombatSystem.bus = events
        // Parse the Wiki NPC table at boot, not on the first NPC hit (no tick-time allocation spike).
        onWorldStart { check(NpcCombatTable.size > 0) { "npc-combat.tsv is empty" } }
        onLogin {
            if (varp(Varps.SA_ENERGY) == 0 && this[Attr.ATTACK_CLOCK] == null) setVarp(Varps.SA_ENERGY, Specials.FULL_ENERGY)
            Specials.startRegen(this)
            Poison.onLogin(this)
        }
        onOpNpcOption("Attack") { npc -> CombatSystem.attackFromOp(this, npc) }
        onApNpcOption("Attack") { npc -> CombatSystem.attackFromAp(this, npc) }
        onOpPlayer(ATTACK_OP) { other -> CombatSystem.attackFromOp(this, other) }
        onApPlayer(ATTACK_OP) { other -> CombatSystem.attackFromAp(this, other) }
        val stances =
            listOf(
                Components.Combat_Interface._0,
                Components.Combat_Interface._1,
                Components.Combat_Interface._2,
                Components.Combat_Interface._3,
            )
        stances.forEachIndexed { mode, component ->
            onButton(component) { if (weaponCategory().slots.getOrNull(mode) != null) setVarp(Varps.COM_MODE, mode) }
        }
        onButton(Components.Combat_Interface.RETALIATE) { setVarp(Varps.OPTION_NODEF, 1 - varp(Varps.OPTION_NODEF)) }
        onButton(Components.Combat_Interface.SPECIAL_ATTACK) { Specials.toggle(this) }
        onButton(Components.Orbs.SPECBUTTON) { Specials.toggle(this) }
    }

    public companion object {
        /** Player op slot of "Attack" (set by PvP content; RS Mod `LoginScript` leaves slot 2 for it). */
        public const val ATTACK_OP: Int = 2
    }
}

/** The combat rules shared by every attack type. */
internal object CombatSystem {
    /** The content event bus (set at registration; content modules talk through it). */
    lateinit var bus: EventBus

    // ---- hitmark / head bar config ids (cache hitmark/headbar types; RS Mod `hitmark_groups`, `headbars`)
    private const val HITMARK_DAMAGE_LIT = 16
    private const val HITMARK_DAMAGE_TINT = 17
    private const val HITMARK_BLOCK_LIT = 12
    private const val HITMARK_BLOCK_TINT = 13
    private const val HEADBAR_HEALTH_30 = 0
    private const val HEADBAR_SEGMENTS = 30

    /** Player death: animation 2 ticks in, respawn 4 later (RS Mod `PlayerDeath`: delay(2), anim, delay(4)). */
    private const val DEATH_ANIM_DELAY = 2
    private const val DEATH_RESPAWN_DELAY = 6

    /** OPEN-N2: NPC death length isn't in the cache or the Wiki infobox; dev default. */
    private const val NPC_DEATH_DELAY = 4

    /** Respawn ticks for NPCs the Wiki table has no respawn time for (npc-combat.md N6). */
    private const val NPC_RESPAWN_DELAY = 25

    /** Respawn point: Lumbridge (RS Mod `PlayerDeath` default). */
    private val RESPAWN = CoordGrid(3222, 3218, 0)

    // ---- interaction entry points

    /** Op script (in reach): melee attacks here; ranged/magic too (reach implies range). */
    fun attackFromOp(script: PlayerScript, target: EntityRef) {
        attackLoop(script, target)
    }

    /** Ap script: ranged sets its approach range (I2); melee turns the ap into an op (aprange -1). */
    fun attackFromAp(script: PlayerScript, target: EntityRef) {
        val player = script.player
        val style = player.stanceAndType().second.style
        if (style == CombatStyle.MELEE) {
            if (script.currentApRange != -1) script.apRange(-1)
            return
        }
        val range = player.attackRange()
        if (script.currentApRange != range) {
            script.apRange(range)
            return
        }
        attackLoop(script, target)
    }

    private fun attackLoop(script: PlayerScript, target: EntityRef) {
        val player = script.player
        if (!canFight(player, target)) return
        script.keepInteraction()
        player.face(target)
        if (player.attackClock() > player.tick) return
        Attacks.playerAttack(player, target)
    }

    /** Whether [attacker] may attack [target] now (AttackCheck event: PvP rules subscribe). */
    fun canFight(attacker: EntityRef, target: EntityRef): Boolean {
        if (target.isDying || attacker.isDying) return false
        if (target is NpcRef && !target.isInWorld) return false
        val check = AttackCheck(attacker, target)
        bus.publish(check)
        val denial = check.denial
        if (denial != null || !check.allowed) {
            if (attacker is PlayerRef) attacker.message(denial ?: "You can't attack that.")
            return false
        }
        return true
    }

    // ---- hit application (combat-timeline.md: application tick in the defender's queue)

    /** Queues a hit on [target] [delay] ticks from now; [onLand] runs after HP changes if the hit wasn't discarded. */
    fun queueHit(target: EntityRef, source: EntityRef?, damage: Int, style: HitStyle, delay: Int, onLand: (() -> Unit)? = null) {
        target.queueHit(delay) { applyHit(target, source, damage, style, onLand) }
    }

    /** A hitsplat type pair: [lit] for the entity itself and the source, [tint] for other viewers. */
    class Hitmark(val lit: Int, val tint: Int)

    private val REGULAR = Hitmark(HITMARK_DAMAGE_LIT, HITMARK_DAMAGE_TINT)

    fun applyHit(
        target: EntityRef,
        source: EntityRef?,
        damage: Int,
        style: HitStyle,
        onLand: (() -> Unit)? = null,
        mark: Hitmark = REGULAR,
    ) {
        if (target.isDying || (target is NpcRef && !target.isInWorld)) return // OPEN-C2
        val dealt = minOf(damage, target.hitpoints)
        target.hitpoints -= dealt
        if (dealt > 0) {
            target.hitsplat(source, dealt, mark.lit, mark.lit, mark.tint)
        } else {
            target.hitsplat(source, 0, HITMARK_BLOCK_LIT, HITMARK_BLOCK_LIT, HITMARK_BLOCK_TINT)
        }
        target.headbar(source, HEADBAR_HEALTH_30, target.hitpoints * HEADBAR_SEGMENTS / maxOf(1, target.maxHitpoints))
        if (source != null) {
            target[Attr.LAST_HIT_BY] = source
            target[Attr.LAST_HIT_TICK] = target.tick
        }
        onLand?.invoke()
        bus.publish(HitEvent(target, source, dealt, style))
        if (target.hitpoints <= 0) {
            die(target, source)
        } else if (source != null) {
            retaliate(target, source)
        }
    }

    /** C4 (players: auto retaliate) / C5 (NPCs). */
    private fun retaliate(target: EntityRef, source: EntityRef) {
        val now = target.tick
        when (target) {
            is PlayerRef -> {
                if (target.varp(Varps.OPTION_NODEF) != 0 || target.isInteracting || target.isMoving) return
                target.setAttackClock(maxOf(target.attackClock(), now + (target.attackSpeed() + 1) / 2))
                val op = attackOpOf(source) ?: return
                target.interact(source, op)
            }

            is NpcRef -> {
                if (target.target != null) return
                target.setAttackClock(maxOf(target.attackClock(), now + npcSpeed(target) / 2))
                NpcCombat.engage(target, source)
            }
        }
    }

    /** The op number of "Attack" on [target]. */
    fun attackOpOf(target: EntityRef): Int? =
        when (target) {
            is PlayerRef -> Combat.ATTACK_OP
            is NpcRef -> (1..NPC_OPS).firstOrNull { target.opName(it).equals("Attack", ignoreCase = true) }
        }

    private const val NPC_OPS = 5

    /** Attack speed: content override, else the Wiki table (npc-combat.md N1), else the default. */
    fun npcSpeed(npc: NpcRef): Int =
        npc[Attr.NPC_ATTACK_SPEED] ?: NpcCombatTable.of(npc.type)?.speed?.takeIf { it > 0 } ?: DEFAULT_SPEED

    // ---- death

    private fun die(target: EntityRef, killer: EntityRef?) {
        target[Attr.DYING] = true
        when (target) {
            is PlayerRef -> {
                target.stopInteraction()
                DeathRules.retribution(target)
                target.queueHit(DEATH_ANIM_DELAY) { target.anim(Seqs.HUMAN_DEATH) }
                target.queueHit(DEATH_RESPAWN_DELAY) { respawn(target, killer) }
            }

            is NpcRef -> {
                target.stopInteraction()
                Poison.clear(target)
                target.queueHit(NPC_DEATH_DELAY) {
                    bus.publish(DeathEvent(target, killer))
                    target.respawnAfter(NpcCombatTable.of(target.type)?.respawn?.takeIf { it > 0 } ?: NPC_RESPAWN_DELAY)
                }
            }
        }
    }

    private fun respawn(player: PlayerRef, killer: EntityRef?) {
        DeathRules.dropItems(player, killer)
        bus.publish(DeathEvent(player, killer))
        player.message("Oh dear, you are dead!")
        player.teleport(RESPAWN)
        for (stat in 0 until STAT_COUNT) player.setLevel(stat, player.baseLevel(stat))
        player.deactivateAllPrayers()
        Poison.clear(player)
        player.frozenUntil = 0
        player[Attr.FROZEN_BY] = null
        player[Attr.DYING] = null
        player.setVarp(Varps.SA_ATTACK, 0)
        player.setVarp(Varps.SA_ENERGY, Specials.FULL_ENERGY)
        player.anim(-1)
        Appearance.refresh(player)
    }

    private const val STAT_COUNT = 24

    // ---- shared helpers for attacks

    fun hitStyle(style: CombatStyle): HitStyle =
        when (style) {
            CombatStyle.MELEE -> HitStyle.MELEE
            CombatStyle.RANGED -> HitStyle.RANGED
            CombatStyle.MAGIC -> HitStyle.MAGIC
        }

    fun announceAttack(attacker: EntityRef, target: EntityRef, style: CombatStyle) {
        attacker.trace("attack ${style.name.lowercase()}")
        bus.publish(AttackEvent(attacker, target, hitStyle(style)))
    }

    /** Removes one piece of ammo from the quiver; false if there's none. */
    fun useAmmo(player: PlayerRef): Boolean {
        val ammo = player.worn(Wear.AMMO)
        if (ammo < 0) return false
        return player.transaction("ammo") { removeAt(Invs.WORN, Wear.AMMO, ammo, 1) }
    }
}

/**
 * NPC attacks (docs/mechanics/npc-combat.md): style, accuracy, max hit and speed from the Wiki table. A melee NPC
 * walks into reach; one with ranged or magic attacks from [DISTANCE_RANGE] tiles, and also closes in if it has a melee
 * style (it melees once in reach).
 */
internal object NpcCombat {
    /** OPEN-N5: NPC ranged/magic attack distance isn't in the cache or the infobox; named default. */
    const val DISTANCE_RANGE = 7

    /** Starts [npc]'s attack interaction on [victim]. */
    fun engage(npc: NpcRef, victim: EntityRef) {
        val def = NpcCombatTable.of(npc.type)
        if (def == null || (!def.hasRanged && !def.hasMagic)) {
            npc.interact(victim) { v -> attack(this, v, distance = false) }
            return
        }
        val melee = def.meleeTypes.isNotEmpty()
        npc.interactAtRange(
            victim,
            DISTANCE_RANGE,
            closeIn = melee,
            inReach = if (melee) ({ v -> attack(this, v, distance = false) }) else null,
        ) { v -> attack(this, v, distance = true) }
    }

    /** One attack if the clock allows; [distance] = not in melee reach (ranged/magic only). */
    fun attack(script: NpcScript, victim: EntityRef, distance: Boolean) {
        val npc = script.npc
        if (!npc.isInWorld || npc.isDying || victim.isDying) {
            npc.stopInteraction()
            return
        }
        npc.face(victim)
        val now = npc.tick
        if (npc.attackClock() > now) return
        npc.setAttackClock(now + CombatSystem.npcSpeed(npc))
        val def = NpcCombatTable.of(npc.type)
        val type = chooseType(npc, def, distance)
        val style = type.style
        val attackRoll =
            when (style) {
                CombatStyle.MELEE -> Formulas.npcDefenceRoll(npc.level(NpcStat.ATTACK), def?.meleeAttack ?: 0)
                CombatStyle.RANGED -> Formulas.npcDefenceRoll(npc.level(NpcStat.RANGED), def?.rangedAttack ?: 0)
                CombatStyle.MAGIC -> Formulas.npcDefenceRoll(npc.level(NpcStat.MAGIC), def?.magicAttack ?: 0)
            }
        val defenceRoll = Attacks.defenceRoll(victim, type)
        val max = def?.maxFor(style) ?: Formulas.maxHit(npc.level(NpcStat.STRENGTH) + EFFECTIVE_BASE + 1, 0)
        // P5: protection prayers block NPC attacks fully.
        val protected = victim is PlayerRef && victim.protects(style)
        val hit = !protected && Formulas.hits(attackRoll, defenceRoll, npc::random)
        val damage = if (hit) Formulas.damage(max, npc::random) else 0
        val delay =
            when (style) {
                CombatStyle.MELEE -> 0

                // OPEN-N8: NPC projectile hit delays use the player formulas (C1).
                CombatStyle.RANGED -> Formulas.bowDelay(npc.distanceTo(victim))

                CombatStyle.MAGIC -> Formulas.magicDelay(npc.distanceTo(victim))
            }
        CombatSystem.announceAttack(npc, victim, style)
        CombatSystem.queueHit(victim, npc, damage, CombatSystem.hitStyle(style), delay)
        if (def != null) Toxic.onNpcAttack(npc, def, victim)
    }

    /**
     * N2: melee (first listed melee type) when in reach and the NPC has one; at a distance, or without melee, ranged or
     * magic; with both, a uniform pick per attack (OPEN-N3). Unknown NPCs: crush.
     */
    private fun chooseType(npc: NpcRef, def: NpcCombatDef?, distance: Boolean): AttackType {
        if (def == null) return AttackType.CRUSH
        val melee = def.meleeTypes.firstOrNull()
        if (!distance && melee != null) return melee
        return when {
            def.hasRanged && def.hasMagic -> if (npc.random(2) == 0) AttackType.RANGED else AttackType.MAGIC
            def.hasRanged -> AttackType.RANGED
            def.hasMagic -> AttackType.MAGIC
            else -> melee ?: AttackType.CRUSH
        }
    }

    private const val EFFECTIVE_BASE = 8
}
