package tp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import tp.engine.AccountBridge
import tp.engine.AccountProfile
import tp.engine.World
import tp.engine.entity.Player
import tp.protocol.api.LoginRejection
import tp.protocol.api.LoginRequest
import tp.services.HubServices
import tp.services.LoginResult
import tp.services.Rights
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

/**
 * The world's [AccountBridge] to the hub. Hub calls run on virtual threads (never the world thread). Saves for one
 * account are chained in order, so an autosave still in flight always lands before that account's logout save, which
 * the hub writes durably before releasing the session lock (brief §7 dupe prevention).
 */
class HubAccountBridge(private val hub: HubServices, private val worldId: Int) : AccountBridge {
    private val io = Executors.newVirtualThreadPerTaskExecutor()
    private val chains = ConcurrentHashMap<Long, CompletableFuture<Unit>>()

    override fun authorize(request: LoginRequest, world: World) {
        io.execute {
            val grant =
                runCatching { runBlocking { hub.accounts.login(request.username, request.password, worldId, request.address) } }
                    .onFailure { logger.warn(it) { "hub login failed for ${request.username}" } }
                    .getOrNull()
            when (grant?.result) {
                LoginResult.OK -> {
                    world.admitAuthorized(request, AccountProfile(grant.accountId, grant.displayName, staffLevel(grant.rights), grant.save))
                }

                LoginResult.INVALID_CREDENTIALS -> {
                    request.reject(LoginRejection.INVALID_CREDENTIALS)
                }

                LoginResult.ALREADY_ONLINE -> {
                    request.reject(LoginRejection.ALREADY_ONLINE)
                }

                LoginResult.BANNED -> {
                    request.reject(LoginRejection.BANNED)
                }

                LoginResult.THROTTLED -> {
                    request.reject(LoginRejection.TOO_MANY_ATTEMPTS)
                }

                LoginResult.SAVE_PENDING -> {
                    request.reject(LoginRejection.HOP_BLOCKED)
                }

                LoginResult.SERVER_ERROR, null -> {
                    request.reject(LoginRejection.SERVER_ERROR)
                }
            }
        }
    }

    override fun saved(player: Player, save: String, final: Boolean) {
        val account = player.accountId
        if (account == 0L) return
        val started = System.nanoTime()
        chains.compute(account) { _, previous ->
            (previous ?: CompletableFuture.completedFuture(Unit)).thenApplyAsync({
                runCatching {
                    runBlocking {
                        if (final) hub.accounts.logout(account, worldId, save) else hub.saves.save(account, worldId, save)
                    }
                }.onFailure { logger.error(it) { "save failed for account $account (final=$final)" } }
                SaveMetrics.record(System.nanoTime() - started)
            }, io)
        }
        if (final) chains[account]?.whenComplete { _, _ -> chains.remove(account) }
    }

    private fun staffLevel(rights: Rights): Int =
        when (rights) {
            Rights.PLAYER -> 0
            Rights.MODERATOR -> 1
            Rights.ADMIN -> 2
        }
}

/** Save latency (brief §12: alert on save latency). */
object SaveMetrics {
    @Volatile var lastNanos: Long = 0
        private set

    @Volatile var maxNanos: Long = 0
        private set

    fun record(nanos: Long) {
        lastNanos = nanos
        if (nanos > maxNanos) maxNanos = nanos
    }
}
