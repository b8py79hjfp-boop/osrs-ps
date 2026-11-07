# Special attacks: research notes

Research input for the special-attack plugin. It does not define mechanics yet: before implementation, each rule
still needs its own `docs/mechanics/<topic>.md`. The data is in `special-attacks.tsv`, one row per spec-capable obj.

## Coverage

| | |
|---|---|
| TSV rows (objs) | **288**: every key of cache enum 906 |
| Distinct special attacks | 95 (by name; some names such as Smash, Spear Wall, Hamstring split by variant) |
| Rows by style | melee 159, ranged 48, magic 22 (incl. Voidwaker, atlatl, shields), instant / non-combat 59 |
| Rows with `instant` ≠ `no` | 68 (skilling boosts, Rampage, Sanctuary, Power of Death, Quick Smash, Momentum Throw, Rondache, …) |
| Rows with no `seq_symbol` | 34 (Rod of Ivandis ×10, flails ×3, hasta ×5, felling axes ×4, Blue Moon spear ×2, dual macuahuitl ×2, purging staff ×2, noxious halberd ×2, Dawnbringer, Echo crystal dagger, sunlight spear, fang of the hound) |
| Rows with no graphic at all | 48. Rock Knocker (×15) has none by design (RS Mod plays no spotanim). The others are unidentified: Retainer ×13, Vesta's longsword ×4, dragon crossbow ×3, Dinh's ×2, accursed sceptre ×2, purging staff ×2, DFW, the dogsword ×2, tecpatl, fang of the hound, sunlight spear, Echo dagger |
| Rows flagged `OPEN-SA-ANIM` (anim/graphic inferred, not name-matched) | 44 |
| Rows with a `SOURCE CONFLICT` | 19 |

Every wiki "Special attacks" entry is present in this cache (including Leagues and Deadman items). Two items are in the
cache but not on the wiki list: Dragon candle dagger (*Celebrate*) and the variant objs (LMS `BR_*`, BH `(cr)`/`(bh)`,
Deadman, ornament kits). The task brief gives the **rock thrownhammer** (`SLAYER_ROCK_THROWNHAMMER`) as an instant spec,
but it is not in enum 906 and not on the wiki list, so it has no special attack and gets no row.

## Sources and evidence legend (`notes` column)

- **C**: cache 2757. **Enum 906** (obj → energy ×10, 288 keys) is the authoritative list of objs with a spec bar;
  `energy_percent` comes from it. **Enum 1739** (obj → `"Name: tooltip"`, 267 keys) gives the special name and tooltip,
  quoted in every row. The 21 objs with no tooltip are the 3 dragonfire/wyvern shields and 18 Trailblazer tools.
  Dumped with `:tools:cache-dump:run --args="config enum 906|1739"`.
- **W**: OSRS Wiki `Special attacks` plus 146 weapon pages, fetched 2026-11-07 through the parse API. Raw copies are in
  the session scratchpad under `specs/`. Mod Ash, Mod Rice and news citations are named where the wiki gives them.
- **R**: RS Mod `content/other/special-attacks`. It covers only 4 maps: the skilling boosts (StatBoostSpecialAttacks),
  Dragon longsword and Dark bow. Its gameval names map 1:1 to ours, upper-cased.
- **D**: wiki DPS calculator source (`osrs-dps-calc`, GPL-3). Used for numeric factors only; no code was copied.
- How anims and graphics were matched:
  - *name-match*: the gameval name identifies the weapon or special.
  - *RS Mod*: taken directly from RS Mod.
  - *inferred (OPEN-SA-ANIM)*: a plausible choice, for example the weapon's normal attack seq.
  - Every chosen player seq was checked against the spotanim table: none of them is a spotanim's model seq. That check
    removed `SPECIALATTACK_UNLEASH`, `TOXIC_BLOWPIPE_SPECIAL_UPDATED`, `SLAYER_WHIP_SP_ATTACK` and
    `DTTD_DAGGER_SP_ATTACK` as player anims.
- Every symbol in the TSV was grep-verified in `Objs*.java`, `Seqs.java` and `SpotAnims.java`.

## Column conventions

