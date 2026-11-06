package tp.tools.scenario

import tp.engine.TickTracer
import tp.engine.entity.Entity
import tp.engine.entity.Npc
import tp.protocol.api.ClientCommand
import tp.protocol.api.Visual
import tp.symbols.Components
import tp.util.CoordGrid

/** Shared setup for the golden scenarios (brief §9 test 3). */
internal object Golden {
    /** Level-11 Wilderness (z 3600), open ground east of the Graveyard of Shadows (wilderness.md OPEN-W1 bounds). */
    fun wild(dx: Int = 0, dz: Int = 0): CoordGrid = CoordGrid(3150 + dx, 3600 + dz)

    const val ATTACK_OP = 2
    const val ITEM_OP1 = 2
    const val MAX_XP = 13_034_431

    /** Admits players and runs one settle tick (Wilderness op, gameframe). */
    fun Scenario.start(vararg players: ScenarioPlayer) {
        forcePid(*players)
        tick(2)
    }

    fun ScenarioPlayer.cheat(text: String) = input(ClientCommand.Cheat(text))

    fun ScenarioPlayer.attack(other: ScenarioPlayer) = input(ClientCommand.OpPlayer(ATTACK_OP, other.player.slot, false))

    /** Backpack op 1 on [slot] (wield/eat/drink). */
    fun ScenarioPlayer.itemOp1(slot: Int) {
        val inv = player.world.inventory(player, tp.symbols.Invs.INV)
        input(ClientCommand.Button(Components.Inventory.ITEMS, ITEM_OP1, slot, inv.obj(slot)))
    }

    fun ScenarioPlayer.button(component: Int, op: Int = 1) = input(ClientCommand.Button(component, op, -1, -1))

    /** Ticks on which this player's avatar received hitsplats (one entry per hitsplat). */
    fun ScenarioPlayer.hitTicks(): List<Long> = session.visuals.filter { it.second is Visual.Hit }.map { it.first }

    fun ScenarioPlayer.hits(): List<Pair<Long, Visual.Hit>> = session.visuals.mapNotNull { (t, v) -> (v as? Visual.Hit)?.let { t to it } }

    fun Scenario.npcHitTicks(npc: Npc): List<Long> =
        network.npcVisuals
            .filter {
                it.second == npc.index && it.third is Visual.Hit
            }.map { it.first }

    /** Records "attack ..." trace labels per entity. */
    class AttackLog : TickTracer {
        var tick = 0L
        val attacks = ArrayList<Pair<Long, Entity>>()

        override fun onTickStart(tick: Long) {
            this.tick = tick
        }

        override fun onPhase(entity: Entity, phase: String) {
            if (phase.startsWith("attack")) attacks += tick to entity
        }

        fun ticksOf(entity: Entity): List<Long> = attacks.filter { it.second === entity }.map { it.first }
    }

    fun Scenario.attackLog(): AttackLog = AttackLog().also { world.tracer = it }
}
