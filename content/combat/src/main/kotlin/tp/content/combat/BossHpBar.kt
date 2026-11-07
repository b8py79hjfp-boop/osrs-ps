/*
 * Adapted from OpenRune-Server `api/boss-hp-bar-plugin` BossHpBarScript (ISC licence, Copyright (c) 2025 RS Mod; see
 * DECISIONS D-069): the varps/varbits it sets and the client scripts it runs to open, update and fade the boss HUD.
 */
package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.HitEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * The boss health bar (`hpbar_hud`, docs/mechanics/boss-hp-bar.md): opens for a player the first time they damage an NPC
 * listed in `boss-hp-bar.tsv` (Wiki "Category:Bosses", matched by name), follows its hitpoints, and fades out 10 ticks
 * after that player's last hit or when the NPC dies. The player's "boss health overlay" setting
 * (`hpbar_hud_boss_disabled`) turns it off. Instanced content can open it on entry through [open].
 */
public class BossHpBar : ContentPlugin {
    override fun ContentScope.register() {
        onLogout { close(this, fade = false) }
        events.on<HitEvent> { e ->
            val npc = e.target as? NpcRef ?: return@on
            if (npc.type !in BOSSES) return@on
            val player = e.source as? PlayerRef
            if (player != null) attacked(player, npc)
            for (p in watchers(npc).toList()) {
                if (npc.hitpoints <= 0) close(p, fade = true) else update(p, npc)
            }
        }
    }

    private fun attacked(p: PlayerRef, npc: NpcRef) {
        p[LAST_HIT] = p.tick
        if (p[TRACKED] != npc) open(p, npc)
        p.timer(EXPIRY_TIMER, 1, soft = true) {
            val last = this[LAST_HIT] ?: 0L
            val tracked = this[TRACKED]
            if (tracked == null || tick - last >= EXPIRE_TICKS) close(this, fade = true)
        }
    }

