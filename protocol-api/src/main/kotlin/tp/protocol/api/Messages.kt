package tp.protocol.api

/** Decoded client input, revision-independent. Every field is untrusted until the engine validates it (brief §12). */
sealed interface ClientCommand {
    /** Walk-here click on the game scene or minimap. [ctrl] = held run modifier (inverts run mode for this route). */
    data class MoveClick(val x: Int, val z: Int, val ctrl: Boolean, val minimap: Boolean) : ClientCommand

    /** `::command` typed in the chatbox (dev commands). */
    data class Cheat(val text: String) : ClientCommand

    /** Client finished loading the map after a rebuild. */
    data object MapBuildComplete : ClientCommand

    /** Loc op (1-based option). */
    data class OpLoc(val option: Int, val id: Int, val x: Int, val z: Int, val ctrl: Boolean) : ClientCommand

    /** NPC op by NPC-info index. */
    data class OpNpc(val option: Int, val index: Int, val ctrl: Boolean) : ClientCommand

    /** Player op by player index (option 1..8). */
    data class OpPlayer(val option: Int, val index: Int, val ctrl: Boolean) : ClientCommand

    /** Interface component (e.g. a spell) used on a player: [component] packed, [sub]/[obj] of the selection. */
    data class OpPlayerT(val index: Int, val component: Int, val sub: Int, val obj: Int, val ctrl: Boolean) : ClientCommand

    /** Interface component (e.g. a spell) used on an NPC. */
    data class OpNpcT(val index: Int, val component: Int, val sub: Int, val obj: Int, val ctrl: Boolean) : ClientCommand

    /** Ground item op. */
    data class OpObj(val option: Int, val id: Int, val x: Int, val z: Int, val ctrl: Boolean) : ClientCommand

    /** Interface component (item / spell) [component]/[sub]/[obj] used on loc [id] at ([x], [z]) (OPLOCT). */
    data class OpLocT(
        val id: Int,
        val x: Int,
        val z: Int,
        val component: Int,
        val sub: Int,
        val obj: Int,
        val ctrl: Boolean,
    ) : ClientCommand

    /** Interface component used on ground obj [id] at ([x], [z]) (OPOBJT). */
    data class OpObjT(
        val id: Int,
        val x: Int,
        val z: Int,
        val component: Int,
        val sub: Int,
        val obj: Int,
        val ctrl: Boolean,
    ) : ClientCommand

    /** Examine a loc / NPC type / ground obj by config id (OPLOC6 / OPNPC6 / OPOBJ6). [kind]: 0 loc, 1 npc, 2 obj. */
    data class Examine(val kind: Int, val id: Int) : ClientCommand

    /** SET_HEADING: the player at a boat's helm chose heading [heading] (0..15, the angle / 128; sailing-movement.md). */
    data class SetHeading(val heading: Int) : ClientCommand

    /** OPWORLDENTITY1..5: option [option] on world entity [index] (a boat). */
    data class OpWorldEntity(val option: Int, val index: Int, val ctrl: Boolean) : ClientCommand

    /** One component slot used on another (IF_BUTTONT: item on item, spell on item). */
    data class ButtonT(
        val fromComponent: Int,
        val fromSub: Int,
        val fromObj: Int,
        val toComponent: Int,
        val toSub: Int,
        val toObj: Int,
    ) : ClientCommand

    /**
     * IF3 button / inventory slot op. [component] is packed `iface << 16 | comp`; [sub] the slot, [obj] the item;
     * [subop] the right-click sub-option (IF_SUBOP, e.g. a jewellery "Rub" destination), -1 for a plain op.
     */
    data class Button(val component: Int, val option: Int, val sub: Int, val obj: Int, val subop: Int = -1) : ClientCommand

    /** An item dragged from one component slot onto another (IF_BUTTOND); subs/objs are -1 when absent. */
    data class ButtonDrag(
        val fromComponent: Int,
        val fromSub: Int,
        val fromObj: Int,
        val toComponent: Int,
        val toSub: Int,
        val toObj: Int,
    ) : ClientCommand

    /** Public chat line (already Huffman-decoded). */
    data class PublicChat(val text: String, val colour: Int, val effect: Int, val type: Int) : ClientCommand

    /** The chatbox filter buttons (RSProt `SetChatFilterSettings`): 0 on, 1 friends, 2 off (public 3 = hide). */
    data class ChatFilters(val publicChat: Int, val privateChat: Int, val trade: Int) : ClientCommand

    /** The client asks for a clan's full channel ([settings] false) or settings (CLAN*_FULL_REQUEST); [clanId] < 0 = guest. */
    data class ClanFullRequest(val clanId: Int, val settings: Boolean) : ClientCommand

    /** Friends/ignore list edits and private messages ([text] only for [SocialAction.PRIVATE_MESSAGE]). */
    data class Social(val action: SocialAction, val name: String, val text: String = "", val rank: Int = 0) : ClientCommand

