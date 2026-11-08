package tp.plugin

import tp.engine.TradingPostRequest

/** Trading post calls (docs/mechanics/trading-post.md); see [PlayerRef.tradingPost]. */
public sealed interface TpRequest {
    /** Lists [quantity] [obj] at [price] each (items already taken by the caller's transaction). */
    public class Listing(public val obj: Int, public val quantity: Int, public val price: Int, public val maxListings: Int) : TpRequest

    public class Cancel(public val id: Long) : TpRequest

    /** [objs] / [seller] null = any. */
    public class Search(public val objs: Set<Int>?, public val seller: String?, public val limit: Int) : TpRequest

    public data object Mine : TpRequest

    /** Buys up to [quantity] at [price] each (coins already taken by the caller's transaction). */
    public class Buy(public val id: Long, public val quantity: Int, public val price: Int) : TpRequest

    public data object Collect : TpRequest

    public data object Pending : TpRequest

    public data object History : TpRequest
}

public class TpListingView(
    public val id: Long,
    public val accountId: Long,
    public val seller: String,
    public val obj: Int,
    public val quantity: Int,
    public val sold: Int,
    public val price: Int,
    public val listedAtMs: Long,
) {
    public val remaining: Int get() = quantity - sold
}

public class TpTradeView(
    public val obj: Int,
    public val quantity: Int,
    public val price: Int,
    public val bought: Boolean,
    public val other: String,
    public val atMs: Long,
)

public class TpReply(
    public val listings: List<TpListingView>,
    public val bought: Int,
    public val error: String?,
    public val coins: Long,
    public val items: Map<Int, Int>,
    public val trades: List<TpTradeView>,
)

/** Sends [request] to the hub; [reply] runs on the world thread when it answers (null: no hub or the call failed). */
public fun PlayerRef.tradingPost(request: TpRequest, reply: PlayerRef.(TpReply?) -> Unit) {
    val bridge = p.world.accounts ?: return reply(null)
    val engineRequest =
        when (request) {
            is TpRequest.Listing -> TradingPostRequest.Listing(request.obj, request.quantity, request.price, request.maxListings)
            is TpRequest.Cancel -> TradingPostRequest.Cancel(request.id)
            is TpRequest.Search -> TradingPostRequest.Search(request.objs, request.seller, request.limit)
            TpRequest.Mine -> TradingPostRequest.Mine
            is TpRequest.Buy -> TradingPostRequest.Buy(request.id, request.quantity, request.price)
            TpRequest.Collect -> TradingPostRequest.Collect
            TpRequest.Pending -> TradingPostRequest.Pending
            TpRequest.History -> TradingPostRequest.History
        }
    bridge.tradingPost(p, engineRequest) { player, r ->
        val view =
            r?.let {
                TpReply(
                    it.listings.map { l -> TpListingView(l.id, l.accountId, l.seller, l.obj, l.quantity, l.sold, l.price, l.listedAtMs) },
                    it.bought,
                    it.error,
                    it.coins,
                    it.items,
                    it.trades.map { t -> TpTradeView(t.obj, t.quantity, t.price, t.bought, t.other, t.atMs) },
                )
            }
        refOf(player).reply(view)
    }
}
