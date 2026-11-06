package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.symbols.Varps

/** Emote tab slots from cache enum 1000, loaded at world start. */
internal object EmoteTab {
    var names: Map<Int, String> = emptyMap()
        internal set

    /** Allows ops 1..3 on every emote slot (RS Mod `EmotesScript.onTabOpen`). */
    fun enableEvents(player: PlayerRef) {
        val slots = names.keys
        if (slots.isEmpty()) return
        player.ifEvents(Components.Emote.CONTENTS, slots.min()..slots.max(), IfEvents.op(1) or IfEvents.op(2) or IfEvents.op(3))
    }
}

/**
 * Emote tab (docs/mechanics/gameframe-buttons.md E1..E7). Emote names come from cache enum 1000 (slot -> name); the
 * seq/spotanim and unlock rules per emote are ported from RS Mod `EmotesScript` / `EmotesConfig` (ISC, DECISIONS D-027).
 */
public class Emotes : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart { EmoteTab.names = enumStrings(EMOTE_NAMES_ENUM) }
        onButton(Components.Emote.CONTENTS) { click -> perform(click.slot, loop = click.op == OP_LOOP) }
    }

    private fun PlayerRef.perform(slot: Int, loop: Boolean) {
        val name = EmoteTab.names[slot] ?: return
        closeModal()
        val emote = EMOTES[name]
        when {
            emote != null -> {
                play(emote, loop)
            }

            name == "Skill Cape" -> {
                skillCape()
            }

            name == "Air Guitar" -> {
                airGuitar()
            }

            name == "Flap" -> {
                unlocked(
                    Varbits.SOS_EMOTE_FLAP,
                    1,
                    SOS_TEXT,
                ) { play(Emote(Seqs.EMOTE_PANIC_FLAP, Seqs.EMOTE_PANIC_FLAP_LOOP), loop) }
            }

            name == "Crazy dance" -> {
                crazyDance(loop)
            }

            name == "Premier Shield" -> {
                premierShield()
            }

            name == "Fortis Salute" -> {
                fortisSalute(loop)
            }

            name == "Crab dance" -> {
                Unit
            }

            // not in the tab (RS Mod)
            else -> {
                message("That emote isn't available yet.")
            } // OPEN E7: Uri transform, Relic unlock, Diary cape
        }
    }

    private fun PlayerRef.play(emote: Emote, loop: Boolean) {
        val lock = emote.lock
        if (lock != null && varbit(lock.varbit) < lock.state) {
            message(lock.text)
            return
        }
        interrupt()
        val seq = if (loop) emote.loopSeq else emote.seq
        anim(seq)
        if (emote.spot >= 0) spotAnim(emote.spot)
        // A looping seq runs until the animation is reset; RS Mod resets it on the next walk (emote_cancelanim trigger).
        if (loop) timer(CANCEL_WATCH, 1, soft = true) { cancelOnMove() }
    }

    private fun PlayerRef.unlocked(varbit: Int, state: Int, text: String, then: () -> Unit) {
        if (varbit(varbit) < state) message(text) else then()
    }

    private fun PlayerRef.cancelOnMove() {
        if (!isMoving) return
        anim(RESET_ANIM)
        stopTimer(CANCEL_WATCH)
    }

    private fun PlayerRef.airGuitar() {
        if (varbit(Varbits.EMOTE_MUSICCAPE) == 0) {
            message(
                "You need to have bought a music cape and have all music tracks unlocked " +
                    "(apart from holiday events) in order to perform that emote.",
            )
            return
        }
        // OPEN E6: RS Mod also plays jingle EMOTE_AIR_GUITAR; plugin-api has no jingle message.
        interrupt()
        anim(Seqs.EMOTE_AIR_GUITAR)
        spotAnim(SpotAnims.AIR_GUITAR_SPOTANIM)
    }

    private fun PlayerRef.crazyDance(loop: Boolean) {
        unlocked(Varbits.EMOTE_GANGNAM, 1, BIRTHDAY_TEXT) {
            // RS Mod alternates style/lasso with a persisted counter; ours is per session (OPEN E6).
            val next = ((this[CRAZY_DANCE] ?: 0) + 1) % 2
            this[CRAZY_DANCE] = next
            val emote =
                if (next ==
                    0
                ) {
                    Emote(Seqs.BDAY17_STYLE, Seqs.BDAY17_STYLE_LOOP)
                } else {
                    Emote(Seqs.BDAY17_LASSO, Seqs.BDAY17_LASSO_LOOP)
                }
            play(emote, loop)
        }
    }

    private fun PlayerRef.premierShield() {
        if (varbit(Varbits.EMOTE_PREMIER_CLUB_2018) < 1) {
            message("This emote is unlocked upon creating an account.")
            return
        }
        val last = this[PREMIER_CLOCK] ?: Long.MIN_VALUE
        if (tick - last < PREMIER_COOLDOWN) {
            message("You're already doing that.")
            return
        }
        val count = this[PREMIER_COUNT] ?: 0
        val spot =
            when (count) {
                1 -> SpotAnims.PREMIER_CLUB_EMOTE_SPOTANIM_SILVER
                2 -> SpotAnims.PREMIER_CLUB_EMOTE_SPOTANIM_GOLD
                else -> SpotAnims.PREMIER_CLUB_EMOTE_SPOTANIM_BRONZE
            }
        this[PREMIER_COUNT] = (count + 1) % 3
        this[PREMIER_CLOCK] = tick
        interrupt()
        anim(Seqs.PREMIER_CLUB_EMOTE)
        spotAnim(spot)
    }

    private fun PlayerRef.fortisSalute(loop: Boolean) {
        if (varp(Varps.COLOSSEUM_GLORY) < GRAND_CHAMPION_GLORY) {
            message("This emote is unlocked by reaching <col=ff0000>Grand Champion</col> status in the Fortis Colosseum.")
            return
        }
        interrupt()
        anim(if (loop) Seqs.EMOTE_VARLAMORE_SALUTE_LOOP else Seqs.EMOTE_VARLAMORE_SALUTE)
        if (loop) timer(CANCEL_WATCH, 1, soft = true) { cancelOnMove() }
    }

    private fun PlayerRef.skillCape() {
        val cape = inv(Invs.WORN).obj(WORN_CAPE_SLOT)
        val name = if (cape >= 0) objName(cape).lowercase() else ""
        val mastery =
            when {
                name == "music cape" || name == "music cape (t)" -> {
                    Mastery(Seqs.EMOTE_AIR_GUITAR, SpotAnims.AIR_GUITAR_SPOTANIM)
                }

                name == "quest point cape" -> {
                    Mastery(Seqs.SKILLCAPES_PLAYER_QUEST_CAPE, SpotAnims.SKILLCAPES_QUEST_CAPE_SPOTANIM)
                }

                name == "max cape" -> {
                    Mastery(Seqs.MAX_CAPE_PLAYER_ANIM, SpotAnims.MAX_CAPE)
                }

                name.startsWith("achievement diary cape") -> {
                    message("That emote isn't available yet.") // OPEN E7: spawns an NPC beside the player
                    return
                }

                else -> {
                    SKILL_CAPES[name.removeSuffix("(t)").removeSuffix(" cape")]
                }
            }
        if (mastery == null) {
            message("You need to be wearing a skillcape in order to perform that emote.")
            return
        }
        interrupt()
        anim(mastery.seq)
        spotAnim(mastery.spot)
    }

    private class Lock(val varbit: Int, val state: Int, val text: String)

    private class Emote(val seq: Int, val loopSeq: Int, val spot: Int = -1, val lock: Lock? = null)

    private class Mastery(val seq: Int, val spot: Int)

    private companion object {
        /** Cache enum 1000 (`emote_names`): emote tab slot -> emote name (cache 2757). */
        const val EMOTE_NAMES_ENUM = 1000

        /** IF button op 2 of an emote = its looping variant (RS Mod `loopAnim`: op2 plays the `_loop` seq). */
        const val OP_LOOP = 2

        /** Anim id that clears the current animation (RS Mod `resetAnim`; RSProt sequence -1 = 65535). */
        const val RESET_ANIM = -1

        /** Worn cape slot (wear position 1). */
        const val WORN_CAPE_SLOT = 1

        /** Colosseum glory needed for the Fortis Salute (RS Mod `fortisSaluteEmote`). */
        const val GRAND_CHAMPION_GLORY = 20_000

        /** Ticks between Premier Shield uses (RS Mod `premierShieldEmote`: `mapClock - clock < 4`). */
        const val PREMIER_COOLDOWN = 4

        const val SOS_TEXT = "You can't use that emote yet - visit the Stronghold of Security to unlock it."
        const val DRILL_TEXT = "You can't use that emote yet - complete the Drill Demon event to unlock them."
        const val MIME_TEXT = "This emote can be unlocked during the mime random event."
        const val GRAVEDIGGER_TEXT = "This emote can be unlocked during the gravedigger random event."
        const val HALLOWEEN_TEXT = "This emote can be unlocked by doing a Halloween seasonal event."
        const val BIRTHDAY_TEXT = "This emote can be unlocked by doing a birthday event."
        const val LOST_TRIBE_TEXT = "This emote can be unlocked during the Lost Tribe quest."

        /** Lost Tribe quest state at which the goblin emotes unlock (RS Mod `lockedLoopAnimDialog(... varbitStateReq = 7)`). */
        const val LOST_TRIBE_DONE = 7

        val CANCEL_WATCH = TimerId("emote_cancel_watch")
        val CRAZY_DANCE = AttrKey<Int>("emote_crazy_dance")
        val PREMIER_COUNT = AttrKey<Int>("emote_premier_count")
        val PREMIER_CLOCK = AttrKey<Long>("emote_premier_clock")

        private fun lock(varbit: Int, text: String, state: Int = 1) = Lock(varbit, state, text)

        /** Plain and unlock-gated emotes by cache name; every id is a gameval symbol (RS Mod `EmotesScript.selectEmote`). */
        val EMOTES: Map<String, Emote> =
            mapOf(
                "Yes" to Emote(Seqs.EMOTE_YES, Seqs.EMOTE_YES_LOOP),
                "No" to Emote(Seqs.EMOTE_NO, Seqs.EMOTE_NO_LOOP),
                "Bow" to Emote(Seqs.EMOTE_BOW, Seqs.EMOTE_BOW_LOOP),
                "Angry" to Emote(Seqs.EMOTE_ANGRY, Seqs.EMOTE_ANGRY_LOOP),
                "Think" to Emote(Seqs.EMOTE_THINK, Seqs.EMOTE_THINK_LOOP),
                "Wave" to Emote(Seqs.EMOTE_WAVE, Seqs.EMOTE_WAVE_LOOP),
                "Shrug" to Emote(Seqs.EMOTE_SHRUG, Seqs.EMOTE_SHRUG_LOOP),
                "Cheer" to Emote(Seqs.EMOTE_CHEER, Seqs.EMOTE_CHEER_LOOP),
                "Beckon" to Emote(Seqs.EMOTE_BECKON, Seqs.EMOTE_BECKON_LOOP),
                "Laugh" to Emote(Seqs.EMOTE_LAUGH, Seqs.EMOTE_LAUGH_LOOP),
                "Jump for Joy" to Emote(Seqs.EMOTE_JUMP_WITH_JOY, Seqs.EMOTE_JUMP_WITH_JOY_LOOP),
                "Yawn" to Emote(Seqs.EMOTE_YAWN, Seqs.EMOTE_YAWN_LOOP),
                "Dance" to Emote(Seqs.EMOTE_DANCE, Seqs.EMOTE_DANCE_LOOP),
                "Jig" to Emote(Seqs.EMOTE_DANCE_SCOTTISH, Seqs.EMOTE_DANCE_SCOTTISH_LOOP),
                "Spin" to Emote(Seqs.EMOTE_DANCE_SPIN, Seqs.EMOTE_DANCE_SPIN_LOOP),
                "Headbang" to Emote(Seqs.EMOTE_DANCE_HEADBANG, Seqs.EMOTE_DANCE_HEADBANG_LOOP),
                "Cry" to Emote(Seqs.EMOTE_CRY, Seqs.EMOTE_CRY_LOOP),
                "Blow Kiss" to Emote(Seqs.EMOTE_BLOW_KISS, Seqs.EMOTE_BLOW_KISS_LOOP),
                "Panic" to Emote(Seqs.EMOTE_PANIC, Seqs.EMOTE_PANIC_LOOP),
                "Raspberry" to Emote(Seqs.EMOTE_YA_BOO_SUCKS, Seqs.EMOTE_YA_BOO_SUCKS_LOOP),
                "Clap" to Emote(Seqs.EMOTE_CLAP, Seqs.EMOTE_CLAP_LOOP),
                "Salute" to Emote(Seqs.EMOTE_FREMMENIK_SALUTE, Seqs.EMOTE_FREMMENIK_SALUTE_LOOP),
                "Goblin Bow" to
                    Emote(
                        Seqs.HUMAN_CAVE_GOBLIN_BOW,
                        Seqs.HUMAN_CAVE_GOBLIN_BOW_LOOP,
                        lock = lock(Varbits.LOST_TRIBE_QUEST, LOST_TRIBE_TEXT, LOST_TRIBE_DONE),
                    ),
                "Goblin Salute" to
                    Emote(
                        Seqs.HUMAN_CAVE_GOBLIN_DANCE,
                        Seqs.HUMAN_CAVE_GOBLIN_DANCE_LOOP,
                        lock = lock(Varbits.LOST_TRIBE_QUEST, LOST_TRIBE_TEXT, LOST_TRIBE_DONE),
                    ),
                "Glass Box" to Emote(Seqs.EMOTE_GLASS_BOX, Seqs.EMOTE_GLASS_BOX_LOOP, lock = lock(Varbits.EMOTE_GLASSBOX, MIME_TEXT)),
                "Climb Rope" to
                    Emote(Seqs.EMOTE_CLIMBING_ROPE, Seqs.EMOTE_CLIMBING_ROPE_LOOP, lock = lock(Varbits.EMOTE_CLIMBROPE, MIME_TEXT)),
                "Lean" to Emote(Seqs.EMOTE_MIME_LEAN, Seqs.EMOTE_MIME_LEAN_LOOP, lock = lock(Varbits.EMOTE_LEAN, MIME_TEXT)),
                "Glass Wall" to Emote(Seqs.EMOTE_GLASS_WALL, Seqs.EMOTE_GLASS_WALL_LOOP, lock = lock(Varbits.EMOTE_GLASSWALL, MIME_TEXT)),
                "Idea" to
                    Emote(
                        Seqs.EMOTE_LIGHTBULB,
                        Seqs.EMOTE_LIGHTBULB_LOOP,
                        SpotAnims.EMOTE_LIGHTBULB_SPOT,
                        lock(Varbits.SOS_EMOTE_IDEA, SOS_TEXT),
                    ),
                "Stamp" to
                    Emote(
                        Seqs.EMOTE_STAMPFEET,
                        Seqs.EMOTE_STAMPFEET_LOOP,
                        SpotAnims.EMOTE_DUSTSTAMP_SPOT,
                        lock(Varbits.SOS_EMOTE_STAMP, SOS_TEXT),
                    ),
                // RS Mod gates Slap Head on the Idea varbit (sos_emote_idea); kept as is.
                "Slap Head" to Emote(Seqs.EMOTE_SLAP_HEAD, Seqs.EMOTE_SLAP_HEAD_LOOP, lock = lock(Varbits.SOS_EMOTE_IDEA, SOS_TEXT)),
                "Zombie Walk" to
                    Emote(Seqs.ZOMBIE_WALK_EMOTE, Seqs.ZOMBIE_WALK_EMOTE_LOOP, lock = lock(Varbits.EMOTE_ZOMBIE_WALK, GRAVEDIGGER_TEXT)),
                "Zombie Dance" to
                    Emote(Seqs.ZOMBIE_DANCE, Seqs.ZOMBIE_DANCE_LOOP, lock = lock(Varbits.EMOTE_ZOMBIE_DANCE, GRAVEDIGGER_TEXT)),
                "Scared" to Emote(Seqs.TERRIFIED_EMOTE, Seqs.TERRIFIED_EMOTE_LOOP, lock = lock(Varbits.EMOTE_TERRIFIED, HALLOWEEN_TEXT)),
                "Rabbit Hop" to
                    Emote(
                        Seqs.RABBIT_EMOTE,
                        Seqs.RABBIT_EMOTE_LOOP,
                        lock = lock(Varbits.EMOTE_BUNNY_HOP, "This emote can be unlocked by doing an Easter seasonal event."),
                    ),
                "Sit up" to Emote(Seqs.EMOTE_SITUPS_5, Seqs.EMOTE_SITUPS_5_LOOP, lock = lock(Varbits.EMOTE_DRILLDEMON, DRILL_TEXT)),
                "Push up" to Emote(Seqs.EMOTE_PUSHUPS_5, Seqs.EMOTE_PUSHUPS_5_LOOP, lock = lock(Varbits.EMOTE_DRILLDEMON, DRILL_TEXT)),
                "Star jump" to Emote(Seqs.EMOTE_STARJUMP_5, Seqs.EMOTE_STARJUMP_5_LOOP, lock = lock(Varbits.EMOTE_DRILLDEMON, DRILL_TEXT)),
                "Jog" to Emote(Seqs.EMOTE_RUN_ON_SPOT, Seqs.EMOTE_RUN_ON_SPOT_LOOP, lock = lock(Varbits.EMOTE_DRILLDEMON, DRILL_TEXT)),
                "Flex" to
                    Emote(
                        Seqs.EMOTE_FLEX,
                        Seqs.EMOTE_FLEX_LOOP,
                        lock = lock(Varbits.EMOTE_FLEX, "You can unlock this emote by completing Checkal's task in Below Ice Mountain."),
                    ),
                // Zombie Hand and Smooth dance have no loop seq in RS Mod: both ops play the single seq.
                "Zombie Hand" to
                    Emote(
                        Seqs.HW07_ARM_FROM_THE_GROUND_EMOTE,
                        Seqs.HW07_ARM_FROM_THE_GROUND_EMOTE,
                        lock = lock(Varbits.EMOTE_ZOMBIE_HAND, HALLOWEEN_TEXT),
                    ),
                "Hypermobile Drinker" to Emote(Seqs.ASH_EMOTE, Seqs.ASH_EMOTE_LOOP, lock = lock(Varbits.EMOTE_ASH, HALLOWEEN_TEXT)),
                "Smooth dance" to Emote(Seqs.BDAY17_BLING, Seqs.BDAY17_BLING, lock = lock(Varbits.EMOTE_HOTLINE_BLING, BIRTHDAY_TEXT)),
                "Explore" to
                    Emote(
                        Seqs.EMOTE_EXPLORE,
                        Seqs.EMOTE_EXPLORE_LOOP,
                        lock = lock(Varbits.EMOTE_EXPLORE, "This emote can be unlocked by completing at least 600 beginner clue scrolls."),
                    ),
                "Party" to
                    Emote(
                        Seqs.EMOTE_PARTY,
                        Seqs.EMOTE_PARTY_LOOP,
                        SpotAnims.FX_EMOTE_PARTY01_ACTIVE,
                        lock(Varbits.EMOTE_PARTY, BIRTHDAY_TEXT),
                    ),
                "Trick" to
                    Emote(
                        Seqs.EMOTE_TRICK,
                        Seqs.EMOTE_TRICK,
                        SpotAnims.HW23_EMOTE_BAT_SPOTANIM,
                        lock(Varbits.EMOTE_TRICK, "This emote can be unlocked by doing a Halloween event."),
                    ),
                // RS Mod passes (sit_loop, sit) as (op1, op2) for Sit down.
                "Sit down" to Emote(Seqs.EMOTE_SIT_LOOP, Seqs.EMOTE_SIT),
            )

        private fun cape(seq: Int, spot: Int) = Mastery(seq, spot)

        /** Skill cape emotes by skill name (RS Mod `EmotesConfig` `skill_cape_anims` / `skill_cape_spots`). */
        val SKILL_CAPES: Map<String, Mastery> =
            mapOf(
                "attack" to cape(Seqs.SKILL_CAPE_ATTACK, SpotAnims.SKILLCAPE_ATTACK_SPOTANIM),
                "defence" to cape(Seqs.SKILL_CAPE_DEFEND, SpotAnims.SKILLCAPE_DEFEND_SPOTANIM),
                "strength" to cape(Seqs.SKILLCAPES_HUMAN_STRENGTH, SpotAnims.SKILLCAPES_STRENGTH),
                "hitpoints" to cape(Seqs.SKILLCAPES_HUMAN_HITPOINTS, SpotAnims.SKILLCAPES_HITPOINTS),
                "ranging" to cape(Seqs.SKILLCAPES_HUMAN_RANGE, SpotAnims.SKILLCAPES_RANGE),
                "prayer" to cape(Seqs.SKILLCAPES_HUMAN_PRAYER, SpotAnims.SKILLCAPES_PRAYER),
                "magic" to cape(Seqs.SKILLCAPES_PLAYER_MAGIC, SpotAnims.SKILLCAPES_MAGIC_SPOTANIM),
                "cooking" to cape(Seqs.SKILLCAPES_COOKING_PLAYER_ANIM, SpotAnims.SKILLCAPES_COOKING_SPOTANIM),
                "woodcutting" to cape(Seqs.SKILLCAPES_WOODCUTTING_PLAYER_ANIM, SpotAnims.SKILLCAPES_WOODCUTTING_SPOTANIM),
                "fletching" to cape(Seqs.SKILLCAPES_PLAYER_FLETCHING_BOW, SpotAnims.SKILLCAPES_FLETCHING_BOW_SPOTANIM),
                "fishing" to cape(Seqs.SKILLCAPES_FISHING_PLAYER_ANIM, SpotAnims.SKILLCAPES_FISHING_SPOTANIM),
                "firemaking" to cape(Seqs.SKILLCAPES_HUMAN_FIREMAKING, SpotAnims.SKILLCAPES_FIREMAKING),
                "crafting" to cape(Seqs.SKILLCAPES_CRAFTING_PLAYER_ANIM, SpotAnims.SKILLCAPES_CRAFTING_SPOTANIM),
                "smithing" to cape(Seqs.SKILLCAPES_PLAYER_SMITHING, SpotAnims.SKILLCAPES_SMITHING_SPOTANIM),
                "mining" to cape(Seqs.SKILLCAPES_PLAYER_MINING, SpotAnims.SKILLCAPES_MINING_SPOTANIM),
                "herblore" to cape(Seqs.SKILLCAPES_HUMAN_HERBLORE, SpotAnims.SKILLCAPES_HERBLORE),
                "agility" to cape(Seqs.SKILLCAPES_HUMAN_AGILITY, SpotAnims.SKILLCAPES_AGILITY),
                "thieving" to cape(Seqs.SKILL_CAPE_THIEVING, SpotAnims.SKILLCAPE_THIEVING_SPOTANIM),
                "slayer" to cape(Seqs.SKILL_CAPE_SLAYER, SpotAnims.SKILLCAPE_SLAYER_SPOTANIM),
                "farming" to cape(Seqs.SKILLCAPE_FARMING, SpotAnims.SKILLCAPE_FARMING_SPOTANIM),
                "runecraft" to cape(Seqs.SKILLCAPES_PLAYER_RUNECRAFTING, SpotAnims.SKILLCAPES_RUNECRAFTING_SPOTANIM),
                "hunter" to cape(Seqs.SKILLCAPES_HUMAN_HUNTING, SpotAnims.SKILLCAPES_HUNTING),
                "construct." to cape(Seqs.SKILLCAPES_CONSTRUCTION_PLAYER_ANIM, SpotAnims.SKILLCAPES_CONSTRUCTION_SPOTANIM),
            )
    }
}
