package tp.plugin

import tp.engine.ChannelConfig
import tp.engine.ExchangeRequest
import tp.engine.World
import tp.engine.entity.Entity
import tp.engine.entity.Player
import tp.engine.interact.clearInteraction
import tp.engine.inv.InvStack
import tp.engine.inv.Inventory
import tp.engine.inv.ItemTransaction
import tp.engine.script.QueueType
import tp.engine.tick.TickInspector
import tp.engine.zone.GroundObj
import tp.protocol.api.ServerMessage
import tp.protocol.api.Visual
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varps
import tp.util.CoordGrid
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Content's view of a player. Every change goes through the engine (interfaces, vars, item transactions). */
public class PlayerRef internal constructor(internal val p: Player) : EntityRef() {
    override val e: Entity get() = p
    public val username: String get() = p.username

    /** Hub account id: stable across display-name changes (0 without an account authority). */
    public val accountId: Long get() = p.accountId

    /** Staff level from the account: 0 player, 1 moderator, 2 administrator. */
    public val staffLevel: Int get() = p.staffLevel

    /**
     * Grand Exchange call through the hub. [reply] runs later on the world thread (only if the player is still online)
     * with the affected offers, or null if the hub is unavailable.
     */
    public fun exchange(request: GeRequest, reply: PlayerRef.(List<GeOffer>?) -> Unit) {
        val bridge = p.world.accounts ?: return reply(null)
        val engineRequest =
            when (request) {
                GeRequest.Offers -> {
                    ExchangeRequest.Offers
                }

                is GeRequest.Place -> {
                    ExchangeRequest.Place(
                        request.slot,
                        request.buy,
                        request.obj,
                        request.quantity,
                        request.price,
                        request.taxRate,
                    )
                }

                is GeRequest.Collect -> {
                    ExchangeRequest.Collect(request.slot, request.items, request.coins)
                }

                is GeRequest.Abort -> {
                    ExchangeRequest.Abort(request.slot)
                }

                GeRequest.History -> {
                    ExchangeRequest.History
                }
            }
        bridge.exchange(p, engineRequest) { player, offers ->
            PlayerRef(player).reply(
                offers?.map {
                    GeOffer(
                        it.slot,
                        it.buy,
                        it.obj,
                        it.quantity,
                        it.price,
                        it.completed,
                        it.collectCoins,
                        it.collectItems,
                        it.gold,
                        it.aborted,
                        it.tax,
                    )
                },
            )
        }
    }

    /**
     * The player's own friends chat settings (hub). With [update], they are stored first. [reply] runs later on the
     * world thread, or with null if the hub is unavailable.
     */
    public fun channelSettings(update: FriendsChatSettings? = null, reply: PlayerRef.(FriendsChatSettings?) -> Unit) {
        val bridge = p.world.accounts ?: return reply(null)
        val config = update?.let { ChannelConfig(it.name, it.enterRank, it.talkRank, it.kickRank) }
        bridge.channelConfig(p, config) { player, c ->
            PlayerRef(player).reply(c?.let { FriendsChatSettings(it.name, it.enterRank, it.talkRank, it.kickRank) })
        }
    }

    /** The player's clan (null: none), their rank in it, and the clan they are a guest in (docs/mechanics/clans.md). */
    public val clanName: String? get() = p.clanName
    public val clanRank: Int get() = p.clanRank
    public val guestClanName: String? get() = p.guestClanName

    /** A clan action at the hub; [reply] runs later on the world thread with null on success, else the message. */
    public fun clan(action: ClanAction, reply: PlayerRef.(String?) -> Unit = { it?.let(::message) }) {
        val bridge = p.world.accounts ?: return reply(tp.engine.AccountBridge.NO_HUB)
        bridge.clan(p, action.request()) { player, err -> PlayerRef(player).reply(err) }
    }

    /** The NPC type this player sees [npc] as: a multi-NPC (Aubury, quest NPCs) resolves through the player's vars. */
    public fun seenType(npc: NpcRef): Int = p.world.seenNpcType(p, npc.n.type)

    // ---- cutscene camera (absolute tiles; height in client units; rate / acceleration 0 = at once)

    public fun camLookAt(at: CoordGrid, height: Int, rate: Int = 0, acceleration: Int = 0) {
        p.session.send(ServerMessage.CamLookAt(at.x, at.z, height, rate, acceleration))
    }

    public fun camMoveTo(at: CoordGrid, height: Int, rate: Int = 0, acceleration: Int = 0) {
        p.session.send(ServerMessage.CamMoveTo(at.x, at.z, height, rate, acceleration))
    }

    /** Gives the camera back to the player. */
    public fun camReset() {
        p.session.send(ServerMessage.CamReset)
    }

    public fun camShake(axis: Int, random: Int, amplitude: Int, rate: Int) {
        p.session.send(ServerMessage.CamShake(axis, random, amplitude, rate))
    }

    /** 0 enabled, 1 unclickable, 2 hidden, 3 compass hidden, 4 unclickable + compass hidden, 5 disabled. */
    public fun minimapState(state: Int) {
        p.session.send(ServerMessage.MinimapState(state))
    }

    /** Sends a staff command to the hub (moderation, broadcasts, system update); the reply arrives as a game message. */
    public fun staffCommand(command: String, args: List<String>) {
        val bridge = p.world.accounts
        if (bridge == null) message("Staff commands need the hub.") else bridge.staff(p, command, args)
    }

    public val slot: Int get() = p.slot
    public val worldId: Int get() = p.world.config.id

    /** Current processing position (0 = acts first). */
    public val pid: Int get() = p.world.pidOf(p.slot)
    public val windowMode: Int get() = p.windowMode
    public val isDelayed: Boolean get() = p.isDelayed

    /** Whether a modal interface is open. */
    public val hasModal: Boolean get() = p.hasModal

