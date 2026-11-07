package tp.content.combat

import tp.plugin.AttackCheck
import tp.plugin.AttackEvent
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.EntityRef
import tp.plugin.HitEvent
import tp.plugin.HitStyle
import tp.plugin.LethalHitEvent
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
        // SE10: finishing items used on their monster.
        for ((item, npc, finish) in SlayerGear.finishUses()) {
            onUseOnNpc(item, npc) { target -> SlayerGear.use(player, target, finish, item) }
        }
        // Parse the Wiki NPC table at boot, not on the first NPC hit (no tick-time allocation spike).
        onWorldStart { check(NpcCombatTable.size > 0) { "npc-combat.tsv is empty" } }
        onLogin {
            if (varp(Varps.SA_ENERGY) == 0 && this[Attr.ATTACK_CLOCK] == null) setVarp(Varps.SA_ENERGY, Specials.FULL_ENERGY)
            Specials.startRegen(this)
            Poison.onLogin(this)
        }
        // Content damage (a failed pickpocket, a rooftop fall): a regular hit that can kill. No source, so it does not
        // trigger auto-retaliate against the NPC that dealt it.
        events.on<tp.plugin.DamageRequest> { req ->
            if (!req.handled) CombatSystem.applyHit(req.target, null, req.damage, req.style)
            req.handled = true
        }
        events.on<tp.plugin.SpellAccuracyRoll> { roll ->
            val attack = Attacks.attackRoll(roll.caster, Stance.SPELL, AttackType.MAGIC, target = roll.target)
            roll.hit = Formulas.hits(attack, Attacks.defenceRoll(roll.target, AttackType.MAGIC), roll.caster::random)
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
            onButton(component) {
                if (weaponCategory().slots.getOrNull(mode) == null) return@onButton
                Autocast.clear(this)
                setVarp(Varps.COM_MODE, mode)
            }
        }
        Autocast.register(this)
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

    /** Wiki "Ward of Arceuus": demons deal 10% less (the reduction rounds down). */
    private const val WARD_DIVISOR = 10

    /** Respawn point: Lumbridge (RS Mod `PlayerDeath` default). */
    val RESPAWN = CoordGrid(3222, 3218, 0)

    // ---- interaction entry points

    /** Op script (in reach): melee attacks here; ranged/magic too (reach implies range). */
    fun attackFromOp(script: PlayerScript, target: EntityRef) {
        attackLoop(script, target)
    }

    /** Ap script: ranged sets its approach range (I2); melee turns the ap into an op (aprange -1). */
    fun attackFromAp(script: PlayerScript, target: EntityRef) {
        val player = script.player
        if (Autocast.spellOf(player) != null) {
            if (script.currentApRange != SpellCasting.SPELL_RANGE) {
                script.apRange(SpellCasting.SPELL_RANGE)
                return
            }
            attackLoop(script, target)
            return
        }
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
        val spell = Autocast.spellOf(player)
        val style = if (spell != null) HitStyle.MAGIC else hitStyle(player.stanceAndType().second.style)
        if (!canFight(player, target, style)) return
        if (!DuelChecks.weaponAllowed(player)) return
        script.keepInteraction()
        player.face(target)
        if (player.attackClock() > player.tick) return
        if (spell == null) return Attacks.playerAttack(player, target)
        // CM14: an autocast that can't be cast stops the attack (the reason was already said).
        if (!SpellCasting.cast(player, target, spell, manual = false, defensive = Autocast.defensive(player))) player.stopInteraction()
    }

    /** Whether [attacker] may attack [target] now (AttackCheck event: PvP rules subscribe). */
    fun canFight(attacker: EntityRef, target: EntityRef, style: HitStyle? = null, quiet: Boolean = false): Boolean {
        if (target.isDying || attacker.isDying) return false
        if (target is NpcRef && !target.isInWorld) return false
        val check = AttackCheck(attacker, target, style)
        attacker.events.publish(check)
        val denial = check.denial
        if (denial != null || !check.allowed) {
            if (attacker is PlayerRef && !quiet) attacker.message(denial ?: "You can't attack that.")
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
        val boss = (target as? NpcRef)?.let { Bosses.of(it.type) }
        var adjusted = if (boss != null && target is NpcRef) boss.incoming(target, source, damage, style) else damage
        adjusted = SpecialEffects.incoming(target, adjusted, style)
        // CM17: Ward of Arceuus takes ⌊damage / 10⌋ off demons' hits.
        if (source is NpcRef && SpellEffects.warded(target) && NpcCombatTable.of(source.type)?.attributes?.contains("demon") == true) {
            adjusted -= adjusted / WARD_DIVISOR
        }
        // SE9: leafy monsters, harpie bug swarms and warped creatures only take damage from their required gear.
        adjusted = SlayerGear.adjust(source, target, style, adjusted)
        val dealt = minOf(adjusted, target.hitpoints)
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
        target.events.publish(HitEvent(target, source, dealt, style))
        if (target is NpcRef) Bosses.recordDamage(target, source, dealt)
        if (target is NpcRef && boss != null) {
            if (target.hitpoints <= 0 && boss.onLethal(target)) return
            if (target.hitpoints > 0) boss.afterHit(target, source)
        }
        lethalCheck(target, source)
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
                // Retreat (Wiki "Retreat"): "initiated by attacking an NPC from outside the aggression range".
                if (NpcCombat.outsideAggressionRange(target, source)) return target.retreat(source)
                target.retreat(null)
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

    /** N12: content may keep an NPC alive at 0 hitpoints (quest NPCs that need a special killing blow). */
    private fun lethalCheck(target: EntityRef, source: EntityRef?) {
        if (target !is NpcRef) return
        // SE10: finishing-item monsters don't die to hits (an auto-finish unlock kills them at the threshold instead).
        SlayerGear.finishOnHit(source as? PlayerRef, target)
        if (target.hitpoints > 0) return
        val lethal = LethalHitEvent(target, source)
        target.events.publish(lethal)
        if (lethal.surviveWith > 0) target.hitpoints = minOf(lethal.surviveWith, target.maxHitpoints)
    }

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
                Bosses.of(target.type)?.died(target, killer)
                CombatVisuals.npcDeath(target)
                target.queueHit(NPC_DEATH_DELAY) {
                    // Drops go to the player who dealt the most damage (Wiki "Drops").
                    target.events.publish(DeathEvent(target, killer, Bosses.topDamager(target) ?: killer as? PlayerRef))
                    // Summoned minions and one-off spawns (maledictus) don't come back (bosses.md B5).
                    if (target[Bosses.MINION] == true) {
                        target.despawn()
                    } else {
                        target.respawnAfter(NpcCombatTable.of(target.type)?.respawn?.takeIf { it > 0 } ?: NPC_RESPAWN_DELAY)
                    }
                }
            }
        }
    }

    private fun respawn(player: PlayerRef, killer: EntityRef?) {
        // A safe death (Duel Arena) keeps every item and respawns where the minigame says.
        val safe = player[tp.plugin.SharedAttrs.SAFE_DEATH_AT]
        if (safe == null) DeathRules.dropItems(player, killer)
        player.events.publish(DeathEvent(player, killer))
        player.message("Oh dear, you are dead!")
        player.teleport(safe ?: RESPAWN)
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
        // AG2: both sides are "in combat" for hunting purposes.
        for (e in listOf(attacker, target)) if (e is PlayerRef) e[tp.plugin.SharedAttrs.LAST_COMBAT_TICK] = e.tick
        if (attacker is PlayerRef && target is PlayerRef) {
            attacker[tp.plugin.SharedAttrs.LAST_PVP_TICK] = attacker.tick
            target[tp.plugin.SharedAttrs.LAST_PVP_TICK] = target.tick
        }
        attacker.events.publish(AttackEvent(attacker, target, hitStyle(style)))
    }

    /**
     * CD-A1: spends the ammunition of one ranged attack at [target]: arrows / bolts from the ammo slot, thrown weapons
     * from the weapon slot (not the blowpipe, which uses charges). False when there is none.
     */
    fun useAmmo(player: PlayerRef, target: EntityRef, category: WeaponCategory): Boolean {
        val weapon = player.objName(player.worn(Wear.WEAPON)).lowercase()
        if (NO_AMMO_BOWS.any { it in weapon }) return true
        val slot =
            when {
                category === Weapons.BOW || category === Weapons.CROSSBOW -> Wear.AMMO
                category === Weapons.THROWN && "blowpipe" !in weapon -> Wear.WEAPON
                else -> return true
            }
        val ammo = player.worn(slot)
        if (ammo < 0) return false
        return AmmoRecovery.spend(player, slot, ammo, target)
    }

    /** Bows that make their own arrows or use charges (Wiki: crystal bow, bow of faerdhinen, Craw's / webweaver bow). */
    private val NO_AMMO_BOWS = listOf("crystal bow", "faerdhinen", "craw's bow", "webweaver bow")
}

