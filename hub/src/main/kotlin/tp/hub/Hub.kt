package tp.hub

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tp.services.AccountService
import tp.services.Broadcast
import tp.services.BroadcastService
import tp.services.ChannelSettings
import tp.services.CollectPart
import tp.services.ExchangeService
import tp.services.FriendEntry
import tp.services.GeOffer
import tp.services.HiscoreEntry
import tp.services.HiscoreService
import tp.services.HubServices
import tp.services.LoginGrant
import tp.services.LoginResult
import tp.services.ModAnswer
import tp.services.ModerationService
import tp.services.PresenceEntry
import tp.services.SaveService
import tp.services.SocialEvent
import tp.services.SocialService
import tp.services.TpBuyResult
import tp.services.TpCollect
import tp.services.TpListing
import tp.services.TpTrade
import tp.services.TradingPostService
import tp.services.WorldDirectory
import tp.services.WorldInfo
import tp.util.ModRecord
import tp.util.PlayerCard
import tp.util.Rank
import tp.util.RecordType
import tp.util.Sanction
import tp.util.Sanctions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

/** Hub settings. */
class HubConfig(
    /** Dev: unknown usernames are registered on first login (bots, local testing). Off in production. */
    val autoRegister: Boolean = false,
    /** Login throttling (brief §12): failed attempts allowed per window, per IP and per account. */
    val maxFailuresPerIp: Int = 10,
    val maxFailuresPerAccount: Int = 5,
    val failureWindowMs: Long = 60_000,
    /** How long a login waits for the previous world's logout save before answering SAVE_PENDING. */
    val pendingSaveWaitMs: Long = 10_000,
    /** Account names given administrator rights at login (`--admins`; deprecated: [bootstrap] supersedes it). */
    val admins: Set<String> = emptySet(),
    /**
     * Commands brief §1: account name (normalised: lower case, spaces for underscores) to its first rank, from
     * `[ranks.bootstrap]`. Applied once per account at its next login and audited.
     */
    val bootstrap: Map<String, Rank> = emptyMap(),
)

/**
 * The hub's services in one process (brief §7): login authority with per-account session locks, durable saves,
 * world directory, social, GE skeleton, hiscores, moderation, broadcasts. Served over gRPC by `HubGrpcServer`, or
 * used directly by a world-node running an embedded hub (dev). Blocking store calls run on virtual threads.
 */
class Hub(internal val store: HubStore, private val config: HubConfig = HubConfig()) : HubServices, AutoCloseable {
    private val io: CoroutineDispatcher = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()

    internal suspend fun <T> db(block: () -> T): T = withContext(io) { block() }

    /** [account] with its bootstrap rank applied (set and audited) when one is due; else unchanged. */
    internal suspend fun withBootstrap(account: AccountRow, name: String): AccountRow {
        val boot = bootstrapRank(name) ?: return account
        if (!bootstrapDue(account, boot)) return account
        return db {
            store.setRights(account.id, boot)
            store.audit("hub", BOOTSTRAP, "account:${account.id}", "${account.displayName} ${account.rights.stored} -> ${boot.stored}")
            store.account(account.id)
        } ?: account
    }

    /** Once per account: a later demotion is not undone by the next boot (commands brief §1). */
    private suspend fun bootstrapDue(account: AccountRow, rank: Rank): Boolean =
        account.rights != rank && !db { store.audited(BOOTSTRAP, "account:${account.id}") }

    /** [name]'s bootstrap rank: `[ranks.bootstrap]`, else ADMINISTRATOR for a `--admins` name. */
    internal fun bootstrapRank(name: String): Rank? {
        val key = name.trim().replace('_', ' ').lowercase()
        config.bootstrap[key]?.let { return it }
        // D-088: a trailing `*` is a name prefix (bot accounts on dev worlds); the longest prefix wins.
        val pattern =
            config.bootstrap.entries
                .filter { (k, _) -> k.endsWith('*') && key.startsWith(k.dropLast(1)) }
                .maxByOrNull { it.key.length }
        if (pattern != null) return pattern.value
        return Rank.ADMINISTRATOR.takeIf { config.admins.any { it.replace('_', ' ').equals(key, ignoreCase = true) } }
    }

    /** Session locks: account → world holding it. */
    private val online = ConcurrentHashMap<Long, Int>()

    /** Every online account with its world, rank and hidden flag (pushed to every world, commands brief §5). */
    internal val presence = ConcurrentHashMap<Long, PresenceEntry>()

    private fun presenceChanged(online: List<PresenceEntry> = emptyList(), offline: List<Long> = emptyList()) {
        val event = Broadcast.Presence(online, offline, snapshot = false)
        broadcastSinks.forEach { runCatching { it(event) } }
    }

    /**
     * Argon2id needs 19 MiB and ~50 ms of CPU per hash. A login storm (world restart, botswarm) would otherwise run
     * thousands at once and exhaust memory, so at most one hash per core runs; the rest wait on their virtual thread.
     */
    private val hashPermits = java.util.concurrent.Semaphore(Runtime.getRuntime().availableProcessors())

    internal fun <T> hashing(block: () -> T): T {
        hashPermits.acquire()
        try {
            return block()
        } finally {
            hashPermits.release()
        }
    }

    private val onlineNames = ConcurrentHashMap<String, Long>()

    /** Logout saves in flight: account → completes when durable. */
    private val pendingLogout = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

    internal val failures = Throttle(config.failureWindowMs)
    internal val worldList = ConcurrentHashMap<Int, WorldInfo>()

    /** Last registration or heartbeat per world, epoch ms (panel dashboard). */
    internal val lastBeat = ConcurrentHashMap<Int, Long>()

