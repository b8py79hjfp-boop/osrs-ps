package tp.content.raids.toa

/** Named flags for the ToA rules the sources leave open (docs/mechanics/raids/toa.md); system properties override. */
internal object ToaConfig {
    /** OPEN-TA5: Blazing Tombs I / II are Leagues and event content; not offered by default. */
    val blazingTombsEnabled: Boolean = System.getProperty("tp.toa.blazingTombsEnabled") == "true"

    /** OPEN-TA2: a player with no party who enters the raid door starts a solo raid (a party of one). */
    val soloWithoutParty: Boolean = System.getProperty("tp.toa.soloWithoutParty") != "false"

    /** OPEN-TA8: the mode of a finished raid (records, KC) follows the final raid level, not the starting one. */
    val modeUsesFinalLevel: Boolean = System.getProperty("tp.toa.modeUsesFinalLevel") != "false"

    /**
     * OPEN-TA6 (toa.md TA12): the missed time limit lowers only the reward and mode level at completion (on, the working
     * assumption). Off: it lowers the raid level the tick the limit passes (HUD and every NPC spawned afterwards).
     */
    val timeLimitPenaltyAtEndOnly: Boolean = System.getProperty("tp.toa.timeLimitPenaltyAtEndOnly") != "false"

    /** OPEN-TA11: the party size used for scaling is the size at the start (fixed for the run). */
    val partySizeAtStart: Boolean = System.getProperty("tp.toa.partySizeAtStart") != "false"

    /** OPEN-TA23: when the leader leaves a forming party, the next slot leads. */
    val leaderHandover: Boolean = System.getProperty("tp.toa.leaderHandover") != "false"

    /** OPEN-TC13 (client-contract 10): "Move up" on member row 1 promotes that member to leader; false ignores that click. */
    val moveUpPromotes: Boolean = System.getProperty("tp.toa.moveUpPromotes") != "false"

    // ---- the puzzle rooms (docs/mechanics/raids/toa/puzzles.md): every value below is a working assumption for an OPEN item

    private fun puzzleProp(name: String, default: Long): Long = System.getProperty("tp.toa.$name")?.toLongOrNull() ?: default

    /** OPEN-PZ13: the next Apmeken wave starts once at most this many combat baboons are left (fewer kills needed, 30 Aug 2023). */
    val apmekenWaveThreshold: Int = puzzleProp("apmeken.waveThreshold", 2).toInt()

    /** OPEN-PZ13: ticks from the room start to wave 1. */
    val apmekenFirstWaveDelay: Long = puzzleProp("apmeken.firstWaveDelay", 5)

    /** OPEN-PZ3: from this wave on the level 68 baboons (the "large" variant) spawn instead of level 56. */
    val apmekenLargeFromWave: Int = puzzleProp("apmeken.largeFromWave", 5).toInt()

    /** OPEN-PZ13: the extra combat baboon a group gets per wave (default: the level 56 Brawler 11709). */
    val apmekenGroupExtraBaboon: Int = puzzleProp("apmeken.groupExtraBaboon", 11709).toInt()

    /** OPEN-PZ11: ticks the Sight stays with a raider before it moves to the next one. */
    val apmekenSightCycleTicks: Long = puzzleProp("apmeken.sightCycleTicks", 100)

    /** OPEN-PZ12: ticks between one danger being answered (or failing) and the next. */
    val apmekenDangerPeriodTicks: Long = puzzleProp("apmeken.dangerPeriodTicks", 60)

    /** OPEN-PZ12: ticks from the warning to the failure. */
    val apmekenDangerCountdownTicks: Long = puzzleProp("apmeken.dangerCountdownTicks", 25)

    /** OPEN-PZ12: damage to every raider per missed action. */
    val apmekenFailureDamage: Int = puzzleProp("apmeken.failureDamage", 10).toInt()

    /** OPEN-PZ12: damage to a raider who repairs / pours where nothing was wrong. */
    val apmekenWrongActionDamage: Int = puzzleProp("apmeken.wrongActionDamage", 10).toInt()

    /** OPEN-PZ18: water per additional player on top of the solo 175 (the pre-25 Jun 2025 linear rule). */
    val crondisWaterPerPlayer: Int = puzzleProp("crondis.waterPerPlayer", 125).toInt()

    /** OPEN-PZ37: the palm keeps its growth when a wipe restarts the room ("previous progress being saved"). */
    val crondisKeepGrowthOnWipe: Boolean = System.getProperty("tp.toa.crondis.keepGrowthOnWipe") != "false"

