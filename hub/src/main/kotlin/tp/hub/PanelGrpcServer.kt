package tp.hub

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth
import tp.services.Broadcast
import tp.services.grpc.AccountRef
import tp.services.grpc.AuditQuery
import tp.services.grpc.AuditRowPb
import tp.services.grpc.AuditRows
import tp.services.grpc.ErrorReply
import tp.services.grpc.HistoryReply
import tp.services.grpc.LinesReply
import tp.services.grpc.LoginLockPb
import tp.services.grpc.LoginLockRequest
import tp.services.grpc.LoginLockState
import tp.services.grpc.LookupReply
import tp.services.grpc.OnlineReply
import tp.services.grpc.PanelActor
import tp.services.grpc.PanelAuditRequest
import tp.services.grpc.PanelBroadcastRequest
import tp.services.grpc.PanelGrpcKt
import tp.services.grpc.PanelSanctionRequest
import tp.services.grpc.PanelSetRankRequest
import tp.services.grpc.PanelTargetRequest
import tp.services.grpc.PanelUpdateRequest
import tp.services.grpc.PanelWorld
import tp.services.grpc.PanelWorlds
import tp.services.grpc.PresencePb
import tp.services.grpc.RankReply
import tp.services.grpc.SanctionRowPb
import tp.services.grpc.SanctionRows
import tp.services.grpc.SanctionsQuery
import tp.services.grpc.StaffRowPb
import tp.services.grpc.StaffRows
import tp.services.grpc.VerifyStaffReply
import tp.services.grpc.VerifyStaffRequest
import tp.services.toPb
import tp.util.Rank
import tp.util.RecordType
import tp.util.Sanction
import java.io.File
import java.net.InetSocketAddress

private val logger = KotlinLogging.logger {}

/**
 * The control panel's hub API (panel/docs/plan.md §3): a separate listener on loopback that only serves [PanelGrpcKt]
 * and only to clients whose certificate chains to the panel CA (mutual TLS, TLS 1.3). World nodes never reach it, and the
 * panel's certificate reaches nothing else. Every call re-checks the acting staff member's stored rank here; the
 * panel's own checks are a second line, not the only one. State changes and every view of IPs or alts are audited
 * with source `PANEL`.
 */