    /** Run toggle; setting it also updates varp `option_run` (what the run orb displays; RS Mod `toggleRun`). */
    public var runMode: Boolean
        get() = p.runMode
        set(value) {
            // Run can only be turned on with at least 1% (100 units) energy (Wiki "Energy").
            val on = value && p.runEnergy >= MIN_RUN_ENERGY
            p.world.setRunMode(p, on)
            p.vars[Varps.OPTION_RUN] = if (on) 1 else 0
        }

    /** Run energy multipliers in permille (run-energy.md): drain (stamina 300, ring of endurance 850), restore (graceful). */
    public var runDrainPermille: Int
        get() = p.runDrainPermille
        set(value) {
            p.runDrainPermille = value
        }

    public var runRestorePermille: Int
        get() = p.runRestorePermille
        set(value) {
            p.runRestorePermille = value
        }

    public var runEnergy: Int
        get() = p.runEnergy
        set(value) {
            p.runEnergy = value
        }

    public fun message(text: String) {
        p.session.send(ServerMessage.GameMessage(text))
    }

    /** A chatbox line of client message [type] (e.g. 27 item / 28 NPC / 29 scenery examine). */
    public fun message(text: String, type: Int) {
        p.session.send(ServerMessage.GameMessage(text, type))
    }

    /** A clickable request line from [from]: chat type 101 (trade) or 103 (duel), e.g. "<name> wishes to trade with you." */
    public fun requestMessage(from: PlayerRef, text: String, duel: Boolean) {
        p.session.send(ServerMessage.GameMessage(text, if (duel) DUEL_REQUEST_TYPE else TRADE_REQUEST_TYPE, from.username))
    }

    /** Hard interruption: cancels the interaction and route, closes the modal, clears weak scripts (interactions.md I7). */
    public fun interrupt() {
        p.world.interrupt(p)
    }

    /**
     * Starts an interaction with [target] using op [op] (e.g. auto-retaliate's attack), exactly as if the player had
     * clicked it (interrupts the current one).
     */
    public fun interact(target: EntityRef, op: Int) {
        val t =
            when (target) {
                is PlayerRef -> {
                    tp.engine.interact.InteractionTarget
                        .OfPlayer(target.p)
                }

                is NpcRef -> {
                    tp.engine.interact.InteractionTarget
                        .OfNpc(target.n)
                }
            }
        p.world.interact(p, t, op)
    }

    /** Runs [action] through the area queue (area-triggered scripts: scripts.md Q11). */
    public fun areaQueue(action: PlayerRef.() -> Unit) {
        p.areaQueue.add { action() }
    }

    /** Logs the player out at the end of the tick. */
    public fun logout() {
        p.world.requestLogout(p)
    }

    /** Hops to world [worldId] (logout + reconnect); false if that world is unknown or this one. */
    public fun hop(worldId: Int): Boolean = p.world.hop(p, worldId)

    /** Ends the current interaction (no other interruption effects). */
    public fun stopInteraction() {
        p.clearInteraction()
    }

    /** Whether the player has a route to walk. */
    public val isMoving: Boolean get() = p.isRouting

    /** Whether the player has an interaction in progress. */
    public val isInteracting: Boolean get() = p.interactionTarget != null

    /** The NPC or player this player is interacting with, or null. */
    public val interactionTarget: EntityRef?
        get() =
            when (val t = p.interactionTarget) {
                is tp.engine.interact.InteractionTarget.OfNpc -> NpcRef(t.npc)
                is tp.engine.interact.InteractionTarget.OfPlayer -> PlayerRef(t.player)
                else -> null
            }

    /** Real time in whole minutes since the Unix epoch (cooldowns the client shows as real time, e.g. home teleport). */
    public val epochMinutes: Long get() = System.currentTimeMillis() / MILLIS_PER_MINUTE

    /** Real time in whole seconds since the Unix epoch (real-time lockouts and delays, e.g. the bank PIN). */
    public val epochSeconds: Long get() = System.currentTimeMillis() / MILLIS_PER_SECOND

    /**
     * A one-way hash of the address this session logged in from: lets content tell "the same place" apart (bank PIN
     * logout grace) without the IP itself ever reaching content or the save.
     */
    public val loginAddressHash: Long get() = addressHash(p.loginAddress)

    /** Persisted content value [key] (saved with the account), or null. */
    public fun saved(key: String): Long? = p.saved[key]

    /**
     * IN8: where this player goes if they are later found inside an instance that no longer exists (a restart or a
     * torn-down raid); null clears it. Saved with the player.
     */
    public fun setInstanceExit(exit: CoordGrid?) {
        if (exit ==
            null
        ) {
            p.saved.remove(tp.engine.map.Instances.EXIT_KEY)
        } else {
            p.saved[tp.engine.map.Instances.EXIT_KEY] = exit.packed.toLong()
        }
    }

    /** Sets (or with null, clears) persisted content value [key]. */
    public fun setSaved(key: String, value: Long?) {
        if (value == null) p.saved.remove(key) else p.saved[key] = value
    }

    /** Starts (or replaces) timer [id] every [interval] ticks; soft timers run even while delayed (scripts.md T3/T4). */
    public fun timer(id: TimerId, interval: Int, soft: Boolean, action: PlayerRef.() -> Unit) {
        p.timers.add(id.key, interval, soft) { action() }
    }

    public fun stopTimer(id: TimerId) {
        p.timers.remove(id.key)
    }

    public fun hasTimer(id: TimerId): Boolean = p.timers.has(id.key)

    /** Toggles streaming this player's tick trace to their chatbox (`::trace`); false if no inspector is installed. */
    public fun toggleTrace(): Boolean = (p.world.tracer as? TickInspector)?.toggle(p) ?: false

    public fun teleport(to: CoordGrid) {
        p.world.teleport(p, to)
    }

