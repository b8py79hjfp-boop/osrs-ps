# Revenant Caves

Code: `content/pvp/.../RevenantCaves.kt`. The revenants themselves are covered in bosses.md (RV, RM).

Source: OSRS Wiki "Revenant Caves" (https://oldschool.runescape.wiki/w/Revenant_Caves).

- **RC1. Fee.**
  - "Before entering the caves, players must pay a fee of 100,000 coins. The fee can be taken from the bank and does
    not need to be brought along in the inventory."
  - It is paid once and saved with the account (`PlayerRef.saved`, key `rev_cave_fee`).
- **RC2. Entrances.** "three one-way entrances":
  - Cavern 31555, level 17, at (3073,3654).
  - Cavern 31556, level 40, at (3124,3831).
  - Crevice 40386, level 26, at (3067,3740). "players will be warned when attempting to enter from here".
- **RC3. Losing the fee.** "Upon dying to anything inside the caves (including monsters) or to another player anywhere
  in the Wilderness, the fee will be lost ... If the killer was another player, the 100,000 coins will be dropped as
  loot."
- **RC4. Exits.** One-way stairs "by the level 17 and level 40 locations": stairs 31558 at (3218,10058) and 43868 at
  (3244,10215).

## OPEN

- **OPEN-RC1.** Arrival tiles are not published. We use walkable floor in the south (3199,10072), middle (3238,10160)
  and north (3230,10199). Exits come out beside the level-17 and level-40 entrances.
- **OPEN-RC2.** The crevice warning wording.
- **OPEN-RC3.** The 2-tick teleport delay while in NPC combat in the caves. It is part of the teleport leftovers.
