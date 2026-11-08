# TFA timings encoded in RuneLite plugins

Read 2026-11-08. Research notes only: nothing here is implemented yet. Each value is what a plugin's code or comments
state about the live game. Plugins are written by players from in-game observation (several say "set in game" or
"fitted to N logged fights"), so they are evidence, not Jagex data. Where a value conflicts with the wiki/Strat value
in the boss docs, both are kept and the conflict is noted.

## Sources

| Key | Repository @ commit | What it is |
|---|---|---|
| LU | `luuj/fracturedarchive` @ `ea573b049c9579f275c8d3fe75e638bb61a238ad` (2026-11-07) | Boss-helper plugin with per-boss tick counters. **Not on the Plugin Hub** (sideloaded; package `net.runelite.client.plugins.fracturedarchive`, one commit "Add files via upload", no licence file). Values are cited as facts; no code is copied. Paths are relative to the repo root. |
| LD | `LlemonDuck/the-fractured-archive` @ `28a5c062059e8e06378288ea5a6ad77b54fe3e89` | Plugin Hub `the-fractured-archive` (points, loot, HP orbs). |
| CA | `Nubles/OSRS-Codex-Archive-Splits` @ `370e6e4ca8ba6a7a6de027958812dfed6e61c328` | Plugin Hub `codex-archive-splits` (`disabled=true`): splits only. |
| DT | `P3ARGIT/tfa-dry-tracker` @ `086b25e174ce0330fd23ed6a84701373e5bd29ce` | Plugin Hub `tfa-dry-tracker`: loot only, no fight timings. |
| BL | `blert-io/plugin` @ `3e8bf4e13fc0145fe6b90ba2eae95d2b5f1c2c16` | Plugin Hub `blert`: room splits. |
| AT | `ngraves95/attacktimer` @ `863789f57dad2de984540cf8f513248f7baed393` | Plugin Hub `attacktimer`: TFA player weapons only. |
| CH | `crejaud/runelite-chanced` @ `0a1efda5be5c3f43f342f7d7951f8eb3a5d2ac3f` | Not on the Hub. `attack_styles.txt` is generated from gameval **names** (low value). |
| RL | `runelite/runelite` @ `27fcc1da7757bbe25c49856b51d3a6fdf61db7ee` (2026-11-07) | gameval names (`runelite-api/.../gameval/*.java`). No core TFA plugin exists. |
| TR | `Julian-Dasilva/ArchiveOcellusTrainer` @ `1727143bb794cfed370b5cb0a29022790320d3f7` | **Not a RuneLite plugin**: a web trainer whose P4 timings cite RuneLite fight logs and VODs from 2026-10-23 (README: "Timings are unverified"). Only P4 values are kept. |

Clones are in `/tmp/claude-0/ref/plugins/<name>` (and `/tmp/claude-0/ref/runelite`, `/tmp/claude-0/ref/plugin-hub` @
`97f07e26`).

**Counter convention (LU):** most counters are set to `N + 1` on the animation and drop by 1 each `GameTick`, "the same as
Boss Ticks", so the first number shown is N. The table gives N, the real interval. Ticks given as "after the
animation" count from the tick the client sees the animation.

Confidence: **high** = a constant the author says was confirmed, set in game, or fitted to logs; **medium** = a
constant with an approximate or partial comment ("about", "1 or 2 ticks"); **low** = inferred from names, or marked
"not confirmed yet" by the author.

## Archive Guardian

| Value | Meaning | Source | Conf. |
|---|---|---|---|
| 5 t | Ranged (seq 14751) and melee (14752) attack cycle | LU `archiveguardian/ArchiveGuardian.java:47-49` | high |
| seq 14754 | The special plays `NPC_ARCHIVE_GUARDIAN_ENRAGED_PHASE01`, not `SPECIAL_ATTACK01` (14753); both treated as the special | LU `ArchiveGuardian.java:43-53` | high |
| 10 t | Special anim → **laser/beam** hits | LU `ArchiveGuardian.java:78-79` | high |
| 5 t | Special anim → **mushroom attack** hits (the beam and the mushroom attack share one animation) | LU `ArchiveGuardian.java:72-80` | high |
| ≤ 2 t | Mushrooms appear within 2 ticks of the special anim when it is the mushroom attack | LU `ArchiveGuardian.java:76`, `:227-241` | high |
| locs 62578 / 62579 / 62580 | Mushroom states SPAWN → ACTIVE → DESPAWN; dangerous from SPAWN until DESPAWN starts (1 tile around it) | LU `ArchiveGuardian.java:39-40`, `:260-273`; config `FracturedArchiveConfig.java:160-161` | high |
| 35 t | Grounded duration, **first** down, counted from `IDLE_TO_GROUND01` (seq 14747) | LU `ArchiveGuardian.java:82-84`, `:207-217` | high ("set in game") |
| 40 t | Grounded duration on **every later** down | same | high |
| 5 t | The woken Guardian stays the inert NPC 16657 for 5 ticks after its start anim (seq 14755, played only on the start tick) | BL `.../tfa/bosses/guardian/GuardianDataTracker.java:39-50` | high |
| 16659 / 16660 | Fighting forms (shielded / unshielded); 16658 = dead husk | CA `core/Encounter.java:26-47`; RL `NpcID.java:72030-72045` | high |