    /** Maintenance mode (panel brief §3): world id (0 = every world) to the message shown as the world's activity. */
    internal val loginLocks = ConcurrentHashMap<Int, String>()

    /** The maintenance message for [worldId] while logins there are locked, else null. */
    fun lockMessage(worldId: Int): String? = loginLocks[worldId] ?: loginLocks[0]

    /** A server message to one world ([worldId]) or all (0): the panel's broadcast. */
    internal fun broadcastTo(text: String, worldId: Int) {
        broadcastSinks.forEach { runCatching { it(Broadcast.Message(text, worldId)) } }
    }

    internal val maxFailuresPerIp: Int get() = config.maxFailuresPerIp
    internal val maxFailuresPerAccount: Int get() = config.maxFailuresPerAccount
    private val socialSinks = ConcurrentHashMap<Int, CopyOnWriteArrayList<(SocialEvent) -> Unit>>()
    private val kickSinks = ConcurrentHashMap<Int, CopyOnWriteArrayList<(Long) -> Unit>>()
    private val broadcastSinks = CopyOnWriteArrayList<(Broadcast) -> Unit>()

    // ---------------------------------------------------------------- accounts

    override val accounts: AccountService =
        object : AccountService {
            @Suppress("ReturnCount") // guard clauses, one per refusal reason
            override suspend fun login(username: String, password: String, worldId: Int, ip: String, device: String): LoginGrant {
                val name = username.trim()
                if (name.isEmpty() || name.length > MAX_NAME) return LoginGrant(LoginResult.INVALID_CREDENTIALS)
                if (failures.count("ip:$ip") >= config.maxFailuresPerIp ||
                    failures.count("acct:${name.lowercase()}") >= config.maxFailuresPerAccount
                ) {
                    return LoginGrant(LoginResult.THROTTLED)
                }
                if (db { store.ipBanned(ip) || store.scopeBanned(RecordType.IP_BAN, ip, System.currentTimeMillis()) }) {
                    return LoginGrant(LoginResult.BANNED)
                }
                var row = db { store.account(name) }
                if (row == null &&
                    config.autoRegister
                ) {
                    row = db { store.createAccount(name, hashing { Passwords.hash(password) }, Rank.PLAYER) }
                }
                if (row == null || !db { hashing { Passwords.verify(password, row.passwordHash) } }) {
                    failures.add("ip:$ip")
                    failures.add("acct:${name.lowercase()}")
                    return LoginGrant(LoginResult.INVALID_CREDENTIALS)
                }
                if (row.bannedUntil < 0 || row.bannedUntil > System.currentTimeMillis()) return LoginGrant(LoginResult.BANNED)
                if (db {
                        store.scopeBanned(
                            RecordType.DEVICE_BAN,
                            device,
                            System.currentTimeMillis(),
                        )
                    }
                ) {
                    return LoginGrant(LoginResult.BANNED)
                }
                // Panel maintenance mode: staff only (the bootstrap rank counts, so the first developer can get in).
                if (lockMessage(worldId) != null && !maxOf(row.rights, bootstrapRank(name) ?: Rank.PLAYER).isStaff) {
                    return LoginGrant(LoginResult.LOCKED)
                }
                // Dupe prevention: the previous world's logout save must be durable first (brief §7).
                pendingLogout[row.id]?.let { pending ->
                    if (withTimeoutOrNull(config.pendingSaveWaitMs) { pending.await() } == null) return LoginGrant(LoginResult.SAVE_PENDING)
                }
                if (online.putIfAbsent(row.id, worldId) != null) return LoginGrant(LoginResult.ALREADY_ONLINE)
                onlineNames[row.username] = row.id
                val save = db { store.loadSave(row.id) }
                db {
                    store.setLastIp(row.id, ip)
                    val at = System.currentTimeMillis()
                    store.recordSeen(row.id, SEEN_IP, ip, at)
                    store.recordSeen(row.id, SEEN_DEVICE, device, at)
                    store.audit(row.displayName, "login", "world $worldId", ip)
                }
                notifyFriendStatus(row.displayName, worldId)
                db { clans.presenceChanged(row.id) }
                // Commands brief §1: the configured first ranks, once per account, after every login check passed.
                val rank = withBootstrap(row, name).rights
                val entry = PresenceEntry(row.id, row.displayName, worldId, rank, invisible = false)
                presence[row.id] = entry
                presenceChanged(online = listOf(entry))
                // Commands brief §7: sanctions set while offline apply now.
                val sanctions = Sanctions.of(db { store.records(row.id) }, System.currentTimeMillis())
                val donator = tp.util.DonatorTier.forCents(db { store.donationTotal(row.id) })
                return LoginGrant(LoginResult.OK, row.id, row.displayName, rank, save, sanctions, donator)
            }

            override suspend fun logout(accountId: Long, worldId: Int, save: String) {
                val pending = CompletableDeferred<Unit>()
                pendingLogout[accountId] = pending
                try {
                    // Blank: the world refused the login after the hub accepted it; only the lock is released.
                    if (save.isNotBlank()) db { store.writeSave(accountId, save) }
                } finally {
                    friendChats.leave(accountId)
                    if (online.remove(accountId, worldId)) {
                        if (presence.remove(accountId) != null) presenceChanged(offline = listOf(accountId))
                        val row = db { store.account(accountId) }
                        if (row != null) {
                            onlineNames.remove(row.username)
                            notifyFriendStatus(row.displayName, 0)
                        }
                        db { clans.presenceChanged(accountId) }
                    }
                    pendingLogout.remove(accountId, pending)
                    pending.complete(Unit)
                }
            }

