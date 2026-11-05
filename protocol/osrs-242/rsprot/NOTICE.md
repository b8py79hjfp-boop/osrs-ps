# Vendored: RSProt revision 242

- Upstream: https://github.com/blurite/rsprot, paths `protocol/osrs-242/osrs-242-{api,common,desktop,internal,model,shared}`
- Commit: `67d2136882fbc6072d1b72e935025209d9f63b5a` ("Revision 242 (#82)", 2026-11-03). Upstream says 242 is not
  published yet "as testing needs to be done" — see docs/DECISIONS.md D-005.
- License: MIT (see `LICENSE`, copied verbatim). Copyright (c) 2024 Z-Kris.
- Copied: `src/main` only. Benchmarks and tests are not vendored.
- Depends on RSProt's published shared modules `net.rsprot:{buffer,crypto,compression,haproxy,protocol}:1.0.0-ALPHA-20261014`,
  verified byte-identical to the 242 commit's sources (`git diff 37c4eb46 67d21368 -- buffer crypto compression haproxy protocol/src` is empty).
- Local modifications: none. Any change must be listed here with its reason and the RSProx evidence.
