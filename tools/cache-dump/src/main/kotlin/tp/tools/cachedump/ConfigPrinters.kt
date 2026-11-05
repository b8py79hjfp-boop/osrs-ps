package tp.tools.cachedump

import tp.cache.config.Configs
import tp.cache.config.TypeList

/** Collects one [ConfigPrinter] per config type under its command-line name. */
internal class ConfigPrinters {
    val printers = LinkedHashMap<String, ConfigPrinter<*>>()

    fun <T : Any> add(name: String, types: TypeList<T>, fields: (T) -> Fields, brief: (T) -> Fields) {
        printers[name] = ConfigPrinter(types, fields, brief)
    }
}

/** One printer per config type, keyed by the name used on the command line. Field lists are written out by hand (no reflection). */
internal fun configPrinters(c: Configs): Map<String, ConfigPrinter<*>> {
    val registry = ConfigPrinters()
    registry.obj(c)
    registry.loc(c)
    registry.npc(c)
    registry.seq(c)
    registry.spotanim(c)
    registry.varbit(c)
    registry.varp(c)
    registry.enum(c)
    registry.struct(c)
    registry.param(c)
    registry.inv(c)
    registry.dbrow(c)
    registry.dbtable(c)
    registry.underlay(c)
    registry.overlay(c)
    registry.idk(c)
    registry.hitmark(c)
    registry.headbar(c)
    registry.mapelement(c)
    registry.area(c)
    registry.hunt(c)
    return registry.printers
}

private fun ConfigPrinters.obj(c: Configs) {
    add(
        "obj",
        c.objs,
        { t ->
            listOf(
                "name" to t.name,
                "cost" to t.cost,
                "stackable" to t.stackable,
                "members" to t.members,
                "tradeable" to t.tradeable,
                "geTradeable" to t.geTradeable,
                "wearPos1" to t.wearPos1,
                "wearPos2" to t.wearPos2,
                "wearPos3" to t.wearPos3,
                "weight" to t.weight,
                "team" to t.team,
                "category" to t.category,
                "groundOps" to t.groundOps,
                "iops" to t.iops,
                "iopSubOps" to t.iopSubOps,
                "shiftClickDropIndex" to t.shiftClickDropIndex,
                "certLink" to t.certLink,
                "certTemplate" to t.certTemplate,
                "boughtLink" to t.boughtLink,
                "boughtTemplate" to t.boughtTemplate,
                "placeholderLink" to t.placeholderLink,
                "placeholderTemplate" to t.placeholderTemplate,
                "countObj" to t.countObj,
                "countCo" to t.countCo,
                "params" to t.params,
            )
        },
        { t -> listOf("name" to t.name, "cost" to t.cost, "stackable" to t.stackable, "members" to t.members) },
    )
}

private fun ConfigPrinters.loc(c: Configs) {
    add(
        "loc",
        c.locs,
        { t ->
            listOf(
                "name" to t.name,
                "width" to t.width,
                "length" to t.length,
                "blockWalk" to t.blockWalk,
                "blockRange" to t.blockRange,
                "breakRouteFinding" to t.breakRouteFinding,
                "forceApproach" to t.forceApproach,
                "active" to t.active,
                "anim" to t.anim,
                "category" to t.category,
                "mapScene" to t.mapScene,
                "mapArea" to t.mapArea,
                "supportItems" to t.supportItems,
                "multiVarbit" to t.multiVarbit,
                "multiVarp" to t.multiVarp,
                "multiLocs" to t.multiLocs,
                "ops" to t.ops,
                "params" to t.params,
            )
        },
        { t -> listOf("name" to t.name, "size" to "${t.width}x${t.length}", "blockWalk" to t.blockWalk, "blockRange" to t.blockRange) },
    )
}