Values: 10.

## Ezekiel

| Value | Meaning | Source | Conf. |
|---|---|---|---|
| 5 t | Attack cycle after melee (14685), magic (14686) and special (14687) | LU `ezekiel/Ezekiel.java:72-73`, `:322-342` | high |
| 2 basics → star | Rhythm: two basic attacks, then the star special (14687 = the lightning; floor cracks spotanims 4169/4170/4171) | LU `Ezekiel.java:53-58`; `ezekiel/EzekielOverlay.java:37-40` | high |
| ≥ 9 t pause | Between stars: two melees, a pause of 9+ ticks, two more melees, then the star (the pause resets the 1-2 count) | LU `Ezekiel.java:61-65`, `:335-336` | medium |
| 15 t | Sword NPC (16653) spawn → sword launches; 16654 (`_NOOP`) is a spent sword | LU `Ezekiel.java:39-43`, `:408-412` | high |
| +1 t each | A set of three swords on one side goes off one tick apart (15, 16, 17); a set = same direction, spawns ≤ 2 ticks apart | LU `EzekielOverlay.java:78-79`, `:150-180` | high |
| order | Flying east/west: northern sword first, then middle, then southern. Flying north/south: western first | LU `EzekielOverlay.java:150-155`, `:203-215` | high |
| straight across | Swords sit in the wall and fly cardinal-only straight across to the opposite side of the room (lane up to 30 tiles, size of the sword NPC) | LU `Ezekiel.java:417-452`, `:516-519`; `EzekielOverlay.java:30` | high |
| 5 t | Lane shown only for a sword's last 5 ticks (plugin choice, not a game rule) | LU `EzekielOverlay.java:33-34` | n/a |
| ≥ 4 swords | Four or more live lanes can share a crossing tile ("X-section") | LU `EzekielOverlay.java:270-333` | medium |
| ~4, 5, 6 t | Finale: each round queues its three strikes at once, landing about 4, 5 and 6 ticks ahead, in a random order, on the 3×3 in front of him (impact spotanims 4196/4197/4198) | LU `Ezekiel.java:184-188`, `:261-281` | medium |
| safe tiles | Each finale strike leaves either one side column safe (stand in that back corner) or the whole back row (stand in its middle); "front" = his facing rounded to N/E/S/W | LU `Ezekiel.java:195-258`; `EzekielOverlay.java:224-231` | high |
| seq 14694/14695/14696/14697 | Finale start / vfx / loop / finale death; no attack counter during the finale | LU `Ezekiel.java:344-362` | high |

Values: 12.

## Bork and the Smorks

