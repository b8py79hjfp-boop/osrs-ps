package tp.content.collectionlog

import tp.plugin.AttrKey
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.LootEvent
import tp.plugin.NpcRef
import tp.plugin.OpenCollectionLog
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * The collection log (docs/mechanics/collection-log.md CLOG1-CLOG9). The layout (tabs, pages, items) comes from the cache and
 * the client draws the interface itself; the server owns what the player has obtained and answers the clicks:
 *
 * - [LootEvent] (NPC drops) adds the obj to the log (CLOG3), with the chat and popup notice the player's setting asks for.
 * - Opening the log, clicking a tab or a page row loads that page's counts into inv `collection_transmit` and its kill
 *   counts into varps, then redraws the interface with the client's own script (CLOG5, CLOG6).
 */
public class CollectionLog : ContentPlugin {
    @Volatile private var loaded: LogData? = null

    override fun ContentScope.register() {
        onWorldStart { loaded = LogData.load(enumInts(ITEM_REMAP_ENUM)) }
        onLogin { sync() }
        onModalClosed(Interfaces.COLLECTION) { closed() }
        events.on<LootEvent> { e -> loaded?.let { e.player.obtain(it, e.obj, e.count) } }
        events.on<DeathEvent> { e -> loaded?.let { countKill(it, e) } }
        for ((tab, layout) in TABS.withIndex()) {
            onButton(layout.button) { show(tab, 0) }
            onButton(layout.background) { c -> selectRow(tab, c) }
        }
        // Summary panel row "Collection Log": routed by the journal tab (gameframe J4).
        events.on<OpenCollectionLog> { it.player.open() }
        onCommand("collectionlog") { open() }
    }

    private fun data(): LogData? = loaded

    // ---- obtaining

    /** Adds [count] of [obj] to the log if it has a slot (CLOG2, CLOG3); first time: counters, page colours, notice. */
    private fun PlayerRef.obtain(
        log: LogData,
        obj: Int,
        count: Int,
    ) {
        if (count <= 0) return
        val key = log.keyOf[unnoted(obj)] ?: return
        val slotKey = LogData.savedKey(key)
        val had = saved(slotKey) ?: 0L
        setSaved(slotKey, minOf(had + count, Int.MAX_VALUE.toLong()))
        if (had > 0) return
        syncCounts(log)
        log.pagesOfKey[key]?.forEach { colour(it) }
        notice(objName(unnoted(obj)))
    }

    /** CLOG4: the setting varbit's bit 0 sends the chat line, bit 1 the popup. */
    private fun PlayerRef.notice(name: String) {
        val setting = varbit(Varbits.OPTION_COLLECTION_NEW_ITEM)
        if (setting and NOTICE_CHAT != 0) message("New item added to your collection log: <col=ef1020>$name</col>")
        if (setting and NOTICE_POPUP != 0) {
            openOverlay(Components.Toplevel.NOTIFICATIONS, Interfaces.NOTIFICATION_DISPLAY)
            clientScript(CS_NOTIFICATION, NOTIFICATION_COLOUR_DEFAULT, POPUP_TITLE, "New item:<br>$name")
        }
    }

    private fun obtained(log: LogData, player: PlayerRef): Int =
        log.pagesOfKey.keys.count {
            (
                player.saved(
                    LogData.savedKey(it),
                ) ?: 0L
            ) > 0
        }

    /** Header counters (varps `collection_count`, `collection_count_max`, and the summary panel's copy). */
    private fun PlayerRef.syncCounts(log: LogData) {
        val n = obtained(log, this)
        setVarp(Varps.COLLECTION_COUNT, n)
        setVarp(Varps.COLLECTION_COUNT_MAX, log.total)
        setVarp(Varps.COLLECTION_COUNT_HIGHSCORES, n)
    }

    /** Page colour: green once every slot of [page] has been obtained (client script 4103). */
    private fun PlayerRef.colour(page: LogPage) {
        if (page.completeVarbit < 0) return
        val done = page.keys.all { (saved(LogData.savedKey(it)) ?: 0L) > 0 }
        setVarbit(page.completeVarbit, if (done) 1 else 0)
    }

    /** Login: counters and page colours are derived from the saved counts; the setting gets its default once (CLOG4). */
    private fun PlayerRef.sync() {
        val log = data() ?: return
        syncCounts(log)
        log.pages.forEach { colour(it) }
        if (saved(NOTICE_INIT) == null) {
            setSaved(NOTICE_INIT, 1L)
            if (varbit(Varbits.OPTION_COLLECTION_NEW_ITEM) == 0) setVarbit(Varbits.OPTION_COLLECTION_NEW_ITEM, NOTICE_CHAT)
        }
    }

