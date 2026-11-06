package tp.protocol.osrs242

import io.netty.channel.Channel
import java.net.InetSocketAddress

/** The client's IP for a channel (the real client IP when a PROXY-protocol proxy is in front: see [Osrs242Network]). */
internal object ClientAddress {
    fun of(channel: Channel): String = (channel.remoteAddress() as? InetSocketAddress)?.address?.hostAddress ?: "unknown"
}