| Value | Meaning | Source | Conf. |
|---|---|---|---|
| 16 t | Cannon (heavy cannonball) stun on Bork | LU `bork/Bork.java:260-261` | high |
| seq 14538 / 14543 / 14544 / 14545 | Stun anims (`BORK_STUNNED`, `STUN01`, `12_CYCLE_STUN01`, `14_CYCLE_STUN01`); several can play in a row | LU `Bork.java:51-56`, `:424-431` | high |
| 4 stomps, 3 t apart | After **every** stun Bork stomps 4 times, starting about 17 ticks after the stun; he does not move between stun and stomp | LU `Bork.java:75-84`, `:114-118` | high |
| T, T+2, T+8 | Wind-up seq 14549 (or 14556 from idle) at T with overhead "Bork very angry!!"; 14550 at T+2 (one tick before stomp 1), 14551 at T+8 (one tick before stomp 3) | LU `Bork.java:411-422`, `:58-60` | high |
| T+3, 6, 9, 12 / T+6, 9, 12, 15 | Hit tiles flash 3, 6, 9, 12 ticks after the wind-up; the damage lands one stomp later: 6, 9, 12, 15 | LU `Bork.java:95-110` | high |
| spotanim 4395 | Stomp hit-tile graphic (`TFA_WAVE_DUST_SHORT`) | LU `Bork.java:637-638` | high |
| shadow rule | Stomp hits the whole room except the shadow behind a Smork seen from Bork's centre (his SW tile + 2,2): a tile in the ring around a Smork is safe if ≥ 13 of 100 sample points are hidden behind the Smork's middle box (SW+0.5 … SW+2.5). Fitted to 53 logged stomps / 316 safe tiles | LU `Bork.java:148-224` | high |
| 5 or 6 shots | A Smork's fire bomb (napalm projectile 4045, fire spotanim 4046) comes after 5 or 6 normal shots | LU `bork/BorkOverlay.java:20-21`; `Bork.java:445-452` | high |
| ~20 t | Big cannonball (projectile 4440 `CANNONBALL_TRAVEL_BIG`, from a Smork, at the ground) about 20 ticks after each fire bomb | LU `Bork.java:268-269` | medium |
| target lock | A Smork's fire-bomb target is whoever it is attacking when it picks, kept until it throws the bomb | LU `Bork.java:297-301`, `:585-593` | high |
| proj 4057 / 4056 | Smork magic / ranged volley projectiles; a volley hits every player at once and has no source actor | LU `Bork.java:263-266`, `:544-556` | high |
| seq 14554 / 14555 | Smork normal shot / big-cannon (bomb) anim; the bomb resets that Smork's shot count | LU `Bork.java:48-49`, `:445-452` | high |
| seq 14542, 14546, 14547, 14550, 14557 | Bork attack anims (gong melee, enraged gong in/attack, stomp, idle→gong); any of them ends a stun early | LU `Bork.java:61-66` | high |
| "Bork smash …" | Re-target call-out arrives as overhead or NPC chat | LU `Bork.java:455-510` | high |

Values: 14. Bork's own attack interval is not encoded.

## Moia

| Value | Meaning | Source | Conf. |
|---|---|---|---|
| proj 4213 → 6 t | Her slow magic projectile (`MOIA_DAGGER_MAGIC`) lands 6 ticks after it is thrown | LU `necromancer/Necromancer.java:66-67` | high |
| proj 4214 | Fast ranged projectile (`MOIA_DAGGER`) | LU `Necromancer.java:68` | high |
| magic, magic, cough | Before the enrage her specials repeat magic, magic, cough; seq 14788 (teleport) = magic special, seq 14786 (dagger) = cough throw | LU `Necromancer.java:131-149`, `:317-320` | high |
| proj 4218, +3 t | Cough projectile; the target's first cough is 3 ticks after the throw | LU `Necromancer.java:69-77`, `:348-354` | high |
| 8 t | Each coughing player (or binder) coughs every 8 ticks; it spreads to whoever is next to them | LU `Necromancer.java:71-76` | high |
| ≤ 4 coughs | At most 4 coughs each; it can end sooner on a cough that hits for 5; being coughed on again does not restart it | LU `Necromancer.java:78-79`, `:383-406` | high |
| 9 spots | Binder spots as offsets from Moia's start tile, in walking order: (4,5) (8,3) (12,5) (12,1) (12,−3) (8,−1) (4,−3) (4,1) (8,1); each shuffle moves every binder one spot on, the last loops to the first; grid centre = start + (8,1) | LU `Necromancer.java:152-181` | high |
| "Cease this futile…" | Enrage line; she vanishes for about 10 ticks | LU `Necromancer.java:241-242`, `:367-374` | medium |
| 41, 39, 37 … t | Enrage charges (seq 14787 `MOIA_CHANT01`; "Useless corpses... Give me your strength!", then "Again!"): the first lasts 41 ticks, each next 2 less | LU `Necromancer.java:126-136`, `:323-329` | high |
| 5 t | Enrage attack cycle; one animation (14785) for both, the projectile tells magic from ranged | LU `Necromancer.java:183-188`, `:312-316`, `:339-346` | high |
| "Hmph" | Death line | LU `Necromancer.java:375-379` | high |

Values: 11. Binder shuffle interval is not encoded.

## Dreadnoth the Jellied

