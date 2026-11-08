# Muttadiles (CoX combat room)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Muttadile" (Page), "Meat tree" (Tree), "Chambers of Xeric"
(Main), "Chambers of Xeric/Strategies" (Strat), "Chambers of Xeric/Challenge Mode" (CM); cache 2757 (`cache-dump config
npc <id>`; the `stats` array is `[1,1,1,1,1,1]` on every Chambers NPC because the raid scales them at runtime, so numbers
come from the wiki infobox and the cache `params` map: params 5 / 6 / 7 / 8 = stab / slash / crush / magic defence bonus,
4 = ranged attack bonus, 10 = melee strength bonus, 12 = ranged strength bonus, 65 = magic strength bonus; all agree with the
infobox for ids 7562 and 7563); generated symbols (`Npcs`, `Seqs`, `Locs`). Raid-wide rules: `../cox.md` (not part of this
set). Seconds are converted at 0.6 s per tick. Tick timelines below list only what a source states; everything else is
`OPEN-MU<n>`. The cache calls the monster "dogodile / dohgadyle" in its symbol names; the wiki calls it Muttadile.

## Data

- NPC ids (the same ids serve Normal and Challenge Mode, the infobox lists the same ids for both): **7562**
  `RAIDS_DOGODILE_JUNIOR` (the small one, cache param 510 "Baby Muttadile", op2 "Attack", size **3**), **7561**
  `RAIDS_DOGODILE_SUBMERGED` (the large one while submerged, no ops, size **5**), **7563** `RAIDS_DOGODILE` (the large one
  after it emerges, param 510 "Big Muttadile", op2 "Attack", size **5**), **7564** `RAIDS_DOGODILE_MEAT_TREE` ("Meat tree",
  op1 "Cut", size 2). Category 1026. No combat level. Aggressive, poison and venom immune. **Attack speed 4** (infobox, both).
- Attack styles: small melee and ranged; large melee, ranged and magic, plus a **stomp**. Max hits (infobox, scaled for a maxed
  player): small Normal **28** melee / **30** ranged, CM **41** / **44**; large Normal **72** stomp, **48** melee, **45** ranged,
  **23** magic, CM **106** / **71** / **67** / **34**. The stomp "can hit 50% more than the large muttadile's melee attack",
  "acts as melee ... crush, I think" (Mod Ash, 30 Nov 2023): 48 x 1.5 = 72; 71 x 1.5 = 106.5 is shown as 106.
- Stats (infobox): small Normal att 150, str 150, def 138, magic 1, ranged 150; CM 225 / 225 / 207 / 1 / 225. Large Normal att 250,
  str 250, def 220, magic 250, ranged 250; CM 375 / 375 / 330 / 375 / 375. Bonuses: small attack +71, strength +48, magic -8, ranged
  attack +83, ranged strength +56; large attack +88, strength +55, magic -8, ranged attack +82, ranged strength +47. Defence
  bonuses: small stab -5, slash 72, crush 50, magic 60; large stab -5, slash 82, crush 60, magic 75; ranged defence 0 (light /
  standard / heavy). Cache 7562: params 4 / 5 / 6 / 7 / 8 / 10 / 12 / 65 = 83 / -5 / 72 / 50 / 60 / 48 / 56 / -8; 7563: 82 / -5 /
  82 / 60 / 75 / 55 / 47 / -8; 7561: 56 / -5 / 60 / 50 / 60 / 39 / 36 / -8 (the submerged form's values are not on the wiki;
  **OPEN-MU1**). Melee attack bonus is not in the cache params (wiki 71 / 88 unverified).
- HP (infobox, "scaledhp"): **250** each Normal, **375** each CM. These are maxed-combat values; the party-size and combat
  level rule is **OPEN-MU2** (no wiki formula). Elemental weakness **earth 40%** on both (25 Jun 2025). XP bonus small 12.5 / CM
  17.5, large 20 / CM 32.5. Stat regeneration **1 per 15 ticks** (Mod Ash, Discord 9 Jul 2026).
- Animations (cache seq names, `DOHGADYLE_*`): idle `DOHGADYLE_IDLE` 7417, defend 7418, walk 7419, bite 7420, bark 7421, "lazor"
  7422, emerge 7423, slam 7424, idle in water 7425, death 7426. Which of bite / bark / lazor is melee / ranged / magic and which
  of slam is the stomp is not stated (**OPEN-MU3**; names suggest bite = melee, bark = ranged, lazor = magic, slam = stomp, not
  verified). No spot anim / projectile symbols with a Muttadile or dogodile name were found (**OPEN-MU3**).