    /** "Click here to continue" / pause button. */
    data class ResumePause(val component: Int, val sub: Int) : ClientCommand

    /** Answer to a string input dialog (RESUME_P_STRINGDIALOG). */
    data class ResumeString(val text: String) : ClientCommand

    /** Answer to a number input dialog (RESUME_P_COUNTDIALOG). */
    data class ResumeCount(val count: Int) : ClientCommand

    /** Answer to the chatbox item search (RESUME_P_OBJDIALOG); -1 when cancelled. */
    data class ResumeObj(val obj: Int) : ClientCommand

    /** Client closed its modal (Escape / close button). */
    data object CloseModal : ClientCommand

    /** Client window mode changed (1 fixed, 2 resizable). */
    data class WindowStatus(val mode: Int, val width: Int, val height: Int) : ClientCommand
}

enum class SocialAction {
    FRIEND_ADD,
    FRIEND_DEL,
    IGNORE_ADD,
    IGNORE_DEL,
    PRIVATE_MESSAGE,

    /** Friends chat: join [ClientCommand.Social.name]'s channel. */
    CHANNEL_JOIN,
    CHANNEL_LEAVE,

    /** Friends chat line ([ClientCommand.Social.text]; public chat type 2). */
    CHANNEL_MESSAGE,
    CHANNEL_KICK,

    /** Owner ranks a friend ([ClientCommand.Social.rank]). */
    CHANNEL_RANK,

    /** Clan chat line (public chat type 3; "//" in the client). */
    CLAN_MESSAGE,

    /** Guest clan chat line (public chat type 4; "///"). */
    CLAN_GUEST_MESSAGE,

    /** Clan channel kick of [ClientCommand.Social.name] (CLANCHANNEL_KICKUSER). */
    CLAN_KICK,

    /** Ban from the channel's member menu (AFFINEDCLANSETTINGS_ADDBANNED_FROMCHANNEL). */
    CLAN_BAN,
}

/** A friends chat member row ([rank]: -1 guest, 0 friend, 1..6 recruit..general, 7 owner, 127 Jagex moderator). */
data class ChannelRow(val name: String, val worldId: Int, val rank: Int)

/** A clan channel member: rank (-1 guest) and world. */
data class ClanRow(val name: String, val rank: Int, val worldId: Int)

/** A ranked clan member as the clan settings carry it ([joinedDay]: rune day). */
data class ClanAffined(val name: String, val rank: Int, val joinedDay: Int)

/** A friends-list row: [worldId] 0 = offline. */
data class FriendRow(val name: String, val worldId: Int, val added: Boolean, val rank: Int = 0)

/** One operation inside a zone; coordinates are 0..7 within the zone. */
sealed interface ZoneOp {
    data class ObjAdd(val x: Int, val z: Int, val id: Int, val count: Int) : ZoneOp

    data class ObjDel(val x: Int, val z: Int, val id: Int, val count: Int) : ZoneOp

    /** Adds loc [id] or replaces the loc on its layer (LOC_ADD_CHANGE). */
    data class LocAddChange(val x: Int, val z: Int, val id: Int, val shape: Int, val angle: Int) : ZoneOp

    /** A spot anim [id] on a tile (MAP_ANIM), [height] above the ground, starting after [delay] client cycles. */
    data class MapAnim(val x: Int, val z: Int, val id: Int, val height: Int, val delay: Int) : ZoneOp

    /** Removes the loc of [shape]'s layer (LOC_DEL). */
    data class LocDel(val x: Int, val z: Int, val shape: Int, val angle: Int) : ZoneOp

    /**
     * A projectile (spot anim [id]) from ([x], [z]) in this zone to the absolute tile ([endX], [endZ], [endLevel]),
     * locked to [source]/[target] if given. Times are client cycles (20 ms) from receipt.
     */
    data class Projectile(
        val x: Int,
        val z: Int,
        val id: Int,
        val startHeight: Int,
        val endHeight: Int,
        val startTime: Int,
        val endTime: Int,
        val angle: Int,
        val progress: Int,
        val source: AvatarRef?,
        val target: AvatarRef?,
        val endX: Int,
        val endZ: Int,
        val endLevel: Int,
    ) : ZoneOp
}

/** Outgoing messages, revision-independent. Info packets and rebuilds are handled by the session itself. */
sealed interface ServerMessage {
    companion object {
        /** [ZoneReset.world] / [ZoneOps.world] of the root world. */
        const val ROOT: Int = -1
    }

    /**
     * A chatbox line: [type] is the client's message type (0 game; 101 trade request and 103 duel request, which the
     * client makes clickable for [name]).
     */
    data class GameMessage(val text: String, val type: Int = 0, val name: String? = null) : ServerMessage