| Value | Meaning | Source | Conf. |
|---|---|---|---|
| 6 t | Jump (seq 14777 / 14780) → landing, normally | LU `dreadnoth/Dreadnoth.java:61-67`, `:122-130` | high |
| 4 t | Jump → landing after "glub glub", or once below 25% HP | LU `Dreadnoth.java:65-67`, `:257-262` | high |
| 4 t | Cooldown after a fast jump lands before he can jump again | LU `Dreadnoth.java:69-72`, `:250-255` | high |
| back-to-back | Consecutive jumps start on the tick the previous one lands | LU `Dreadnoth.java:122-124` | high |
| proj 4211 / 4212 | Blood blob: aimed at a **tile** = floor splatter (loc 62609 `TFA_JELLY_BOSS_DAMAGE_TILE`); aimed at a **player** = his ranged attack | LU `Dreadnoth.java:47-52`, `:223-241` | high |

Values: 5. Slam counts and the 85/65/45% thresholds are not encoded.

## The Dagon'hai (Elder + Monks)

| Value | Meaning | Source | Conf. |
|---|---|---|---|
| 18 t | A channel completes 18 ticks after the yell (fire "feed the flames", water "guide the flow", air "focus the winds", earth "strengthen the bond") | LU `dagonhaitrio/DagonhaiTrio.java:68-75`, `:165-185` | high |
| 4 t / 8 t | A tome is blocked within 4 ticks of the god yell; an unblocked one completes 8 ticks after it | LU `DagonhaiTrio.java:68-75` | high |
| +3 t | "What have we here?" warns of an incantation; the god is called 3 ticks later | LU `DagonhaiTrio.java:76-78` | high |
| yells | Zamorak "zamorak fuels"/"zamorak empowers", Bandos "bandosian strength", Saradomin "saradominist zeal"; "Argh!" = stunned by the matching element; "Curse you!" = tome blocked | LU `DagonhaiTrio.java:186-221` | high |
| 5 t | Elder attack cycle: ranged (seq 14593) or magic (14597); hits 5 ticks after the anim (thrown 2 ticks in, 3 ticks to land) | LU `DagonhaiTrio.java:136-142`, `:261-272` | high |
| block path | A tome travels from the monk to the cauldron along the floor gusts; standing on the path blocks it | LU `DagonhaiTrioOverlay.java:140-141` | high |
| 1 tile/t | Bandos shockwave: a ring spreading one tile per tick from the cauldron (or whoever tanked it); all its tiles are queued at once | LU `DagonhaiTrioOverlay.java:104-106` | high |
| 3-5 t | Zamorak flame glyphs erupt 3 to 5 ticks after they appear | LU `DagonhaiTrioOverlay.java:167-168` | medium |
| ~4 t / ~5 t | Saradomin lightning: blue (hurts) ~4 ticks, yellow (heals) ~5 ticks from telegraph to strike | LU `DagonhaiTrioOverlay.java:189-190` | medium |
| corners | Imp portals (16651) stand in the corners; a tile adjacent to every open rift closes them all at once | LU `DagonhaiTrioOverlay.java:65-73` | medium |

Values: 10. Ritual-bar and shield-break counts are not encoded.

## Xarth

| Value | Meaning | Source | Conf. |
|---|---|---|---|
| 4 t | Attack cycle for melee (14983), magic (14984), ranged (14985) and the specials flame blast (14989), root (14991), AoE (14992), summon (15017) | LU `xarth/Xarth.java:27-35`, `:110-138` | high |
| P2 magic = volley | Once his ranged attacks have started, his magic anim is only ever the corpse volley | LU `Xarth.java:126-133`, `:162-163` | high |
| proj 4034 at a tile | Corpse fireballs (`LUC2_UNDEAD_DEMON_FIREBALL_PROJ_BIG`, also his normal magic at a player); each spawns a corpse where it lands | LU `Xarth.java:152-156`, `:223-233` | high |
| 7 t | Repeated volley animations within 7 ticks of the first fireball belong to one volley | LU `Xarth.java:157-158` | high |
| 4 t (1-20) | Counter after the first corpse fireball until his next attack; a setting, "more corpses in bigger parties take longer" | LU `FracturedArchiveConfig.java:442-452` | medium |
| 2 t, then 1 tile/t | A corpse (16687) stands still 2 ticks after spawning, then walks one tile per tick to him; 16688 = doused | LU `Xarth.java:167-204` | high |

Values: 6. Explosion cycle, flame spread and burn are not encoded.

## Ocellus

