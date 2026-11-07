package tp.content.diaries

import tp.plugin.Stats

/**
 * Reads `diary-triggers.tsv` (written by tools/wiki-import/achievement_diaries.py): one row per hooked task,
 * `area  tier  index  label  parts`. [label] is a word of the Wiki task text, checked at world start so a renumbered Wiki page
 * cannot silently move a trigger to the wrong task. `parts` are joined by ` && `; a part is `kind;key=value;...` with the keys
 * `names` (cache names, `|`-separated), `ids`, `op` (number or text), `stat`, `xp`, `in` and `dest` (boxes `x1,z1,x2,z2[,level]`,
 * `|`-separated), `reason`, `count`, `min`, `worn`, `target`, `wild` and `seq` (loc ids, a step's alternatives `/`-separated).
 * `names=*` is any item.
 */
internal object DiaryTriggerData {
    private const val TSV = "diary-triggers.tsv"
    private const val COLUMNS = 5
    private const val PART_SEPARATOR = " && "

    fun load(data: DiaryData): List<TriggerTask> {
        val text = DiaryTriggerData::class.java.getResource(TSV)?.readText() ?: error("$TSV missing")
        val out = ArrayList<TriggerTask>()
        for (line in text.lines().drop(1)) {
            if (line.isEmpty()) continue
            val c = line.split('\t')
            check(c.size >= COLUMNS) { "$TSV: expected $COLUMNS columns in '$line'" }
            val id = TaskId.of(c[0].toInt(), c[1].toInt(), c[2].toInt())
            val task = checkNotNull(data.task(id)) { "$TSV: no task ${id.raw}" }
            check(task.text.contains(c[3], ignoreCase = true)) { "$TSV: ${id.raw} expects '${c[3]}' in '${task.text}'" }
            val parts = c[4].split(PART_SEPARATOR).map(::part)
            check(parts.size <= MAX_PARTS) { "$TSV: ${id.raw} has more than $MAX_PARTS parts" }
            out += TriggerTask(id, c[3], parts)
        }
        return out
    }

    private const val MAX_PARTS = 8

    private fun part(spec: String): Part {
        val fields = spec.split(';')
        val kind = PartKind.valueOf(fields[0].trim().uppercase())
        val kv = HashMap<String, String>()
        for (f in fields.drop(1)) kv[f.substringBefore('=')] = f.substringAfter('=')
        val op = kv["op"]
        val opNumber = op?.toIntOrNull() ?: -1
        val steps = kv["seq"]?.split(',')?.map { step -> step.split('/').map { it.trim().toInt() } }.orEmpty()
        return Part(
            kind = kind,
            names =
                kv["names"]
                    ?.takeIf { it != "*" }
                    ?.split('|')
                    ?.map(DiaryHooks::key)
                    ?.toSet()
                    .orEmpty(),
            ids =
                kv["ids"]
                    ?.split(',')
                    ?.map { it.trim().toInt() }
                    ?.toSet()
                    .orEmpty(),
            opNumber = opNumber,
            opText = if (op != null && opNumber < 0) op else null,
            stat = kv["stat"]?.let(::stat) ?: -1,
            xp = kv["xp"]?.toDouble() ?: Double.NaN,
            boxes = kv["in"]?.split('|')?.map(::box).orEmpty(),
            dest = kv["dest"]?.let(::box),
            reason = kv["reason"],
            count = kv["count"]?.toInt() ?: 0,
            min = kv["min"]?.toInt() ?: 0,
            worn = kv["worn"]?.split('|')?.map(DiaryHooks::key).orEmpty(),
            target =
                kv["target"]
                    ?.split('|')
                    ?.map(DiaryHooks::key)
                    ?.toSet()
                    .orEmpty(),
            seq = steps.map { it.first() }.toIntArray(),
            alias = steps.flatMap { s -> s.drop(1).map { it to s.first() } }.toMap(),
            wild = kv["wild"] == "1",
            any = kv["names"] == "*",
        )
    }

    private fun stat(name: String): Int =
        (0 until Stats.COUNT).firstOrNull { Stats.name(it).equals(name, ignoreCase = true) } ?: error("unknown stat '$name'")

    private fun box(spec: String): Box {
        val n = spec.split(',').map { it.trim().toInt() }
        return Box(n[0], n[1], n[2], n[3], n.getOrElse(4) { -1 })
    }
}
