package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * A hand-written NPC fight (docs/mechanics/bosses.md): the generic NPC attack (npc-combat.md) hands the attack to the
 * script, and hits on the NPC go through [incoming] / [afterHit] / [onLethal]. Minions (hellhounds, spiderlings,
 * guardians) are scripts too.
 */
internal abstract class Boss(vararg ids: Int) {
    val ids: IntArray = ids

    /** Runs when an NPC of one of [ids] spawns or respawns (its attrs and queue start empty). */
    open fun spawned(npc: NpcRef) {}

    /** One attack on [victim]; the attack clock is already set to the next attack. [distance]: not in melee reach. */
    abstract fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean)

    /** Damage the NPC takes from a landing hit (immunity, resistance). */
    open fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int = damage

    /** After a hit changed the NPC's hitpoints and it is still alive (health thresholds). */
    open fun afterHit(npc: NpcRef, source: EntityRef?) {}

    /** Hitpoints reached 0: true when the NPC survives (a phase change restores it), false to die. */
    open fun onLethal(npc: NpcRef): Boolean = false

    /** The NPC died (before its death delay). */
    open fun died(npc: NpcRef, killer: EntityRef?) {}

    /** False for NPCs that follow normal tolerance (revenants); lair bosses and their minions ignore it. */
    open val ignoresTolerance: Boolean = true

    /** Whether the NPC may pick [player] when hunting (revenants: not bracelet wearers). */
    open fun hunts(npc: NpcRef, player: PlayerRef): Boolean = true

    /** The fraction of a freeze the NPC holds (freeze resistance; 1.0 = none). */
    open val freezeHold: Double = 1.0

    /** Whether a hit makes it target (and chase) its attacker; false for bosses whose own script moves and targets them. */
    open fun retaliates(npc: NpcRef): Boolean = true

    /** Whether a player's melee on the NPC always hits right now (a dazed Bork). */
    open fun meleeAlwaysHits(npc: NpcRef): Boolean = false

    /** Callisto/Artio: a freeze on them while unfrozen always lands (bosses.md CA6). */
    open val firstFreezeLands: Boolean = false
}

/** Boss registry and the shared boss state: minions, damage tracking, lair kill credit. */
internal object Bosses {
    private val byType = HashMap<Int, Boss>()

    init {
        for (
        boss in listOf(
            Callisto,
            Vetion,
            VetionHound,
            Venenatis,
            Spiderling,
            ChaosElemental,
            Scorpia,
            ScorpiaGuardian,
            ChaosFanatic,
            CrazyArchaeologist,
            KingBlackDragon,
            Revenants,
            Maledictus,
            ArchiveGuardian,
            GuardianBattery,
            TfaEzekiel,
            TfaEzekielSword,
            TfaBork,
            TfaSmorks,
            TfaMoia,
            TfaMoiaBinder,
            TfaDreadnoth,
            TfaJelly,
            TfaDagonhai,
            TfaDagonhaiImp,
            TfaXarth,
            TfaXarthCorpse,
            TfaOcellus,
            TfaOcellusRift,
            TfaOcellusVine,
            TfaOcellusBlade,
            TobMaiden,
            TobMaidenMinions,
            TobBloat,
            TobNylocasRoom,
            TobNylo,
            TobVasilias,
            TobSotetseg,
            TobXarpus,
            TobVerzik,
            TobVerzikAthanatos,
        )
        ) {
            for (id in boss.ids) byType[id] = boss
        }
    }

    fun of(type: Int): Boss? = byType[type]

    /** Set on summoned minions: they despawn when they die instead of respawning. */
    val MINION = AttrKey<Boolean>("boss_minion")

    /** The NPC that summoned this minion. */
    val OWNER = AttrKey<NpcRef>("boss_owner")
    private val MINIONS = AttrKey<MutableList<NpcRef>>("boss_minions")
    private val DAMAGE = AttrKey<HashMap<PlayerRef, Int>>("boss_damage")

    /** Spawns minion [type] for [owner] at [at]; it hunts the owner's arena and dies with the owner. */
    fun summon(owner: NpcRef, type: Int, at: CoordGrid, huntRange: Int = MINION_HUNT_RANGE, inheritRun: Boolean = false): NpcRef {
        val minion = owner.spawnNpc(type, at, wanderRange = 0)
        if (inheritRun) {
            // A raid minion that counts as the run's NPC (ToB: Vasilias, Matomenos damage counts for the MVP).
            owner[SharedAttrs.RAID_RUN]?.let { minion[SharedAttrs.RAID_RUN] = it }
            owner[SharedAttrs.RAID_SCALE]?.let { minion[SharedAttrs.RAID_SCALE] = it }
            owner[SharedAttrs.RAID_ORDER]?.let { minion[SharedAttrs.RAID_ORDER] = it }
        }
        minion[MINION] = true
        minion[OWNER] = owner
        minion.huntRange = huntRange
        val list = owner[MINIONS] ?: ArrayList<NpcRef>().also { owner[MINIONS] = it }
        list += minion
        return minion
    }

