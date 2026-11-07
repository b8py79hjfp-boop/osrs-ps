@file:Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth", "TooManyFunctions", "LargeClass")
// The long functions are Wiki transcripts and cutscenes: straight-line dialogue.

package tp.content.quests

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.TimerId
import tp.plugin.choice
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Misthalin Mystery, second half (docs/mechanics/quest-misthalin-mystery.md MM4-MM12): Tayten, the notes and the three riddles,
 * the painting, the candles and the fuse, Lacey, the piano (interface 554), Mandy's kitchen, the gem switch panel (interface 555),
 * the showdown with the mirror and the end. Dialogue and scene descriptions are the Wiki "Transcript:Misthalin Mystery". The cache
 * draws most of the state itself: the locs and NPCs are multi-locs / multi-NPCs on varbit 3468, so the stage numbers below are the
 * cache's thresholds (Tayten gone from 35, painting slashed from 45, wall and barrel from 65, Lacey from 70, open piano from 80,
 * Mandy from 90, revealed fireplace from 100, movable mirror at 111, unmasked Abigale 115-129); the stages between them are ours
 * (OPEN-QU1). Cutscenes are staged with [Scene] (camera, hidden minimap, scene NPCs); their camera placement is ours (OPEN-QUC5).
 */
public class MisthalinManor : ContentPlugin {
    override fun ContentScope.register() {
        registerDoors()
        registerNotes()
        registerRiddles()
        registerPuzzles()
        registerShowdown()
        onLogin { timer(TICK, TICK_INTERVAL, true) { tick() } }
        onLogout { clearShowdown() }
    }

    private fun ContentScope.registerDoors() {
        onOpLoc(Locs.MISTMYST_DOOR_REDTOPAZ, OP_FIRST) {
            player.stopInteraction()
            redTopazDoor()
        }
        for ((door, key) in KEY_DOORS) {
            onOpLoc(door, OP_FIRST) { loc ->
                player.stopInteraction()
                keyDoor(loc, key)
            }
            for (other in ALL_KEYS) {
                if (other == key) continue
                onUseOnLoc(other, door) {
                    player.stopInteraction()
                    box("The key doesn't fit.")
                }
            }
        }
        for (door in LOCKED_DOORS) {
            onOpLoc(door, OP_FIRST) {
                player.stopInteraction()
                box("The door is securely locked.")
            }
        }
        for (wall in listOf(Locs.MISTMYST_DESTRUCTABLE_WALL_CLIMBABLE, Locs.MISTMYST_DESTRUCTABLE_WALL_CLIMBABLE_BROKEN)) {
            onOpLoc(wall, OP_FIRST) { loc ->
                player.stopInteraction()
                player.passDoor(loc)
            }
        }
    }

    /** Wiki "Opening colourful handle doors". */
    private suspend fun PlayerScript.keyDoor(door: LocRef, key: Int) {
        val p = player
        if (!p.has(key)) return box("The door is securely locked.")
        p.message("You use the ${p.objName(key).lowercase()} to unlock the door.")
        p.passDoor(door)
    }

    // ---- the notes

    private fun ContentScope.registerNotes() {
        for (loc in listOf(Locs.MISTMYST_CLUE_LIBRARY, Locs.MISTMYST_CLUE_LIBRARY_VIS)) {
            onOpLoc(loc, OP_FIRST) { takeNote(Objs.MISTMYST_CLUE_LIBRARY) }
        }
        for (loc in listOf(Locs.MISTMYST_CLUE_OUTSIDE, Locs.MISTMYST_CLUE_OUTSIDE_VIS)) {
            onOpLoc(loc, OP_FIRST) { takeNote(Objs.MISTMYST_CLUE_OUTSIDE) }
        }
        for (loc in listOf(Locs.MISTMYST_CLUE_KITCHEN, Locs.MISTMYST_CLUE_KITCHEN_VIS)) {
            onOpLoc(loc, OP_FIRST) { takeNote(Objs.MISTMYST_CLUE_KITCHEN) }
        }
        // Wiki "Notes (Misthalin Mystery - 1..3)" transcripts; reading is required to go on ("You must read it in-order to progress").
        onItemOp(Objs.MISTMYST_CLUE_LIBRARY, "Read") {
            startScript {
                box(
                    "Isn't murder just a work of art?<br>Beautiful, yet haunting, like the blade of a knife.<br>" +
                        "As we wander through the valley of death.",
                )
                QuestsB.MISTHALIN_MYSTERY.advance(player, Mm.NOTE1)
            }
        }
        onItemOp(Objs.MISTMYST_CLUE_OUTSIDE, "Read") {
            startScript {
                box("It's like music to my ears!<br>The glorious sounds, spelling out your fate!")
                QuestsB.MISTHALIN_MYSTERY.advance(player, Mm.NOTE2)
            }
        }
        onItemOp(Objs.MISTMYST_CLUE_KITCHEN, "Read") {
            startScript {
                box(
                    "<b>H</b>ear at first these word<b>s</b>.<br><b>E</b>ach murder you witness helplessly from start to en<b>d</b>." +
                        "<br><b>A</b>s you fail to solve the final letters of this qui<b>z</b>." +
                        "<br><b>R</b>azor sharp like a gemstone is the blade of my knif<b>e</b>." +
                        "<br><b>T</b>he last sound you hear will be your scream's ech<b>o</b>." +
                        "<br><b>H</b>eed that I will have the final word in this thrille<b>r</b>.",
                )
                QuestsB.MISTHALIN_MYSTERY.advance(player, Mm.NOTE3)
            }
        }
    }

    /** Wiki "Taking the note": no space, already taken, or a copy. */
    private suspend fun PlayerScript.takeNote(note: Int) {
        val p = player
        p.stopInteraction()
        when {
            p.has(note) -> p.message("You have already taken a copy of the note.")
            p.inv(Invs.INV).freeSlots == 0 -> p.message("You need space in your inventory to take the note.")
            p.give(note, 1, "misthalin mystery note") -> itemBox(note, "You take a copy of the note.")
        }
    }

    // ---- riddle 1: Tayten, the painting, the candles, the fuse

    private fun ContentScope.registerRiddles() {
        // The painting with mountains, a river and the sun is 29650; the others are decoys ("Nope, nothing here...").
        onUseOnLoc(Objs.KNIFE, Locs.MISTMYST_PAINTING) {
            player.stopInteraction()
            slashPainting()
        }
        for (decoy in DECOY_PAINTINGS) {
            onUseOnLoc(Objs.KNIFE, decoy) {
                player.stopInteraction()
                player.message("Nope, nothing here...")
            }
        }
        onOpLoc(Locs.MISTMYST_PAINTING_SLASHED, OP_FIRST) {
            player.stopInteraction()
            searchPainting()
        }
        for ((index, candle) in CANDLES.withIndex()) {
            onUseOnLoc(Objs.TINDERBOX, candle) {
                player.stopInteraction()
                lightCandle(index)
            }
        }
        onUseOnLoc(Objs.TINDERBOX, Locs.MISTMYST_EXPLOSIVE_BARREL) {
            player.stopInteraction()
            lightFuse()
        }
        onUseOnLoc(Objs.TINDERBOX, Locs.MISTMYST_EXPLOSIVE_BARREL_VIS) {
            player.stopInteraction()
            lightFuse()
        }
    }

