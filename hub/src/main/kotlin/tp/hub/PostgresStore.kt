package tp.hub

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import tp.services.ChannelSettings
import tp.services.GeOffer
import tp.services.HiscoreEntry
import tp.services.TpCollect
import tp.services.TpListing
import tp.services.TpTrade
import tp.util.Rank
import java.sql.Connection
import java.sql.ResultSet

/**
 * PostgreSQL [HubStore] (brief §12: PostgreSQL with point-in-time recovery; docs/OPERATIONS.md). Schema is created on
 * start (`CREATE TABLE IF NOT EXISTS`, schema version in `meta`). Each write is its own committed transaction, so a
 * returned [writeSave] is durable.
 */
class PostgresStore(jdbcUrl: String, user: String, password: String) : HubStore {
    private val ds =
        HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                username = user
                this.password = password
                maximumPoolSize = POOL_SIZE
            },
        )

    init {
        sql { c -> c.createStatement().use { st -> for (ddl in SCHEMA) st.execute(ddl) } }
    }

    private fun <T> sql(block: (Connection) -> T): T = ds.connection.use(block)

    private fun ResultSet.account(): AccountRow =
        AccountRow(
            getLong("id"),
            getString("username"),
            getString("display_name"),
            getString("password_hash"),
            Rank.parseOrPlayer(getString("rights")),
            getLong("muted_until"),
            getLong("banned_until"),
        )

    private fun one(query: String, vararg args: Any): AccountRow? =
        sql { c ->
            c.prepareStatement(query).use { ps ->
                args.forEachIndexed { i, a -> ps.setObject(i + 1, a) }
                ps.executeQuery().use { rs -> if (rs.next()) rs.account() else null }
            }
        }

    private fun exec(query: String, vararg args: Any?) {
        sql { c ->
            c.prepareStatement(query).use { ps ->
                args.forEachIndexed { i, a -> ps.setObject(i + 1, a) }
                ps.executeUpdate()
            }
        }
    }

    private fun strings(query: String, vararg args: Any): List<String> =
        sql { c ->
            c.prepareStatement(query).use { ps ->
                args.forEachIndexed { i, a -> ps.setObject(i + 1, a) }
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
            }
        }

    override fun account(username: String): AccountRow? = one("SELECT * FROM accounts WHERE username = ?", username.lowercase())

    override fun account(id: Long): AccountRow? = one("SELECT * FROM accounts WHERE id = ?", id)

    override fun createAccount(username: String, passwordHash: String, rights: Rank): AccountRow? =
        one(
            "INSERT INTO accounts (username, display_name, password_hash, rights) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (username) DO NOTHING RETURNING *",
            username.lowercase(),
            username,
            passwordHash,
            rights.stored,
        )

    override fun setPasswordHash(id: Long, hash: String) = exec("UPDATE accounts SET password_hash = ? WHERE id = ?", hash, id)

    override fun setRights(id: Long, rights: Rank) = exec("UPDATE accounts SET rights = ? WHERE id = ?", rights.stored, id)

    override fun setMuted(id: Long, until: Long) = exec("UPDATE accounts SET muted_until = ? WHERE id = ?", until, id)

    override fun setBanned(id: Long, until: Long) = exec("UPDATE accounts SET banned_until = ? WHERE id = ?", until, id)

    override fun ipBanned(ip: String): Boolean = strings("SELECT ip FROM ip_bans WHERE ip = ?", ip).isNotEmpty()

    override fun setIpBan(ip: String, banned: Boolean) =
        if (banned) exec("INSERT INTO ip_bans (ip) VALUES (?) ON CONFLICT DO NOTHING", ip) else exec("DELETE FROM ip_bans WHERE ip = ?", ip)

    override fun lastIp(id: Long): String? = strings("SELECT last_ip FROM accounts WHERE id = ?", id).firstOrNull()

    @Suppress("MagicNumber") // JDBC parameter indexes
    override fun addRecord(record: tp.util.ModRecord): tp.util.ModRecord =
        sql { c ->
            val q =
                "INSERT INTO moderation_records (account_id, type, actor, reason, start_ms, expires_ms, scope) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id"
            c.prepareStatement(q).use { ps ->
                with(record) {
                    ps.setLong(1, accountId)
                    ps.setString(2, type.name)
                    ps.setString(3, actor)
                    ps.setString(4, reason)
                    ps.setLong(5, start)
                    ps.setLong(6, expires)
                    ps.setString(7, scope)
                }
                ps.executeQuery().use { rs ->
                    rs.next()
                    with(record) { tp.util.ModRecord(rs.getLong(1), accountId, type, actor, reason, start, expires, scope) }
                }
            }
        }

    override fun records(accountId: Long): List<tp.util.ModRecord> =
        sql { c ->
            c.prepareStatement("SELECT * FROM moderation_records WHERE account_id = ? ORDER BY id DESC").use { ps ->
                ps.setLong(1, accountId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(record(rs)) } }
            }
        }

    @Suppress("MagicNumber") // JDBC parameter indexes
    override fun liftRecords(accountId: Long, type: tp.util.RecordType, by: String, at: Long): Int =
        sql { c ->
            val q =
                "UPDATE moderation_records SET lifted_by = ?, lifted_at_ms = ? WHERE account_id = ? AND type = ? " +
                    "AND lifted_by = '' AND (expires_ms = -1 OR expires_ms > ?)"
            c.prepareStatement(q).use { ps ->
                ps.setString(1, by)
                ps.setLong(2, at)
                ps.setLong(3, accountId)
                ps.setString(4, type.name)
                ps.setLong(5, at)
                ps.executeUpdate()
            }
        }

    override fun scopeBanned(type: tp.util.RecordType, scope: String, now: Long): Boolean =
        scope.isNotEmpty() &&
            sql { c ->
                val q =
                    "SELECT 1 FROM moderation_records WHERE type = ? AND scope = ? AND lifted_by = '' " +
                        "AND (expires_ms = -1 OR expires_ms > ?) LIMIT 1"
                c.prepareStatement(q).use { ps ->
                    ps.setString(1, type.name)
                    ps.setString(2, scope)
                    ps.setLong(3, now)
                    ps.executeQuery().use { it.next() }
                }
            }

    private fun record(rs: java.sql.ResultSet): tp.util.ModRecord =
        tp.util.ModRecord(
            rs.getLong("id"),
            rs.getLong("account_id"),
            tp.util.RecordType.valueOf(rs.getString("type")),
            rs.getString("actor"),
            rs.getString("reason"),
            rs.getLong("start_ms"),
            rs.getLong("expires_ms"),
            rs.getString("scope"),
            rs.getString("lifted_by"),
            rs.getLong("lifted_at_ms"),
        )

    override fun setLastIp(id: Long, ip: String) = exec("UPDATE accounts SET last_ip = ? WHERE id = ?", ip, id)

    override fun loadSave(id: Long): String? = strings("SELECT data FROM saves WHERE account_id = ?", id).firstOrNull()

    override fun writeSave(id: Long, save: String) =
        exec(
            "INSERT INTO saves (account_id, data, updated_at) VALUES (?, ?::jsonb, now()) " +
                "ON CONFLICT (account_id) DO UPDATE SET data = EXCLUDED.data, updated_at = now()",
            id,
            save,
        )

    override fun friends(id: Long): List<String> = strings("SELECT name FROM friends WHERE account_id = ? ORDER BY name", id)

    override fun setFriend(id: Long, name: String, present: Boolean) =
        if (present) {
            exec("INSERT INTO friends (account_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING", id, name)
        } else {
            exec("DELETE FROM friends WHERE account_id = ? AND lower(name) = lower(?)", id, name)
        }

    override fun ignores(id: Long): List<String> = strings("SELECT name FROM ignores WHERE account_id = ? ORDER BY name", id)

    override fun setIgnore(id: Long, name: String, present: Boolean) =
        if (present) {
            exec("INSERT INTO ignores (account_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING", id, name)
        } else {
            exec("DELETE FROM ignores WHERE account_id = ? AND lower(name) = lower(?)", id, name)
        }

    private fun ResultSet.offer(): GeOffer =
        GeOffer(
            getLong("id"),
            getLong("account_id"),
            getInt("slot"),
            getBoolean("buy"),
            getInt("obj"),
            getInt("quantity"),
            getInt("price"),
            getInt("completed"),
            getLong("collect_coins"),
            getInt("collect_items"),
            getLong("gold"),
            getBoolean("aborted"),
            getInt("tax_rate"),
            getLong("tax"),
        )

    private fun offers(query: String, vararg args: Any): List<GeOffer> =
        sql { c ->
            c.prepareStatement(query).use { ps ->
                args.forEachIndexed { i, a -> ps.setObject(i + 1, a) }
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.offer()) } }
            }
        }

    private fun java.sql.ResultSet.tpListing() =
        TpListing(
            getLong("id"),
            getLong("account_id"),
            getString("seller"),
            getInt("obj"),
            getInt("quantity"),
            getInt("sold"),
            getInt("price"),
            getLong("listed_at"),
            getBoolean("closed"),
        )

    override fun tpOpenListings(): List<TpListing> =
        sql { c ->
            c.prepareStatement("SELECT * FROM tp_listings WHERE NOT closed AND sold < quantity ORDER BY id").use { ps ->
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.tpListing()) } }
            }
        }

    override fun putTpListing(listing: TpListing): TpListing =
        sql { c ->
            if (listing.id == 0L) {
                c
                    .prepareStatement(
                        "INSERT INTO tp_listings (account_id, seller, obj, quantity, sold, price, listed_at, closed) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING *",
                    ).use { ps ->
                        with(listing) { listOf(accountId, seller, obj, quantity, sold, price, listedAtMs, closed) }
                            .forEachIndexed { i, a -> ps.setObject(i + 1, a) }
                        ps.executeQuery().use { rs ->
                            rs.next()
                            rs.tpListing()
                        }
                    }
            } else {
                c.prepareStatement("UPDATE tp_listings SET sold = ?, closed = ? WHERE id = ?").use { ps ->
                    ps.setInt(1, listing.sold)
                    ps.setBoolean(2, listing.closed)
                    ps.setLong(3, listing.id)
                    ps.executeUpdate()
                }
                listing
            }
        }

    override fun tpCollect(id: Long): TpCollect =
        sql { c ->
            c.prepareStatement("SELECT coins, items FROM tp_collect WHERE account_id = ?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) TpCollect(rs.getLong(1), decodeItems(rs.getString(2))) else TpCollect(0, emptyMap())
                }
            }
        }

    override fun setTpCollect(id: Long, collect: TpCollect) {
        if (collect.empty) return exec("DELETE FROM tp_collect WHERE account_id = ?", id)
        exec(
            "INSERT INTO tp_collect (account_id, coins, items) VALUES (?, ?, ?) " +
                "ON CONFLICT (account_id) DO UPDATE SET coins = EXCLUDED.coins, items = EXCLUDED.items",
            id,
            collect.coins,
            collect.items.entries.joinToString(",") { "${it.key}:${it.value}" },
        )
    }

    private fun decodeItems(text: String): Map<Int, Int> =
        text.split(',').filter { it.isNotEmpty() }.associate { e -> e.substringBefore(':').toInt() to e.substringAfter(':').toInt() }

    override fun addTpTrade(trade: TpTrade) {
        with(trade) {
            exec(
                "INSERT INTO tp_trades (account_id, obj, quantity, price, bought, other, at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                accountId,
                obj,
                quantity,
                price,
                bought,
                other,
                atMs,
            )
        }
        exec(
            "DELETE FROM tp_trades WHERE account_id = ? AND id NOT IN " +
                "(SELECT id FROM tp_trades WHERE account_id = ? ORDER BY id DESC LIMIT ?)",
            trade.accountId,
            trade.accountId,
            TP_HISTORY_SIZE,
        )
    }

    override fun tpTrades(id: Long): List<TpTrade> =
        sql { c ->
            c.prepareStatement("SELECT * FROM tp_trades WHERE account_id = ? ORDER BY id DESC").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                TpTrade(
                                    rs.getLong("account_id"),
                                    rs.getInt("obj"),
                                    rs.getInt("quantity"),
                                    rs.getInt("price"),
                                    rs.getBoolean("bought"),
                                    rs.getString("other"),
                                    rs.getLong("at"),
                                ),
                            )
                        }
                    }
                }
            }
        }

    override fun channelSettings(id: Long): ChannelSettings? =
        sql { c ->
            c.prepareStatement("SELECT name, enter_rank, talk_rank, kick_rank FROM channel_settings WHERE owner_id = ?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    if (rs.next()) ChannelSettings(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)) else null
                }
            }
        }

    override fun setChannelSettings(id: Long, settings: ChannelSettings) =
        exec(
            "INSERT INTO channel_settings (owner_id, name, enter_rank, talk_rank, kick_rank) VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT (owner_id) DO UPDATE SET name = EXCLUDED.name, enter_rank = EXCLUDED.enter_rank, " +
                "talk_rank = EXCLUDED.talk_rank, kick_rank = EXCLUDED.kick_rank",
            id,
            settings.name,
            settings.enterRank,
            settings.talkRank,
            settings.kickRank,
        )

    override fun channelRanks(id: Long): Map<String, Int> =
        sql { c ->
            c.prepareStatement("SELECT name, rank FROM channel_ranks WHERE owner_id = ?").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getString(1), rs.getInt(2)) } }
            }
        }

    override fun setChannelRank(id: Long, name: String, rank: Int) =
        if (rank >= 0) {
            exec(
                "INSERT INTO channel_ranks (owner_id, name, rank) VALUES (?, ?, ?) " +
                    "ON CONFLICT (owner_id, name) DO UPDATE SET rank = EXCLUDED.rank",
                id,
                name.lowercase(),
                rank,
            )
        } else {
            exec("DELETE FROM channel_ranks WHERE owner_id = ? AND name = ?", id, name.lowercase())
        }

    override fun guidePrices(): Map<Int, Pair<Int, Int>> =
        sql { c ->
            c.prepareStatement("SELECT obj, price, weight FROM ge_prices").use { ps ->
                ps.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getInt(1), rs.getInt(2) to rs.getInt(3)) } }
            }
        }

    override fun setGuidePrice(obj: Int, price: Int, weight: Int) =
        exec(
            "INSERT INTO ge_prices (obj, price, weight) VALUES (?, ?, ?) " +
                "ON CONFLICT (obj) DO UPDATE SET price = EXCLUDED.price, weight = EXCLUDED.weight",
            obj,
            price,
            weight,
        )

    override fun offers(id: Long): List<GeOffer> = offers("SELECT * FROM ge_offers WHERE account_id = ? ORDER BY slot", id)

    override fun openOffers(obj: Int, buy: Boolean): List<GeOffer> =
        offers("SELECT * FROM ge_offers WHERE obj = ? AND buy = ? AND NOT aborted AND completed < quantity ORDER BY id", obj, buy)

    override fun putOffer(offer: GeOffer): GeOffer {
        if (offer.id == 0L) {
            return offers(
                "INSERT INTO ge_offers (account_id, slot, buy, obj, quantity, price, completed, collect_coins, collect_items, gold, " +
                    "aborted, tax_rate, tax) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING *",
                offer.accountId,
                offer.slot,
                offer.buy,
                offer.obj,
                offer.quantity,
                offer.price,
                offer.completed,
                offer.collectCoins,
                offer.collectItems,
                offer.gold,
                offer.aborted,
                offer.taxRate,
                offer.tax,
            ).single()
        }
        exec(
            "UPDATE ge_offers SET completed = ?, collect_coins = ?, collect_items = ?, gold = ?, aborted = ?, tax = ? WHERE id = ?",
            offer.completed,
            offer.collectCoins,
            offer.collectItems,
            offer.gold,
            offer.aborted,
            offer.tax,
            offer.id,
        )
        return offer
    }

    override fun deleteOffer(offerId: Long) = exec("DELETE FROM ge_offers WHERE id = ?", offerId)

    override fun addHistory(offer: GeOffer) {
        exec(
            "INSERT INTO ge_history (account_id, buy, obj, quantity, price, completed, gold, tax) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            offer.accountId,
            offer.buy,
            offer.obj,
            offer.quantity,
            offer.price,
            offer.completed,
            offer.gold,
            offer.tax,
        )
        exec(
            "DELETE FROM ge_history WHERE account_id = ? AND id NOT IN " +
                "(SELECT id FROM ge_history WHERE account_id = ? ORDER BY id DESC LIMIT $GE_HISTORY_SIZE)",
            offer.accountId,
            offer.accountId,
        )
    }

    override fun history(id: Long): List<GeOffer> =
        sql { c ->
            c.prepareStatement("SELECT * FROM ge_history WHERE account_id = ? ORDER BY id DESC").use { ps ->
                ps.setLong(1, id)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                GeOffer(
                                    rs.getLong("id"),
                                    id,
                                    -1,
                                    rs.getBoolean("buy"),
                                    rs.getInt("obj"),
                                    rs.getInt("quantity"),
                                    rs.getInt("price"),
                                    rs.getInt("completed"),
                                    0,
                                    0,
                                    rs.getLong("gold"),
                                    tax = rs.getLong("tax"),
                                ),
                            )
                        }
                    }
                }
            }
        }

    override fun submitHiscores(id: Long, name: String, xpTenths: IntArray) =
        exec(
            "INSERT INTO hiscores (account_id, name, xp) VALUES (?, ?, ?) " +
                "ON CONFLICT (account_id) DO UPDATE SET name = EXCLUDED.name, xp = EXCLUDED.xp",
            id,
            name,
            xpTenths.toTypedArray(),
        )

    override fun topHiscores(stat: Int, count: Int): List<HiscoreEntry> {
        val rows =
            sql { c ->
                c.prepareStatement("SELECT name, xp FROM hiscores").use { ps ->
                    ps.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                @Suppress("UNCHECKED_CAST")
                                val xp = (rs.getArray(2).array as Array<Int>).toIntArray()
                                add(rs.getString(1) to xp)
                            }
                        }
                    }
                }
            }
        return Hiscores.rank(rows, stat, count)
    }

    override fun audited(action: String, target: String): Boolean =
        strings("SELECT action FROM audit_log WHERE action = ? AND target = ? LIMIT 1", action, target).isNotEmpty()

    override fun audit(actor: String, action: String, target: String, detail: String) =
        exec("INSERT INTO audit_log (actor, action, target, detail) VALUES (?, ?, ?, ?)", actor, action, target, detail)

    @Suppress("MagicNumber") // JDBC column / parameter indexes
    override fun clans(): List<tp.services.ClanRecord> =
        sql { c ->
            val members = HashMap<Long, MutableList<tp.services.ClanMemberRow>>()
            val q = "SELECT clan_id, account_id, name, rank, joined_day FROM clan_members ORDER BY clan_id, joined_day"
            c.prepareStatement(q).use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        members.getOrPut(rs.getLong(1)) { ArrayList() } +=
                            tp.services.ClanMemberRow(rs.getLong(2), rs.getString(3), rs.getInt(4), rs.getInt(5))
                    }
                }
            }
            val bans = HashMap<Long, MutableList<String>>()
            c.prepareStatement("SELECT clan_id, name FROM clan_bans").use { ps ->
                ps.executeQuery().use { rs -> while (rs.next()) bans.getOrPut(rs.getLong(1)) { ArrayList() } += rs.getString(2) }
            }
            c.prepareStatement("SELECT id, name, created_minute, talk_rank, kick_rank, allow_guests, recruit_rank FROM clans").use { ps ->
                ps.executeQuery().use { rs ->
                    val out = ArrayList<tp.services.ClanRecord>()
                    while (rs.next()) {
                        val id = rs.getLong(1)
                        out +=
                            tp.services.ClanRecord(
                                id,
                                rs.getString(2),
                                rs.getLong(3),
                                members[id].orEmpty(),
                                bans[id].orEmpty(),
                                tp.services.ClanRules(rs.getInt(4), rs.getInt(5), rs.getBoolean(6), rs.getInt(7)),
                            )
                    }
                    out
                }
            }
        }

    @Suppress("MagicNumber") // JDBC column / parameter indexes
    override fun putClan(clan: tp.services.ClanRecord) {
        sql { c ->
            c.autoCommit = false
            try {
                c
                    .prepareStatement(
                        "INSERT INTO clans (id, name, created_minute, talk_rank, kick_rank, allow_guests, recruit_rank) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, talk_rank = EXCLUDED.talk_rank, " +
                            "kick_rank = EXCLUDED.kick_rank, " +
                            "allow_guests = EXCLUDED.allow_guests, recruit_rank = EXCLUDED.recruit_rank",
                    ).use { ps ->
                        ps.setLong(1, clan.id)
                        ps.setString(2, clan.name)
                        ps.setLong(3, clan.createdMinute)
                        ps.setInt(4, clan.rules.talkRank)
                        ps.setInt(5, clan.rules.kickRank)
                        ps.setBoolean(6, clan.rules.allowGuests)
                        ps.setInt(7, clan.rules.recruitRank)
                        ps.executeUpdate()
                    }
                for (table in listOf("clan_members", "clan_bans")) {
                    c.prepareStatement("DELETE FROM $table WHERE clan_id = ?").use { ps ->
                        ps.setLong(1, clan.id)
                        ps.executeUpdate()
                    }
                }
                val insert = "INSERT INTO clan_members (clan_id, account_id, name, rank, joined_day) VALUES (?, ?, ?, ?, ?)"
                c.prepareStatement(insert).use { ps ->
                    for (m in clan.members) {
                        ps.setLong(1, clan.id)
                        ps.setLong(2, m.accountId)
                        ps.setString(3, m.name)
                        ps.setInt(4, m.rank)
                        ps.setInt(5, m.joinedDay)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                c.prepareStatement("INSERT INTO clan_bans (clan_id, name) VALUES (?, ?)").use { ps ->
                    for (b in clan.banned) {
                        ps.setLong(1, clan.id)
                        ps.setString(2, b)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                c.commit()
            } finally {
                c.autoCommit = true
            }
        }
    }

    override fun deleteClan(id: Long) {
        for (table in listOf("clan_members", "clan_bans")) exec("DELETE FROM $table WHERE clan_id = ?", id)
        exec("DELETE FROM clans WHERE id = ?", id)
    }

    override fun close() = ds.close()

    private companion object {
        const val POOL_SIZE = 16

        val SCHEMA =
            listOf(
                "CREATE TABLE IF NOT EXISTS meta (key text PRIMARY KEY, value text NOT NULL)",
                "INSERT INTO meta (key, value) VALUES ('schema_version', '1') ON CONFLICT DO NOTHING",
                """CREATE TABLE IF NOT EXISTS accounts (
                    id bigserial PRIMARY KEY, username text UNIQUE NOT NULL, display_name text NOT NULL,
                    password_hash text NOT NULL, rights text NOT NULL DEFAULT 'PLAYER',
                    muted_until bigint NOT NULL DEFAULT 0, banned_until bigint NOT NULL DEFAULT 0, last_ip text,
                    created_at timestamptz NOT NULL DEFAULT now())""",
                """CREATE TABLE IF NOT EXISTS saves (
                    account_id bigint PRIMARY KEY REFERENCES accounts(id), data jsonb NOT NULL, updated_at timestamptz NOT NULL)""",
                "CREATE TABLE IF NOT EXISTS friends (account_id bigint REFERENCES accounts(id), name text, PRIMARY KEY (account_id, name))",
                "CREATE TABLE IF NOT EXISTS ignores (account_id bigint REFERENCES accounts(id), name text, PRIMARY KEY (account_id, name))",
                "CREATE TABLE IF NOT EXISTS ip_bans (ip text PRIMARY KEY)",
                "CREATE TABLE IF NOT EXISTS ge_prices (obj int PRIMARY KEY, price int NOT NULL, weight int NOT NULL)",
                """CREATE TABLE IF NOT EXISTS channel_settings (
                    owner_id bigint PRIMARY KEY REFERENCES accounts(id), name text, enter_rank int NOT NULL,
                    talk_rank int NOT NULL, kick_rank int NOT NULL)""",
                """CREATE TABLE IF NOT EXISTS channel_ranks (
                    owner_id bigint REFERENCES accounts(id), name text, rank int NOT NULL, PRIMARY KEY (owner_id, name))""",
                """CREATE TABLE IF NOT EXISTS ge_offers (
                    id bigserial PRIMARY KEY, account_id bigint REFERENCES accounts(id), slot int NOT NULL, buy boolean NOT NULL,
                    obj int NOT NULL, quantity int NOT NULL, price int NOT NULL, completed int NOT NULL,
                    collect_coins bigint NOT NULL, collect_items int NOT NULL)""",
                "ALTER TABLE ge_offers ADD COLUMN IF NOT EXISTS gold bigint NOT NULL DEFAULT 0",
                """CREATE TABLE IF NOT EXISTS tp_listings (
                    id bigserial PRIMARY KEY, account_id bigint REFERENCES accounts(id), seller text NOT NULL, obj int NOT NULL,
                    quantity int NOT NULL, sold int NOT NULL, price int NOT NULL, listed_at bigint NOT NULL,
                    closed boolean NOT NULL DEFAULT false)""",
                """CREATE TABLE IF NOT EXISTS tp_collect (
                    account_id bigint PRIMARY KEY REFERENCES accounts(id), coins bigint NOT NULL, items text NOT NULL)""",
                """CREATE TABLE IF NOT EXISTS tp_trades (
                    id bigserial PRIMARY KEY, account_id bigint REFERENCES accounts(id), obj int NOT NULL, quantity int NOT NULL,
                    price int NOT NULL, bought boolean NOT NULL, other text NOT NULL, at bigint NOT NULL)""",
                "ALTER TABLE ge_offers ADD COLUMN IF NOT EXISTS aborted boolean NOT NULL DEFAULT false",
                "ALTER TABLE ge_offers ADD COLUMN IF NOT EXISTS tax_rate int NOT NULL DEFAULT 0",
                "ALTER TABLE ge_offers ADD COLUMN IF NOT EXISTS tax bigint NOT NULL DEFAULT 0",
                """CREATE TABLE IF NOT EXISTS ge_history (
                    id bigserial PRIMARY KEY, account_id bigint REFERENCES accounts(id), buy boolean NOT NULL, obj int NOT NULL,
                    quantity int NOT NULL, price int NOT NULL, completed int NOT NULL, gold bigint NOT NULL)""",
                "ALTER TABLE ge_history ADD COLUMN IF NOT EXISTS tax bigint NOT NULL DEFAULT 0",
                """CREATE TABLE IF NOT EXISTS hiscores (
                    account_id bigint PRIMARY KEY REFERENCES accounts(id), name text NOT NULL, xp int[] NOT NULL)""",
                """CREATE TABLE IF NOT EXISTS clans (
                    id bigint PRIMARY KEY, name text UNIQUE NOT NULL, created_minute bigint NOT NULL, talk_rank int NOT NULL,
                    kick_rank int NOT NULL, allow_guests boolean NOT NULL, recruit_rank int NOT NULL)""",
                """CREATE TABLE IF NOT EXISTS clan_members (
                    clan_id bigint REFERENCES clans(id), account_id bigint REFERENCES accounts(id), name text NOT NULL,
                    rank int NOT NULL, joined_day int NOT NULL, PRIMARY KEY (clan_id, account_id))""",
                "CREATE TABLE IF NOT EXISTS clan_bans (clan_id bigint REFERENCES clans(id), name text, PRIMARY KEY (clan_id, name))",
                """CREATE TABLE IF NOT EXISTS audit_log (
                    id bigserial PRIMARY KEY, at timestamptz NOT NULL DEFAULT now(), actor text NOT NULL, action text NOT NULL,
                    target text NOT NULL, detail text NOT NULL)""",
                // Commands brief §7 (D-088): additive; accounts.muted_until / banned_until and ip_bans stay the fast checks.
                """CREATE TABLE IF NOT EXISTS moderation_records (
                    id bigserial PRIMARY KEY, account_id bigint NOT NULL REFERENCES accounts(id), type text NOT NULL,
                    actor text NOT NULL, reason text NOT NULL, start_ms bigint NOT NULL, expires_ms bigint NOT NULL,
                    scope text NOT NULL DEFAULT '', lifted_by text NOT NULL DEFAULT '', lifted_at_ms bigint NOT NULL DEFAULT 0)""",
                "CREATE INDEX IF NOT EXISTS moderation_records_account ON moderation_records (account_id)",
                "CREATE INDEX IF NOT EXISTS moderation_records_scope ON moderation_records (type, scope) WHERE scope <> ''",
            )
    }
}
