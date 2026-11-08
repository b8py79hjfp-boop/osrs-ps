package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.plugin.commands.donator
import tp.symbols.Varbits
import tp.util.ChatIcons
import tp.util.DonatorTier

/**
 * The chatbox re-skin's server half (D-094, D-096). The client already has chat timestamps and a transparent chatbox; this
 * turns both on once per account, at its first login, so players can still switch them off in Settings and keep it:
 * - `settings_chat_timestamps` (varbit 12384): client script 876 copies it into the client's timestamp flag, which
 *   script 4742 reads when it builds each chat line;
 * - `chatbox_transparency` (varbit 4608): client script 2373 makes the chatbox transparent in resizable mode when it is
 *   non-zero (fixed mode keeps the stone background).
 *
 * D-096 adds two things the packed chatbox (pack/interface-patches/chatbox_tabs.toml) reads:
 * - the Staff tab (162:575) is shown to staff only;
 * - the roster (162:579): this world's staff and donators as `;name=<img=N><col=rrggbb>;`, which the patched chat
 *   builder (script 84) uses to draw their public-chat names with their icon and colour. Public chat only comes from
 *   players on the same world, so the roster is per world.
 * Both are checked every [SYNC_PERIOD] ticks and sent only when they changed for that player, so a new login, a
 * `::setrank` or a `::donated` reaches the client within that period, after the chatbox is open.
 * Mentions and the tab filters are the cache half: `pack/cs2/patch/chatbox_*.diff`.
 */
public class ChatSkin : ContentPlugin {
    override fun ContentScope.register() {
        onLogin {
            if ((saved(DEFAULTS_KEY) ?: 0L) != 0L) return@onLogin
            setVarbit(Varbits.SETTINGS_CHAT_TIMESTAMPS, 1)
            setVarbit(Varbits.CHATBOX_TRANSPARENCY, 1)
            setSaved(DEFAULTS_KEY, 1L)
        }
        onWorldTick {
            if (tick % SYNC_PERIOD != 0L) return@onWorldTick
            val roster = roster()
            forEachPlayer { p -> p.sync(roster) }
        }
    }

    /** This world's roster text; rebuilt only when its fingerprint (names, ranks, tiers) changes. */
    private fun WorldRef.roster(): Roster {
        var hash = 1
        forEachPlayer { p ->
            if (p.rank.isStaff || p.donator != DonatorTier.NONE) {
                hash = HASH_PRIME * (HASH_PRIME * (HASH_PRIME * hash + p.username.hashCode()) + p.rank.ordinal) + p.donator.ordinal
            }
        }
        val old = worldAttr(ROSTER)
        if (old != null && old.hash == hash) return old
        val text = StringBuilder(";")
        forEachPlayer { p ->
            val prefix = prefix(p)
            if (prefix.isNotEmpty()) {
                val name = p.username.lowercase()
                // The client may hold the sender's name with no-break spaces (U+00A0) instead of spaces: list both.
                val names = if (' ' in name) listOf(name, name.replace(' ', NBSP)) else listOf(name)
                for (n in names) {
                    val entry = "$n=$prefix;"
                    if (text.length + entry.length <= ROSTER_MAX_CHARS) text.append(entry)
                }
            }
        }
        val roster = Roster(hash, if (text.length == 1) "" else text.toString())
        setWorldAttr(ROSTER, roster)
        return roster
    }

    private fun prefix(p: PlayerRef): String {
        val icon = ChatIcons.of(p.rank, p.donator)?.let { "<img=$it>" }.orEmpty()
        val colour = ChatIcons.nameColour(p.donator)?.let { "<col=$it>" }.orEmpty()
        return icon + colour
    }

    private fun PlayerRef.sync(roster: Roster) {
        val staff = rank.isStaff
        if (this[STAFF_SHOWN] != staff) {
            ifHide(STAFF_TAB, !staff)
            this[STAFF_SHOWN] = staff
        }
        if (this[ROSTER_SENT] != roster.hash) {
            ifText(ROSTER_COMPONENT, roster.text)
            this[ROSTER_SENT] = roster.hash
        }
    }

    private class Roster(val hash: Int, val text: String)

    private companion object {
        /** Saved flag: the chat defaults were applied once (later changes are the player's own). */
        const val DEFAULTS_KEY = "chatskin.defaults"

        /** Ticks between roster / Staff tab checks (3 s): our choice, cosmetic only (D-096). */
        const val SYNC_PERIOD = 5L

        /** Packed `chatbox:tab_staff` (162:575, chatbox_tabs.toml), hidden in the cache. */
        const val STAFF_TAB = (162 shl 16) or 575

        /** Packed `chatbox:tp_chat_roster` (162:579, chatbox_tabs.toml), read by patched script 84. */
        const val ROSTER_COMPONENT = (162 shl 16) or 579

        /** Roster text cap: well under the 32767-byte limit of a var-short server packet such as IF_SETTEXT. */
        const val ROSTER_MAX_CHARS = 20_000

        /** Multiplier of the roster fingerprint (the usual 31 of `hashCode`). */
        const val HASH_PRIME = 31

        /** No-break space, which the client uses in displayed names. */
        const val NBSP = ' '

        val ROSTER = AttrKey<Roster>("chatskin.roster")
        val ROSTER_SENT = AttrKey<Int>("chatskin.roster_sent")
        val STAFF_SHOWN = AttrKey<Boolean>("chatskin.staff_shown")
    }
}
