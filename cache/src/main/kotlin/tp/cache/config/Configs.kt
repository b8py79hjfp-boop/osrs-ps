package tp.cache.config

import tp.cache.config.area.AreaType
import tp.cache.config.area.AreaTypeDecoder
import tp.cache.config.dbrow.DbRowType
import tp.cache.config.dbrow.DbRowTypeDecoder
import tp.cache.config.dbtable.DbTableIndex
import tp.cache.config.dbtable.DbTableIndexLoader
import tp.cache.config.dbtable.DbTableType
import tp.cache.config.dbtable.DbTableTypeDecoder
import tp.cache.config.enum.EnumType
import tp.cache.config.enum.EnumTypeDecoder
import tp.cache.config.headbar.HeadbarType
import tp.cache.config.headbar.HeadbarTypeDecoder
import tp.cache.config.hitmark.HitmarkType
import tp.cache.config.hitmark.HitmarkTypeDecoder
import tp.cache.config.hunt.HuntType
import tp.cache.config.hunt.HuntTypeDecoder
import tp.cache.config.idk.IdkType
import tp.cache.config.idk.IdkTypeDecoder
import tp.cache.config.inv.InvType
import tp.cache.config.inv.InvTypeDecoder
import tp.cache.config.loc.LocType
import tp.cache.config.loc.LocTypeDecoder
import tp.cache.config.mapelement.MapElementType
import tp.cache.config.mapelement.MapElementTypeDecoder
import tp.cache.config.npc.NpcType
import tp.cache.config.npc.NpcTypeDecoder
import tp.cache.config.obj.ObjType
import tp.cache.config.obj.ObjTypeDecoder
import tp.cache.config.obj.ObjTypes
import tp.cache.config.overlay.OverlayType
import tp.cache.config.overlay.OverlayTypeDecoder
import tp.cache.config.param.ParamType
import tp.cache.config.param.ParamTypeDecoder
import tp.cache.config.seq.SeqType
import tp.cache.config.seq.SeqTypeDecoder
import tp.cache.config.spotanim.SpotAnimType
import tp.cache.config.spotanim.SpotAnimTypeDecoder
import tp.cache.config.struct.StructType
import tp.cache.config.struct.StructTypeDecoder
import tp.cache.config.underlay.UnderlayType
import tp.cache.config.underlay.UnderlayTypeDecoder
import tp.cache.config.varbit.VarbitType
import tp.cache.config.varbit.VarbitTypeDecoder
import tp.cache.config.varp.VarpType
import tp.cache.config.varp.VarpTypeDecoder
import tp.cache.js5.Js5Cache

/**
 * Every decoded config, loaded eagerly at boot (Phase 1 plan: lazy decoding would hide cache drift).
 * Immutable after [load]; shared by all worlds in the JVM.
 *
 * Archive 2 groups with no reference decoder (varclientstring 15, varclient 19, varobj 20, gamelogevent 70) are not
 * decoded; see [ConfigGroup.UNDECODED].
 */
