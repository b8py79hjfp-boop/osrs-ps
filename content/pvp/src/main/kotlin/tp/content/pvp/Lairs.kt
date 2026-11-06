package tp.content.pvp

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.SharedAttrs
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * The Wilderness boss lairs (docs/mechanics/boss-lairs.md): Callisto's Den, Vet'ion's Rest and the Silk Chasm (multi)
 * and Hunter's End, the Skeletal Tomb and the Web Chasm (singles-plus), with the shared entry fee and the Escape Caves.
 * Lair geometry, exits and entrances come from the cache (lair arenas by flood fill from their unique locs; gameval
 * names `*_SINGLES_ENTRANCE01` tell the singles-plus entrances apart).
 */
public class Lairs : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart { for (lair in LAIRS) spawnNpc(lair.boss, lair.bossSpawn, wanderRange = 0) }
        for (lair in LAIRS) {
            onOpLoc(lair.entrance, OP_ENTER) { enter(player, lair) }
            onOpLoc(lair.entrance, OP_PEEK) { peek(player, lair) }
            onOpLoc(lair.entrance, OP_FEE) { player.message(feeMessage(player)) }
        }
        for (exit in listOf(Locs.WILD_CALLISTO_EXIT01, Locs.WILD_VETION_EXIT01, Locs.WILD_VENANATIS_EXIT)) {
            onOpLoc(exit, OP_ENTER) { leave(player) }
        }
        for (exit in listOf(Locs.WILD_BOSS_ESCAPE_CAVE_EXIT01, Locs.WILD_BOSS_ESCAPE_CAVE_EXIT02, Locs.WILD_BOSS_ESCAPE_CAVE_EXIT03)) {
            onOpLoc(exit, OP_ENTER) { loc -> escape(loc) }
        }
        // BL8: the Escape Caves' own surface entrances share the fee (OPEN-BL5: they lead to a random Escape Caves point).
        onOpLoc(Locs.WILD_CAVE_EXIT01, OP_ENTER) {
            if (!pay(player)) return@onOpLoc
            player[VISITED] = true
            player[SharedAttrs.TELEPORT_DELAY_UNTIL] = player.tick + TELEPORT_DELAY
            player.teleport(ESCAPE_POINTS[player.random(ESCAPE_POINTS.size)])
        }
        onOpLoc(Locs.WILD_CAVE_EXIT01, OP_PEEK) { player.message(feeMessage(player)) }
        // BL9: "If you log out inside a boss lair then you'll log back in outside the Escape Caves."
        onLogin { if (LAIRS.any { it.contains(coord) }) teleport(ESCAPE_EXITS.first().second) }
        events.on<DeathEvent> { e -> onDeath(e) }
    }

    // ---- fee (BL2-BL4)

    private fun fee(player: PlayerRef): Int = maxOf(0, FEE - FEE_DISCOUNT * player.varbit(Varbits.WBR_ENTRANCE_DISCOUNT))

    private fun feeMessage(player: PlayerRef): String =
        if (player.varbit(Varbits.WBR_ENTRANCE_FEE_PAID) == 1) {
            "Your entry fee is paid."
        } else {
            "The entry fee is %,d coins.".format(fee(player))
        }

    /** BL2: "this fee does not need to be brought along in the inventory, and can be deducted from the bank". */
    private fun pay(player: PlayerRef): Boolean {
        if (player.varbit(Varbits.WBR_ENTRANCE_FEE_PAID) == 1) return true
        val amount = fee(player)
        val paid =
            amount == 0 ||
                player.transaction("lair fee") { remove(Invs.INV, COINS, amount) } ||
                player.transaction("lair fee (bank)") { remove(Invs.BANK, COINS, amount) }
        if (!paid) {
            player.message("You need %,d coins in your inventory or bank to enter.".format(amount))
            return false
        }
        player.setVarbit(Varbits.WBR_ENTRANCE_FEE_PAID, 1)
        player.setVarbit(Varbits.WBR_ENTRANCE_DISCOUNT, 0)
        player[PAID_AMOUNT] = amount
        player.message("You pay the entry fee of %,d coins.".format(amount))
        return true
    }

    private fun enter(player: PlayerRef, lair: Lair) {
        if (!pay(player)) return
        player[VISITED] = true
        // BL5: "Upon entering the lair, players will be unable to teleport for 3 ticks".
        player[SharedAttrs.TELEPORT_DELAY_UNTIL] = player.tick + TELEPORT_DELAY
        player.teleport(lair.arrival)
    }

    /** OPEN-BL2: the Peek wording isn't published. */
    private fun peek(player: PlayerRef, lair: Lair) {
        val inside = player.playersAround(lair).size
        player.message(if (inside == 0) "You peek inside and see no adventurers." else "You peek inside and count $inside adventurer(s).")
    }

    /** Players inside [lair] (scanned from the lair's own coordinates, so the peeker outside doesn't matter). */
    private fun PlayerRef.playersAround(lair: Lair): List<PlayerRef> = playersInWorld().filter { lair.contains(it.coord) }

    /**
     * BL6: leaving a multi lair puts the player at one of three Escape Caves points at random, "any player who exits
     * within 10 seconds of the previous player will arrive at the same point"; singles-plus lairs exit to the surface.
     */
    private fun PlayerScript.leave(player: PlayerRef) {
        val lair = LAIRS.firstOrNull { it.contains(player.coord) } ?: return
        if (!lair.multi) return player.teleport(lair.surfaceExit)
        val now = player.tick
        val last = player.worldAttr(LAST_ESCAPE)
        val point = if (last != null && now - last.first <= SAME_POINT_TICKS) last.second else player.random(ESCAPE_POINTS.size)
        player.setWorldAttr(LAST_ESCAPE, now to point)
        player[SharedAttrs.TELEPORT_DELAY_UNTIL] = now + TELEPORT_DELAY
        player.teleport(ESCAPE_POINTS[point])
    }

    /** OPEN-BL3: Escape Caves exits lead to the surface tile above them (the usual 6,400-tile dungeon offset). */
    private fun PlayerScript.escape(loc: LocRef) {
        val target =
            ESCAPE_EXITS.minByOrNull { (cave, _) ->
                maxOf(
                    kotlin.math.abs(cave.x - loc.coord.x),
                    kotlin.math.abs(cave.z - loc.coord.z),
                )
            }
        player.teleport(target?.second ?: return)
    }

    /**
     * BL4: a player killed by another player, without having left the Wilderness since visiting a lair or the Escape
     * Caves, loses the paid fee, which "is dropped as loot for the killer".
     */
    private fun onDeath(e: DeathEvent) {
        val dead = e.entity as? PlayerRef ?: return
        val killer = e.killer as? PlayerRef ?: return
        if (dead[VISITED] != true || dead.varbit(Varbits.WBR_ENTRANCE_FEE_PAID) != 1) return
        dead.setVarbit(Varbits.WBR_ENTRANCE_FEE_PAID, 0)
        dead[VISITED] = null
        val amount = dead[PAID_AMOUNT] ?: FEE
        if (amount > 0) killer.transaction("lair fee loot") { createOnGround(COINS, amount, dead.coord, killer.username) }
    }

    internal class Lair(
        val name: String,
        val entrance: Int,
        val boss: Int,
        val multi: Boolean,
        /** Wilderness level of the lair: its entrance's level (BL7). */
        val entranceTile: CoordGrid,
        val minX: Int,
        val maxX: Int,
        val minZ: Int,
        val maxZ: Int,
        val arrival: CoordGrid,
        val bossSpawn: CoordGrid,
    ) {
        fun contains(c: CoordGrid): Boolean = c.level == 0 && c.x in minX..maxX && c.z in minZ..maxZ

        /** Singles-plus lairs exit to the tile beside their entrance (OPEN-BL3). */
        val surfaceExit: CoordGrid get() = CoordGrid(entranceTile.x, entranceTile.z - 2, entranceTile.level)
    }

    public companion object {
        private const val OP_ENTER = 1
        private const val OP_PEEK = 2
        private const val OP_FEE = 3

        /** BL2: "a fee of 50,000 coins"; BL3: "each kill in which the player deals the most damage ... reduces the fee by 10,000". */
        private const val FEE = 50_000
        private const val FEE_DISCOUNT = 10_000
        private const val COINS = 995

        /** BL5: 3 ticks without teleporting after entering or leaving. */
        private const val TELEPORT_DELAY = 3

        /** BL6: 10 seconds = 16 ticks. */
        private const val SAME_POINT_TICKS = 16L

        private val VISITED = AttrKey<Boolean>("lair_visited")

        /** World-scoped: tick and point of the last multi-lair exit (BL6). */
        private val LAST_ESCAPE = AttrKey<Pair<Long, Int>>("lair_last_escape")
        private val PAID_AMOUNT = AttrKey<Int>("lair_fee_paid")

        /** Escape Caves arrival tiles beside its three exits (cache: `wild_boss_escape_cave_exit01..03`). */
        private val ESCAPE_POINTS = listOf(CoordGrid(3338, 10285), CoordGrid(3380, 10285), CoordGrid(3359, 10247))

        /** Escape Caves exit loc tile -> surface tile above it (walkable, checked on the collision map). */
        private val ESCAPE_EXITS =
            listOf(
                CoordGrid(3336, 10287) to CoordGrid(3336, 3887),
                CoordGrid(3382, 10287) to CoordGrid(3382, 3887),
                CoordGrid(3358, 10244) to CoordGrid(3360, 3844),
                CoordGrid(3361, 10273) to CoordGrid(3361, 3873),
            )

        internal val LAIRS =
            listOf(
                Lair(
                    "Callisto's Den",
                    Locs.WILD_CALLISTO_ENTRANCE01,
                    Npcs.CALLISTO,
                    true,
                    CoordGrid(3291, 3849),
                    3351,
                    3367,
                    10317,
                    10336,
                    CoordGrid(3359, 10335),
                    CoordGrid(3357, 10318),
                ),
                Lair(
                    "Hunter's End",
                    Locs.WILD_CALLISTO_SINGLES_ENTRANCE01,
                    ARTIO,
                    false,
                    CoordGrid(3115, 3676),
                    1751,
                    1767,
                    11533,
                    11552,
                    CoordGrid(1759, 11551),
                    CoordGrid(1758, 11534),
                ),
                Lair(
                    "Vet'ion's Rest",
                    Locs.WILD_VETION_ENTRANCE01,
                    VETION,
                    true,
                    CoordGrid(3221, 3787),
                    3286,
                    3304,
                    10190,
                    10214,
                    CoordGrid(3295, 10213),
                    CoordGrid(3294, 10193),
                ),
                Lair(
                    "Skeletal Tomb",
                    Locs.WILD_VETION_SINGLES_ENTRANCE01,
                    CALVARION,
                    false,
                    CoordGrid(3180, 3683),
                    1878,
                    1896,
                    11534,
                    11558,
                    CoordGrid(1887, 11557),
                    CoordGrid(1886, 11537),
                ),
                Lair(
                    "Silk Chasm",
                    VENENATIS_ENTRANCE,
                    Npcs.VENENATIS,
                    true,
                    CoordGrid(3321, 3794),
                    3410,
                    3436,
                    10190,
                    10217,
                    CoordGrid(3423, 10216),
                    CoordGrid(3421, 10192),
                ),
                Lair(
                    "Web Chasm",
                    Locs.WILD_VENENATIS_SINGLES_ENTRANCE01,
                    SPINDEL,
                    false,
                    CoordGrid(3183, 3744),
                    1618,
                    1644,
                    11534,
                    11561,
                    CoordGrid(1631, 11560),
                    CoordGrid(1629, 11536),
                ),
            )

        /** Npc config ids (cache 2757) for bosses without a gameval constant name we can rely on. */
        private const val ARTIO = 11992
        private const val VETION = 6611
        private const val CALVARION = 11993
        private const val SPINDEL = 11998
        private const val VENENATIS_ENTRANCE = 47077

        /** The lair the coordinate is in, or null. */
        public fun lairAt(c: CoordGrid): String? = LAIRS.firstOrNull { it.contains(c) }?.name

        /** Wilderness level inside a lair: its entrance's level (BL7). */
        internal fun levelAt(c: CoordGrid): Int? = LAIRS.firstOrNull { it.contains(c) }?.let { Wilderness.level(it.entranceTile) }

        internal fun multiAt(c: CoordGrid): Boolean = LAIRS.any { it.multi && it.contains(c) } || inEscapeCaves(c)

        internal fun singlesPlusAt(c: CoordGrid): Boolean = LAIRS.any { !it.multi && it.contains(c) }

        /** The Escape Caves (cache square 52,160 walkable area). */
        @Suppress("MagicNumber") // cache bounds
        internal fun inEscapeCaves(c: CoordGrid): Boolean = c.level == 0 && c.x in 3332..3387 && c.z in 10245..10298
    }
}
