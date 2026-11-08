package tp.content.raids

/** A party id, unique per world (brief 3.4). */
@JvmInline
public value class PartyId(public val value: Int)

/** Party states: forming at a board, locked once its raid started, or gone. */
public sealed interface PartyState {
    public object Forming : PartyState

    public class Locked(public val run: RaidRun) : PartyState

    public object Disbanded : PartyState
}

/**
 * A party for one raid on one world (brief 3.4): a leader, ordered member slots, applicants, and leader-only settings
 * (ToA invocations, CoX scaling, TFA spectator toggle). Usernames, not player handles: a member who logs out stays a
 * member (TFA: since 28 Oct a party survives logout and wipes, tfa.md TFA5).
 */
public class Party internal constructor(
    public val id: PartyId,
    public val raid: RaidDefinition,
    leader: String,
) {
    public var state: PartyState = PartyState.Forming
        internal set

    private val slots = arrayListOf(leader)
    private val applying = LinkedHashSet<String>()
    public val settings: MutableMap<String, Int> = HashMap()

    public val leader: String get() = slots.first()
    public val members: List<String> get() = slots
    public val applicants: Set<String> get() = applying
    public val isFull: Boolean get() = slots.size >= raid.partySize.last

    public fun has(name: String): Boolean = slots.any { it.equals(name, ignoreCase = true) }

    internal fun forming(): Boolean = state == PartyState.Forming

    /** Applies to join (boards with applications); false when full, already in, or the party is locked. */
    public fun apply(name: String): Boolean = forming() && !isFull && !has(name) && applying.add(name)

    /** The leader accepts an applicant. */
    public fun accept(name: String): Boolean {
        if (!forming() || isFull || !applying.remove(name)) return false
        slots += name
        return true
    }

    public fun reject(name: String): Boolean = applying.remove(name)

    /** Joins directly (boards without applications). */
    public fun join(name: String): Boolean {
        if (!forming() || isFull || has(name)) return false
        applying.remove(name)
        slots += name
        return true
    }

    /** Leaves (or is kicked); the next member leads (brief 3.4). True when the party is now empty. */
    public fun leave(name: String): Boolean {
        slots.removeAll { it.equals(name, ignoreCase = true) }
        if (slots.isEmpty()) state = PartyState.Disbanded
        return slots.isEmpty()
    }

    /** Moves [name] to the leader slot. */
    public fun promote(name: String): Boolean {
        val i = slots.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (i <= 0) return false
        slots.add(0, slots.removeAt(i))
        return true
    }

    /** Swaps members [a] and [b] (leader reorder, ToB). */
    public fun reorder(a: Int, b: Int): Boolean {
        if (a !in slots.indices || b !in slots.indices) return false
        val t = slots[a]
        slots[a] = slots[b]
        slots[b] = t
        return true
    }
}
