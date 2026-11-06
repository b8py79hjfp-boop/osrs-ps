package tp.hub

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import tp.services.GeOffer
import tp.services.HiscoreEntry
import tp.services.Rights
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
            Rights.valueOf(getString("rights")),
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

    override fun createAccount(username: String, passwordHash: String, rights: Rights): AccountRow? =
        one(
            "INSERT INTO accounts (username, display_name, password_hash, rights) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (username) DO NOTHING RETURNING *",
            username.lowercase(),
            username,
            passwordHash,
            rights.name,
        )

    override fun setRights(id: Long, rights: Rights) = exec("UPDATE accounts SET rights = ? WHERE id = ?", rights.name, id)

    override fun setMuted(id: Long, until: Long) = exec("UPDATE accounts SET muted_until = ? WHERE id = ?", until, id)

    override fun setBanned(id: Long, until: Long) = exec("UPDATE accounts SET banned_until = ? WHERE id = ?", until, id)

    override fun ipBanned(ip: String): Boolean = strings("SELECT ip FROM ip_bans WHERE ip = ?", ip).isNotEmpty()

    override fun setIpBan(ip: String, banned: Boolean) =
        if (banned) exec("INSERT INTO ip_bans (ip) VALUES (?) ON CONFLICT DO NOTHING", ip) else exec("DELETE FROM ip_bans WHERE ip = ?", ip)

    override fun lastIp(id: Long): String? = strings("SELECT last_ip FROM accounts WHERE id = ?", id).firstOrNull()

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
        )

    private fun offers(query: String, vararg args: Any): List<GeOffer> =
        sql { c ->
            c.prepareStatement(query).use { ps ->
                args.forEachIndexed { i, a -> ps.setObject(i + 1, a) }
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.offer()) } }
            }
        }

    override fun offers(id: Long): List<GeOffer> = offers("SELECT * FROM ge_offers WHERE account_id = ? ORDER BY slot", id)

    override fun openOffers(obj: Int, buy: Boolean): List<GeOffer> =
        offers("SELECT * FROM ge_offers WHERE obj = ? AND buy = ? AND completed < quantity ORDER BY id", obj, buy)

    override fun putOffer(offer: GeOffer): GeOffer {
        if (offer.id == 0L) {
            return offers(
                "INSERT INTO ge_offers (account_id, slot, buy, obj, quantity, price, completed, collect_coins, collect_items) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING *",
                offer.accountId,
                offer.slot,
                offer.buy,
                offer.obj,
                offer.quantity,
                offer.price,
                offer.completed,
                offer.collectCoins,
                offer.collectItems,
            ).single()
        }
        exec(
            "UPDATE ge_offers SET completed = ?, collect_coins = ?, collect_items = ? WHERE id = ?",
            offer.completed,
            offer.collectCoins,
            offer.collectItems,
            offer.id,
        )
        return offer
    }

    override fun deleteOffer(offerId: Long) = exec("DELETE FROM ge_offers WHERE id = ?", offerId)

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

    override fun audit(actor: String, action: String, target: String, detail: String) =
        exec("INSERT INTO audit_log (actor, action, target, detail) VALUES (?, ?, ?, ?)", actor, action, target, detail)

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
                """CREATE TABLE IF NOT EXISTS ge_offers (
                    id bigserial PRIMARY KEY, account_id bigint REFERENCES accounts(id), slot int NOT NULL, buy boolean NOT NULL,
                    obj int NOT NULL, quantity int NOT NULL, price int NOT NULL, completed int NOT NULL,
                    collect_coins bigint NOT NULL, collect_items int NOT NULL)""",
                """CREATE TABLE IF NOT EXISTS hiscores (
                    account_id bigint PRIMARY KEY REFERENCES accounts(id), name text NOT NULL, xp int[] NOT NULL)""",
                """CREATE TABLE IF NOT EXISTS audit_log (
                    id bigserial PRIMARY KEY, at timestamptz NOT NULL DEFAULT now(), actor text NOT NULL, action text NOT NULL,
                    target text NOT NULL, detail text NOT NULL)""",
            )
    }
}
