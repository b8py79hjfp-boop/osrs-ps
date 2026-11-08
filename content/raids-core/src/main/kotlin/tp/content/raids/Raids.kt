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

    /** A new forming party led by [leader] (who must not be in another one). */
    public fun createParty(raid: RaidDefinition, leader: String): Party? {
        if (partyOf(leader) != null) return null
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
        ): String? {
            val raids = of(leader)
            val def = party.raid
            if (!party.forming()) return "That party has already started."
            if (party.members.size !in def.partySize) return "Your party is not the right size."
            val players = party.members.map { leader.onlinePlayer(it) ?: return "$it is not online." }
            for (p in players) def.requirements(p)?.let { return "${p.username}: $it" }
            val highest = IntArray(COMBAT_STATS) { stat -> players.maxOf { it.baseLevel(stat) } }
            val scaling = ScalingContext(party.members.size, maxOf(scaledSize, party.members.size), mode, highest, party.settings.toMap())
            val run =
                RaidRun(raids.newRunId(), def, party, scaling, RaidRng(leader.random(Int.MAX_VALUE).toLong() shl 1 or 1L), leader.tick)
            party.state = PartyState.Locked(run)
            raids.addRun(run)
            for (p in players) {
                p[RaidRun.PLAYER_RUN] = run.id.value
                p.setInstanceExit(def.exit)
            }
            val squares = def.squares.map { (x, z) -> InstanceSquare(x, z, x - def.originSquareX, z - def.originSquareZ) }
            leader.buildInstance(def.instanceWidth, def.instanceLength, squares) { inst ->
                run.instance = inst
                enterRoom(leader, run, def.slots.first().first())
                for (p in players) run.raidEvent(p, RaidEvent.Entered(run, p.username))
            }
            return null
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
            for (p in run.presentPlayers(any)) p.teleport(run.at(room.entry))
            run.dirty = true
            room.onStart(run)
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
            if (next != null && next.size == 1) enterRoom(any, run, next.first())
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
            m.points = maxOf(0.0, m.points - run.raid.contribution.deathPenalty(m, run))
            policy.handled = true
            run.raidEvent(player, RaidEvent.Died(run, m.name))
            run.dirty = true
            when (val rule = run.raid.deaths) {
                is DeathPolicy.RespawnAtCheckpoint -> {
                    policy.keepItems = true
                    run.raid.rooms[rule.checkpoint(run)]?.let { policy.respawnAt = run.at(it.entry) }
                }

                is DeathPolicy.WaitUntilRoomEnds -> {
                    m.alive = false
                    val room = (run.state as? RunState.InRoom)?.room
                    val othersAlive = run.members.values.any { it !== m && it.inside && it.online && it.alive }
                    if (othersAlive && room != null) {
                        policy.keepItems = true
                        policy.respawnAt = run.at(room.waitAt ?: room.entry)
                    } else {
                        wipe(player, run, rule.wipe, policy)
                    }
                }
            }
        }

        /** Everyone in the room is dead: eject with graves (TFA) or fail (ToB). */
        private fun wipe(dying: PlayerRef, run: RaidRun, rule: WipePolicy, policy: tp.plugin.DeathPolicyRequest) {
            val room = (run.state as? RunState.InRoom)?.room
            if (room != null) dying.events.publish(RaidEvent.RoomFailed(run, room))
            run.state = RunState.Failed("wipe")
            run.endedTick = dying.tick
            policy.respawnAt = run.raid.exit
            if (rule == WipePolicy.EjectWithGraves) {
                policy.graveAt = run.raid.graveAt
                // Members who died earlier in the room kept their items while waiting; the wipe costs them too.
                for (other in run.presentPlayers(dying)) {
                    if (other == dying) continue
                    dying.events.publish(tp.plugin.DeathItemsRequest(other, run.raid.graveAt))
                    leave(other, run, toExit = true)
                }
            }
            run.member(dying.username)?.let { it.inside = false }
            cleanup(dying, run)
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
        public fun complete(any: EntityRef, run: RaidRun) {
            if (run.state == RunState.Completed) return
            run.state = RunState.Completed
            run.endedTick = any.tick
            val snapshot =
                RunSnapshot(
                    run.raid.id,
                    run.scaling.mode,
                    run.scaling.partySize,
                    run.scaling.scaledSize,
                    run.members.mapValues { (_, m) -> Triple(m.points, m.deaths, m.inside) },
                    run.teamDeaths,
                    run.scaling.settings,
                )
            val results = run.raid.rewards(snapshot, run.rng)
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

        /** Puts [player]'s reward in the claim inventory once (also on a later login while the run lives). */
        internal fun pay(player: PlayerRef, run: RaidRun) {
            val m = run.member(player.username) ?: return
            if (m.paid) return
            val reward = m.reward ?: return
            val ok =
                reward.items.isEmpty() ||
                    player.transaction("raid reward ${run.raid.id} ${run.id.value}") {
                        reward.items.all { (obj, n) ->
                            add(run.raid.claimInv, obj, n)
                        }
                    }
            if (ok) m.paid = true
        }

        private fun records(player: PlayerRef, run: RaidRun) {
            val r = run.raid.records
            val kc = player.varp(r.kcVarp) + 1
            player.setVarp(r.kcVarp, kc)
            val ticks = run.endedTick - run.startedTick
            val key = "${r.pbKey}.${run.scaling.partySize}"
            val best = player.saved(key)
            val newBest = best == null || ticks < best
            if (newBest) player.setSaved(key, ticks)
            player.message(r.timeMessage(formatTicks(ticks), best?.let { formatTicks(it) }, newBest))
            player.message(r.kcMessage(kc))
        }

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
        internal fun close(world: WorldRef, run: RaidRun) {
            if (run.state == RunState.Closed) return
            run.instance?.destroy()
            run.state = RunState.Closed
            val raids = of(world)
            raids.runs.remove(run.id.value)
            run.party.state = PartyState.Forming
            if (run.party.members.isEmpty()) raids.dropParty(run.party)
        }

        /** Spawns NPC [type] for [run] at template tile [template]: tagged to the run and scaled (brief 3.7). */
        public fun spawn(any: EntityRef, run: RaidRun, type: Int, template: CoordGrid): NpcRef {
            val npc = any.spawnNpc(type, run.at(template))
            npc[RaidRun.NPC_RUN] = run.id.value
            run.raid.scaling(run.scaling, npc)
            return npc
        }

        private fun RaidRun.raidEvent(any: EntityRef, event: RaidEvent) {
            any.events.publish(event)
        }

        private const val COMBAT_STATS = 6
        private val RAID_ITEM_INVS = intArrayOf(tp.symbols.Invs.INV, tp.symbols.Invs.WORN)
        private const val CENTIS_PER_TICK = 60L
        private const val CENTIS_PER_SECOND = 100L
        private const val CENTIS_PER_MINUTE = 6000L
        private const val SECONDS_PER_MINUTE = 60L
    }
}