    /** OPEN-PZ21: ticks between crocodile spawns (one per corner each time, at most eight alive). */
    val crondisCrocSpawnTicks: Long = puzzleProp("crondis.crocSpawnTicks", 25)

    /** OPEN-PZ20: a trap tile repeats every this many ticks and is dangerous for the first [crondisTrapActiveTicks] of them. */
    val crondisTrapPeriodTicks: Int = puzzleProp("crondis.trapPeriodTicks", 8).toInt()
    val crondisTrapActiveTicks: Int = puzzleProp("crondis.trapActiveTicks", 2).toInt()

    /** OPEN-PZ20: damage of a trap hit. */
    val crondisTrapDamage: Int = puzzleProp("crondis.trapDamage", 8).toInt()

    /** OPEN-PZ20: percent chance that a trap hit takes only 16% of the container instead of 50%. */
    val crondisSmallLossPercent: Int = puzzleProp("crondis.smallLossPercent", 10).toInt()

    /** OPEN-PZ19: ticks a waterfall stays dry after a fill. */
    val crondisRefillTicks: Int = puzzleProp("crondis.refillTicks", 50).toInt()

    /** OPEN-PZ5: the cloud loc that damages (45418; 45419 / 45420 are the other cloud). */
    val crondisDarkCloudLoc: Int = puzzleProp("crondis.darkCloudLoc", 45418).toInt()

    /** OPEN-PZ5: the lighter (warning) cloud, shown the tick before a cloud tile turns dangerous. */
    val crondisLightCloudLoc: Int = puzzleProp("crondis.lightCloudLoc", 45419).toInt()

    /** OPEN-PZ26: ticks between beams (LD's 9; `toa.het.beamPeriodTicks` in the doc). */
    val hetBeamPeriodTicks: Long = puzzleProp("het.beamPeriodTicks", 9)

    /** OPEN-PZ27: damage of a beam hit and of an orb of darkness hit (the beam is "lower than the orbs"). */
    val hetBeamDamage: Int = puzzleProp("het.beamDamage", 10).toInt()
    val hetOrbDamage: Int = puzzleProp("het.orbDamage", 20).toInt()

    /** OPEN-PZ25: worn (breakable) walls placed on the drafted beam line. */
    val hetWornWalls: Int = puzzleProp("het.wornWalls", 2).toInt()

    /** OPEN-PZ25: ticks to break a worn wall with the slowest pickaxe; each tier above the lowest saves one. */
    val hetBreakTicks: Int = puzzleProp("het.breakTicks", 4).toInt()

    /** OPEN-PZ28: ticks between Seal hits (2 gives 8 hits in the 15-tick window, matching the wiki's 8-hit claims). */
    val hetSealHitIntervalTicks: Int = puzzleProp("het.sealHitIntervalTicks", 2).toInt()

    /** OPEN-PZ31: ticks between scarab spawns and the cap (plus one per player). */
    val scabarasScarabTicks: Long = puzzleProp("scabaras.scarabRate", 20)
    val scabarasScarabMax: Int = puzzleProp("scabaras.scarabMax", 2).toInt()

    /** OPEN-PZ32: ticks per plate of the sequence, how many of them it stays lit, and the damage of a wrong plate. */
    val scabarasSeqStepTicks: Long = puzzleProp("scabaras.seqStepTicks", 2)
    val scabarasSeqLitTicks: Long = puzzleProp("scabaras.seqLitTicks", 1)
    val scabarasSeqDamage: Int = puzzleProp("scabaras.seqDamage", 10).toInt()

    /** OPEN-PZ33: ticks from a wrong obelisk to the rocks landing, and their damage. */
    val scabarasRockDelayTicks: Long = puzzleProp("scabaras.rockDelayTicks", 3)
    val scabarasRockDamage: Int = puzzleProp("scabaras.rockDamage", 10).toInt()

    /** SC5: damage of the "Flip" option (Strat: 20, unavoidable). */
    val scabarasFlipDamage: Int = puzzleProp("scabaras.flipDamage", 20).toInt()

    /** OPEN-PZ35: damage of going over the addition target. */
    val scabarasAdditionDamage: Int = puzzleProp("scabaras.additionDamage", 10).toInt()

    /** OPEN-PZ36: ticks a revealed matching symbol stays visible. */
    val scabarasRevealTicks: Int = puzzleProp("scabaras.revealTicks", 3).toInt()

    // ---- points, rewards and records (toa.md TA35-TA41, TA60-TA69; toa/lobby-rewards.md TR1-TR29)