    /** The chat filter settings the client shows on its chatbox buttons (CHAT_FILTER_SETTINGS + the private one). */
    data class ChatFilters(val publicChat: Int, val privateChat: Int, val trade: Int) : ServerMessage

    /** Root (toplevel) interface. */
    data class OpenTop(val interfaceId: Int) : ServerMessage

    /** Opens [interfaceId] inside the parent component [destination] (packed `iface << 16 | comp`). */
    data class OpenSub(val destination: Int, val interfaceId: Int, val overlay: Boolean) : ServerMessage

    /** Closes whatever is open in [destination] (packed component). */
    data class CloseSub(val destination: Int) : ServerMessage

    /** Allowed ops/events on a component's slot range (IF_SETEVENTS). */
    data class InterfaceEvents(val component: Int, val start: Int, val end: Int, val events: Long) : ServerMessage

    /** Sets a client varp. */
    data class Varp(val id: Int, val value: Int) : ServerMessage

    /** Sets a client 64-bit varp (VARP_LONG; e.g. `ge_newoffer_price`). The server keeps no copy. */
    data class VarpLong(val id: Int, val value: Long) : ServerMessage

    /** Zeroes every client varp (sent at login before transmitting server values). */
    data object VarpReset : ServerMessage

    /**
     * Inventory contents for [invId] (the client binds inventories to components itself, by id). If [slots] is null this
     * is a full update of [objs]/[counts] (index = slot); otherwise a partial update of only those slots. Empty: obj -1.
     */
    class Inventory(
        val invId: Int,
        val objs: IntArray,
        val counts: IntArray,
        val slots: IntArray?,
        /** The partner's copy of the inventory (client `invother`, e.g. the other side of a trade). */
        val other: Boolean = false,
    ) : ServerMessage

    /** Skill level/xp (stat id per the client's stat order). */
    data class Stat(val stat: Int, val level: Int, val baseLevel: Int, val xp: Int) : ServerMessage

    /** Run energy, 0..10000 (client shows /100). */
    data class RunEnergy(val energy: Int) : ServerMessage

    /** Runs client script [id] with [args] (Int or String). */
    data class ClientScript(val id: Int, val args: List<Any>) : ServerMessage

    /** Right-click option [slot] (1..8) on other players. */
    data class PlayerOption(val slot: Int, val text: String?, val top: Boolean) : ServerMessage

    /** Map rebuild centred on the player's current position (sent when the build area changes). */
    data class Rebuild(val x: Int, val z: Int) : ServerMessage

    /**
     * REBUILD_REGION: an instanced build area centred on the tile ([x], [z]) (docs/mechanics/instances.md IN4). [zones]
     * holds 4 levels × 13 × 13 reference zones (level, then zone x, then zone z, from the centre zone − 6): each is
     * `rotation shl 1 | zoneZ shl 3 | zoneX shl 14 | level shl 24` of the template zone to draw there, or -1 for none.
     * [reload] forces the client to rebuild even if the centre did not change.
     */
    class RebuildRegion(val x: Int, val z: Int, val reload: Boolean, val zones: IntArray) : ServerMessage

    /**
     * SET_INTERACTION_MODE for [world] (a world entity's index, or -1 the root world, -2 every world): what a click on a
     * tile does ([tileMode] 0 disabled, 1 walk, 2 heading) and on an entity ([entityMode] 0 disabled, 1 enabled,
     * 2 examine only). RSProt `SetInteractionMode`.
     */
    data class InteractionMode(val world: Int, val tileMode: Int, val entityMode: Int) : ServerMessage

    /**
     * Zone header (full): the client clears the zone at ([dx], [dz]) from the build-area origin, then the following ops
     * refill it. [world] is [ROOT], or a world entity's index: then ([dx], [dz]) count from its deck's base and the
     * session sends it inside that world's block of the info packets (docs/mechanics/world-entities.md WE6).
     */
    data class ZoneReset(val dx: Int, val dz: Int, val level: Int, val world: Int = ROOT) : ServerMessage

    /** A batch of [ops] for the zone at ([dx], [dz]) from the build-area origin (one enclosed zone packet); [world] as for [ZoneReset]. */
    class ZoneOps(val dx: Int, val dz: Int, val level: Int, val ops: List<ZoneOp>, val world: Int = ROOT) : ServerMessage

    /** System update countdown in ticks (0 clears it): the client shows "System update in: m:ss". */
    data class SystemUpdate(val ticks: Int) : ServerMessage

    data object Logout : ServerMessage

    /** IF_SETTEXT on packed [component] (`iface << 16 | comp`). */
    data class IfText(val component: Int, val text: String) : ServerMessage

    /** IF_SETHIDE on packed [component]. */
    data class IfHide(val component: Int, val hidden: Boolean) : ServerMessage

