package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.commands.Category
import tp.plugin.commands.CommandResult
import tp.plugin.commands.Tags
import tp.plugin.commands.commandsConfig
import tp.plugin.commands.donator
import tp.plugin.commands.lookupPlayer
import tp.plugin.commands.perk
import tp.plugin.commands.recordDonation
import tp.plugin.commands.showList
import tp.plugin.savedText
import tp.plugin.setSavedText
import tp.util.DonatorPerk
import tp.util.DonatorTier
import tp.util.Rank
import tp.util.commands.Impact

/**
 * Donator commands (D-093): `::donated` (owner: records a donation; the hub derives the tier from the total),
 * `::donator [player]` (your tier and perks; staff may look up others) and `::yelltag` (Legendary and above).
 */
public class DonatorCommands : ContentPlugin {
    override fun ContentScope.register() {
        command("donated", Rank.OWNER, Category.OPERATIONS, Impact.OPERATIONS) {
            description("Records a donation in dollars; the player's donator tier follows their total.")
            example("::donated Big_Bob 25", "::donated Big_Bob 12.50 store order 1042")
            target()
            crossWorld = true
            val who = player("player", allowOffline = true)
            val amount = text("dollars [note]")
            signature(who, amount) { ctx ->
                val words = ctx[amount].trim().split(' ')
                val cents = cents(words.first()) ?: return@signature ctx.fail("Give the amount in dollars, like 25 or 12.50.")
                val note = words.drop(1).joinToString(" ")
                val name = ctx[who].name
                ctx.player.recordDonation(name, cents, note) { error, tier ->
                    message(error ?: "Recorded ${dollars(cents)} for ${Tags.escape(name)}: now ${tier?.title ?: "?"}.")
                }
                CommandResult.Pending()
            }
        }
        command("donator", Rank.PLAYER, Category.GENERAL, Impact.INFO) {
            description("Your donator tier and its perks; staff can look up another player's tier.")
            example("::donator", "::donator Big_Bob")
            signature { ctx -> mine(ctx.player) }
            val who = text("player")
            signature(who) { ctx ->
                if (!ctx.player.rank.isStaff) return@signature mine(ctx.player)
                ctx.player.lookupPlayer(ctx[who].replace('_', ' ')) { error, card ->
                    message(error ?: "${card?.name}: ${card?.donator?.title} (${dollars(card?.donatedCents ?: 0)} donated).")
                }
                CommandResult.Pending()
            }
        }
        command("yelltag", Rank.PLAYER, Category.SOCIAL, Impact.SOCIAL) {
            description("Sets the tag shown before your yells (Legendary donators and above), or clears it with off.")
            example("::yelltag Big Spender", "::yelltag off")
            requires { p ->
                if (p.perk(DonatorPerk.ABILITY_TO_SET_A_CUSTOM_YELL_TAG) >
                    0
                ) {
                    null
                } else {
                    "Custom yell tags are a Legendary donator perk."
                }
            }
            val tag = text("tag|off")
            signature(tag) { ctx -> setTag(ctx.player, ctx[tag].trim()) }
        }
    }

    private fun mine(p: PlayerRef): CommandResult {
        val tier = p.donator
        if (tier == DonatorTier.NONE) return CommandResult.Done("You are not a donator.")
        val boosts = p.commandsConfig.donatorBoosts
        val rows =
            DonatorPerk.entries.mapNotNull { perk ->
                val cell = perk.raw(tier)
                if (cell.isEmpty() || cell == CROSS) null else "${perk.label}: $cell"
            }
        p.showList("${tier.title} donator${if (boosts) "" else " (gameplay perks are off on this world)"}", rows)
        return CommandResult.Done()
    }

    /** The perk's cell is the minutes between changes (perks.tsv "50m", "25m"). */
    private fun setTag(p: PlayerRef, raw: String): CommandResult {
        if (raw.equals("off", ignoreCase = true)) {
            p.setSavedText(YELL_TAG_KEY, null)
            return CommandResult.Done("Your yell tag is cleared.")
        }
        val tag = raw.filter { it.isLetterOrDigit() || it == ' ' }.trim()
        if (tag.isEmpty() || tag.length > MAX_TAG) return CommandResult.Failed("Tags are 1-$MAX_TAG letters, digits or spaces.")
        val minutes = p.perk(DonatorPerk.ABILITY_TO_SET_A_CUSTOM_YELL_TAG)
        val last = p.saved(TAG_CHANGED_KEY) ?: 0L
        val now = System.currentTimeMillis()
        val wait = last + minutes * MS_PER_MINUTE - now
        if (wait > 0) return CommandResult.Failed("You can change your tag again in ${(wait + MS_PER_MINUTE - 1) / MS_PER_MINUTE} min.")
        p.setSavedText(YELL_TAG_KEY, tag)
        p.setSaved(TAG_CHANGED_KEY, now)
        return CommandResult.Done("Your yell tag is now [${Tags.escape(tag)}].")
    }

    internal companion object {
        /** Saved text: the custom yell tag. */
        const val YELL_TAG_KEY = "donator.yell_tag"
        const val TAG_CHANGED_KEY = "donator.yell_tag_changed"

        /** Our limit: a tag no longer than a display name (12 characters). */
        const val MAX_TAG = 12
        const val MS_PER_MINUTE = 60_000L
        const val CROSS = "✗"

        /** Cents per dollar. */
        const val CENTS = 100L

        fun cents(raw: String): Long? {
            val v = raw.removePrefix("$").toBigDecimalOrNull() ?: return null
            if (v.signum() <= 0 || v.scale() > 2) return null
            return v.movePointRight(2).toLong()
        }

        fun dollars(cents: Long): String = "$" + (cents / CENTS) + "." + (cents % CENTS).toString().padStart(2, '0')
    }
}