    /** OPEN-TA13: room points enter Total at once (RL-TOA's reading) instead of at room completion (the official post). */
    val roomPointsLiveInTotal: Boolean = System.getProperty("tp.toa.roomPointsLiveInTotal") == "true"

    /** OPEN-TA14: Zebak's room-point cap (RL-TOA says 10,000; the post says 20,000 for every room). */
    val zebakRoomPointCap: Int = System.getProperty("tp.toa.zebakRoomPointCap")?.toIntOrNull() ?: ToaPoints.ROOM_CAP

    /** OPEN-TA14: the Wardens' room-point cap, counted as one room (RL-TOA says 3 x 20,000). */
    val wardensRoomPointCap: Int = System.getProperty("tp.toa.wardensRoomPointCap")?.toIntOrNull() ?: ToaPoints.ROOM_CAP

    /** OPEN-TA15: the phase-2 Wardens earn no points once this many downs happened (post: 3; RL-TOA's counter: 4). */
    val wardensP2PointDowns: Int = System.getProperty("tp.toa.wardensP2PointDowns")?.toIntOrNull() ?: DEFAULT_P2_DOWNS

    /** OPEN-TA18: a tie for a room's MVP pays every tied player (off: the first in party order). */
    val mvpTieAwardsAll: Boolean = System.getProperty("tp.toa.mvpTieAwardsAll") == "true"

    /** OPEN-TA18: the four challenge (puzzle) rooms award an MVP too (the wiki module counts 9 rooms). */
    val mvpPuzzleRooms: Boolean = System.getProperty("tp.toa.mvpPuzzleRooms") != "false"

    /** OPEN-TA19: puzzle rooms credit a flat per-player estimate at completion unless the puzzle reported its own points. */
    val puzzleFlatPoints: Boolean = System.getProperty("tp.toa.puzzlePoints.flat") != "false"

    /** OPEN-TA19: the flat estimates per player (RL-TOA / wiki module: Scabaras 300, Apmeken 450, Crondis 400, Het none). */
    val puzzlePointsScabaras: Int = System.getProperty("tp.toa.puzzlePoints.scabaras")?.toIntOrNull() ?: DEFAULT_SCABARAS_POINTS
    val puzzlePointsApmeken: Int = System.getProperty("tp.toa.puzzlePoints.apmeken")?.toIntOrNull() ?: DEFAULT_APMEKEN_POINTS
    val puzzlePointsCrondis: Int = System.getProperty("tp.toa.puzzlePoints.crondis")?.toIntOrNull() ?: DEFAULT_CRONDIS_POINTS

    /** OPEN-TR5: a player who left before the end can be drawn for the unique (their reward is then discarded). */
    val leaverCanWinUnique: Boolean = System.getProperty("tp.toa.leaverCanWinUnique") == "true"

    /** OPEN-TR4: raid levels above 550 add nothing to the unique chance (RL-TOA and the 2022 post; Calc and the Chest do not clamp). */
    val uniqueLevelClamp550: Boolean = System.getProperty("tp.toa.uniqueLevelClamp550") == "true"

    /** OPEN-TR6: the fossilised dung threshold of 1,500 applies to the contribution (Total - 5,000) instead of to Total. */
    val dungThresholdOnContribution: Boolean = System.getProperty("tp.toa.dungThresholdOnContribution") == "true"

    /** OPEN-TR2: the pet roll uses the player's own contribution (off: the team average). */
    val petUsesPersonalPoints: Boolean = System.getProperty("tp.toa.petUsesPersonalPoints") != "false"

    /** OPEN-TR11: commons below raid level 150 are scaled by this percent (Calc: 75; the Chest page's prose reads 100). */
    val entryCommonScalePercent: Int = System.getProperty("tp.toa.entryCommonScalePercent")?.toIntOrNull() ?: DEFAULT_ENTRY_COMMON_PERCENT

    /** OPEN-TR7: how the purple weights between the wiki's anchors round: "floor" (the Calc module) or "nearest". */
    val purpleReweightRounding: String = System.getProperty("tp.toa.purpleReweightRounding") ?: "floor"

    /** OPEN-TR13: the item a failed gated unique turns into: "uniform" (thread or an unowned jewel), "thread" or "none". */
    val gatedFailFallback: String = System.getProperty("tp.toa.gatedFailFallback") ?: "uniform"

