package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.symbols.Varbits

/**
 * The chatbox re-skin's server half (D-094). The client already has chat timestamps and a transparent chatbox; this
 * turns both on once per account, at its first login, so players can still switch them off in Settings and keep it:
 * - `settings_chat_timestamps` (varbit 12384): client script 876 copies it into the client's timestamp flag, which
 *   script 4742 reads when it builds each chat line;
 * - `chatbox_transparency` (varbit 4608): client script 2373 makes the chatbox transparent in resizable mode when it is
 *   non-zero (fixed mode keeps the stone background).
 * Mentions are the cache half: `pack/cs2/patch/chatbox_mentions.diff`.
 */
public class ChatSkin : ContentPlugin {
    override fun ContentScope.register() {
        onLogin {
            if ((saved(DEFAULTS_KEY) ?: 0L) != 0L) return@onLogin
            setVarbit(Varbits.SETTINGS_CHAT_TIMESTAMPS, 1)
            setVarbit(Varbits.CHATBOX_TRANSPARENCY, 1)
            setSaved(DEFAULTS_KEY, 1L)
        }
    }

    private companion object {
        /** Saved flag: the chat defaults were applied once (later changes are the player's own). */
        const val DEFAULTS_KEY = "chatskin.defaults"
    }
}
