package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.symbols.Components
import tp.symbols.Interfaces

/**
 * Friends / Ignore side tab switch (docs/mechanics/gameframe-buttons.md F1). The two lists share one side slot: the
 * "View Ignore List" button of the friends list and "View Friends List" of the ignore list replace each other there.
 * Adding and removing names, sorting and the list contents are client script driven (FRIENDLIST_ADD/IGNORELIST_ADD
 * packets are handled by the social service).
 */
public class FriendsTab : ContentPlugin {
    override fun ContentScope.register() {
        onButton(Components.Friends.IGNORE) { openOverlay(Components.Toplevel.SIDE9, Interfaces.IGNORE) }
        onButton(Components.Ignore.FRIENDS) { openOverlay(Components.Toplevel.SIDE9, Interfaces.FRIENDS) }
    }
}
