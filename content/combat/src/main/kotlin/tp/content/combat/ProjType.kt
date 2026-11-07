package tp.content.combat

import tp.plugin.EntityRef

/**
 * Projectile shapes (RS Mod `ProjAnimBuilds`, ISC; docs/DECISIONS.md D-054): heights in MAP_PROJANIM_V2 units (no
 * implicit × 4), the start delay in client cycles, the arc angle and the start offset. The arrival is set by the
 * caller to the hit's landing tick (combat-timeline.md), so the visual always lands with the hitsplat.
 */
internal class ProjType(val startHeight: Int, val endHeight: Int, val delay: Int, val angle: Int, val progress: Int) {
    /** Launches [gfx] from [from] at [to], arriving when a hit queued now with [hitDelay] lands. */
    fun launch(from: EntityRef, to: EntityRef, gfx: Int, hitDelay: Int) {
        if (gfx < 0) return
        val arrive = to.ticksUntilHit(hitDelay) * CYCLES_PER_TICK
        from.projectile(to, gfx, startHeight, endHeight, minOf(delay, arrive - 1), maxOf(arrive, 1), angle, progress)
    }

    companion object {
        private const val CYCLES_PER_TICK = 30

        @Suppress("MagicNumber")
        val ARROW = ProjType(163, 146, 41, 15, 11)

        @Suppress("MagicNumber")
        val BOLT = ProjType(155, 146, 41, 5, 11)

        @Suppress("MagicNumber")
        val CHINCHOMPA = ProjType(163, 146, 21, 15, 11)

        @Suppress("MagicNumber")
        val THROWN = ProjType(163, 146, 32, 15, 11)

        @Suppress("MagicNumber")
        val DOUBLE_ARROW_ONE = ProjType(163, 146, 41, 5, 11)

        @Suppress("MagicNumber")
        val DOUBLE_ARROW_TWO = ProjType(163, 146, 41, 25, 11)

        @Suppress("MagicNumber")
        val MAGIC = ProjType(172, 124, 51, 16, 64)

        @Suppress("MagicNumber")
        val MAGIC_LOW = ProjType(172, 0, 51, 16, 64)

        @Suppress("MagicNumber")
        val IBAN = ProjType(144, 124, 60, 16, 64)

        @Suppress("MagicNumber")
        val CURSE = ProjType(144, 124, 52, 16, 64)

        @Suppress("MagicNumber")
        val CRUMBLE = ProjType(124, 124, 46, 16, 64)

        @Suppress("MagicNumber")
        val BIND = ProjType(180, 0, 75, 16, 64)

        @Suppress("MagicNumber")
        val TUMEKENS = ProjType(250, 124, 56, 32, 40)

        /** NPC projectiles without their own data: the magic spell shape. */
        val NPC_DEFAULT = MAGIC

        /** RS Mod obj `proj_anim` names (weapons.tsv) to shapes. */
        fun named(name: String): ProjType? =
            when (name) {
                "arrow" -> ARROW
                "bolt" -> BOLT
                "chinchompa" -> CHINCHOMPA
                "thrown" -> THROWN
                "doublearrow_one" -> DOUBLE_ARROW_ONE
                "doublearrow_two" -> DOUBLE_ARROW_TWO
                else -> null
            }
    }
}
