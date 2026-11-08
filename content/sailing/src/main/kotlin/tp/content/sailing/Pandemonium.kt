package tp.content.sailing

import tp.plugin.AttrKey
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * The Pandemonium quest (quest 174, docs/mechanics/sailing-pandemonium.md): talk to Will or Anne at Port Sarim, sail their boat
 * to the wreck, lower the salvaging hook, wash up at The Pandemonium, trade the cup for a raft with Junior Jim, ferry his cargo
 * from Port Sarim and report to Steve. Progress is the cache quest varp `SAILING_INTRO_PRIMARY` (4959): 0 not started, 50 complete
 * (the quest row's end state), the values between are this class's stages (the real ones are server-only, OPEN-PAN6).
 *
 * The shipyard cargo-hold build (S4) does not exist yet: the hold is fitted during Jim's conversation (OPEN-PAN1). Jim's
 * delivery is port task 599, given when he sends the player off; the ledger tables ([PortLedger]) call [cargoTaken] and
 * [cargoDelivered].
 */
internal class Pandemonium(
    private val state: SailingState,
    private val docking: Docking,
    private val ports: PortTasks,
) : QuestCargo {
    private val talk = PandemoniumDialogue(this)
    private val jim = PandemoniumJim(this)

    fun ContentScope.register() {
        for (npc in listOf(Npcs.SAILING_INTRO_WILL_SARIM, Npcs.SAILING_INTRO_ANNE_SARIM)) {
            onOpNpc(npc, OP_TALK) { with(talk) { sarim() } }
        }
        for (npc in listOf(Npcs.SAILING_INTRO_WILL_BOAT, Npcs.SAILING_INTRO_ANNE_BOAT)) {
            onOpNpc(npc, OP_TALK) { with(talk) { aboard() } }
        }
        for (npc in Npcs.STEVE_BEANIE..Npcs.STEVE_BEANIE_2OPS) onOpNpc(npc, OP_TALK) { with(talk) { steveTalk() } }
        for (npc in Npcs.SAILING_INTRO_RIBS..Npcs.SAILING_INTRO_RIBS_VIS) onOpNpc(npc, OP_TALK) { with(talk) { ribsTalk() } }
        for (npc in Npcs.JUNIOR_JIM..Npcs.JUNIOR_JIM_4OPS) onOpNpc(npc, OP_TALK) { with(jim) { jimTalk() } }
        for (loc in Locs.SAILING_INTRO_SALVAGING_HOOK..Locs.SAILING_INTRO_SALVAGING_HOOK_OPS) onOpLoc(loc, OP_HOOK) { deployHook() }
        onCommand("pandemonium") { args -> stageCommand(this, args) }
        onLogout { clearNpcs(this) }
    }

    fun stage(p: PlayerRef): Int = p.varp(Varps.SAILING_INTRO_PRIMARY)

    fun setStage(p: PlayerRef, stage: Int) {
        p.setVarp(Varps.SAILING_INTRO_PRIMARY, stage)
        if (stage == ERRAND) ports.assignQuest(p, QUEST_TASK)
    }

    // ---- the intro boat

    /** Takes the player to Will and Anne's boat at the Port Sarim mooring and starts the briefing the first time. */
    fun boardIntroBoat(p: PlayerRef, firstTime: Boolean) {
        removeIntro(p)
        val site = state.sites.of(PORT_SARIM_DOCK)
        val pose = site?.sea?.let { Pose(it, site.angle) } ?: return p.message("The boat isn't ready yet.")
        val owned = OwnedBoat(BoatTemplates.INTRO, BoatSave.AT_SEA, 0, 0, 0, 0, 0, 0)
        val boat = state.boats.spawn(p, p.username.lowercase(), INTRO_SLOT, owned, pose)
        if (boat == null) return p.message("The boat isn't ready yet.")
        p[WILL] = p.spawnNpc(Npcs.SAILING_INTRO_WILL_BOAT, boat.entry.translate(-1, 1), 0)
        p[ANNE] = p.spawnNpc(Npcs.SAILING_INTRO_ANNE_BOAT, boat.entry.translate(-1, 2), 0)
        docking.board(p, boat)
        if (stage(p) < ON_BOAT) setStage(p, ON_BOAT)
        p.timer(WRECK_WATCH, WATCH_TICKS, soft = true) { watch(this) }
        if (firstTime) p.startScript { with(talk) { briefing() } }
    }

    /** "Actually, I want to go back to Port Sarim": ashore at Port Sarim, boat removed; the stage is kept. */
    fun returnToSarim(p: PlayerRef) {
        docking.ashoreAt(p, PORT_SARIM_DOCK)
        removeIntro(p)
    }

    /** Removes the player's intro boat (if any) with Will and Anne; callers move everyone off it first. */
    private fun removeIntro(p: PlayerRef) {
        p.stopTimer(WRECK_WATCH)
        clearNpcs(p)
        val boat = ActiveBoats.ownedBy(p, p.username.lowercase())?.takeIf { it.slot == INTRO_SLOT } ?: return
        state.boats.despawn(p, boat)
    }

    private fun clearNpcs(p: PlayerRef) {
        p[WILL]?.despawn()
        p[ANNE]?.despawn()
        p[WILL] = null
        p[ANNE] = null
    }

    /** Soft timer while sailing to the wreck: arriving within [WRECK_RANGE] of it ends the leg. */
    private fun watch(p: PlayerRef) {
        val boat = ActiveBoats.ownedBy(p, p.username.lowercase())?.takeIf { it.slot == INTRO_SLOT }
        if (boat == null || stage(p) != BRIEFED) return p.stopTimer(WRECK_WATCH)
        if (boat.ref.rootCoord.chebyshev(WRECK) > WRECK_RANGE) return
        p.stopTimer(WRECK_WATCH)
        state.boats
            .motion(p)
            ?.motionOf(boat.ref)
            ?.sails = BoatMotion.Sails.DOWN
        setStage(p, AT_WRECK)
        p.setVarbit(Varbits.SAILING_INTRO_REACHED_WRECK, 1)
        p.startScript { with(talk) { reachedWreck() } }
    }

    /** The intro boat's salvaging hook (the static facility of boat type 3). */
    private suspend fun PlayerScript.deployHook() {
        val p = player
        if (ActiveBoats.of(p)?.slot != INTRO_SLOT || stage(p) > AT_WRECK) return
        if (stage(p) < AT_WRECK) return with(talk) { hookTooEarly() }
        if (!with(talk) { hook() }) return
        if (!p.transaction("pandemonium cup") { add(Invs.INV, Objs.SAILING_INTRO_CUP, 1) }) return
        // Anne pushes the player overboard (OPEN-PAN2: the camera work and the push animation are not done).
        setStage(p, WASHED_UP)
        p.setVarbit(Varbits.SAILING_INTRO_REACHED_WRECK, 0)
        docking.ashoreAt(p, PANDEMONIUM_DOCK)
        removeIntro(p)
        with(talk) { overboard() }
    }

    // ---- Junior Jim and the rewards

    fun takeCup(p: PlayerRef): Boolean = p.transaction("pandemonium cup") { remove(Invs.INV, Objs.SAILING_INTRO_CUP, 1) }

    /** The raft: boat slot 0, wooden parts, moored at The Pandemonium. */
    fun giveRaft(p: PlayerRef) {
        if (BoatSave.get(p, 0) != null) return
        val fresh = OwnedBoat(BoatTemplates.RAFT, PANDEMONIUM_DOCK, 0, 0, 0, 0, 0, p.random(NAME_RANGE))
        BoatSave.set(p, 0, fresh.copy(hp = state.boats.maxHp(fresh)))
        p.setSaved(BoatSave.LAST_BOAT_KEY, 0L)
        p.setSaved(BoatSave.LAST_DOCK_KEY, PANDEMONIUM_DOCK.toLong())
    }

    /** Jim's captain's log; false when the backpack is full. OPEN-PAN4: initial log (31985) or the full log (31986) is unknown. */
    fun giveLog(p: PlayerRef): Boolean =
        p.inv(Invs.INV).total(Objs.SAILING_LOG) > 0 || p.transaction("pandemonium log") { add(Invs.INV, Objs.SAILING_LOG, 1) }

    /** Two repair kits and a spyglass for the cargo hold of the raft (Jim: "chucked some useful bits and bobs in your cargo hold"). */
    fun giveJimsGifts(p: PlayerRef) {
        if (p.varbit(Varbits.SAILING_INTRO_RECEIVED_REPAIRKITS) == 1) return
        val hold = Cargo.holdInv(RAFT_SLOT)
        val stored =
            p.transaction("pandemonium gifts") {
                add(hold, Objs.BOAT_REPAIR_KIT, REPAIR_KITS) && add(hold, Objs.SAILING_CHARTING_SPYGLASS, 1)
            }
        if (!stored) {
            p.transaction("pandemonium gifts") {
                addOrDrop(Invs.INV, Objs.BOAT_REPAIR_KIT, REPAIR_KITS) && addOrDrop(Invs.INV, Objs.SAILING_CHARTING_SPYGLASS, 1)
            }
        }
        p.setVarbit(Varbits.SAILING_INTRO_RECEIVED_REPAIRKITS, 1)
    }

    /** The player picked Jim's crate up at the Port Sarim ledger table (port task [QUEST_TASK]). */
    override fun cargoTaken(p: PlayerRef, taskId: Int) {
        if (taskId == QUEST_TASK && stage(p) == ERRAND) setStage(p, CARGO_TAKEN)
    }

    /** The crate was delivered on the Pandemonium ledger table. Its 100 Sailing XP is the task's own (Wiki: task 599). */
    override fun cargoDelivered(p: PlayerRef, taskId: Int) {
        if (taskId == QUEST_TASK && stage(p) == CARGO_TAKEN) setStage(p, CARGO_DELIVERED)
    }

    /** Steve's finale: the quest is complete. QP and the reward scroll are OPEN-PAN7 (no quest point store yet). */
    fun complete(p: PlayerRef) {
        if (stage(p) == COMPLETE) return
        setStage(p, COMPLETE)
        p.addXp(Stats.SAILING, QUEST_XP)
        p.transaction("pandemonium reward") { addOrDrop(Invs.INV, Objs.SAWMILL_COUPON, COUPONS) }
        p.message("Congratulations! You have completed the Pandemonium quest!")
    }

    /** Old Grog for the finale, next to the player. */
    fun spawnGrog(p: PlayerRef): NpcRef = p.spawnNpc(Npcs.SAILING_INTRO_GROG, p.coord.translate(1, 0), 0)

    /** `::pandemonium <stage>` (administrators): sets the quest stage, for testing the later steps. */
    private fun stageCommand(p: PlayerRef, args: List<String>) {
        if (!p.rank.atLeast(ADMIN)) return
        val value = args.firstOrNull()?.toIntOrNull() ?: return p.message("Pandemonium stage: ${stage(p)}")
        setStage(p, value)
        p.message("Pandemonium stage set to $value.")
    }

    companion object {
        const val NOT_STARTED = 0
        const val STARTED = 1
        const val OFFERED = 2
        const val ON_BOAT = 3
        const val BRIEFED = 4
        const val AT_WRECK = 5
        const val WASHED_UP = 6
        const val STEVE_TOLD = 7
        const val RIBS_TOLD = 8
        const val JIM_SENT = 9
        const val HOLD_BUILT = 10
        const val ERRAND = 11
        const val CARGO_TAKEN = 12
        const val CARGO_DELIVERED = 13
        const val JIM_REPORTED = 14

        /** The quest row's end state (db `Quest` 206, column ENDSTATE). */
        const val COMPLETE = 50

        private val WILL = AttrKey<NpcRef>("pandemonium.will")
        private val ANNE = AttrKey<NpcRef>("pandemonium.anne")
        private val WRECK_WATCH = TimerId("pandemonium_wreck_watch")

        /** Talk-to is op 1 of these NPCs. */
        private const val OP_TALK = 1
        private const val OP_HOOK = 1
        private val ADMIN = tp.util.Rank.ADMINISTRATOR

        /** Dock ids (db 194): Port Sarim and The Pandemonium. */
        private const val PORT_SARIM_DOCK = 0
        private const val PANDEMONIUM_DOCK = 1

        /** Boat slot value of the temporary Will and Anne's boat in [ActiveBoat.slot]. */
        const val INTRO_SLOT = -1

        /** The pirate wreck east of the Karamja Shipyard (Wiki Pandemonium map square, 3031,3035, radius 3). */
        private val WRECK = CoordGrid(3031, 3035, 0)

        /** Tiles around [WRECK] that count as being at it: the Wiki square radius 3 plus the boat's half length (OPEN-PAN2). */
        private const val WRECK_RANGE = 5
        private const val WATCH_TICKS = 3
        private const val NAME_RANGE = 1 shl 24

        /** Port task id of Jim's delivery (cache `Port_Task` 599 "Pandemonium ship part delivery"). */
        const val QUEST_TASK = 599

        /** Boat slot of the raft the quest gives. */
        private const val RAFT_SLOT = 0

        /** Rewards (Wiki Pandemonium): 300 Sailing XP, 25 sawmill coupons (wood plank), 2 repair kits (the 100 delivery XP is a task's). */
        private const val QUEST_XP = 300.0
        private const val COUPONS = 25
        private const val REPAIR_KITS = 2
    }
}