            override suspend fun register(username: String, password: String, ip: String): String? {
                if (!NAME.matches(username)) return "Names are 1-12 letters, numbers, spaces, - or _."
                if (password.length !in MIN_PASSWORD..MAX_PASSWORD) return "Passwords are $MIN_PASSWORD-$MAX_PASSWORD characters."
                if (db { store.ipBanned(ip) }) return "Registration is not available."
                // A `::rename`d display name is taken too, so two accounts never show the same name.
                if (db { store.accountByDisplayName(username) } != null) return "That name is taken."
                val row =
                    db { store.createAccount(username, hashing { Passwords.hash(password) }, Rank.PLAYER) }
                        ?: return "That name is taken."
                db { store.audit(username, "register", row.id.toString(), ip) }
                return null
            }

            @Suppress("ReturnCount") // one guard per refusal reason
            override suspend fun changePassword(accountId: Long, current: String, new: String): String? {
                val row = db { store.account(accountId) } ?: return "Unknown account."
                val key = "acct:${row.username}"
                // Throttled with the login failures: guessing the current password here is no cheaper than at login.
                if (failures.count(key) >= config.maxFailuresPerAccount) return "Too many attempts. Try again later."
                if (new.length !in MIN_PASSWORD..MAX_PASSWORD) return "Passwords are $MIN_PASSWORD-$MAX_PASSWORD characters."
                if (!hashing { Passwords.verify(current, row.passwordHash) }) {
                    failures.add(key)
                    return "That is not your current password."
                }
                val hash = hashing { Passwords.hash(new) }
                db {
                    store.setPasswordHash(row.id, hash)
                    store.audit(row.displayName, "change-password", "account:${row.id}", "")
                }
                return null
            }
        }

    override val saves: SaveService =
        object : SaveService {
            override suspend fun save(accountId: Long, worldId: Int, save: String) {
                // Only the world holding the session lock may write (a stale world's autosave can't overwrite).
                if (online[accountId] == worldId) db { store.writeSave(accountId, save) }
            }
        }

    // ---------------------------------------------------------------- worlds

    override val worlds: WorldDirectory =
        object : WorldDirectory {
            override suspend fun register(world: WorldInfo) {
                worldList[world.id] = world
                lastBeat[world.id] = System.currentTimeMillis()
                logger.info { "world ${world.id} registered at ${world.host}:${world.port}" }
            }

            override suspend fun heartbeat(worldId: Int, population: Int) {
                val w = worldList[worldId] ?: return
                worldList[worldId] = WorldInfo(w.id, w.host, w.port, w.activity, w.flags, population)
                lastBeat[worldId] = System.currentTimeMillis()
            }

            // A locked world shows the maintenance message as its activity (panel brief §3).
            override suspend fun list(): List<WorldInfo> =
                worldList.values.sortedBy { it.id }.map { w ->
                    lockMessage(w.id)?.let { WorldInfo(w.id, w.host, w.port, it, w.flags, w.population) } ?: w
                }
        }

    // ---------------------------------------------------------------- social

    private fun notifyFriendStatus(name: String, worldId: Int) {
        // Everyone online who has [name] as a friend gets the status (lists are small; scan online accounts).
        for ((id, world) in online) {
            val friends = store.friends(id)
            if (friends.any { it.equals(name, ignoreCase = true) }) deliver(world, SocialEvent.FriendStatus(id, name, worldId))
        }
    }

    private fun deliver(worldId: Int, event: SocialEvent) {
        socialSinks[worldId]?.forEach { runCatching { it(event) } }
    }

    private val friendChats = FriendChats(store, online, ::deliver)
    private val clans by lazy { Clans(store, online, ::deliver) }

    override val social: SocialService =
        object : SocialService {
            override suspend fun joinChannel(accountId: Long, owner: String): String? = db { friendChats.join(accountId, owner) }

            override suspend fun leaveChannel(accountId: Long) = db { friendChats.leave(accountId) }

            override suspend fun channelMessage(accountId: Long, text: String): String? =
                db { friendChats.message(accountId, text.take(MAX_MESSAGE)) }

            override suspend fun channelKick(accountId: Long, name: String): String? = db { friendChats.kick(accountId, name) }

            override suspend fun channelSettings(accountId: Long): ChannelSettings = db { friendChats.settings(accountId) }

            override suspend fun setChannelSettings(accountId: Long, settings: ChannelSettings) =
                db { friendChats.setSettings(accountId, settings) }

            override suspend fun setChannelRank(accountId: Long, name: String, rank: Int): String? =
                db { friendChats.setRank(accountId, name, rank) }

            override suspend fun clanFound(accountId: Long, name: String): String? = db { clans.found(accountId, name) }

            override suspend fun clanInvite(accountId: Long, name: String): String? = db { clans.invite(accountId, name) }

            override suspend fun clanAccept(accountId: Long): String? = db { clans.accept(accountId) }

            override suspend fun clanLeave(accountId: Long): String? = db { clans.leave(accountId) }

            override suspend fun clanSetRank(accountId: Long, name: String, rank: Int): String? =
                db {
                    clans.setRank(
                        accountId,
                        name,
                        rank,
                    )
                }

            override suspend fun clanKick(accountId: Long, name: String, ban: Boolean): String? = db { clans.kick(accountId, name, ban) }

            override suspend fun clanSetRules(accountId: Long, rules: tp.services.ClanRules): String? =
                db {
                    clans.setRules(
                        accountId,
                        rules,
                    )
                }

            override suspend fun clanJoinGuest(accountId: Long, clanName: String): String? = db { clans.joinGuest(accountId, clanName) }

            override suspend fun clanLeaveGuest(accountId: Long) = db { clans.leaveGuest(accountId) }