    /**
     * Forced movement from [start] to [end] (agility obstacles, knockbacks): the player is moved to [end] now and the
     * client animates the way, reaching [start] after [delay1] and [end] after [delay2] client cycles (20 ms each, so
     * 30 per tick), facing [angle] (0 south, 512 west, 1024 north, 1536 east). RS Mod `PathingEntityCommon.exactMove`.
     */
    public fun exactMove(start: CoordGrid, end: CoordGrid, delay1: Int, delay2: Int, angle: Int) {
        p.world.teleport(p, end)
        p.visual(Visual.ExactMove(start.x - end.x, start.z - end.z, delay1, 0, 0, delay2, angle))
    }

    /**
     * Sets a normal walking route to [to], as a map click would (movement phase of this tick); false if there is no route.
     * Content uses it for scripted single steps, e.g. the step off a freshly lit fire.
     */
    public fun walkTo(to: CoordGrid): Boolean = p.world.movement.routePlayer(p, to.x, to.z, false)

    // ---- interfaces

    public fun openTop(interfaceId: Int) {
        p.world.interfaces.openTop(p, interfaceId)
    }

    /** The client's window was resizable at login (gameframe-settings.md G8). */
    public val loginResizable: Boolean get() = p.loginResizable

    /** The toplevel interface open now (-1 before the gameframe). */
    public val toplevel: Int get() = p.world.interfaces.top(p)

    /**
     * Switches the toplevel to [interfaceId] and moves every open interface into it (IF_OPENTOP + IF_MOVESUB, G8). Content
     * keeps naming destinations by their fixed-mode `toplevel` components; the engine redirects them.
     */
    public fun moveTop(interfaceId: Int) {
        p.world.interfaces.moveTop(p, interfaceId)
    }

    public fun openOverlay(destination: Int, interfaceId: Int) {
        p.world.interfaces.openOverlay(p, destination, interfaceId)
    }

    /** Closes the overlay open in [destination] (not the modal). */
    public fun closeOverlay(destination: Int) {
        p.world.interfaces.closeOverlay(p, destination)
    }

    public fun openModal(destination: Int, interfaceId: Int) {
        p.world.interfaces.openModal(p, destination, interfaceId)
    }

    public fun closeModal() {
        p.closeModal()
    }

    public fun isOpen(interfaceId: Int): Boolean = p.world.interfaces.isOpen(p, interfaceId)

    /** Asks for text (client script 110 `meslayer_mode9`); [answer] runs with what the player types. */
    public fun stringDialog(title: String, answer: PlayerRef.(String) -> Unit) {
        dialogs()[p] = { (it as? String)?.let { text -> answer(text) } }
        p.session.send(ServerMessage.ClientScript(MESLAYER_STRING, listOf(title, 0)))
    }

    /**
     * A Yes/No popup over [target] (`popupoverlay` 289, client script 4212 "title|text|cancel|confirm"; RS Mod
     * `confirmOverlay`) that keeps the main modal open; the client answers with a count, nonzero = [confirm]
     * (scripts 4207 / 4209). [answer] runs with true for confirm; the popup closes either way.
     */
    public fun confirmOverlay(
        target: Int,
        title: String,
        text: String,
        cancel: String,
        confirm: String,
        answer: PlayerRef.(Boolean) -> Unit,
    ) {
        dialogs()[p] = { value ->
            closeOverlay(target)
            (value as? Int)?.let { answer(it != 0) }
        }
        openOverlay(target, POPUP_OVERLAY)
        clientScript(CS_CONFIRM_OVERLAY, "$title|$text|$cancel|$confirm", target)
    }

    /**
     * Waits for a number the client sends without a server-opened dialog (e.g. the colour picker's choice, which the
     * client submits as RESUME_P_COUNTDIALOG); the value arrives as sent (no sign change).
     */
    public fun awaitCount(answer: PlayerRef.(Int) -> Unit) {
        dialogs()[p] = { (it as? Int)?.let { n -> answer(n) } }
    }

    /** Asks for a number (client script 108 `meslayer_mode7`); [answer] runs with the (non-negative) count. */
    public fun countDialog(title: String, answer: PlayerRef.(Int) -> Unit) {
        dialogs()[p] = { (it as? Int)?.let { n -> answer(kotlin.math.abs(n)) } }
        p.session.send(ServerMessage.ClientScript(MESLAYER_COUNT, listOf(title)))
    }

    /**
     * Opens the chatbox item search (client script 750 `meslayer_mode14`, RS Mod `ClientScripts.objSearch`): [title],
     * tradeable items only, no enum filter, no "last searched" row. [answer] runs with the chosen obj (-1 = cancelled).
     */
    public fun objDialog(title: String, answer: PlayerRef.(Int) -> Unit) {
        dialogs()[p] = { (it as? tp.engine.ObjDialogAnswer)?.let { a -> answer(a.obj) } }
        p.session.send(ServerMessage.ClientScript(OBJ_SEARCH, listOf(title, 1, -1, 0)))
    }

    /**
     * Opens the chatbox option menu (`chatmenu`, client script 58 `chatbox_multi_init`; RS Mod `ifChoice`/`choiceN`):
     * [title] above [choices] (2..6). [answer] runs with the 1-based pick after the menu closed; closing the menu or
     * opening another interface drops the pick. Only the latest menu per player can be answered.
     */
    public fun choiceDialog(title: String, choices: List<String>, answer: PlayerRef.(Int) -> Unit) {
        require(choices.size in MIN_CHOICES..MAX_CHOICES) { "choice menus have $MIN_CHOICES..$MAX_CHOICES options" }
        p.world.interfaces.openModal(p, Components.Chatbox.CHATMODAL, Interfaces.CHATMENU)
        p.session.send(ServerMessage.ClientScript(CHATBOX_MULTI_INIT, listOf(title, choices.joinToString(CHOICE_SEPARATOR))))
        ifEvents(Components.Chatmenu.OPTIONS, 1..choices.size, IfEvents.PAUSE_BUTTON)
        (p.world.content as PluginContent).pendingChoices[p] = PendingChoice(choices.size, answer)
    }

