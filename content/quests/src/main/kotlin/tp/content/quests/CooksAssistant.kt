@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Cook's Assistant (docs/mechanics/quest-cooks-assistant.md): the Lumbridge cook wants a bucket of milk, a pot of flour and an
 * egg. The quest variable (varp 29) is 0 / 1 / 2; which ingredients were handed in are account flags (OPEN-QU2). Dialogue is the
 * Wiki "Transcript:Cook's Assistant" and "Transcript:Cook (Lumbridge)".
 */
public class CooksAssistant : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.COOK, OP_TALK) { cook ->
            player.stopInteraction()
            when (Quests.COOKS_ASSISTANT.state(player)) {
                QuestState.NOT_STARTED -> starting(cook)
                QuestState.IN_PROGRESS -> again(cook)
                QuestState.FINISHED -> after(cook)
            }
        }
        onOpNpc(Npcs.MILLIE_THE_MILLER, OP_TALK) { millie ->
            player.stopInteraction()
            millieTalk(millie)
        }
        onOpNpc(Npcs.GILLIE_THE_MILKMAID, OP_TALK) { gillie ->
            player.stopInteraction()
            gillieTalk(gillie)
        }
        events.on<JournalRequest> { if (it.quest == Quests.COOKS_ASSISTANT) it.lines += journal(it.player) }
    }

    // ---- state

    private fun PlayerRef.handed(item: Ingredient): Boolean = flag(item.key)

    private fun PlayerRef.allHanded(): Boolean = Ingredient.entries.all { handed(it) }

    private fun journal(p: PlayerRef): List<String> =
        when (Quests.COOKS_ASSISTANT.state(p)) {
            QuestState.NOT_STARTED -> {
                listOf("I can start this quest by speaking to the Cook in the kitchen of Lumbridge Castle.")
            }

            QuestState.IN_PROGRESS -> {
                listOf("The Cook needs a bucket of milk, a pot of flour and an egg to bake the Duke's birthday cake.") +
                    Ingredient.entries.map { if (p.handed(it)) "I have given him ${it.text}." else "I still need to bring him ${it.text}." }
            }

            QuestState.FINISHED -> {
                listOf("I brought the Cook the ingredients for the Duke's birthday cake.")
            }
        }

    // ---- the cook

    private suspend fun PlayerScript.starting(cook: NpcRef) {
        npc(cook, "What am I to do?")
        while (true) {
            val pick =
                choice(
                    "Select an option",
                    "What's wrong?",
                    "You're a cook, why don't you bake me a cake?",
                    "You don't look very happy.",
                    "Nice hat!",
                )
            when (pick) {
                1 -> {
                    me("What's wrong?")
                    return whatsWrong(cook)
                }

                2 -> {
                    me("You're a cook, why don't you bake me a cake?")
                    npc(cook, "*sniff* Don't talk to me about cakes...")
                    me("What's wrong?")
                    return whatsWrong(cook)
                }

                3 -> {
                    me("You don't look very happy.")
                    npc(cook, "No, I'm not. The world is caving in around me - I am overcome by dark feelings of impending doom.")
                    if (unhappy(cook)) return
                }

                4 -> {
                    me("Nice hat!")
                    npc(cook, "Err thank you. It's a pretty ordinary cooks hat really.")
                    me("Still, suits you. The trousers are pretty special too.")
                    npc(cook, "Its all standard cook's issue uniform...")
                    me("The whole hat, apron, stripey trousers ensemble - it works. It make you looks like a real cook.")
                    npc(
                        cook,
                        "I am a real cook! I haven't got time to be chatting about Culinary Fashion. I am in desperate need of " +
                            "help!",
                    )
                    me("What's wrong?")
                    return whatsWrong(cook)
                }

                else -> {
                    return
                }
            }
        }
    }

    /** After "You don't look very happy." Returns true when the conversation moved on. */
    private suspend fun PlayerScript.unhappy(cook: NpcRef): Boolean {
        when (choice("Select an option", "What's wrong?", "I'd take the rest of the day off if I were you.")) {
            1 -> {
                me("What's wrong?")
                whatsWrong(cook)
                return true
            }

            2 -> {
                me("I'd take the rest of the day off if I were you.")
                npc(cook, "No, that's the worst thing I could do. I'd get in terrible trouble.")
                me("Well maybe you need to take a holiday...")
                npc(cook, "That would be nice, but the Duke doesn't allow holidays for core staff.")
                me("Hmm, why not run away to the sea and start a new life as a Pirate?")
                npc(cook, "My wife gets sea sick, and I have an irrational fear of eyepatches. I don't see it working myself.")
                me("I'm afraid I've run out of ideas.")
                npc(cook, "I know I'm doomed.")
                me("What's wrong?")
                whatsWrong(cook)
                return true
            }

            else -> {
                return true
            }
        }
    }

    private suspend fun PlayerScript.whatsWrong(cook: NpcRef) {
        npc(
            cook,
            "Oh dear, oh dear, oh dear, I'm in a terrible terrible mess! It's the Duke's birthday today, and I should be making him a " +
                "lovely big birthday cake.",
            "I've forgotten to buy the ingredients. I'll never get them in time now. He'll sack me! What will I do? I have four " +
                "children " +
                "and a goat to look after. Would you help me? Please?",
        )
        if (choice("Start the Cook's Assistant quest?", "Yes.", "No.") != 1) {
            me("No, I don't feel like it. Maybe later.")
            npc(cook, "Fine. I always knew you Adventurer types were callous beasts. Go on your merry way!")
            return
        }
        me("Yes, I'll help you.")
        npc(cook, "Oh thank you, thank you. I need milk, an egg and flour. I'd be very grateful if you can get them for me.")
        Quests.COOKS_ASSISTANT.advance(player, STARTED)
        val p = player
        if (Ingredient.entries.all { p.has(it.obj) }) {
            me("I have all of those ingredients on me already!")
            npc(cook, "That's an odd coincidence... Were you planning on making a cake too?")
            me(
                "Not exactly. I just had an odd feeling you might be needing these ingredients. If I see a cook, I presume " +
                    "there's food " +
                    "of some kind! Lucky guess I suppose.",
            )
            npc(cook, "Well thank you! Hand them over, please.")
            for (item in Ingredient.entries) handIn(item)
            itemBox(Objs.POT_FLOUR, "You hand the cook all of the ingredients.")
            finish(cook)
            return
        }
        me("So where do I find these ingredients then?")
        information(cook, first = true)
    }

    private suspend fun PlayerScript.again(cook: NpcRef) {
        val p = player
        npc(cook, "How are you getting on with finding the ingredients?")
        val carrying = Ingredient.entries.filter { !p.handed(it) && p.has(it.obj) }
        if (carrying.isEmpty()) {
            me("I haven't got any of them yet, I'm still looking.")
            npc(cook, "Please get the ingredients quickly - I'm running out of time! The Duke will throw me into the streets!")
            val missing = Ingredient.entries.filter { !p.handed(it) }
            if (missing.size < Ingredient.entries.size) {
                box("You still need to get:<br>" + missing.joinToString("<br>") { it.listed })
            }
            when (choice("Select an option", "I'll get right on it.", "Can you remind me how to find these things again?")) {
                2 -> {
                    me("So where do I find these ingredients then?")
                    information(cook, first = false)
                }

                else -> {
                    Unit
                }
            }
            return
        }
        for (item in carrying) {
            me(item.give)
            handIn(item)
        }
        if (p.allHanded()) {
            finish(cook)
        } else {
            npc(
                cook,
                "Thanks for the ingredients you have got so far. Please get the rest quickly - I'm running out of time! The Duke " +
                    "will throw me into the streets!",
            )
        }
    }

    private fun PlayerScript.handIn(item: Ingredient) {
        if (player.take(item.obj, 1, "cooks assistant ${item.name.lowercase()}")) player.setFlag(item.key, true)
    }

    private suspend fun PlayerScript.finish(cook: NpcRef) {
        npc(cook, "You've brought me everything I need! I am saved! Thank you!")
        me("So do I get to go to the Duke's Party?")
        npc(cook, "I'm afraid not, only the big cheeses get to dine with the Duke.")
        me("Well, maybe one day I'll be important enough to sit on the Duke's table.")
        npc(cook, "Maybe, but I won't be holding my breath.")
        for (item in Ingredient.entries) player.setFlag(item.key, false)
        QuestCompletion.complete(player, Quests.COOKS_ASSISTANT, REWARD)
    }

    /** The "where do I find..." menu; [first] is the one right after starting the quest. */
    private suspend fun PlayerScript.information(cook: NpcRef, first: Boolean) {
        var exit = if (first) "Actually, I know where to find this stuff." else "I've got all the information I need. Thanks."
        while (true) {
            when (choice("Select an option", "Where do I find some flour?", "How about milk?", "And eggs? Where are they found?", exit)) {
                1 -> {
                    me("Where do I find some flour?")
                    npc(
                        cook,
                        "There is a Mill fairly close, go North and then West. Mill Lane Mill is just off the road to Draynor. I " +
                            "usually get my flour from there.",
                    )
                    if (player.has(Objs.POT_EMPTY)) {
                        npc(
                            cook,
                            "Talk to Millie, she'll help, she's a lovely girl and a fine Miller. Make sure you take a pot with you " +
                                "for the flour though, you've got one on you already.",
                        )
                    } else {
                        npc(
                            cook,
                            "Talk to Millie, she'll help, she's a lovely girl and a fine Miller. Make sure you take a pot with you " +
                                "for the flour though, there should be one on the table in here.",
                        )
                    }
                }

                2 -> {
                    me("How about milk?")
                    npc(
                        cook,
                        "There is a cattle field on the other side of the river, just across the road from the Groats' Farm.",
                        "Talk to Gillie Groats, she looks after the Dairy cows - she'll tell you everything you need to know " +
                            "about milking cows!",
                    )
                    if (player.has(Objs.BUCKET_EMPTY)) {
                        npc(
                            cook,
                            "You'll need an empty bucket for the milk itself. I do see you've got a bucket with you already " +
                                "luckily!",
                        )
                    } else {
                        npc(
                            cook,
                            "You'll need an empty bucket for the milk itself. The general store just north of the castle will sell " +
                                "you one for a couple of coins.",
                        )
                    }
                }

                3 -> {
                    me("And eggs? Where are they found?")
                    npc(
                        cook,
                        "I normally get my eggs from the Groats' farm, on the other side of the river.",
                        "But any chicken should lay eggs.",
                    )
                }

                4 -> {
                    me(exit)
                    return
                }

                else -> {
                    return
                }
            }
            exit = "I've got all the information I need. Thanks."
        }
    }

    private suspend fun PlayerScript.after(cook: NpcRef) {
        npc(cook, "How is the adventuring going, my friend?")
        while (true) {
            val pick =
                choice(
                    "Select an option",
                    "Do you have any other quests for me?",
                    "I am getting strong and mighty.",
                    "I keep on dying.",
                    "Can I use your range?",
                )
            when (pick) {
                1 -> {
                    me("Do you have any other quests for me?")
                    // Members worlds continue into Recipe for Disaster (OPEN-QU1: not implemented).
                    npc(cook, "I don't have anything for you to do right now, sorry.")
                    return
                }

                2 -> {
                    me("I am getting strong and mighty. Grrr")
                    npc(cook, "Glad to hear it.")
                    return
                }

                3 -> {
                    me("I keep on dying.")
                    npc(cook, "Ah well, at least you keep coming back to life!")
                    return
                }

                4 -> {
                    me("Can I use your range?")
                    npc(
                        cook,
                        "Go ahead - it's a very good range. It's easier to use than most other ranges.",
                        "Its called the Cook-o-matic 100. It uses a combination of state of the art temperature regulation and magic.",
                    )
                    me("Will it mean my food will burn less often?")
                    npc(cook, "Well, that's what the salesman told us anyway...")
                    me("Thanks?")
                    return
                }

                else -> {
                    return
                }
            }
        }
    }

    // ---- Millie and Gillie (optional help, Wiki transcripts)

    private suspend fun PlayerScript.millieTalk(millie: NpcRef) {
        npc(millie, "Hello Adventurer. Welcome to Mill Lane Mill. Can I help you?")
        when (choice("Select an option", "Who are you?", "What is this place?", "How do I mill flour?", "I'm fine, thanks.")) {
            1 -> {
                me("Who are you?")
                npc(millie, "I'm Miss Millicent Miller the Miller of Mill Lane Mill. Our family have been milling flour for generations.")
                me("It's a good business to be in. People will always need flour.", "How do I mill flour?")
                millingHow(millie)
            }

            2 -> {
                me("What is this place?")
                npc(
                    millie,
                    "This is Mill Lane Mill. Millers of the finest flour in Gielinor, and home to the Miller family for many generations",
                    "We take grain from the field nearby and mill into flour.",
                )
                me("How do I mill flour?")
                millingHow(millie)
            }

            3 -> {
                me("How do I mill flour?")
                millingHow(millie)
            }

            4 -> {
                me("I'm fine, thanks.")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.millingHow(millie: NpcRef) {
        npc(
            millie,
            "Making flour is pretty easy. First of all you need to get some grain. You can pick some from wheat fields. There " +
                "is one just " +
                "outside the Mill, but there are many others scattered across Gielinor. Feel free to pick wheat from our field! " +
                "There always " +
                "seems to be plenty of wheat there.",
        )
        me("Then I bring my wheat here?")
        npc(
            millie,
            "Yes, or one of the other mills in Gielinor. They all work the same way. Just take your grain to the top floor of the mill " +
                "(up two ladders, there are three floors including this one) and then place some grain into the hopper.",
            "Then you need to start the grinding process by pulling the hopper lever. You can add more grain, but each time you " +
                "add grain " +
                "you have to pull the hopper lever again.",
        )
        me("So where does the flour go then?")
        npc(
            millie,
            "The flour appears in this room here, you'll need a pot to put the flour into. One pot will hold the flour made by " +
                "one load of grain",
            "And that's it! You now have some pots of finely ground flour of the highest quality. Ideal for making tasty cakes " +
                "or delicous " +
                "bread. I'm not a cook so you'll have to ask a cook to find out how to bake things.",
        )
        me("Great! Thanks for your help.")
    }

    private suspend fun PlayerScript.gillieTalk(gillie: NpcRef) {
        npc(gillie, "Hello, I'm Gillie the Milkmaid. What can I do for you?")
        when (choice("Select an option", "Who are you?", "Can you tell me how to milk a cow?", "I'm fine, thanks.")) {
            1 -> {
                npc(gillie, "My name is Gillie Groats. My father is a farmer and I milk the cows for him.")
                me("Do you have any buckets of milk spare?")
                npc(
                    gillie,
                    "I'm afraid not. We need all of our milk to sell to market, but you can milk the cow yourself if you need the " +
                        "milk.",
                )
                me("Thanks.")
            }

            2 -> {
                me("So how do you get milk from a cow then?")
                npc(
                    gillie,
                    "It's very easy. First you need an empty bucket to hold the milk.",
                    "Then find a dairy cow to milk - you can't milk just any cow.",
                )
                me("How do I find a dairy cow?")
                npc(
                    gillie,
                    "They are easy to spot - they are dark brown and white, unlike beef cows, which are light brown and white. " +
                        "We also tether " +
                        "them to a post to stop them wandering around all over the place.",
                    "There are a couple very near, in this field.",
                    "Then just milk the cow and your bucket will fill with tasty, nutritious milk.",
                )
            }

            3 -> {
                me("I'm fine, thanks.")
            }

            else -> {
                Unit
            }
        }
    }

    private enum class Ingredient(val obj: Int, val text: String, val listed: String, val give: String) {
        MILK(Objs.BUCKET_MILK, "a bucket of milk", "A bucket of milk", "Here's a bucket of milk."),
        FLOUR(Objs.POT_FLOUR, "a pot of flour", "A pot of flour", "Here's a pot of flour."),
        EGG(Objs.EGG, "an egg", "An egg", "Here's a fresh egg."),
        ;

        val key: String get() = "quests.cooks.${name.lowercase()}"
    }

    private companion object {
        const val OP_TALK = 1

        /** First stage of the quest variable: started (end state 2 is the cache's `endstate`). */
        const val STARTED = 1

        /** Wiki "Cook's Assistant" rewards: 1 quest point, 300 Cooking experience, the Cook-o-matic 100. */
        val REWARD =
            QuestReward(
                model = Objs.CAKE,
                xp = listOf(Stats.COOKING to 300.0),
                notes = listOf("Access to the Cook-o-matic 100"),
            )
    }
}
