package tp.content.raids

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.commands.Arg
import tp.plugin.commands.Category
import tp.plugin.commands.CommandContext
import tp.plugin.commands.CommandResult
import tp.plugin.commands.PlayerTarget
import tp.plugin.commands.Tags
import tp.plugin.commands.commandsConfig
import tp.plugin.commands.showList
import tp.plugin.savedKeys
import tp.util.CoordGrid
import tp.util.Rank
import tp.util.commands.Impact

/**
 * The raid tables of commands brief §6. The owner subcommands are live-safe: they inspect, end, remove or pay out what
 * a run already earned, at most once. The developer subcommands (all ECONOMY except `layout` and `points` without `set`)
 * need a world that allows ECONOMY. A run is named by its id or by any player in it.
 */
public class RaidCommands : ContentPlugin {
    override fun ContentScope.register() {
        command("raids", Rank.OWNER, Category.RAIDS, Impact.OPERATIONS) {
            description("Lists this world's active runs: raid, mode, party, room, elapsed time and run id.")
            example("::raids")
            signature { ctx -> list(ctx.player) }
        }
        command("raid", Rank.OWNER, Category.RAIDS, Impact.OPERATIONS) {
            description("Raid tools: info, parties, watch, leave, kick, end, reward, reissue, waivefee, kc, pb, disable, enable, stats.")
            example("::raid info Big_Bob", "::raid end 12 instance bugged", "::raid reissue 12 Big_Bob")
            owner(this)
            developer(this)
        }
    }

    // ---- owner subcommands

    private fun owner(b: tp.plugin.commands.CommandBuilder) =
        with(b) {
            val info = lit("info")
            val runArg = text("run|player")
            signature(info, runArg) { ctx -> info(ctx.player, ctx[runArg]) }
            signature(lit("parties")) { ctx -> parties(ctx.player) }
            val watch = lit("watch")
            val watchRun = text("run|player")
            signature(watch, watchRun) { ctx -> watch(ctx.player, ctx[watchRun]) }
            signature(lit("leave")) { ctx -> leave(ctx.player) }
            val kick = lit("kick")
            val kicked = player("player")
            confirmedSignature(kick, kicked, text("reason", optional = true)) { ctx -> kick(ctx.player, ctx[kicked]) }
            val end = lit("end")
            val endRun = text("run|player reason")
            confirmedSignature(end, endRun) { ctx -> end(ctx.player, ctx[endRun]) }
            val reward = lit("reward")
            val rewardRun = text("run [player]")
            signature(reward, rewardRun) { ctx -> reward(ctx.player, ctx[rewardRun]) }
            val reissue = lit("reissue")
            val reissueRun = long("run", min = 0)
            val reissueWho = player("player")
            confirmedSignature(reissue, reissueRun, reissueWho) { ctx -> reissue(ctx.player, ctx[reissueRun], ctx[reissueWho]) }
            val waive = lit("waivefee")
            val waiveWho = player("player")
            confirmedSignature(waive, waiveWho) { ctx -> waive(ctx[waiveWho]) }
            val kc = lit("kc")
            val kcWho = player("player")
            signature(kc, kcWho, text("raid", optional = true)) { ctx -> kc(ctx.player, ctx[kcWho]) }
            val pb = lit("pb")
            val pbRemove = lit("remove")
            val pbRemoveRest = text("player raid mode size")
            confirmedSignature(pb, pbRemove, pbRemoveRest) { ctx -> pbRemove(ctx.player, listOf("remove") + words(ctx[pbRemoveRest])) }
            val pbWho = text("player")
            signature(pb, pbWho) { ctx -> pb(ctx.player, ctx[pbWho]) }
            val toggle = choice("disable|enable", "disable", "enable")
            val toggleRaid = text("raid")
            signature(toggle, toggleRaid) { ctx -> toggle(ctx.player, ctx[toggle] == "enable", ctx[toggleRaid]) }
            signature(lit("stats")) { ctx -> stats(ctx.player) }
        }