    /**
     * Runs [script] as a strong queue script of this player, starting this tick when called from input handling (queue
     * delay 0, scripts.md Q13). The script may [PlayerScript.delay]; use it for actions that stall the player.
     */
    public fun startScript(script: suspend PlayerScript.() -> Unit) {
        p.queue.add(QueueType.STRONG, 0) {
            val body: suspend PlayerScript.() -> Unit = script
            val scope = PlayerScript(this)
            suspendCoroutineUninterceptedOrReturn { c -> body.startCoroutineUninterceptedOrReturn(scope, c) }
        }
    }

    /**
     * Queues [script] as a weak script of this player, [delay] ticks from now (scripts.md Q1, Q13). A weak script is
     * removed by any interruption (a click that walks or interacts, a modal opening or closing: Q3, Q7) before it runs,
     * and is held while a modal is open or the player is delayed (Q4). Use it for repeating production actions that a
     * click must be able to end: each run does one step and queues the next.
     */
    public fun weakQueue(delay: Int, script: suspend PlayerScript.() -> Unit) {
        p.queue.add(QueueType.WEAK, delay) {
            val body: suspend PlayerScript.() -> Unit = script
            val scope = PlayerScript(this)
            suspendCoroutineUninterceptedOrReturn { c -> body.startCoroutineUninterceptedOrReturn(scope, c) }
        }
    }

    /** Sends one Grand Exchange slot to the client ([status] 0 clears it). */
    public fun stockMarketSlot(slot: Int, status: Int, obj: Int, price: Int, count: Int, completed: Int, gold: Int) {
        p.session.send(ServerMessage.StockMarketSlot(slot, status, obj, price, count, completed, gold))
    }

    private fun dialogs() = (p.world.content as PluginContent).pendingDialogs

    /**
     * Sends [invId] to the client as [objs]/[counts] without changing any server inventory: for panels that only show
     * items (items kept on death). Nothing moves, so nothing goes through a transaction or the item journal.
     */
    public fun showInv(invId: Int, objs: IntArray, counts: IntArray, other: Boolean = false) {
        p.session.send(ServerMessage.Inventory(invId, objs, counts, null, other))
    }

    /**
     * Runs the handler of another button as if the player clicked it (e.g. the equipment-stats panel reuses the
     * backpack's Wield and the worn tab's Remove). Server-side only: no client packet, no open-interface check.
     */
    public fun dispatchButton(component: Int, op: Int, slot: Int = -1, obj: Int = -1) {
        p.world.content.onButton(p, component, op, slot, obj)
    }

    /** Sets the text of packed [component] (`tp.symbols.Components`). */
    public fun ifText(component: Int, text: String) {
        p.session.send(ServerMessage.IfText(component, text))
    }

    /**
     * What clicks do for this player (SET_INTERACTION_MODE): in [world] (a world entity's index, -1 the root world, -2
     * every world) a tile click walks (1), sets a heading (2) or does nothing (0); an entity click works (1), only
     * examines (2) or does nothing (0). Sailing switches the root world to heading mode while navigating.
     */
    public fun setInteractionMode(world: Int, tileMode: Int, entityMode: Int) {
        p.session.send(ServerMessage.InteractionMode(world, tileMode, entityMode))
    }

    /** Sets [component]'s colour to [rgb] (0xRRGGBB; the client keeps 5 bits per channel). */
    public fun ifColour(component: Int, rgb: Int) {
        p.session.send(ServerMessage.IfColour(component, rgb))
    }

    /** Shows or hides packed [component]. */
    public fun ifHide(component: Int, hidden: Boolean) {
        p.session.send(ServerMessage.IfHide(component, hidden))
    }

    /** Shows [obj] x[count] in packed [component]. */
    public fun ifObject(component: Int, obj: Int, count: Int = 1) {
        p.session.send(ServerMessage.IfObject(component, obj, count))
    }

    /** Plays music track [id] (cache music/midi id). */
    public fun playMusic(id: Int) {
        p.session.send(ServerMessage.MusicTrack(id))
    }

    /** Plays jingle [id] (`tp.symbols.Jingles`) over the music. */
    public fun jingle(id: Int) {
        p.session.send(ServerMessage.Jingle(id))
    }

    /** Allowed ops/events on [component]'s slots [slots] (IF_SETEVENTS bit set). */
    public fun ifEvents(component: Int, slots: IntRange, events: Long) {
        p.session.send(ServerMessage.InterfaceEvents(component, slots.first, slots.last, events))
    }

    /** Runs client script [id] with Int/String [args]. */
    public fun clientScript(id: Int, vararg args: Any) {
        require(args.all { it is Int || it is String || it is Long }) { "client script args must be Int, Long or String" }
        p.session.send(ServerMessage.ClientScript(id, args.toList()))
    }

    /** Right-click option [slot] (1..8) on other players; null text removes it. */
    public fun playerOp(slot: Int, text: String?, top: Boolean = false) {
        p.session.send(ServerMessage.PlayerOption(slot, text, top))
    }

    /** Sets the chatbox filter buttons (0 on, 1 friends, 2 off). */
    public fun chatFilters(publicFilter: Int, privateFilter: Int, tradeFilter: Int) {
        p.session.send(ServerMessage.ChatFilters(publicFilter, privateFilter, tradeFilter))
    }

    // ---- vars

    public fun varp(id: Int): Int = p.vars[id]

    public fun setVarp(id: Int, value: Int) {
        p.vars[id] = value
    }

