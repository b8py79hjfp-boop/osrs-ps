# Vendored: RSProt revision 242

- Upstream: https://github.com/blurite/rsprot, paths `protocol/osrs-242/osrs-242-{api,common,desktop,internal,model,shared}`
- Commit: `67d2136882fbc6072d1b72e935025209d9f63b5a` ("Revision 242 (#82)", 2026-11-03). Upstream says 242 is not
  published yet "as testing needs to be done" — see docs/DECISIONS.md D-005.
- License: MIT (see `LICENSE`, copied verbatim). Copyright (c) 2024 Z-Kris.
- Copied: `src/main` only. Benchmarks and tests are not vendored.
- Depends on RSProt's published shared modules `net.rsprot:{buffer,crypto,compression,haproxy,protocol}:1.0.0-ALPHA-20261014`,
  verified byte-identical to the 242 commit's sources (`git diff 37c4eb46 67d21368 -- buffer crypto compression haproxy protocol/src` is empty).
- Local modifications (each with reason and evidence):
  1. `shared/.../loginprot/incoming/codec/shared/LoginBlockDecoder.kt` (2026-11-05): the rev-242 login decode first
     assumes **no jxSessionId section** between the RSA and XTEA blocks, then falls back to upstream's flag (+ string)
     layout. XTEA decoding moved into `decodeXteaBlock` unchanged. Added `JxLayoutStats` counters.
     Evidence: RuneLite 1.13.6 via RSProx failed upstream decoding with "Unterminated string" right after the RSA
     block; RSProx's live proxy (`ClientLoginHandler.kt:166-167`) treats the bytes after RSA as the XTEA block.
     DECISIONS D-005 OPEN-B.
