package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * Chinchompas (docs/mechanics/combat-multi.md CH1-CH4; OSRS Wiki "Chinchompa (weapon)"): each attack throws one
 * chinchompa from the weapon slot at the target. The fuse (attack style) and the distance scale the accuracy roll,
 * and in a multi-combat area the explosion also hits up to 8 more attackable targets within one tile of the target.
 * One accuracy roll against the primary target decides whether every target is hit; damage is rolled per target.
 */
internal object Chinchompas {
    fun attack(player: PlayerRef, target: EntityRef, stance: Stance, type: AttackType) {
        val thrown = player.worn(Wear.WEAPON)
        if (thrown < 0 || !player.transaction("chinchompa") { removeAt(Invs.WORN, Wear.WEAPON, thrown, 1) }) {
            player.message("You have no chinchompas left.")
            player.stopInteraction()
            return
        }
        player.setAttackClock(player.tick + player.attackSpeed())
        val distance = player.distanceTo(target)
        val attackRoll = Attacks.attackRoll(player, stance, type) * fuseAccuracy(stance, distance) / PERCENT
        val defenceRoll = Attacks.defenceRoll(target, type, NpcCombatDef.DEF_HEAVY)
        val delay = Formulas.thrownDelay(distance)
        // Gameval names: the `_pvn` throw is the player-versus-NPC variant.
        player.anim(if (target is PlayerRef) Seqs.HUMAN_CHINCHOMPA_ATTACK else Seqs.HUMAN_CHINCHOMPA_ATTACK_PVN)
        ProjType.CHINCHOMPA.launch(player, target, projectileOf(thrown), delay)
        // CH3: the primary target's roll decides the whole explosion.
        val hit = Formulas.hits(attackRoll, defenceRoll, player::random)
        val max = Attacks.maxHit(player, stance, type)
        for (victim in victims(player, target)) {
            CombatSystem.announceAttack(player, victim, CombatStyle.RANGED)
            var victimMax = max
            if (victim is PlayerRef && victim.protects(CombatStyle.RANGED)) victimMax = Formulas.protectedMaxHit(victimMax)
            val damage = if (hit) Formulas.damage(victimMax, player::random) else 0
            CombatSystem.queueHit(victim, player, damage, CombatSystem.hitStyle(CombatStyle.RANGED), delay)
            if (hit) Attacks.grantXp(player, stance, CombatStyle.RANGED, damage)
        }
    }

    /** CH2: the target, then (multi-combat only) up to 8 attackable entities within one tile that are in multi too. */
    private fun victims(player: PlayerRef, target: EntityRef): List<EntityRef> {
        val multiAt = player.worldAttr(SharedAttrs.MULTI_AREA)
        if (multiAt == null || !multiAt(target.coord)) return listOf(target)
        val out = arrayListOf(target)
        val around: List<EntityRef> = player.npcsWithin(target.coord, RADIUS) + player.playersAround(target.coord, RADIUS)
        for (other in around) {
            if (out.size > MAX_EXTRA) break
            if (other == target || other == player || !multiAt(other.coord)) continue
            if (CombatSystem.canFight(player, other, quiet = true)) out += other
        }
        return out
    }

    /**
     * CH1 (Wiki "Chinchompa (weapon)", Accuracy): short fuse (accurate) 100 / 75 / 50 % at 0-3 / 4-6 / 7+ tiles,
     * medium fuse (rapid) 75 / 100 / 75 %, long fuse (longrange) 50 / 75 / 100 %.
     */
    private fun fuseAccuracy(stance: Stance, distance: Int): Int {
        val band =
            when {
                distance <= SHORT_MAX -> 0
                distance <= MEDIUM_MAX -> 1
                else -> 2
            }
        val table =
            when (stance) {
                Stance.ACCURATE_RANGED -> SHORT_FUSE
                Stance.LONGRANGE -> LONG_FUSE
                else -> MEDIUM_FUSE
            }
        return table[band]
    }

    private fun projectileOf(obj: Int): Int =
        when (obj) {
            Objs.CHINCHOMPA_BIG_CAPTURED -> SpotAnims.BIG_CHINCHOMPA_GRENADE
            Objs.CHINCHOMPA_BLACK -> SpotAnims.BLACK_CHINCHOMPA_GRENADE
            else -> SpotAnims.CHINCHOMPA_GRENADE
        }

    private const val PERCENT = 100
    private const val SHORT_MAX = 3
    private const val MEDIUM_MAX = 6
    private val SHORT_FUSE = intArrayOf(100, 75, 50)
    private val MEDIUM_FUSE = intArrayOf(75, 100, 75)
    private val LONG_FUSE = intArrayOf(50, 75, 100)
    private const val RADIUS = 1

    /** Up to 9 targets in all (the primary + 8). */
    private const val MAX_EXTRA = 8
}
