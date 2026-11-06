# Duel Arena (classic, with staking)

Code:
- `content/pvp/.../DuelArena.kt`.
- Rule checks on the combat side: `content/combat/.../DuelChecks.kt`.
- Shared hooks: `plugin-api` `DuelRules`, and `SharedAttrs.DUEL_RULES`, `DUEL_OPPONENT` and `SAFE_DEATH_AT`.

Sources:
- OSRS Wiki "Duel Arena" (https://oldschool.runescape.wiki/w/Duel_Arena): the arena as it was until its removal on
  6 July 2022, including the 2018 and 2021 staking changes.
- "Emir's Arena" for the legacy duel screens.
- Rev 242 cache client scripts 6169, 6170, 9642 (options), 6193 (confirm) and 6189 / 6190 (opponent items).

The user chose the classic Duel Arena with staking over the Emir's Arena ranked minigame. Rev 242 keeps the rules and
confirm screens as the Emir's Arena "legacy duel" interfaces 755 / 756, but the stake screen is gone.

## Rules

- **DA1. Challenge.**
  - "players had to right-click their intended opponent and select the Challenge option ... they accepted by
    challenging that player in return". Challenge is player op 1 on the Duel Arena grounds.
  - A request stays open for 100 ticks.
  - Duels are safe and never skull.
- **DA2. Options.**
  - Rules are bits of varp `duel_options` (286), per client script 6169: 0 No Forfeit, 1 No Movement, 2 No Weapon
    Switch, 3 Show Inventories, 4 No Ranged, 5 No Melee, 6 No Magic, 7 No Drinks, 8 No Food, 9 No Prayer,
    12 Fun Weapons, 13 No Special Attacks.
  - Disabled worn slots are bits 14 + slot (varbit `duelwornoptions`).
  - Either player may toggle; any change clears both accepts. "No ranged, Melee, or Magic" is refused.
  - While the fight runs, the combat module enforces:
    - food and drinks;
    - prayer;
    - specials;
    - weapon switching;
    - disabled slots, including a 2-handed weapon when the shield slot is off;
    - fun weapons: every attack bonus negative, no bare hands;
    - hold spells under No Movement.
  - The Duel Arena plugin enforces the attack styles and "only your opponent".
- **DA3. Presets.**
  - The Save button's three ops: save as preset, load preset, load last duel.
  - Whip and Boxing load the client script 6193 values (229,499,867 and 229,630,939; free worlds would add bit 13).
  - Presets are saved with the account.
- **DA4. Stakes.**
  - "Players can no longer stake items; only Coins and Platinum tokens can be used."
  - "Each stake must now be within 10,000 Coins of each other in value."
  - "a stake limit of 10 million coins per player".
  - Stakes must be in the inventory. A platinum token counts as 1,000 coins: coins are taken first, then whole
    tokens, with the change paid back in coins.
- **DA5. Confirm.**
  - Script 6193 lists the rules, the "Before the duel starts" effects and the preset match.
  - With Show Inventories, scripts 6189 / 6190 show the opponent's backpack and worn items.
  - Both must accept.
- **DA6. The fight.**
  - Stakes go into escrow, and items in disabled slots are taken off (there must be room for them).
  - With No Drinks, boosted stats are restored. With No Prayer, prayers are stopped.
  - The players are put in one of the four arenas (cache map square 52,50), next to each other under No Movement, and
    frozen there.
  - "a 3-second countdown before the fighting actually began". Then the Duel Arena jingle plays and op 2 "Fight"
    attacks the opponent only.
- **DA7. Forfeit.**
  - "players who were losing ... could retreat through one of the trapdoors, ending the battle immediately. Anything
    staked was awarded to the other player". Not allowed under No Forfeit.
  - A dueller who logs out loses. Closing either screen before the fight declines for both.
- **DA8. Outcome.**
  - "players were teleported back to the building beside the arena and healed (poison and stat-reducing effects are
    negated as well) ... When you die you only lost the gold you staked." The death is safe (`SAFE_DEATH_AT`).
  - Winnings are both stakes less the tax: "If the total winnings were less than ten million coins, then 0.25% was
    deducted. If the winnings were over ten million coins, then 0.5% was deducted."
  - Wins and losses are saved.
  - The scoreboard shows "the last 50 battles on that server".
- **DA9. Escrow.** Stakes in escrow are saved with the account. A duel cut short by a crash refunds them at the next
  login.

## OPEN

- **OPEN-DA1.** Rev 242 has no stake screen. Stakes are typed into the chatbox count prompt after both accept the
  options.
- **OPEN-DA2.** Countdown timing: 2 ticks per step.
- **OPEN-DA3.** The scoreboard is shown as chat messages (the latest 10, plus your record). The cache scoreboard
  interface belongs to the ranked minigame.
- **OPEN-DA4.** Refusal and result message wording.
- **OPEN-DA5.** The classic trapdoors are gone from the rev 242 map. Each arena gets a Forfeit door (loc 29067) on its
  west and east walls.
- **OPEN-DA6.** Return tile: the hospital (3364,3267).
- Ranged ammo is not recovered from the ground yet ("Any Ranged weapons that fell to the ground were automatically
  returned"); this comes with ammo recovery.