    public fun varbit(id: Int): Int = p.vars.getBit(id)

    /** Sends a 64-bit client varp; the server keeps no copy (content keeps the value it mirrors). */
    public fun sendVarpLong(id: Int, value: Long) {
        p.session.send(ServerMessage.VarpLong(id, value))
    }

    public fun setVarbit(id: Int, value: Int) {
        p.vars.setBit(id, value)
    }

    /** Whether [value] fits the bit range of varbit [id]; check it before storing a value that came from a client packet. */
    public fun varbitFits(id: Int, value: Int): Boolean {
        val type = p.world.data.configs.varbits[id] ?: return false
        val bits = type.endBit - type.startBit + 1
        return value >= 0 && (bits >= Int.SIZE_BITS - 1 || value < (1 shl bits))
    }

    // ---- stats

    /** Sets the current (boosted/drained) level of [stat]. */
    public fun setLevel(stat: Int, level: Int) {
        p.stats.setLevel(stat, level)
    }

    public fun level(stat: Int): Int = p.stats.level(stat)

    public fun baseLevel(stat: Int): Int = p.stats.baseLevel(stat)

    /**
     * Adds [xp] (fractional xp allowed, stored in tenths); returns levels gained. A gain publishes [StatAdvanceEvent]
     * (the level-up message and display, docs/mechanics/level-up.md).
     */
    public fun addXp(stat: Int, xp: Double): Int {
        val from = p.stats.baseLevel(stat)
        val gained = p.stats.addXp(stat, (xp * XP_TENTHS).toInt())
        if (xp > 0) events.publish(XpGained(this, stat, xp))
        if (gained > 0) events.publish(StatAdvanceEvent(this, stat, from, from + gained))
        return gained
    }

    /**
     * The OSRS skilling success roll (Wiki "Skilling success rate", RS Mod `SkillingSuccessRate`): with current level L
     * (1..99), success chance is `(1 + floor(low * (99 - L) / 98 + high * (L - 1) / 98 + 0.5)) / 256`, rolled with
     * the world RNG. [low] and [high] are the chances out of 256 at levels 1 and 99.
     */
    public fun statRandom(stat: Int, low: Int, high: Int): Boolean {
        val level = p.stats.level(stat).coerceIn(1, Stats.MAX_LEVEL)
        val span = Stats.MAX_LEVEL - 1
        val chance = 1 + kotlin.math.floor((low * (Stats.MAX_LEVEL - level) + high * (level - 1)).toDouble() / span + HALF).toInt()
        return random(SUCCESS_SCALE) < chance
    }

    // ---- appearance and objs

    /** Combat level from base levels (OSRS Wiki "Combat level"). */
    public val combatLevel: Int
        get() {
            val st = p.stats
            val base = (st.baseLevel(DEF) + st.baseLevel(HP) + st.baseLevel(PRAY) / 2) / 4.0
            val melee = MULT * (st.baseLevel(ATT) + st.baseLevel(STR))
            val range = MULT * (st.baseLevel(RNG) * 3 / 2)
            val mage = MULT * (st.baseLevel(MAG) * 3 / 2)
            return kotlin.math.floor(base + maxOf(melee, range, mage)).toInt()
        }

    /** Sends this player's look: worn obj per wear position 0..11 (-1 empty). */
    public fun appearance(worn: IntArray, combatLevel: Int, skullIcon: Int = -1, prayerIcon: Int = -1, renderAnims: IntArray? = null) {
        p.world.refreshAppearance(p, worn, combatLevel, skullIcon, prayerIcon, renderAnims)
    }

    /** Obj config param [param] of [obj] (int), or [default]. */
    public fun objParam(obj: Int, param: Int, default: Int): Int =
        p.world
            .objType(obj)
            ?.params
            ?.int(param) ?: default

    /** Primary wear position of [obj] (-1 = not wearable). */
    public fun objWearPos(obj: Int): Int = p.world.objType(obj)?.wearPos1 ?: -1

    /** Secondary wear position of [obj] (two-handed weapons occupy the shield position: 5). */
    public fun objWearPos2(obj: Int): Int = p.world.objType(obj)?.wearPos2 ?: -1

    /** Obj category (cache `category`), -1 if none. */
    public fun objCategory(obj: Int): Int = p.world.objType(obj)?.category ?: -1

    /** The text of right-click sub-option [subop] under backpack button op [buttonOp] of [obj] (cache iop sub-ops). */
    public fun objSubOp(obj: Int, buttonOp: Int, subop: Int): String? =
        p.world
            .objType(obj)
            ?.iopSubOps
            ?.get(backpackIopIndex(buttonOp))
            ?.get(subop)

    /** Inventory op text of [obj] for iop [index] (0-based: button op = index + 2), or null. */
    public fun objIop(obj: Int, index: Int): String? =
        p.world
            .objType(obj)
            ?.iops
            ?.getOrNull(index)

    public fun objName(obj: Int): String = p.world.objType(obj)?.name ?: "null"

    /** The cache gameval name of [obj] (e.g. `iron_ore`), or "" when the cache has none. */
    public fun objGameval(obj: Int): String =
        p.world.data.objGamevals[obj]
            .orEmpty()

    /** Number of obj types in the cache (ids `0 until objTypeCount`). */
    public val objTypeCount: Int get() = p.world.data.configs.objs.size

    /** The stack-size variants of [obj] (cache `countobj`, e.g. coin piles); empty if none. */
    public fun objCountVariants(obj: Int): IntArray =
        p.world
            .objType(obj)
            ?.countObj
            ?.filter { it > 0 && it != obj }
            ?.toIntArray() ?: IntArray(0)

    /** Whether [obj] is a note or a bought/placeholder template copy rather than a real item definition. */
    public fun objIsTemplateCopy(obj: Int): Boolean =
        p.world.objType(obj)?.let { it.isNoted || it.isPlaceholder || it.boughtTemplate != -1 } ?: true

