package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits

/**
 * Dragonfire protection (bosses.md DF1–DF3): anti-dragon shields, Protect from Magic and the antifire potions. The
 * potion timers live in the client's varbits `antifire_potion` (30-tick units) and `super_antifire_potion` (20-tick
 * units), which the client also shows.
 */
internal object Dragonfire {
    /** Shields that protect from dragonfire (Wiki "Dragonfire#Protection"). */
    private val SHIELDS =
        setOf(
            Objs.ANTIDRAGONBREATHSHIELD,
            Objs.NZONE_ANTIDRAGONSHIELD,
            Objs.DRAGONFIRE_SHIELD,
            Objs.DRAGONFIRE_SHIELD_UNCHARGED,
            Objs.DRAGONFIRE_WARD,
            Objs.DRAGONFIRE_WARD_UNCHARGED,
            Objs.WYVERN_SHIELD,
            Objs.WYVERN_SHIELD_UNCHARGED,
        )

    private val ANTIFIRE = TimerId("antifire")
    private val SUPER_ANTIFIRE = TimerId("super_antifire")
    private const val ANTIFIRE_UNIT = 30
    private const val SUPER_UNIT = 20

    fun hasShield(p: PlayerRef): Boolean = p.worn(Wear.SHIELD) in SHIELDS

    /** 0 = none, 1 = antifire, 2 = super antifire. */
    fun antifire(p: PlayerRef): Int =
        when {
            p.varbit(Varbits.SUPER_ANTIFIRE_POTION) > 0 -> 2
            p.varbit(Varbits.ANTIFIRE_POTION) > 0 -> 1
            else -> 0
        }

    /** A dose: the timer is set to [units] (never shortened). */
    fun drink(p: PlayerRef, superAntifire: Boolean, units: Int) {
        val varbit = if (superAntifire) Varbits.SUPER_ANTIFIRE_POTION else Varbits.ANTIFIRE_POTION
        p.setVarbit(varbit, maxOf(p.varbit(varbit), units))
        start(p, superAntifire)
    }

    fun onLogin(p: PlayerRef) {
        if (p.varbit(Varbits.ANTIFIRE_POTION) > 0) start(p, superAntifire = false)
        if (p.varbit(Varbits.SUPER_ANTIFIRE_POTION) > 0) start(p, superAntifire = true)
    }

    private fun start(p: PlayerRef, superAntifire: Boolean) {
        val varbit = if (superAntifire) Varbits.SUPER_ANTIFIRE_POTION else Varbits.ANTIFIRE_POTION
        val id = if (superAntifire) SUPER_ANTIFIRE else ANTIFIRE
        if (p.hasTimer(id)) return
        p.timer(id, if (superAntifire) SUPER_UNIT else ANTIFIRE_UNIT, soft = true) {
            val left = varbit(varbit) - 1
            setVarbit(varbit, maxOf(0, left))
            if (left <= 0) {
                stopTimer(id)
                message("Your resistance to dragonfire has run out.")
            }
        }
    }

    /**
     * DF2, the Wiki "Dragonfire" table for the King Black Dragon: [special] = the toxic / shocking / icy breaths
     * (antifire potions don't reduce them; a shield or the prayer does).
     */
    @Suppress("MagicNumber") // the Wiki table's max hits
    fun kbdMax(p: PlayerRef, special: Boolean): Int {
        val shield = hasShield(p)
        val prayer = p.protects(CombatStyle.MAGIC)
        if (special) {
            return when {
                shield -> 10
                prayer -> 15
                else -> 50
            }
        }
        val potion = antifire(p)
        return when {
            potion == 2 -> 0
            shield && potion == 1 -> 0
            shield -> 15
            prayer && potion == 1 -> 5
            prayer -> 20
            potion == 1 -> 50
            else -> 65
        }
    }

    /** Whether any protection is up (the special breath effect chance drops from 1/2 to 1/8, DF3). */
    fun protectedFrom(p: PlayerRef): Boolean = hasShield(p) || p.protects(CombatStyle.MAGIC) || antifire(p) > 0
}