    /** The owner's minions still alive (optionally of [type]). */
    fun minions(owner: NpcRef, type: Int = -1): List<NpcRef> {
        val list = owner[MINIONS] ?: return emptyList()
        list.removeAll { !it.isInWorld || it.isDying }
        return if (type < 0) list.toList() else list.filter { it.type == type }
    }

    /** Removes every minion of [owner] (death, phase end). */
    fun dismissMinions(owner: NpcRef) {
        val list = owner[MINIONS] ?: return
        for (m in list) if (m.isInWorld) m.despawn()
        list.clear()
    }

    /**
     * CA6: "if Callisto is not already frozen and the attacking player has equipment with a total Magic accuracy
     * greater than 0, the first attack that would freeze the boss is guaranteed to hit" (the Artio page says "0 or
     * higher"; OPEN-CA4: 0 counts).
     */
    fun freezeLands(caster: PlayerRef, target: EntityRef): Boolean =
        target is NpcRef && of(target.type)?.firstFreezeLands == true && !target.isFrozen && caster.bonus(Param.ATTACK_MAGIC) >= 0

    /** Damage per player on any NPC this life (drops, lair credit). */
    fun recordDamage(npc: NpcRef, source: EntityRef?, dealt: Int) {
        if (source !is PlayerRef || dealt <= 0) return
        val map = npc[DAMAGE] ?: HashMap<PlayerRef, Int>().also { npc[DAMAGE] = it }
        map[source] = (map[source] ?: 0) + dealt
    }

    /** The player who dealt the most damage to [npc] this life (ties: the first to reach it). */
    fun topDamager(npc: NpcRef): PlayerRef? = npc[DAMAGE]?.maxByOrNull { it.value }?.key

    /**
     * Boss-lairs BL3: "each kill in which the player deals the most damage reduces the fee by 10,000 coins", up to the
     * full fee (5 kills); the varbit is the one the lair entrance reads.
     */
    fun creditLairKill(npc: NpcRef) {
        val top = topDamager(npc) ?: return
        val discount = top.varbit(Varbits.WBR_ENTRANCE_DISCOUNT)
        if (discount < MAX_DISCOUNT) top.setVarbit(Varbits.WBR_ENTRANCE_DISCOUNT, discount + 1)
    }

    private const val MAX_DISCOUNT = 5

    /** OPEN-B1: minion hunt ranges aren't published; they attack anyone in their arena. */
    private const val MINION_HUNT_RANGE = 12
}

/** Hooks boss scripts into NPC spawns. */
public class BossScripts : ContentPlugin {
    override fun ContentScope.register() {
        onNpcSpawn {
            val boss = Bosses.of(type) ?: return@onNpcSpawn
            // Wilderness boss rework: "immune to Ring of Recoil and similar effects" (bosses.md B3).
            this[SharedAttrs.REFLECT_IMMUNE] = true
            boss.spawned(this)
        }
        onLogin { Dragonfire.onLogin(this) }
        // TFA Ocellus censers (OC3): "Interact" on an active censer.
        for (id in TfaOcellus.CENSERS) onOpLoc(id, 1) { loc -> TfaOcellus.useCenser(player, id, loc.coord) }
        // ToB VZ1 / TB24: Verzik's "Talk-to" (op1) starts her fight; "Quick-start" (op2) needs a completion.
        for (id in TobVerzik.INITIALS) {
            onOpNpc(id, 1) { npc -> TobVerzik.start(npc, player, quick = false) }
            onOpNpc(id, 2) { npc -> TobVerzik.start(npc, player, quick = true) }
        }
    }
}

/** Prayer outcome against a boss attack. */
internal enum class PrayerRule {
    /** The matching protection prayer stops the hit. */
    BLOCKS,

    /** The matching protection prayer halves the damage. */
    HALVES,

    /** Prayer has no effect. */
    IGNORED,
}

/** Attack building blocks for boss scripts. */
internal object BossCombat {
    /** Accuracy roll of [npc]'s [type] attack against [victim] (npc-combat.md N4, the Wiki table's bonuses). */
    fun rolls(npc: NpcRef, victim: EntityRef, type: AttackType): Boolean {
        val def = NpcCombatTable.of(npc.type)
        val attack =
            when (type.style) {
                CombatStyle.MELEE -> Formulas.npcDefenceRoll(npc.level(NpcStat.ATTACK), def?.meleeAttack ?: 0)
                CombatStyle.RANGED -> Formulas.npcDefenceRoll(npc.level(NpcStat.RANGED), def?.rangedAttack ?: 0)
                CombatStyle.MAGIC -> Formulas.npcDefenceRoll(npc.level(NpcStat.MAGIC), def?.magicAttack ?: 0)
            }
        return Formulas.hits(attack, Attacks.defenceRoll(victim, type), npc::random)
    }

