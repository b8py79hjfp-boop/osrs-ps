package tp.tools.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tp.content.raids.ContributionRules
import tp.content.raids.DeathPolicy
import tp.content.raids.Raids
import tp.content.raids.RecordsDef
import tp.content.raids.RewardResult
import tp.content.raids.RoomDef
import tp.content.raids.RoomKind
import tp.content.raids.WipePolicy
import tp.content.raids.raid
import tp.engine.AccountBridge
import tp.engine.World
import tp.engine.entity.Player
import tp.plugin.PlayerRef
import tp.plugin.PluginContent
import tp.plugin.commands.CommandDocs
import tp.plugin.commands.Source
import tp.plugin.commands.UNKNOWN_COMMAND
import tp.plugin.commands.runCommandLine
import tp.protocol.api.ClientCommand
import tp.protocol.api.LoginRequest
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varps
import tp.util.CoordGrid
import tp.util.Rank
import tp.util.commands.CommandsConfig
import java.nio.file.Files
import java.nio.file.Path

/**
 * Commands brief §10 test 2: one player of each rank on one world. Every registered command is seen and usable exactly
 * from its minimum rank; the targeting rule; ECONOMY blocked by a live policy; confirmation; audit entries; an
 * interface run equal to the typed one; docs/commands.md up to date; and the raid `end` / `reissue` guarantees.
 */
class CommandPermissionScenario {
    private val audits = ArrayList<String>()
    private val s = Scenario()
    private val content = s.world.content as PluginContent
    private val players: Map<Rank, ScenarioPlayer>

    init {
        s.world.accounts = AuditRecorder(audits)
        // Hundreds of commands from one player: the rate limit is not under test here.
        s.world.commands = CommandsConfig(rateLimitPerSecond = BIG, rateLimitPerMinute = BIG)
        players = Rank.entries.associateWith { s.player("r_${it.name.lowercase()}", CoordGrid(X, Z), rank = it) }
        s.tick()
    }

    @Test
    fun `every command is seen and usable exactly from its minimum rank`() {
        val config = s.world.commands
        for (def in content.registry.all) {
            for (rank in Rank.entries) {
                val expected = rank.atLeast(def.rank) && def.available(config)
                assertEquals(expected, content.registry.usableBy(rank, config).contains(def), "::${def.name} seen by $rank")
            }
            if (!def.available(config)) continue
            // Below the minimum: exactly the unknown reply, so the command's existence never leaks (§2).
            Rank.entries.lastOrNull { !it.atLeast(def.rank) }?.let { below ->
                assertTrue(reply(players.getValue(below), def.name).startsWith(UNKNOWN_COMMAND), "::${def.name} hidden from $below")
            }
            // At the minimum: anything but the unknown reply (usage, a result, or a confirmation prompt).
            val at = reply(players.getValue(def.rank), def.name)
            assertFalse(at.startsWith(UNKNOWN_COMMAND), "::${def.name} usable by ${def.rank}: $at")
        }
    }

    @Test
    fun `the targeting rule holds for staff commands`() {
        val mod = players.getValue(Rank.MODERATOR)
        assertEquals("You can't use ::checkinv on r_administrator.", reply(mod, "checkinv r_administrator"))
        assertFalse(reply(mod, "checkinv r_player").startsWith("You can't"), "a moderator reaches a player")
        val dev = players.getValue(Rank.DEVELOPER)
        assertFalse(reply(dev, "checkinv r_owner").startsWith("You can't"), "a developer reaches an owner")
        val owner = players.getValue(Rank.OWNER)
        assertEquals("You can't use ::checkinv on r_developer.", reply(owner, "checkinv r_developer"))
    }

