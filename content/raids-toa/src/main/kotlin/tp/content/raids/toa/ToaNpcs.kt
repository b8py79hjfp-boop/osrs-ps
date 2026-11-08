package tp.content.raids.toa

/** ToA NPC groups (cache 2757 ids, client-contract 8; scaling sets from the wiki DPS calculator's ToA lists, TA28-TA33). */
internal object ToaNpcs {
    val AKKHA = (11789..11796).toSet()
    val AKKHA_SHADOWS = setOf(11797, 11798, 11799)
    val BABA = setOf(11778, 11779, 11780)

    /** Kephri's forms: shielded, weakened, enraged, dead (client-contract 8). */
    val KEPHRI = setOf(11719, 11720, 11721, 11722)

    /** The overlord scarabs: soldier, spitting, arcane. */
    val OVERLORDS = setOf(11724, 11725, 11726)
    val ZEBAK = setOf(11730, 11732, 11733)
    val OBELISKS = setOf(11750, 11751, 11752)
    val P2_WARDENS = setOf(11753, 11754, 11756, 11757)
    val EJECTED_CORES = setOf(11755, 11758)
    val P3_WARDENS = setOf(11761, 11762, 11763, 11764)

    /** TA28: the NPCs path levels scale. */
    val PATH_SCALED = AKKHA + AKKHA_SHADOWS + BABA + KEPHRI + OVERLORDS + ZEBAK

    fun pathOf(type: Int): ToaPath? =
        when (type) {
            in AKKHA, in AKKHA_SHADOWS -> ToaPath.HET
            in BABA -> ToaPath.APMEKEN
            in KEPHRI, in OVERLORDS -> ToaPath.SCABARAS
            in ZEBAK -> ToaPath.CRONDIS
            else -> null
        }
}