class PanelGrpcServer(
    private val hub: Hub,
    private val tls: PanelTls,
    private val port: Int = DEFAULT_PORT,
    private val bind: String = "127.0.0.1",
) : AutoCloseable {
    private var server: Server? = null

    /** The panel's certificate files (PEM; the key in PKCS#8), made by `lotus-panel certs`. */
    class PanelTls(val cert: File, val key: File, val clientCa: File)

    fun start(): PanelGrpcServer {
        val ssl =
            GrpcSslContexts
                .forServer(tls.cert, tls.key)
                .trustManager(tls.clientCa)
                .clientAuth(ClientAuth.REQUIRE)
                .protocols("TLSv1.3")
                .build()
        server =
            NettyServerBuilder
                .forAddress(InetSocketAddress(bind, port))
                .sslContext(ssl)
                .addService(Service())
                .build()
                .start()
        logger.info { "panel API on $bind:$port (mTLS)" }
        return this
    }

    override fun close() {
        server?.shutdown()
    }

    private inner class Service : PanelGrpcKt.PanelCoroutineImplBase() {
        /** The actor's stored row if it is at least [min] and not banned, else the refusal text. */
        private suspend fun actor(a: PanelActor, min: Rank): Pair<AccountRow?, String?> {
            val row = hub.db { hub.store.account(a.accountId) } ?: return null to "Unknown staff account."
            val now = System.currentTimeMillis()
            if (row.bannedUntil < 0 || row.bannedUntil > now) return null to "Account banned."
            if (!row.rights.atLeast(min)) return null to "Needs ${min.name.lowercase()} or above."
            return row to null
        }

        private suspend fun audit(row: AccountRow, a: PanelActor, action: String, target: String, detail: String, result: String) {
            hub.db { hub.store.auditEntry(row.displayName, action, target, detail, SOURCE, a.ip, result) }
        }

        private fun err(text: String?): ErrorReply = ErrorReply.newBuilder().setError(text.orEmpty()).build()

        @Suppress("ReturnCount") // guard clauses, one per refusal reason
        override suspend fun verifyStaff(request: VerifyStaffRequest): VerifyStaffReply {
            fun reply(result: String) = VerifyStaffReply.newBuilder().setResult(result).build()
            val name = request.username.trim()
            val key = "acct:${name.lowercase()}"
            if (name.isEmpty() || name.length > MAX_NAME) return reply(INVALID)
            if (hub.failures.count("ip:${request.ip}") >= hub.maxFailuresPerIp ||
                hub.failures.count(key) >= hub.maxFailuresPerAccount
            ) {
                return reply(THROTTLED)
            }
            val ipBanned =
                hub.db {
                    hub.store.ipBanned(
                        request.ip,
                    ) || hub.store.scopeBanned(RecordType.IP_BAN, request.ip, System.currentTimeMillis())
                }
            if (ipBanned) return reply(BANNED)
            val row = hub.db { hub.store.account(name) }
            if (row == null || !hub.db { hub.hashing { Passwords.verify(request.password, row.passwordHash) } }) {
                hub.failures.add("ip:${request.ip}")
                hub.failures.add(key)
                hub.db { hub.store.auditEntry(name, "panel login", name, "", SOURCE, request.ip, "failed") }
                return reply(INVALID)
            }
            if (row.bannedUntil < 0 || row.bannedUntil > System.currentTimeMillis()) return reply(BANNED)
            // The configured first ranks apply here exactly as at game login, so the first owner can sign in before ever
            // logging in to the game (commands brief §1: once per account, audited).
            val rank = hub.withBootstrap(row, name).rights
            if (!rank.isStaff) {
                hub.db { hub.store.auditEntry(row.displayName, "panel login", row.displayName, "", SOURCE, request.ip, "not staff") }
                return reply(NOT_STAFF)
            }
            hub.db { hub.store.auditEntry(row.displayName, "panel login", row.displayName, rank.stored, SOURCE, request.ip, "ok") }
            return VerifyStaffReply
                .newBuilder()
                .setResult(OK)
                .setAccountId(row.id)
                .setDisplayName(row.displayName)
                .setRank(rank.stored)
                .build()
        }

        override suspend fun getRank(request: AccountRef): RankReply {
            val row = hub.db { hub.store.account(request.accountId) } ?: return RankReply.newBuilder().setExists(false).build()
            val now = System.currentTimeMillis()
            return RankReply
                .newBuilder()
                .setExists(true)
                .setRank(row.rights.stored)
                .setBanned(row.bannedUntil < 0 || row.bannedUntil > now)
                .setDisplayName(row.displayName)
                .build()
        }

        override suspend fun audit(request: PanelAuditRequest): ErrorReply {
            val (row, refused) = actor(request.actor, Rank.MODERATOR)
            if (row == null) return err(refused)
            audit(row, request.actor, request.action, request.target, request.detail, request.result)
            return err(null)
        }

        override suspend fun queryAudit(request: AuditQuery): AuditRows {
            val (row, refused) = actor(request.actor, Rank.OWNER)
            if (row == null) return AuditRows.newBuilder().setError(refused).build()
            val q =
                AuditSearch(
                    request.by,
                    request.target,
                    request.action,
                    request.source,
                    request.text,
                    request.sinceMs,
                    request.untilMs,
                    request.beforeId,
                    request.limit.coerceIn(1, MAX_ROWS),
                )
            val rows = hub.db { hub.store.auditSearch(q) }
            return AuditRows
                .newBuilder()
                .addAllRows(
                    rows.map {
                        AuditRowPb
                            .newBuilder()
                            .setId(it.id)
                            .setAtMs(it.atMs)
                            .setActor(it.actor)
                            .setAction(it.action)
                            .setTarget(it.target)
                            .setDetail(it.detail)
                            .setSource(it.source)
                            .setIp(it.ip)
                            .setResult(it.result)
                            .build()
                    },
                ).build()
        }

        override suspend fun listOnline(request: PanelActor): OnlineReply {
            val (row, refused) = actor(request, Rank.MODERATOR)
            if (row == null) return OnlineReply.newBuilder().setError(refused).build()
            // Hidden staff stay hidden from lower ranks, as in game (commands brief §1).
            val visible =
                hub.presence.values
                    .filter { !it.invisible || row.rights.atLeast(it.rank) }
                    .sortedBy { it.name.lowercase() }
            return OnlineReply
                .newBuilder()
                .addAllPlayers(
                    visible.map {
                        PresencePb
                            .newBuilder()
                            .setAccountId(it.accountId)
                            .setName(it.name)
                            .setWorldId(it.worldId)
                            .setRank(it.rank.stored)
                            .setInvisible(it.invisible)
                            .build()
                    },
                ).build()
        }

        override suspend fun listWorlds(request: PanelActor): PanelWorlds {
            val (row, refused) = actor(request, Rank.MODERATOR)
            if (row == null) return PanelWorlds.newBuilder().setError(refused).build()
            return PanelWorlds
                .newBuilder()
                .addAllWorlds(
                    hub.worldList.values.sortedBy { it.id }.map { w ->
                        PanelWorld
                            .newBuilder()
                            .setId(w.id)
                            .setHost(w.host)
                            .setPort(w.port)
                            .setActivity(w.activity)
                            .setFlags(w.flags)
                            .setPopulation(w.population)
                            .setLocked(hub.lockMessage(w.id) != null)
                            .setLastBeatMs(hub.lastBeat[w.id] ?: 0)
                            .build()
                    },
                ).build()
        }

        override suspend fun setLoginLock(request: LoginLockRequest): ErrorReply {
            val (row, refused) = actor(request.actor, Rank.OWNER)
            if (row == null) return err(refused)
            if (request.worldId < 0) return err("Unknown world.")
            val target = if (request.worldId == 0) "all worlds" else "world ${request.worldId}"
            if (request.locked) {
                hub.loginLocks[request.worldId] = request.message.ifBlank { DEFAULT_LOCK_MESSAGE }.take(MAX_ACTIVITY)
            } else {
                hub.loginLocks.remove(request.worldId)
            }
            audit(row, request.actor, if (request.locked) "panel lock" else "panel unlock", target, request.message, "ok")
            return err(null)
        }

        override suspend fun getLoginLock(request: PanelActor): LoginLockState {
            val (row, refused) = actor(request, Rank.MODERATOR)
            if (row == null) return LoginLockState.newBuilder().setError(refused).build()
            return LoginLockState
                .newBuilder()
                .addAllLocks(
                    hub.loginLocks.entries.sortedBy { it.key }.map {
                        LoginLockPb
                            .newBuilder()
                            .setWorldId(it.key)
                            .setMessage(it.value)
                            .build()
                    },
                ).build()
        }

        override suspend fun systemUpdate(request: PanelUpdateRequest): ErrorReply {
            val (row, refused) = actor(request.actor, Rank.OWNER)
            if (row == null) return err(refused)
            if (request.seconds !in 1..MAX_UPDATE_SECONDS) return err("The timer must be 1 to $MAX_UPDATE_SECONDS seconds.")
            if (request.message.isNotBlank()) hub.broadcasts.broadcast(request.message.take(MAX_MESSAGE))
            hub.broadcasts.systemUpdate(request.seconds)
            audit(row, request.actor, "panel update", "all worlds", "${request.seconds}s ${request.message}".trim(), "ok")
            return err(null)
        }

        override suspend fun cancelUpdate(request: PanelActor): ErrorReply {
            val (row, refused) = actor(request, Rank.OWNER)
            if (row == null) return err(refused)
            // A zero timer cancels the countdown on every world (OPEN-CMD9).
            hub.broadcasts.systemUpdate(0)
            audit(row, request, "panel cancel update", "all worlds", "", "ok")
            return err(null)
        }

        override suspend fun broadcast(request: PanelBroadcastRequest): ErrorReply {
            val (row, refused) = actor(request.actor, Rank.ADMINISTRATOR)
            if (row == null) return err(refused)
            val text = request.text.trim()
            if (text.isEmpty()) return err("Empty message.")
            hub.broadcastTo(text.take(MAX_MESSAGE), request.worldId)
            val target = if (request.worldId == 0) "all worlds" else "world ${request.worldId}"
            audit(row, request.actor, "panel broadcast", target, text, "ok")
            return err(null)
        }

        override suspend fun applySanction(request: PanelSanctionRequest): ErrorReply {
            val (row, refused) = actor(request.actor, Rank.MODERATOR)
            if (row == null) return err(refused)
            val sanction = Sanction.entries.firstOrNull { it.name == request.sanction } ?: return err("Unknown action.")
            // The same code path, rank and targeting rules as the in-game command.
            val error = hub.moderation.apply(row.id, request.target, sanction, request.durationMs, request.reason)
            audit(row, request.actor, "panel ${sanction.name.lowercase()}", request.target, request.reason, error ?: "ok")
            return err(error)
        }

        override suspend fun history(request: PanelTargetRequest): HistoryReply {
            val (row, refused) = actor(request.actor, Rank.MODERATOR)
            if (row == null) return HistoryReply.newBuilder().setError(refused).build()
            val a = hub.moderation.history(row.id, request.target)
            if (row.rights.atLeast(Rank.ADMINISTRATOR)) audit(row, request.actor, "panel history", request.target, "", a.error ?: "ok")
            val b = HistoryReply.newBuilder()
            a.error?.let(b::setError)
            a.value?.forEach { b.addRecords(it.toPb()) }
            return b.build()
        }

        override suspend fun lookup(request: PanelTargetRequest): LookupReply {
            val (row, refused) = actor(request.actor, Rank.MODERATOR)
            if (row == null) return LookupReply.newBuilder().setError(refused).build()
            val a = hub.moderation.lookup(row.id, request.target)
            // Administrators see the last IP in the card: every such view is audited (brief §8).
            if (row.rights.atLeast(Rank.ADMINISTRATOR)) audit(row, request.actor, "panel lookup", request.target, "", a.error ?: "ok")
            return a.value?.toPb() ?: LookupReply.newBuilder().setError(a.error ?: "Not available.").build()
        }

        override suspend fun investigate(request: PanelTargetRequest): LinesReply {
            val min = if (request.kind == "audit") Rank.OWNER else Rank.ADMINISTRATOR
            val (row, refused) = actor(request.actor, min)
            if (row == null) return LinesReply.newBuilder().setError(refused).build()
            val a = hub.moderation.investigate(row.id, request.target, request.kind, request.filter)
            audit(row, request.actor, "panel investigate ${request.kind}", request.target, request.filter, a.error ?: "ok")
            val b = LinesReply.newBuilder()
            a.error?.let(b::setError)
            a.value?.let(b::addAllLines)
            return b.build()
        }

        override suspend fun setRank(request: PanelSetRankRequest): ErrorReply {
            val (row, refused) = actor(request.actor, Rank.OWNER)
            if (row == null) return err(refused)
            val rank = Rank.parse(request.rank) ?: return err("Unknown rank.")
            val error = hub.moderation.setRank(row.id, request.target, rank)
            audit(row, request.actor, "panel setrank", request.target, rank.stored, error ?: "ok")
            return err(error)
        }

        override suspend fun listSanctions(request: SanctionsQuery): SanctionRows {
            val (row, refused) = actor(request.actor, Rank.MODERATOR)
            if (row == null) return SanctionRows.newBuilder().setError(refused).build()
            val wanted = request.type.takeIf { it.isNotBlank() }?.let { t -> RecordType.entries.firstOrNull { it.name == t } }
            val now = System.currentTimeMillis()
            val admin = row.rights.atLeast(Rank.ADMINISTRATOR)
            val records =
                hub
                    .db { hub.store.allRecords(MAX_RECORD_SCAN) }
                    .asSequence()
                    .filter { wanted == null || it.type == wanted }
                    .filter { request.staff.isBlank() || it.actor.equals(request.staff, true) }
                    .filter { !request.activeOnly || it.active(now) }
                    .take(request.limit.coerceIn(1, MAX_ROWS))
                    .toList()
            val ids = records.map { it.accountId }.distinct()
            val names =
                hub.db {
                    ids.associateWith { id ->
                        hub.store
                            .account(id)
                            ?.displayName
                            .orEmpty()
                    }
                }
            return SanctionRows
                .newBuilder()
                .addAllRows(
                    records.map { r ->
                        // IP and device scopes are administrator-only (commands brief: privacy).
                        val shown =
                            if (admin || r.scope.isEmpty()) {
                                r
                            } else {
                                with(r) { tp.util.ModRecord(id, accountId, type, actor, reason, start, expires, "", liftedBy, liftedAt) }
                            }
                        SanctionRowPb
                            .newBuilder()
                            .setRecord(shown.toPb())
                            .setAccountName(names[r.accountId].orEmpty())
                            .build()
                    },
                ).build()
        }

        override suspend fun listStaff(request: PanelActor): StaffRows {
            val (row, refused) = actor(request, Rank.OWNER)
            if (row == null) return StaffRows.newBuilder().setError(refused).build()
            val staff = hub.db { hub.store.staffAccounts() }
            return StaffRows
                .newBuilder()
                .addAllStaff(
                    staff.map {
                        StaffRowPb
                            .newBuilder()
                            .setAccountId(it.id)
                            .setName(it.displayName)
                            .setRank(it.rights.stored)
                            .setWorldId(hub.presence[it.id]?.worldId ?: 0)
                            .build()
                    },
                ).build()
        }
    }

    companion object {
        /** Panel brief §9 `[hub] endpoint`. */
        const val DEFAULT_PORT: Int = 50051

        /**
         * Starts the panel listener when `--panel-cert`, `--panel-key` and `--panel-ca` are all given (`--panel-port`,
         * default [DEFAULT_PORT]), else returns null: the panel API is opt-in.
         */
        fun fromOptions(hub: Hub, options: Map<String, String?>): PanelGrpcServer? {
            val cert = options["--panel-cert"] ?: return null
            val key = options["--panel-key"] ?: return null
            val ca = options["--panel-ca"] ?: return null
            val port = options["--panel-port"]?.toIntOrNull() ?: DEFAULT_PORT
            return PanelGrpcServer(hub, PanelTls(File(cert), File(key), File(ca)), port).start()
        }

        private const val SOURCE = "PANEL"
        private const val OK = "OK"
        private const val INVALID = "INVALID_CREDENTIALS"
        private const val THROTTLED = "THROTTLED"
        private const val BANNED = "BANNED"
        private const val NOT_STAFF = "NOT_STAFF"
        private const val MAX_NAME = 12

        /** Rows per audit or sanctions page. */
        private const val MAX_ROWS = 500

        /** Records scanned for one sanctions listing, newest first. */
        private const val MAX_RECORD_SCAN = 5_000

        /** One broadcast line; the chatbox wraps longer text badly (our limit). */
        private const val MAX_MESSAGE = 200

        /** World switcher activity text (the client shows about 30 characters; our limit). */
        private const val MAX_ACTIVITY = 30
        private const val DEFAULT_LOCK_MESSAGE = "Maintenance"

        /** An hour: longer is a typo (our limit). */
        private const val MAX_UPDATE_SECONDS = 3_600
    }
}