| Value | Meaning | Source | Conf. |
|---|---|---|---|
| 16674 / 16675 / 16677 | P1 / P2 (Blade) / P3 NPCs; P4 is the last P3 quadrant, not a new NPC | LU `finalboss/FinalBoss.java:34-58`, `:324-326`; LD `features/pointstracker/PointsTracker.java:149-160`; CA `core/Encounter.java:37-40` | high |
| 60 / 30 / 158 / 52 | Points for P1 / P2 / P3 / P4 (P3 complete after 3 quadrant-destroy anims, seq 14859) | LD `PointsTracker.java:49-55`, `:162-179` | high |
| 5 t | P1: one cast fires a volley of water (magic; effect 4228 on him) and fire (ranged; effect 4231) shots; each starts a 5-tick counter | LU `FinalBoss.java:405-410` | medium |
| 4 × 2 tendrils, 6 t / 8 t | P1 centre (after teleport-in seq 14813): 4 quick ground attacks (seq 14809), each spawning two tendrils on neighbouring sides; first goes off 6 ticks after appearing, second 8 (clockwise rule picks the first); the charge anim comes ~2 ticks before the tendrils; finisher seq 14810 | LU `FinalBoss.java:415-426`, `:431-434` | high |
| 7 tendrils, 2 t apart, +5 t | P1 corners: the slow ground attack sends 7 tendrils one after another, 2 ticks apart, each going off 5 ticks after it appears (shown as 7); attacks 8 ticks apart | LU `FinalBoss.java:437-442`, `:1675` | high |
| 5 t cycle, 4 t telegraph | P2: every 5 ticks the portals about to fire light up (loc 62685 `TFA_OCELLUS_QUADRANT_WALL_ACTIVE`) 4 ticks before firing; one portal per inner wall stays dark (orange); their lanes cross in a 2×2 safe area | LU `FinalBoss.java:1059-1071` | high |
| 6 per wall, 2 tiles apart | Each inner wall has 6 portals, every 2 tiles from the corner; a lane is the portal's tile and the next one up/east | LU `FinalBoss.java:1072-1073`, `:1161` | high |
| 3 sequences × 6 volleys | The orange portals follow one of three fixed orders of 6 volleys each, as {row, column} portal numbers: {1,1}{3,1}{1,3}{1,1}{2,2}{4,1} · {2,2}{2,3}{1,4}{2,2}{2,4}{3,3} · {1,0}{1,3}{1,5}{3,3}{2,2}{3,0}; the first volley says which | LU `FinalBoss.java:1182-1190` | high |
| 4 / 5 / 7 t | P3 attack lengths confirmed in game: basic melee (14842) 4, sweep/slash (14846) 5, hybrid (14844) 7; his swings are 3 and 4 ticks apart | LU `FinalBoss.java:99-107` | high |
| 5 t | Every other P3 attack (projectile 14850/14851/14853, superior slash 14848 outside Insolence, soul fragment 14855, deflect 14863/14866, quadrant destroy 14859) "not timed yet" | LU `FinalBoss.java:66-97` | low |
| 2 basics + sweep, ×2, special | P3 rhythm: two basic attacks then the sweep = a combo; two combos, then a special; Obliteration starts the two over | LU `FinalBoss.java:152-163`, `:1495-1531` | high |
| spotanim 4328 / 4329 / 4330 | Hybrid 14844 style: melee / magic / ranged effect on Ocellus | LU `FinalBoss.java:346-350`, `:648-675` | high |
| order | Specials: Prayer, orange, Prayer, orange …, starting with Prayer; the orange ones start with Untether then alternate Untether/Insolence, except that after Obliteration/"Run" the next orange is always Insolence; a quadrant move does not change whose turn it is (84 of 86 moves) | LU `FinalBoss.java:164-169`, `:817-821` | high |
| 3 moves | Three quadrant moves (Obliteration/"Run") per fight; no specials after the third (last quadrant = P4) | LU `FinalBoss.java:823-829` | high |
| 15 t | Obliteration/"Run" countdown from the shout | LU `FinalBoss.java:342-343`, `:803-805` | high |
| 6 t, then 3-4 t, +3 t | Prayer special: first hit 6 ticks after `PRAYER_IN` (14869); when he holds it (14870, later in P3) a hit lands then and the next ~3 ticks later (counter 4); "prayer out" effect 4327 comes 1 tick after the hold (short version) or 4 ticks (long version, one more hit 3 ticks later) | LU `FinalBoss.java:794-801`, `:1477-1488`, `:1597-1625` | high |
| 5 t × 3, first +2 t | Insolence: "Insolence..." 2 ticks before the first of 3 slams (seq 14848), slams 5 ticks apart; counter 6 per slam (the client drops the 2nd/3rd slam anim in ~1 of 3 casts) | LU `FinalBoss.java:89-93`, `:104`, `:1583-1595` | high |
| 4 waves, 4 t apart, +12 t | Untether/Unleash: four floor-AoE waves 4 ticks apart, then the arena-wide hit when he lands 12 ticks after the fourth wave; jump to landing about 29 ticks | LU `FinalBoss.java:141-146`, `:324-326`; config `:692` | high |
| 15 t | "This... is enough" (P3 start) → first quadrant's void marks you 15 ticks later (42/42 logs) | LU `FinalBoss.java:845-847`, `:1711-1717` | high |
| 18 t, +3, +2, +2 | Each quadrant move: the void marks a player 18 ticks after the line (spotanim 4302), drops at the earliest 3 ticks after the mark, drops 2 ticks after the player stops, explodes 2 ticks after dropping (floor spotanim 4299); none on the move into the last quadrant | LU `FinalBoss.java:831-843`, `:1000-1001` | high |
| (24,42) (36,42) (24,54) (36,54) | Quadrant landing spots (SW tile, offsets in the map region); arena centre (30,48) | LU `FinalBoss.java:1820-1824`, `:1864` | high |
| start quadrant | The fight starts in the quadrant diagonally opposite the Bandosian Censer, whose place changes each fight; censers are locs 62693-62700 (`TFA_OCELLUS_ARTIFACT_DAMAGE_MELEE/RANGED/SPEC/HEAL`, each with `_INACTIVE`) | LU `FinalBoss.java:117-122`, `:1826-1829`; RL `ObjectID.java:84197-84232` | high |
| locs 62701-62705 | Void degen bomb, void AoE and Insolence AoEs 1/4/7 (stand off these) | LU `FinalBoss.java:109-115`; RL `ObjectID.java:84237-84257` | high |
| 21 t / 19 t | P4 deflects: ranged deflect 21 ticks, then later a melee deflect 19 ticks (effects 4349/4355/4361/4367 ranged, 4352/4358/4364/4370 melee; the suffix is the facing) | LU `FinalBoss.java:352-360` | medium (order conflicts with TR) |
| +3 t, every 2 t × 6 | P4 void chain: telegraph spotanim 4340 on players, explosions (4339) start 3 ticks later and land every 2 ticks, 6 in all, chasing them; a soul-fragment effect (4313) on the player comes 3 ticks before the telegraph (6 to the first explosion) | LU `FinalBoss.java:677-687`, `:732-733` | high |
| 6 t / 12 t | Soul fragments (orbs) spawn over 6 or 12 ticks (spotanims 4309 / 4311 `..._SPAWN_6_TICKS` / `_12_TICKS`) | LU `finalboss/FinalBossOverlay.java:291-295`; RL `SpotanimID` | high (Jagex name) |
| P4 cycle (TR) | S = Deflect rise (seq 14863). Boss hidden t0-4, present 4, first inner void S+0..S+21 (22 t, Deflect Melee); transition 1 tell S+24, Soul Split S+25, damage S+29; outer void S+31..S+50 (20 t, Deflect Range); transition 2 tell S+52, damage S+57; next inner void S+60 (60-tick cycle). Void hit 4 ticks after each void forms; Soul Split heal 2 ticks after the hit | TR `src/content/ocellus/js/OcellusTimings.ts:343-445` | medium (not a plugin) |