    /** Cache name of NPC type [npc]. */
    public fun npcName(npc: Int): String =
        p.world.data.configs.npcs[npc]
            ?.name ?: "null"

    /** Cache name of loc type [loc]. */
    public fun locName(loc: Int): String =
        p.world.data.configs.locs[loc]
            ?.name ?: "null"

    public fun objTradeable(obj: Int): Boolean = p.world.objType(obj)?.tradeable ?: false

    /** Int param [param] of cache struct [struct], or null. */
    public fun structInt(struct: Int, param: Int): Int? =
        p.world.data.configs.structs[struct]
            ?.params
            ?.int(param)

    /** String param [param] of cache struct [struct], or null. */
    public fun structString(struct: Int, param: Int): String? =
        p.world.data.configs.structs[struct]
            ?.params
            ?.string(param)

    /** Int param [param] of obj [obj], or null. */
    public fun objParamInt(obj: Int, param: Int): Int? =
        p.world
            .objType(obj)
            ?.params
            ?.int(param)

    /** String param [param] of obj [obj], or null. */
    public fun objParamString(obj: Int, param: Int): String? =
        p.world
            .objType(obj)
            ?.params
            ?.string(param)

    /** Tradeable, unnoted objs whose name contains [query] (case-insensitive), at most [limit] (search boxes). */
    public fun tradeableObjsContaining(
        query: String,
        limit: Int = OBJ_SEARCH_LIMIT,
    ): Set<Int> = p.world.tradeableObjsContaining(query, limit)

    /** The unnoted form of [obj] (itself when not a note). */
    public fun unnoted(obj: Int): Int =
        p.world
            .objType(obj)
            ?.takeIf { it.isNoted }
            ?.certLink ?: obj

    /** The noted form of unnoted [obj], or -1 when it has none. */
    public fun noted(obj: Int): Int =
        p.world
            .objType(obj)
            ?.takeIf { !it.isNoted }
            ?.certLink ?: -1

    /** Whether [obj] stacks in a normal inventory (cache `stackable`; notes and placeholders follow their template). */
    public fun objStackable(obj: Int): Boolean = p.world.objType(obj)?.isStackable ?: false

    /** Whether [obj] is a bank placeholder (cache placeholder template set). */
    public fun isPlaceholder(obj: Int): Boolean = p.world.objType(obj)?.isPlaceholder ?: false

    /** The placeholder obj of real [obj] (cache `placeholderLink`), or -1 when it has none or [obj] is itself one. */
    public fun placeholderOf(obj: Int): Int =
        p.world
            .objType(obj)
            ?.takeIf { !it.isPlaceholder }
            ?.placeholderLink ?: -1

    /** The real obj a placeholder [obj] stands for, or -1 when [obj] is not a placeholder. */
    public fun placeholderTarget(obj: Int): Int =
        p.world
            .objType(obj)
            ?.takeIf { it.isPlaceholder }
            ?.placeholderLink ?: -1

    /** Whether [obj] can be traded on the Grand Exchange (cache flag). */
    public fun isGeTradeable(obj: Int): Boolean = p.world.objType(unnoted(obj))?.geTradeable == true

    /** Item value (kept on death, price checker): the GE guide price if the obj has traded, else cache `cost`. */
    public fun objValue(obj: Int): Int = p.world.guidePrices[obj] ?: p.world.objType(obj)?.cost ?: 0

    /** The cache `cost` of [obj] (the Wiki's "value": what shops and alchemy scale), never a Grand Exchange price. */
    public fun objCost(obj: Int): Int = p.world.objType(obj)?.cost ?: 0

    // ---- inventories

    /** Inventory [invId]; [stackAll] = every obj stacks (bank). */
    public fun inv(invId: Int, stackAll: Boolean = false): InvView =
        InvView(p.world.inventory(p, invId, if (stackAll) InvStack.ALWAYS else InvStack.NORMAL))

    /** Starts sending inventory [invId] to the client (full update at the end of this tick). */
    public fun transmitInv(invId: Int) {
        p.world.transmitInventory(p.world.inventory(p, invId))
    }

    /** One atomic, journaled item transaction (brief §12): all steps happen or none. */
    public fun transaction(reason: String, block: ItemTx.() -> Boolean): Boolean {
        var steps: ItemTx? = null
        val ok = p.world.transaction(p, reason) { ItemTx(this, this@PlayerRef).also { steps = it }.block() }
        if (ok) steps?.committed()
        return ok
    }

    override fun equals(other: Any?): Boolean = other is PlayerRef && other.p === p

    override fun hashCode(): Int = p.hashCode()

    override fun toString(): String = p.toString()

    private companion object {
        const val XP_TENTHS = 10

        /** Skilling success chances are out of 256 (Wiki "Skilling success rate"). */
        const val SUCCESS_SCALE = 256

        /** Rounding term of the success-rate interpolation. */
        const val HALF = 0.5
        const val ATT = 0
        const val DEF = 1
        const val STR = 2
        const val HP = 3
        const val RNG = 4
        const val PRAY = 5
        const val MAG = 6

        /** 13/40 (Combat level formula, CL:37-57). */
        const val MULT = 0.325
    }
}

/** Read-only view of an inventory. Change it with [PlayerRef.transaction]. */
public class InvView internal constructor(internal val inv: Inventory) {
    public val invId: Int get() = inv.invId
    public val size: Int get() = inv.size
    public val freeSlots: Int get() = inv.freeSlots

    public fun obj(slot: Int): Int = inv.obj(slot)

    public fun count(slot: Int): Int = inv.count(slot)

    public fun total(obj: Int): Long = inv.total(obj)
}

