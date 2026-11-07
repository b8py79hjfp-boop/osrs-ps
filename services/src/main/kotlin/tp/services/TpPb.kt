package tp.services

import tp.services.grpc.TpBuyReply
import tp.services.grpc.TpCollectPb
import tp.services.grpc.TpListingPb
import tp.services.grpc.TpListings
import tp.services.grpc.TpMaybeListing
import tp.services.grpc.TpTradePb
import tp.services.grpc.TpTrades

/** Trading post model <-> gRPC messages, shared by [GrpcHubClient] and the hub's gRPC server. */
object TpPb {
    fun listing(l: TpListing): TpListingPb =
        TpListingPb
            .newBuilder()
            .setId(l.id)
            .setAccountId(l.accountId)
            .setSeller(l.seller)
            .setObj(l.obj)
            .setQuantity(l.quantity)
            .setSold(l.sold)
            .setPrice(l.price)
            .setListedAt(l.listedAtMs)
            .setClosed(l.closed)
            .build()

    fun listing(p: TpListingPb): TpListing =
        TpListing(
            p.id,
            p.accountId,
            p.seller,
            p.obj,
            p.quantity,
            p.sold,
            p.price,
            p.listedAt,
            p.closed,
        )

    fun listings(ls: List<TpListing>): TpListings = TpListings.newBuilder().addAllListings(ls.map { listing(it) }).build()

    fun maybe(l: TpListing?): TpMaybeListing =
        if (l == null) {
            TpMaybeListing.getDefaultInstance()
        } else {
            TpMaybeListing
                .newBuilder()
                .setPresent(true)
                .setListing(listing(l))
                .build()
        }

    fun maybe(p: TpMaybeListing): TpListing? = if (p.present) listing(p.listing) else null

    fun collect(c: TpCollect): TpCollectPb =
        TpCollectPb
            .newBuilder()
            .setCoins(c.coins)
            .addAllObjs(c.items.keys)
            .addAllCounts(c.items.values)
            .build()

    fun collect(p: TpCollectPb): TpCollect = TpCollect(p.coins, p.objsList.zip(p.countsList).toMap())

    fun buy(r: TpBuyResult): TpBuyReply =
        TpBuyReply
            .newBuilder()
            .setBought(r.bought)
            .setHasListing(r.listing != null)
            .apply { r.listing?.let { setListing(listing(it)) } }
            .setError(r.error.orEmpty())
            .build()

    fun buy(p: TpBuyReply): TpBuyResult = TpBuyResult(p.bought, if (p.hasListing) listing(p.listing) else null, p.error.ifEmpty { null })

    fun trades(ts: List<TpTrade>): TpTrades =
        TpTrades
            .newBuilder()
            .addAllTrades(
                ts.map {
                    TpTradePb
                        .newBuilder()
                        .setAccountId(it.accountId)
                        .setObj(it.obj)
                        .setQuantity(it.quantity)
                        .setPrice(it.price)
                        .setBought(it.bought)
                        .setOther(it.other)
                        .setAt(it.atMs)
                        .build()
                },
            ).build()

    fun trades(p: TpTrades): List<TpTrade> =
        p.tradesList.map {
            TpTrade(it.accountId, it.obj, it.quantity, it.price, it.bought, it.other, it.at)
        }
}
