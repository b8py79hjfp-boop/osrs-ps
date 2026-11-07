package tp.content.sailing

/**
 * One row of `Sailing_Crew` (db 173): a recruitable crewmate. The three stats are Helmsmanship, Privateering and
 * Deckhandiness (1..4). The id-to-name mapping is derived from the Wiki (docs/mechanics/sailing-crew.md C1); the world NPC,
 * quest requirement and order strings are server-only columns the cache strips.
 */
internal class CrewMember(
    val row: Int,
    val uniqueId: Int,
    /** NPC shown on a boat deck. */
    val boatNpc: Int,
    /** NPC variants that carry cargo on a dock. */
    val cargoNpcs: List<Int>,
    val sprite: Int?,
    val helmsmanship: Int,
    val privateering: Int,
    val deckhandiness: Int,
)

/**
 * One row of `Boat_Selection_Type` (db 149): a mode of the boat selection interface (934). Flags the cache leaves unset
 * read false (docs/plans/sailing.md 2.5).
 */
internal class BoatSelectionType(
    /** `TYPE`: 1 personal boats, 2 customise, 3 board, 4 repair, 5 recover, 6 and 7 summon, 8 teleport to, 9 destroy, 10 rename,
     * 11 bank cargo, 12 bottle.
     */
    val type: Int,
    val title: String,
    val selectionText: String?,
    val selectionEnabled: Boolean,
    val showRecentBoat: Boolean,
    val showRetrievalCost: Boolean,
    val showBoatHealth: Boolean,
    val showEmptyBoatSlots: Boolean,
    val showCargoHold: Boolean,
    val bottledNotAllowed: Boolean,
    val capsizedNotAllowed: Boolean,
    val lostNotAllowed: Boolean,
    val onlyBoatsAtCurrentPort: Boolean,
    val requiresTeleportFocus: Boolean,
    val requiresGreaterTeleportFocus: Boolean,
    val requiresEmptyCargoHold: Boolean,
)