    private suspend fun PlayerScript.redTopazDoor() {
        val p = player
        if (Mm.stage(p) != KEY) {
            box("The door is securely locked.")
            return
        }
        taytenDies()
    }

    /** Wiki "Tayten's demise". */
    private suspend fun PlayerScript.taytenDies() {
        val p = player
        QuestsB.MISTHALIN_MYSTERY.advance(p, Mm.TAYTEN)
        scene { scene ->
            scene.shot(TAYTEN_CAMERA, TAYTEN_AT)
            val tayten = scene.actor(Npcs.MISTMYST_TAYTEN_VIS, TAYTEN_AT)
            tayten.anim(Seqs.HUMAN_READBOOK)
            delay(SCENE_BEAT)
            val killer = scene.actor(Npcs.MISTMYST_KILLER_BACKGROUND_VIS, TAYTEN_CLOSET)
            walkNpc(killer, TAYTEN_AT.translate(1, 0), SCENE_BEAT)
            killer.anim(Seqs.HUMAN_SWORD_STAB)
            delay(SCENE_BEAT)
            npcAs(Npcs.MISTMYST_TAYTEN_VIS, "Tayten", "Gurgle...")
            tayten.anim(Seqs.HUMAN_DEATH)
            me("Nooooo!")
            walkNpc(killer, TAYTEN_CLOSET, SCENE_BEAT)
            scene.remove(killer)
        }
        me("What's this? A note? I guess I should read it.")
    }

    private suspend fun PlayerScript.slashPainting() {
        val p = player
        if (Mm.stage(p) < Mm.NOTE1) return p.message("Nope, nothing here...")
        if (Mm.stage(p) >= Mm.PAINTING) return p.message("You have already slashed the painting.")
        QuestsB.MISTHALIN_MYSTERY.advance(p, Mm.PAINTING)
        box("You slash open the painting, revealing a hidden storage compartment.")
    }

    private suspend fun PlayerScript.searchPainting() {
        val p = player
        when {
            p.has(Objs.MISTMYST_RUBY_KEY) -> {
                p.message("You have already found the ruby key.")
            }

            p.inv(Invs.INV).freeSlots == 0 -> {
                box("You don't have enough space in your inventory.")
            }

            p.give(Objs.MISTMYST_RUBY_KEY, 1, "misthalin mystery ruby key") -> {
                QuestsB.MISTHALIN_MYSTERY.advance(p, Mm.RUBY)
                itemBox(Objs.MISTMYST_RUBY_KEY, "You find a key hidden inside the slashed painting.")
            }
        }
    }

    /** Wiki "Lighting a candle" / "Lighting the last candle": the four candles are varbits 4039-4042, the count varbit 4043. */
    private suspend fun PlayerScript.lightCandle(index: Int) {
        val p = player
        if (p.varbit(CANDLE_VARBITS[index]) != 0) return p.message("The candle is already lit.")
        p.setVarbit(CANDLE_VARBITS[index], 1)
        val lit = CANDLE_VARBITS.count { p.varbit(it) != 0 }
        if (lit == CANDLE_VARBITS.size) {
            p.setVarbit(Varbits.MISTMYST_CANDLES, 1)
            box("You light the candle. The room is sufficiently warmed, the fuse should now have dried out.")
        } else {
            box("You light the candle.")
        }
    }

    /** Wiki "Attempting to light the fuse ...": dry only when all four candles burn; the explosion follows when the room is left. */
    private suspend fun PlayerScript.lightFuse() {
        val p = player
        when {
            p.varbit(Varbits.MISTMYST_CANDLES) == 0 -> {
                me("The fuse is too damp to light...")
            }

            Mm.stage(p) >= Mm.BLOWN -> {
                p.message("The barrel has already exploded.")
            }

            p[FUSE_LIT] == true -> {
                me("The fuse is already lit! I need to get out of here before it blows!")
            }

            else -> {
                p.message("You attempt to light the fuse...")
                p[FUSE_LIT] = true
                me("The fuse is lit, I should get out of here before it blows!")
            }
        }
    }

    // ---- riddle 2 and 3 scenes, piano, gem panel

    private fun ContentScope.registerPuzzles() {
        for (piano in listOf(Locs.MISTMYST_PIANO, Locs.MISTMYST_PIANO_CLOSED, Locs.MISTMYST_PIANO_OPEN)) {
            onOpLoc(piano, OP_FIRST) {
                player.stopInteraction()
                playPiano()
            }
        }
        for (piano in listOf(Locs.MISTMYST_PIANO, Locs.MISTMYST_PIANO_OPEN)) {
            onOpLoc(piano, OP_THIRD) {
                player.stopInteraction()
                searchPiano()
            }
        }
        for ((component, note) in PIANO_KEYS) {
            onButton(component, OP_FIRST) { pianoKey(note) }
        }
        for (fireplace in listOf(Locs.MISTMYST_FIREPLACE, Locs.MISTMYST_FIREPLACE_REVEALED)) {
            onOpLoc(fireplace, OP_FIRST) {
                player.stopInteraction()
                searchFireplace()
            }
        }
        onUseOnLoc(Objs.KNIFE, Locs.MISTMYST_FIREPLACE) {
            player.stopInteraction()
            knifeOnFireplace()
        }
        onUseOnLoc(Objs.KNIFE, Locs.MISTMYST_FIREPLACE_UNLIT) {
            player.stopInteraction()
            knifeOnFireplace()
        }
        for ((component, gem) in GEM_BUTTONS) {
            onButton(component, OP_FIRST) { flipGem(gem) }
        }
    }