            override suspend fun clanMessage(accountId: Long, guest: Boolean, text: String): String? =
                db { clans.message(accountId, guest, text.take(MAX_MESSAGE)) }

            override suspend fun friends(accountId: Long): List<FriendEntry> =
                db {
                    val ranks = store.channelRanks(accountId)
                    store.friends(accountId).map { name ->
                        FriendEntry(name, onlineNames[name.lowercase()]?.let { online[it] } ?: 0, ranks[name.lowercase()] ?: 0)
                    }
                }

            override suspend fun ignores(accountId: Long): List<String> = db { store.ignores(accountId) }

            override suspend fun addFriend(accountId: Long, name: String): String? {
                val target = db { store.account(name) } ?: return "Unable to add friend - unknown player."
                if (target.id == accountId) return "You can't add yourself to your own friend list."
                if (db { store.friends(accountId) }.size >= MAX_FRIENDS) return "Your friend list is full."
                db { store.setFriend(accountId, target.displayName, true) }
                return null
            }

            override suspend fun removeFriend(accountId: Long, name: String) = db { store.setFriend(accountId, name, false) }

            override suspend fun addIgnore(accountId: Long, name: String): String? {
                val target = db { store.account(name) } ?: return "Unable to add name - unknown player."
                db { store.setIgnore(accountId, target.displayName, true) }
                return null
            }

            override suspend fun removeIgnore(accountId: Long, name: String) = db { store.setIgnore(accountId, name, false) }

            override suspend fun privateMessage(fromAccount: Long, toName: String, text: String): String? {
                val from = db { store.account(fromAccount) } ?: return "Unable to send message."
                if (from.mutedUntil < 0 || from.mutedUntil > System.currentTimeMillis()) return "You are muted."
                val target = db { store.account(toName) } ?: return "That player is currently offline."
                val world = online[target.id] ?: return "That player is currently offline."
                if (db { store.ignores(target.id) }.any { it.equals(from.displayName, ignoreCase = true) }) {
                    return "That player is currently offline."
                }
                deliver(world, SocialEvent.PrivateMessage(target.id, from.displayName, from.rights, text.take(MAX_MESSAGE)))
                return null
            }

            override fun subscribe(worldId: Int, sink: (SocialEvent) -> Unit): AutoCloseable {
                val list = socialSinks.getOrPut(worldId) { CopyOnWriteArrayList() }
                list += sink
                return AutoCloseable { list -= sink }
            }
        }

    // ---------------------------------------------------------------- Grand Exchange skeleton

    override val exchange: ExchangeService = Exchange(store, ::db)

    override val tradingPost: TradingPostService = TradingPost(store, ::db)

    // ---------------------------------------------------------------- hiscores, moderation, broadcasts

    override val hiscores: HiscoreService =
        object : HiscoreService {
            override suspend fun submit(
                accountId: Long,
                name: String,
                xpTenths: IntArray,
            ) = db { store.submitHiscores(accountId, name, xpTenths) }

            override suspend fun top(stat: Int, count: Int): List<HiscoreEntry> = db { store.topHiscores(stat, count.coerceIn(1, MAX_TOP)) }
        }

    override val moderation: ModerationService =
        object : ModerationService {
            override suspend fun audit(actor: String, action: String, target: String, detail: String) {
                db { store.audit(actor, action, target, detail) }
            }

            @Suppress("ReturnCount") // guard clauses, one per refusal reason
            override suspend fun apply(staffAccount: Long, target: String, sanction: Sanction, durationMs: Long, reason: String): String? {
                val staff = db { store.account(staffAccount) } ?: return "Unknown staff account."
                if (!staff.rights.atLeast(sanction.minRank)) return "You don't have permission to do that."
                val row = db { store.accountByName(target) } ?: return "There is no account called $target."
                // Commands brief §1: checked here against the stored rank, so offline targets obey it too.
                if (row.id != staff.id && !staff.rights.canTarget(row.rights)) return "You can't do that to ${row.displayName}."
                val now = System.currentTimeMillis()
                sanction.lifts?.let { type -> return lift(staff, row, sanction, type, now, reason) }
                val type = sanction.record ?: return "Unknown action."
                val expires =
                    when {
                        !type.lasting -> 0L
                        durationMs == ModRecord.PERMANENT -> ModRecord.PERMANENT
                        durationMs <= 0 -> return "That needs a duration."
                        else -> now + durationMs
                    }
                val scope =
                    when (type) {
                        RecordType.IP_BAN -> {
                            db { store.lastIp(row.id) } ?: return "${row.displayName} has no known IP address."
                        }

                        RecordType.DEVICE_BAN -> {
                            db { store.seen(row.id, SEEN_DEVICE).firstOrNull() } ?: return "${row.displayName}'s client sent no device id."
                        }

                        else -> {
                            ""
                        }
                    }
                db {
                    store.addRecord(ModRecord(0, row.id, type, staff.displayName, reason, now, expires, scope))
                    // D-088: the account columns stay the fast login and chat checks, written alongside the record.
                    when (type) {
                        RecordType.MUTE -> store.setMuted(row.id, expires)
                        RecordType.BAN -> store.setBanned(row.id, expires)
                        RecordType.IP_BAN -> if (expires == ModRecord.PERMANENT) store.setIpBan(scope, true)
                        else -> Unit
                    }
                    store.audit(staff.displayName, sanction.name, row.displayName, "until=$expires reason=$reason")
                }
                pushSanctions(row.id, now)
                val world = online[row.id]
                if (world != null && type == RecordType.WARN) deliver(world, SocialEvent.Warned(row.id, reason))
                if (world != null && type in KICKING) kickSinks[world]?.forEach { runCatching { it(row.id) } }
                return null
            }