    @Test
    fun `a live world blocks ECONOMY, even for developers`() {
        s.world.commands = s.world.commands.copy(live = true, enabledImpacts = CommandsConfig.LIVE_IMPACTS)
        val dev = players.getValue(Rank.DEVELOPER)
        assertTrue(reply(dev, "item 995 100").startsWith(UNKNOWN_COMMAND))
        assertTrue(reply(dev, "spawn").startsWith(UNKNOWN_COMMAND), "the spawn menu is ECONOMY")
        assertEquals(0L, inv(dev).total(Objs.COINS))
        assertTrue(reply(dev, "home").startsWith(UNKNOWN_COMMAND), "CONVENIENCE is off on live worlds too")
        assertFalse(reply(dev, "pos").startsWith(UNKNOWN_COMMAND), "other impacts still work")
    }

    @Test
    fun `a destructive command waits for confirm`() {
        val dev = players.getValue(Rank.DEVELOPER)
        reply(dev, "item 995 100")
        assertEquals(100L, inv(dev).total(Objs.COINS))
        assertTrue("::confirm" in reply(dev, "empty"))
        assertEquals(100L, inv(dev).total(Objs.COINS), "nothing happens before ::confirm")
        reply(dev, "confirm")
        assertEquals(0L, inv(dev).total(Objs.COINS))
        assertEquals("There is nothing to confirm.", reply(dev, "confirm"))
    }

    @Test
    fun `staff commands are audited, and an interface run equals the typed one`() {
        val mod = players.getValue(Rank.MODERATOR)
        reply(mod, "checkinv r_player")
        val typed = audits.last()
        assertTrue("cmd:checkinv" in typed && "source=typed" in typed, typed)
        val outcome = PlayerRef.of(mod.player).runCommandLine("checkinv r_player", Source.INTERFACE)
        assertNotNull(outcome)
        val clicked = audits.last()
        assertEquals(typed.normalised(), clicked.normalised(), "same invocation, only the source differs")
        assertTrue("source=interface" in clicked)
        // Player commands that aren't sensitive are not audited (§4).
        val before = audits.size
        reply(players.getValue(Rank.PLAYER), "time")
        assertEquals(before, audits.size)
    }

    @Test
    fun `docs commands md is generated from the registry`() {
        val root = System.getProperty("tp.repoRoot") ?: "."
        val file = Path.of(root, "docs", "commands.md")
        assertEquals(CommandDocs.markdown(content.registry), Files.readString(file), "run ./gradlew :server:commandsDoc")
    }

    @Test
    fun `raid end ejects everyone with no item loss and pays nothing, reissue pays once`() {
        val owner = players.getValue(Rank.OWNER)
        val a = players.getValue(Rank.PLAYER)
        val b = players.getValue(Rank.MODERATOR)
        val ra = PlayerRef.of(a.player)
        val raids = Raids.of(ra)
        raids.register(FIXTURE)

        // end: both members leave by the leave rules; their own items stay, nothing is paid.
        val party = checkNotNull(raids.createParty(FIXTURE, a.player.username))
        assertTrue(party.join(b.player.username))
        assertNull(Raids.start(ra, party))
        s.tick()
        val run = checkNotNull(raids.runOf(a.player.username))
        ra.transaction("test") { add(Invs.INV, Objs.LOBSTER, 3) }
        assertTrue("::confirm" in reply(owner, "raid end ${a.player.username} test end"))
        val ended = reply(owner, "confirm")
        assertNull(raids.runOf(a.player.username), "the run is gone: $ended / ${owner.messages.takeLast(3)}")
        assertEquals(FIXTURE.exit, a.coord)
        assertEquals(FIXTURE.exit, b.coord)
        assertEquals(3L, inv(a).total(Objs.LOBSTER), "no item loss")
        assertTrue(run.members.values.all { it.reward == null }, "nothing paid")

        // reissue: a reward that could not be delivered (no room in the claim inventory or the bank) is stored, unpaid.
        fill(ra, Invs.BANK)
        ra.transaction("test fill") { (0 until inv(a).freeSlots).all { add(Invs.INV, Objs.LOBSTER, 1) } }
        val party2 = checkNotNull(raids.partyOf(a.player.username) ?: raids.createParty(FIXTURE, a.player.username))
        assertNull(Raids.start(ra, party2))
        s.tick()
        val run2 = checkNotNull(raids.runOf(a.player.username))
        Raids.complete(ra, run2)
        val member = checkNotNull(run2.member(a.player.username))
        assertNotNull(member.reward)
        assertFalse(member.paid, "no room anywhere: the reward stays stored")
        ra.transaction("make room") { remove(Invs.INV, Objs.LOBSTER, 1) }
        reply(owner, "raid reissue ${run2.id.value} ${a.player.username}")
        reply(owner, "confirm")
        assertTrue(member.paid)
        assertEquals(1L, inv(a).total(Objs.COINS), "paid from the stored result (never re-rolled)")
        reply(owner, "raid reissue ${run2.id.value} ${a.player.username}")
        assertEquals("That reward was already paid.", reply(owner, "confirm"))
        assertEquals(1L, inv(a).total(Objs.COINS), "a second reissue pays nothing")
    }