- `accuracy_multiplier` and `damage_multiplier` multiply the max attack roll and the max hit. `1.0` means no modifier.
  Godswords apply `floor(max × 1.1)` first, then their own factor (AGS ×1.25, BGS ×1.1).
- `defence_roll_against` gives the target's defence style when the spec forces one. `selected style` means it uses the
  normal style. Specs nearly always roll *accuracy* with the player's selected stance.
- Tick figures follow the wiki where it gives ticks. Tooltip seconds are often rounded (Sever "5 s" = 8 ticks; ZGS
  "20 s" = 32 ticks).

## General rules

- **Energy.** Stored as varp `SA_ENERGY` (300) on a scale of 0..1000. The bar toggle is varp `SA_ATTACK` (301), and
  `SA_ENERGY_STORED` (2926) also exists.
  - Regen is +100 (10%) every 50 ticks (30 s), so a full bar takes 500 ticks.
  - The timer resets on logout. There is no regen while logged out, and death, the Clan Wars exit portal and the Ferox
    pool do not restore energy.
  - Energy belongs to the player, not the weapon.
  - Source: W "Special attacks#Energy".
- **Lightbearer** (`LIGHTBEARER`) doubles regen to +10% every 25 ticks (15 s), so a full bar takes 150 s.
  OPEN-SA-24: whether equipping Lightbearer mid-cycle shortens the running 50-tick timer is not stated.
  The most plausible reading is that the period changes on the next cycle.
- **Other restores.**
  - 100% energy: Energy Transfer (the caster pays 100% energy and 10 HP), Elidinis statuette, POH restoration pool or
    better, and entering CoX, ToB or NMZ.
  - Surge potion restores 25%.
  - Venator heart at an aranei shrine restores 50%.
  - Death Charge restores 15% per kill (with Rite of vile transference, up to 2 kills per cast).
- **Activation.** The player clicks the combat-tab spec bar or the minimap spec orb, which toggles `SA_ATTACK`.
  - Combat specs fire on the next attack.
  - Instant specs fire on click without waiting for a target or the attack delay: skilling boosts, Rampage,
    Sanctuary, Power of Death, Tumeken's Light, Entice, Virulence, Quick Smash (same tick, stackable) and Momentum
    Throw (next tick).
  - Not enough energy: the spec does not fire. OPEN-SA-25: the exact message and whether the bar unticks need checking
    in the client.
  - Shields (DFS, DFW, AWS) have energy 0 in enum 906. They use a 2-minute cooldown and charges.
  - Soulreaper axe, Sunlight spear, Lithic sceptre and Rondache use their own stacks or charges. Their enum values
    (1, 7, 5, 10) are minimum-stack markers, not energy (inferred from the matching values).
- **Hit logic.** As normal combat: an accuracy roll, then a damage roll.
  - Heal and restore effects (SGS, blowpipe, Eldritch staff) use the *rolled* damage, not the damage actually dealt.
  - Drain effects that need "damage dealt" (DWH) require a hitsplat above 0. Other specs trigger on a successful roll
    even when it rolls 0 (Mod Ash).
- **PvP-only differences.**
  - Whip run-energy siphon.
  - D scim prayer disable (8 ticks).
  - Morrigan's hamstring and bleed.
  - Ursine run block and −20 Agility.
  - AnGS heal cap 15 (25 vs NPCs).
  - Dinh's hits the primary target once (twice vs NPCs).
  - Halberd and d2h hit at most 3 players.
  - Saradomin sword's magic hit always splashes vs Protect from Magic.
  - Tonalztics: the first hit's drain does not help the second roll.
  - Deadman thunder khopesh bolt barrage.
  - Teleport block after an offensive spec in PvP: "a few seconds" (W). **OPEN-SA-26**: the duration is not sourced.
  - PvP protection prayers reduce melee by 40%. The hasta and dragon shortsword specs ignore it, and the ancient mace
    hits through it.

## OPEN items