            private suspend fun lift(
                staff: AccountRow,
                row: AccountRow,
                sanction: Sanction,
                type: RecordType,
                now: Long,
                reason: String,
            ): String? {
                val lifted =
                    db {
                        val n = store.liftRecords(row.id, type, staff.displayName, now)
                        when (type) {
                            RecordType.MUTE -> store.setMuted(row.id, 0)
                            RecordType.BAN -> store.setBanned(row.id, 0)
                            RecordType.IP_BAN -> store.lastIp(row.id)?.let { store.setIpBan(it, false) }
                            else -> Unit
                        }
                        if (n > 0) store.audit(staff.displayName, sanction.name, row.displayName, "lifted=$n reason=$reason")
                        n
                    }
                if (lifted == 0) return "${row.displayName} has no active ${type.name.lowercase().replace('_', ' ')}."
                pushSanctions(row.id, now)
                return null
            }

            /** Commands brief §7: the change applies at once on the world the account is on. */
            private suspend fun pushSanctions(accountId: Long, now: Long) {
                val world = online[accountId] ?: return
                val sanctions = Sanctions.of(db { store.records(accountId) }, now)
                deliver(world, SocialEvent.Sanctioned(accountId, sanctions))
            }

            override suspend fun history(staffAccount: Long, target: String): ModAnswer<List<ModRecord>> {
                val staff = db { store.account(staffAccount) } ?: return ModAnswer("Unknown staff account.", null)
                if (!staff.rights.isStaff) return ModAnswer("You don't have permission to do that.", null)
                val row = db { store.accountByName(target) } ?: return ModAnswer("There is no account called $target.", null)
                val records = db { store.records(row.id) }
                val admin = staff.rights.atLeast(Rank.ADMINISTRATOR)
                // §8: IP addresses and device ids are for administrators only, and every view of them is audited.
                if (admin &&
                    records.any { it.scope.isNotEmpty() }
                ) {
                    db { store.audit(staff.displayName, "view-history-scopes", row.displayName, "") }
                }
                val shown =
                    if (admin) {
                        records
                    } else {
                        records.map {
                            with(it) { ModRecord(id, accountId, type, actor, reason, start, expires, "", liftedBy, liftedAt) }
                        }
                    }
                return ModAnswer(null, shown)
            }

            override suspend fun investigate(staffAccount: Long, target: String, kind: String, filter: String): ModAnswer<List<String>> {
                val staff = db { store.account(staffAccount) } ?: return ModAnswer("Unknown staff account.", null)
                // §8: IP addresses, device ids and alt links are administrator and above, and every view is audited.
                if (!staff.rights.atLeast(Rank.ADMINISTRATOR)) return ModAnswer("You don't have permission to do that.", null)
                if (kind == "audit") return auditLog(staff, target, filter)
                val row = db { store.accountByName(target) } ?: return ModAnswer("There is no account called $target.", null)
                val lines =
                    db {
                        when (kind) {
                            "ips" -> store.seen(row.id, SEEN_IP)
                            "alts" -> alts(row.id).mapNotNull { store.account(it)?.displayName }
                            else -> null
                        }
                    } ?: return ModAnswer("Unknown investigation.", null)
                db { store.audit(staff.displayName, "view-$kind", row.displayName, "${lines.size} rows") }
                return ModAnswer(null, lines)
            }

            /** `::audit [player|staff] [filter]` (owner): the newest matching rows; viewing is itself audited. */
            private suspend fun auditLog(staff: AccountRow, who: String, filter: String): ModAnswer<List<String>> {
                if (!staff.rights.atLeast(Rank.OWNER)) return ModAnswer("You don't have permission to do that.", null)
                val rows = db { store.auditQuery(who, filter, AUDIT_ROWS) }
                db { store.audit(staff.displayName, "view-audit", who.ifBlank { "-" }, filter) }
                return ModAnswer(null, rows)
            }

            override suspend fun setRank(staffAccount: Long, target: String, rank: Rank): String? {
                val staff = db { store.account(staffAccount) } ?: return "Unknown staff account."
                val row = db { store.accountByName(target) } ?: return "There is no account called $target."
                if (row.id == staff.id) return "You can't change your own rank."
                // §1: owners set up to administrator on lower ranks; developers set any rank on non-developers.
                if (!staff.rights.canSetRank(row.rights, rank)) return "You can't give ${row.displayName} that rank."
                if (row.rights == rank) return "${row.displayName} is already ${rank.name.lowercase()}."
                db {
                    store.setRights(row.id, rank)
                    store.audit(staff.displayName, "set-rank", row.displayName, "${row.rights.stored} -> ${rank.stored}")
                }
                // §8: rank changes take effect at once, on the player's world and in every world's presence.
                online[row.id]?.let { world ->
                    deliver(world, SocialEvent.RankChanged(row.id, rank))
                    presence[row.id]?.let { e ->
                        val updated = PresenceEntry(e.accountId, e.name, e.worldId, rank, e.invisible)
                        presence[row.id] = updated
                        presenceChanged(online = listOf(updated))
                    }
                }
                return null
            }

            override suspend fun recordDonation(
                staffAccount: Long,
                target: String,
                cents: Long,
                note: String,
            ): ModAnswer<tp.util.DonatorTier> {
                val staff = db { store.account(staffAccount) } ?: return ModAnswer("Unknown staff account.", null)
                if (!staff.rights.atLeast(Rank.OWNER)) return ModAnswer("You don't have permission to do that.", null)
                if (cents <= 0 || cents > MAX_DONATION_CENTS) return ModAnswer("That amount isn't valid.", null)
                val row = db { store.accountByName(target) } ?: return ModAnswer("There is no account called $target.", null)
                val tier =
                    db {
                        store.addDonation(row.id, cents, staff.displayName, note, System.currentTimeMillis())
                        store.audit(staff.displayName, "donation", row.displayName, "cents=$cents note=$note")
                        tp.util.DonatorTier.forCents(store.donationTotal(row.id))
                    }
                online[row.id]?.let { world -> deliver(world, SocialEvent.DonatorChanged(row.id, tier)) }
                return ModAnswer(null, tier)
            }

