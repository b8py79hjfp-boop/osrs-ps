package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Varbits
import tp.util.CoordGrid

/** The five wizards who teleport a player to the Rune Essence Mine (Wiki "Rune Essence Mine", Transportation). */
internal enum class EssenceTeleporter(
    /** Varbit that remembers the scrying orb reading of this origin (cache `rcu_essencespot_*`). */
    val spotVarbit: Int,
    /** Value of the exit portal varbit [Varbits.ESSENCEMINE_PORTAL] that shows a portal ("Use"/"Exit" child) for this origin (OPEN-EM1). */
    val portalValue: Int,
    /** Which of the four essence outcrops the player arrives beside (OPEN-EM1). */
    val outcrop: Int,
) {
    WIZARDS_TOWER(Varbits.RCU_ESSENCESPOT_WIZARDSTOWER, 1, 0),
    AUBURY(Varbits.RCU_ESSENCESPOT_AUBURY, 0, 1),
    CROMPERTY(Varbits.RCU_ESSENCESPOT_CROMPERTY, 3, 2),
    BRIMSTAIL(Varbits.RCU_ESSENCESPOT_BRIMSTAIL, 4, 3),
    WIZARDS_GUILD(Varbits.RCU_ESSENCESPOT_WIZARDSGUILD, 7, 0),
}

/**
 * The Rune Essence Mine teleport (docs/mechanics/quest-enter-the-abyss.md EA1-EA3): Aubury, Archmage Sedridor, Wizard Cromperty,
 * Brimstail and Wizard Distentor teleport a player who has finished Rune Mysteries, shouting the incantation; a portal beside each
 * outcrop returns the player to the wizard who sent them. Teleporting with the Mage of Zamorak's scrying orb in the backpack
 * charges it (Enter the Abyss).
 */