/**
 * The King Black Dragon (bosses.md KB1–KB3): "if it's in melee range, it's got a 1/3 chance of doing melee, 1/3
 * chance of doing fire and 1/3 of doing one of the ice/toxic/shocking attacks"; out of range fire or a special, half
 * each. A special that hits has "up to 1/2" chance of its effect, "1/8 for a well-protected player".
 */
internal object KingBlackDragon : Boss(Npcs.KING_DRAGON) {
    private enum class Breath(
        val gfx: Int,
    ) {
        FIRE(
            SpotAnims.DRAGON_RANGED_FIRE_ATTACK,
        ),
        TOXIC(
            SpotAnims.DRAGON_RANGED_TOXIC_ATTACK,
        ),
        SHOCK(SpotAnims.DRAGON_RANGED_LIGHTNING_ATTACK),
        ICE(SpotAnims.DRAGON_RANGED_ICE_ATTACK),
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val roll = if (distance) 1 + npc.random(2) else npc.random(3)
        when (roll) {
            0 -> {
                npc.anim(Seqs.DRAGON_HEAD_ATTACK)
                CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
                BossCombat.strike(npc, victim, AttackType.STAB, MELEE_MAX, 0)
            }

            1 -> {
                breathe(npc, victim, Breath.FIRE)
            }

            else -> {
                breathe(npc, victim, listOf(Breath.TOXIC, Breath.SHOCK, Breath.ICE)[npc.random(SPECIALS)])
            }
        }
    }

    private fun breathe(npc: NpcRef, victim: EntityRef, breath: Breath) {
        npc.anim(Seqs.DRAGON_FIREBREATH_ALL_ATTACK)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MAGIC)
        val delay = BossCombat.delay(npc, victim, CombatStyle.MAGIC)
        BossCombat.projectile(npc, victim, breath.gfx, delay)
        val special = breath != Breath.FIRE
        // OPEN-KB1: dragonfire has no accuracy roll here; damage is uniform up to the protected max.
        val max =
            if (victim is PlayerRef) {
                Dragonfire.kbdMax(victim, special)
            } else if (special) {
                SPECIAL_MAX
            } else {
                FIRE_MAX
            }
        val damage = BossCombat.roll(npc, 0, max)
        CombatSystem.queueHit(victim, npc, damage, HitStyle.MAGIC, delay) {
            if (!special || damage <= 0 || victim !is PlayerRef) return@queueHit
            val chance = if (Dragonfire.protectedFrom(victim)) PROTECTED_CHANCE else UNPROTECTED_CHANCE
            if (npc.random(chance) != 0) return@queueHit
            when (breath) {
                // KB2: "Toxic breath inflicts poison to the player, starting at 8 damage."
                Breath.TOXIC -> Poison.poison(victim, TOXIC_POISON)

                // "Shocking breath drains two levels off of all the player's stats." (OPEN-KB2: Hitpoints excluded)
                Breath.SHOCK -> for (s in 0 until STAT_COUNT) {
                    if (s !=
                        Stat.HITPOINTS
                    ) {
                        victim.setLevel(s, maxOf(0, victim.level(s) - SHOCK_DRAIN))
                    }
                }

                // "Icy breath freezes the player for 10 ticks (6 seconds)."
                Breath.ICE -> Freezes.freeze(victim, npc, ICE_TICKS)

                Breath.FIRE -> Unit
            }
        }
    }

    private const val MELEE_MAX = 25
    private const val FIRE_MAX = 65
    private const val SPECIAL_MAX = 50
    private const val SPECIALS = 3
    private const val UNPROTECTED_CHANCE = 2
    private const val PROTECTED_CHANCE = 8
    private const val TOXIC_POISON = 8
    private const val SHOCK_DRAIN = 2
    private const val ICE_TICKS = 10
    private const val STAT_COUNT = 24
}