/**
 * NPC attacks (docs/mechanics/npc-combat.md): style, accuracy, max hit and speed from the Wiki table. A melee NPC
 * walks into reach; one with ranged or magic attacks from [DISTANCE_RANGE] tiles, and also closes in if it has a melee
 * style (it melees once in reach).
 */
internal object NpcCombat {
    /** OPEN-N5: NPC ranged/magic attack distance isn't in the cache or the infobox; named default. */
    const val DISTANCE_RANGE = 7

    /**
     * AG5: the aggression range is the max range around the spawn point plus the attack range (melee 1, else
     * [DISTANCE_RANGE]), accounting for the NPC's size.
     */
    fun outsideAggressionRange(npc: NpcRef, victim: EntityRef): Boolean {
        val def = NpcCombatTable.of(npc.type)
        val attackRange = if (def != null && (def.hasRanged || def.hasMagic)) DISTANCE_RANGE else 1
        val reach = npc.maxRange + attackRange
        val s = npc.spawn
        val v = victim.coord
        val dx = maxOf(0, v.x - (s.x + npc.size - 1 + reach), (s.x - reach) - v.x)
        val dz = maxOf(0, v.z - (s.z + npc.size - 1 + reach), (s.z - reach) - v.z)
        return dx > 0 || dz > 0 || v.level != s.level
    }