/** Steps of an item transaction; each returns false (and dooms the transaction) on failure. */
public class ItemTx internal constructor(private val tx: ItemTransaction, private val player: PlayerRef) {
    private fun of(invId: Int) = player.p.world.inventory(player.p, invId)

    private val afterCommit = ArrayList<() -> Unit>()

    /**
     * Runs [action] only if the whole transaction commits (after it): state kept outside inventories, such as the rune
     * pouch's varbits, changes together with the items or not at all.
     */
    public fun onCommit(action: () -> Unit) {
        afterCommit += action
    }

    internal fun committed() {
        for (action in afterCommit) action()
    }

    public fun add(invId: Int, obj: Int, count: Int = 1): Boolean = tx.add(of(invId), obj, count)

    public fun remove(invId: Int, obj: Int, count: Int = 1): Boolean = tx.remove(of(invId), obj, count)

    public fun removeAt(invId: Int, slot: Int, obj: Int, count: Int = 1): Boolean = tx.removeAt(of(invId), slot, obj, count)

    public fun swap(invId: Int, from: Int, to: Int): Boolean = tx.swap(of(invId), from, to)

    public fun move(fromInv: Int, slot: Int, obj: Int, count: Int, toInv: Int): Boolean = tx.move(of(fromInv), slot, obj, count, of(toInv))

    /**
     * Moves [count] of [obj] from [slot] of [from]'s [fromInv] into [to]'s [toInv], in this same transaction (trades:
     * both sides change hands atomically).
     */
    public fun moveTo(from: PlayerRef, fromInv: Int, slot: Int, obj: Int, count: Int, to: PlayerRef, toInv: Int): Boolean =
        tx.move(from.p.world.inventory(from.p, fromInv), slot, obj, count, to.p.world.inventory(to.p, toInv))

    public fun replaceAt(invId: Int, slot: Int, obj: Int, newObj: Int, count: Int = 1): Boolean =
        tx.replaceAt(of(invId), slot, obj, newObj, count)

    public fun setAt(invId: Int, slot: Int, obj: Int, count: Int = 1): Boolean = tx.setAt(of(invId), slot, obj, count)

    /** Drops [count] of [obj] from [slot] of [invId] under the player, private to them at first. */
    public fun drop(invId: Int, slot: Int, obj: Int, count: Int): Boolean =
        tx.dropToGround(of(invId), slot, obj, count, player.coord, player.username)

    /** Drops [count] of [obj] from [slot] of [invId] onto [at], private to [owner] at first (death drops). */
    public fun dropAt(invId: Int, slot: Int, obj: Int, count: Int, at: CoordGrid, owner: String): Boolean =
        tx.dropToGround(of(invId), slot, obj, count, at, owner)

    /**
     * Creates [count] of [obj] on the ground at [at], private to [owner] first; [lifetime] in ticks overrides the
     * default drop lifetime (e.g. marks of grace 1000); -1 = default.
     */
    public fun createOnGround(obj: Int, count: Int, at: CoordGrid, owner: String, lifetime: Int = -1): Boolean =
        tx.createOnGround(obj, count, at, owner, lifetime)

    /** Creates [count] of [obj] on the ground at [at], visible to everyone at once (a destroyed looting bag's contents). */
    public fun createOnGroundPublic(obj: Int, count: Int, at: CoordGrid): Boolean = tx.createOnGround(obj, count, at, null)

    /** Adds to [invId], or drops at the player's feet (private to them) if it doesn't fit. */
    public fun addOrDrop(invId: Int, obj: Int, count: Int): Boolean =
        tx.addOrDrop(of(invId), obj, count, player.p.coord, player.p.username)

    /** Picks [obj] up into [invId]. */
    public fun take(obj: ObjRef, invId: Int): Boolean = tx.takeFromGround(obj.g, of(invId))
}

/** A ground obj the player can see. */
public class ObjRef internal constructor(internal val g: GroundObj) {
    public val id: Int get() = g.id
    public val count: Int get() = g.count
    public val coord: CoordGrid get() = g.coord
}

/** World-level operations for content (spawns, cache data). */
public class WorldRef internal constructor(internal val w: World) {
    /** World-scoped content state (one value per world; plugin instances are shared by every world in the JVM). */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> worldAttr(key: AttrKey<T>): T? = w.attrs[key] as T?

    public fun <T : Any> setWorldAttr(key: AttrKey<T>, value: T?) {
        if (value == null) w.attrs.remove(key) else w.attrs[key] = value
    }

    public val tick: Long get() = w.tick

    /** Whether a boat may occupy [tile]: sea with no blocking loc (sailing-movement.md SM1, SM5). */
    public fun isSailable(tile: CoordGrid): Boolean =
        w.data.locs.isSea(tile.x, tile.z) &&
            w.collision.flags[tile.x, tile.z, 0] and org.rsmod.routefinder.flag.CollisionFlag.LOC == 0

    /** Int values of cache enum [id] (key → value); empty if absent. */
    public fun enumInts(id: Int): Map<Int, Int> =
        w.data.configs.enums[id]
            ?.intValues
            .orEmpty()

    /** String values of cache enum [id] (key → value); empty if absent. */
    public fun enumStrings(id: Int): Map<Int, String> =
        w.data.configs.enums[id]
            ?.stringValues
            .orEmpty()

    public fun objParamInt(obj: Int, param: Int): Int? = w.objType(obj)?.params?.int(param)

    /** Puts loc [id] on the tile for this world (replacing its layer); reverts after [duration] ticks if positive. */
    public fun addLoc(id: Int, at: CoordGrid, shape: Int, angle: Int, duration: Int = -1) {
        w.locs.add(id, at, shape, angle, w.tick, duration)
    }

