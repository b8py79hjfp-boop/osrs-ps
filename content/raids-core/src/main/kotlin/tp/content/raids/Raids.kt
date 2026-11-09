package tp.content.raids

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.InstanceSquare
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.util.CoordGrid

/**
 * The raids of one world (brief section 3): registered definitions, parties and runs. World-scoped (plugins are shared
 * by every world in the JVM): get it with [of].
 */
public class Raids internal constructor() {
    private val definitions = LinkedHashMap<String, RaidDefinition>()
    private val parties = LinkedHashMap<Int, Party>()
    internal val runs = LinkedHashMap<Long, RaidRun>()

    /** `::raid disable` (commands brief §6): raids that start no new runs on this world until restart. */
    public val disabled: MutableSet<String> = HashSet()

    /** `::raid seed` (commands brief §6): the next run's RNG seed, used once. */
    public var nextSeed: Long? = null

    /** `::raid stats`: outcomes since boot. */
    public var completedRuns: Int = 0
        internal set
    public var uniqueRewards: Int = 0
        internal set
    public var rewardsRolled: Int = 0
        internal set

    /** Every run on this world that has not closed. */
    public fun activeRuns(): List<RaidRun> = runs.values.toList()

    private var nextParty = 1
    private var nextRun = 1L

    public fun register(def: RaidDefinition) {
        definitions[def.id] = def
    }

    public fun definition(id: String): RaidDefinition? = definitions[id]

    public fun allDefinitions(): Collection<RaidDefinition> = definitions.values

    public fun parties(raid: String): List<Party> = parties.values.filter { it.raid.id == raid && it.state != PartyState.Disbanded }

    public fun partyOf(name: String): Party? = parties.values.firstOrNull { it.state != PartyState.Disbanded && it.has(name) }

    public fun runOf(name: String): RaidRun? = runs.values.firstOrNull { r -> r.state != RunState.Closed && r.member(name) != null }

    public fun run(id: Long): RaidRun? = runs[id]

    public val activeRuns: Collection<RaidRun> get() = runs.values

    /**
     * The party that keeps [name] from making or joining a [raidId] party: one of that raid, or any party whose run has
     * started. A forming party of another raid doesn't block: [leaveOtherRaids] leaves it (one party at a time).
     */
    public fun blockingParty(name: String, raidId: String): Party? =
        parties.values.firstOrNull {
            it.state != PartyState.Disbanded && it.has(name) && (it.raid.id == raidId || it.state != PartyState.Forming)
        }

    /** Leaves [name]'s forming parties of raids other than [raidId] (the newest party wins); returns the ones left. */
    public fun leaveOtherRaids(name: String, raidId: String): List<Party> {
        val left = parties.values.filter { it.state == PartyState.Forming && it.raid.id != raidId && it.has(name) }
        for (party in left) {
            party.leave(name)
            if (party.state == PartyState.Disbanded) dropParty(party)
        }
        return left
    }

    /** A new forming party led by [leader], who leaves a forming party of another raid first; null if [blockingParty]. */
    public fun createParty(raid: RaidDefinition, leader: String): Party? {
        if (blockingParty(leader, raid.id) != null) return null
        leaveOtherRaids(leader, raid.id)
        val p = Party(PartyId(nextParty++), raid, leader)
        parties[p.id.value] = p
        return p
    }

    internal fun dropParty(party: Party) {
        parties.remove(party.id.value)
    }

    internal fun newRunId(): RaidRunId = RaidRunId(nextRun++)

    internal fun addRun(run: RaidRun) {
        runs[run.id.value] = run
    }

