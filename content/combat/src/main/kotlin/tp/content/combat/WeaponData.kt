package tp.content.combat

/**
 * Per-weapon server data the client cache lacks (docs/mechanics/combat-data.md CD-W1..W4; `weapons.tsv`, converted from
 * RS Mod's `objs.toml` (ISC) by tools/wiki-import/rsmod_weapons.py, D-054): weapon category, the attack animation of
 * each combat-tab slot, the block animation, the stand / turn / walk / run animations the player's appearance uses
 * while it is wielded, the attack sound per slot, speed and range overrides, and ammo projectiles. -1 = not given.
 */
internal class WeaponDef(
    val category: Int?,
    /** Attack seq per combat-tab slot 0..3. */
    val attackAnims: IntArray,
    val blockAnim: Int,
    /** Render anims: ready, turn, walk, walk back, walk left, walk right, run (appearance block order). */
    val renderAnims: IntArray?,
    val sounds: IntArray,
    val speed: Int,
    val range: Int,
    /** Ammo / thrown weapon: projectile shape name, travel and launch spot anims (and a dark bow's second launch). */
    val projAnim: String,
    val projTravel: Int,
    val projLaunch: Int,
    val projLaunchDouble: Int,
)

internal object WeaponData {
    /** Declared before [byObj]: object properties initialise in order. */
    private val RENDER_COLUMNS = listOf("ready", "turn", "walk", "walk_back", "walk_left", "walk_right", "run")
    private val byObj: Map<Int, WeaponDef> = parse()

    fun of(obj: Int): WeaponDef? = byObj[obj]

    val size: Int get() = byObj.size

    private fun parse(): Map<Int, WeaponDef> {
        val text = WeaponData::class.java.getResource("weapons.tsv")?.readText() ?: error("weapons.tsv missing")
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val col =
            lines
                .first()
                .split('\t')
                .withIndex()
                .associate { (i, name) -> name to i }
        val out = HashMap<Int, WeaponDef>(lines.size * 2)
        for (line in lines.drop(1)) {
            val cells = line.split('\t')

            fun i(name: String): Int = cells.getOrNull(col.getValue(name))?.toIntOrNull() ?: -1

            fun s(name: String): String = cells.getOrNull(col.getValue(name)).orEmpty()
            val render = RENDER_COLUMNS.map { i(it) }
            out[i("obj")] =
                WeaponDef(
                    category = i("category").takeIf { it >= 0 },
                    attackAnims = IntArray(SLOTS) { i("anim${it + 1}") },
                    blockAnim = i("block"),
                    renderAnims = if (render.all { it < 0 }) null else render.toIntArray(),
                    sounds = IntArray(SLOTS) { i("sound${it + 1}") },
                    speed = i("speed"),
                    range = i("range"),
                    projAnim = s("proj_anim"),
                    projTravel = i("proj_travel"),
                    projLaunch = i("proj_launch"),
                    projLaunchDouble = i("proj_launch_double"),
                )
        }
        return out
    }

    private const val SLOTS = 4
}
