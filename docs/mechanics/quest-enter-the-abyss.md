# Miniquest: Enter the Abyss (and the Rune Essence Mine teleports)

Code: `content/quests` `EnterTheAbyss` (dialogue, scrying orb, the Varrock Mage of Zamorak) and `EssenceMine` (`EssenceMineTeleports`).
Sources: OSRS Wiki /w/Enter_the_Abyss, /w/Transcript:Enter_the_Abyss, /w/Scrying_orb, /w/Mage_of_Zamorak, /w/Rune_Essence_Mine,
/w/Transcript:Aubury, /w/Transcript:Wizard_Distentor, /w/Transcript:Wizard_Cromperty, /w/Transcript:Brimstail; cache 2757 `Quest` row 43
(miniquest id 85): varp 492 (`abyssal_miniquest`), end state 4, no quest points; multi-NPCs 3228 (Wilderness mage: varp 492 0-3 -> 2580,
4 -> 2581 with "Teleport") and 3229 (Varrock mage: 1-4 -> 2582, otherwise hidden); varbits `rcu_essencespot_*` 2314-2318 and
`essencemine_portal` 2313. `content/skills` `RunecraftAbyssContent` (RC18) reads varp 492 = 4 for the teleport.

| # | Rule |
|---|---|
| EA1 | Stages of varp 492 (OPEN-EA1: only 0 and 4 are cache values; 1-3 follow the multi-NPC tables): 0 not started; 1 told to meet him in Varrock's chaos temple (the Varrock mage appears); 2 holds the scrying orb; 4 finished. Talking to the Wilderness mage at 0 only sends the player to Varrock (and offers his shop). The Varrock mage (npc 3229, Wiki map 3259,3386 r=5) takes the offer after the Wiki's dialogue tree; "Deal." gives the empty scrying orb (a full backpack sets a "reconsider" flag, the miniquest does not advance). |
| EA2 | **Scrying orb** (empty 5519, full 5518). Teleporting to the Rune Essence Mine with the orb in the backpack records the origin in the cache varbit of that wizard (`rcu_essencespot_wizardstower` 2314, `_aubury` 2315, `_cromperty` 2316, `_brimstail` 2317, `_wizardsguild` 2318); once three differ the empty orb becomes the full one. The readings stay on the account, so a replacement orb fills on the next teleport (Wiki "Scrying orb"). The mage takes the full orb: he also answers "it's in my bank" (full orb in the bank) and "I lost it" (a new empty orb). |
| EA3 | **Reward** (Wiki): 1,000 Runecraft xp, an abyssal book (5520) and a small pouch (5509); varp 492 = 4 (the Abyss teleport works). After it the Varrock mage's menu offers the two information branches of the transcript, and Temple of the Eye adds its own options. |
| EA4 | **Rune Essence Mine teleport** (`EssenceMineTeleports`, resolves OPEN-QU7): Aubury (op 4 "Teleport"), Archmage Sedridor, Wizard Cromperty, Brimstail and Wizard Distentor (op 3) shout "Senventior Disthine Molenko!" and teleport a player who has finished Rune Mysteries (varp 63 = 6; the cache's multi-NPCs only show the op then). The talk options ("Can you teleport me to the Rune Essence?") do the same. The exit portal (loc 34779, a multi-loc on varbit 2313) returns the player to the wizard's tile, kept in the account value `quests.essencemine.return`. Brimstail (Wiki map 2409,9817) and Distentor (2591,3088) are spawned by the plugin. |

## OPEN

- **OPEN-EA1:** intermediate values of varp 492 (see EA1).
- **OPEN-EA2:** the Wiki's rule that the Mage of Zamorak will not talk to players wearing Saradomin or Guthix items needs the list of those items (not sourced); not implemented.
- **OPEN-EA3:** the orb's chat messages when charged are ours ("absorbs some of the energy").
- **OPEN-EA4:** the Wilderness mage's post-miniquest Talk-to reuses the Varrock mage's menu (no transcript).
- **OPEN-EA5:** the Wiki's league auto-complete and the Abyss' dangerous monsters are out of scope here.
- **OPEN-EM1:** the arrival tile in the mine and the values of varbit 2313 per wizard. The cache gives four essence outcrops (loc `blankrunestone` at 2891,4847; 2893,4812; 2925,4848; 2927,4814); the arrival is the first walkable tile two tiles around one outcrop, chosen by wizard. The portal child shown is the one the multi-loc table of 34779 offers for the chosen value (0, 1, 3, 4 or 7).
- **OPEN-EM2:** Cromperty's, Brimstail's and Distentor's other dialogue branches (daily pure essence, teleportation block, crystal items) are not implemented; their Talk-to offers only the teleport.
- **OPEN-EM3:** the animation and graphic of the teleport are not published.