            override suspend fun rename(staffAccount: Long, target: String, newName: String): String? {
                val staff = db { store.account(staffAccount) } ?: return "Unknown staff account."
                if (!staff.rights.atLeast(Rank.OWNER)) return "You don't have permission to do that."
                val row = db { store.accountByName(target) } ?: return "There is no account called $target."
                val name = newName.trim()
                if (!NAME.matches(name)) return "Names are 1-12 letters, numbers, spaces, - or _."
                val taken = db { (store.account(name) ?: store.accountByDisplayName(name))?.takeIf { it.id != row.id } }
                if (taken != null) return "The name $name is taken."
                val now = System.currentTimeMillis()
                db {
                    store.setDisplayName(row.id, name)
                    store.addRecord(ModRecord(0, row.id, RecordType.RENAME, staff.displayName, "${row.displayName} -> $name", now, 0))
                    store.audit(staff.displayName, "rename", row.displayName, name)
                }
                // The client shows the name it logged in with: a relog shows the new one.
                online[row.id]?.let { world -> kickSinks[world]?.forEach { runCatching { it(row.id) } } }
                return null
            }

            /** Accounts sharing an IP or a device with [accountId]. Blocking (inside db). */
            private fun alts(accountId: Long): List<Long> =
                (
                    store.seen(accountId, SEEN_IP).flatMap { store.accountsSeen(SEEN_IP, it) } +
                        store.seen(accountId, SEEN_DEVICE).flatMap { store.accountsSeen(SEEN_DEVICE, it) }
                ).distinct()
                    .filter { it != accountId }

            override suspend fun lookup(staffAccount: Long, target: String): ModAnswer<PlayerCard> {
                val staff = db { store.account(staffAccount) } ?: return ModAnswer("Unknown staff account.", null)
                if (!staff.rights.isStaff) return ModAnswer("You don't have permission to do that.", null)
                val row = db { store.accountByName(target) } ?: return ModAnswer("There is no account called $target.", null)
                val now = System.currentTimeMillis()
                val records = db { store.records(row.id) }
                val admin = staff.rights.atLeast(Rank.ADMINISTRATOR)
                val ip = if (admin) db { store.lastIp(row.id) }.orEmpty() else ""
                if (ip.isNotEmpty()) db { store.audit(staff.displayName, "view-ip", row.displayName, "") }
                val card =
                    PlayerCard(
                        row.id,
                        row.displayName,
                        row.rights,
                        online[row.id] ?: 0,
                        Sanctions.of(records, now),
                        row.bannedUntil,
                        records.size,
                        ip,
                        tp.util.DonatorTier.forCents(db { store.donationTotal(row.id) }),
                        db { store.donationTotal(row.id) },
                    )
                return ModAnswer(null, card)
            }

            override fun subscribeKicks(worldId: Int, sink: (Long) -> Unit): AutoCloseable {
                val list = kickSinks.getOrPut(worldId) { CopyOnWriteArrayList() }
                list += sink
                return AutoCloseable { list -= sink }
            }
        }

    override val broadcasts: BroadcastService =
        object : BroadcastService {
            override suspend fun broadcast(text: String) {
                broadcastSinks.forEach { runCatching { it(Broadcast.Message(text)) } }
            }

            override suspend fun systemUpdate(seconds: Int) {
                broadcastSinks.forEach { runCatching { it(Broadcast.SystemUpdate(seconds)) } }
            }

            override suspend fun yell(
                from: String,
                rank: Rank,
                worldId: Int,
                text: String,
                donator: tp.util.DonatorTier,
                staff: Boolean,
            ) {
                // D-096: staff chat only from staff; a forged flag from a world is dropped here too.
                if (staff && !rank.isStaff) return
                broadcastSinks.forEach { runCatching { it(Broadcast.Yell(from, rank, worldId, text, donator, staff)) } }
            }

            override suspend fun setInvisible(accountId: Long, invisible: Boolean) {
                val entry = presence[accountId] ?: return
                val updated = PresenceEntry(entry.accountId, entry.name, entry.worldId, entry.rank, invisible)
                presence[accountId] = updated
                presenceChanged(online = listOf(updated))
            }

            override fun subscribe(sink: (Broadcast) -> Unit): AutoCloseable {
                // A new world first learns who is online everywhere (commands brief §5: presence is pushed, not fetched).
                runCatching { sink(Broadcast.Presence(presence.values.toList(), emptyList(), snapshot = true)) }
                broadcastSinks += sink
                return AutoCloseable { broadcastSinks -= sink }
            }
        }

    /** Accounts holding a session lock (health/metrics). */
    val onlineCount: Int get() = online.size

    override fun close() {
        store.close()
    }

    private companion object {
        const val MAX_NAME = 12
        const val MIN_PASSWORD = 5
        const val MAX_PASSWORD = 20
        const val MAX_FRIENDS = 400
        const val MAX_MESSAGE = 80
        const val MAX_TOP = 100
        const val MS_PER_MINUTE = 60_000L
        val NAME = Regex("[A-Za-z0-9 _-]{1,12}")

        /** `::donated`: at most $100,000 per entry, so a typo can't create a fortune (our limit). */
        const val MAX_DONATION_CENTS = 10_000_000L

        /** `::audit`: rows returned per query. */
        const val AUDIT_ROWS = 100

        /** account_seen kinds. */
        const val SEEN_IP = "ip"
        const val SEEN_DEVICE = "device"

        /** Records that end the target's session at once. */
        val KICKING = setOf(RecordType.KICK, RecordType.BAN, RecordType.IP_BAN, RecordType.DEVICE_BAN)
    }
}

