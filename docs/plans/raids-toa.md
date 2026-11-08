# Tombs of Amascut: implementation plan (R6)

Sources: `docs/mechanics/raids/toa.md` (TA1-TA72, TI1-TI46), `toa/lobby-rewards.md` (TR1-TR29, TL1-TL18), the room docs
`toa/puzzles.md`, `toa/baba.md`, `toa/zebak.md`, `toa/akkha.md`, `toa/kephri.md`, `toa/wardens.md`, the client side
`toa/client-contract.md` and `toa/runelite-timings.md` (RT1-RT214). Brief sections 1.4, 3 and 4. Each doc had a verifier
pass before code depends on it; open conflicts are settled in the "Decisions" section below and in DECISIONS.md.

## Structure on this server

- **Module:** `content/raids-toa` (plugin-api, symbols, raids-core only). Boss and puzzle-foe fight scripts live in
  `content/combat` (`Toa*.kt`), as for ToB and CoX; room rules (puzzles, Nexus, supplies) live in raids-toa.
- **Instance:** one instance per run, built from the real squares as **unrotated whole-square copies** (RuneLite reads
  region ids and fixed scene coordinates, RT section 10): Nexus 14160 (55, 80), the tomb 14672 (57, 80), the Wardens
  15184 (59, 80) and 15696 (61, 80), puzzles 14162 (55, 82) Scabaras, 14674 (57, 82) Het, 15186 (59, 82) Apmeken,
  15698 (61, 82) Crondis, bosses 14164 (55, 84) Kephri, 14676 (57, 84) Akkha, 15188 (59, 84) Ba-Ba, 15700 (61, 84) Zebak.
  The 7 x 5 square box fits the 8 x 8 instance limit; unused squares are left empty. The pyramid entrance 13354 and the
  lobby 13454 are the real shared map (like Ver Sinhaza for ToB).
- **Rooms in raids-core:** `nexus` (CHOICE), `apmeken`, `baba`, `crondis`, `zebak`, `het`, `akkha`, `scabaras`,
  `kephri`, `wardens` (ENCOUNTER), `tomb` (REWARD). `autoAdvance = false`: `ToaFlow` drives the graph — a Nexus door
  takes the party to that path's puzzle (any order, a finished path's door refuses), puzzle done -> its boss, boss done
  -> back to the Nexus; four bosses done -> the Wardens door; Wardens dead -> the tomb.
- **Party:** the Grouping Obelisk 46068 (772 / 774 / 773): apply / accept / reject, kick, move up / down, promote, the
  leader's invocation toggles (776, 774:1 subs) and the five presets (varps 3680-3694). Invocations live in
  `party.settings` as the three script-6729 words; raid level = the sum of struct param 1162 over the set (TX-D1).
- **Deaths:** `WaitUntilRoomEnds` with a new raids-core `WipePolicy.ByRun { run -> ... }`: with an attempts invocation
  a wipe spends an attempt and restarts the room (`RetryRoom`); out of attempts (or with none left to spend) it is
  `FailRaid` with the reclaim store (TA41-TA45). Logout in an encounter counts as a death (`DEATH_IN_ENCOUNTER`), the
  escape crystal turns a wipe into an eject (TA48).
- **Scaling:** party HP (90% for the 2nd and 3rd member, 60% each after), raid level (+2% per 5 levels to HP, defence and
  accuracy; damage per TA scaling rules), path level (+8% first, +5% each later, max 6) applied through
  `SharedAttrs.RAID_SCALER` like CoX.
