package tp.tools.scenario

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tp.engine.entity.Player
import tp.hub.Hub
import tp.hub.HubConfig
import tp.hub.MemoryStore
import tp.protocol.api.ClientCommand
import tp.protocol.api.ClientSession
import tp.protocol.api.LoginDetails
import tp.protocol.api.LoginRejection
import tp.protocol.api.LoginRequest
import tp.server.HubAccountBridge
import tp.server.WorldLinks
import tp.util.Rank
import tp.util.RecordType
import tp.util.commands.JailArea

/**
 * Commands brief §10 test 3: two worlds in one JVM and the hub. A moderator on world 1 mutes, jails, kicks and bans a
 * player on world 2, and an offline player (applied at their next login); then reverses each and checks the history.
 */
class CrossWorldModerationScenario {
    private val store = MemoryStore()
    private val hub = Hub(store, HubConfig(autoRegister = true, bootstrap = mapOf("mod" to Rank.ADMINISTRATOR)))
    private val w1 = Scenario(worldId = 1)
    private val w2 = Scenario(worldId = 2)
    private val links = listOf(link(w1), link(w2))

    @AfterEach
    fun close() {
        links.forEach { it.close() }
        hub.close()
    }

    @Test
    fun `sanctions reach another world and offline accounts, and are reversed in the history`() {
        settle()
        val mod = login(w1, "mod")
        val bob = login(w2, "bob")
        assertEquals(Rank.ADMINISTRATOR, mod.player(w1).rank)

        // Mute across worlds: bob's chat is refused with the reason and the expiry.
        command(mod, "mute bob 1h spam")
        waitFor { bob.player(w2).sanctions.muted(System.currentTimeMillis()) }
        bob.session.input(ClientCommand.PublicChat("hello", 0, 0, 0))
        waitFor { bob.session.messages.any { "muted" in it && "(spam)" in it } }

        // Jail across worlds: moved into the jail area, and pulled back after a teleport out.
        command(mod, "jail bob 1h scamming")
        waitFor { inJail(bob.player(w2)) }
        w2.world.teleport(bob.player(w2), w2.world.config.spawn)
        waitFor { inJail(bob.player(w2)) }

        // Reverse both while online.
        command(mod, "unmute bob")
        command(mod, "unjail bob")
        waitFor { !bob.player(w2).sanctions.muted(now()) && !inJail(bob.player(w2)) }

        // Kick across worlds.
        command(mod, "kick bob testing")
        waitFor { w2.world.playerByAccount(bob.accountId(w2)) == null }

        // Offline: mute, jail and ban carol (never online), applied at her next login.
        check(kotlinx.coroutines.runBlocking { hub.accounts.register("carol", "secret1", "127.0.0.1") } == null)
        val carolId = checkNotNull(store.account("carol")).id
        command(mod, "mute carol 1d offline mute")
        command(mod, "jail carol 1d offline jail")
        waitFor { store.records(carolId).count { it.active(now()) } == 2 }
        val carol = login(w2, "carol", "secret1")
        assertTrue(carol.player(w2).sanctions.muted(now()))
        waitFor { inJail(carol.player(w2)) }

        // Ban across worlds: kicked, and the next login is refused; unban lets her back in.
        command(mod, "ban carol 1d botting")
        waitFor { w2.world.playerByAccount(carolId) == null }
        val refused = TestLogin("carol", "secret1")
        authorize(w2, refused)
        waitFor { refused.rejected != null }
        assertEquals(LoginRejection.BANNED, refused.rejected)
        command(mod, "unban carol")
        command(mod, "unmute carol")
        command(mod, "unjail carol")
        waitFor { store.records(carolId).none { it.active(now()) } }
        val back = login(w2, "carol", "secret1")
        assertFalse(back.player(w2).sanctions.muted(now()))

        // The history keeps every record, each lasting one lifted by the moderator.
        val history = store.records(carolId)
        assertEquals(setOf(RecordType.MUTE, RecordType.JAIL, RecordType.BAN), history.map { it.type }.toSet())
        assertTrue(history.all { it.liftedBy == "mod" && it.liftedAt > 0 })
        val bobHistory = store.records(bob.accountId(w2).takeIf { it != 0L } ?: checkNotNull(store.account("bob")).id)
        assertEquals(listOf(RecordType.KICK, RecordType.JAIL, RecordType.MUTE), bobHistory.map { it.type })
        assertTrue(bobHistory.filter { it.type.lasting }.all { it.liftedBy == "mod" })
    }

    // ---- harness

    private fun link(s: Scenario): WorldLinks {
        s.world.accounts = HubAccountBridge(hub, s.world.config.id, s.world)
        return WorldLinks(s.world, hub, "127.0.0.1", 0).start()
    }

    private fun now() = System.currentTimeMillis()

    private fun inJail(p: Player): Boolean = JailArea.DEFAULT.contains(p.coord.x, p.coord.z, p.coord.level)

    /** Lets the worlds' hub subscriptions start. */
    private fun settle() = repeat(SETTLE_TICKS) { tickBoth() }

    private fun login(s: Scenario, name: String, password: String = "secret1"): TestLogin {
        val request = TestLogin(name, password)
        authorize(s, request)
        waitFor { request.rejected != null || s.world.playerByName(name) != null }
        check(request.rejected == null) { "$name refused: ${request.rejected}" }
        return request
    }

    private fun authorize(s: Scenario, request: TestLogin) {
        checkNotNull(s.world.accounts).authorize(request, s.world)
    }

    /** Types `::line` for [who] on world 1 and waits for the hub's answer. */
    private fun command(who: TestLogin, line: String) {
        val before = who.session.messages.size
        who.session.input(ClientCommand.Cheat(line))
        waitFor { who.session.messages.size > before }
        val reply = who.session.messages.last()
        check("." in reply && !reply.startsWith("There is no") && !reply.startsWith("You can't")) { "::$line -> $reply" }
    }

    private fun tickBoth() {
        w1.tick()
        w2.tick()
    }

    /** Ticks both worlds until [condition] holds (hub calls are asynchronous). */
    private fun waitFor(condition: () -> Boolean) {
        repeat(MAX_WAIT_TICKS) {
            if (condition()) return
            tickBoth()
            Thread.sleep(WAIT_SLEEP_MS)
        }
        check(condition()) { "timed out" }
    }

    private fun tp.engine.World.playerByName(name: String): Player? {
        var found: Player? = null
        forEachPlayer { if (it.username.equals(name, ignoreCase = true)) found = it }
        return found
    }

    private fun TestLogin.player(s: Scenario): Player =
        checkNotNull(s.world.playerByName(username)) {
            "$username is not on world ${s.world.config.id}"
        }

    private fun TestLogin.accountId(s: Scenario): Long = s.world.playerByName(username)?.accountId ?: 0L

    /** A network login without the network: the hub authorizes it and the world admits it in its post-tick. */
    private class TestLogin(override val username: String, override val password: String) : LoginRequest {
        override val crcValid: Boolean = true
        override val address: String = "127.0.0.1"
        val session = HeadlessSession()

        @Volatile var rejected: LoginRejection? = null

        override fun accept(slot: Int, details: LoginDetails): ClientSession = session

        override fun reject(reason: LoginRejection) {
            rejected = reason
        }
    }

    private companion object {
        const val SETTLE_TICKS = 5
        const val MAX_WAIT_TICKS = 400
        const val WAIT_SLEEP_MS = 5L
    }
}
