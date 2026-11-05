package tp.engine

import org.rsmod.routefinder.collision.CollisionFlagMap
import tp.cache.config.Configs
import tp.engine.map.StaticLocs

/** Immutable cache-derived data a world runs on; built once per JVM and shared by every world (brief §7). */
class WorldData(val configs: Configs, val collision: CollisionFlagMap, val locs: StaticLocs)
