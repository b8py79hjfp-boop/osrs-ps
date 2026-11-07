package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.plugin.SlayerTaskCheck

/** Builds [GearEffects] inputs from live entities. The [GearEffects.Kit] is cached per player until the worn items change. */
internal object GearContext {
    private class Cached(val ids: IntArray, val kit: GearEffects.Kit)

    private val KIT = AttrKey<Cached>("gear_effects_kit")

    fun kit(player: PlayerRef): GearEffects.Kit {
        val cached = player[KIT]
        if (cached != null && (0 until Wear.SLOTS).all { cached.ids[it] == player.worn(it) }) return cached.kit
        val ids = IntArray(Wear.SLOTS) { player.worn(it) }
        val kit = GearEffects.Kit(ids.filter { it >= 0 }.map { player.objName(it) })
        player[KIT] = Cached(ids, kit)
        return kit
    }

    fun foe(player: PlayerRef, target: EntityRef?, kit: GearEffects.Kit): GearEffects.Foe =
        when (target) {
            is PlayerRef -> {
                GearEffects.Foe(
                    emptySet(),
                    1,
                    target.level(Stat.MAGIC),
                    target.bonus(Param.ATTACK_MAGIC),
                    target.username,
                    isPlayer = true,
                    onSlayerTask = false,
                )
            }

            is NpcRef -> {
                val def = NpcCombatTable.of(target.type)
                val onTask =
                    with(GearEffects) { kit.blackMask() } &&
                        SlayerTaskCheck(player, target).also { player.events.publish(it) }.onTask
                GearEffects.Foe(
                    def?.attributes ?: emptySet(),
                    target.size,
                    target.level(NpcStat.MAGIC),
                    def?.magicAttack ?: 0,
                    player.npcName(target.type),
                    isPlayer = false,
                    onSlayerTask = onTask,
                )
            }

            null -> {
                GearEffects.Foe(emptySet(), 1, 0, 0, "", isPlayer = false, onSlayerTask = false)
            }
        }

    /**
     * Equipment bonus [param] with the adjustments the game makes to the worn totals (gear-effects.md GE8): Keris
     * partisan of amascut outside the Tombs of Amascut (−22 strength, −50 stab), Dinh's bulwark strength from its
     * defences, a charged Dizana's quiver with arrows (+10 ranged attack, +1 ranged strength) and the blowpipe's darts.
     */
    fun bonus(player: PlayerRef, param: Int, kit: GearEffects.Kit = kit(player)): Int {
        var total = player.bonus(param)
        if (kit.has("keris partisan of amascut")) {
            if (param == Param.MELEE_STRENGTH) total -= AMASCUT_STRENGTH
            if (param == Param.ATTACK_STAB) total -= AMASCUT_STAB
        }
        if (param == Param.MELEE_STRENGTH && kit.any { it.startsWith("dinh's") && it.endsWith("bulwark") }) {
            val defences =
                intArrayOf(
                    Param.DEFENCE_STAB,
                    Param.DEFENCE_SLASH,
                    Param.DEFENCE_CRUSH,
                    Param.DEFENCE_RANGED,
                ).sumOf { player.bonus(it) }
            total += maxOf(0, (defences - BULWARK_BASE) / BULWARK_DIVISOR - BULWARK_OFFSET)
        }
        val rangedParam = param == Param.ATTACK_RANGED || param == Param.RANGED_STRENGTH
        if (rangedParam && kit.has(*DIZANA_CHARGED) && arrowsInUse(player)) {
            total += if (param == Param.ATTACK_RANGED) DIZANA_ATTACK else DIZANA_STRENGTH
        }
        if (param == Param.RANGED_STRENGTH) total += Blowpipe.dartStrength(player)
        return total
    }

    private fun arrowsInUse(player: PlayerRef): Boolean {
        val ammo = player.worn(Wear.AMMO)
        return player.weaponCategory() === Weapons.BOW && ammo >= 0 && player.objName(ammo).lowercase().contains("arrow")
    }

    /** Wiki "Keris partisan of amascut": its bonuses drop outside the Tombs (not modelled here, so always). */
    private const val AMASCUT_STRENGTH = 22
    private const val AMASCUT_STAB = 50

    /** Wiki "Dinh's bulwark": strength = ⌊(stab + slash + crush + ranged defence − 800) / 12⌋ − 38, at least 0. */
    private const val BULWARK_BASE = 800
    private const val BULWARK_DIVISOR = 12
    private const val BULWARK_OFFSET = 38

    /** Wiki "Dizana's quiver": charged, with arrows: +10 ranged attack, +1 ranged strength. */
    private val DIZANA_CHARGED =
        arrayOf(
            "dizana's quiver",
            "dizana's quiver (l)",
            "blessed dizana's quiver",
            "blessed dizana's quiver (l)",
            "dizana's max cape",
            "dizana's max cape (l)",
        )
    private const val DIZANA_ATTACK = 10
    private const val DIZANA_STRENGTH = 1

    fun ctx(player: PlayerRef, target: EntityRef?, type: AttackType, special: Boolean = false): GearEffects.Ctx {
        val kit = kit(player)
        return GearEffects.Ctx(kit, foe(player, target, kit), type, player[SharedAttrs.IN_WILDERNESS] == true, special)
    }
}