    /** IF_SETOBJECT: shows [obj] x[count] in packed [component]. */
    data class IfObject(val component: Int, val obj: Int, val count: Int) : ServerMessage

    /** Chat head model of NPC type [npc] on [component] (IF_SETNPCHEAD). */
    data class IfNpcHead(val component: Int, val npc: Int) : ServerMessage

    /** The player's own chat head on [component] (IF_SETPLAYERHEAD). */
    data class IfPlayerHead(val component: Int) : ServerMessage

    /** Animation [seq] (-1 none) on a model component (IF_SETANIM): chat head expressions. */
    data class IfAnim(val component: Int, val seq: Int) : ServerMessage

    /** Plays music track [id] (MIDI_SONG; fades in client cycles, 0 = cut). */
    data class MusicTrack(val id: Int, val fadeOut: Int = 0, val fadeIn: Int = 0) : ServerMessage

    /** A short jingle (MIDI_JINGLE) over the current track. */
    data class Jingle(val id: Int) : ServerMessage

    /**
     * One Grand Exchange slot (UPDATE_STOCKMARKET_SLOT). [status] 0 clears the slot (the other fields are ignored);
     * [gold] is the coins spent (buy) or received (sell) so far.
     */
    data class StockMarketSlot(
        val slot: Int,
        val status: Int,
        val obj: Int,
        val price: Int,
        val count: Int,
        val completed: Int,
        val gold: Int,
    ) : ServerMessage

    /** Friends list rows (full list at login, single rows on changes). */
    data class FriendList(val friends: List<FriendRow>) : ServerMessage

    /** Ignore list: [names] added (or, if [removed], taken off). */
    data class IgnoreList(val names: List<String>, val removed: Boolean = false) : ServerMessage

    /** Ends the friends tab's "Loading friend list" state. */
    data object FriendListLoaded : ServerMessage

    /** Incoming PM: [counter] makes each message unique per world (client dedup); [crown] 0 none, 1 mod, 2 admin. */
    data class PrivateMessage(val from: String, val worldId: Int, val counter: Int, val crown: Int, val text: String) : ServerMessage

    /** "To [to]: [text]" echo of a sent PM. */
    data class PrivateMessageEcho(val to: String, val text: String) : ServerMessage

    /** Friends chat: joined [channelName] (owned by [owner]) with its full member list. */
    data class ChannelJoined(val owner: String, val channelName: String, val kickRank: Int, val members: List<ChannelRow>) :
        ServerMessage

    /** Friends chat: left the channel (the client clears it). */
    data object ChannelLeft : ServerMessage

    /** Friends chat: one member joined/changed rank, or left ([removed]). */
    data class ChannelMember(val member: ChannelRow, val removed: Boolean) : ServerMessage

    /** Friends chat line from [from] in [channelName] ([crown] 0 none, 1 mod, 2 admin). */
    data class ChannelMessage(
        val from: String,
        val channelName: String,
        val worldId: Int,
        val counter: Int,
        val crown: Int,
        val text: String,
    ) :
        ServerMessage

    /**
     * Clan channel (docs/mechanics/clans.md CL9): the full member list of the player's clan, or of the clan they
     * listen to as a guest ([guest]); [members] rank -1 = guest, world 0 = offline (not sent). [updateNum] grows
     * with every change.
     */
    data class ClanChannel(
        val guest: Boolean,
        val clanName: String,
        val kickRank: Int,
        val talkRank: Int,
        val members: List<ClanRow>,
        val updateNum: Long,
    ) : ServerMessage

    /** Clan channel left: the client clears that clan's chat. */
    data class ClanChannelLeft(val guest: Boolean) : ServerMessage

    /** Clan settings (CL10): the ranked members (rank, rune day joined), the ban list and the channel ranks. */
    data class ClanSettings(
        val guest: Boolean,
        val clanName: String,
        val createdMinute: Int,
        val allowGuests: Boolean,
        val talkRank: Int,
        val kickRank: Int,
        val members: List<ClanAffined>,
        val banned: List<String>,
        val updateNum: Int,
    ) : ServerMessage

    /** Clan settings cleared (left the clan). */
    data class ClanSettingsLeft(val guest: Boolean) : ServerMessage

    /** Clan chat line ([crown] 0 none, 1 mod, 2 admin; [counter] as [PrivateMessage]). */
    data class ClanMessage(val guest: Boolean, val from: String, val worldId: Int, val counter: Int, val crown: Int, val text: String) :
        ServerMessage

    /** Clan system line (joins, leaves, rank changes) in the clan chat. */
    data class ClanSystemMessage(val guest: Boolean, val worldId: Int, val counter: Int, val text: String) : ServerMessage

    /** World hop: the client logs out and connects to [host] as world [worldId] ([flags]: world-list properties). */
    data class LogoutTransfer(val host: String, val worldId: Int, val flags: Int) : ServerMessage
}
