# Quest: Temple of the Eye

Code: `content/quests` `TempleOfTheEye` (plugin), `TempleOfTheEyeTalk.kt` (Persten's amulet part, Herbert, the Tea Seller, the Dark Mage, the
abyssal energy puzzle), `TempleOfTheEyeScenes.kt` (Sedridor, Traiborn, the apprentices, the cutscenes, the tutorial's talk); the tutorial
floor itself is `content/skills` `GuardiansOfTheRift` (guardians-of-the-rift.md GR1, GR16).
Sources: OSRS Wiki /w/Temple_of_the_Eye, /w/Transcript:Temple_of_the_Eye, /w/Wizard_Persten, /w/Tea_Seller, /w/Dark_Mage_(Abyss),
/w/Transcript:Archmage_Sedridor, /w/Great_Guardian; cache 2757 `Quest` row 167 (quest id 168): varbit 13738, end state 130, 1 quest point;
the multi-NPC tables of Persten (11457, 11458), the apprentices (11459-11461) and the portal (loc 43841) fix the stage ranges; the quest's
varbits 13739-13764; items `tote_amulet` 26903, `tote_cup_of_tea_strong` 26904, `tote_incantation` 26905.

| # | Rule |
|---|---|
| TE1 | **Stages** of varbit 13738 (only 0, the ranges below and 130 are cache values): 0; 10 amulet given; 20 the Mage of Zamorak asks for a bucket of water and a strong cup of tea; 30 he drank the tea (the one-time Abyss teleport, varbit 13740); 40 the Dark Mage was washed, the six energies are touchable; 50 the incantation is given; 60 Persten saw it (she stands in Al Kharid up to 60, offers the one-time tower teleport, varbit 13753); 70 Sedridor keeps the incantation, the three apprentices appear in the tower; 85 Traiborn's riddle solved (Persten appears in the tower, 85-90); 91 in the temple (Persten and the apprentices are gone from the tower); 100 the Great Guardian has appeared: the tutorial; 120 the rift is closed, tell Sedridor; 130 finished. |
| TE2 | **Start** (Persten, Al Kharid, npc 11457, Wiki map 3284,3231 r=4): needs 10 Runecraft (not boostable) and Enter the Abyss. She gives the eye amulet. The Wiki's "One Small Favour" line is not played (OPEN-TE1). |
| TE3 | **Herbert** (the Varrock Mage of Zamorak) inspects the amulet and wants a bucket of water and a strong cup of tea; the **Tea Seller** (npc 8687, Wiki map 3270,3413 r=4) hands one over for "Herbert sent me" (varbit 13741 remembers it; a lost one is replaced). With both, Herbert drinks it and offers one teleport to the Abyss' centre (varbit 13740). |
| TE4 | **Dark Mage** (npc 2583 in the Abyss' centre): the bucket of water is thrown over him (talk option or the amulet used on him), he casts the tracing spell and the six energies (locs `tote_abyssal_energy_*`, multi-locs on varbits 13747-13752: 1 touchable, 2 white, 3 gone) must be touched in the player's random order (account values `quests.tote.order`, `.progress`); a wrong touch starts the run again (OPEN-TE2). When all six are white he gives the incantation. |
| TE5 | **Sedridor** takes the incantation (stage 70) and sends the player to Traiborn. Traiborn: "I need your apprentices to help with an incantation." (account flag `quests.tote.traiborn`); each apprentice (tower parents 11459-11461) shows the player their riddle once Traiborn was asked (varbits 13742-13744); the answer 11 (Wiki walkthrough) said to Traiborn through a count dialog sets stage 85. Then Sedridor and Persten hold the Wiki's "ready" conversation and the incantation cutscene teleports the player to the temple (stage 91). |
| TE6 | **The temple and the tutorial.** Stage 91: the three apprentices (temple types 11442, 11445, 11448; varbits 13754-13756) and then Persten (11439): the Wiki's vision and the rift cutscene; stage 100. The tutorial is the minigame's floor shifted by (-1152,-3776) with the quest's own loc ids: the apprentices answer from what the player has done there (account values `gotr.t.*`, written by the minigame code: cell taken, barrier built, two guardians, fragments, guardian essence, altar visits, feedings, a medium guardian, a strengthened barrier). The Great Guardian closes the rift at the third feeding (Wiki: Mind altar, Water altar, Water altar by portal talisman); the quest polls this every 2 ticks (OPEN-TE4), plays the closing cutscene, sends the player to Sedridor (stage 120) and the finale completes the quest. |
| TE7 | **Reward** (Wiki): 1 quest point, 5,000 Runecraft xp, the minigame, a medium pouch if the player owns none. The Wiki's 4,210 xp "during the quest" comes from the tutorial's own actions (cells 30-250, guardians, stones 5 each, fragments). |
| TE8 | After the quest: Sedridor's "How are things at the Temple of the Eye?", Herbert's "About that incantation your initiate gave me..." (varbit 13758), and the portal in the tower leads to the minigame (guardians-of-the-rift.md GR1). |

## OPEN

- **OPEN-TE1:** One Small Favour, the pet-rat and other alternative lines of the Persten transcript that depend on quests not implemented.
- **OPEN-TE2:** the Wiki does not say what a wrong touch of the abyssal energies does; we reset all whites (a touch of the first one starts a new run).
- **OPEN-TE3:** the tower positions of Persten, Sedridor's neighbours and the apprentices (Wiki gives the tower, first floor; Traiborn's room) and the temple Persten's spot are ours.
- **OPEN-TE4:** no event says "the tutorial guardian was fed"; the quest polls the account value. Only one player at a time should use the tutorial floor: it is one shared copy, not an instance (the Wiki's "instance is refreshed" for the altars is not modelled either).
- **OPEN-TE5:** the riddle interface is a message box with the Wiki's two sentences; the "Puzzle interface" of the cache is not identified. Traiborn's other options ("What's a thingummywut?", "Teach me...") are not transcribed.
- **OPEN-TE6:** camera heights, rates and actor positions of all cutscenes (OPEN-QUC5), the portal graphic, the Mage's teleport animation.
- **OPEN-TE7:** the Abyss skull / prayer drain of the Mage's one-time teleport (it is a plain teleport to the Dark Mage).
- **OPEN-TE8:** the tutorial's items dropped on the floor and the altars' "instance refresh".
