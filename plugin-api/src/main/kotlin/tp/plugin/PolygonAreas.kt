package tp.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tp.util.CoordGrid

/**
 * Named polygon areas (e.g. multi-combat), loaded once from JSON `[{name, levels:[..], polygons:[{vertices:[{x,z}..]}]}]`
 * (RS Mod `multiway.json` layout). Immutable after loading; a tile is inside if it lies in any polygon on its level.
 */
public class PolygonAreas private constructor(private val areas: List<Area>) {
    private class Area(
        val name: String,
        val levels: Set<Int>,
        val polygons: List<IntArray>,
        val minX: Int,
        val maxX: Int,
        val minZ: Int,
        val maxZ: Int,
    )

    public val size: Int get() = areas.size

    /** Name of the first area containing [c], or null. */
    public fun areaAt(c: CoordGrid): String? = areas.firstOrNull { it.contains(c) }?.name

    public operator fun contains(c: CoordGrid): Boolean = areas.any { it.contains(c) }

    private fun Area.contains(c: CoordGrid): Boolean {
        if (c.level !in levels || c.x !in minX..maxX || c.z !in minZ..maxZ) return false
        // Vertices are inclusive tile coordinates (RS Mod `multiway.json`): a tile is in when it lies inside the polygon
        // through the vertex tiles (even-odd rule) or on one of its edges.
        val px = c.x.toDouble()
        val pz = c.z.toDouble()
        return polygons.any { poly -> onEdge(poly, c.x, c.z) || inside(poly, px, pz) }
    }

    private fun onEdge(poly: IntArray, x: Int, z: Int): Boolean {
        val n = poly.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val x1 = poly[j * 2]
            val z1 = poly[j * 2 + 1]
            val x2 = poly[i * 2]
            val z2 = poly[i * 2 + 1]
            val cross = (x2 - x1).toLong() * (z - z1) - (z2 - z1).toLong() * (x - x1)
            if (cross == 0L && x in minOf(x1, x2)..maxOf(x1, x2) && z in minOf(z1, z2)..maxOf(z1, z2)) return true
            j = i
        }
        return false
    }

    private fun inside(poly: IntArray, px: Double, pz: Double): Boolean {
        var result = false
        val n = poly.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[i * 2].toDouble()
            val zi = poly[i * 2 + 1].toDouble()
            val xj = poly[j * 2].toDouble()
            val zj = poly[j * 2 + 1].toDouble()
            if ((zi > pz) != (zj > pz) && px < (xj - xi) * (pz - zi) / (zj - zi) + xi) result = !result
            j = i
        }
        return result
    }

    public companion object {
        public fun fromJson(text: String): PolygonAreas {
            val list =
                Json.parseToJsonElement(text).jsonArray.map { a ->
                    val o = a.jsonObject
                    val polygons =
                        o.getValue("polygons").jsonArray.map { p ->
                            val vs = p.jsonObject.getValue("vertices").jsonArray
                            IntArray(vs.size * 2) { k ->
                                val v = vs[k / 2].jsonObject
                                (if (k % 2 == 0) v.getValue("x") else v.getValue("z")).jsonPrimitive.int
                            }
                        }
                    val xs = polygons.flatMap { p -> p.filterIndexed { i, _ -> i % 2 == 0 } }
                    val zs = polygons.flatMap { p -> p.filterIndexed { i, _ -> i % 2 == 1 } }
                    Area(
                        o.getValue("name").jsonPrimitive.content,
                        o
                            .getValue("levels")
                            .jsonArray
                            .map { it.jsonPrimitive.int }
                            .toSet(),
                        polygons,
                        xs.min(),
                        xs.max(),
                        zs.min(),
                        zs.max(),
                    )
                }
            return PolygonAreas(list)
        }
    }
}