    /** Starts [npc]'s attack interaction on [victim]. */
    fun engage(npc: NpcRef, victim: EntityRef) {
        if (victim is PlayerRef) victim[tp.plugin.SharedAttrs.ENGAGED_BY] = npc
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
        if (bossAttack(npc, def, victim, distance)) return
        // DF4-DF7: ordinary dragons' breath instead of this attack.
        if (def != null && Dragonfire.breathes(npc, def, distance)) return Dragonfire.breathe(npc, def, victim)
        val type = chooseType(npc, def, distance)
        val style = type.style
        val attackRoll =
            when (style) {
                CombatStyle.MELEE -> Formulas.npcDefenceRoll(npc.level(NpcStat.ATTACK), def?.meleeAttack ?: 0)
                CombatStyle.RANGED -> Formulas.npcDefenceRoll(npc.level(NpcStat.RANGED), def?.rangedAttack ?: 0)
                CombatStyle.MAGIC -> Formulas.npcDefenceRoll(npc.level(NpcStat.MAGIC), def?.magicAttack ?: 0)
            }
        val defenceRoll = Attacks.defenceRoll(victim, type)
        val damage = npcDamage(npc, def, style, victim, attackRoll, defenceRoll)
        val delay =
            when (style) {
                CombatStyle.MELEE -> 0

                // OPEN-N8: NPC projectile hit delays use the player formulas (C1).
                CombatStyle.RANGED -> Formulas.bowDelay(npc.distanceTo(victim))

                CombatStyle.MAGIC -> Formulas.magicDelay(npc.distanceTo(victim))
            }
        CombatSystem.announceAttack(npc, victim, style)
        CombatVisuals.npcAttack(npc, style)
        CombatVisuals.block(victim, delay)
        CombatSystem.queueHit(victim, npc, damage, CombatSystem.hitStyle(style), delay)
        if (def != null) Toxic.onNpcAttack(npc, def, victim)
    }

    /**
     * The NPC's damage: SE2-SE8 (missing Slayer protective equipment: the attack always lands, some prayers still block
     * it), else the accuracy roll (P5: protection prayers block NPC attacks fully) and the max hit (SE6-SE7 overrides).
     */
    private fun npcDamage(npc: NpcRef, def: NpcCombatDef?, style: CombatStyle, victim: EntityRef, attackRoll: Int, defenceRoll: Int): Int {
        val protected = victim is PlayerRef && victim.protects(style)
        val unprotected = SlayerGear.unprotected(npc, victim)
        if (unprotected != null) {
            if (victim is PlayerRef) unprotected.effect(victim)
            return if (protected && unprotected.prayerBlocks) 0 else unprotected.damage
        }
        val max =
            SlayerGear.unprotectedMax(npc, victim) ?: def?.maxFor(style)
                ?: Formulas.maxHit(npc.level(NpcStat.STRENGTH) + EFFECTIVE_BASE + 1, 0)
        val hit = !protected && Formulas.hits(attackRoll, defenceRoll, npc::random)
        return if (hit) Formulas.damage(max, npc::random) else 0
    }

    /** Hand-written fights (bosses.md B1): the script picks the attack; poisonous hits still poison (N7). */
    private fun bossAttack(npc: NpcRef, def: NpcCombatDef?, victim: EntityRef, distance: Boolean): Boolean {
        val boss = Bosses.of(npc.type) ?: return false
        boss.attack(npc, victim, distance)
        if (def != null) Toxic.onNpcAttack(npc, def, victim)
        return true
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