    /** Wiki "Playing the piano": the three conditions before the keyboard opens. */
    private suspend fun PlayerScript.playPiano() {
        val p = player
        val stage = Mm.stage(p)
        if (stage < Mm.LACEY) return me("There is no time to be playing around!")
        if (stage < Mm.NOTE2) return me("I don't think that's right... Perhaps the clue the killer left behind could help?")
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.MISTMYST_PIANO)
        for ((component, _) in PIANO_KEYS) p.ifEvents(component, -1..-1, IfEvents.op(OP_FIRST))
    }

    /** The piano keys D, E, A, D in this order unlock the compartment (Wiki "Piano"; varbits 4044-4048 follow the progress). */
    private fun PlayerRef.pianoKey(note: Char) {
        val article = if (note == 'A' || note == 'E') "an" else "a"
        message("You play $article $note.")
        val played = this[PIANO_PROGRESS] ?: 0
        // A wrong note starts the sequence over (a first D counts as the new start).
        val next =
            if (note == PIANO_SEQUENCE[played]) {
                played + 1
            } else if (note == PIANO_SEQUENCE[0]) {
                1
            } else {
                0
            }
        for ((index, v) in PIANO_VARBITS.withIndex()) setVarbit(v, if (index < next) 1 else 0)
        this[PIANO_PROGRESS] = next
        if (next == PIANO_SEQUENCE.size) {
            this[PIANO_PROGRESS] = 0
            setVarbit(Varbits.MISTMYST_PIANO_DEAD, 1)
            closeModal()
            message("There is a clicking sound as the compartment on the piano unlocks.")
            QuestsB.MISTHALIN_MYSTERY.advance(this, Mm.PIANO)
        }
    }

    private suspend fun PlayerScript.searchPiano() {
        val p = player
        if (Mm.stage(p) < Mm.PIANO) return p.message("You can't find anything in the piano.")
        when {
            p.inv(Invs.INV).freeSlots == 0 -> {
                box("You don't have enough space in your inventory.")
            }

            p.has(Objs.MISTMYST_EMERALD_KEY) -> {
                p.message("You have already found the emerald key.")
            }

            p.give(Objs.MISTMYST_EMERALD_KEY, 1, "misthalin mystery emerald key") -> {
                QuestsB.MISTHALIN_MYSTERY.advance(p, Mm.EMERALD)
                itemBox(Objs.MISTMYST_EMERALD_KEY, "You find a key hidden inside the piano's compartment.")
            }
        }
    }

    /** Wiki "Using a knife on the fireplace": needs the third note read. */
    private suspend fun PlayerScript.knifeOnFireplace() {
        val p = player
        if (Mm.stage(p) < Mm.NOTE3) return p.message("You can't see what a knife would do to the fireplace.")
        if (Mm.stage(p) >= Mm.FIREPLACE) return p.message("You have already pried open the loose brick.")
        QuestsB.MISTHALIN_MYSTERY.advance(p, Mm.FIREPLACE)
        box("You use your knife to pry open a loose brick in the fireplace, revealing a secret panel.")
    }

    /** Wiki "Searching the fireplace" (first, the switch panel; after the riddle the sapphire key). */
    private suspend fun PlayerScript.searchFireplace() {
        val p = player
        if (p.flag(GEMS_SOLVED)) {
            when {
                p.has(Objs.MISTMYST_SAPPHIRE_KEY) -> {
                    p.message("You have already found the sapphire key.")
                }

                p.inv(Invs.INV).freeSlots == 0 -> {
                    box("You don't have enough space in your inventory.")
                }

                p.give(Objs.MISTMYST_SAPPHIRE_KEY, 1, "misthalin mystery sapphire key") -> {
                    QuestsB.MISTHALIN_MYSTERY.advance(p, Mm.SAPPHIRE)
                    itemBox(Objs.MISTMYST_SAPPHIRE_KEY, "You find a key hidden inside the fireplace.")
                }
            }
            return
        }
        box("You find a panel of switches inside the hidden compartment. Each switch is crafted from a different gemstone.")
        openGemPanel()
    }

    private fun PlayerScript.openGemPanel() {
        val p = player
        resetGems(p)
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.MISTMYST_GEM_PUZZLE)
        for ((component, _) in GEM_BUTTONS) p.ifEvents(component, -1..-1, IfEvents.op(OP_FIRST))
    }

    private fun resetGems(p: PlayerRef) {
        p[GEM_PICKS] = emptyList()
        for (v in GEM_VARBITS) p.setVarbit(v, 0)
        p.setVarbit(Varbits.MISTMYST_GEMS_SWITCHED, 0)
        for (text in SELECTED_TEXTS) p.ifText(text, "")
    }

    /**
     * A gem switch is flipped once; the sixth in order S-D-Z-E-O-R (Wiki "Misthalin Mystery" riddle 3) unlocks the compartment.
     * OPEN-QUC7: which components of interface 555 are clickable and what the client scripts draw for the selection is not
     * published; the server writes the switch varbits and the six "selected" texts.
     */
    private fun PlayerRef.flipGem(gem: Gem) {
        val picks = (this[GEM_PICKS] ?: emptyList()).toMutableList()
        if (gem in picks) return
        message("You flip the ${gem.label} switch.")
        picks += gem
        this[GEM_PICKS] = picks
        setVarbit(gem.varbit, 1)
        setVarbit(Varbits.MISTMYST_GEMS_SWITCHED, picks.size)
        ifText(SELECTED_TEXTS[picks.size - 1], gem.label)
        if (picks.size < GEM_ORDER.size) return
        if (picks == GEM_ORDER) {
            closeModal()
            message("There is a clicking sound as the compartment in the fireplace unlocks.")
            setFlag(GEMS_SOLVED, true)
        } else {
            startScript { me("I don't think that's the right order.. Perhaps the clue the killer left behind could help?") }
            resetGems(this)
        }
    }

    // ---- showdown

    private fun ContentScope.registerShowdown() {
        onOpLoc(Locs.MISTMYST_DOOR_SAPPHIRE, OP_FIRST) { loc ->
            player.stopInteraction()
            sapphireDoor(loc)
        }
        for (mirror in listOf(Npcs.MISTMYST_MIRROR, Npcs.MISTMYST_MIRROR_MOVABLE, Npcs.MISTMYST_MIRROR_FIXED)) {
            onOpNpc(mirror, OP_FIRST) { who ->
                player.stopInteraction()
                pushMirror(who)
            }
        }
        for (abigale in listOf(Npcs.MISTMYST_ABIGALE_KILLER_UNMASKED, Npcs.MISTMYST_ABIGALE_CUTSCENE_MULTI)) {
            onOpNpc(abigale, OP_FIRST) {
                player.stopInteraction()
                me("I don't think she wants to talk to me right now.")
            }
        }
        onOpNpc(Npcs.MISTMYST_ABIGALE_KILLER_ATTACKABLE, OP_FIRST) {
            player.stopInteraction()
            stabAbigale()
        }
        onOpNpc(Npcs.MISTMYST_HEWEY_KILLER_UNMASKED, OP_FIRST) {
            player.stopInteraction()
            me("I don't think he's in a very talkative mood.")
        }
        for (mandy in listOf(Npcs.MISTMYST_MANDY, Npcs.MISTMYST_MANDY_VIS)) {
            onOpNpc(mandy, OP_FIRST) { who ->
                player.stopInteraction()
                npc(who, "Why am I the one who is always left clearing up after everyone?")
            }
        }
    }

    /** The sapphire door: the key, then the confrontation (Wiki "Confronting the killer"); at the end the Mandy scene. */
    private suspend fun PlayerScript.sapphireDoor(door: LocRef) {
        val p = player
        val stage = Mm.stage(p)
        if (stage == ABIGALE_STABBED) {
            leaveManor(door)
            return
        }
        if (!p.has(Objs.MISTMYST_SAPPHIRE_KEY) && stage < Mm.SHOWDOWN) {
            box("The door is securely locked.")
            return me("I'll have to find the key...")
        }
        if (stage < Mm.SHOWDOWN && stage >= Mm.SAPPHIRE) {
            p.message("You use the sapphire key to unlock the door.")
            p.passDoor(door)
            confront()
            return
        }
        p.passDoor(door)
    }

    /** Wiki "Confronting the killer". */
    private suspend fun PlayerScript.confront() {
        val p = player
        scene { scene ->
            scene.shot(SHOWDOWN_CAMERA, SHOWDOWN_CENTRE)
            val killer = scene.actor(Npcs.MISTMYST_ABIGALE_KILLER, WARDROBES[0].at.translate(1, 0))
            me("Show yourself you coward!")
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER, "Killer", "So we meet at last. I have to say, I'm impressed you worked out my riddles.")
            me("Well maybe you aren't as smart as you think you are.")
            npcAs(
                Npcs.MISTMYST_ABIGALE_KILLER,
                "Killer",
                "Oh really? All the dead bodies suggest otherwise. And what did you do to stop them?",
            )
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER, "Killer", "NOTHING!")
            npcAs(
                Npcs.MISTMYST_ABIGALE_KILLER,
                "Killer",
                "People think you are some big hero but you're not. All you adventurers are the same. Nothing but opportunists!",
            )
            me("What are you going on about?")
            npcAs(
                Npcs.MISTMYST_ABIGALE_KILLER,
                "Killer",
                "Well, I did have this big speech planned out but quite frankly I don't think you are worth the effort it " +
                    "would take to deliver it.",
            )
            npcAs(
                Npcs.MISTMYST_ABIGALE_KILLER,
                "Killer",
                "So, this is the end of the line for you. It is time for the final act. The showdown. The finale. " +
                    "Let's see if you are up to the challenge!",
            )
            scene.remove(killer)
        }
        QuestsB.MISTHALIN_MYSTERY.advance(p, Mm.SHOWDOWN)
        p[SHOWDOWN] = Showdown()
    }

    /**
     * "Push" on the mirror: it moves one tile away from the player along the longer axis and then faces that way (Wiki: "push
     * the mirror into the row/column of the wardrobe and then push it at least once towards the wardrobe").
     */
    private fun PlayerScript.pushMirror(mirror: NpcRef) {
        val p = player
        val state = p[SHOWDOWN]
        if (state == null || Mm.stage(p) != Mm.SHOWDOWN) return p.message("Nothing interesting happens.")
        val dx = mirror.coord.x - p.coord.x
        val dz = mirror.coord.z - p.coord.z
        val (sx, sz) =
            if (kotlin.math.abs(dx) >= kotlin.math.abs(dz)) {
                Integer.signum(dx) to 0
            } else {
                0 to Integer.signum(dz)
            }
        val target = mirror.coord.translate(sx, sz)
        if (!p.isWalkable(target) || !SHOWDOWN_ROOM.contains(target)) return p.message("The mirror won't move any further that way.")
        mirror.walkTo(target)
        state.facingX = sx
        state.facingZ = sz
    }

    private suspend fun PlayerScript.stabAbigale() {
        val p = player
        val weapon = p.inv(Invs.WORN).obj(WEAPON_SLOT)
        when {
            Mm.stage(p) != ABIGALE_FIGHT -> {
                return
            }

            weapon < 0 -> {
                me("I'm not going at her empty-handed!")
            }

            weapon != Objs.MISTMYST_CUTSCENE_KNIFE -> {
                me("It would be wiser to use the killer's knife rather than my own weapon...")
            }

            else -> {
                p.anim(Seqs.HUMAN_SWORD_STAB)
                delay(SCENE_BEAT)
                me("Well thank Saradomin that's over! I should get out of here.")
                QuestsB.MISTHALIN_MYSTERY.advance(p, ABIGALE_STABBED)
            }
        }
    }

    /** Wiki "Leaving the manor": Mandy kills Abigale with the Bandos godsword. */
    private suspend fun PlayerScript.leaveManor(door: LocRef) {
        val p = player
        scene { scene ->
            scene.shot(SHOWDOWN_CAMERA, SHOWDOWN_CENTRE)
            val abigale = scene.actor(Npcs.MISTMYST_ABIGALE_KILLER_UNMASKED, ABIGALE_AT)
            val mandy = scene.actor(Npcs.MISTMYST_MANDY_VIS, MANDY_FLOOR)
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER_UNMASKED, "Abigale", "You'll never escape me!")
            mandy.anim(Seqs.HUMAN_PICKUPFLOOR)
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER_UNMASKED, "Abigale", "You dare try and ruin my moment!")
            walkNpc(mandy, GODSWORD_TABLE, SCENE_WALK)
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER_UNMASKED, "Abigale", "Hewey is dead because of you!")
            walkNpc(mandy, WARDROBES[2].at, SCENE_WALK)
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER_UNMASKED, "Abigale", "Now it is time for you to join him!")
            walkNpc(mandy, ABIGALE_AT.translate(0, -1), SCENE_WALK)
            npcAs(Npcs.MISTMYST_MANDY_VIS, "Mandy", "You're not killing anyone else!")
            mandy.anim(Seqs.HUMAN_SWORD_SLASH)
            delay(SCENE_BEAT)
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER_UNMASKED, "Abigale", "No....this can't be it....")
            abigale.anim(Seqs.HUMAN_DEATH)
            delay(SCENE_BEAT)
            scene.remove(abigale)
            me("Wow, that was close!")
            me("Mandy, how are you alive!? I watched you get stabbed through the heart!")
            npcAs(Npcs.MISTMYST_MANDY_VIS, "Mandy", "I did get stabbed, but I was born with a rare condition called Dextrocardia.")
            me("Dextrowhat?")
            npcAs(
                Npcs.MISTMYST_MANDY_VIS,
                "Mandy",
                "Dextrocardia. It means that my heart is on the right side of my chest, not the left like most people.",
            )
            me("So when the killer thought they stabbed you in the heart, they actually missed?")
            npcAs(Npcs.MISTMYST_MANDY_VIS, "Mandy", "Well, missed the heart yes.")
            npcAs(Npcs.MISTMYST_MANDY_VIS, "Mandy", "I did still get stabbed though, and am actually feeling pretty woozy...")
            me("Oh, yes, right! You should go sort yourself out!")
            npcAs(
                Npcs.MISTMYST_MANDY_VIS,
                "Mandy",
                "I will wait outside the house after seeing to this stab wound. You should come talk to me there.",
            )
        }
        p.clearShowdown()
        p.message("You decide it's wise to leave the murder weapon behind.")
        p.takeAll(Objs.MISTMYST_CUTSCENE_KNIFE)
        QuestsB.MISTHALIN_MYSTERY.advance(p, Mm.SHOWDOWN_WON)
        p.passDoor(door)
    }

    private fun PlayerRef.takeAll(obj: Int) {
        val count = inv(Invs.INV).total(obj).toInt()
        if (count > 0) take(obj, count, "misthalin mystery knife")
        val worn = inv(Invs.WORN)
        for (slot in 0 until worn.size) {
            if (worn.obj(slot) == obj) transaction("misthalin mystery knife worn") { removeAt(Invs.WORN, slot, obj, 1) }
        }
    }

    private fun PlayerRef.clearShowdown() {
        this[SHOWDOWN]?.let { state ->
            state.hewey?.takeIf { it.isInWorld }?.despawn()
        }
        this[SHOWDOWN] = null
    }

    // ---- the timer: fuse, Lacey and Mandy triggers, the knives of the showdown

    private fun PlayerRef.tick() {
        val stage = Mm.stage(this)
        if (stage < ARRIVED_ISLAND || stage >= Mm.END) return
        if (this[FUSE_LIT] == true && !EAST_ROOM.contains(coord)) explode()
        if (stage == Mm.BLOWN && LACEY_TRIGGER.contains(coord)) startScript { laceyDies() }
        if (stage == Mm.EMERALD && MANDY_TRIGGER.contains(coord)) startScript { mandyDies() }
        if (stage == Mm.SHOWDOWN && SHOWDOWN_ROOM.contains(coord)) showdownStep()
    }

    /** Wiki "Exit the room to trigger the explosion": the barrel blows when the player has left the room with the fuse lit. */
    private fun PlayerRef.explode() {
        this[FUSE_LIT] = false
        QuestsB.MISTHALIN_MYSTERY.advance(this, Mm.BLOWN)
        spotAnimAt(BARREL_AT, SpotAnims.DWARF_ROCK_EXPLOSION)
        camShake(0, SHAKE_RANDOM, SHAKE_AMPLITUDE, SHAKE_RATE)
        message("The barrel explodes, blasting a hole in the wall!")
    }

    /** Wiki "Run in with Lacey". */
    private suspend fun PlayerScript.laceyDies() {
        val p = player
        QuestsB.MISTHALIN_MYSTERY.advance(p, Mm.LACEY)
        scene { scene ->
            scene.shot(LACEY_CAMERA, LACEY_AT)
            val lacey = scene.actor(Npcs.MISTMYST_LACEY_VIS, LACEY_AT)
            val killer = scene.actor(Npcs.MISTMYST_ABIGALE_KILLER, LACEY_AT.translate(1, 0))
            npcAs(Npcs.MISTMYST_LACEY_VIS, "Lacey", "Please, stop this, just let me go!")
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER, "Killer", "We're going to play a game. Do you like to play games?")
            npcAs(Npcs.MISTMYST_LACEY_VIS, "Lacey", "No...yes....whatever you say, just don't hurt me!")
            npcAs(
                Npcs.MISTMYST_ABIGALE_KILLER,
                "Killer",
                "Whether you get hurt or not is entirely up to you. Your fate is in your own hands.",
            )
            npcAs(Npcs.MISTMYST_LACEY_VIS, "Lacey", "What!? What do you mean!?")
            me("Leave her alone you monster!")
            npcAs(
                Npcs.MISTMYST_ABIGALE_KILLER,
                "Killer",
                "Ah, $name, how kind of you to join us. Now if you say another word, I will end her right now!",
            )
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER, "Killer", "Sorry, where were we? Ah yes, of course! The game!")
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER, "Killer", "Do you like quests, Lacey?")
            npcAs(Npcs.MISTMYST_LACEY_VIS, "Lacey", "Ummm, sure, I like quests.")
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER, "Killer", "Do you like scary quests?")
            npcAs(Npcs.MISTMYST_LACEY_VIS, "Lacey", "Uh-huh.")
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER, "Killer", "What's your favourite scary quest?")
            npcAs(Npcs.MISTMYST_LACEY_VIS, "Lacey", "I don't know.")
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER, "Killer", "You have to have a favourite.")
            npcAs(Npcs.MISTMYST_LACEY_VIS, "Lacey", "Umm.....the one with that vampyre in it who lives in a big house.")
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER, "Killer", "Good, good. And now for that game I spoke about.")
            npcAs(
                Npcs.MISTMYST_ABIGALE_KILLER,
                "Killer",
                "I am going to ask you one question. Get it wrong and you die, but get it right and you live.",
            )
            npcAs(Npcs.MISTMYST_LACEY_VIS, "Lacey", "Oh Guthix please no, please don't, I don't want to die!")
            npcAs(
                Npcs.MISTMYST_ABIGALE_KILLER,
                "Killer",
                "Then think hard about your answer. You like vampyres so this should be easy for you. The question is this...",
            )
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER, "Killer", "Who is the Vampyre that resides in South Misthalin?")
            npcAs(Npcs.MISTMYST_LACEY_VIS, "Lacey", "Urm...")
            when (choice("Interrupt with answer?", "Count Draynor", "Count Check", "Lord Drakan", "Say nothing")) {
                1 -> {
                    me("Count Draynor!")
                }

                2 -> {
                    me("Count Check!")
                }

                3 -> {
                    me("Lord Drakan!")
                    npcAs(
                        Npcs.MISTMYST_ABIGALE_KILLER,
                        "Killer",
                        "This game didn't involve you $name! Now her death is on you! Her blood is on your hands you fool!",
                    )
                }

                else -> {
                    npcAs(Npcs.MISTMYST_LACEY_VIS, "Lacey", "Oh yes, yes, I know it! Count Draynor!")
                    npcAs(Npcs.MISTMYST_LACEY_VIS, "Lacey", "Ha, in your face you masked freak!")
                    npcAs(Npcs.MISTMYST_ABIGALE_KILLER, "Killer", "Oh Lacey, Lacey, Lacey. I did tell you to think hard about your answer.")
                    npcAs(
                        Npcs.MISTMYST_ABIGALE_KILLER,
                        "Killer",
                        "Count Draynor is in fact a vampyre, and he does reside in his manor in Misthalin.",
                    )
                    npcAs(
                        Npcs.MISTMYST_ABIGALE_KILLER,
                        "Killer",
                        "But that manor is in WEST Misthalin! I was talking about Count Check! Thanks for playing!",
                    )
                    npcAs(Npcs.MISTMYST_LACEY_VIS, "Lacey", "No, no you tricked me! This isn't fair, ask me another...")
                }
            }
            killer.anim(Seqs.HUMAN_SWORD_STAB)
            npcAs(Npcs.MISTMYST_LACEY_VIS, "Lacey", "Gargle...urgh...")
            lacey.anim(Seqs.HUMAN_DEATH)
            delay(SCENE_BEAT)
            walkNpc(killer, LACEY_AT.translate(3, 0), SCENE_WALK)
            scene.remove(killer)
        }
        me("That monster! I have to stop them! Let's see what that new note says.")
    }

    /** Wiki "Mandy in the kitchen". */
    private suspend fun PlayerScript.mandyDies() {
        val p = player
        QuestsB.MISTHALIN_MYSTERY.advance(p, Mm.MANDY)
        scene { scene ->
            scene.shot(MANDY_CAMERA, MANDY_AT)
            val mandy = scene.actor(Npcs.MISTMYST_MANDY_VIS, MANDY_AT)
            npcAs(Npcs.MISTMYST_MANDY_VIS, "Mandy", "Why am I the one who is always left clearing up after everyone?")
            mandy.say("*shuffle*")
            npcAs(Npcs.MISTMYST_MANDY_VIS, "Mandy", "Hello? Anyone there?")
            mandy.say("*rustle*")
            npcAs(Npcs.MISTMYST_MANDY_VIS, "Mandy", "Hello? Lacey? Sid?")
            mandy.say("*scratch*")
            npcAs(
                Npcs.MISTMYST_MANDY_VIS,
                "Mandy",
                "Hmm, well I guess I should go check it out. No point staying in this nice, locked room when I could go " +
                    "outside into the creepy darkness.",
            )
            me("Seriously!?")
            box("The walls and door seem too thick for Mandy to be able to hear you.")
            npcAs(
                Npcs.MISTMYST_MANDY_VIS,
                "Mandy",
                "Oh, and I don't think there is any need to take this big, shiny weapon with me in order to defend myself. " +
                    "Nothing bad ever happens when people go check out noises in the dark outside of houses!",
            )
            me("You have got to be kidding...")
            npcAs(Npcs.MISTMYST_MANDY_VIS, "Mandy", "Ok, well, here I go!")
            walkNpc(mandy, MANDY_OUTSIDE, SCENE_WALK)
            npcAs(Npcs.MISTMYST_MANDY_VIS, "Mandy", "Hello, anyone out here?")
            delay(SCENE_BEAT)
            npcAs(
                Npcs.MISTMYST_MANDY_VIS,
                "Mandy",
                "See, I knew nothing would happen! Must have just been a squirrel or something running about.",
            )
            walkNpc(mandy, MANDY_AT, SCENE_WALK)
            npcAs(Npcs.MISTMYST_MANDY_VIS, "Mandy", "Oh well, back to the dishes.")
            me("Phew, that was lucky!")
            val killer = scene.actor(Npcs.MISTMYST_ABIGALE_KILLER, MANDY_CLOSET)
            walkNpc(killer, MANDY_AT.translate(0, -1), SCENE_BEAT)
            killer.anim(Seqs.HUMAN_SWORD_STAB)
            npcAs(Npcs.MISTMYST_MANDY_VIS, "Mandy", "No...why...me...")
            mandy.anim(Seqs.HUMAN_DEATH)
            delay(SCENE_BEAT)
            me("That psycho! He was just toying with her!")
            walkNpc(killer, MANDY_CLOSET, SCENE_BEAT)
            scene.remove(killer)
        }
        me("Another note. This guy really likes playing games, but this is going to be a game he won't win!")
    }

    // ---- the showdown game (OPEN-QUC6)

    /** One round of the knife game every [TICK_INTERVAL] ticks: the wardrobe opens, then the killer throws. */
    private fun PlayerRef.showdownStep() {
        val state = this[SHOWDOWN] ?: Showdown().also { this[SHOWDOWN] = it }
        state.beat++
        when (state.beat) {
            BEAT_OPEN -> {
                state.wardrobe = random(WARDROBES.size)
                val w = WARDROBES[state.wardrobe]
                addLoc(Locs.MISTMYST_BOSS_WARDROBE_OPEN, w.at, WARDROBE_SHAPE, w.angle, WARDROBE_OPEN_TICKS)
            }

            BEAT_THROW -> {
                throwKnife(state)
            }

            else -> {
                if (state.beat >= BEAT_PAUSE) state.beat = 0
            }
        }
    }

    private fun PlayerRef.throwKnife(state: Showdown) {
        val w = WARDROBES[state.wardrobe]
        spotAnimAt(w.at, SpotAnims.BRONZE_TKNIFE_LAUNCH)
        val mirror = npcsWithin(SHOWDOWN_CENTRE, SHOWDOWN_SEARCH).firstOrNull { it.type == Npcs.MISTMYST_MIRROR }
        val onLine = mirror != null && w.crosses(mirror.coord)
        if (onLine && state.facingX == -w.travelX && state.facingZ == -w.travelZ) {
            state.hits++
            spotAnimAt(w.at, SpotAnims.BRONZE_TKNIFE_TRAVEL)
            val killer = spawnNpc(Npcs.MISTMYST_ABIGALE_KILLER, w.at.translate(w.travelX, w.travelZ))
            killer.say(if (random(2) == 0) "*ouch*" else "Ouch!")
            killer.queueHit(HIT_VISIBLE_TICKS) { killer.despawn() }
            if (state.hits >= KILLER_HITS) startScript { killerDefeated() }
        } else if (!onLine) {
            hitpoints = maxOf(1, hitpoints - KNIFE_DAMAGE)
            message("A knife flies out of the wardrobe and hits you.")
        }
    }

    /** Wiki "Defeating the killer": the unmasking, Hewey, and Abigale stabbing him. */
    private suspend fun PlayerScript.killerDefeated() {
        val p = player
        p[SHOWDOWN] = null
        QuestsB.MISTHALIN_MYSTERY.advance(p, Mm.UNMASKED)
        scene { scene ->
            scene.shot(SHOWDOWN_CAMERA, SHOWDOWN_CENTRE)
            val killer = "Killer"
            val abigale = "Abigale"
            npcAs(Npcs.MISTMYST_ABIGALE_KILLER, killer, "Urgh...I think I may have underestimated you.")
            me("You've lost at your own game. And now it is time to see who you really are under that mask!")
            box("The player unmasks the killer, revealing Abigale as the culprit.")
            me(
                "No! You!? But why!? Why on earth would you kill all your friends!? Why beg me to come here to help them!?",
            )
            val a = Npcs.MISTMYST_ABIGALE_KILLER_UNMASKED
            npcAs(a, abigale, "You just don't get it do you?")
            npcAs(a, abigale, "So big headed. So used to it all being about you. About you saving the day.")
            npcAs(a, abigale, "Well I'm sick of it!")
            me("What do you mean?")
            npcAs(a, abigale, "Adventurers! You're all the same. Everything always has to be about you.")
            me("I don't understand?")
            npcAs(a, abigale, "Of course you wouldn't! Because you can't see past yourself!")
            npcAs(a, abigale, "Believe it or not... I used to be like you.")
            npcAs(
                a,
                abigale,
                "I'd hear about some damsel in distress and go to help. To have my moment. For me to be the hero, just for once.",
            )
            npcAs(
                a,
                abigale,
                "But YOU! You and every other darned adventurer! Every time I get there to help, it's too late. Crisis solved. " +
                    "Disaster averted. Reward claimed. Every time you and those other adventurers steal my moment!",
            )
            npcAs(a, abigale, "But not this time!")
            npcAs(a, abigale, "This time, it is all about me! I'm the star of the show! ME ME ME ME ME!")
            me("Well not any longer. The show is over, and there will be no curtain call.")
            npcAs(a, abigale, "Oh, but there is always a curtain call.")
            val hewey = scene.actor(Npcs.MISTMYST_HEWEY_KILLER, HEWEY_CLOSET)
            walkNpc(hewey, HEWEY_AT, SCENE_WALK)
            me("What!? How!? There was another killer!?")
            npcAs(
                a,
                abigale,
                "Of course. Every good show needs a good supporting actor. Now, take off your mask darling. Take a bow. " +
                    "You have earned it.",
            )
            npcAs(Npcs.MISTMYST_HEWEY_KILLER, killer, "Of course, my love.")
            box("The killer unmasks himself, revealing Hewey as the culprit.")
            val h = Npcs.MISTMYST_HEWEY_KILLER_UNMASKED
            me("You!? But how!? You were almost dead when I saw you! I saw your wounds!")
            npcAs(h, "Hewey", "No, what you watched was what we wanted you to watch. A masterful performance if I do say myself.")
            me("But what is in this for you?")
            npcAs(h, "Hewey", "Everyone loves the limelight, and who better to share it with than my beloved Abigale.")
            me("But your friends?")
            me("They didn't deserve to die!")
            npcAs(
                a,
                abigale,
                "Perhaps not, but neither did I deserve to be cast back into the shadows when I deserve the spotlight!",
            )
            npcAs(h, "Hewey", "Yes, while we deserve the spotlight.")
            npcAs(a, abigale, "And now I have my starring role!")
            npcAs(h, "Hewey", "OUR starring role.")
            npcAs(a, abigale, "Gielinor will forever remember this night and what I have done here.")
            npcAs(h, "Hewey", "No, what WE have done!")
            npcAs(a, abigale, "Quiet Hewey! This is my moment!")
            npcAs(h, "Hewey", "No, this is supposed to be our moment! It isn't just all about you either! We were in this together!")
            npcAs(
                a,
                abigale,
                "Stop ruining this Hewey! I have worked so hard for this and I'm not going to let you take it away from me!",
            )
            npcAs(h, "Hewey", "Well I'm not going to be pushed aside either, we were both in this as equals!")
            npcAs(a, abigale, "We were never equals! How could you possibly compare to me!?")
            npcAs(h, "Hewey", "You egotistical, selfish...")
            box("Abigale stabs Hewey.")
            npcAs(h, "Hewey", "No...my love...")
            hewey.anim(Seqs.HUMAN_DEATH)
            delay(SCENE_BEAT)
            npcAs(a, abigale, "No....no...what have I done!?")
            npcAs(
                a,
                abigale,
                "It wasn't supposed to be like this...but you just couldn't let me have my moment. WHY HEWEY WHY!? I loved you...",
            )
            me("This could be my chance to take advantage of the situation...")
            // Hewey's body stays for the fight; the scene would remove it, so it is a world NPC owned by this player.
            val body = p.spawnNpc(Npcs.MISTMYST_HEWEY_KILLER_UNMASKED, hewey.coord)
            body.anim(Seqs.HUMAN_DEATH)
            p[SHOWDOWN] = Showdown().also { it.hewey = body }
            p.transaction("misthalin mystery killer knife") {
                createOnGround(Objs.MISTMYST_CUTSCENE_KNIFE, 1, hewey.coord, p.username)
            }
        }
        QuestsB.MISTHALIN_MYSTERY.advance(p, ABIGALE_FIGHT)
    }

    /** The state of one player's knife game (Wiki "Showdown"; OPEN-QUC6). */
    private class Showdown {
        var beat = 0
        var wardrobe = 0
        var hits = 0
        var facingX = 0
        var facingZ = 0
        var hewey: NpcRef? = null
    }

    /** A wardrobe of the showdown room: its tile, its loc angle and the direction a knife thrown from it travels. */
    private class Wardrobe(val at: CoordGrid, val angle: Int, val travelX: Int, val travelZ: Int) {
        /** True when [c] lies on the line the knife travels, in front of the wardrobe. */
        fun crosses(c: CoordGrid): Boolean =
            if (travelX != 0) {
                c.z == at.z && (c.x - at.x) * travelX > 0
            } else {
                c.x == at.x && (c.z - at.z) * travelZ > 0
            }
    }

    private companion object {
        const val OP_FIRST = 1
        const val OP_THIRD = 3
        const val WEAPON_SLOT = 3
        val TICK = TimerId("misthalin_mystery_tick")
        const val TICK_INTERVAL = 2
        val FUSE_LIT = AttrKey<Boolean>("quests.mm.fuse")
        val GEM_PICKS = AttrKey<List<Gem>>("quests.mm.gems")
        val SHOWDOWN = AttrKey<Showdown>("quests.mm.showdown")
        val PIANO_PROGRESS = AttrKey<Int>("quests.mm.piano")
        const val GEMS_SOLVED = "quests.mm.gems_solved"

        /** Quest stages that are not Mm's (ours, OPEN-QU1): the island, the showdown fight and the end of the scenes. */
        const val ARRIVED_ISLAND = 16
        const val KEY = 30
        const val ABIGALE_FIGHT = 120
        const val ABIGALE_STABBED = 125

        const val SCENE_BEAT = 2
        const val SCENE_WALK = 3

        /** Showdown pacing in timer beats (each [TICK_INTERVAL] ticks): open at 1, throw at 2, pause until 4 (ours, OPEN-QUC6). */
        const val BEAT_OPEN = 1
        const val BEAT_THROW = 2
        const val BEAT_PAUSE = 4
        const val WARDROBE_OPEN_TICKS = 3
        const val WARDROBE_SHAPE = 10
        const val HIT_VISIBLE_TICKS = 2

        /** Reflected knives needed to beat the killer ("a few times", Wiki; OPEN-QUC6) and the damage of a knife that hits the player. */
        const val KILLER_HITS = 3
        const val KNIFE_DAMAGE = 2
        const val SHOWDOWN_SEARCH = 10

        const val SHAKE_RANDOM = 4
        const val SHAKE_AMPLITUDE = 4
        const val SHAKE_RATE = 6

        val ALL_KEYS =
            intArrayOf(Objs.MISTMYST_FRONTDOOR_KEY, Objs.MISTMYST_RUBY_KEY, Objs.MISTMYST_EMERALD_KEY, Objs.MISTMYST_SAPPHIRE_KEY)

        /** Wiki: the ruby, emerald and sapphire doors take the key of their stone; the others stay locked (no key exists for them). */
        val KEY_DOORS =
            listOf(
                Locs.MISTMYST_DOOR_RUBY to Objs.MISTMYST_RUBY_KEY,
                Locs.MISTMYST_DOOR_EMERALD to Objs.MISTMYST_EMERALD_KEY,
            )
        val LOCKED_DOORS =
            intArrayOf(
                Locs.MISTMYST_DOOR_JADE,
                Locs.MISTMYST_DOOR_OPAL,
                Locs.MISTMYST_DOOR_DRAGONSTONE,
                Locs.MISTMYST_DOOR_DIAMOND,
            )

        val DECOY_PAINTINGS =
            intArrayOf(
                Locs.MISTMYST_PAINTING1,
                Locs.MISTMYST_PAINTING2,
                Locs.MISTMYST_PAINTING3,
                Locs.MISTMYST_PAINTING4,
                Locs.MISTMYST_PAINTING5,
            )
        val CANDLES = intArrayOf(Locs.MISTMYST_CANDLE1, Locs.MISTMYST_CANDLE2, Locs.MISTMYST_CANDLE3, Locs.MISTMYST_CANDLE4)
        val CANDLE_VARBITS =
            intArrayOf(Varbits.MISTMYST_CANDLE1, Varbits.MISTMYST_CANDLE2, Varbits.MISTMYST_CANDLE3, Varbits.MISTMYST_CANDLE4)

        // Wiki "Piano (Misthalin Mystery)": D-E-A-D. The interface's key components are named by note and octave.
        val PIANO_SEQUENCE = charArrayOf('D', 'E', 'A', 'D')
        val PIANO_VARBITS =
            intArrayOf(Varbits.MISTMYST_PIANO_D1, Varbits.MISTMYST_PIANO_E, Varbits.MISTMYST_PIANO_A, Varbits.MISTMYST_PIANO_D2)
        val PIANO_KEYS =
            listOf(
                Components.Mistmyst_Piano.KEY_C1 to 'C',
                Components.Mistmyst_Piano.KEY_D1 to 'D',
                Components.Mistmyst_Piano.KEY_E1 to 'E',
                Components.Mistmyst_Piano.KEY_F1 to 'F',
                Components.Mistmyst_Piano.KEY_G1 to 'G',
                Components.Mistmyst_Piano.KEY_A2 to 'A',
                Components.Mistmyst_Piano.KEY_B2 to 'B',
                Components.Mistmyst_Piano.KEY_C2 to 'C',
                Components.Mistmyst_Piano.KEY_D2 to 'D',
                Components.Mistmyst_Piano.KEY_E2 to 'E',
                Components.Mistmyst_Piano.KEY_F2 to 'F',
                Components.Mistmyst_Piano.KEY_G2 to 'G',
                Components.Mistmyst_Piano.KEY_A3 to 'A',
                Components.Mistmyst_Piano.KEY_B3 to 'B',
            )

        val GEM_ORDER = listOf(Gem.SAPPHIRE, Gem.DIAMOND, Gem.ZENYTE, Gem.EMERALD, Gem.ONYX, Gem.RUBY)
        val GEM_VARBITS = Gem.entries.map { it.varbit }.toIntArray()
        val GEM_BUTTONS =
            Gem.entries.flatMap { listOf(it.button to it, it.layer to it) }
        val SELECTED_TEXTS =
            intArrayOf(
                Components.Mistmyst_Gem_Puzzle.SELECTED_1_TEXT,
                Components.Mistmyst_Gem_Puzzle.SELECTED_2_TEXT,
                Components.Mistmyst_Gem_Puzzle.SELECTED_3_TEXT,
                Components.Mistmyst_Gem_Puzzle.SELECTED_4_TEXT,
                Components.Mistmyst_Gem_Puzzle.SELECTED_5_TEXT,
                Components.Mistmyst_Gem_Puzzle.SELECTED_6_TEXT,
            )

        // Positions: cache map 25 75 (locs), Wiki NPC maps (Tayten 1639,4838; Lacey 1629,4850; Mandy 1626,4840).
        val TAYTEN_AT = CoordGrid(1639, 4838, 0)
        val TAYTEN_CLOSET = CoordGrid(1641, 4839, 0)
        val TAYTEN_CAMERA = CoordGrid(1636, 4836, 0)
        val LACEY_AT = CoordGrid(1629, 4850, 0)
        val LACEY_CAMERA = CoordGrid(1626, 4846, 0)
        val MANDY_AT = CoordGrid(1626, 4840, 0)
        val MANDY_OUTSIDE = CoordGrid(1627, 4836, 0)
        val MANDY_CLOSET = CoordGrid(1626, 4838, 0)
        val MANDY_CAMERA = CoordGrid(1623, 4838, 0)
        val BARREL_AT = CoordGrid(1647, 4830, 0)

        /** The east room (candles, shelves, explosive barrel): the fuse blows once the player is out of it. */
        val EAST_ROOM = Area(1641, 4826, 1647, 4832)

        /** Where the Lacey and Mandy scenes start (OPEN-QUC8: the Wiki ties them to leaving / entering by the doors). */
        val LACEY_TRIGGER = Area(1626, 4847, 1634, 4852)
        val MANDY_TRIGGER = Area(1626, 4837, 1628, 4839)

        /** The showdown room west of the sapphire door (cache map: free tiles x 1620-1626, z 4826-4833). */
        val SHOWDOWN_ROOM = Area(1620, 4826, 1626, 4833)
        val SHOWDOWN_CENTRE = CoordGrid(1623, 4829, 0)
        val SHOWDOWN_CAMERA = CoordGrid(1626, 4832, 0)
        val ABIGALE_AT = CoordGrid(1622, 4830, 0)
        val HEWEY_AT = CoordGrid(1624, 4828, 0)
        val HEWEY_CLOSET = CoordGrid(1626, 4826, 0)
        val MANDY_FLOOR = CoordGrid(1621, 4829, 0)
        val GODSWORD_TABLE = CoordGrid(1621, 4831, 0)

        /** Wardrobes of the showdown room (cache loc 30141) and the line each knife travels. */
        val WARDROBES =
            listOf(
                Wardrobe(CoordGrid(1619, 4828, 0), 0, 1, 0),
                Wardrobe(CoordGrid(1622, 4834, 0), 1, 0, -1),
                Wardrobe(CoordGrid(1624, 4825, 0), 3, 0, 1),
                Wardrobe(CoordGrid(1627, 4831, 0), 2, -1, 0),
            )
    }
}