private fun ConfigPrinters.npc(c: Configs) {
    add(
        "npc",
        c.npcs,
        { t ->
            listOf(
                "name" to t.name,
                "size" to t.size,
                "category" to t.category,
                "combatLevel" to t.combatLevel,
                "ops" to t.ops,
                "standSeq" to t.standSeq,
                "idleRotateLeftSeq" to t.idleRotateLeftSeq,
                "idleRotateRightSeq" to t.idleRotateRightSeq,
                "walkSeq" to t.walkSeq,
                "walkTurnAroundSeq" to t.walkTurnAroundSeq,
                "walkTurnLeftSeq" to t.walkTurnLeftSeq,
                "walkTurnRightSeq" to t.walkTurnRightSeq,
                "runSeq" to t.runSeq,
                "runTurnAroundSeq" to t.runTurnAroundSeq,
                "runTurnLeftSeq" to t.runTurnLeftSeq,
                "runTurnRightSeq" to t.runTurnRightSeq,
                "crawlSeq" to t.crawlSeq,
                "crawlTurnAroundSeq" to t.crawlTurnAroundSeq,
                "crawlTurnLeftSeq" to t.crawlTurnLeftSeq,
                "crawlTurnRightSeq" to t.crawlTurnRightSeq,
                "stats" to t.stats,
                "minimapVisible" to t.minimapVisible,
                "interactable" to t.interactable,
                "rotationFlag" to t.rotationFlag,
                "rotationSpeed" to t.rotationSpeed,
                "height" to t.height,
                "footprintSize" to t.footprintSize,
                "renderPriority" to t.renderPriority,
                "follower" to t.follower,
                "lowPriorityFollowerOps" to t.lowPriorityFollowerOps,
                "headIconArchives" to t.headIconArchives,
                "headIconSprites" to t.headIconSprites,
                "multiVarbit" to t.multiVarbit,
                "multiVarp" to t.multiVarp,
                "multiNpcs" to t.multiNpcs,
                "params" to t.params,
            )
        },
        { t -> listOf("name" to t.name, "size" to t.size, "combatLevel" to t.combatLevel, "standSeq" to t.standSeq) },
    )
}

private fun ConfigPrinters.seq(c: Configs) {
    add(
        "seq",
        c.seqs,
        { t ->
            listOf(
                "frameLengths" to t.frameLengths,
                "frameGroups" to t.frameGroups,
                "frameIndices" to t.frameIndices,
                "frameStep" to t.frameStep,
                "interleaveLeave" to t.interleaveLeave,
                "stretches" to t.stretches,
                "forcedPriority" to t.forcedPriority,
                "maxLoops" to t.maxLoops,
                "precedenceAnimating" to t.precedenceAnimating,
                "priority" to t.priority,
                "replyMode" to t.replyMode,
                "keyframeSet" to t.keyframeSet,
                "keyframeStart" to t.keyframeStart,
                "keyframeEnd" to t.keyframeEnd,
                "keyframeWalkMerge" to t.keyframeWalkMerge,
                "debugName" to t.debugName,
                "soundsCrossWorldView" to t.soundsCrossWorldView,
                "totalFrameCycles" to t.totalFrameCycles,
            )
        },
        { t -> listOf("debugName" to t.debugName, "cycles" to t.totalFrameCycles, "priority" to t.priority) },
    )
}

private fun ConfigPrinters.spotanim(c: Configs) {
    add("spotanim", c.spotAnims, { t -> listOf("seq" to t.seq) }, { t -> listOf("seq" to t.seq) })
}

private fun ConfigPrinters.varbit(c: Configs) {
    add(
        "varbit",
        c.varbits,
        { t -> listOf("baseVar" to t.baseVar, "startBit" to t.startBit, "endBit" to t.endBit) },
        { t -> listOf("baseVar" to t.baseVar, "bits" to "${t.startBit}..${t.endBit}") },
    )
}

private fun ConfigPrinters.varp(c: Configs) {
    add("varp", c.varps, { t -> listOf("clientCode" to t.clientCode) }, { t -> listOf("clientCode" to t.clientCode) })
}

private fun ConfigPrinters.enum(c: Configs) {
    add(
        "enum",
        c.enums,
        { t ->
            listOf(
                "inputType" to t.inputType,
                "outputType" to t.outputType,
                "defaultString" to t.defaultString,
                "defaultInt" to t.defaultInt,
                "defaultLong" to t.defaultLong,
                "intValues" to t.intValues,
                "stringValues" to t.stringValues,
                "longValues" to t.longValues,
                "size" to t.size,
            )
        },
        { t -> listOf("in" to t.inputType, "out" to t.outputType, "size" to t.size) },
    )
}

private fun ConfigPrinters.struct(c: Configs) {
    add("struct", c.structs, { t -> listOf("params" to t.params) }, { t -> listOf("params" to t.params) })
}

private fun ConfigPrinters.param(c: Configs) {
    add(
        "param",
        c.params,
        { t ->
            listOf(
                "typeChar" to t.typeChar,
                "typeId" to t.typeId,
                "defaultInt" to t.defaultInt,
                "defaultLong" to t.defaultLong,
                "defaultString" to t.defaultString,
                "autoDisable" to t.autoDisable,
            )
        },
        { t -> listOf("typeChar" to t.typeChar, "defaultInt" to t.defaultInt, "defaultString" to t.defaultString) },
    )
}

