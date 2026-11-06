package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/** The worn slots Madness empties, weapon first (bosses.md CE3: "The weapon of the player is always prioritised"). */
private val MADNESS_ORDER =
    intArrayOf(Wear.WEAPON, Wear.SHIELD, Wear.HEAD, Wear.BODY, Wear.LEGS, Wear.HANDS, Wear.FEET, Wear.CAPE, Wear.NECK, Wear.RING)

/** Unequips up to [max] worn items, weapon first, while the backpack has room; returns how many moved. */
internal fun disarm(player: PlayerRef, max: Int): Int {
    var moved = 0
    for (slot in MADNESS_ORDER) {
        if (moved >= max || player.inv(Invs.INV).freeSlots == 0) break
        if (player.worn(slot) >= 0 && unequip(player, slot)) moved++
    }
    return moved
}

/**
 * The Chaos Elemental (bosses.md CE1–CE4): Discord (melee, ranged or magic damage, mostly magic), Confusion
 * (teleports the target nearby) and Madness (unequips up to four items, weapon first; never used on a full backpack).
 */
internal object ChaosElemental : Boss(Npcs.CHAOSELEMENTAL) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        npc.anim(Seqs.CHAOSELEMENTAL_ATTACK)
        val roll = npc.random(SPECIAL_ROLL)
        val player = victim as? PlayerRef
        when {
            player != null && roll == CONFUSION -> confusion(npc, player)
            player != null && roll == MADNESS && player.inv(Invs.INV).freeSlots > 0 -> madness(npc, player)
            else -> discord(npc, victim)
        }
    }

    /** CE1: "it is impossible to distinguish what damage is dealt"; OPEN-CE1: magic 1/2, ranged 1/4, melee 1/4. */
    private fun discord(npc: NpcRef, victim: EntityRef) {
        val type =
            when (npc.random(4)) {
                0 -> AttackType.CRUSH
                1 -> AttackType.RANGED
                else -> AttackType.MAGIC
            }
        val delay = maxOf(1, BossCombat.delay(npc, victim, CombatStyle.MAGIC))
        npc.spotAnim(SpotAnims.CHAOSELEMENTAL_SPOTANIM_DISCORD_CASTING)
        BossCombat.projectile(npc, victim, SpotAnims.CHAOSELEMENTAL_SPOTANIM_DISCORD_TRAVEL, delay)
        CombatSystem.announceAttack(npc, victim, type.style)
        BossCombat.strike(npc, victim, type, NpcCombatTable.of(npc.type)?.max ?: 0, delay) {
            victim.spotAnim(SpotAnims.CHAOSELEMENTAL_SPOTANIM_DISCORD_IMPACT)
        }
    }

    /** CE2: "teleports players a short space or several spaces away from it" (OPEN-CE2: up to 6 tiles). */
    private fun confusion(npc: NpcRef, victim: PlayerRef) {
        val delay = maxOf(1, BossCombat.delay(npc, victim, CombatStyle.MAGIC))
        npc.spotAnim(SpotAnims.CHAOSELEMENTAL_SPOTANIM_CONFUSION_CASTING)
        BossCombat.projectile(npc, victim, SpotAnims.CHAOSELEMENTAL_SPOTANIM_CONFUSION_TRAVEL, delay)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MAGIC)
        victim.queueHit(delay) {
            if (victim.isDying) return@queueHit
            victim.spotAnim(SpotAnims.CHAOSELEMENTAL_SPOTANIM_CONFUSION_IMPACT)
            victim.teleport(BossCombat.randomTile(npc, victim.coord, CONFUSION_RANGE))
        }
    }

    /** CE3: "unequips up to four items ... The weapon of the player is always prioritised". */
    private fun madness(npc: NpcRef, victim: PlayerRef) {
        val delay = maxOf(1, BossCombat.delay(npc, victim, CombatStyle.MAGIC))
        npc.spotAnim(SpotAnims.CHAOSELEMENTAL_SPOTANIM_MADNESS_CASTING)
        BossCombat.projectile(npc, victim, SpotAnims.CHAOSELEMENTAL_SPOTANIM_MADNESS_TRAVEL, delay)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MAGIC)
        victim.queueHit(delay) {
            if (victim.isDying) return@queueHit
            victim.spotAnim(SpotAnims.CHAOSELEMENTAL_SPOTANIM_MADNESS_IMPACT)
            disarm(victim, MADNESS_ITEMS)
        }
    }

    /** OPEN-CE3: special frequencies aren't published; each special 1 in 6. */
    private const val SPECIAL_ROLL = 6
    private const val CONFUSION = 0
    private const val MADNESS = 1
    private const val MADNESS_ITEMS = 4
    private const val CONFUSION_RANGE = 6
}

