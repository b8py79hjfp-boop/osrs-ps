package tp.content.sailing

/**
 * World-scoped state of the Sailing content: the plugin instance belongs to one world, and its tables exist only once the
 * world has started ([Sailing] fills this in `onWorldStart`). Handlers read it when they run, never when they register.
 */
internal class SailingState {
    lateinit var data: SailingData
    lateinit var boats: Boats
    lateinit var ports: PortSites
    lateinit var catalog: PortCatalog
    lateinit var shipyard: Shipyard
    lateinit var services: BoatServices
    lateinit var charting: SeaCharting
    val sites: DockSites get() = boats.sites
}
