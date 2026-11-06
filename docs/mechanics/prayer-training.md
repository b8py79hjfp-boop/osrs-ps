# Prayer training

`content/skills/.../PrayerTraining.kt`; the table of bones and ashes is `remains.tsv`, written by
`tools/wiki-import/prayer_remains.py` (DECISIONS D-041). Shared skilling rules: skilling.md. Combat prayers are not here (prayers.md).
Sources, fetched as wikitext from oldschool.runescape.wiki on 2026-11-06:

- (B) "Bones" and each bone's page: the `{{Prayer info}}` template (level, xp, type); "Bones" xp list (bury 4.5, gilded altar with two
  burners or Chaos Temple 15.7, i.e. 3.5x)
- (P) "Prayer training" and "Pay-to-play Prayer training": the Chaos Temple section, the bone comparison table (which bones exist,
  superior dragon bones need 70 Prayer), the offering spells and ashes table
- (C) "Chaos Temple (church)" and "Chaos altar (Prayer)": 3.5x, the 50% spare chance, the spared message, "starting from the top left"
  (22 February 2023 change), altar tile 3239,3608
- (A) cache 2757: iops "Bury" on bones, "Scatter" on ashes (`cache-dump config obj`), loc 411 "Chaos altar" placements,
  gameval seqs `human_pickupfloor` 827, `human_bone_sacrifice` 3705

## Rules

| # | Rule | Source |
|---|---|---|
| PT1 | **Burying.** The backpack op "Bury" on a bone of `remains.tsv` (24 bones, xp per bone from `{{Prayer info}}`: bones, wolf, burnt 4.5; monkey 5; bat 5.3; big, jogre 15; wyrmling 21; zogre 22.5; shaikahan 25; babydragon 30; wyrm 50; strykewyrm 60; wyvern, dragon 72; drake 80; fayrg 84; lava dragon 85; raurg 96; frost dragon 100; hydra 110; dagannoth 125; ourg 140; superior dragon 150, level 70) removes the bone, plays `human_pickupfloor`, gives the xp and holds the player for 1 tick. The click must match the bone in that slot (hostile-input check); a player that is already held does nothing. Messages "You dig a hole in the ground..." then, after the hold, "You bury the bones." | B, P, A; OPEN-PT1, OPEN-PT2 |
| PT2 | **Scattering.** The backpack op "Scatter" on fiendish (10), vile (25), malicious (65), abyssal (85) and infernal ashes (110) works like PT1 with the ashes' xp and the message "You scatter the ashes in the wind.". Plain ashes have no Scatter op. | B, P (ashes table), A; OPEN-PT2 |
| PT3 | **Chaos Temple altar.** A bone used on the Chaos altar (loc 411) at 3239,3608 (level 0) gives 3.5 times its xp and is spared with chance 1 in 2 ("The Dark Lord spares your sacrifice but still rewards you for your efforts."). The bone offered is the first one from the top left of the backpack, whichever bone was used. One offering per click, then a 2-tick hold. Any other altar answers "Nothing interesting happens.". | C; OPEN-PT3, OPEN-PT4 |

## OPEN

- **OPEN-PT1: the hold after burying.** The Wiki pages read ("Bones", "Prayer", "Prayer training", "Tick manipulation", "Game tick")
  state no delay for burying or scattering. The 1-tick hold is the brief's, behind `BURY_DELAY_TICKS`.
- **OPEN-PT2: texts and animation.** The bury / scatter messages, the "level" message and the scatter animation (the bury one is
  reused) are not on the pages read; `human_pickupfloor` is the cache's name for the bury gesture (Wiki "Blackjack" mentions the "bury
  bone animation").
- **OPEN-PT3: which bone the altar takes.** The Wiki change note reads "consumed starting from the top left of the Inventory, rather
  than using the bone that was clicked on"; we take that to mean the first bone of any type. Zealot's robes (5% spare) are not modelled.
- **OPEN-PT4: pace at the altar.** No source gives a minimum gap between two offerings. The Wiki's comparison table assumes about 2,000
  bones an hour by hand at the Chaos altar and 2,550 at a gilded altar, so `OFFER_DELAY_TICKS` is 2.
- **OPEN-PT5: members check.** The Chaos altar bonus is members-only ("Free players may visit the temple, however only members may take
  advantage of the altar's benefits"); the server has no members flag yet. The 2017 confirmation dialogue for burying inside the temple
  and the Elder Chaos druid's unnoting are not implemented.
- **OPEN-PT6: not implemented.** Gilded altars and incense burners (no player-owned houses yet), the Ectofuntus, the libation bowl,
  Sinister and Demonic Offering (Arceuus spellbook, magic module), the bonecrusher necklace and ash sanctifier, the Catacombs prayer
  restore on burying, Prayer-restoring altars ("Pray-at"), ensouled heads (they need the Arceuus Reanimation spells and the Wiki gives
  their xp per spell cast, not per head), urium remains, bird's eggs, the prayer altar in the Wilderness church (2947,3820), and
  "Demonic ashes" (no such obj in cache 2757: the cache has fiendish, vile, malicious, abyssal and infernal ashes).