    /** Fills [invId] with one each of distinct cache objs (never coins, the reward) until it refuses more. */
    private fun fill(p: PlayerRef, invId: Int) {
        var obj = 0
        var misses = 0
        while (misses < MAX_MISSES && obj < MAX_OBJ) {
            if (obj != Objs.COINS && p.transaction("test fill") { add(invId, obj, 1) }) misses = 0 else misses++
            obj++
        }
    }

    // ---- harness

    private fun inv(p: ScenarioPlayer) = PlayerRef.of(p.player).inv(Invs.INV)

    /** Types `::line` and returns the first reply line of that tick ("" when the command answered nothing). */
    private fun reply(p: ScenarioPlayer, line: String): String {
        val before = p.messages.size
        p.input(ClientCommand.Cheat(line))
        s.tick()
        return p.messages.drop(before).firstOrNull().orEmpty()
    }

    /** An audit row without the parts that differ between two runs of one invocation. */
    private fun String.normalised(): String = replace(Regex("tick=\\d+"), "").replace(Regex("source=\\w+"), "")

    /** Records audit calls (the hub's append-only log in production). */
    private class AuditRecorder(private val rows: MutableList<String>) : AccountBridge {
        override fun authorize(request: LoginRequest, world: World) = Unit

        override fun saved(player: Player, save: String, final: Boolean) = Unit

        override fun audit(actor: String, action: String, target: String, detail: String) {
            rows += "$actor $action $target $detail"
        }
    }

    private companion object {
        const val BIG = 100_000

        /** `fill`: give up after this many objs in a row are refused (the inventory is full). */
        const val MAX_MISSES = 200
        const val MAX_OBJ = 30_000
        const val X = 3222
        const val Z = 3218

        /** A one-room test raid on a real template square (Lumbridge castle, as the raid lifecycle scenario). */
        val FIXTURE =
            raid("permfixture") {
                partySize = 1..2
                squares = listOf(50 to 50)
                exit = CoordGrid(3222, 3230)
                deaths = DeathPolicy.WaitUntilRoomEnds(WipePolicy.EjectWithGraves)
                claimInv = Invs.INV
                contribution = ContributionRules(normalisedRooms = true, deathPenalty = { _, _ -> 0.0 })
                rewards = { snap, _ -> snap.members.filterValues { it.third }.mapValues { RewardResult(listOf(Objs.COINS to 1)) } }
                records =
                    RecordsDef(
                        kcVarp = Varps.TOTAL_COMPLETED_THEFRACTUREDARCHIVE,
                        kcMessage = { "Fixture count: $it." },
                        timeMessage = { t, _, _ -> "Fixture time: $t" },
                        pbKey = "raids.permfixture.pb",
                    )
                room(RoomDef("a", RoomKind.ENCOUNTER, CoordGrid(3222, 3218)))
                slot("a")
            }
    }
}