    private fun list(p: PlayerRef): CommandResult {
        val runs = Raids.of(p).activeRuns()
        if (runs.isEmpty()) return CommandResult.Done("No active runs on this world.")
        p.showList(
            "Active runs",
            runs.map { r ->
                "#${r.id.value} ${r.raid.id} ${r.scaling.mode} ${r.members.keys.joinToString(", ")} | ${room(r)} | ${elapsed(p, r)}"
            },
        )
        return CommandResult.Done()
    }

    private fun info(p: PlayerRef, which: String): CommandResult {
        val r = find(p, which) ?: return CommandResult.Failed("No run '$which'.")
        val rows =
            buildList {
                add("Run #${r.id.value}: ${r.raid.name} (${r.scaling.mode}), ${room(r)}, ${elapsed(p, r)}.")
                val settings = r.scaling.settings + r.extraSettings
                add("Scaling: party ${r.scaling.partySize}, scaled as ${r.scaling.scaledSize}; settings $settings.")
                add("Team deaths: ${r.teamDeaths}. Rooms: ${r.roomsTaken.joinToString(", ").ifEmpty { "-" }}.")
                for ((name, m) in r.members) {
                    add(
                        "$name: ${if (m.inside) "inside" else "out"}, deaths ${m.deaths}, points ${"%.1f".format(
                            m.points,
                        )}${if (m.alive) "" else ", dead"}",
                    )
                }
                if (r.spectators.isNotEmpty()) add("Spectators: ${r.spectators.joinToString(", ")}.")
            }
        p.showList("Run #${r.id.value}", rows)
        return CommandResult.Done()
    }

    private fun parties(p: PlayerRef): CommandResult {
        val raids = Raids.of(p)
        val rows =
            raids.allDefinitions().flatMap { d ->
                raids.parties(d.id).map { "${d.id}: ${it.members.joinToString(", ")} (${it.state})" }
            }
        if (rows.isEmpty()) return CommandResult.Done("No parties are forming.")
        p.showList("Parties", rows)
        return CommandResult.Done()
    }

    /** §6: a hidden observer inside the run (not in the party, never targeted, scaled, counted or rewarded). */
    private fun watch(p: PlayerRef, which: String): CommandResult {
        val r = find(p, which) ?: return CommandResult.Failed("No run '$which'.")
        val room = (r.state as? RunState.InRoom)?.room ?: return CommandResult.Failed("That run is not in a room right now.")
        if (Raids.of(p).runOf(p.username) != null) return CommandResult.Failed("Leave your own raid first.")
        if (p[WATCH_FROM] == null) p[WATCH_FROM] = p.coord
        p[WATCHING] = r.id.value
        r.spectators += p.username
        p.observer = true
        p.teleport(Raids.entryOf(r, room, p))
        return CommandResult.Done("Watching run #${r.id.value}. ::raid leave to stop.")
    }

    private fun leave(p: PlayerRef): CommandResult {
        val watching = p[WATCHING]
        if (watching != null) {
            Raids
                .of(p)
                .run(watching)
                ?.spectators
                ?.remove(p.username)
            p[WATCHING] = null
            p.observer = false
            p[WATCH_FROM]?.let(p::teleport)
            p[WATCH_FROM] = null
            return CommandResult.Done("Stopped watching.")
        }
        val run = Raids.of(p).runOf(p.username) ?: return CommandResult.Failed("You are not in or watching a raid.")
        Raids.leave(p, run)
        return CommandResult.Done("You left the raid.")
    }

    private fun kick(p: PlayerRef, who: PlayerTarget): CommandResult {
        val target = online(who) ?: return CommandResult.Failed("${who.name} is not on this world.")
        val run = Raids.of(p).runOf(target.username) ?: return CommandResult.Failed("${target.username} is not in a raid.")
        // Leave rules: no item loss, raid-only items removed; a member who left is never rewarded (RC5).
        Raids.leave(target, run)
        target.message("A staff member removed you from the raid.")
        return CommandResult.Done("Removed ${target.username} from run #${run.id.value}.")
    }

    private fun end(p: PlayerRef, args: String): CommandResult {
        val words = args.trim().split(' ')
        val r = find(p, words.first()) ?: return CommandResult.Failed("No run '${words.first()}'.")
        val reason = words.drop(1).joinToString(" ").ifBlank { return CommandResult.Failed("Usage: ::raid end <run|player> <reason…>") }
        for (m in r.presentPlayers(p)) m.message("The raid was ended by staff: ${Tags.escape(reason)}")
        // Everyone leaves by the leave rules (no item loss, nothing paid), then the instance goes.
        Raids.reset(p, r)
        return CommandResult.Done("Ended run #${r.id.value}.")
    }

    private fun reward(p: PlayerRef, args: String): CommandResult {
        val words = args.trim().split(' ')
        val id = words.first().toLongOrNull() ?: return CommandResult.Failed("Usage: ::raid reward <run> [player]")
        val r = Raids.of(p).run(id) ?: return CommandResult.Failed("No run #$id.")
        val only = words.drop(1).joinToString(" ").takeIf { it.isNotBlank() }
        val rows =
            buildList {
                add("Seed ${r.rng.seed}; ${r.rng.rolls.size} rolls; state ${r.state::class.simpleName}.")
                for ((name, m) in r.members) {
                    if (only != null && !name.equals(only, ignoreCase = true)) continue
                    val items = m.reward?.items?.joinToString(", ") { (obj, n) -> "${p.objName(obj)} x$n" } ?: "none"
                    add("$name: $items${if (m.reward?.unique == true) " (unique)" else ""}, ${if (m.paid) "paid" else "unpaid"}")
                }
                addAll(r.rng.rolls.map { "roll: $it" })
            }
        p.showList("Rewards of run #$id", rows)
        return CommandResult.Done()
    }

    private fun reissue(p: PlayerRef, id: Long, who: PlayerTarget): CommandResult {
        val target = online(who) ?: return CommandResult.Failed("${who.name} is not on this world.")
        val r = Raids.of(p).run(id) ?: return CommandResult.Failed("No run #$id.")
        Raids.reissue(target, r)?.let { return CommandResult.Failed(it) }
        return CommandResult.Done("Reissued ${target.username}'s reward from run #$id.")
    }

    private fun waive(who: PlayerTarget): CommandResult {
        val target = online(who) ?: return CommandResult.Failed("${who.name} is not on this world.")
        Raids.waiveFee(target)
        target.message("Your next item reclaim is free.")
        return CommandResult.Done("${target.username}'s next reclaim is free.")
    }

    private fun kc(p: PlayerRef, who: PlayerTarget): CommandResult {
        val target = online(who) ?: return CommandResult.Failed("${who.name} is not on this world.")
        val rows =
            Raids.of(p).allDefinitions().flatMap { d ->
                val byMode = d.records.kcVarpByMode
                if (byMode.isEmpty()) {
                    listOf("${d.id}: ${target.varp(d.records.kcVarp)}")
                } else {
                    byMode.map { (mode, varp) ->
                        "${d.id} $mode: ${target.varp(varp)}"
                    }
                }
            }
        p.showList("Kill counts of ${target.username}", rows)
        return CommandResult.Done()
    }

    /** `::raid pb <player>`: personal bests by mode and team size. */
    private fun pb(p: PlayerRef, who: String): CommandResult {
        val name = who.trim().replace('_', ' ')
        val target = p.onlinePlayer(name) ?: return CommandResult.Failed("$name is not on this world.")
        val rows =
            Raids.of(p).allDefinitions().flatMap { d ->
                val prefix = d.records.pbKey + "."
                target.savedKeys(prefix).map { key ->
                    "${key.removePrefix(prefix)} (${d.id}): ${Raids.formatTicks(target.saved(key) ?: 0L)}"
                }
            }
        p.showList("Personal bests of ${target.username}", rows.ifEmpty { listOf("None.") })
        return CommandResult.Done()
    }

    private fun words(text: String): List<String> = text.trim().split(' ').filter { it.isNotEmpty() }

    private fun pbRemove(p: PlayerRef, words: List<String>): CommandResult {
        if (words.size < PB_REMOVE_WORDS) return CommandResult.Failed("Usage: ::raid pb remove <player> <raid> <mode> <size>")
        val target = p.onlinePlayer(words[1].replace('_', ' ')) ?: return CommandResult.Failed("${words[1]} is not on this world.")
        val def = Raids.of(p).definition(words[2]) ?: return CommandResult.Failed("No raid '${words[2]}'.")
        val r = def.records
        val bucket = words[PB_SIZE_WORD]
        val key = if (r.kcVarpByMode.isEmpty()) "${r.pbKey}.$bucket" else "${r.pbKey}.${words[PB_MODE_WORD]}.$bucket"
        if (target.saved(key) == null) return CommandResult.Failed("${target.username} has no best for that.")
        target.setSaved(key, null)
        return CommandResult.Done("Cleared ${target.username}'s ${def.id} best ($key).")
    }

    private fun toggle(p: PlayerRef, enable: Boolean, id: String): CommandResult {
        val raids = Raids.of(p)
        val def = raids.definition(id.trim()) ?: return CommandResult.Failed("No raid '$id'.")
        if (enable) raids.disabled -= def.id else raids.disabled += def.id
        return CommandResult.Done("${def.name} ${if (enable) "enabled" else "disabled (runs in progress finish)"} on this world.")
    }

    private fun stats(p: PlayerRef): CommandResult {
        val raids = Raids.of(p)
        val runs = raids.activeRuns()
        val rate = if (raids.rewardsRolled == 0) "-" else "%.2f%%".format(PERCENT * raids.uniqueRewards / raids.rewardsRolled)
        val rows =
            buildList {
                add(
                    "Live instances: ${runs.count {
                        it.instance != null
                    }}; runs ${runs.size} (${runs.groupingBy { it.raid.id }.eachCount()}).",
                )
                add(
                    "Since boot: ${raids.completedRuns} completed, ${raids.uniqueRewards} uniques in " +
                        "${raids.rewardsRolled} rewards ($rate).",
                )
                add("Build and attach times: see the tp_instance_us histogram on the metrics port.")
                if (raids.disabled.isNotEmpty()) add("Disabled: ${raids.disabled.joinToString(", ")}.")
            }
        p.showList("Raid stats", rows)
        return CommandResult.Done()
    }

    // ---- developer subcommands (§6: ECONOMY unless noted)

    private fun developer(b: tp.plugin.commands.CommandBuilder) =
        with(b) {
            val start = lit("start")
            val startRaid = text("raid [mode] [size]")
            signature(start, startRaid) { ctx -> dev(ctx, economy = true) { start(ctx.player, ctx[startRaid]) } }
            signature(lit("skip")) { ctx -> dev(ctx, economy = true) { skip(ctx.player) } }
            val room = lit("room")
            val roomName = text("room|next")
            signature(room, roomName) { ctx -> dev(ctx, economy = true) { room(ctx.player, ctx[roomName]) } }
            val level = lit("level")
            val levelN = int("n", min = 0, max = MAX_LEVEL.toLong())
            signature(level, levelN) { ctx -> dev(ctx, economy = true) { level(ctx.player, ctx[levelN]) } }
            val layout = lit("layout")
            val layoutCode = text("code", optional = true)
            signature(
                layout,
                layoutCode,
            ) { ctx -> dev(ctx, economy = ctx.opt(layoutCode) != null) { layout(ctx.player, ctx.opt(layoutCode)) } }
            val seed = lit("seed")
            val seedN = long("n")
            signature(seed, seedN) { ctx ->
                dev(ctx, economy = true) {
                    Raids.of(ctx.player).nextSeed = ctx[seedN]
                    CommandResult.Done("The next run's seed is ${ctx[seedN]}.")
                }
            }
            val points = lit("points")
            val pointsRest = text("[player] [set n]", optional = true)
            signature(points, pointsRest) { ctx ->
                val rest = ctx.opt(pointsRest).orEmpty()
                dev(ctx, economy = " set " in " $rest ") { points(ctx.player, rest) }
            }
            val setkc = lit("setkc")
            val setkcRest = text("player raid mode n")
            signature(setkc, setkcRest) { ctx -> dev(ctx, economy = true) { setKc(ctx.player, ctx[setkcRest]) } }
        }

    private fun start(p: PlayerRef, args: String): CommandResult {
        val words = args.trim().split(' ').filter { it.isNotEmpty() }
        val raids = Raids.of(p)
        val def =
            raids.definition(words.firstOrNull().orEmpty())
                ?: return CommandResult.Failed("Raids: ${raids.allDefinitions().joinToString(", ") { it.id }}.")
        if (raids.runOf(p.username) != null) return CommandResult.Failed("You are already in a raid.")
        val mode = words.getOrNull(1)?.takeIf { it in def.modes } ?: def.modes.first()
        // §6: "you and virtual party members": scaling counts [size] members although only you are inside.
        val size = words.lastOrNull()?.toIntOrNull()?.coerceIn(1, def.partySize.last) ?: 1
        val party = raids.partyOf(p.username)?.takeIf { it.raid.id == def.id } ?: raids.createParty(def, p.username)
        party ?: return CommandResult.Failed("You are already in another party.")
        Raids.start(p, party, mode, scaledSize = size)?.let { return CommandResult.Failed(it) }
        return CommandResult.Done("Started ${def.name} ($mode, scaled for $size).")
    }

    private fun skip(p: PlayerRef): CommandResult {
        val r = Raids.of(p).runOf(p.username) ?: return CommandResult.Failed("You are not in a raid.")
        if (r.state !is RunState.InRoom) return CommandResult.Failed("You are not in a room.")
        Raids.roomCleared(p, r)
        return CommandResult.Done("Room completed.")
    }

    private fun room(p: PlayerRef, name: String): CommandResult {
        val r = Raids.of(p).runOf(p.username) ?: return CommandResult.Failed("You are not in a raid.")
        val rooms = r.raid.slots.map { it.first() }
        val target =
            if (name.trim() == "next") {
                val now = (r.state as? RunState.InRoom)?.room?.id
                rooms[(rooms.indexOf(now) + 1).mod(rooms.size)]
            } else {
                r.raid.rooms.keys
                    .firstOrNull { it.equals(name.trim(), ignoreCase = true) }
                    ?: return CommandResult.Failed("Rooms: ${r.raid.rooms.keys.joinToString(", ")}.")
            }
        Raids.enterRoom(p, r, target)
        return CommandResult.Done("Moved to $target.")
    }

    /** ToA's raid level comes from the party's invocations, which only raids-toa can choose (commands.md CMD13). */
    private fun level(p: PlayerRef, n: Int): CommandResult {
        Raids.of(p).runOf(p.username) ?: return CommandResult.Failed("You are not in a raid.")
        return CommandResult.Failed("Setting the raid level to $n needs ToA's invocation picker; not supported yet (commands.md CMD13).")
    }

    private fun layout(p: PlayerRef, code: String?): CommandResult {
        val r = Raids.of(p).runOf(p.username) ?: return CommandResult.Failed("You are not in a raid.")
        if (code != null) return CommandResult.Failed("Forcing a layout is not supported yet (commands.md CMD13).")
        return CommandResult.Done(
            "Rooms of run #${r.id.value}: ${r.roomsTaken.joinToString(
                ", ",
            ).ifEmpty { "-" }}; content ${r.content?.javaClass?.simpleName ?: "-"}.",
        )
    }

    private fun points(p: PlayerRef, rest: String): CommandResult {
        val words = rest.trim().split(' ').filter { it.isNotEmpty() }
        val setAt = words.indexOf("set")
        val name = (if (setAt >= 0) words.take(setAt) else words).joinToString(" ").ifBlank { p.username }.replace('_', ' ')
        val r = Raids.of(p).runOf(name) ?: return CommandResult.Failed("$name is not in a raid.")
        val m = r.member(name) ?: return CommandResult.Failed("$name is not in a raid.")
        if (setAt < 0) return CommandResult.Done("$name has ${"%.1f".format(m.points)} points.")
        val n = words.getOrNull(setAt + 1)?.toDoubleOrNull() ?: return CommandResult.Failed("Usage: ::raid points [player] set <n>")
        m.points = n
        r.dirty = true
        return CommandResult.Done("$name now has $n points.")
    }

    private fun setKc(p: PlayerRef, rest: String): CommandResult {
        val w = rest.trim().split(' ').filter { it.isNotEmpty() }
        if (w.size < SETKC_WORDS) return CommandResult.Failed("Usage: ::raid setkc <player> <raid> <mode> <n>")
        val target = p.onlinePlayer(w[0].replace('_', ' ')) ?: return CommandResult.Failed("${w[0]} is not on this world.")
        val def = Raids.of(p).definition(w[1]) ?: return CommandResult.Failed("No raid '${w[1]}'.")
        val n = w[SETKC_N].toIntOrNull()?.takeIf { it >= 0 } ?: return CommandResult.Failed("The count must be a number.")
        val varp = def.records.kcVarpByMode[w[2]] ?: def.records.kcVarp
        target.setVarp(varp, n)
        return CommandResult.Done("${target.username}'s ${def.id} ${w[2]} kill count is $n.")
    }

    // ---- helpers

    /** A one-word subcommand. */
    private fun tp.plugin.commands.CommandBuilder.lit(word: String): Arg.ChoiceArg = choice(word, word)

    /** Developer subcommands: rank, then the world's ECONOMY policy when [economy] (§3). */
    private fun dev(ctx: CommandContext, economy: Boolean, block: () -> CommandResult): CommandResult {
        if (!ctx.player.rank.atLeast(Rank.DEVELOPER)) return CommandResult.Failed("That needs the developer rank.")
        if (economy && !ctx.player.commandsConfig.allows(Impact.ECONOMY)) {
            return CommandResult.Failed("That is an ECONOMY command, which this world does not allow.")
        }
        return block()
    }

    private fun find(p: PlayerRef, which: String): RaidRun? {
        val raids = Raids.of(p)
        val key = which.trim().removePrefix("#")
        return key.toLongOrNull()?.let(raids::run) ?: raids.runOf(key.replace('_', ' '))
    }

    private fun online(who: PlayerTarget): PlayerRef? = (who as? PlayerTarget.Online)?.player

    private fun room(r: RaidRun): String =
        when (val s = r.state) {
            is RunState.InRoom -> s.room.id
            else -> s::class.simpleName ?: "-"
        }

    private fun elapsed(p: PlayerRef, r: RaidRun): String = Raids.formatTicks(p.tick - r.startedTick)

    private companion object {
        val WATCHING = AttrKey<Long>("raid_cmd_watching")
        val WATCH_FROM = AttrKey<CoordGrid>("raid_cmd_watch_from")

        /** ToA raid levels run 0..600 with every regular invocation (docs/mechanics/raids/toa.md TA10). */
        const val MAX_LEVEL = 600
        const val PB_REMOVE_WORDS = 5
        const val PB_MODE_WORD = 3
        const val PB_SIZE_WORD = 4
        const val SETKC_WORDS = 4
        const val SETKC_N = 3
        const val PERCENT = 100.0
    }
}
