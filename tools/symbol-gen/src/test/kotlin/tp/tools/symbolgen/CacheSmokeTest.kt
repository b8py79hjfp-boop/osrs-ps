package tp.tools.symbolgen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tp.cache.config.Configs
import tp.cache.interfaces.InterfaceTypes
import tp.cache.js5.CacheSpotChecks
import tp.cache.js5.Js5Cache
import tp.cache.map.CollisionBuilder
import tp.cache.map.GameMap
import tp.cache.map.MapKeysLoader
import tp.util.Revision
import java.nio.file.Files
import java.nio.file.Path

/**
 * Test 1 — cache smoke (brief §9.1). Opens the real cache from `./gradlew setup`, runs the §1 spot-checks, decodes every
 * decodable config, every interface and every map square (building full collision), and generates every symbol.
 *
 * It lives here because only this module sees both the cache and the symbol generator.
 * Time budget: the brief asks for < 1 s; measured ~3-4 s on 4 cores (recorded in PROGRESS.md, Phase 1 report).
 */
class CacheSmokeTest {
    private val cacheDir: Path =
        Path.of(System.getProperty("tp.repoRoot") ?: ".", ".data", "cache", Revision.NUMBER.toString(), "disk")

    @Test
    fun `cache 2757 decodes completely`(
        @TempDir symbolsOut: Path,
    ) {
        assertTrue(Files.isDirectory(cacheDir), "Cache missing at $cacheDir — run ./gradlew setup")
        Js5Cache.open(cacheDir).use { cache ->
            val failures = ArrayList<String>()
            assertTrue(CacheSpotChecks.verify(cache) { failures += it }, "Spot-checks failed: $failures")

            val configs = Configs.load(cache)
            assertTrue(configs.summary().all { (_, count) -> count > 0 }, "Empty config type: ${configs.summary()}")

            val interfaces = InterfaceTypes.load(cache)
            assertTrue(interfaces.componentCount > 0)

            val map = GameMap.load(cache, MapKeysLoader.load(cacheDir.resolveSibling("keys.json")))
            assertEquals(
                cache.index(MAPS_ARCHIVE).groupCount,
                map.squares.size + map.skippedGroups.size,
                "Every archive-5 group is either a decoded square or an explicitly skipped group",
            )
            CollisionBuilder.build(map, configs.locs)

            val reports = SymbolGen.generate(cache, symbolsOut)
            val objs = reports.single { it.namespace == "Objs" }
            assertEquals(configs.objs.count, objs.count, "One obj symbol per obj config")
        }
    }

    private companion object {
        const val MAPS_ARCHIVE = 5
    }
}