- Scenery: tendrils at the entrance `RAIDS_DOGODILE_TENDRILS_ENTRANCE` 29767; meat tree locs `RAIDS_MEAT_TREE_FULL` 30012 and
  `RAIDS_MEAT_TREE_EMPTY` 30013 (both Tree infobox ids); a bronze axe spawn is in the room (26 Jun 2024).
- Meat tree HP: **5 x the party's total Woodcutting level, minimum 100** (Mod Ash, 11 Oct 2018, Tree page). Strat says "a
  minimum of 100 health ... with a maximum of 500 health and 78% accuracy at 99 Woodcutting"; see conflict 4.

## Scaling

The infobox is for a player with maxed combat stats; NPC stats follow the player with the highest combat stats (7 Mar 2019); party
size is fixed at raid start and never lowered (Strat); Captain Rimor can disable level scaling when nobody is level 115+ (16
Jun 2021); the leader can raise the scale (29 Nov 2023). Challenge Mode: combat stats including HP x1.5 (CM): small HP 250 ->
375, att / str 150 -> 225, def 138 -> 207; large def 220 -> 330. The formula for party size and highest combat level is
**OPEN-MU2**.

## Fight

- **MU1 Start:** both muttadiles are present when the party enters: the small one walks on the ground, the large one lurks in
  the water. Noxious tendrils appear at the entrance and "deal damage to players who try to leave" (Strat). The CM page says
  standing at the southern edge of **Vasa's** chamber makes the muttadiles spawn in their own chamber (CM note; trigger
  **OPEN-MU4**). Whether the fight starts on entering or on first aggression: **OPEN-MU4**.
- **MU2 Small muttadile:** attacks with melee and ranged. It prefers ranged from a distance and melee when "within melee
  distance of a target" (Page). It constantly walks towards and away from players ("notorious for constantly walking",
  Page); the walk rule is **OPEN-MU5**.
- **MU3 Submerged large muttadile:** while the small one lives the large one stays submerged and cannot be attacked. It
  attacks players with "erratic Magic that covers the entire chamber": every player **within line of sight** of it has a
  **1/3** chance of being targeted (Mod Ash, 19 Aug 2020). In Challenge Mode it deals **20-80% of its maximum hit**
  (16 Jun 2021) "to avoid potential combos from both it and its young". The attack period and the damage in Normal are
  **OPEN-MU6**.
- **MU4 Emerging:** when the small muttadile dies the large one emerges and attacks (it can attack as it emerges). It uses
  melee, ranged and magic. Ranged from a distance, melee up close. When attacking from a distance it "will stay on one combat
  style for a minimum of three auto-attacks" and then may swap between Magic and Ranged; the three do not count Melee attacks
  (29 Nov 2023). "Not guaranteed to swap" (Strat). The swap probability is **OPEN-MU7**.
- **MU5 Stomp:** if anyone is within melee range the large muttadile may stomp **instead of** a melee attack, "dealing massive damage
  to everyone in melee range", max 50% above its melee max (Page; Mod Ash). Telegraph: it raises its front legs (Strat).
  Chance, area and what "within melee range" means for a size-5 NPC: **OPEN-MU8**.
- **MU6 Prayers:** protection prayers block melee fully and only reduce ranged and magic (Strat). The exact reductions: **OPEN-MU9**.
  Ranged hits "far higher and more accurately" than the large magic (Strat).
- **MU7 Meat tree healing:** when a muttadile is reduced **below 50%** HP (29 Nov 2023, up from 40%) it goes to the meat tree
  and eats; the Page says "~50% of their health, eating up to 50% of their health (100% total)" and that each muttadile eats at most
  **three** times and gives up if it cannot reach the tree for "~15 or so seconds" (25 ticks, **OPEN-MU10**); Strat says it
  restores "up to 40%". The amount per bite is **OPEN-MU10**. It can be damaged while frozen, even while trying to reach the
  tree (29 Nov 2023). A bound muttadile that cannot reach the tree for more than 15 s "will give up and no longer attempt to
  eat" (Strat). If the bind ends or the muttadile survives it "will only eat one time and will not do so again" (Strat).