    /** Uniform damage in [min]..[max]. */
    fun roll(npc: NpcRef, min: Int, max: Int): Int = if (max <= min) max else min + npc.random(max - min + 1)

    /** Damage after protection prayers ([rule]) for [style]. */
    fun afterPrayer(victim: EntityRef, style: CombatStyle, damage: Int, rule: PrayerRule): Int {
        if (victim !is PlayerRef || !victim.protects(style)) return damage
        return when (rule) {
            PrayerRule.BLOCKS -> 0
            PrayerRule.HALVES -> damage / 2
            PrayerRule.IGNORED -> damage
        }
    }

    /**
     * A standard hit: accuracy roll (unless [accurate]), damage [min]..[max], prayer at launch ([rule]), landing after
     * [delay] ticks; [onLand] gets the damage dealt.
     */
    fun strike(
        npc: NpcRef,
        victim: EntityRef,
        type: AttackType,
        max: Int,
        delay: Int,
        min: Int = 0,
        rule: PrayerRule = PrayerRule.BLOCKS,
        accurate: Boolean = false,
        onLand: ((Int) -> Unit)? = null,
    ) {
        val hit = accurate || rolls(npc, victim, type)
        val raw = if (hit) roll(npc, maxOf(min, if (max > 0) 1 else 0), max) else 0
        val damage = afterPrayer(victim, type.style, raw, rule)
        CombatSystem.queueHit(victim, npc, damage, CombatSystem.hitStyle(type.style), delay) { onLand?.invoke(damage) }
    }

    /** Hit delay of [style] over the distance to [victim] (OPEN-N8: player formulas). */
    fun delay(npc: NpcRef, victim: EntityRef, style: CombatStyle): Int =
        when (style) {
            CombatStyle.MELEE -> 0
            CombatStyle.RANGED -> Formulas.bowDelay(npc.distanceTo(victim))
            CombatStyle.MAGIC -> Formulas.magicDelay(npc.distanceTo(victim))
        }

    /** A projectile from [npc] to [victim] landing after [delay] ticks (30 client cycles per tick). */
    fun projectile(npc: NpcRef, victim: EntityRef, gfx: Int, delay: Int) {
        ProjType.NPC_DEFAULT.launch(npc, victim, gfx, delay)
    }

    /** Players inside the rectangle (an arena) on [npc]'s level. */
    fun playersIn(npc: NpcRef, minX: Int, maxX: Int, minZ: Int, maxZ: Int): List<PlayerRef> =
        npc.playersWithin(ARENA_SCAN).filter { p ->
            !p.isDying && p.coord.x in minX..maxX && p.coord.z in minZ..maxZ
        }

    /** Players standing on [tile]. */
    fun playersOn(npc: NpcRef, tile: CoordGrid, radius: Int = 0): List<PlayerRef> =
        npc.playersWithin(ARENA_SCAN).filter { p ->
            !p.isDying && p.coord.level == tile.level &&
                kotlin.math.abs(p.coord.x - tile.x) <= radius && kotlin.math.abs(p.coord.z - tile.z) <= radius
        }

    /** Typeless damage from an area effect (no prayer, no accuracy), shown as a normal hitsplat. */
    fun areaHit(npc: NpcRef, victim: EntityRef, damage: Int, style: HitStyle = HitStyle.TYPELESS) {
        CombatSystem.queueHit(victim, npc, damage, style, 0)
    }

    /** Lowers [player]'s prayer points by [amount] (not below 0). */
    fun drainPrayer(player: PlayerRef, amount: Int) {
        player.setLevel(Stat.PRAYER, maxOf(0, player.level(Stat.PRAYER) - amount))
    }

    /** A random tile within [radius] of [centre] that can be stood on, or [centre]. */
    fun randomTile(npc: NpcRef, centre: CoordGrid, radius: Int, tries: Int = RANDOM_TRIES): CoordGrid {
        repeat(tries) {
            val t = CoordGrid(centre.x + npc.random(radius * 2 + 1) - radius, centre.z + npc.random(radius * 2 + 1) - radius, centre.level)
            if (npc.isWalkable(t)) return t
        }
        return centre
    }

    private const val CYCLES_PER_TICK = 30
    private const val ARENA_SCAN = 32
    private const val RANDOM_TRIES = 20
}
