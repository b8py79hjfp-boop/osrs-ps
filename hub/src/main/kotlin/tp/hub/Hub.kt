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
import tp.services.ExchangeService
import tp.services.FriendEntry
import tp.services.GeOffer
import tp.services.HiscoreEntry
import tp.services.HiscoreService
import tp.services.HubServices
import tp.services.LoginGrant
import tp.services.LoginResult
import tp.services.ModerationService
import tp.services.Rights
import tp.services.Sanction
import tp.services.SaveService
import tp.services.SocialEvent
import tp.services.SocialService
import tp.services.WorldDirectory
import tp.services.WorldInfo
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
)

/**
 * The hub's services in one process (brief §7): login authority with per-account session locks, durable saves,
 * world directory, social, GE skeleton, hiscores, moderation, broadcasts. Served over gRPC by `HubGrpcServer`, or
 * used directly by a world-node running an embedded hub (dev). Blocking store calls run on virtual threads.
 */
class Hub(private val store: HubStore, private val config: HubConfig = HubConfig()) : HubServices, AutoCloseable {
    private val io: CoroutineDispatcher = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()

    private suspend fun <T> db(block: () -> T): T = withContext(io) { block() }

    /** Session locks: account → world holding it. */
    private val online = ConcurrentHashMap<Long, Int>()
    private val onlineNames = ConcurrentHashMap<String, Long>()

    /** Logout saves in flight: account → completes when durable. */
    private val pendingLogout = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

    private val failures = Throttle(config.failureWindowMs)
    private val worldList = ConcurrentHashMap<Int, WorldInfo>()
    private val socialSinks = ConcurrentHashMap<Int, CopyOnWriteArrayList<(SocialEvent) -> Unit>>()
    private val kickSinks = ConcurrentHashMap<Int, CopyOnWriteArrayList<(Long) -> Unit>>()
    private val broadcastSinks = CopyOnWriteArrayList<(Broadcast) -> Unit>()

    // ---------------------------------------------------------------- accounts

    override val accounts: AccountService =
        object : AccountService {
            @Suppress("ReturnCount") // guard clauses, one per refusal reason
            override suspend fun login(username: String, password: String, worldId: Int, ip: String): LoginGrant {
                val name = username.trim()
                if (name.isEmpty() || name.length > MAX_NAME) return LoginGrant(LoginResult.INVALID_CREDENTIALS)
                if (failures.count("ip:$ip") >= config.maxFailuresPerIp ||
                    failures.count("acct:${name.lowercase()}") >= config.maxFailuresPerAccount
                ) {
                    return LoginGrant(LoginResult.THROTTLED)
                }
                if (db { store.ipBanned(ip) }) return LoginGrant(LoginResult.BANNED)
                var row = db { store.account(name) }
                if (row == null && config.autoRegister) row = db { store.createAccount(name, Passwords.hash(password), Rights.PLAYER) }
                if (row == null || !db { Passwords.verify(password, row.passwordHash) }) {
                    failures.add("ip:$ip")
                    failures.add("acct:${name.lowercase()}")
                    return LoginGrant(LoginResult.INVALID_CREDENTIALS)
                }
                if (row.bannedUntil < 0 || row.bannedUntil > System.currentTimeMillis()) return LoginGrant(LoginResult.BANNED)
                // Dupe prevention: the previous world's logout save must be durable first (brief §7).
                pendingLogout[row.id]?.let { pending ->
                    if (withTimeoutOrNull(config.pendingSaveWaitMs) { pending.await() } == null) return LoginGrant(LoginResult.SAVE_PENDING)
                }
                if (online.putIfAbsent(row.id, worldId) != null) return LoginGrant(LoginResult.ALREADY_ONLINE)
                onlineNames[row.username] = row.id
                val save = db { store.loadSave(row.id) }
                db {
                    store.setLastIp(row.id, ip)
                    store.audit(row.displayName, "login", "world $worldId", ip)
                }
                notifyFriendStatus(row.displayName, worldId)
                return LoginGrant(LoginResult.OK, row.id, row.displayName, row.rights, save, row.mutedUntil)
            }

            override suspend fun logout(accountId: Long, worldId: Int, save: String) {
                val pending = CompletableDeferred<Unit>()
                pendingLogout[accountId] = pending
                try {
                    db { store.writeSave(accountId, save) }
                } finally {
                    if (online.remove(accountId, worldId)) {
                        val row = db { store.account(accountId) }
                        if (row != null) {
                            onlineNames.remove(row.username)
                            notifyFriendStatus(row.displayName, 0)
                        }
                    }
                    pendingLogout.remove(accountId, pending)
                    pending.complete(Unit)
                }
            }

