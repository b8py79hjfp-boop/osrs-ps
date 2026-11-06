package tp.protocol.osrs242

import io.netty.channel.Channel
import org.jire.netty.haproxy.HAProxyAttributes.haproxyAttribute
import java.net.InetSocketAddress

/** The client's IP for a channel: the PROXY-protocol source address when a trusted proxy sent one, else the peer. */
internal object ClientAddress {
    fun of(channel: Channel): String =
        channel.haproxyAttribute?.sourceHost ?: (channel.remoteAddress() as? InetSocketAddress)?.address?.hostAddress ?: "unknown"
}
