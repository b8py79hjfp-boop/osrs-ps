package tp.engine

import org.rsmod.routefinder.collision.CollisionFlagMap
import tp.cache.config.Configs
import tp.cache.js5.Js5Cache
import tp.cache.map.CollisionBuilder
import tp.cache.map.GameMap
import tp.cache.map.MapKeysLoader
import tp.engine.map.StaticLocs
import java.nio.file.Path

/** Immutable cache-derived data a world runs on; built once per JVM and shared by every world (brief §7). */
class WorldData(val configs: Configs, val collision: CollisionFlagMap, val locs: StaticLocs) {
    companion object {
        /** Decodes configs, map, collision and the loc index from an open cache (keys.json beside the disk dir). */
        fun load(cache: Js5Cache, cacheDir: Path): WorldData {
            val configs = Configs.load(cache)
            val map = GameMap.load(cache, MapKeysLoader.load(cacheDir.resolveSibling("keys.json")))
            return WorldData(configs, CollisionBuilder.build(map, configs.locs), StaticLocs.build(map, configs.locs))
        }
    }
}