- **MU8 Chopping the tree:** an axe is needed (a bronze axe spawns); the tree's HP is MU data. Each player's Woodcutting level sets
  the hit chance (**20% at level 1, 78% at level 99**, linear in between is not stated: **OPEN-MU11**) and the damage: "half
  the player's Woodcutting level with a -7/+3 margin" (Strat; Tree: level 80 deals 33-43, Strat: level 98 deals 42-52).
  The axe tier does not matter (Mod Ash, 12 Dec 2018). Cutting it down gives points (Main) and removes the healing (Strat). Chopping
  XP 45 (Tree page, info box).
- **MU9 Death:** small drops: Big bones, Overload (+)(4), Prayer enhance (+)(4), 1-3 stinkhorn mushrooms, Houndmaster's diary
  (unless read; `RAIDS_HOUNDMASTER_BOOK` 20897). Large drops: Big bones, Overload (+)(4), Prayer enhance (+)(4), Xeric's aid
  (+)(4), Revitalisation (+)(4), the diary. Tertiary long bone 1/400 and curved bone 1/5012.5 on each. A message names the player
  who dealt most damage; potions can be picked up by any player including ironmen (Strat).

## Points (stated)

- Damage dealt gives points (Main); the formula, cap and mini-boss "decay" multiplier are **OPEN-MU12**.
- The meat tree: "cutting this tree awards some points" (Main, **OPEN-MU12**).
- Potions dropped here award points when drunk, up to the potion cap (Main). Death rules: `../cox.md`.

## Per-mode differences

- **Normal:** HP 250 each; max hits as Data; submerged attack at full max hit.
- **Challenge Mode:** HP 375 each, stats x1.5; submerged large muttadile hits for 20-80% of its max; CM-Strat mentions a "normal
  safespot" for the large one and a second one when it walks south; the CM page also describes a tree skip with a Zamorak godsword
  (bind at about 55% HP). No CM-only mechanic other than the 20-80% rule is stated.

## Tick timeline (stated parts)

- Attack speed 4 for both (infobox). Large muttadile keeps one ranged / magic style for at least 3 auto-attacks.
- Tree: gives up after about 15 s (25 ticks) without reaching it; at most 3 meals each.
- Walk speeds, attack animations lengths and the emerge duration are not stated.

## Changes up to 2026-11-03 that matter

25 Jun 2025 earth weakness 40%; 26 Jun 2024 bronze axe spawn; 29 Nov 2023 tree damage formula made consistent, healing trigger
raised from 40% to 50%, damageable while frozen, max hits reduced, style swap only after three non-melee uses; 16 Jun 2021 CM
submerged attack 20-80%; 4 May 2017 permanent-invincibility fix. No entry after 25 Jun 2025.

## Open list

OPEN-MU1 the submerged form's stats (cache only); OPEN-MU2 HP / stat scaling by party size and highest combat level (no wiki
formula); OPEN-MU3 animation / projectile per style (names guessed from the cache only) and the missing spot anims; OPEN-MU4 fight
start trigger and the Vasa-edge spawn rule; OPEN-MU5 walk / retreat rule; OPEN-MU6 submerged attack period and Normal damage;
OPEN-MU7 style swap probability; OPEN-MU8 stomp chance, area and trigger; OPEN-MU9 protection prayer reductions; OPEN-MU10 amount
healed per meal and the give-up timer; OPEN-MU11 woodcutting hit chance between levels 1 and 99 and the tree HP cap (conflict
4); OPEN-MU12 combat and tree point values. **12 open items.** Working assumption: the meal size is a config flag (name left to the
implementer), default 50% per the Page, alternative 40% per Strat; trigger below 50% HP (29 Nov 2023).

## Conflicts between sources

1. Meal: Page "eating up to 50% of their health (100% total)" and "~50% trigger" vs Strat "restoring up to 40%" and "reaches 55%"
   (that is the player's cut-off for switching to the tree, not the trigger). 29 Nov 2023 says the trigger is below 50%.
2. Tree HP maximum: see conflict 4.
3. Not a conflict: the Page's max hits (magic 23, ranged 45) and Strat's "ranged far higher than magic" agree.
4. Meat tree HP: Strat "minimum of 100 ... maximum of 500 at 99 Woodcutting" vs Tree page "5 x the party's total Woodcutting level,
   minimum 100" (a trio of 99s gives 1,485). The Tree page (with a Mod Ash quote) is used.
5. Ids: the infobox gives one id set for Normal and CM; the cache has no CM-specific ids.
6. Stomp type: "typeless melee attack?" asked, Mod Ash: "acts as melee - crush, I think" (not certain).