    /** OPEN-TR14: the elite clue chance multiplier in percent (105 with the Elite Combat Achievements perk; no tier API yet). */
    val eliteClueMultiplierPercent: Int = System.getProperty("tp.toa.eliteClueMultiplierPercent")?.toIntOrNull() ?: PERCENT

    /** OPEN-TR12: the kill count of bad luck mitigation includes Entry completions (off: Normal and Expert only). */
    val bmlCountsEntry: Boolean = System.getProperty("tp.toa.bmlCountsEntry") != "false"

    /** OPEN-TR16: unclaimed loot in the Rewards Niche is lost when the player enters another raid. */
    val unclaimedLootLostOnEntry: Boolean = System.getProperty("tp.toa.unclaimedLootLostOnEntry") != "false"

    /** OPEN-TA21: the Item Retrieval fee as a percent of the stored items' Grand Exchange value (the cap is [reclaimFeeCap]). */
    val reclaimFeePercent: Int = System.getProperty("tp.toa.reclaimFeePercent")?.toIntOrNull() ?: PERCENT

    /** TA45: "up to 500,000 coins". */
    val reclaimFeeCap: Int = System.getProperty("tp.toa.reclaimFeeCap")?.toIntOrNull() ?: DEFAULT_RECLAIM_CAP

    /** OPEN-TA32: the loadout value (Peasant, Showoff) counts the backpack as well as the worn equipment. */
    val loadoutIncludesInventory: Boolean = System.getProperty("tp.toa.loadoutIncludesInventory") != "false"

    // ---- supplies (toa.md TA51-TA59, ToaSupplies / ToaConsumables / ToaRestores)

    /** OPEN-TA25 (TA53): a supply cut to 66% / 33% / 10% rounds up with a probability equal to the fraction (off: plain floor). */
    val supplyStochasticRounding: Boolean = System.getProperty("tp.toa.supplyStochasticRounding") != "false"

    /** OPEN-TA24 (TA52): the percent chance of each rare Chaos extra (ambrosia 0-1, liquid adrenaline 0-1); the wiki only says "rare". */
    val chaosRareChancePercent: Int = System.getProperty("tp.toa.chaosRareChancePercent")?.toIntOrNull() ?: DEFAULT_CHAOS_RARE_PERCENT

    /** OPEN-TA27 (TA59): smelling salts restore run energy at every 25-tick refresh (the item page) instead of once (the main page). */
    val saltsEnergyEveryRefresh: Boolean = System.getProperty("tp.toa.saltsEnergyEveryRefresh") == "true"

    /** OPEN-TA26 (TA55): honey locusts given to each player on a wipe in a challenge room, a boss room and the Wardens. */
    val honeyLocustsPuzzle: Int = System.getProperty("tp.toa.honeyLocustsPuzzle")?.toIntOrNull() ?: DEFAULT_LOCUSTS_PUZZLE
    val honeyLocustsBoss: Int = System.getProperty("tp.toa.honeyLocustsBoss")?.toIntOrNull() ?: DEFAULT_LOCUSTS_BOSS
    val honeyLocustsWardens: Int = System.getProperty("tp.toa.honeyLocustsWardens")?.toIntOrNull() ?: DEFAULT_LOCUSTS_WARDENS

    /** OPEN-TA26 (TA55): in Expert Mode a stack above this many honey locusts resets to one when one is eaten ("more than 2"). */
    val honeyLocustExpertCap: Int = System.getProperty("tp.toa.honeyLocustExpertCap")?.toIntOrNull() ?: DEFAULT_LOCUST_EXPERT_CAP

    /** OPEN-TA34 (TA68): Entry completions count towards Icthlarin's shroud (the wiki names Normal and Expert only). */
    val shroudCountsEntry: Boolean = System.getProperty("tp.toa.shroudCountsEntry") == "true"

    private const val PERCENT = 100
    private const val DEFAULT_P2_DOWNS = 3
    private const val DEFAULT_SCABARAS_POINTS = 300
    private const val DEFAULT_APMEKEN_POINTS = 450
    private const val DEFAULT_CRONDIS_POINTS = 400
    private const val DEFAULT_ENTRY_COMMON_PERCENT = 75
    private const val DEFAULT_RECLAIM_CAP = 500_000
    private const val DEFAULT_CHAOS_RARE_PERCENT = 25
    private const val DEFAULT_LOCUSTS_PUZZLE = 1
    private const val DEFAULT_LOCUSTS_BOSS = 1
    private const val DEFAULT_LOCUSTS_WARDENS = 2
    private const val DEFAULT_LOCUST_EXPERT_CAP = 2
}