    public companion object {
        private val KEY = AttrKey<Raids>("raids_world")

        public fun of(any: EntityRef): Raids = any.worldAttr(KEY) ?: Raids().also { any.setWorldAttr(KEY, it) }

        /** [name] was accepted into a [raidId] party: leaves forming parties of other raids, telling them if online. */
        public fun leaveOtherRaids(any: EntityRef, name: String, raidId: String) {
            for (party in of(
                any,
            ).leaveOtherRaids(name, raidId)) {
                any.onlinePlayer(name)?.message("You leave your party for ${party.raid.name}.")
            }
        }

        /**
         * Before [p] makes or joins a [raidId] party: false (with nothing changed) when [blockingParty] stops it, else
         * leaves any forming party of another raid and says so.
         */
        public fun claim(p: PlayerRef, raidId: String): Boolean {
            val raids = of(p)
            if (raids.blockingParty(p.username, raidId) != null) return false
            for (party in raids.leaveOtherRaids(p.username, raidId)) p.message("You leave your party for ${party.raid.name}.")
            return true
        }

        public fun of(world: WorldRef): Raids = world.worldAttr(KEY) ?: Raids().also { world.setWorldAttr(KEY, it) }

        /** Ticks an empty run stays before it is destroyed (raids brief section 5 `empty_instance_grace_ticks`). */
        internal const val EMPTY_GRACE_TICKS = 100L

        /** OPEN-RC2: the teleport refusal text inside raids (not captured yet). */
        internal const val NO_TELEPORT = "A magical force prevents you from teleporting."

        // ---- lifecycle

        /**
         * RC2: starts [party]'s raid: requirements re-checked for every online member, scaling and membership locked,
         * the run's RNG seeded from the world RNG, the instance built (it attaches next tick), then every member is
         * moved to the first room. Returns the problem, or null when started.
         */
        public fun start(
            leader: PlayerRef,
            party: Party,
            mode: String = party.raid.modes.first(),
            scaledSize: Int = party.members.size,
            enterNow: Boolean = true,
        ): String? {
            val raids = of(leader)
            val def = party.raid
            if (!party.forming()) return "That party has already started."
            if (def.id in raids.disabled) return "That raid is closed on this world for now."
            if (party.members.size !in def.partySize) return "Your party is not the right size."
            val players = party.members.map { leader.onlinePlayer(it) ?: return "$it is not online." }
            for (p in players) def.requirements(p)?.let { return "${p.username}: $it" }
            val highest = IntArray(COMBAT_STATS) { stat -> players.maxOf { it.baseLevel(stat) } }
            val scaling =
                ScalingContext(
                    party.members.size,
                    maxOf(scaledSize, party.members.size),
                    mode,
                    highest,
                    party.settings.toMap(),
                    players.maxOf { it.combatLevel },
                    IntArray(SKILLS) { skill -> players.sumOf { it.baseLevel(skill) } },
                )
            val seed = raids.nextSeed?.also { raids.nextSeed = null } ?: (leader.random(Int.MAX_VALUE).toLong() shl 1 or 1L)
            val run = RaidRun(raids.newRunId(), def, party, scaling, RaidRng(seed), leader.tick)
            party.state = PartyState.Locked(run)
            raids.addRun(run)
            val entering = if (enterNow) players else listOf(leader)
            val custom = def.layout?.invoke(run)
            val squares =
                custom?.squares ?: def.squares.map { (x, z) ->
                    val levels = def.squareLevels[x to z] ?: ALL_LEVELS
                    InstanceSquare(x, z, x - def.originSquareX, z - def.originSquareZ, levels = levels)
                }
            val width = custom?.widthSquares ?: def.instanceWidth
            val length = custom?.lengthSquares ?: def.instanceLength
            leader.buildInstance(width, length, squares, custom?.zones.orEmpty()) { inst ->
                run.instance = inst
                // Whoever logged out during the build is not admitted (they would hold the run open, unseen).
                val still = entering.mapNotNull { leader.onlinePlayer(it.username) }
                for (p in still) admit(p, run)
                val any = still.firstOrNull()
                if (any == null) {
                    run.state = RunState.Abandoned
                } else {
                    enterRoom(any, run, def.slots.first().first())
                }
            }
            return null
        }

        /**
         * A member who has not entered yet joins the run (TFA: the others follow the leader through the door) while it is
         * still in its first room. Returns the problem, or null.
         */
        public fun enter(player: PlayerRef, run: RaidRun): String? {
            val m = run.member(player.username) ?: return "You are not in this party."
            if (m.inside) return null
            val state = run.state
            if (run.instance == null || state !is RunState.InRoom || run.roomsTaken.size != 1) return "Your party has already moved on."
            admit(player, run)
            player.teleport(entryOf(run, state.room, player))
            return null
        }

        private fun admit(player: PlayerRef, run: RaidRun) {
            val m = run.member(player.username) ?: return
            m.inside = true
            m.online = true
            player[RaidRun.PLAYER_RUN] = run.id.value
            player.setInstanceExit(run.raid.exit)
            player.events.publish(RaidEvent.Entered(run, m.name))
            run.dirty = true
        }

        /** Starts room [roomId]: members inside move to its entry, everyone is alive again, the room script runs. */
        public fun enterRoom(any: EntityRef, run: RaidRun, roomId: String) {
            val room = requireNotNull(run.raid.rooms[roomId]) { "${run.raid.id}: no room $roomId" }
            run.state = RunState.InRoom(room, any.tick)
            run.roomsTaken += roomId
            for (m in run.members.values) {
                m.roomDamage = 0
                m.alive = true
            }
            if (room.gather) for (p in run.presentPlayers(any)) p.teleport(entryOf(run, room, p))
            run.dirty = true
            room.onStart(run, any)
            any.events.publish(RaidEvent.RoomStarted(run, room))
        }

        /**
         * The current room's condition is met: normalised contribution is split by damage share, the room time is
         * recorded, and the run moves on (between rooms, or the reward room).
         */
        public fun roomCleared(any: EntityRef, run: RaidRun) {
            val state = run.state as? RunState.InRoom ?: return
            val room = state.room
            if (run.raid.contribution.normalisedRooms && room.contribution > 0) {
                val total = run.members.values.sumOf { it.roomDamage }
                if (total > 0) for (m in run.members.values) m.points += room.contribution * m.roomDamage.toDouble() / total
            }
            any.events.publish(RaidEvent.RoomCleared(run, room, any.tick - state.startedTick))
            run.slotIndex++
            run.state = RunState.BetweenRooms(run.slotIndex)
            for (m in run.members.values) m.alive = true
            run.dirty = true
            val next = run.raid.slots.getOrNull(run.slotIndex)
            if (run.raid.autoAdvance && next != null && next.size == 1) enterRoom(any, run, next.first())
        }

        /** The party picks [roomId] for the current choice slot (TFA's doors). False when it is not on offer. */
        public fun advance(any: EntityRef, run: RaidRun, roomId: String): Boolean {
            val state = run.state as? RunState.BetweenRooms ?: return false
            if (roomId !in run.raid.slots.getOrElse(state.slot) { emptyList() }) return false
            enterRoom(any, run, roomId)
            return true
        }

        // ---- points

        /** Damage by member [name] on an NPC of this run (normalised rooms count shares; others add points now). */
        internal fun damage(run: RaidRun, name: String, npcType: Int, amount: Int) {
            val m = run.member(name) ?: return
            if (run.state !is RunState.InRoom) return
            if (run.raid.contribution.normalisedRooms) {
                m.roomDamage += amount
            } else {
                m.points += amount * run.raid.contribution.multiplier(npcType)
            }
            run.dirty = true
        }

        /** Points for skilling or other actions (CoX storage, scavenging). */
        public fun addPoints(run: RaidRun, name: String, points: Double) {
            run.member(name)?.let {
                it.points += points
                run.dirty = true
            }
        }

        // ---- deaths, leaving

        /** RC6: a member died in the run. Fills [policy] for combat's respawn. */
        internal fun died(player: PlayerRef, run: RaidRun, policy: tp.plugin.DeathPolicyRequest) {
            val m = run.member(player.username) ?: return
            m.deaths++
            run.teamDeaths++
            m.points = maxOf(0.0, m.points - deathLoss(player, run.raid.contribution.deathPenalty(m, run)))
            policy.handled = true
            run.raidEvent(player, RaidEvent.Died(run, m.name))
            run.dirty = true
            when (val rule = run.raid.deaths) {
                is DeathPolicy.RespawnAtCheckpoint -> {
                    policy.keepItems = true
                    run.raid.rooms[rule.checkpoint(run)]?.let { policy.respawnAt = entryOf(run, it, player) }
                }

                is DeathPolicy.RespawnAt -> {
                    policy.keepItems = true
                    policy.respawnAt = rule.at(run, player) ?: (run.state as? RunState.InRoom)?.room?.let { entryOf(run, it, player) }
                }

                is DeathPolicy.WaitUntilRoomEnds -> {
                    val inRoom = run.state as? RunState.InRoom
                    if (inRoom == null) {
                        // Between rooms (a death resolving after its room was won, poison) or after completion: no
                        // wipe; the player stands up at the last room's entry with their items.
                        policy.keepItems = true
                        val last = run.roomsTaken.lastOrNull()?.let { run.raid.rooms[it] }
                        policy.respawnAt = last?.let { entryOf(run, it, player) }
                        return
                    }
                    m.alive = false
                    val room = inRoom.room
                    val othersAlive = run.members.values.any { it !== m && it.inside && it.online && it.alive }
                    if (othersAlive && room != null) {
                        policy.keepItems = true
                        policy.respawnAt = waitOf(run, room, player)
                    } else {
                        wipe(player, run, rule.wipeByMode[run.scaling.mode] ?: rule.wipe, policy)
                    }
                }
            }
        }

        /** Everyone in the room is dead: eject with graves (TFA) or fail (ToB). */
        private fun wipe(dying: PlayerRef, run: RaidRun, given: WipePolicy, policy: tp.plugin.DeathPolicyRequest) {
            // A per-run rule resolves once to a concrete one (a nested ByRun falls back to failing the raid).
            val rule = (given as? WipePolicy.ByRun)?.pick?.invoke(run)?.takeIf { it !is WipePolicy.ByRun } ?: given
            if (rule is WipePolicy.ByRun) return wipe(dying, run, WipePolicy.FailRaid(), policy)
            val room = (run.state as? RunState.InRoom)?.room
            if (rule == WipePolicy.RetryRoom && room != null) {
                // TB16: everyone stands up again outside the room, items kept; content restarts the room on RoomFailed.
                policy.keepItems = true
                policy.respawnAt = waitOf(run, room, dying)
                for (m in run.members.values) m.alive = true
                dying.events.publish(RaidEvent.RoomFailed(run, room))
                return
            }
            if (room != null) dying.events.publish(RaidEvent.RoomFailed(run, room))
            run.state = RunState.Failed("wipe")
            run.endedTick = dying.tick
            policy.respawnAt = run.raid.exit
            val store = (rule as? WipePolicy.FailRaid)?.storeInv ?: -1
            if (rule == WipePolicy.EjectWithGraves || store >= 0) {
                policy.graveAt = run.raid.graveAt
                policy.storeInv = store
                // Members who died earlier in the room kept their items while waiting; the wipe costs them too.
                for (other in run.presentPlayers(dying)) {
                    if (other == dying) continue
                    dying.events.publish(tp.plugin.DeathItemsRequest(other, run.raid.graveAt, store))
                    leave(other, run, toExit = true)
                }
            }
            run.member(dying.username)?.let { it.inside = false }
            // Members offline inside are out too: the failed run must close (they come back outside it).
            for (m in run.members.values) if (m.inside && !m.online) m.inside = false
            cleanup(dying, run)
            run.raidEvent(dying, RaidEvent.Left(run, dying.username))
        }

        /** [player] leaves [run] (exit, escape, ejection): raid-only items removed, moved to the exit when [toExit]. */
        public fun leave(player: PlayerRef, run: RaidRun, toExit: Boolean = true) {
            val m = run.member(player.username) ?: return
            m.inside = false
            cleanup(player, run)
            if (toExit) player.teleport(run.raid.exit)
            run.raidEvent(player, RaidEvent.Left(run, m.name))
            run.dirty = true
        }

        /** Leave rules for one player: raid-only items out (journaled transaction), run tags and exit cleared. */
        internal fun cleanup(player: PlayerRef, run: RaidRun) {
            player[RaidRun.PLAYER_RUN] = null
            player.setInstanceExit(null)
            removeRaidItems(player, run.raid)
        }

        internal fun removeRaidItems(player: PlayerRef, def: RaidDefinition) {
            if (def.raidOnlyItems.isEmpty()) return
            for (invId in RAID_ITEM_INVS) {
                val inv = player.inv(invId)
                val slots = (0 until inv.size).filter { inv.obj(it) in def.raidOnlyItems }
                if (slots.isNotEmpty()) {
                    player.transaction(
                        "raid items removed",
                    ) { slots.all { removeAt(invId, it, inv.obj(it), inv.count(it)) } }
                }
            }
        }

        // ---- completion and rewards

        /**
         * RC5: the raid is complete. Rewards are computed **once** from a snapshot with the run's RNG, then paid into
         * each present member's claim inventory (a persisted inventory: the claim is the move out of it, so it cannot
         * happen twice). Members who left get nothing (their roll is discarded). KC, time and PB messages follow.
         */
        public fun complete(any: EntityRef, run: RaidRun, endedAt: Long = any.tick) {
            if (run.state == RunState.Completed) return
            run.state = RunState.Completed
            run.endedTick = endedAt
            val snapshot =
                RunSnapshot(
                    run.raid.id,
                    run.scaling.mode,
                    run.scaling.partySize,
                    run.scaling.scaledSize,
                    run.members.mapValues { (_, m) -> Triple(m.points, m.deaths, m.inside) },
                    run.teamDeaths,
                    run.scaling.settings + run.extraSettings,
                )
            val results = run.raid.rewards(snapshot, run.rng)
            val raids = of(any)
            raids.completedRuns++
            raids.rewardsRolled += results.size
            raids.uniqueRewards += results.values.count { it.unique }
            for ((name, m) in run.members) {
                m.reward = if (m.inside) results[name] else null
                if (!m.inside) m.paid = true
            }
            for (p in run.presentPlayers(any)) {
                pay(p, run)
                records(p, run)
            }
            any.events.publish(RaidEvent.Completed(run))
            run.dirty = true
        }

        /** Saved flag: 1 while `::raid waivefee` lets [player]'s next reclaim skip the fee (ToB, ToA). */
        public const val FEE_WAIVED_KEY: String = "raids.fee_waived"

        public fun feeWaived(player: PlayerRef): Boolean = (player.saved(FEE_WAIVED_KEY) ?: 0L) != 0L

        public fun waiveFee(player: PlayerRef) {
            player.setSaved(FEE_WAIVED_KEY, 1L)
        }

        public fun useFeeWaiver(player: PlayerRef) {
            player.setSaved(FEE_WAIVED_KEY, null)
        }

        /** `::raid reissue`: pays [player]'s stored, unpaid reward once; never re-rolls. The problem, or null. */
        public fun reissue(player: PlayerRef, run: RaidRun): String? {
            val m = run.member(player.username) ?: return "${player.username} is not in that run."
            if (m.reward == null) return "${player.username} has no stored reward in that run."
            if (m.paid) return "That reward was already paid."
            pay(player, run)
            return if (m.paid) null else "The reward could not be delivered (no room)."
        }

        /** Puts [player]'s reward in the claim inventory once (also on a later login while the run lives). */
        internal fun pay(player: PlayerRef, run: RaidRun) {
            val m = run.member(player.username) ?: return
            if (m.paid) return
            val reward = m.reward ?: return
            var ok =
                reward.items.isEmpty() ||
                    player.transaction("raid reward ${run.raid.id} ${run.id.value}") {
                        reward.items.all { (obj, n) ->
                            add(run.raid.claimInv, obj, n)
                        }
                    }
            if (!ok) {
                // The claim chest is full (unclaimed loot from an earlier run): the reward goes to the bank instead.
                ok =
                    player.transaction("raid reward to bank ${run.raid.id} ${run.id.value}") {
                        reward.items.all { (obj, n) -> add(tp.symbols.Invs.BANK, obj, n) }
                    }
                player.message(if (ok) REWARD_BANKED else REWARD_NO_ROOM)
            }
            if (ok) {
                m.paid = true
                // The collection log (and any other loot listener) sees raid rewards like NPC drops.
                for ((obj, n) in reward.items) player.events.publish(tp.plugin.LootEvent(player, obj, n))
            }
        }

        private fun records(player: PlayerRef, run: RaidRun) {
            val r = run.raid.records
            val practiceKey = r.practiceKcKey
            if (practiceKey != null && run.scaling.mode == PRACTICE) {
                // Practice runs keep their own count and no personal best (TFA27 "completions (practice)").
                val n = (player.saved(practiceKey) ?: 0L) + 1
                player.setSaved(practiceKey, n)
                r.practiceMessage?.let { player.message(it(n.toInt())) }
                return
            }
            val mode = r.modeOf?.invoke(run) ?: run.scaling.mode
            val varp = r.kcVarpByMode[mode] ?: r.kcVarp
            val kc = player.varp(varp) + 1
            player.setVarp(varp, kc)
            val ticks = run.endedTick - run.startedTick
            val size = run.scaling.partySize
            val bucket = r.pbBucket?.invoke(size) ?: size.toString()
            val key = if (r.kcVarpByMode.isEmpty()) "${r.pbKey}.$bucket" else "${r.pbKey}.$mode.$bucket"
            val best = player.saved(key)
            val newBest = best == null || ticks < best
            if (newBest) player.setSaved(key, ticks)
            val format = r.formatTime ?: { _, t -> formatTicks(t) }
            val time = format(player, ticks)
            val pb = best?.let { format(player, it) }
            player.message(r.teamTimeMessage?.invoke(size, time, pb, newBest) ?: r.timeMessage(time, pb, newBest))
            player.message(r.kcMessageByMode?.invoke(mode, kc) ?: r.kcMessage(kc))
        }

        /**
         * A raid time as [player] reads it (CoX CX53, ToA TA62): `m:ss`, `h:mm:ss` past an hour, with `.cc` hundredths when
         * the precise-timing option (varbit 11866) is on. 60 hundredths per tick.
         */
        public fun formatTime(player: PlayerRef, ticks: Long): String {
            val precise = player.varbit(tp.symbols.Varbits.OPTION_PRECISE_TIMING) == 1
            val centis = ticks * CENTIS_PER_TICK
            val totalSeconds = centis / CENTIS
            val h = totalSeconds / SECONDS_PER_HOUR
            val m = (totalSeconds / SECONDS_PER_MINUTE) % SECONDS_PER_MINUTE
            val sec = totalSeconds % SECONDS_PER_MINUTE
            val base = if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
            return if (precise) base + ".%02d".format(centis % CENTIS) else base
        }

        private const val CENTIS = 100L
        private const val SECONDS_PER_HOUR = 3600L

        /** Ticks as `m:ss.cc` (0.6 s per tick), the raids' time format (`2:25.80`). */
        public fun formatTicks(ticks: Long): String {
            val centis = ticks * CENTIS_PER_TICK
            val minutes = centis / CENTIS_PER_MINUTE
            val seconds = centis / CENTIS_PER_SECOND % SECONDS_PER_MINUTE
            val c = centis % CENTIS_PER_SECOND
            return "$minutes:${seconds.toString().padStart(2, '0')}.${c.toString().padStart(2, '0')}"
        }

        // ---- closing

        /** Tears a finished or empty run down: NPCs, ground items and the instance go (IN9), the party is released. */
        internal fun close(world: WorldRef, run: RaidRun) = close(of(world), run)

        /**
         * Ends [run] at once (CoX's lobby Reload, CX5): everyone inside leaves to the exit, the instance goes and the party
         * forms again, so its leader can start a new run.
         */
        public fun reset(any: EntityRef, run: RaidRun) {
            for (p in run.presentPlayers(any)) leave(p, run)
            close(of(any), run)
        }

        private fun close(raids: Raids, run: RaidRun) {
            if (run.state == RunState.Closed) return
            run.instance?.destroy()
            run.state = RunState.Closed
            raids.runs.remove(run.id.value)
            run.party.state = PartyState.Forming
            if (run.party.members.isEmpty()) raids.dropParty(run.party)
        }

        /** Spawns NPC [type] for [run] at instance tile [at] (generated layouts): tagged and scaled like [spawn]. */
        public fun spawnAt(any: EntityRef, run: RaidRun, type: Int, at: CoordGrid, init: (NpcRef.() -> Unit)? = null): NpcRef =
            any.spawnNpc(type, at, 0) {
                tag(run, this)
                init?.invoke(this)
            }

        /** Tags [npc] to [run] and scales it (brief 3.7); done before its spawn handlers run. */
        private fun tag(run: RaidRun, npc: NpcRef) {
            npc[RaidRun.NPC_RUN] = run.id.value
            npc[tp.plugin.SharedAttrs.RAID_SCALE] = run.scaling.scaledSize
            npc[tp.plugin.SharedAttrs.RAID_ORDER] = run.party.members.toList()
            npc[tp.plugin.SharedAttrs.RAID_SCALER] = { n -> run.raid.scaling(run.scaling, n) }
            npc[tp.plugin.SharedAttrs.RAID_MODE] = run.scaling.mode
            run.raid.scaling(run.scaling, npc)
        }

        /** Where [room] is entered in [run] (its per-run entry, else its template entry). */
        public fun entryOf(run: RaidRun, room: RoomDef, any: EntityRef): CoordGrid = room.entryAt?.invoke(run, any) ?: run.at(room.entry)

        /** Where a dead player waits in [room]. */
        public fun waitOf(run: RaidRun, room: RoomDef, any: EntityRef): CoordGrid =
            room.waitAt?.let { run.at(it) } ?: entryOf(run, room, any)

        /** Spawns NPC [type] for [run] at template tile [template]: tagged to the run and scaled (brief 3.7). */
        public fun spawn(any: EntityRef, run: RaidRun, type: Int, template: CoordGrid, init: (NpcRef.() -> Unit)? = null): NpcRef =
            spawnAt(any, run, type, run.at(template), init)

        private fun RaidRun.raidEvent(any: EntityRef, event: RaidEvent) {
            any.events.publish(event)
        }

        private const val COMBAT_STATS = 6
        private const val SKILLS = tp.plugin.Stats.SAILING + 1

        /** OPEN: no captured line for a reward that did not fit the raid's reward chest. */
        private const val REWARD_BANKED = "Your reward chest is full, so your reward has been sent to your bank."
        private const val REWARD_NO_ROOM = "You have no room for your reward: clear your reward chest and bank, then log in again."

        /** The mode name of practice runs (their completions are counted apart). */
        internal const val PRACTICE = "practice"

        /** The map's four levels (0-3). */
        private const val ALL_LEVELS = 4
        private val RAID_ITEM_INVS = intArrayOf(tp.symbols.Invs.INV, tp.symbols.Invs.WORN)
        private const val CENTIS_PER_TICK = 60L
        private const val CENTIS_PER_SECOND = 100L
        private const val CENTIS_PER_MINUTE = 6000L
        private const val SECONDS_PER_MINUTE = 60L
    }
}
