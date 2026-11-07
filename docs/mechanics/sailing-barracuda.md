# Sailing: Barracuda Trials

Milestone S8: NOT implemented (blocked, see "Why not"). Research: docs/plans/sailing.md section 5.3. `content/sailing` reads `Sailing_Bt_Trial_Core` (db 143)
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

## Why not (S8 handed back)

The rank table (R1) and rewards (R3) are known. The courses are not in a form the server can run:

1. **Course rules are on the Wiki as prose and map drawings only.** The three trial pages (The Tempor Tantrum, The Jubbly Jive, The Gwenith Glide) give,
   per rank, a route as a list of crate tiles plus a delivery tile (Tempor swordfish lap 1: 2994:2891, 2978:2866, 2981:2848, 2978:2828, 2990:2808,
   3001:2788, 3012:2768, 3057:2792, 3065:2811, 3077:2825, 3078:2863, 3082:2875, 3084:2896, 3053:2920; delivery 3035,2915; boats 3036,2926 and
   3037,2766), and `Module:Tile markers/The Tempor Tantrum (box locations with labels).json`. They do not state pick-up radius, which crates count for which
   lap, the order rules, Jubbly pillars or Gwenith crystal / portal sequences. Needed: those rules (OPEN-B2) as data.
2. **Trial start and state**: the master NPCs 15094-15108 (Talk-to, start, dismiss crew for Tempor), the 10 s leave-area timer, crew refusal, skiff and
   helm / mast checks, HUD interface 931 and varps 4985-5000 (time start 4987, objectives 4990-4992, performance 4993-4997, completed 4998-5000) and the
   personal-best varbits (143 `SAILING_BT_*`). All of that is client-visible state to be driven from a trial state machine whose inputs (boat position
   against crate tiles, delivery tile, boundary buoys 59236) need the boat's footprint in the root world each tick; `ActiveBoat.ref.rootCoord` gives the pivot only.
3. **Hazards** (rapids stacks, lightning clouds, rocks): speed changes are `BoatMotion`'s, i.e. S2 (main session).
4. Per-player instances (OPEN-BT1): the courses are open sea; two players in a trial would see each other's crates unless the crates are per-player locs
   (`PlayerRef` has no per-player loc API).

Proposal: a `Trial` data file (tile lists per rank, delivery tile, boundary rectangle) from the Wiki tile markers once the rules are confirmed, and per-player
locs or a world entity per trial for the crates.
