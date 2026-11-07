# Quest: Romeo & Juliet

Code: `content/quests` `RomeoAndJuliet`. Sources: OSRS Wiki /w/Romeo_%26_Juliet, /w/Transcript:Romeo_%26_Juliet and the NPC transcripts
of Romeo, Juliet, Phillipa, Draul Leptoc, Father Lawrence and the Apothecary; cache `Quest` row 121 (id 4): varp 144 (`rjquest`),
end state 100, 5 quest points, no stat XP.

| # | Rule |
|---|---|
| RJ1 | Start: Romeo (5037, Varrock Square 3211,3425). Varp 144: 0, 10 started, 20 Juliet's message given, 30 message delivered, 40 sent by Father Lawrence, 50 the Apothecary needs berries, 60 potion made, 70 Juliet drank it, 100 finished (OPEN-QU1: 0 and 100 are cache values). |
| RJ2 | Juliet (5035, first floor of the Leptoc mansion 3159,3426) gives the message (obj 755) and takes the cadava potion; Draul Leptoc (5039) and Phillipa (5040) comment by stage. |
| RJ3 | Romeo takes the message and sends the player to Father Lawrence (5038, 3254,3485), who suggests a cadava potion from the Apothecary (5036, 3195,3404, already spawned). |
| RJ4 | The Apothecary asks for cadava berries (obj 753), picked from cadava bushes (locs 23625 / 23626 "Pick-from", by the south-east Varrock mine), and gives the potion (obj 756) via one transaction. |
| RJ5 | Giving Juliet the potion plays the cutscene dialogue (Phillipa, Draul); stage 70. Telling Romeo then plays the crypt dialogue and finishes the quest. |
| RJ6 | Reward: 5 quest points (cache). The Wiki's antique lamp is OPEN-QU9. |

OPEN: OPEN-QU8 (cutscenes), OPEN-QU9, OPEN-QU14 (bushes do not deplete), OPEN-QU15 (Apothecary's potion shop). Drinking the
cadava potion has no special handling (Wiki: outside the Apothecary the player "dares not"; at the Apothecary's they black out).