/**
 * Scorpia (bosses.md SC1–SC4): stab attacks that drain 2 prayer when Protect from Melee is on, poison 20, and two
 * guardians on the first attack against her after she falls below half health.
 */
internal object Scorpia : Boss(Npcs.SCORPIA) {
    private val BELOW_HALF = AttrKey<Boolean>("scorpia_below_half")
    private val SUMMONED = AttrKey<Boolean>("scorpia_summoned")

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        BossCombat.strike(npc, victim, AttackType.STAB, NpcCombatTable.of(npc.type)?.max ?: 0, 0)
        // SC1: "whether successful or not, drain the player's prayer points by 2 if they have Protect from Melee".
        if (victim is PlayerRef && victim.protects(CombatStyle.MELEE)) BossCombat.drainPrayer(victim, PRAYER_DRAIN)
    }

    /** SC2: "Once Scorpia falls below 50% health, the next attack against her, whether successful or not". */
    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        if (npc[SUMMONED] == true) return
        if (npc[BELOW_HALF] == true) {
            npc[SUMMONED] = true
            repeat(GUARDIANS) {
                ScorpiaGuardian.start(Bosses.summon(npc, Npcs.SCORPIA_GUARDIAN, BossCombat.randomTile(npc, npc.coord, SPREAD), 0), npc)
            }
        } else if (npc.hitpoints * 2 < npc.maxHitpoints) {
            npc[BELOW_HALF] = true
        }
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
    }

    private const val PRAYER_DRAIN = 2
    private const val GUARDIANS = 2
    private const val SPREAD = 2
}

/**
 * Scorpia's guardians (SC3): heal her 8 every 3 ticks while within 3 tiles; one that hasn't healed for 25 ticks
 * despawns. They don't attack.
 */
internal object ScorpiaGuardian : Boss(Npcs.SCORPIA_GUARDIAN) {
    private val LAST_HEAL = AttrKey<Long>("guardian_last_heal")

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        npc.stopInteraction()
    }

    fun start(guardian: NpcRef, scorpia: NpcRef) {
        guardian[LAST_HEAL] = guardian.tick
        loop(guardian, scorpia)
    }

    private fun loop(guardian: NpcRef, scorpia: NpcRef) {
        guardian.queueHit(HEAL_INTERVAL) {
            if (!guardian.isInWorld || guardian.isDying) return@queueHit
            val now = guardian.tick
            if (!scorpia.isInWorld || scorpia.isDying || now - (guardian[LAST_HEAL] ?: now) >= IDLE_TICKS) {
                guardian.despawn()
                return@queueHit
            }
            if (guardian.distanceTo(scorpia) <= HEAL_RANGE) {
                scorpia.hitpoints = minOf(scorpia.maxHitpoints, scorpia.hitpoints + HEAL)
                guardian[LAST_HEAL] = now
            } else {
                guardian.walkTo(scorpia.coord)
            }
            loop(guardian, scorpia)
        }
    }

    private const val HEAL = 8
    private const val HEAL_INTERVAL = 3
    private const val HEAL_RANGE = 3
    private const val IDLE_TICKS = 25L
}

/**
 * The Chaos Fanatic (bosses.md CF1–CF3): a red magic attack; "a 10/15 chance ... standard red attack, 4/15 ... an
 * explosion, and 1/15 ... the disarming attack"; each explosion is "followed by a 12-attack cooldown".
 */
