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
- (E) "Ectofuntus", "Bonemeal" (the bone to pot table, bury and worship xp), "Ecto-token", "Ghost disciple", 2026-11-07
- (L) "Libation bowl", "Shrine of Ralos", "Altar", "Blessed bone shards", 2026-11-07
- (O) "Bonecrusher necklace" (prayer restored per bone), "Catacombs of Kourend" (restore on burying), "Ash sanctifier", "Sinister Offering",
  "Demonic Offering", 2026-11-07
- (A) cache 2757: iops "Bury" on bones, "Scatter" on ashes (`cache-dump config obj`), loc 411 "Chaos altar" placements,
  gameval seqs `human_pickupfloor` 827, `human_bone_sacrifice` 3705

## Rules

| # | Rule | Source |
|---|---|---|
| PT1 | **Burying.** The backpack op "Bury" on a bone of `remains.tsv` (24 bones, xp per bone from `{{Prayer info}}`: bones, wolf, burnt 4.5; monkey 5; bat 5.3; big, jogre 15; wyrmling 21; zogre 22.5; shaikahan 25; babydragon 30; wyrm 50; strykewyrm 60; wyvern, dragon 72; drake 80; fayrg 84; lava dragon 85; raurg 96; frost dragon 100; hydra 110; dagannoth 125; ourg 140; superior dragon 150, level 70) removes the bone, plays `human_pickupfloor`, gives the xp and holds the player for 1 tick. The click must match the bone in that slot (hostile-input check); a player that is already held does nothing. Messages "You dig a hole in the ground..." then, after the hold, "You bury the bones." | B, P, A; OPEN-PT1, OPEN-PT2 |
| PT2 | **Scattering.** The backpack op "Scatter" on fiendish (10), vile (25), malicious (65), abyssal (85) and infernal ashes (110) works like PT1 with the ashes' xp and the message "You scatter the ashes in the wind.". Plain ashes have no Scatter op. | B, P (ashes table), A; OPEN-PT2 |
| PT3 | **Chaos Temple altar.** A bone used on the Chaos altar (loc 411) at 3239,3608 (level 0) gives 3.5 times its xp and is spared with chance 1 in 2 ("The Dark Lord spares your sacrifice but still rewards you for your efforts."). The bone offered is the first one from the top left of the backpack, whichever bone was used. One offering per click, then a 2-tick hold. Any other altar answers "Nothing interesting happens.". | C; OPEN-PT3, OPEN-PT4 |
| PT4 | **Ectofuntus: bonemeal and slime.** A bone of `bonemeal.tsv` (written by `tools/wiki-import/ectofuntus_bonemeal.py` from the Wiki's "Bonemeal" table: 24 bones, each with its own pot obj, e.g. bones to "Bonemeal" 4255, dragon bones to "Dragon bonemeal" 4261) used on the Loader (loc 16654) of the bone grinder turns it and an empty pot (obj 1931) into that pot of bonemeal, repeating every 3 ticks for each further bone of that kind while empty pots last. A bucket used on a Pool of Slime (locs 17116..17119) becomes a bucket of slime (obj 4286), one every 2 ticks while buckets last. | E; OPEN-PT7 |
| PT5 | **Worship.** "Worship" (op 1) on the Ectofuntus (loc 16648) takes the first pot of bonemeal in the backpack and one bucket of slime, returns the empty pot and bucket, gives the bone's worship xp (4 times its burying xp: bones 18, big bones 60, dragon bones 288, ... superior dragon bones 600; wyrmling bones 120 as listed) and adds 5 to the player's unclaimed ecto-tokens, repeating every 3 ticks while sets last. At 1000 unclaimed tokens: "There isn't room to put any more in." | E; OPEN-PT7 |
| PT6 | **Ecto-tokens.** "Talk-to" on a ghost disciple (npc 2988) pays out the unclaimed tokens (obj 4278) to a player wearing a ghostspeak amulet (obj 552 / 4250) or Morytania legs 2 to 4 (13113..13115). | E; OPEN-PT7 |
| PT7 | **Altars.** "Pray-at" on any loc named "Altar" (an id-specific handler, such as the Ancient Pyramid altar, wins) and "Bask" at the sun Shrine of Ralos (loc 52405) restore Prayer to its base level with `human_pray` 645; at full points they say "You already have full Prayer points." Prayer draining effects are not reset (combat state). | L; OPEN-PT2 |
| PT8 | **Libation bowl** (the Teomat): level 30 Prayer. "Fill" (op 1 on the empty bowl locs 52797 / 52798) uses a jug of blessed sunfire wine (obj 29384, 6 xp a shard) or blessed wine (29386, 5 xp) and adds 400 shards' worth to the bowl, which is then shown as the filled loc (53016 / 53017). "Sacrifice" (op 1) removes up to 100 blessed bone shards (obj 29381) a tick, each worth the bowl's xp, and 2 Prayer points an action, until shards, charges or Prayer run out; at 0 charges the bowl reverts. "Check" (op 2) tells the shards left. The bowl's charges are shared by everyone at that bowl (world state, not saved). | L; OPEN-PT8 |

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
- **OPEN-PT6: not implemented.** (stale entries removed 2026-11-07: ensouled heads exist in content/magic Reanimation; the Ectofuntus, altars and
  libation bowl are PT4-PT8.) Still open: urium remains, bird's eggs, the prayer altar in the Wilderness church (2947,3820) as a special
  case, and "Demonic ashes" (no such obj in cache 2757: the cache has fiendish, vile, malicious, abyssal and infernal ashes).
- **OPEN-PT7: Ectofuntus pace and parts.** The Wiki gives no pace for the grinder, the slime pool or worship ("over the next few seconds", "automated
  process"), so each step is a timed loop (3, 2 and 3 ticks, `Ectofuntus` companion). The grinder's wheel and bin are not separate ops (only the
  automatic loader process), the Pool of Slime's tiers and the Morytania diary teleport, Robin's daily bonemeal and slime, the zealot's robes
  saving chance (1.25% a piece), the worship animation (none is sent) and the messages (none besides the sourced token cap line; ghost lines are
  invented: "You cannot understand what the ghost is saying.", "The ghost disciple has no ecto-tokens for you.") are not modelled. A real
  dialogue with the disciple is not implemented. The unclaimed token count is a saved per-player value (`ectofuntus.tokens`).
- **OPEN-PT8: libation bowl.** The bowl is one shared state per placement (the Wiki does not say whether it is per player; the cache locs have no
  varbit), the bone-shard sources and the blessing of wine at the exposed altar are not implemented, mixing the two wines in one bowl takes the
  last jug's xp rate, the zealot's robes saving shards is not modelled and the sacrifice pace is one action a tick (derived from "10,800 shards
  ... in only 65 seconds"). Texts are invented ("The libation bowl is empty.", "... enough wine for N more blessed bone shards.").
- **OPEN-PT9: gilded altar and incense burners (Construction).** `construction-furniture.tsv` lists the Gilded altar (level 75) and the burners but
  its `locs` column is empty for them, and the house hotspots are not interactive for altars, so the 3.5x offering at a POH altar cannot be hooked
  without loc ids. Nothing is implemented.
- **OPEN-PT10: Sinister and Demonic Offering (content/magic).** Not implemented here (content/magic owns the spellbooks). Proposal, from the Wiki
  pages: Sinister Offering is Arceuus level 92, 180 Magic xp, one blood and one wrath rune (free at the Fountain of Rune), needs A Kingdom Divided,
  offers up to three bones in the backpack for 300% of their burying xp each (superior dragon bones need level 70 Prayer: "You require a Prayer
  level of 70 to do that."), shared 5.4 second (9 tick) cooldown. Demonic Offering is level 84, 175 Magic xp, one soul and one wrath rune, offers
  up to three demonic ashes (the first three, whatever the kind) for 300% of their scattering xp. Both can reuse `remains.tsv` (internal to
  content/skills, so the proposal needs a shared lookup in plugin-api).
- **OPEN-PT11: bonecrusher (necklace) and ash sanctifier.** Both act on an NPC's drop as it is created. No plugin-api hook intercepts drops
  (`LootEvent` is a notification after the obj exists), so nothing is implemented; the engine needs a drop-replacement hook. Rules for it (O): the
  bonecrusher turns bones into Prayer xp (half? the Wiki's "Bonecrusher" page was not read: OPEN), is charged with ecto-tokens (25 charges a token
  for the necklace); the necklace also restores Prayer per bone (bones, burnt, monkey, wolf, bat 1; big, jogre, zogre, wyrmling, shaikahan, small
  ninja monkey 2; baby dragon, bull, wyrm, strykewyrm 3; dragon, frost dragon, wyvern, drake, dagannoth, hydra, fayrg, raurg, lava dragon 4;
  superior dragon, giant, ourg 5); the ash sanctifier scatters ashes without restoring Prayer.
- **OPEN-PT12: Catacombs of Kourend restore on burying.** Burying a bone inside the Catacombs restores Prayer points (O: the table of OPEN-PT11,
  and it does not stack with the necklace). Not implemented: the Wiki gives no region bounds for the Catacombs and the cache dump has no area
  table; it needs a named area first.