class Configs private constructor(
    val objs: TypeList<ObjType>,
    val locs: TypeList<LocType>,
    val npcs: TypeList<NpcType>,
    val seqs: TypeList<SeqType>,
    val spotAnims: TypeList<SpotAnimType>,
    val varbits: TypeList<VarbitType>,
    val varps: TypeList<VarpType>,
    val enums: TypeList<EnumType>,
    val structs: TypeList<StructType>,
    val params: TypeList<ParamType>,
    val invs: TypeList<InvType>,
    val dbRows: TypeList<DbRowType>,
    val dbTables: TypeList<DbTableType>,
    val underlays: TypeList<UnderlayType>,
    val overlays: TypeList<OverlayType>,
    val idks: TypeList<IdkType>,
    val hitmarks: TypeList<HitmarkType>,
    val headbars: TypeList<HeadbarType>,
    val mapElements: TypeList<MapElementType>,
    val areas: TypeList<AreaType>,
    val hunts: TypeList<HuntType>,
    val dbTableIndexes: List<DbTableIndex>,
) {
    /** (name, decoded count) per config, for boot logs and the cache-dump tool. */
    fun summary(): List<Pair<String, Int>> =
        listOf(
            "obj" to objs.count,
            "loc" to locs.count,
            "npc" to npcs.count,
            "seq" to seqs.count,
            "spotanim" to spotAnims.count,
            "varbit" to varbits.count,
            "varp" to varps.count,
            "enum" to enums.count,
            "struct" to structs.count,
            "param" to params.count,
            "inv" to invs.count,
            "dbrow" to dbRows.count,
            "dbtable" to dbTables.count,
            "underlay" to underlays.count,
            "overlay" to overlays.count,
            "idk" to idks.count,
            "hitmark" to hitmarks.count,
            "headbar" to headbars.count,
            "mapelement" to mapElements.count,
            "area" to areas.count,
            "hunt" to hunts.count,
            DbTableIndexLoader.NAME to dbTableIndexes.size,
        )

    companion object {
        fun load(cache: Js5Cache): Configs {
            val objs = ConfigLoader.load(cache, ConfigGroup.OBJ, ObjTypeDecoder).also(ObjTypes::link)
            val locs = ConfigLoader.load(cache, ConfigGroup.LOC, LocTypeDecoder)
            val npcs = ConfigLoader.load(cache, ConfigGroup.NPC, NpcTypeDecoder)
            val seqs = ConfigLoader.load(cache, ConfigGroup.SEQ, SeqTypeDecoder)
            val spotAnims = ConfigLoader.load(cache, ConfigGroup.SPOTANIM, SpotAnimTypeDecoder)
            val varbits = ConfigLoader.load(cache, ConfigGroup.VARBIT, VarbitTypeDecoder)
            val varps = ConfigLoader.load(cache, ConfigGroup.VARP, VarpTypeDecoder)
            val enums = ConfigLoader.load(cache, ConfigGroup.ENUM, EnumTypeDecoder)
            val structs = ConfigLoader.load(cache, ConfigGroup.STRUCT, StructTypeDecoder)
            val params = ConfigLoader.load(cache, ConfigGroup.PARAM, ParamTypeDecoder)
            val invs = ConfigLoader.load(cache, ConfigGroup.INV, InvTypeDecoder)
            val dbRows = ConfigLoader.load(cache, ConfigGroup.DBROW, DbRowTypeDecoder)
            val dbTables = ConfigLoader.load(cache, ConfigGroup.DBTABLE, DbTableTypeDecoder)
            val underlays = ConfigLoader.load(cache, ConfigGroup.UNDERLAY, UnderlayTypeDecoder)
            val overlays = ConfigLoader.load(cache, ConfigGroup.OVERLAY, OverlayTypeDecoder)
            val idks = ConfigLoader.load(cache, ConfigGroup.IDK, IdkTypeDecoder)
            val hitmarks = ConfigLoader.load(cache, ConfigGroup.HITMARK, HitmarkTypeDecoder)
            val headbars = ConfigLoader.load(cache, ConfigGroup.HEADBAR, HeadbarTypeDecoder)
            val mapElements = ConfigLoader.load(cache, ConfigGroup.MAPELEMENT, MapElementTypeDecoder)
            val areas = ConfigLoader.load(cache, ConfigGroup.AREA, AreaTypeDecoder)
            val hunts = ConfigLoader.load(cache, ConfigGroup.HUNT, HuntTypeDecoder)
            val dbTableIndexes = DbTableIndexLoader.load(cache)
            return Configs(
                objs,
                locs,
                npcs,
                seqs,
                spotAnims,
                varbits,
                varps,
                enums,
                structs,
                params,
                invs,
                dbRows,
                dbTables,
                underlays,
                overlays,
                idks,
                hitmarks,
                headbars,
                mapElements,
                areas,
                hunts,
                dbTableIndexes,
            )
        }
    }
}
