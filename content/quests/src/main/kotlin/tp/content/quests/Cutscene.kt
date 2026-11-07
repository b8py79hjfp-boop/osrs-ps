package tp.content.quests

import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.util.CoordGrid

// Staging of the quest cutscenes (docs/mechanics/quests.md QU13): minimap hidden, camera calls, temporary NPCs that walk and speak.

/** Minimap state hidden (plugin-api `PlayerRef.minimapState`: 0 enabled, 2 hidden). */
private const val MINIMAP_HIDDEN = 2
private const val MINIMAP_ENABLED = 0

/**
 * The camera heights of the staged scenes, in client units. OPEN-QUC5: the Wiki names scenes, not camera placement, so heights,
 * rates and angles are ours.
 */
internal object CamHeight {
    const val EYE = 200
    const val HIGH = 450
    const val LOW = 120
}

/** One running cutscene of [p]: the NPCs it spawned are removed and the camera is returned when it [end]s. */
internal class Scene(val p: PlayerRef) {
    private val actors = ArrayList<NpcRef>()

    fun begin() {
        p.stopInteraction()
        p.minimapState(MINIMAP_HIDDEN)
    }

    /** The camera stands at [from] and looks at [at] (both snap at once unless [rate] is given). */
    fun shot(from: CoordGrid, at: CoordGrid, fromHeight: Int = CamHeight.HIGH, atHeight: Int = CamHeight.LOW, rate: Int = 0) {
        p.camMoveTo(from, fromHeight, rate, rate)
        p.camLookAt(at, atHeight, rate, rate)
    }

    /** Spawns a scene NPC of [type] at [at]; it is removed by [end]. */
    fun actor(type: Int, at: CoordGrid): NpcRef {
        val npc = p.spawnNpc(type, at)
        actors += npc
        return npc
    }

    /** Removes one scene NPC before the end. */
    fun remove(npc: NpcRef) {
        if (npc.isInWorld) npc.despawn()
        actors -= npc
    }

    fun end() {
        for (npc in actors) if (npc.isInWorld) npc.despawn()
        actors.clear()
        p.camReset()
        p.minimapState(MINIMAP_ENABLED)
    }
}

/** Runs [block] as a cutscene; the camera, minimap and scene NPCs are restored even when the script is cancelled (logout). */
internal suspend fun PlayerScript.scene(block: suspend PlayerScript.(Scene) -> Unit) {
    val scene = Scene(player)
    scene.begin()
    try {
        block(scene)
    } finally {
        scene.end()
    }
}

/** [npc] walks to [to] and the script waits [ticks] for it to arrive. */
internal suspend fun PlayerScript.walkNpc(npc: NpcRef, to: CoordGrid, ticks: Int) {
    npc.walkTo(to)
    delay(ticks)
}