/** Sliding-window failure counter (login throttling). */
internal class Throttle(private val windowMs: Long) {
    private val events = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun add(key: String) {
        val q = events.getOrPut(key) { ArrayDeque() }
        synchronized(q) { q.addLast(System.currentTimeMillis()) }
    }

    fun count(key: String): Int {
        val q = events[key] ?: return 0
        val cutoff = System.currentTimeMillis() - windowMs
        synchronized(q) {
            while (q.isNotEmpty() && q.first() < cutoff) q.removeFirst()
            return q.size
        }
    }
}

/**
 * GE skeleton: offers persist in the store; a new offer matches the oldest compatible opposite offers at the older
 * offer's price (OSRS Wiki "Grand Exchange": "the trade takes place at the price of the offer placed first").
 * Coins and items wait in each offer's collection box; worlds move them through item transactions.
 */
internal class Exchange(private val store: HubStore, private val db: suspend (() -> Any?) -> Any?) : ExchangeService {
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> io(block: () -> T): T = db(block) as T

    override suspend fun place(accountId: Long, slot: Int, buy: Boolean, obj: Int, quantity: Int, price: Int, taxRate: Int): GeOffer =
        io {
            synchronized(this) {
                val rate = if (buy) 0 else taxRate.coerceIn(0, MAX_TAX_RATE)
                var offer = store.putOffer(GeOffer(0, accountId, slot, buy, obj, quantity, price, 0, 0, 0, taxRate = rate))
                for (other in store.openOffers(obj, !buy)) {
                    if (offer.completed >= offer.quantity) break
                    val priceOk = if (buy) other.price <= price else other.price >= price
                    if (!priceOk || other.accountId == accountId) continue
                    val amount = minOf(offer.quantity - offer.completed, other.quantity - other.completed)
                    val tradePrice = other.price // the older offer's price
                    offer = fill(offer, amount, tradePrice)
                    val filled = store.putOffer(fill(other, amount, tradePrice))
                    if (filled.completed >= filled.quantity) store.addHistory(filled)
                    recordTrade(obj, tradePrice, amount)
                }
                if (offer.completed >= offer.quantity) store.addHistory(offer)
                store.putOffer(offer)
            }
        }

    /** Buyer collects items (+ refund of the price difference); seller collects coins. */
    override suspend fun guidePrices(): Map<Int, Int> = io { store.guidePrices().mapValues { it.value.first } }

    /**
     * Guide price: volume-weighted moving average of trade prices, with the history's weight capped at [GUIDE_WEIGHT] so
     * recent trades keep moving it (a stand-in for Jagex's daily guide price; docs/mechanics/grand-exchange.md GE2).
     */
    private fun recordTrade(obj: Int, price: Int, amount: Int) {
        val (old, weight) = store.guidePrices()[obj] ?: (price to 0)
        val w = minOf(weight, GUIDE_WEIGHT).toLong()
        val next = ((old * w + price.toLong() * amount) / (w + amount)).toInt()
        store.setGuidePrice(obj, next, minOf(weight + amount, GUIDE_WEIGHT))
    }

    private companion object {
        const val GUIDE_WEIGHT = 1_000

        /** GE14: tax rate in tenths of a percent (client script 5731), at most 100%; capped at 5,000,000 per item. */
        const val TAX_SCALE = 1_000L
        const val MAX_TAX_RATE = 1_000
        const val TAX_CAP = 5_000_000L
    }

    /**
     * One trade of [amount] at [tradePrice]. Buyer: items, plus a refund of the price difference. Seller: coins less the
     * tax, ⌊tradePrice × rate / 1000⌋ per item capped at 5,000,000 (GE14).
     */
    private fun fill(o: GeOffer, amount: Int, tradePrice: Int): GeOffer {
        val taxEach = if (o.buy) 0L else minOf(TAX_CAP, tradePrice.toLong() * o.taxRate / TAX_SCALE)
        return GeOffer(
            o.id,
            o.accountId,
            o.slot,
            o.buy,
            o.obj,
            o.quantity,
            o.price,
            o.completed + amount,
            o.collectCoins + if (o.buy) (o.price - tradePrice).toLong() * amount else (tradePrice - taxEach) * amount,
            o.collectItems + if (o.buy) amount else 0,
            o.gold + tradePrice.toLong() * amount,
            o.aborted,
            o.taxRate,
            o.tax + taxEach * amount,
        )
    }

    override suspend fun offers(accountId: Long): List<GeOffer> = io { store.offers(accountId) }

    /**
     * Takes [part] of what waits in [slot] and returns the offer as it was (what to hand over). A finished or aborted offer
     * whose collect box is then empty is removed.
     */
    override suspend fun collect(accountId: Long, slot: Int, part: CollectPart): GeOffer? =
        io {
            synchronized(this) {
                val o = store.offers(accountId).firstOrNull { it.slot == slot } ?: return@synchronized null
                val coins = if (part == CollectPart.ITEMS) o.collectCoins else 0L
                val items = if (part == CollectPart.COINS) o.collectItems else 0
                val done = (o.aborted || o.completed >= o.quantity) && coins == 0L && items == 0
                if (done) {
                    store.deleteOffer(o.id)
                } else {
                    store.putOffer(
                        GeOffer(
                            o.id,
                            o.accountId,
                            o.slot,
                            o.buy,
                            o.obj,
                            o.quantity,
                            o.price,
                            o.completed,
                            coins,
                            items,
                            o.gold,
                            o.aborted,
                            o.taxRate,
                            o.tax,
                        ),
                    )
                }
                GeOffer(
                    o.id,
                    o.accountId,
                    o.slot,
                    o.buy,
                    o.obj,
                    o.quantity,
                    o.price,
                    o.completed,
                    o.collectCoins - coins,
                    o.collectItems - items,
                    o.gold,
                    o.aborted,
                    o.taxRate,
                    o.tax,
                )
            }
        }

