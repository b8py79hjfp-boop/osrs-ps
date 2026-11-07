package tp.plugin

/**
 * [player] was given [count] of [obj] by a loot source (an NPC drop made for them, a chest, a reward). Published by the
 * source once the obj exists (docs/mechanics/collection-log.md CLOG3); [obj] may be the noted form. The collection log
 * subscribes; other bookkeeping (drop notifications, loot trackers) can too.
 */
public class LootEvent(
    public val player: PlayerRef,
    public val obj: Int,
    public val count: Int,
) : Event
