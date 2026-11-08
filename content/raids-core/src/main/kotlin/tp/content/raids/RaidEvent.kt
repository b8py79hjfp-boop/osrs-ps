package tp.content.raids

import tp.plugin.Event

/** Lifecycle events on the event bus (brief 3.5): content and other plugins subscribe to these. */
public sealed class RaidEvent(public val run: RaidRun) : Event {
    public class Entered(run: RaidRun, public val member: String) : RaidEvent(run)

    public class Left(run: RaidRun, public val member: String) : RaidEvent(run)

    public class Died(run: RaidRun, public val member: String) : RaidEvent(run)

    public class Disconnected(run: RaidRun, public val member: String) : RaidEvent(run)

    public class Reconnected(run: RaidRun, public val member: String) : RaidEvent(run)

    public class RoomStarted(run: RaidRun, public val room: RoomDef) : RaidEvent(run)

    public class RoomCleared(run: RaidRun, public val room: RoomDef, public val ticks: Long) : RaidEvent(run)

    public class RoomFailed(run: RaidRun, public val room: RoomDef) : RaidEvent(run)

    public class Completed(run: RaidRun) : RaidEvent(run)
}