Values: 26.

## Gameval names that carry timings

Jagex's own names (RL `AnimationID.java`, line = id), useful as cross-checks: `NPC_BORK_12_CYCLE_STUN01` (14544) and
`NPC_BORK_14_CYCLE_STUN01` (14545); `VFX_OCELLUS_SOUL_FRAGMENT_SPAWN_6_TICKS` / `_12_TICKS` (14913 / 14915; spotanims
4309 / 4311). CH `src/main/resources/com/crejaud/chanced/attack_styles.txt:1275-1351` classes the TFA seqs by name only
(e.g. 14751 RANGED, 14752 MELEE, 14983/14984/14985 MELEE/MAGIC/RANGED, Bork stomps SPECIAL); low confidence.

Player weapons (not bosses), AT `src/main/java/com/attacktimer/VariableSpeed/FracturedArchive.java:41-118`: Obligator
3 t (accurate / `TFA_HUMAN_OBLIGATOR_ATTACK`) and 5 t (multihit); Ascension crossbows 3 t, 2 t on rapid; Zorya's tome 3 t
with 3 buffed 2-t attacks after a successful special.

## Maps to our OPEN ids

| OPEN id | Plugin value | Effect |
|---|---|---|
| OPEN-AG5 | Grounded 35 t on the first down, 40 t on later downs, from the crash anim (LU) | **Settles** the start point (from the crash) and replaces 34 with 35/40. Wiki's 34 conflicts. |
| OPEN-AG4 (timing only) | Laser hits 10 t after the special anim; the mushroom attack 5 t after the same anim; mushrooms appear within 2 t (LU) | Settles the telegraph; damage numbers stay OPEN. |
| AG5 timeline | Mushroom spawn → explosion 5 t (LU's 5 t from the special anim, plus up to 2 t to spawn) | Consistent with our T+5; no change. |
| OPEN-AG2 | Not encoded | Stays OPEN. |
| OPEN-EZ8 | Sword telegraph 15 t from spawn; sets of three 1 t apart; lane straight across the room (LU) | **Settles** telegraph (ours 4) and reach (ours 10). Stun and heal stay OPEN. |
| OPEN-EZ3 | Swords fly cardinal-only from the wall to the opposite wall; ordering N→S / W→E within a set (LU) | Partly settles the stripped sword grid. |
| OPEN-EZ4 / OPEN-EZ2 / OPEN-EZ10 | Finale round: three strikes ~4/5/6 t ahead in random order on the 3×3 in front; each leaves a side column or the back row safe (LU) | Partly settles pattern shape and in-round timing; the 11-t cycle scope stays OPEN. |
| EZ2 (doc) | Two melees, a 9+ t pause, two more melees, then the star (LU) | Conflict to review: our doc has lightning after every second melee. |
| OPEN-BK7 | Stun 16 t (LU); big cannonball ~20 t after each fire bomb | **Settles** the stun (ours 8) and gives the heavy-ball cadence (ours every 6th attack). |
| OPEN-BK6 | Fire bomb after 5 or 6 normal Smork shots (LU) | **Settles** the cadence (ours every 4th). Oil size/duration stays OPEN. |
| OPEN-BK8 | After every stun: 4 stomps 3 t apart, first damage 6 t after the wind-up; safe = Smork shadow from Bork's centre with the ≥13/100 rule (LU) | **Settles** the timing and safe-tile logic (ours: every 6th attack, 2-t wind-up, one stomp, line check). |
| OPEN-BK3 | Stomp timeline above; Bork's base attack interval not encoded | Partly settled. |
| OPEN-MO6 | Magic projectile lands 6 t after the throw (LU) | **Settles** (ours +4). |
| OPEN-MO8 | Cough: first at +3 t, then every 8 t, max 4, ends early on a 5; spreads to adjacent players and binders (LU) | Settles timing/spread; the prayer drain stays OPEN. |
| OPEN-MO3 | Enrage charges 41, 39, 37 … t (−2 each) (LU) | **Settles** the countdown (ours ~40, −5); per-binder damage stays OPEN. |
| OPEN-MO7 | 9 binder spots as offsets from Moia's start, centre (8,1) (LU) | **Settles** layout (ours radius-6 ring of 8). Shuffle interval stays as wiki (15 t). |
| MO2 (doc) | Specials magic, magic, cough; enrage attacks every 5 t (LU) | Confirms the doc. |
| OPEN-DR3 / DR1 | Splatter loc 62609 from ground-aimed blobs 4211/4212; jumps 6 t, 4 t after "glub glub" or < 25%, 4-t cooldown (LU) | Gives jump timing; puddle size/duration stay OPEN. |
| OPEN-DH3 | Channel 18 t; tome blocked within 4 t, completes at 8 t; warning +3 t; Elder 5 t cycle, hit 5 t after anim (LU) | **Settles** most timings (ours channel 6 t). Elder 5 t conflicts with the wiki's speed 6. |
| OPEN-DH7 | Bandos shockwave spreads 1 tile/t from the cauldron or its tank (LU) | Settles the shape/speed (ours: flat 2-tile ring); damage stays OPEN. |
| OPEN-DH8 | Saradomin lightning ~4 t (blue, hurts) / ~5 t (yellow, heals) after telegraph (LU) | Partly settles. |
| OPEN-DH4 | Portals in the corners (LU) | Partial; counts stay OPEN. |
| OPEN-XA3 | Attack cycle 4 t for every attack incl. specials; corpses wait 2 t then walk 1 tile/t (LU) | Partly settles; explosion cycle stays OPEN. |
| OPEN-XA2 / XA2 (doc) | P2 corpses come from corpse fireball volleys (proj 4034 at tiles), not a fixed timer; volley ≈ 4 t to his next attack (LU) | Conflict to review: ours spawns a corpse every 15 t from 12 tiles out. |
| OPEN-OC3 (P1) | One cast = mixed water/fire volley, 5 t per shot; centre tendrils 4×2 at +6/+8 t; corner tendrils 7 × 2 t apart, +5 t, attacks 8 t apart (LU) | Settles the channel/tendril timings (OPEN-OC10 timing); the stripped barrage patterns stay OPEN. |
| OPEN-OC5 | P2 cycle 5 t, telegraph 4 t, 6 portals per wall, 3 fixed sequences of 6 volleys (LU) | **Settles** timing and patterns (ours: 2-t delay, 5 sets per quadrant). Damage curve stays OPEN. |
| OPEN-OC6 | Prayer / orange alternation, Untether first, Insolence always next after a move; 3 moves (LU) | **Settles** the order. |
| OPEN-OC7 | Prayer special hits at +6 t, then ~3 t holds, long version one more at +3 t (LU) | Partly settles; the disable duration stays OPEN. |
| OPEN-OC13 | Untether/Unleash: 4 waves 4 t apart, landing hit 12 t after the 4th, ~29 t total (LU) | Settles timing; damage stays OPEN. |
| OPEN-OC9 | Quadrant spots (24,42)/(36,42)/(24,54)/(36,54), centre (30,48); start opposite the Bandosian Censer; quadrant void 18 t after the move line (P3 start: 15 t), drop/explode rule (LU) | Settles positions and the move void. |
| OPEN-OC14 | Censers exist as locs 62693-62700 with `_INACTIVE` forms (RL gameval, LU) | **Contradicts** "no loc in cache 2757": check that cache for these loc ids. |
| OPEN-OC8 | P4 void chain: +3 t, then 6 explosions every 2 t; deflects 21 t ranged / 19 t melee (LU) vs inner 22 t Deflect Melee / outer 20 t Deflect Range in a 60-t cycle (TR) | Gives the cycle timing; the deflect order conflicts between LU and TR and stays OPEN. |
| P3 basic attacks (ours: speed 3) | Basic 4 t, sweep 5 t, hybrid 7 t, Insolence slams 5 t apart (LU) | Replaces our P3 speed 3 for these attacks. |

Nothing in any plugin settles: OPEN-AG2/AG3/AG6-AG9, OPEN-EZ1/EZ5-EZ7/EZ9, OPEN-BK1/BK2/BK4/BK5, OPEN-MO1/MO2/MO4/MO5/MO9,
OPEN-DR1 (counts)/DR2/DR4/DR5, OPEN-DH1/DH2/DH6, OPEN-XA1/XA4/XA6-XA9/XA11, OPEN-OC1/OC2/OC4/OC11/OC15-OC17 (all
damage, HP or count values, which client plugins cannot see).

## What was searched

- Plugin Hub `plugins/` (3,249 entries @ `97f07e26`), grepped for fractured, archive, tfa, ocellus, ezekiel, xarth, moia,
  dreadnoth, dagon, bork, smork: hits `the-fractured-archive`, `tfa-dry-tracker`, `codex-archive-splits`.
- RuneLite core @ `27fcc1da`: no TFA plugin under `runelite-client/.../plugins`; gameval only.
- `gh search code` for `TFA_OCELLUS_P3`, `TFA_EZEKIEL`, `TFA_GUARDIAN`, `TFA_XARTH`, `TFA_BORK`, `TFA_MOIA`,
  `TFA_JELLY_BOSS`, `TFA_DAGONHAI_LEADER`, `NPC_OCELLUS`, `XARTH_ATTACK`, `NPC_BORK_ENRAGED`, "fractured archive"
  (Java), "16652 ezekiel": found LU, AT, BL, CH, combat-logger (`SuperNerdEric/combat-logger` @ `c7647d20`, ids only),
  TR, plus non-plugin projects.
- Not extracted (outside "plugin code"): `MRobertEvers/oldschool-clientc` `docs/rsprox-analysis/guthixian_archive/`
  (RSProx capture analyses with per-boss scripts: `ez_*`, `bk_*`, `dn_*`, `oc_*`, `tfa_mech.py`, `*_plan.md`);
  `alessandrorodi/ocellus-sim` (cited by TR); `runelite/cs2-scripts` scripts 10010/10016/10030 (already in our
  cache); `weirdgloop/osrs-dps-calc`; other private servers (`SamSjosten/OSRSPS`). The RSProx analyses are the most
  promising next source for the damage and count OPENs above.
