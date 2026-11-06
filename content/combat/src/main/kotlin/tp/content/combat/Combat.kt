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
        onLogin {
            if (varp(Varps.SA_ENERGY) == 0 && this[Attr.ATTACK_CLOCK] == null) setVarp(Varps.SA_ENERGY, Specials.FULL_ENERGY)
            Specials.startRegen(this)
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

    /** OPEN-N2: NPC death length and respawn time aren't in the cache; dev defaults. */
    private const val NPC_DEATH_DELAY = 4
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

    fun applyHit(target: EntityRef, source: EntityRef?, damage: Int, style: HitStyle, onLand: (() -> Unit)? = null) {
        if (target.isDying || (target is NpcRef && !target.isInWorld)) return // OPEN-C2
        val dealt = minOf(damage, target.hitpoints)
        target.hitpoints -= dealt
        if (dealt > 0) {
            target.hitsplat(source, dealt, HITMARK_DAMAGE_LIT, HITMARK_DAMAGE_LIT, HITMARK_DAMAGE_TINT)
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
                target.interact(source) { victim -> NpcCombat.attack(this, victim) }
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

    fun npcSpeed(npc: NpcRef): Int = npc[Attr.NPC_ATTACK_SPEED] ?: DEFAULT_SPEED

    // ---- death

    private fun die(target: EntityRef, killer: EntityRef?) {
        target[Attr.DYING] = true
        when (target) {
            is PlayerRef -> {
                target.stopInteraction()
                target.queueHit(DEATH_ANIM_DELAY) { target.anim(Seqs.HUMAN_DEATH) }
                target.queueHit(DEATH_RESPAWN_DELAY) { respawn(target, killer) }
            }

            is NpcRef -> {
                target.stopInteraction()
                target.queueHit(NPC_DEATH_DELAY) {
                    bus.publish(DeathEvent(target, killer))
                    target.respawnAfter(NPC_RESPAWN_DELAY)
                }
            }
        }
    }

    private fun respawn(player: PlayerRef, killer: EntityRef?) {
        bus.publish(DeathEvent(player, killer))
        player.message("Oh dear, you are dead!")
        player.teleport(RESPAWN)
        for (stat in 0 until STAT_COUNT) player.setLevel(stat, player.baseLevel(stat))
        player.deactivateAllPrayers()
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
        bus.publish(AttackEvent(attacker, target, hitStyle(style)))
    }

    /** Removes one piece of ammo from the quiver; false if there's none. */
    fun useAmmo(player: PlayerRef): Boolean {
        val ammo = player.worn(Wear.AMMO)
        if (ammo < 0) return false
        return player.transaction("ammo") { removeAt(Invs.WORN, Wear.AMMO, ammo, 1) }
    }
}

/** NPC attacks (melee only for now). */
internal object NpcCombat {
    /** OPEN-N1: NPC attack style/bonuses/max hit aren't in the cache; crush, bonus 0, max from strength. */
    fun attack(script: NpcScript, victim: EntityRef) {
        val npc = script.npc
        if (!npc.isInWorld || npc.isDying || victim.isDying) {
            npc.stopInteraction()
            return
        }
        npc.face(victim)
        val now = npc.tick
        if (npc.attackClock() > now) return
        npc.setAttackClock(now + CombatSystem.npcSpeed(npc))
        val attackRoll = Formulas.npcDefenceRoll(npc.level(NpcStat.ATTACK), 0)
        val defenceRoll = Attacks.defenceRoll(victim, AttackType.CRUSH)
        val max = Formulas.maxHit(npc.level(NpcStat.STRENGTH) + EFFECTIVE_BASE + 1, 0)
        // P5: protection prayers block NPC attacks fully.
        val protected = victim is PlayerRef && victim.protects(CombatStyle.MELEE)
        val hit = !protected && Formulas.hits(attackRoll, defenceRoll, npc::random)
        val damage = if (hit) Formulas.damage(max, npc::random) else 0
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        CombatSystem.queueHit(victim, npc, damage, HitStyle.MELEE, delay = 0)
    }

    private const val EFFECTIVE_BASE = 8
}