    /** CLOG7: a kill by name, for the player the loot goes to. */
    private fun countKill(
        log: LogData,
        e: DeathEvent,
    ) {
        val npc = e.entity as? NpcRef ?: return
        val killer = e.lootFor ?: return
        val key = log.killKeyByName[npc.displayName.lowercase()] ?: return
        killer.setSaved(key, (killer.saved(key) ?: 0L) + 1)
    }

    // ---- interface

    /** Opens the log on the Bosses tab, first page (CLOG5). */
    private fun PlayerRef.open() {
        if (data() == null) return
        inv(Invs.COLLECTION_TRANSMIT, stackAll = true)
        openModal(Components.Toplevel.MAINMODAL, Interfaces.COLLECTION)
        transmitInv(Invs.COLLECTION_TRANSMIT)
        show(0, 0)
    }

    /** A page row of [tab] was clicked: the row is the child index the client created the row with (CLOG6). */
    private fun PlayerRef.selectRow(
        tab: Int,
        click: ButtonClick,
    ) {
        show(tab, click.slot)
    }

    /**
     * Queues the page [row] of [tab] to be shown. The counts go out in the info pass of the next tick and the client
     * script reads them when it runs, so it is sent the tick after they were set (two timer steps, CLOG5).
     */
    private fun PlayerRef.show(
        tab: Int,
        row: Int,
    ) {
        val log = data() ?: return
        if (log.page(tab, row) == null) return
        val pending = this[PENDING]
        if (pending == null) this[PENDING] = Pending(tab, row) else pending.retarget(tab, row)
        if (!hasTimer(DRAW)) timer(DRAW, 1, true) { step() }
    }

    private fun PlayerRef.step() {
        val pending = this[PENDING]
        val log = data()
        if (pending == null || log == null || !isOpen(Interfaces.COLLECTION)) {
            this[PENDING] = null
            stopTimer(DRAW)
            return
        }
        val page = log.page(pending.tab, pending.row)
        if (page == null) {
            this[PENDING] = null
            stopTimer(DRAW)
            return
        }
        if (!pending.loaded) {
            load(log, page)
            pending.loaded = true
            return
        }
        val layout = TABS[pending.tab]
        clientScript(CS_REDRAW, pending.tab, layout.container, layout.background, layout.text, layout.scrollbar, layout.struct, pending.row)
        this[PENDING] = null
        stopTimer(DRAW)
    }

    /**
     * Fills inv `collection_transmit` with the counts of [page] first and then of everything else obtained, as far as
     * its 500 slots reach (the client reads counts by obj, also for pages it jumps to by search; CLOG5), and sets the
     * counter varps of the page.
     */
    private fun PlayerRef.load(
        log: LogData,
        page: LogPage,
    ) {
        for (v in COUNTER_VARPS) setVarp(v, 0)
        for (c in page.counters) {
            if (c.varp !in COUNTER_VARPS) continue
            val kills = c.killKey?.let { saved(it) } ?: 0L
            setVarp(c.varp, minOf(kills, Int.MAX_VALUE.toLong()).toInt())
        }
        val inv = inv(Invs.COLLECTION_TRANSMIT, stackAll = true)
        val wanted = LinkedHashMap<Int, Int>()
        for (key in page.keys) addWanted(wanted, key)
        for (key in log.pagesOfKey.keys) if (wanted.size < inv.size) addWanted(wanted, key)
        transaction("collection log page") {
            for (slot in 0 until inv.size) {
                val obj = inv.obj(slot)
                if (obj < 0) continue
                val count = inv.count(slot)
                if (wanted[obj] == count) {
                    wanted.remove(obj)
                } else if (!removeAt(Invs.COLLECTION_TRANSMIT, slot, obj, count)) {
                    return@transaction false
                }
            }
            wanted.all { (obj, count) -> add(Invs.COLLECTION_TRANSMIT, obj, count) }
        }
        transmitInv(Invs.COLLECTION_TRANSMIT)
    }

    private fun PlayerRef.addWanted(
        wanted: MutableMap<Int, Int>,
        key: Int,
    ) {
        val count = saved(LogData.savedKey(key)) ?: 0L
        if (count > 0) wanted[key] = minOf(count, Int.MAX_VALUE.toLong()).toInt()
    }

