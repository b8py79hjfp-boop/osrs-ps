# Sailing: Barracuda Trials

Skeleton for milestone S8. Research: docs/plans/sailing.md section 5.3. `content/sailing` reads `Sailing_Bt_Trial_Core` (db 143)
into `BarracudaTrial` / `BarracudaRank`.

Sources: cache 2757 db 143 (`dbrows 143`); OSRS Wiki https://oldschool.runescape.wiki/w/Barracuda_Trials,
/The_Tempor_Tantrum, /The_Jubbly_Jive, /The_Gwenith_Glide, /Rapids.

## Rules

| # | Rule | Source |
|---|---|---|
| R1 | `RANK_DATA` is a 4-tuple list per trial, each `(target ticks, laps, supplies required, bonus xp x10, trial xp x10)` for ranks Unranked, Swordfish, Shark, Marlin. Values read from the cache (XP in whole points): Tempor Tantrum 0/1/10/0/150, 180/1/14/1,000/385, 285/2/25/2,000/650, 450/3/36/3,000/1,250; Jubbly Jive 0/1/16/0/1,000, 206/1/20/5,000/1,700, 300/2/38/7,500/3,000, 535/3/56/10,000/6,200; Gwenith Glide 0/3/30/0/2,050, 200/3/30/25,000/3,050, 370/5/59/35,000/7,250, 615/8/96/50,000/16,050. The Wiki tables agree (Tempor 1:48 = 180 ticks, 2:51 = 285, 4:30 = 450). | cache db 143; Wiki trial pages |
| R2 | Trials: The Tempor Tantrum (Sailing 30, Rum-dashed Ralph, Storm Tempor), The Jubbly Jive (55, Gurtob, Backwater, needs inoculation station and mithril helm), The Gwenith Glide (72, Gwyna, Porth Gwenith, adamant keel, Regicide). Levels are not boostable; skiffs only; leaving the area for 10 s loses progress; a trial finishes when you sail away (17 Dec 2025). | Wiki Barracuda Trials |
| R3 | Ranks unlock in order. Rewards (cache `REWARD_SWORDFISH/SHARK/MARLIN` objs): Swordfish a key (stormy / fetid / serrated), Shark a barrel stand / captured wind mote / heart of Ithell, Marlin a fabric roll (flag) and a 1/240 Barracuda paint. | cache db 143; Wiki Barracuda Trials |
| R4 | Each crate or supply pickup may give a sawmill coupon. Lost supplies give fixed XP (Tempor 15, Gwenith 35). Rapids stacks decay after 15 ticks. | Wiki trial pages, Rapids |
| R5 | HUD: interface 931 with varps `SAILING_BT_*` 4985-5000 (`_TIME_START` 4987, objectives 4990-4992, performance 4993-4997, trials completed 4998-5000), personal bests in varbits `SAILING_BT_*_PB`; selection 932; statistics 933. | cache symbols; plan 5.3 |

## OPEN

- **OPEN-B2:** course rules beyond the Wiki: start tiles, boundary rectangles, lap logic, portal destinations, pillar sequences
  (trial level, start coordinate, boundary and paint odds are stripped from db 143). Source: Wiki tile-marker pages, then RuneLite.
- **OPEN-BT1:** whether per-player instances are used (the courses are open sea in the cache map).
- **OPEN-BT2:** crew rule (Tempor requires dismissing crew per the Wiki; `CREWMATES_ALLOWED` is stripped for the others).