public class EssenceMineTeleports : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.AUBURY, OP_AUBURY) { sendFrom(it, EssenceTeleporter.AUBURY) }
        onOpNpc(Npcs.HEAD_WIZARD, OP_WIZARD) { sendFrom(it, EssenceTeleporter.WIZARDS_TOWER) }
        for (cromperty in intArrayOf(Npcs.CROMPERTY_PRE_DIARY, Npcs.CROMPERTY_POST_DIARY)) {
            onOpNpc(cromperty, OP_WIZARD) { sendFrom(it, EssenceTeleporter.CROMPERTY) }
            onOpNpc(cromperty, OP_TALK) { talkTeleporter(it, EssenceTeleporter.CROMPERTY, CROMPERTY_GREETING) }
        }
        onOpNpc(Npcs.GNOME_BRIMSTAIL, OP_WIZARD) { sendFrom(it, EssenceTeleporter.BRIMSTAIL) }
        onOpNpc(Npcs.GNOME_BRIMSTAIL, OP_TALK) { talkTeleporter(it, EssenceTeleporter.BRIMSTAIL, BRIMSTAIL_GREETING) }
        onOpNpc(Npcs.GUILD_WIZARD, OP_WIZARD) { sendFrom(it, EssenceTeleporter.WIZARDS_GUILD) }
        onOpNpc(Npcs.GUILD_WIZARD, OP_TALK) { talkTeleporter(it, EssenceTeleporter.WIZARDS_GUILD, DISTENTOR_GREETING) }
        onOpLoc(Locs.BLANKRUNESTONE_EXIT_PORTAL_2, OP_PORTAL) { leave(player) }
        // Brimstail (Wiki "Brimstail" map 2409,9817 r=2) and Wizard Distentor ("Wizard Distentor" map 2591,3088 r=6) are not in
        // npc-spawns.tsv.
        onWorldStart {
            spawnNpc(Npcs.GNOME_BRIMSTAIL, CoordGrid(BRIMSTAIL_X, BRIMSTAIL_Z, 0), BRIMSTAIL_WANDER)
            spawnNpc(Npcs.GUILD_WIZARD, CoordGrid(DISTENTOR_X, DISTENTOR_Z, 0), DISTENTOR_WANDER)
        }
    }

    private fun PlayerScript.sendFrom(wizard: NpcRef, from: EssenceTeleporter) {
        player.stopInteraction()
        if (!Quests.RUNE_MYSTERIES.atLeast(player, RUNE_MYSTERIES_DONE)) return player.message("Nothing interesting happens.")
        wizard.say(INCANTATION)
        teleportFrom(player, from, wizard.coord)
    }

    /** Talk-to of the wizards who only have the teleport as a transcribed branch (OPEN-EM2: their other branches are not implemented). */
    private suspend fun PlayerScript.talkTeleporter(wizard: NpcRef, from: EssenceTeleporter, greeting: String) {
        player.stopInteraction()
        npc(wizard, greeting)
        val done = Quests.RUNE_MYSTERIES.atLeast(player, RUNE_MYSTERIES_DONE)
        val pick =
            if (done) {
                choice(
                    "Select an option",
                    "Can you teleport me to the Rune Essence?",
                    "Nothing thanks, I'm just looking around.",
                )
            } else {
                2
            }
        if (pick == 1) {
            me("Can you teleport me to the Rune Essence?")
            wizard.say(INCANTATION)
            teleportFrom(player, from, wizard.coord)
        } else {
            me("Nothing thanks, I'm just looking around.")
        }
    }

    private fun leave(p: PlayerRef) {
        val back = p.saved(RETURN)?.let { CoordGrid(it.toInt()) } ?: p.coord
        p.teleport(back)
    }

    internal companion object {
        const val OP_TALK = 1
        const val OP_WIZARD = 3
        const val OP_AUBURY = 4
        const val OP_PORTAL = 1

        /** Wiki "Rune Essence Mine": "Senventior disthine molenko!". */
        const val INCANTATION = "Senventior Disthine Molenko!"

        /** Rune Mysteries' end stage (varp 63). */
        const val RUNE_MYSTERIES_DONE = 6

        /** Account value: the packed tile of the wizard who teleported the player to the mine, where the portals lead back. */
        const val RETURN = "quests.essencemine.return"

        /** Wiki NPC pages: Brimstail 2409,9817 r=2; Wizard Distentor 2591,3088 r=6 (wander capped at 5, QU9). */
        const val BRIMSTAIL_X = 2409
        const val BRIMSTAIL_Z = 9817
        const val BRIMSTAIL_WANDER = 2
        const val DISTENTOR_X = 2591
        const val DISTENTOR_Z = 3088
        const val DISTENTOR_WANDER = 5

        const val CROMPERTY_GREETING = "Hello there. My name is Cromperty. I am a Wizard, and an inventor."
        const val BRIMSTAIL_GREETING = "Hello adventurer, what can I do for you?"
        const val DISTENTOR_GREETING = "Welcome to the Magicians' Guild!"

        /** The four rune essence outcrops (cache: loc `blankrunestone` placed at these tiles); arrivals stand beside one (OPEN-EM1). */
        private val OUTCROPS =
            listOf(CoordGrid(2891, 4847, 0), CoordGrid(2893, 4812, 0), CoordGrid(2925, 4848, 0), CoordGrid(2927, 4814, 0))
        private val RING =
            (-2..2).flatMap { dx -> (-2..2).map { dz -> dx to dz } }.filter { (dx, dz) ->
                maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) >=
                    2
            }

        /**
         * Teleports [p] to the Rune Essence Mine from the wizard standing at [wizardAt]: remembers where to return, shows the
         * wizard's portal, and charges the scrying orb of Enter the Abyss. Also used by the cutscene-free quest dialogues.
         */
        fun teleportFrom(p: PlayerRef, from: EssenceTeleporter, wizardAt: CoordGrid) {
            p.setSaved(RETURN, wizardAt.packed.toLong())
            p.setVarbit(Varbits.ESSENCEMINE_PORTAL, from.portalValue)
            p.teleport(arrival(p, OUTCROPS[from.outcrop]))
            AbyssOrb.charge(p, from)
        }

        /** The nearest walkable tile two tiles around [outcrop] (so the player never lands on a rock, Wiki 20 Sep 2018). */
        private fun arrival(p: PlayerRef, outcrop: CoordGrid): CoordGrid =
            RING
                .map { (dx, dz) -> outcrop.translate(dx, dz) }
                .firstOrNull { p.isWalkable(it) } ?: outcrop
    }
}