    internal companion object {
        /** NPC ids that show the bar (`boss-hp-bar.tsv`). */
        val BOSSES: Set<Int> by lazy {
            val text = BossHpBar::class.java.getResource("boss-hp-bar.tsv")?.readText() ?: error("boss-hp-bar.tsv missing")
            text
                .lineSequence()
                .drop(1)
                .filter { it.isNotBlank() }
                .map { it.substringBefore('\t').toInt() }
                .toSet()
        }

        private val TRACKED = AttrKey<NpcRef>("bosshp_npc")
        private val LAST_HIT = AttrKey<Long>("bosshp_last_hit")
        private val WATCHERS = AttrKey<MutableSet<PlayerRef>>("bosshp_watchers")
        private val EXPIRY_TIMER = TimerId("bosshp_expiry")

        /** OpenRune `BossHpBarDamageContributor`: ~10 ticks (6 s) after the player's last attack. */
        const val EXPIRE_TICKS = 10L

        /** OpenRune `CLOSE_FADE_TICKS`: the fade script runs, the bar hides 2 ticks later. */
        const val FADE_TICKS = 2

        /** Client scripts (OpenRune `openScripts` / `onClose`). */
        const val CS_ALPHA = 2887
        const val CS_SHOW = 2102
        const val CS_LAYOUT = 2376
        const val CS_FADE = 2889
        const val OPAQUE = 255

        /** OpenRune `ORIGINAL_COLORS`: inner, back, sliding. */
        const val COLOUR_INNER = 0xCC0000
        const val COLOUR_BACK = 0x950000
        const val COLOUR_SLIDING = 0x00F500

        private val COMMON =
            intArrayOf(
                Components.Hpbar_Hud.HP,
                Components.Hpbar_Hud.NAME_AREA,
                Components.Hpbar_Hud.OUTER_BORDER,
                Components.Hpbar_Hud.NAME_BACKING,
                Components.Hpbar_Hud.CREATURE_NAME,
                Components.Hpbar_Hud.INNER_BORDER,
                Components.Hpbar_Hud.HEALTH_BAR_BACK,
                Components.Hpbar_Hud.HEALTH_BAR_SLIDING,
                Components.Hpbar_Hud.HEALTH_BAR_REMAINING,
                Components.Hpbar_Hud.HP_BAR_TEXT,
                Components.Hpbar_Hud.HEALTH_BAR_LOWER_THRESHOLD,
                Components.Hpbar_Hud.HEALTH_BAR_UPPER_THRESHOLD,
                Components.Hpbar_Hud.HP_BAR_1,
                Components.Hpbar_Hud.HP_BAR_2,
            )

        /**
         * Script 2102's components, in the order the interface's own onLoad (2099) passes them to its var-transmit hook
         * on varps 1682 / 1683 (OpenRune passes its common list here, which mislabels the bar in this cache).
         */
        private val SHOW =
            intArrayOf(
                Components.Hpbar_Hud.HP,
                Components.Hpbar_Hud.HP_BAR_TEXT,
                Components.Hpbar_Hud.HEALTH_BAR_BACK,
                Components.Hpbar_Hud.HEALTH_BAR_SLIDING,
                Components.Hpbar_Hud.HEALTH_BAR_REMAINING,
                Components.Hpbar_Hud.NAME_AREA,
                Components.Hpbar_Hud.CREATURE_NAME,
                Components.Hpbar_Hud.HEALTH_BAR_LOWER_THRESHOLD,
                Components.Hpbar_Hud.HEALTH_BAR_UPPER_THRESHOLD,
                Components.Hpbar_Hud.HP_BAR_1,
                Components.Hpbar_Hud.HP_BAR_2,
            )

        private val LAYOUT =
            intArrayOf(
                Components.Hpbar_Hud.UNIVERSE,
                Components.Hpbar_Hud.HPDODGERSIDE,
                Components.Hpbar_Hud.HPDODGER,
                Components.Hpbar_Hud.HP,
                Components.Hpbar_Hud.NAME_AREA,
                Components.Hpbar_Hud.HP_BAR,
                Components.Hpbar_Hud.HP_BAR_TEXT,
                Components.Hpbar_Hud.HEALTH_BAR_BACK,
                Components.Hpbar_Hud.HEALTH_BAR_SLIDING,
                Components.Hpbar_Hud.HEALTH_BAR_REMAINING,
                Components.Hpbar_Hud.CREATURE_NAME,
                Components.Hpbar_Hud.OUTER_BORDER,
                Components.Hpbar_Hud.NAME_BACKING,
                Components.Hpbar_Hud.INNER_BORDER,
                Components.Hpbar_Hud.HEALTH_BAR_LOWER_THRESHOLD,
                Components.Hpbar_Hud.HEALTH_BAR_UPPER_THRESHOLD,
                Components.Hpbar_Hud.HP_BAR_1,
                Components.Hpbar_Hud.HP_BAR_2,
                Components.Hpbar_Hud.HPCONTENTSIDE,
            )

        private fun watchers(npc: NpcRef): MutableSet<PlayerRef> = npc[WATCHERS] ?: HashSet<PlayerRef>().also { npc[WATCHERS] = it }

        /** Shows the bar for [p] bound to [npc] (OpenRune `onOpen`). */
        fun open(p: PlayerRef, npc: NpcRef) {
            if (p.varbit(Varbits.HPBAR_HUD_BOSS_DISABLED) == 1) return
            p[TRACKED]?.let { watchers(it).remove(p) }
            p[TRACKED] = npc
            watchers(npc).add(p)
            p.setVarp(Varps.HPBAR_HUD_NPC, npc.type)
            p.setVarbit(Varbits.HPBAR_HUD_BASEHP, npc.maxHitpoints)
            p.setVarbit(Varbits.HPBAR_HUD_HP, npc.hitpoints.coerceAtLeast(0))
            p.setVarbit(Varbits.HPBAR_HUD_BOSS, 1)
            p.ifColour(Components.Hpbar_Hud.INNER, COLOUR_INNER)
            p.ifColour(Components.Hpbar_Hud.HEALTH_BAR_BACK, COLOUR_BACK)
            p.ifColour(Components.Hpbar_Hud.HEALTH_BAR_SLIDING, COLOUR_SLIDING)
            p.ifHide(Components.Hpbar_Hud.HP, false)
            // Varps reach the client at the end of the tick, after messages sent now; the draw scripts read the HP
            // varbits, so they run on the next tick (OpenRune sends varps immediately).
            p.timer(OPEN_TIMER, 1, soft = true) {
                stopTimer(OPEN_TIMER)
                if (this[TRACKED] == null) return@timer
                clientScript(CS_ALPHA, *COMMON.toTypedArray(), OPAQUE)
                clientScript(CS_SHOW, *SHOW.toTypedArray(), 0)
                clientScript(CS_LAYOUT, *LAYOUT.toTypedArray())
            }
        }

        /** Pushes [npc]'s hitpoints (OpenRune `onUpdate`). */
        fun update(p: PlayerRef, npc: NpcRef) {
            if (p.varbit(Varbits.HPBAR_HUD_BOSS_DISABLED) == 1) return
            p.setVarbit(Varbits.HPBAR_HUD_BASEHP, npc.maxHitpoints)
            p.setVarbit(Varbits.HPBAR_HUD_HP, npc.hitpoints.coerceAtLeast(0))
        }

        /** Hides the bar: a 2-tick fade, or at once (OpenRune `onClose`). */
        fun close(p: PlayerRef, fade: Boolean) {
            val npc = p[TRACKED] ?: return
            p[TRACKED] = null
            watchers(npc).remove(p)
            p.stopTimer(EXPIRY_TIMER)
            if (!fade) return p.ifHide(Components.Hpbar_Hud.HP, true)
            p.clientScript(CS_FADE, *COMMON.toTypedArray(), 0)
            p.timer(FADE_TIMER, FADE_TICKS, soft = true) {
                stopTimer(FADE_TIMER)
                if (this[TRACKED] == null) {
                    ifHide(Components.Hpbar_Hud.HP, true)
                    setVarbit(Varbits.HPBAR_HUD_BOSS, 0)
                }
            }
        }

        private val FADE_TIMER = TimerId("bosshp_fade")
        private val OPEN_TIMER = TimerId("bosshp_open")
    }
}
