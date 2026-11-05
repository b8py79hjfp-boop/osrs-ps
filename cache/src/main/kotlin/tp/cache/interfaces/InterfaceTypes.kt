package tp.cache.interfaces

import tp.cache.config.Js5Archive
import tp.cache.js5.Js5Cache
import tp.util.buf.ReadBuf
import java.io.IOException

/**
 * Every interface of cache archive 3, loaded eagerly at boot. Immutable after [load]; shared by all worlds in the JVM.
 * Group id = interface id, file id = component id.
 */
class InterfaceTypes private constructor(private val interfaces: Map<Int, InterfaceType>) : Iterable<InterfaceType> {
    val interfaceCount: Int get() = interfaces.size
    val componentCount: Int get() = interfaces.values.sumOf { it.components.size }
    val if3ComponentCount: Int get() = interfaces.values.sumOf { i -> i.components.values.count { it.isIf3 } }

    operator fun get(interfaceId: Int): InterfaceType? = interfaces[interfaceId]

    /** The component with [packedId] (`(interface shl 16) or component`), or null. */
    fun component(packedId: Int): ComponentType? =
        interfaces[packedId ushr ComponentType.COMPONENT_BITS]?.components?.get(packedId and COMPONENT_MASK)

    override fun iterator(): Iterator<InterfaceType> = interfaces.values.iterator()

    companion object {
        private const val COMPONENT_MASK = 0xFFFF

        /** Decodes every component file of every group in archive 3. Each file must be fully consumed. */
        fun load(cache: Js5Cache): InterfaceTypes {
            val groupIds = cache.index(Js5Archive.INTERFACES).groupIds
            val interfaces = HashMap<Int, InterfaceType>(groupIds.size * 2)
            for (interfaceId in groupIds) {
                val files = cache.readFiles(Js5Archive.INTERFACES, interfaceId) ?: throw IOException("Interface $interfaceId is absent")
                val components = HashMap<Int, ComponentType>(files.size * 2)
                for (i in 0 until files.size) {
                    val componentId = files.ids[i]
                    val buf = ReadBuf(files.data[i])
                    components[componentId] = ComponentTypeDecoder.decode(interfaceId, componentId, buf)
                    if (buf.isReadable) throw IOException("interface $interfaceId component $componentId: ${buf.remaining} unread bytes")
                }
                interfaces[interfaceId] = InterfaceType(interfaceId, components)
            }
            return InterfaceTypes(interfaces)
        }
    }
}