    /** The log closed: the page data is display state, so it is not kept in the save. */
    private fun PlayerRef.closed() {
        this[PENDING] = null
        stopTimer(DRAW)
        val inv = inv(Invs.COLLECTION_TRANSMIT, stackAll = true)
        transaction("collection log close") {
            (0 until inv.size).all { slot ->
                val obj = inv.obj(slot)
                obj < 0 || removeAt(Invs.COLLECTION_TRANSMIT, slot, obj, inv.count(slot))
            }
        }
    }

    /** Layer components of a tab (struct 471..475 params 685..688) and the button that selects it. */
    private class TabLayout(
        val struct: Int,
        val button: Int,
        val container: Int,
        val background: Int,
        val text: Int,
        val scrollbar: Int,
    )

    /** Page being brought to the client: [loaded] once its counts are set. */
    private class Pending(
        var tab: Int,
        var row: Int,
    ) {
        var loaded: Boolean = false

        fun retarget(
            tab: Int,
            row: Int,
        ) {
            this.tab = tab
            this.row = row
            loaded = false
        }
    }

    private companion object {
        val DRAW = TimerId("collection_log_draw")
        val PENDING = AttrKey<Pending>("collection_log_pending")

        /** Tabs in cache order (tab structs 471..475, param 684 = 0..4). */
        val TABS =
            listOf(
                TabLayout(
                    STRUCT_FIRST,
                    Components.Collection.BOSS_TAB,
                    Components.Collection.BOSS_CONTAINER,
                    Components.Collection.BOSS_BACKGROUND,
                    Components.Collection.BOSS_TEXT,
                    Components.Collection.BOSS_SCROLLBAR,
                ),
                TabLayout(
                    STRUCT_FIRST + 1,
                    Components.Collection.RAID_TAB,
                    Components.Collection.RAID_CONTAINER,
                    Components.Collection.RAID_BACKGROUND,
                    Components.Collection.RAID_TEXT,
                    Components.Collection.RAID_SCROLLBAR,
                ),
                TabLayout(
                    STRUCT_FIRST + 2,
                    Components.Collection.CLUE_TAB,
                    Components.Collection.CLUE_CONTAINER,
                    Components.Collection.CLUE_BACKGROUND,
                    Components.Collection.CLUE_TEXT,
                    Components.Collection.CLUE_SCROLLBAR,
                ),
                TabLayout(
                    STRUCT_FIRST + 3,
                    Components.Collection.MINIGAME_TAB,
                    Components.Collection.MINIGAME_CONTAINER,
                    Components.Collection.MINIGAME_BACKGROUND,
                    Components.Collection.MINIGAME_TEXT,
                    Components.Collection.MINIGAME_SCROLLBAR,
                ),
                TabLayout(
                    STRUCT_FIRST + 4,
                    Components.Collection.OTHER_TAB,
                    Components.Collection.OTHER_CONTAINER,
                    Components.Collection.OTHER_BACKGROUND,
                    Components.Collection.OTHER_TEXT,
                    Components.Collection.OTHER_SCROLLBAR,
                ),
            )

        /** The varps the counter lines of a page read (client script 2735). */
        val COUNTER_VARPS =
            intArrayOf(Varps.COLLECTION_CATEGORY_COUNT, Varps.COLLECTION_CATEGORY_COUNT2, Varps.COLLECTION_CATEGORY_COUNT3)
    }
}

/** Cache enum 3721: log item obj to the obj the client reads its count under (client script 2732). */
private const val ITEM_REMAP_ENUM = 3721

/** Client script that redraws the whole log for (tab, its four layers, tab struct, selected row); script 7798 is its body. */
private const val CS_REDRAW = 7797

/** Client script `notification_display` (colour, title, text) on interface 660. */
private const val CS_NOTIFICATION = 3343

/** Colour -1 makes the script use its default (0xFF981F). */
private const val NOTIFICATION_COLOUR_DEFAULT = -1

private const val POPUP_TITLE = "Collection log"

/** Setting varbit `option_collection_new_item`: bit 0 chat notification, bit 1 popup (client scripts 5188, 5189). */
private const val NOTICE_CHAT = 1
private const val NOTICE_POPUP = 2

/** Saved marker: the notification setting got its default (CLOG4). */
private const val NOTICE_INIT = "clog.init"

/** First tab struct (Bosses); the others follow. */
private const val STRUCT_FIRST = 471