- **Points:** raids-core ledger with the per-NPC multipliers, room caps (20,000) and the personal cap (64,000), the
  5,000 start, the MVP bonus and the death penalty (20%, min 1,000). Room points join the total at room completion
  (Jagex's post; RT C4 conflict noted).
- **Client:** HUD 481 kept open in the raid (RuneLite's in-raid check, 481:40), party orbs `P0..P7` (5-bit HP buckets),
  path levels 14376-14379, raid level 14380, current path 14381, raid timer script 6580, the mid-raid loot 777 / 778,
  chests 771, summary 482 with titles, scoreboard 775. Varp 3585 is shared with ToB: read-modify-write.
- **Rewards:** one unique per raid (lobby-rewards TR2-TR12), commons, pet and tertiaries, per-player sarcophagus chests
  in the tomb with the purple flame, unclaimed loot to the Rewards Niche (multiloc 46224 on varbit 14319).

## Milestones

1. Framework additions (`WipePolicy.ByRun`, attempts counter), the definition and instance, pyramid entrance, lobby,
   Grouping Obelisk party flow and invocations 776 / presets, raid level and modes, Nexus doors and the room graph,
   HUD 481 / party orbs / timer, smoke scenario that walks a level-0 raid with stub rooms.
2. Puzzle rooms: Apmeken, Crondis, Het, Scabaras (all five puzzles), with their foes in content/combat.
3. Bosses (main session, content/combat): Ba-Ba, Zebak, Akkha, Kephri, every attack, special and boss invocation.
4. The Wardens: obelisk phase, both Wardens with the core, phase 3 with slams, siphons, phantoms and enrage.
5. Points, scaling, path levels, Walk the Path, supplies (Helpful Spirit 777 / 778, deposit pot, every supply item),
   attempts and wipes, reclaim, escape crystal, time-limit invocations.
6. Tomb: sarcophagus chests 771 and loot model, Rewards Niche, records (KC by mode, messages, PB), summary 482 with
   titles, scoreboard 775, Osmumten, pet, capes chest, lobby NPCs.

## Decisions (from the doc review)

Recorded per milestone as the docs are settled; open conflicts (Het's Seal hit count, room-point timing, MVP rooms,
Penetration's defence drain, Kephri's shield-break count) are decided before the room that needs them, with the
alternative behind a named flag (`toa.*`).

## Status (2026-11-08)

- Research: done (seven docs from cache 2757, the wiki copies and seven RuneLite ToA plugins; three verifier passes, all
  findings applied).
- Milestone 1: done, with the party interfaces (list 772, details 774, invocations 776, lobby 773, presets; OPEN-TAUI1-4): definition and instance, lobby door,
  Nexus doors and the room graph, flame barriers, the Wardens door, the burial chamber, invocations and raid level,
  starting path levels and Walk the Path, attempts (`WipePolicy.ByRun`), HP / roll scaling, HUD 481; `ToaSmokeScenario`
  walks all four paths to the burial chamber.
- Milestone 2: done. All five puzzles (Scabaras's four tile/obelisk/addition/light puzzles, Het's mirrors, orbs and
  the Seal, Apmeken's sight and the four problems, Crondis's watering), with their foes (shaman, volatile and cursed
  baboons, thralls, crocodiles, Het orbs) in content/combat `ToaPuzzleFoes`. Puzzle signals reach raids-toa through
  `RaidSignal`.
- Milestone 3: done. Ba-Ba, Zebak, Akkha and Kephri as boss scripts (`ToaBaba`, `ToaZebak`, `ToaAkkha`, `ToaKephri`,
  with their adds) covering every attack, special and boss invocation in the room docs.
- Milestone 4: done. `ToaWardens`: the obelisk phase, Elidinis' and Tumeken's Wardens with the core, and phase 3 with
  slams, siphons, phantoms and enrage.
- Milestone 5: done. Points (`ToaPoints`), path levels and Walk the Path, the supplies (Helpful Spirit 777 / 778, every
  supply item, the supply bag, honey locust and smelling salts, Ambrosia, Nectar's attack delay and the restores). The
  deposit pot is served by the deposit box. Also attempts and wipes, reclaim 1037, the escape crystal and the
  time-limit invocations. Consumable and special-cost invocations go through `ConsumeCheck` and `SpecialCostQuery`.
- Milestone 6: done. Sarcophagus chests 771 with the wiki loot model (`ToaLoot`, test rows in `RaidRewardMathTest`),
  the Rewards Niche and records (KC by final level, messages, PB through `RecordsDef.modeOf`). Also the summary 482 with
  titles, scoreboard 775, Osmumten and the lobby NPCs.
- Follow-ups (2026-11-08), all built:
  - `ToaSmokeScenario` fights all five bosses through combat damage (every phase of the Wardens).
  - Puzzles:
    - Venom from Apmeken's pools.
    - The Sight's hints, private to the holder (private map anims).
    - Crondis' spears and clouds shown on the damage cycle.
    - Scabaras's Passage and Platform shortcuts.
    - PC5's per-action puzzle points behind `toa.puzzlePoints.flat=false`.
  - Tomb and lobby:
    - The lobby pickaxe cavity.
    - The Niche warning before entering.
    - The sarcophagus reveal (seq 9505, then the opened form, for the winner).
    - TA12's mid-raid penalty behind `toa.timeLimitPenaltyAtEndOnly=false`.
- Not built, by design:
  - Script 6745 is optional (OPEN-TC14).
  - The pet goes to the chest as an item: the server has no follower system for any pet.
  - The CA clue perk needs Combat Achievements, which brief section 13 leaves out.
  - Deposit box OPEN DB1-DB6 are in docs/mechanics/deposit-box.md.