| id | item |
|---|---|
| SA-1 | Rampage drains 10% of the current or the base level (plausible: current). |
| SA-2 | `*_SPECIAL_EXTRA` staff spotanims: when they play (plausible: when a reduced hit lands). |
| SA-3 | Only one keris spec seq/spot pair exists. Which keris (corruption or sun) uses it? |
| SA-4 | Virulence is assumed to be instant activation (no target). |
| SA-5 | Soulflame horn: which of `SOULFLAME_HORN_BLOW_01/02/03` is the spec. Ally cap is 3 (page) or 4 (list). |
| SA-6 | Abyssal tentacle defence roll is not stated (plausible: slash/selected). |
| SA-7 | BH dragon longsword "increased speed" is not quantified. |
| SA-8 | Dragon 2h: 2h-update seq/spot vs classic `DRAGON_TWO_HANDED_SWORD(_BLAST)`. |
| SA-9 | BH barrelchest anchor may use `BRAIN_PLAYER_ANCHOR_SPECIAL_ATTACK_LONG`. |
| SA-10 | Saradomin's blessed sword: always the stab/slash bonus, or the selected style (wiki `{{fact}}`). |
| SA-11 | Dragon claws damage table: the wiki warns of possible minor errors. |
| SA-12 | Granite hammer +5: within max (wiki list) or on top of max (DPS calc). |
| SA-13 | Statius's warhammer drain is 30% (original tooltip), 70% (wiki list) or 75% (BH tooltip and page). |
| SA-14 | Ursine `FX_URSINE02_SPECIAL` as the target graphic. |
| SA-15 | Voidwaker: `HUMAN_SPECIAL_VOIDWAKER` vs `HUMAN_SPECIAL02_VOIDWAKER`. |
| SA-16 | Does the dark bow's 48-per-arrow cap also apply to Descent of Darkness (DPS calc: yes)? |
| SA-17 | Ballista: `BALLISTA02_*` seqs (heavy, or ornament?) and the PvP/PvN seq split. |
| SA-18 | `BALLISTA_SPECIAL`: projectile or launch graphic. |
| SA-19 | The original Deadman Morrigan's items may use the old `MORRIGANS_TAXE_LAUNCH/TRAVEL` and `MORRIGANS_JAVELIN_LAUNCH/TRAVEL` graphics. |
| SA-20 | Morrigan's javelin bleed cadence: 10 dmg every 3 ticks (list) or every tick (page). |
| SA-21 | DFW and AWS projectile/impact graphics. AWS freeze chance and duration. |
| SA-22 | Felling axes' Lumber Up seq (`HUMAN_2H_AXE_SPECIAL` vs `FORESTRY_2H_AXE_SPECIAL`). |
| SA-23 | Halberd `DARKRED` and `BLUE` facing spot sets: which objs use them. |
| SA-24 | Lightbearer equip/unequip mid-timer behaviour. |
| SA-25 | Insufficient-energy message and bar behaviour. |
| SA-26 | PvP teleport-block duration after a spec. |
| SA-ANIM | 44 rows with an inferred anim or graphic. Verify with RSProx captures before shipping. |

**SOURCE CONFLICTs** (resolution used in the TSV):

| Item | Conflict | Used |
|---|---|---|
| ZGS freeze | 20 s (tooltip and list) vs 32 ticks (page) | 32 ticks |
| Saradomin sword defence | Magic (list) vs slash (Mod Ash, DPS calc) | slash |
| Accursed sceptre damage | none (list) vs ×1.5 (page, DPS calc) | ×1.5 |
| Rune claws | none (list) vs +10% dmg (page) vs +10% Att/Str (tooltip) | 1.1 / 1.1 |
| Dragon longsword accuracy | RS Mod passes ×1.25; wiki, tooltip and DPS calc give none | 1.0 |
| Rosewood blowpipe | tooltip −20% acc / +10% dmg is a known bug | none |
| Sunspear | 70% of max (W/C) vs floor(70%)+1 (DPS calc testing) | — |
| Vesta's spear (Deadman) | wiki vs tooltip on damage reduction and immunity | — |
| Morrigan's thrownaxe | original 25% (enum) vs 50% (wiki = BH rework) | per variant |
| Morrigan's javelin / Statius's warhammer | original vs BH numbers | per variant |
| Thunder khopesh bolt | 100% (page) vs 225% (list) | — |
| Lithic sceptre typeless | 75% (page and tooltip) vs 65% (list) | 75% |
| Soulflame horn ally count | see SA-5 | — |
