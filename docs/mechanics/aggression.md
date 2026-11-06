# NPC aggression, tolerance, max range and retreat

Code: `content/combat/.../Aggression.kt` (hunting, tolerance), `NpcCombat.outsideAggressionRange` (retreat trigger),
engine `npc/NpcAi.kt` (hunt turn, retreat steps) and `interact/Interaction.kt` (max range while following).

Sources: OSRS Wiki "Aggressiveness" (AG), "Tolerance" (TO), "Retreat" (RE); osrs-docs "Retreat" (OD,
https://osrs-docs.com/docs/mechanics/retreat/); RS Mod `NpcPlayerHuntProcessor`, `HuntBuilds` (`notbusy_*`),
`NpcTypeBuilder` defaults, `constants.combat_activecombat_delay` (RM).

| # | Rule | Source |
|---|---|---|
| AG1 | NPCs whose Wiki infobox says `aggressive = Yes` hunt players. | AG, npc-combat.md N1 |
| AG2 | A hunted player must: be in the NPC's hunt range, have line of sight, have a combat level at most twice the NPC's ("less than or equal to twice the monster's level") unless in the Wilderness ("Most monsters in the Wilderness are aggressive regardless of level"), not be in combat (attacked, attacking or hit in the last 8 ticks) unless in a multi-combat area, not be fought by another NPC in single-way combat, have no modal open, and not be tolerated (AG4). | AG; RM `notbusy_*` (`checkNotTooStrong` outside Wilderness, `checkNotCombat`, `checkNotBusy`, line of sight), `combat_activecombat_delay = 8` |
| AG3 | Hunting runs in the NPC's turn while it has no interaction; among eligible players one is picked uniformly at random, then attacked as if retaliating (npc-combat.md N5). The hunt range is measured from the NPC's south-west tile, without its size ("the size of the NPC is not accounted for in the hunt range"). | RM reservoir pick; AG "Hunt range" |
| AG4 | Tolerance: two 21×21 regions centred on the player; leaving both moves the first region to the player's tile and restarts a 10-minute (1,000-tick) timer; after it, standard monsters stop being aggressive to that player. Logging out doesn't reset it. | TO "Standard monsters" |
| AG5 | Max range: while fighting, an NPC doesn't move more than 7 tiles from its spawn point ("The max range is almost always seven tiles"). The aggression range is the max range plus the attack range (melee 1; ranged/magic NPC distance) around the spawn, accounting for the NPC's size; when its target is outside it and can't be reached, the NPC stops fighting. | RE "Mechanics"; AG "Aggression range" |
| AG6 | Retreat: an NPC attacked from outside its aggression range retreats instead of retaliating: each tick it steps away from the attacker (attacker at x ≥ and z ≥ → south-west; x ≥, z < → north-west; x <, z ≥ → south-east; else north-east), staying within its max range. It stops when the step is blocked and a side has a wall, when the attacker is more than 25 tiles away, or when attacked from inside its aggression range. | OD; RE |

## OPEN

- **OPEN-AG1:** per-NPC hunt ranges aren't published; all aggressive NPCs use 5 (RS Mod's default). Per-NPC max ranges
  likewise use 7.
- **OPEN-AG2:** "not busy" is read as "no modal interface open"; RS Mod leaves its AFK check unimplemented, and so do we.
- Bosses and other monsters that ignore tolerance or level set their own hunt rules in their content.
