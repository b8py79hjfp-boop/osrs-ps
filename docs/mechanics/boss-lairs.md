# Wilderness boss lairs

Code: `content/pvp/.../Lairs.kt`. Lair geometry is taken from the cache: arenas are found by flood fill from each
lair's unique locs, and the gameval names `*_SINGLES_ENTRANCE01` mark the singles-plus entrances.

Sources:
- OSRS Wiki pages "Callisto's Den", "Hunter's End", "Vet'ion's Rest", "Skeletal Tomb", "Silk Chasm", "Web Chasm" and
  "Singles-plus combat".
- Updates "Wilderness Boss Rework" and "Wilderness Boss Rework Tweaks & More":
  https://oldschool.runescape.wiki/w/Update:Wilderness_Boss_Rework

| Lair | Entrance loc | Boss | Combat | Arena (x × z) | Arrival | Boss spawn |
|---|---|---|---|---|---|---|
| Callisto's Den | 47140 | Callisto 6609 | multi | 3351–3367 × 10317–10336 | 3359,10335 | 3357,10318 |
| Hunter's End | 47141 | Artio 11992 | singles-plus | 1751–1767 × 11533–11552 | 1759,11551 | 1758,11534 |
| Vet'ion's Rest | 46995 | Vet'ion 6611 | multi | 3286–3304 × 10190–10214 | 3295,10213 | 3294,10193 |
| Skeletal Tomb | 46996 | Calvar'ion 11993 | singles-plus | 1878–1896 × 11534–11558 | 1887,11557 | 1886,11537 |
| Silk Chasm | 47077 | Venenatis 6610 | multi | 3410–3436 × 10190–10217 | 3423,10216 | 3421,10192 |
| Web Chasm | 47078 | Spindel 11998 | singles-plus | 1618–1644 × 11534–11561 | 1631,11560 | 1629,11536 |

## Rules

- **BL1. Ops.** The entrances offer Enter, Peek and Check-Fee.
- **BL2. Fee.** "a fee of 50,000 coins". The fee "does not need to be brought along in the inventory, and can be
  deducted from the bank". It is paid once and kept in varbit `wbr_entrance_fee_paid`.
- **BL3. Discount.** "each kill in which the player deals the most damage ... reduces the fee by 10,000". Up to 5 kills
  count, and the count is kept in varbit `wbr_entrance_discount`. `Bosses.creditLairKill` credits the top damager when
  a lair boss dies, and paying the fee resets the count.
- **BL4. Fee loss.** The fee is lost when the player is killed by another player without having left the Wilderness
  since visiting a lair or the Escape Caves. The fee "is dropped as loot for the killer".
- **BL5. Teleport delay.** "Upon entering the lair, players will be unable to teleport for 3 ticks." This also applies
  on leaving. It is stored in `SharedAttrs.TELEPORT_DELAY_UNTIL`, which `Teleports.denial` checks.
- **BL6. Leaving.**
  - A multi lair exits to one of three Escape Caves points at random: (3338,10285), (3380,10285) or (3359,10247).
  - "any player who exits within 10 seconds of the previous player will arrive at the same point". 10 seconds is 16
    ticks. The last exit is stored per world.
  - A singles-plus lair exits to the surface, beside its entrance.
- **BL7. Wilderness level.** A lair has the Wilderness level of its entrance. It is multi or singles-plus as in the table.
- **BL8. Escape Caves entrances.** The Escape Caves' own surface entrances (loc 47175, ops Enter and Check-Fee) use the
  same fee ("shared across ... the Escape Caves").
- **BL9. Logging out.** "If you log out inside a boss lair then you'll log back in outside the Escape Caves."
- **Escape Caves exits.** Locs 47147–47149 lead to the surface tile above them (the 6,400-tile underground offset).

## OPEN

- **OPEN-BL2.** The Peek wording is not published.
- **OPEN-BL3.** Where singles-plus lairs and the Escape Caves exits put the player. We use the tile beside the entrance
  and the tile above the exit.
- **OPEN-BL4.** The message for a refused teleport during the 3-tick delay.
- **OPEN-BL5.** Where loc 47175 leads. We use a random Escape Caves point.
