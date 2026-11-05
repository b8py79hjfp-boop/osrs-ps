# Phase 1 — Cache and world data

## Scope

1. **JS5 completion** (`cache/`): group → file splitting (multi-file groups), name hashes, an archive facade, and
   map lookups by name (`m{x}_{y}` / `l{x}_{y}`). XTEA keys are loaded from `keys.json` (empty for 2757, but the code
   path stays).
2. **`util/` read buffer**: a zero-copy cursor over `ByteArray`: u8/u16/u24/i32/i64, the smart variants, CP-1252
   `jstr` strings.
3. **Config decoders** (`cache/config/`): strict opcode loops. An unknown opcode throws, so cache drift fails test 1
   loudly. One immutable type class per config, held in an id-indexed list.
   - Main session (sets the pattern): **obj** (10), **loc** (6). Loc feeds collision.
   - `implementer`, following that pattern: npc 9, seq 12, spotanim 13, varp 16, varbit 14, enum 8, struct 34,
     param 11, inv 5, dbrow 38, dbtable 39, dbtable index (archive 21), area 18, mapelement 35, hitmark 32,
     headbar 33, underlay 1, overlay 4, idk 3, varclient 19, varclientstring 15, hunt 2, plus any other group a
     reference decodes; also interfaces (archive 3, IF1 + IF3), which Phase 3 needs for button validation.
   - Groups no reference names or decodes are recorded as **opaque** (id + file count + raw sizes) and listed in
     DECISIONS. They're client-only as far as we know (OPEN until a source says otherwise).
4. **Maps** (`cache/map/`): terrain (u16 opcodes: height / overlay / settings / underlay) and loc lists
   (incremental smart ids, packed position, shape/angle byte), for every map square.
5. **Collision** (main session): vendor RS Mod `engine/routefinder` (ISC) as module `routefinder/`. Build base
   collision for the whole map from tile settings + locs, with client semantics documented in
   `docs/mechanics/collision.md` (D-011). Base map is immutable and shareable across worlds; per-world overlay is Phase 3.
6. **Symbols** (`symbols/`, generator in `tools/symbol-gen`): Kotlin/Java constants generated from archive 24 at build
   time into `symbols/build/generated` (never committed; generated from `.data`, which `setup` provides).
   Constant-pool sharding for namespaces over ~30k names (objs 34,766, locs 63,320). Interfaces get id + component
   constants.
7. **`tools/cache-dump`** (`implementer`): CLI that dumps any decoded type or map square as readable text/JSON.
8. **Test 1 — cache smoke** (brief §9.1): opens 2757, decodes every config of every decodable group and every map
   square, runs the §1 spot-checks, runs symbol generation in-memory.
9. **Measurements**: startup time and heap for cache + collision, recorded in PROGRESS.md.

## Modules touched

`util`, `cache`, new `routefinder` (vendored), `symbols`, new `tools/symbol-gen`, `tools/cache-dump`, `server` (boot
loads configs + collision and logs timings).

## Design choices (details in DECISIONS when made)

- Decoders are hand-written `when(opcode)` loops: no reflection, no codegen. Types are plain classes with `val`s,
  built from a mutable builder inside the decoder only.
- Decode all configs eagerly at boot (the server needs most of them; lazy decoding hides drift).
- Map decode and collision build run in parallel per map square on a startup pool; the result is a single
  `CollisionFlagMap`. A prebuilt collision snapshot (for the <10 s dev boot of §6) comes only if measured boot time
  needs it.
- Symbol ids stay `Int` constants in Phase 1; typed wrappers are a plugin-api decision (Phase 3).

## Risks

| Risk | Mitigation |
|---|---|
| Rev-242 opcodes newer than the references (RuneLite 1.13.6, RS Mod rev 233) | Strict decoders fail test 1 with the group/id/opcode; cross-check RSProx/RuneLite for the opcode, else mark OPEN and skip the opcode's payload only if its size is known. |
| Collision semantics wrong at bridges / ground decor | Follow client behaviour (D-011), cite sources, verifier pass on `collision.md`. |
| Test 1's <1 s budget vs decoding ~200k configs + ~1.5k map squares | Parallel decode; measure. If impossible, record the actual time and ask. |
| Symbol class constant-pool overflow | Sharding via a class hierarchy; verified by compiling the generated sources. |
| Gameval name collisions / invalid identifiers | Generator sanitizes and fails on duplicates within a namespace (dedupe by suffixing `_<id>` if Jagex duplicates exist, logged). |

## Definition of done

- Test 1 passes (locally and in CI).
- Server boot decodes all configs, builds full-map collision and logs counts + timings.
- Startup time and heap for cache + collision recorded in PROGRESS.md.
- `docs/mechanics/collision.md` written with sources; verifier pass done.
