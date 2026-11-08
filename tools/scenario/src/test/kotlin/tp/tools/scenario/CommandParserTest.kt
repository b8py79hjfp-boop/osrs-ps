package tp.tools.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.PluginContent
import tp.plugin.commands.Amounts
import tp.plugin.commands.Arg
import tp.plugin.commands.Category
import tp.plugin.commands.CmdDuration
import tp.plugin.commands.CommandResult
import tp.plugin.commands.Durations
import tp.plugin.commands.NameKind
import tp.plugin.commands.ParseContext
import tp.plugin.commands.ParseResult
import tp.plugin.commands.PlayerTarget
import tp.plugin.commands.Tokenizer
import tp.util.CoordGrid
import tp.util.Rank
import tp.util.commands.CommandsConfig
import tp.util.commands.Impact

/**
 * Commands brief §10 test 1, table-driven: quoting and names with spaces and underscores, every typed argument
 * (durations, k/m/b amounts, every coordinate form, items by id and name, ambiguous players), and "did you mean" that
 * never suggests a command above the player's rank. Player and cache lookups run against a real headless world.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommandParserTest {
    private lateinit var s: Scenario
    private lateinit var ctx: ParseContext

    @BeforeAll
    fun world() {
        s = Scenario(seed = 1L)
        for (name in listOf("Big Bob", "Big Bobby", "Alice", "Zed")) s.player(name)
        s.tick()
        val viewer = PlayerRef.of(requireNotNull(s.world.player(1)))
        ctx = ParseContext.of(viewer)
    }

    @Test
    fun `quotes group words and underscores stay in the token`() {
        val table =
            listOf(
                "mute \"Big Bob\" 2h spamming" to listOf("mute", "Big Bob", "2h", "spamming"),
                "mute Big_Bob 1h spam" to listOf("mute", "Big_Bob", "1h", "spam"),
                "  yell   hello   world " to listOf("yell", "hello", "world"),
                "note \"\" empty" to listOf("note", "", "empty"),
            )
        for ((line, tokens) in table) assertEquals(tokens, Tokenizer.split(line), line)
    }

    @Test
    fun `amounts with k m b suffixes`() {
        val table =
            listOf(
                "10" to 10L,
                "10k" to 10_000L,
                "1.5m" to 1_500_000L,
                "2b" to 2_000_000_000L,
                "1,000" to 1_000L,
                "0.5k" to 500L,
                "abc" to null,
                "k" to null,
                "9999999999999b" to null,
            )
        for ((raw, want) in table) assertEquals(want, Amounts.parse(raw), raw)
    }

    @Test
    fun `durations`() {
        val table =
            listOf(
                "30s" to CmdDuration(30_000),
                "10m" to CmdDuration(600_000),
                "2h" to CmdDuration(7_200_000),
                "7d" to CmdDuration(604_800_000),
                "perm" to CmdDuration.PERMANENT,
                "5" to null,
                "-1h" to null,
                "3w" to null,
            )
        for ((raw, want) in table) assertEquals(want, Durations.parse(raw), raw)
    }

    @Test
    fun `every coordinate form`() {
        val arg = Arg.CoordArg("tile", optional = false)
        val table =
            listOf(
                listOf("3222", "3218") to CoordGrid(3222, 3218, 0),
                listOf("3222", "3218", "1") to CoordGrid(3222, 3218, 1),
                listOf("3222,3218,2") to CoordGrid(3222, 3218, 2),
                listOf("0,50,50,22,18") to CoordGrid(50 * 64 + 22, 50 * 64 + 18, 0),
            )
        for ((tokens, want) in table) assertEquals(want, ok(arg.parseFor(tokens)), tokens.joinToString(" "))
        assertTrue(arg.parseFor(listOf("3222")) is ParseResult.Err, "x without y")
        assertTrue(arg.parseFor(listOf("1,2,3,4")) is ParseResult.Err, "four parts")
        assertTrue(arg.parseFor(listOf("99999", "1")) is ParseResult.Err, "off the map")
    }

    @Test
    fun `items by id and name, noted, and did you mean`() {
        val arg = Arg.ObjArg("item", optional = false)
        assertEquals(4151, ok(arg.parseFor(listOf("4151"))).id, "whip by id")
        assertEquals(4151, ok(arg.parseFor(listOf("abyssal_whip"))).id, "by name, underscores")
        assertEquals(4151, ok(arg.parseFor(listOf("Abyssal whip"))).id, "by name, quoted, any case")
        val noted = ok(arg.parseFor(listOf("noted:4151")))
        assertTrue(noted.noted && noted.id != 4151, "noted whip")
        val miss = arg.parseFor(listOf("abyssal_whp")) as ParseResult.Err
        assertTrue("Did you mean" in miss.message && "abyssal whip" in miss.message, miss.message)
        assertTrue(arg.parseFor(listOf("99999999")) is ParseResult.Err, "unknown id")
        assertTrue(ctx.names(NameKind.NPC).isNotEmpty() && ctx.names(NameKind.LOC).isNotEmpty())
    }

    @Test
    fun `players by name, partial and ambiguous`() {
        val online = Arg.PlayerArg("target", optional = false, allowOffline = false)
        assertEquals("Big Bob", (ok(online.parseFor(listOf("big_bob"))) as PlayerTarget.Online).name, "exact wins over prefix")
        assertEquals("Alice", ok(online.parseFor(listOf("ali"))).name, "unique prefix")
        val ambiguous = online.parseFor(listOf("big")) as ParseResult.Err
        assertTrue("Big Bob" in ambiguous.message && "Big Bobby" in ambiguous.message, ambiguous.message)
        assertTrue(online.parseFor(listOf("nobody")) is ParseResult.Err, "offline not allowed")
        val offline = Arg.PlayerArg("target", optional = false, allowOffline = true)
        assertTrue(ok(offline.parseFor(listOf("Far_Away"))) is PlayerTarget.Offline, "offline allowed")
    }

    @Test
    fun `other typed arguments`() {
        assertEquals(true, ok(Arg.BoolArg("b", false).parseFor(listOf("yes"))))
        assertEquals(false, ok(Arg.BoolArg("b", false).parseFor(listOf("off"))))
        assertEquals(Rank.ADMINISTRATOR, ok(Arg.RankArg("r", false).parseFor(listOf("administrator"))))
        assertEquals(Rank.ADMINISTRATOR, ok(Arg.RankArg("r", false).parseFor(listOf("admin"))))
        assertEquals(2, ok(Arg.SkillArg("s", false).parseFor(listOf("str"))), "strength by prefix")
        assertEquals(6, ok(Arg.SkillArg("s", false).parseFor(listOf("6"))), "magic by id")
        assertEquals("all", ok(Arg.ChoiceArg("c", false, listOf("world", "all")).parseFor(listOf("ALL"))))
        assertEquals("a b c", ok(Arg.Text("t", false).parseFor(listOf("a", "b", "c"))))
        assertEquals(10_000, ok(Arg.IntArg("n", false, 0, 100_000).parseFor(listOf("10k"))))
        assertTrue(Arg.IntArg("n", false, 0, 100).parseFor(listOf("1k")) is ParseResult.Err, "range")
    }

    @Test
    fun `did you mean never suggests a command above the viewer's rank`() {
        val content = PluginContent.of(listOf(Fixtures))
        val config = CommandsConfig()
        val player = content.registry.suggest("banx", Rank.PLAYER, config)
        val admin = content.registry.suggest("banx", Rank.ADMINISTRATOR, config)
        assertTrue("bank" in player && "ban" !in player, "player: $player")
        assertTrue("ban" in admin && "bank" in admin, "admin: $admin")
        assertTrue(content.registry.suggest("ban", Rank.MODERATOR, config).none { it == "ban" }, "moderator does not see ::ban")
    }

    private fun <T> Arg<T>.parseFor(tokens: List<String>): ParseResult<T> = parseAt(tokens, ctx)

    private fun <T> ok(r: ParseResult<T>): T = (r as? ParseResult.Ok<T>)?.value ?: error("not parsed: ${(r as ParseResult.Err).message}")

    /** Two commands a letter apart at different ranks. */
    private object Fixtures : ContentPlugin {
        override fun ContentScope.register() {
            command("bank", Rank.PLAYER, Category.GENERAL, Impact.INFO) {
                description("Test fixture.")
                example("::bank")
                signature { CommandResult.Done() }
            }
            command("ban", Rank.ADMINISTRATOR, Category.MODERATION, Impact.MODERATION) {
                description("Test fixture.")
                example("::ban x")
                signature { CommandResult.Done() }
            }
        }
    }
}
