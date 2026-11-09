# Combat sounds

| Id | Rule | Source |
|---|---|---|
| CS1 | **Attack sound.** When a player attacks (melee, ranged, or a salamander's magic), the attacker hears the weapon's synth for the selected stance (`weapons.tsv` `sound1..4`; a missing stance falls back to the weapon's first sound). Unarmed: `human_unarmedpunch` (2564) for stances 1, 3 and 4, `human_unarmedkick` (2565) for stance 2. A weapon with no sound listed is silent. Sent as SYNTH_SOUND to the attacker only. | RS Mod (ISC) `PlayerAttackManager.playWeaponFx`, `MeleeAnimationAndSound`, synth.sym (commit fa13b3f); weapons.tsv from tools/wiki-import/rsmod_weapons.py |
| CS2 | **OPEN-CS1: not built.** Spell cast and impact sounds, NPC attack / block / death sounds, the player's own block and hit sounds, and area sounds that other players nearby hear. RS Mod takes NPC sounds from params it defines itself, so there is no cache data to read; they need a sourced table. | — |