    /** Removes the loc of [shape]'s layer on the tile for this world; reverts after [duration] ticks if positive. */
    public fun deleteLoc(at: CoordGrid, shape: Int, duration: Int = -1) {
        w.locs.delete(at, shape, w.tick, duration)
    }

    /** Obj ids with exactly this cache name (unnoted, not placeholders). */
    public fun objIdsNamed(name: String): List<Int> = w.objIdsNamed(name)

    public fun objParamString(obj: Int, param: Int): String? = w.objType(obj)?.params?.string(param)

    /** Rows of cache db table [table] (`dbrow` group, `DbTables.*.ID`), in row-id order; empty if absent. */
    public fun dbRows(table: Int): List<DbRowRef> =
        w.data.configs.dbRows
            .filter { it.tableId == table }
            .map { DbRowRef(it) }

    /** A static ground item spawn (ground-items.md G6): always there, back [respawnTicks] after someone takes it. */
    public fun spawnGroundObj(obj: Int, count: Int, at: CoordGrid, respawnTicks: Int) {
        w.ground.spawnStatic(obj, count, at, respawnTicks)
    }

    /** Spawns NPC [type] at [at], wandering up to [wanderRange] tiles from it (random-walk.md). */
    public fun spawnNpc(type: Int, at: CoordGrid, wanderRange: Int) {
        w.spawnNpc(type, at, wanderRange)
    }
}

/** One cache db row; [column] ids are `DbTables.<Table>.COL_*`. */
public class DbRowRef internal constructor(private val row: tp.cache.config.dbrow.DbRowType) {
    public val id: Int get() = row.id

    /** Int cell of [column] (tuple [field], component [component]), or null if the row leaves the column unset. */
    public fun int(column: Int, field: Int = 0, component: Int = 0): Int? {
        val values = row.columns[column] ?: return null
        if (field >= values.fieldCount) return null
        return values.get(field, component) as? Int
    }

    /** String cell of [column], or null if unset. */
    public fun string(column: Int, field: Int = 0, component: Int = 0): String? {
        val values = row.columns[column] ?: return null
        if (field >= values.fieldCount) return null
        return values.get(field, component) as? String
    }
}

/** A map loc an interaction targets. */
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_SECOND = 1_000L

/** Most objs one name search returns. */
private const val OBJ_SEARCH_LIMIT = 500

/** First 8 bytes of SHA-256 over the address. */
private fun addressHash(address: String): Long {
    val d =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(address.toByteArray(Charsets.UTF_8))
    return java.nio.ByteBuffer
        .wrap(d)
        .long
}

/** Client chat message types (RuneLite `ChatMessageType` TRADEREQ / CHALREQ_TRADE). */
private const val TRADE_REQUEST_TYPE = 101
private const val DUEL_REQUEST_TYPE = 103

public class LocRef internal constructor(public val id: Int, public val coord: CoordGrid, public val shape: Int, public val angle: Int)

/** Identifies a player timer. Define as a top-level `val`. */
public class TimerId(name: String) {
    internal val key: tp.engine.script.TimerKey = tp.engine.script.TimerKey(name)
}

/** Grand Exchange requests (hub skeleton). */
public sealed interface GeRequest {
    public data object Offers : GeRequest

    /** Coins (buy) or items (sell) must already be removed through a transaction. */
    public class Place(
        public val slot: Int,
        public val buy: Boolean,
        public val obj: Int,
        public val quantity: Int,
        public val price: Int,
        /** Sell tax in tenths of a percent (0 = exempt). */
        public val taxRate: Int = 0,
    ) :
        GeRequest

    /** Collects the item part, the coin part, or both. */
    public class Collect(public val slot: Int, public val items: Boolean = true, public val coins: Boolean = true) : GeRequest

    public class Abort(public val slot: Int) : GeRequest

    /** Trade history, newest first: each row's [GeOffer.completed] is the amount traded, [GeOffer.gold] the coins. */
    public data object History : GeRequest
}

/** A Grand Exchange offer ([collectCoins]/[collectItems]: waiting to be collected, or returned by an abort). */
public class GeOffer(
    public val slot: Int,
    public val buy: Boolean,
    public val obj: Int,
    public val quantity: Int,
    public val price: Int,
    public val completed: Int,
    public val collectCoins: Long,
    public val collectItems: Int,
    /** Coins exchanged so far (spent buying, received selling). */
    public val gold: Long = 0,
    public val aborted: Boolean = false,
    /** Sell tax paid so far (history: the row's fee). */
    public val tax: Long = 0,
)

/** Client scripts that open chatbox input dialogs (cache clientscript names; RS Mod `ClientScripts.mesLayerMode7/9`). */
private const val MESLAYER_COUNT = 108

/** `popupoverlay` and its init script (RS Mod `ClientScripts.confirmOverlayInit`). */
private const val POPUP_OVERLAY = 289
private const val CS_CONFIRM_OVERLAY = 4212
private const val MESLAYER_STRING = 110
private const val OBJ_SEARCH = 750

/** Client script 58 `chatbox_multi_init` (title, choices joined by `|`; RS Mod `ClientScripts.chatboxMultiInit`). */
private const val CHATBOX_MULTI_INIT = 58
private const val CHOICE_SEPARATOR = "|"
private const val MIN_CHOICES = 2

/** RS Mod offers menus of up to 5 (`choice5`); the Skills necklace has 6 destinations (Wiki), so 6 are allowed (OPEN: layout). */
private const val MAX_CHOICES = 6

/**
 * Friends chat settings. A null [name] means the channel is disabled. Ranks are minimums: -1 anyone, 0 any friends,
 * 1..6 recruit+..general+, 7 only the owner.
 */
public class FriendsChatSettings(public val name: String?, public val enterRank: Int, public val talkRank: Int, public val kickRank: Int)

/** Wiki "Energy": run can be switched on from 1% = 100 units. */
private const val MIN_RUN_ENERGY = 100
