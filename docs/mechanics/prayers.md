# Prayers

Source: OSRS Wiki "Prayer" (PR, https://oldschool.runescape.wiki/w/Prayer, raw wikitext 2026-11-06) and the
Protect from Magic/Missiles/Melee, Smite, Redemption, Retribution, Protect Item pages.

| # | Rule | Source |
|---|---|---|
| P1 | "attacks are only affected by prayers that were active at the start of that game tick". Client input is read at the start of the tick (tick.md), so a prayer toggled by a click processed in tick T's input phase is the state "at the start of" T for every attack in T. | PR:538 |
| P2 | Drain: each tick, the sum of the drain effects of active prayers is added to a counter; while counter > resistance (60 + 2 × prayer bonus), lose one point and subtract the resistance. | PR:467-475 |
| P3 | "the game does not drain prayer for prayers on the tick they are activated". | PR:538 |
| P4 | Drain effects: Thick Skin 1, Burst of Strength 1, Clarity 1, Sharp Eye 1, Mystic Will 1, Rock Skin 6, Superhuman 6, Improved Reflexes 6, Hawk Eye 6, Mystic Lore 6, Steel Skin 12, Ultimate 12, Incredible Reflexes 12, Eagle Eye 12, Mystic Might 12, Rapid Restore 1, Rapid Heal 2, Protect Item 2, Protect from Magic/Missiles/Melee 12, Retribution 3, Redemption 6, Smite 18, Preserve 2, Chivalry 12, Deadeye 12, Mystic Vigour 12, Piety 24, Rigour 24, Augury 24. | PR:172-427 (table) |
| P5 | Protection prayers: 100% vs NPCs, 40% vs players; no accuracy effect in PvP. | PR:302-318, Protect_from_Missiles:18 |
| P6 | Smite: drains ⌊damage / 4⌋ prayer from the target; recoil/vengeance damage doesn't. | Smite:14-18 |
| P7 | Redemption: below 10% HP, drains all prayer and heals ⌊prayer level / 4⌋. | Redemption:14 |
| P8 | Retribution: on death, up to ⌊prayer level / 4⌋ damage in a 3x3 area; not on death by vengeance/recoil. | PR:342, Retribution:17-25 |
| P9 | Protect Item keeps one extra item on death. | Items_Kept_on_Death |

## SOURCE-CONFLICT

- Infobox "drain" values (PfM 20, Smite 30, …) differ from the drain-effect table; the table values reproduce the
  stated drain times (e.g. Protect from Melee 1 point / 3 s at bonus 0: 0.6 × 60 / 12 = 3 s). We use the table.
