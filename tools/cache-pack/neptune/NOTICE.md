# Vendored: Neptune (ClientScript 2 compiler)

- Upstream: https://github.com/neptune-ps/neptune
- Commit: `6f13782d18da4e66965e5dfe25dddb0f83ebefb9` ("fix: string template variables no longer require an underscore", 2026-11-03)
- License: MIT (see `LICENSE`, copied verbatim). Copyright 2021-present Joshua Filby and other contributors.
- Modules copied (`src/main` only): `runescript-parser`, `runescript-compiler`, `clientscript-compiler`. Packages `me.filby.neptune.*` kept.
- Not copied: `runescript-runtime` and all tests (only needed by upstream's own test runner), the compiler's command-line
  application and its config loading (`ClientScriptCompilerApplication.kt`, which pulled in clikt, gson, 4koma and a jitpack
  repository). The `cache-pack` tool drives `ClientScriptCompiler` directly.
- Changes to the sources: none, apart from deleting the file above. The Gradle build files are ours (`tp.kotlin-vendored`
  convention, ANTLR output under `build/`, versions from `gradle/libs.versions.toml`).
- What Neptune does not ship: the base symbols (command signatures and opcodes, script names, constants). `tools/cache-pack`
  generates what the cache contains and curates the rest; see `tools/cache-pack/src/main/resources/cs2/` and
  docs/DECISIONS.md D-071 to D-074.

Any local modification must be listed here with a reason.