internal object ChaosFanatic : Boss(Npcs.CHAOS_FANATIC) {
    private val COOLDOWN = AttrKey<Int>("fanatic_cooldown")

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        npc.anim(Seqs.HUMAN_CASTWAVE)
        val cooldown = npc[COOLDOWN] ?: 0
        if (cooldown > 0) {
            npc[COOLDOWN] = cooldown - 1
            return red(npc, victim)
        }
        val roll = npc.random(FIFTEENTHS)
        when {
            roll < RED_CHANCE -> red(npc, victim)
            roll < RED_CHANCE + EXPLOSION_CHANCE -> explosion(npc, victim)
            victim is PlayerRef -> disarmAttack(npc, victim)
            else -> red(npc, victim)
        }
    }

    /** CF1: "a basic red-coloured Magic projectile with a max hit of 31, from which Protect from Magic negates all damage". */
    private fun red(npc: NpcRef, victim: EntityRef) {
        val delay = BossCombat.delay(npc, victim, CombatStyle.MAGIC)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MAGIC)
        BossCombat.strike(npc, victim, AttackType.MAGIC, RED_MAX, delay)
    }

    /**
     * CF2: "three slow-moving green projectiles toward the player which explode on impact with the ground. One of them
     * always targets the tile the player is standing on when it's launched".
     */
    private fun explosion(npc: NpcRef, victim: EntityRef) {
        npc[COOLDOWN] = EXPLOSION_COOLDOWN
        CombatSystem.announceAttack(npc, victim, CombatStyle.MAGIC)
        val tiles = listOf(victim.coord, BossCombat.randomTile(npc, victim.coord, 1), BossCombat.randomTile(npc, victim.coord, 2))
        npc.queueHit(EXPLOSION_DELAY) {
            for (t in tiles) {
                npc.spotAnimAt(t, SpotAnims.DEVIOUS_EXPLOSION)
                for (p in BossCombat.playersOn(
                    npc,
                    t,
                )) {
                    CombatSystem.applyHit(p, npc, BossCombat.roll(npc, 0, SPECIAL_MAX), HitStyle.TYPELESS)
                }
            }
        }
    }

    /** CF3: "an unavoidable green projectile that disarms the player"; a full backpack negates it. */
    private fun disarmAttack(npc: NpcRef, victim: PlayerRef) {
        val delay = BossCombat.delay(npc, victim, CombatStyle.MAGIC)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MAGIC)
        victim.queueHit(delay) {
            if (!victim.isDying && victim.worn(Wear.WEAPON) >= 0) unequip(victim, Wear.WEAPON)
        }
    }

    private const val FIFTEENTHS = 15
    private const val RED_CHANCE = 10
    private const val EXPLOSION_CHANCE = 4
    private const val EXPLOSION_COOLDOWN = 12
    private const val RED_MAX = 31

    /** OPEN-CF1: explosion damage isn't published; the Wiki infobox "special" max. */
    private const val SPECIAL_MAX = 31

    /** OPEN-CF2: "slow-moving"; flight time not published. */
    private const val EXPLOSION_DELAY = 4
}

/**
 * The Crazy archaeologist (bosses.md CR1–CR2): ranged, melee only when the player is next to him; "Rain of knowledge!"
 * throws three books that explode in a 3×3 area, one splitting into two more that explode nearby after a delay.
 */
internal object CrazyArchaeologist : Boss(Npcs.CRAZY_ARCHAEOLOGIST) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val max = NpcCombatTable.of(npc.type)?.max ?: 0
        if (npc.random(SPECIAL_CHANCE) == 0) return rain(npc, victim, max)
        if (!distance) {
            npc.anim(Seqs.HUMAN_UNARMEDPUNCH)
            CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
            BossCombat.strike(npc, victim, AttackType.CRUSH, max, 0)
            return
        }
        npc.anim(Seqs.HUMAN_THROW)
        val delay = BossCombat.delay(npc, victim, CombatStyle.RANGED)
        CombatSystem.announceAttack(npc, victim, CombatStyle.RANGED)
        BossCombat.projectile(npc, victim, SpotAnims.CRAZY_ARCHAEOLOGIST_BOOK, delay)
        BossCombat.strike(npc, victim, AttackType.RANGED, max, delay)
    }

    private fun rain(npc: NpcRef, victim: EntityRef, max: Int) {
        npc.say("Rain of knowledge!")
        npc.anim(Seqs.HUMAN_THROW)
        CombatSystem.announceAttack(npc, victim, CombatStyle.RANGED)
        val c = victim.coord
        val books = listOf(c, CoordGrid(c.x - 2, c.z + 1, c.level), CoordGrid(c.x + 2, c.z + 1, c.level))
        npc.queueHit(BOOK_DELAY) {
            books.forEachIndexed { i, b ->
                explode(npc, b, max)
                if (i == 0) {
                    val split = listOf(BossCombat.randomTile(npc, b, 2), BossCombat.randomTile(npc, b, 2))
                    npc.queueHit(SPLIT_DELAY) { for (s in split) explode(npc, s, max) }
                }
            }
        }
    }

    private fun explode(npc: NpcRef, tile: CoordGrid, max: Int) {
        if (!npc.isInWorld) return
        npc.spotAnimAt(tile, SpotAnims.CRAZY_ARCHAEOLOGIST_BOOK_SPECIAL)
        for (p in BossCombat.playersOn(npc, tile, radius = 1)) CombatSystem.applyHit(p, npc, BossCombat.roll(npc, 0, max), HitStyle.RANGED)
    }

    /** OPEN-CR1: "Periodically"; 1 in 5. */
    private const val SPECIAL_CHANCE = 5
    private const val BOOK_DELAY = 3
    private const val SPLIT_DELAY = 2
}