    /** GE13: the offer stops matching; its unfilled part (coins for buys, items for sells) moves to the collect box. */
    override suspend fun abort(accountId: Long, slot: Int): GeOffer? =
        io {
            synchronized(this) {
                val o = store.offers(accountId).firstOrNull { it.slot == slot } ?: return@synchronized null
                if (o.aborted || o.completed >= o.quantity) return@synchronized o
                if (o.completed > 0) store.addHistory(o) // a partly traded offer still traded
                val left = o.quantity - o.completed
                store.putOffer(
                    GeOffer(
                        o.id,
                        o.accountId,
                        o.slot,
                        o.buy,
                        o.obj,
                        o.quantity,
                        o.price,
                        o.completed,
                        o.collectCoins + if (o.buy) o.price.toLong() * left else 0,
                        o.collectItems + if (o.buy) 0 else left,
                        o.gold,
                        aborted = true,
                        taxRate = o.taxRate,
                        tax = o.tax,
                    ),
                )
            }
        }

    override suspend fun history(accountId: Long): List<GeOffer> = io { store.history(accountId) }
}

/**
 * The trading post (docs/mechanics/trading-post.md, a custom feature): fixed-price player listings any player can buy
 * from at once. One lock serialises every change so a listing is never oversold; worlds move items and coins through
 * item transactions before (list, buy) or after (collect) calling in.
 */
internal class TradingPost(private val store: HubStore, private val db: suspend (() -> Any?) -> Any?) : TradingPostService {
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> io(block: () -> T): T = db(block) as T

    private val lock = Any()

    override suspend fun list(accountId: Long, seller: String, obj: Int, quantity: Int, price: Int, maxListings: Int): TpListing? =
        io {
            synchronized(lock) {
                if (quantity <= 0 || price <= 0) return@synchronized null
                if (store.tpOpenListings().count { it.accountId == accountId } >= maxListings) return@synchronized null
                store.putTpListing(TpListing(0, accountId, seller, obj, quantity, 0, price, System.currentTimeMillis()))
            }
        }

    override suspend fun cancel(accountId: Long, id: Long): TpListing? =
        io {
            synchronized(lock) {
                val l = store.tpOpenListings().firstOrNull { it.id == id && it.accountId == accountId } ?: return@synchronized null
                val closed = store.putTpListing(l.copy(closed = true))
                val box = store.tpCollect(accountId)
                val items = box.items.toMutableMap().apply { merge(l.obj, l.remaining, Int::plus) }
                store.setTpCollect(accountId, TpCollect(box.coins, items))
                closed
            }
        }

    override suspend fun search(objs: Set<Int>?, seller: String?, limit: Int): List<TpListing> =
        io {
            store
                .tpOpenListings()
                .asReversed()
                .asSequence()
                .filter { objs == null || it.obj in objs }
                .filter { seller == null || it.seller.equals(seller, ignoreCase = true) }
                .take(limit)
                .toList()
        }

    override suspend fun mine(accountId: Long): List<TpListing> = io { store.tpOpenListings().filter { it.accountId == accountId } }

    override suspend fun buy(accountId: Long, buyer: String, id: Long, quantity: Int, price: Int): TpBuyResult =
        io {
            synchronized(lock) {
                val l = store.tpOpenListings().firstOrNull { it.id == id }
                when {
                    l == null -> {
                        TpBuyResult(0, null, "That listing is no longer available.")
                    }

                    l.accountId == accountId -> {
                        TpBuyResult(0, l, "You can't buy your own listing.")
                    }

                    l.price != price -> {
                        TpBuyResult(0, l, "The price of that listing has changed.")
                    }

                    else -> {
                        val n = minOf(quantity, l.remaining)
                        val updated = store.putTpListing(l.copy(sold = l.sold + n))
                        val box = store.tpCollect(l.accountId)
                        store.setTpCollect(l.accountId, TpCollect(box.coins + n.toLong() * l.price, box.items))
                        val now = System.currentTimeMillis()
                        store.addTpTrade(TpTrade(accountId, l.obj, n, l.price, bought = true, other = l.seller, atMs = now))
                        store.addTpTrade(TpTrade(l.accountId, l.obj, n, l.price, bought = false, other = buyer, atMs = now))
                        TpBuyResult(n, updated, null)
                    }
                }
            }
        }

    override suspend fun collect(accountId: Long): TpCollect =
        io {
            synchronized(lock) {
                val box = store.tpCollect(accountId)
                store.setTpCollect(accountId, TpCollect(0, emptyMap()))
                box
            }
        }

    override suspend fun pending(accountId: Long): TpCollect = io { store.tpCollect(accountId) }

    override suspend fun history(accountId: Long): List<TpTrade> = io { store.tpTrades(accountId) }

    private fun TpListing.copy(sold: Int = this.sold, closed: Boolean = this.closed) =
        TpListing(id, accountId, seller, obj, quantity, sold, price, listedAtMs, closed)
}

/** Audit action of a rank bootstrap (one per account: the commands brief's "applied once"). */
private const val BOOTSTRAP = "bootstrap-rank"