/** Misthalin Mystery's quest variable (varbit 3468) stages: the cache's thresholds, and ours in between (OPEN-QU1). */
internal object Mm {
    const val DRAINED = 25
    const val TAYTEN = 35
    const val NOTE1 = 40
    const val PAINTING = 45
    const val RUBY = 50
    const val BLOWN = 65
    const val LACEY = 70
    const val NOTE2 = 75
    const val PIANO = 80
    const val EMERALD = 85
    const val MANDY = 90
    const val NOTE3 = 95
    const val FIREPLACE = 100
    const val SAPPHIRE = 105
    const val SHOWDOWN = 111
    const val UNMASKED = 115
    const val SHOWDOWN_WON = 130
    const val END = 135

    fun stage(p: PlayerRef): Int = QuestsB.MISTHALIN_MYSTERY.stage(p)
}

/** A rectangle of tiles on one level (inclusive bounds). */
internal class Area(private val x0: Int, private val z0: Int, private val x1: Int, private val z1: Int) {
    fun contains(c: CoordGrid): Boolean = c.x in x0..x1 && c.z in z0..z1
}

/** The six switches of interface 555 (Wiki "Misthalin Mystery": S-D-Z-E-O-R) with the varbit that shows each as flipped. */
internal enum class Gem(val label: String, val varbit: Int, val button: Int, val layer: Int) {
    SAPPHIRE(
        "sapphire",
        Varbits.MISTMYST_SAPPHIRE_SWITCHED,
        Components.Mistmyst_Gem_Puzzle.SAPPHIRE_BUTTON_TEMPLATE,
        Components.Mistmyst_Gem_Puzzle.SAPPHIRE,
    ),
    DIAMOND(
        "diamond",
        Varbits.MISTMYST_DIAMOND_SWITCHED,
        Components.Mistmyst_Gem_Puzzle.DIAMOND_BUTTON_TEMPLATE,
        Components.Mistmyst_Gem_Puzzle.DIAMOND,
    ),
    ZENYTE(
        "zenyte",
        Varbits.MISTMYST_ZENYTE_SWITCHED,
        Components.Mistmyst_Gem_Puzzle.ZENYTE_BUTTON_TEMPLATE,
        Components.Mistmyst_Gem_Puzzle.ZENYTE,
    ),
    EMERALD(
        "emerald",
        Varbits.MISTMYST_EMERALD_SWITCHED,
        Components.Mistmyst_Gem_Puzzle.EMERALD_BUTTON_TEMPLATE,
        Components.Mistmyst_Gem_Puzzle.EMERALD,
    ),
    ONYX("onyx", Varbits.MISTMYST_ONYX_SWITCHED, Components.Mistmyst_Gem_Puzzle.ONYX_BUTTON_TEMPLATE, Components.Mistmyst_Gem_Puzzle.ONYX),
    RUBY("ruby", Varbits.MISTMYST_RUBY_SWITCHED, Components.Mistmyst_Gem_Puzzle.RUBY_BUTTON_TEMPLATE, Components.Mistmyst_Gem_Puzzle.RUBY),
}
