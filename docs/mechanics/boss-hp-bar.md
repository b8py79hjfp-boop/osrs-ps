# Boss health bar

Implementation: `content/combat/.../BossHpBar.kt`, adapted from OpenRune-Server `BossHpBarScript` (ISC, D-069).
Sources: OpenRune docs/boss-hp-bar.md; client scripts 2099 (hpbar_hud onLoad), 2102 / 2103 (draw), 2376 (layout),
2887 / 2888 / 2889 (fade); varbits `hpbar_hud_hp` 6099 / `hpbar_hud_basehp` 6100 (varp 1682), varp `hpbar_hud_npc` 1683,
`hpbar_hud_boss` 12401, `hpbar_hud_boss_disabled` 12389.

| # | Rule | Source |
|---|---|---|
| BH1 | NPCs in `boss-hp-bar.tsv` show the bar (Wiki "Category:Bosses" matched to NPC names; names with more than 4 world spawns, e.g. "Black demon", are skipped as generic). | Wiki category; OpenRune `ON_ATTACK` |
| BH2 | The bar opens for a player the first time they damage such an NPC: varp 1683 = NPC type, base / current HP varbits, `hpbar_hud_boss` = 1, OpenRune's colours (inner 0xCC0000, back 0x950000, sliding 0x00F500), then scripts 2887 (alpha 255), 2102 and 2376 one tick later (varps reach the client at the end of the tick). | OpenRune `onOpen` |
| BH3 | Every hit on the NPC pushes its HP to every player watching it; the interface's own var-transmit hook (2099 -> 2102 on varps 1682 / 1683) redraws. | CS 2099 |
| BH4 | It fades (2889, hidden 2 ticks later) 10 ticks after that player's last hit, when the NPC dies, and is hidden at logout. | OpenRune `BossHpBarDamageContributor`, `onClose` |
| BH5 | The "Boss health overlay" setting (`hpbar_hud_boss_disabled`) suppresses it. | OpenRune |

Script 2102's components are in the order 2099 passes them (HP, text, back, sliding, remaining, name area, name, lower / upper
threshold, bar 1, bar 2, 0); OpenRune's order labels the parts wrongly in this cache.

- **OPEN-BH1** OpenRune's `ON_ENTER` mode (instanced bosses, bar shared on entry) is the public `BossHpBar.open` / `close`
  call; no instanced boss uses it yet. Per-NPC colours (OpenRune params) are not modelled.

Verified in the client: Dad (4130) shows "101 / 120", slides on each hit and fades after the player stops.