            override suspend fun register(username: String, password: String, ip: String): String? {
                if (!NAME.matches(username)) return "Names are 1-12 letters, numbers, spaces, - or _."
                if (password.length !in MIN_PASSWORD..MAX_PASSWORD) return "Passwords are $MIN_PASSWORD-$MAX_PASSWORD characters."
                if (db { store.ipBanned(ip) }) return "Registration is not available."
                val row = db { store.createAccount(username, Passwords.hash(password), Rights.PLAYER) } ?: return "That name is taken."
                db { store.audit(username, "register", row.id.toString(), ip) }
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
                logger.info { "world ${world.id} registered at ${world.host}:${world.port}" }
            }

            override suspend fun heartbeat(worldId: Int, population: Int) {
                val w = worldList[worldId] ?: return
                worldList[worldId] = WorldInfo(w.id, w.host, w.port, w.activity, w.flags, population)
            }

            override suspend fun list(): List<WorldInfo> = worldList.values.sortedBy { it.id }
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

    override val social: SocialService =
        object : SocialService {
            override suspend fun friends(accountId: Long): List<FriendEntry> =
                db { store.friends(accountId) }.map { name -> FriendEntry(name, onlineNames[name.lowercase()]?.let { online[it] } ?: 0) }

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
            override suspend fun apply(staffAccount: Long, target: String, sanction: Sanction, minutes: Int, reason: String): String? {
                val staff = db { store.account(staffAccount) } ?: return "Unknown staff account."
                if (staff.rights == Rights.PLAYER) return "You don't have permission to do that."
                if (sanction in ADMIN_ONLY && staff.rights != Rights.ADMIN) return "Only administrators can do that."
                val row = db { store.account(target) } ?: return "Unknown player."
                val until = if (minutes <= 0) -1L else System.currentTimeMillis() + minutes * MS_PER_MINUTE
                when (sanction) {
                    Sanction.KICK -> Unit
                    Sanction.MUTE -> db { store.setMuted(row.id, until) }
                    Sanction.UNMUTE -> db { store.setMuted(row.id, 0) }
                    Sanction.BAN -> db { store.setBanned(row.id, until) }
                    Sanction.UNBAN -> db { store.setBanned(row.id, 0) }
                    Sanction.IP_BAN -> db { store.lastIp(row.id)?.let { store.setIpBan(it, true) } }
                }
                db { store.audit(staff.displayName, sanction.name, row.displayName, "minutes=$minutes reason=$reason") }
                if (sanction == Sanction.KICK || sanction == Sanction.BAN || sanction == Sanction.IP_BAN) {
                    online[row.id]?.let { world -> kickSinks[world]?.forEach { runCatching { it(row.id) } } }
                }
                return null
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

            override fun subscribe(sink: (Broadcast) -> Unit): AutoCloseable {
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
        val ADMIN_ONLY = setOf(Sanction.IP_BAN, Sanction.BAN, Sanction.UNBAN)
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

    override suspend fun place(accountId: Long, slot: Int, buy: Boolean, obj: Int, quantity: Int, price: Int): GeOffer =
        io {
            synchronized(this) {
                var offer = store.putOffer(GeOffer(0, accountId, slot, buy, obj, quantity, price, 0, 0, 0))
                for (other in store.openOffers(obj, !buy)) {
                    if (offer.completed >= offer.quantity) break
                    val priceOk = if (buy) other.price <= price else other.price >= price
                    if (!priceOk || other.accountId == accountId) continue
                    val amount = minOf(offer.quantity - offer.completed, other.quantity - other.completed)
                    val tradePrice = other.price // the older offer's price
                    offer = fill(offer, amount, tradePrice)
                    store.putOffer(fill(other, amount, tradePrice))
                }
                store.putOffer(offer)
            }
        }

    /** Buyer collects items (+ refund of the price difference); seller collects coins. */
    private fun fill(o: GeOffer, amount: Int, tradePrice: Int): GeOffer =
        GeOffer(
            o.id,
            o.accountId,
            o.slot,
            o.buy,
            o.obj,
            o.quantity,
            o.price,
            o.completed + amount,
            o.collectCoins + if (o.buy) (o.price - tradePrice).toLong() * amount else tradePrice.toLong() * amount,
            o.collectItems + if (o.buy) amount else 0,
        )

    override suspend fun offers(accountId: Long): List<GeOffer> = io { store.offers(accountId) }

    override suspend fun collect(accountId: Long, slot: Int): GeOffer? =
        io {
            synchronized(this) {
                val o = store.offers(accountId).firstOrNull { it.slot == slot } ?: return@synchronized null
                if (o.completed >= o.quantity) {
                    store.deleteOffer(o.id)
                } else {
                    store.putOffer(GeOffer(o.id, o.accountId, o.slot, o.buy, o.obj, o.quantity, o.price, o.completed, 0, 0))
                }
                o
            }
        }

    override suspend fun abort(accountId: Long, slot: Int): GeOffer? =
        io {
            synchronized(this) {
                val o = store.offers(accountId).firstOrNull { it.slot == slot } ?: return@synchronized null
                store.deleteOffer(o.id)
                // Aborting returns the unfilled part: coins for buys, items for sells.
                val left = o.quantity - o.completed
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
                )
            }
        }
}
