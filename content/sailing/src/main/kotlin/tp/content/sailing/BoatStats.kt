package tp.content.sailing

/** A facility built on a boat: the hotspot it stands on, the db row and its position in the hotspot's option list (+ 1 is saved). */
internal class BuiltFacility(val hotspot: Int, val facility: BoatFacility, val option: Int)

/**
 * The stats of a boat as built (docs/mechanics/sailing-building.md ST1-ST4), recomputed by [BoatStats.of] after every
 * customisation. The cache stores stats per part (db 164); the Wiki states per-part values only, so the combination rule is
 * OPEN-B3: hitpoints, defences and armours add up over the hull, sail, helm, keel and facilities; speed, speed cap, storm
 * resistance, acceleration and boost duration come from the one part that owns them (hull: speed; sail: the rest); the
 * resistance flags are true if any part gives them; wind motes, ammo saving and auto repair take the best facility.
 */
internal class BoatStats(
    val maxHp: Int,
    val defence: Int,
    val armour: Int,
    /** Fine units per tick (128 per tile) of the hull. */
    val baseSpeed: Int,
    val speedCap: Int,
    /** Sail acceleration, or [DEFAULT_ACCELERATION] when the sail row has none (OPEN-S3). */
    val acceleration: Int,
    val speedBoostDuration: Int,
    val stormResistance: Int,
    val rapidResistance: Int,
    val crystalHelmResistance: Int,
    /** Items the cargo hold holds (0 without a hold; stats row of the built hold, docs/plans/sailing.md 2.3). */
    val cargoHoldSize: Int,
    val maxWindMotes: Int,
    val ammoSavePercentage: Int,
    val autoRepairRate: Int,
    val fetidWaterResistant: Boolean,
    val crystalFleckedResistant: Boolean,
    val tangledKelpResistant: Boolean,
    val icySeasResistant: Boolean,
    /** Percent added to the recovery cost by the hull (0..30). */
    val recoveryCostPercentage: Int,
) {
    companion object {
        /**
         * Default sail acceleration of the tiers whose db row has none (OPEN-S3 / OPEN-M3): the cache stores 64 for the
         * Wiki's 1.0 (camphor and better), so the Wiki's 0.5 is 32 [docs/plans/sailing.md 2.2].
         */
        const val DEFAULT_ACCELERATION: Int = 32

        val EMPTY: BoatStats = BoatStats(0, 0, 0, 0, 0, DEFAULT_ACCELERATION, 0, 0, 0, 0, 0, 0, 0, 0, false, false, false, false, 0)

        /** Stats of [boat] as it is mounted now: the db 164 rows of its core and cosmetic parts and of its facilities. */
        fun of(data: SailingData, boat: ActiveBoat): BoatStats {
            val core = listOfNotNull(boat.hullStats, boat.sailStats, boat.helmStats, boat.keelStats)
            val cosmetic = listOfNotNull(boat.brazier, boat.flag, boat.trim, boat.pattern).mapNotNull { data.statsOf(it.statsRow) }
            val built = boat.facilities.mapNotNull { data.statsOf(it.facility.statsRow) }
            val all = core + cosmetic + built
            val hold = boat.facilities.firstOrNull { it.facility.subtype == ShipbuildingData.CARGO_HOLD_SUBTYPE }
            return BoatStats(
                maxHp = all.sumOf { it.hpMax },
                defence = all.sumOf { it.defence },
                armour = all.sumOf { it.armour },
                baseSpeed = boat.hullStats?.baseSpeed ?: 0,
                speedCap = boat.hullStats?.speedCap ?: 0,
                acceleration = boat.sailStats?.acceleration ?: DEFAULT_ACCELERATION,
                speedBoostDuration = boat.sailStats?.speedBoostDuration ?: 0,
                stormResistance = boat.sailStats?.stormResistance ?: 0,
                rapidResistance = boat.helmStats?.rapidResistance ?: 0,
                crystalHelmResistance = boat.helmStats?.crystalHelmResistance ?: 0,
                cargoHoldSize = hold?.let { data.statsOf(it.facility.statsRow)?.cargoHoldSize } ?: 0,
                maxWindMotes = all.maxOfOrNull { it.maxWindMotes } ?: 0,
                ammoSavePercentage = all.maxOfOrNull { it.ammoSavePercentage } ?: 0,
                autoRepairRate = all.maxOfOrNull { it.autoRepairRate } ?: 0,
                fetidWaterResistant = all.any { it.fetidWaterResistant },
                crystalFleckedResistant = all.any { it.crystalFleckedResistant },
                tangledKelpResistant = all.any { it.tangledKelpResistant },
                icySeasResistant = all.any { it.icySeasResistant },
                recoveryCostPercentage = boat.hullStats?.additionalRecoveryCostPercentage ?: 0,
            )
        }
    }
}
