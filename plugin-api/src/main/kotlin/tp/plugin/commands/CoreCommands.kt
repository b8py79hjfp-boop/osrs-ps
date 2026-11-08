package tp.plugin.commands

import tp.engine.commands.Impact
import tp.plugin.ContentScope
import tp.plugin.PluginContent
import tp.util.Rank

/** Commands the pipeline itself owns (commands brief §4). */
internal object CoreCommands {
    fun register(scope: ContentScope) {
        scope.command("confirm", Rank.PLAYER, Category.GENERAL, Impact.INFO) {
            description("Runs the destructive command waiting for confirmation (within 30 seconds).")
            example("::confirm")
            audit = AuditLevel.NONE
            signature { ctx ->
                val content = ctx.player.p.world.content as? PluginContent
                content?.dispatcher?.confirm(ctx.player)
                CommandResult.Done()
            }
        }
    }
}