private fun ConfigPrinters.inv(c: Configs) {
    add("inv", c.invs, { t -> listOf("size" to t.size, "params" to t.params) }, { t -> listOf("size" to t.size) })
}

private fun ConfigPrinters.dbrow(c: Configs) {
    add(
        "dbrow",
        c.dbRows,
        { t -> listOf("tableId" to t.tableId, "columnCount" to t.columnCount, "columns" to t.columns) },
        { t -> listOf("tableId" to t.tableId, "columns" to t.columnCount) },
    )
}

private fun ConfigPrinters.dbtable(c: Configs) {
    add(
        "dbtable",
        c.dbTables,
        { t -> listOf("columnCount" to t.columnCount, "columns" to t.columns) },
        { t -> listOf("columns" to t.columnCount) },
    )
}

private fun ConfigPrinters.underlay(c: Configs) {
    add("underlay", c.underlays, { t -> listOf("rgb" to hex(t.rgb)) }, { t -> listOf("rgb" to hex(t.rgb)) })
}

private fun ConfigPrinters.overlay(c: Configs) {
    add(
        "overlay",
        c.overlays,
        { t ->
            listOf(
                "rgb" to hex(t.rgb),
                "texture" to t.texture,
                "hideUnderlay" to t.hideUnderlay,
                "secondaryRgb" to t.secondaryRgb,
            )
        },
        { t -> listOf("rgb" to hex(t.rgb), "texture" to t.texture, "hideUnderlay" to t.hideUnderlay) },
    )
}

private fun ConfigPrinters.idk(c: Configs) {
    add(
        "idk",
        c.idks,
        { t -> listOf("bodyPart" to t.bodyPart, "nonSelectable" to t.nonSelectable) },
        { t -> listOf("bodyPart" to t.bodyPart, "nonSelectable" to t.nonSelectable) },
    )
}

private fun ConfigPrinters.hitmark(c: Configs) {
    add(
        "hitmark",
        c.hitmarks,
        { t ->
            listOf(
                "format" to t.format,
                "displayCycles" to t.displayCycles,
                "useDamage" to t.useDamage,
                "multiVarbit" to t.multiVarbit,
                "multiVarp" to t.multiVarp,
                "multiHitmarks" to t.multiHitmarks,
            )
        },
        { t -> listOf("format" to t.format, "displayCycles" to t.displayCycles) },
    )
}

private fun ConfigPrinters.headbar(c: Configs) {
    add(
        "headbar",
        c.headbars,
        { t ->
            listOf(
                "showPriority" to t.showPriority,
                "hidePriority" to t.hidePriority,
                "fadeout" to t.fadeout,
                "stickTime" to t.stickTime,
                "segments" to t.segments,
                "padding" to t.padding,
            )
        },
        { t -> listOf("segments" to t.segments, "stickTime" to t.stickTime, "fadeout" to t.fadeout) },
    )
}

private fun ConfigPrinters.mapelement(c: Configs) {
    add(
        "mapelement",
        c.mapElements,
        { t -> listOf("name" to t.name, "sprite" to t.sprite, "ops" to t.ops, "category" to t.category) },
        { t -> listOf("name" to t.name, "sprite" to t.sprite, "category" to t.category) },
    )
}

private fun ConfigPrinters.area(c: Configs) {
    add("area", c.areas, { t -> listOf("colour" to hex(t.colour)) }, { t -> listOf("colour" to hex(t.colour)) })
}

private fun ConfigPrinters.hunt(c: Configs) {
    add(
        "hunt",
        c.hunts,
        { t ->
            listOf(
                "type" to t.type,
                "checkVis" to t.checkVis,
                "checkNotTooStrong" to t.checkNotTooStrong,
                "checkNotBusy" to t.checkNotBusy,
                "findKeepHunting" to t.findKeepHunting,
                "findNewMode" to t.findNewMode,
                "nobodyNear" to t.nobodyNear,
                "checkNotCombat" to t.checkNotCombat,
                "checkNotCombatSelf" to t.checkNotCombatSelf,
                "checkAfk" to t.checkAfk,
                "rate" to t.rate,
                "checkEntity" to t.checkEntity,
                "checkInv" to t.checkInv,
                "checkVar" to t.checkVar,
            )
        },
        { t -> listOf("type" to t.type, "rate" to t.rate) },
    )
}

private fun hex(value: Int): String = "0x" + Integer.toHexString(value)
