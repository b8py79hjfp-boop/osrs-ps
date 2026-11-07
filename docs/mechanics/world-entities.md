# World entities (boats)

Moving worlds such as Sailing boats: `engine/.../map/WorldEntities.kt`, plugin-api `WorldEntityRef`, the RSProt adapter.
Reference: RSProt README "World Entities" and the `WorldEntityAvatar` / `WorldEntityInfo` / `RebuildWorldEntityV4` KDoc.

## Rules

- **WE1: an entity.** A world entity has:
  - an index (1..4094) and a config id (archive 2 group 72, `WorldEntityType`);
  - a deck of up to 8 × 8 zones held in its own instance (instances.md);
  - a pivot in the root world in fine units (128 per tile), a root level and an angle (0 south, 512 west, 1024 north,
    1536 east);
  - an active level (config op 2; 1 for the boats) and an owner (a player's index, else 0).
  It becomes visible once its deck is copied (`show`).
- **WE2: client view.** RSProt's world entity info decides who sees which entity. In the cycle an entity enters a
  player's view, the session sends, after the root infos:
  - SET_ACTIVE_WORLD (the entity);
  - REBUILD_WORLDENTITY_V4: the deck's reference zones, as for a region rebuild;
  - the entity's NPC info;
  then SET_ACTIVE_WORLD (root). This is the order of the RSProt README.
- **WE3: movement.** Moving an entity updates its avatar's fine coordinate and angle. The client glides between ticks,
  and turns at most 128/2048 per client cycle (RSProt `updateAngle`). A teleport jumps.
- **WE4: players on board** stand on deck tiles: real coordinates in the entity's instance. Their build area is the
  root world around the entity's pivot, and it is rebuilt with REBUILD_NORMAL as the entity sails, not with
  REBUILD_REGION.
- **WE5: clicks on the deck** name deck tiles. They are accepted when the player is on that entity.
- **WE6: deck zones.** Each player gets the zone state of every deck whose pivot is within 31 tiles of the player's
  root position (RSProt `DEFAULT_RENDER_DISTANCE`):
  - The first tick a deck is seen it gets full zone state; after that, each tick's loc changes and ground items.
  - These messages are addressed to the entity's world, with zone offsets counted from the deck's base. The session
    holds them until RSProt reports that world in view, and sends them inside its block after its NPC info.
  - For a player on board, root zone sync is centred on the entity's pivot.
- **WE8: steering and entity clicks.**
  - SET_HEADING (0..15, the angle / 128) goes to the sailing plugin.
  - OPWORLDENTITY1..5 goes to content only if the entity is shown and its pivot is within 31 tiles of the player's root
    position; anything else is an invalid packet.
  - SET_INTERACTION_MODE (`PlayerRef.setInteractionMode`) switches a world's tile clicks between walk and heading,
    and entity clicks between enabled and examine-only.
  - Sources: RSProt `SetHeading`, `OpWorldEntity`, `SetInteractionMode`.
- **WE7: NPCs on decks** stand on deck tiles like any NPC. RSProt's per-world NPC info shows them on the entity.

## Verified

In the real client: a boat deck (config 1, deck copied from the template zone at (1496, 4512), level 1 — that zone is the Pandemonium intro boat, "Will and Anne's boat"; the player raft is at x 3843, z 6458..6460, see docs/plans/sailing.md §2.4) spawned in the sea
off Port Sarim with the player on board. Moving it north and west carried the player and camera along, and the sail
turned with the angle. A loc added to the deck after it was shown (WE6) and an NPC spawned on the deck (WE7) both
showed on the raft.

## OPEN

- **OPEN-WE3:** the pivot's position relative to the deck (config bounds ops 4–9) and the config fields beyond id,
  category and bounds are not interpreted.
