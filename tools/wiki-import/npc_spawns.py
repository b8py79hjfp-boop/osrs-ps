#!/usr/bin/env python3
"""
NPC spawns from the OSRS Wiki (docs/mechanics/npc-spawns.md).

The cache has no spawn data (it's server-side). The Wiki's {{LocLine}} templates on NPC and monster pages list where
each NPC stands: name, combat level(s), plane and pin coordinates (`x:3094,y:3849|...`). LocLines carry no NPC id,
so each line is matched to cache NPCs: candidates are the ids from the same page's {{Infobox Monster}} /
{{Infobox NPC}} (all versions) whose cache name and combat level match the line; failing that, any cache NPC with that
name and level. When a line matches several versions, its pins rotate through them (one id per version: the first).
`mtype = polygon/line` LocLines describe areas, not spawn points, and are skipped.
Pages without LocLines (most non-combat NPCs) give their location in the infobox `map = {{Map|x,y|...}}` field (per
version: `map1`, `id1`, ...): a pin or rectangle centre is the spawn, a polygon's centre of its bounding box; the wander
range is half the rectangle/polygon's smaller side (rectX/rectY), at most 5; pins and squares (`r` is the
map's display radius, not a wander area) don't wander and every pin of a multi-pin map is a spawn. Maps come in two spellings: positional `3185,3441` pairs (also `x:..,y:..`)
and keywords `x=3185|y=3441|r=9|mtype=square`.

Bankers: the "Banker" page itself has `map = No` and no LocLines, so only the banks with their own "Banker (...)" page have
banker tiles on the Wiki. The optional third argument is the file written by `cache-dump banker-spawns` (every cache
"Bank booth" with its free neighbours and collision evidence); `banker_rows` puts a banker (wander 0) behind each booth the
Wiki's banker tiles don't cover (docs/mechanics/npc-spawns.md SP4).

Output: tab-separated `npc x z level wander` rows (header first), sorted, duplicates removed. LocLine pins use wander
-1 (= the server's default wander range).

Usage (needs network access to oldschool.runescape.wiki; cache NPC list from cache-dump):
  ./gradlew -q :tools:cache-dump:run --args="config npc --all" > npcs.txt
  ./gradlew -q :tools:cache-dump:run --args="banker-spawns docs/research/banker-spawns.tsv"
  python3 -I tools/wiki-import/npc_spawns.py npcs.txt content/world/src/main/resources/tp/content/world/npc-spawns.tsv docs/research/banker-spawns.tsv

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import json
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
UA = "tickperfect-wiki-import/1.0 (private server dev tool)"


def api(params):
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json"})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except Exception:  # noqa: BLE001 - retry transient network errors
            time.sleep(2 ** attempt)
    raise RuntimeError("wiki request failed: " + url)


def pages_using(template):
    titles, cont = [], {}
    while True:
        d = api({"action": "query", "list": "embeddedin", "eititle": template, "einamespace": 0, "eilimit": 500, **cont})
        titles += [p["title"] for p in d["query"]["embeddedin"]]
        if "continue" not in d:
            return titles
        cont = d["continue"]


def wikitexts(titles):
    out = {}
    for i in range(0, len(titles), 50):
        d = api({"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
                 "titles": "|".join(titles[i:i + 50])})
        for page in d["query"]["pages"].values():
            revs = page.get("revisions")
            if revs:
                out[page["title"]] = revs[0]["slots"]["main"]["*"]
        time.sleep(0.2)
    return out


def templates(text, name):
    """Bodies of every {{name ...}} (brace-balanced)."""
    out, start, opener = [], 0, "{{" + name
    while True:
        i = text.find(opener, start)
        if i < 0:
            return out
        after = text[i + len(opener):i + len(opener) + 1]
        if after not in ("|", "\n", " ", "}"):
            start = i + len(opener)
            continue
        depth, j = 0, i
        while j < len(text):
            if text.startswith("{{", j):
                depth += 1
                j += 2
            elif text.startswith("}}", j):
                depth -= 1
                j += 2
                if depth == 0:
                    break
            else:
                j += 1
        out.append(text[i + len(opener):j - 2])
        start = j


def split_params(body):
    parts, depth, cur, i = [], 0, [], 0
    while i < len(body):
        two = body[i:i + 2]
        if two in ("{{", "[["):
            depth += 1
            cur.append(two)
            i += 2
            continue
        if two in ("}}", "]]"):
            depth -= 1
            cur.append(two)
            i += 2
            continue
        if body[i] == "|" and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(body[i])
        i += 1
    parts.append("".join(cur))
    return [p.strip() for p in parts]


def clean(v):
    v = re.sub(r"<ref[^>]*/>", "", v)
    v = re.sub(r"<ref[^>]*>.*?</ref>", "", v, flags=re.S)
    v = re.sub(r"<!--.*?-->", "", v, flags=re.S)
    v = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", v)
    return v.strip()


def load_cache_npcs(path):
    """id -> (name, combat level) from cache-dump `config npc --all`."""
    out = {}
    for line in open(path):
        m = re.match(r'(\d+) name="(.*)" size=\d+ combatLevel=(-?\d+)', line)
        if m:
            out[int(m.group(1))] = (m.group(2), int(m.group(3)))
    return out


def infobox_versions(text):
    """List of id lists, one per infobox version, from Infobox Monster / Infobox NPC on the page."""
    versions = []
    for name in ("Infobox Monster", "Infobox NPC"):
        for body in templates(text, name):
            params = {}
            for p in split_params(body):
                if "=" in p:
                    k, v = p.split("=", 1)
                    params[k.strip().lower()] = clean(v)
            keys = sorted(k for k in params if re.fullmatch(r"id\d*", k))
            for k in keys:
                ids = [int(x) for x in re.findall(r"\d+", params[k])]
                if ids:
                    versions.append(ids)
    return versions


def infobox_maps(text):
    """(id, x, z, plane, wander) per infobox version with a {{Map}} in its map field."""
    out = []
    for name in ("Infobox NPC", "Infobox Monster"):
        for body in templates(text, name):
            params = {}
            for p in split_params(body):
                if "=" in p:
                    k, v = p.split("=", 1)
                    params[k.strip().lower()] = v.strip()
            suffixes = sorted({m.group(1) for k in params for m in [re.fullmatch(r"(?:id|map)(\d*)", k)] if m}) or [""]
            shared_used = False
            for sfx in suffixes:
                ids = [int(x) for x in re.findall(r"\d+", clean(params.get("id" + sfx, params.get("id", ""))))]
                if "map" + sfx in params:
                    raw = params["map" + sfx]
                elif shared_used:
                    continue  # one `map` shared by several id versions: the NPC stands there once, not once per version
                else:
                    raw = params.get("map", "")
                    shared_used = bool(raw.strip())
                for m in templates(raw, "Map"):
                    mp = split_params(m)
                    pts = [(int(m2.group(1)), int(m2.group(2))) for q in mp for m2 in [re.fullmatch(r"(\d+),(\d+)", q)] if m2]
                    pts += [(int(a), int(b)) for q in mp for a, b in re.findall(r"x:(\d+),y:(\d+)", q)]
                    kv = {q.split("=", 1)[0].strip().lower(): q.split("=", 1)[1].strip() for q in mp if "=" in q}
                    # Keyword form: {{Map|x=3185|y=3441|r=9|mtype=square}} (a centre and a radius).
                    if not pts and re.fullmatch(r"\d+", kv.get("x", "")) and re.fullmatch(r"\d+", kv.get("y", "")):
                        pts = [(int(kv["x"]), int(kv["y"]))]
                    if not pts or not ids:
                        continue
                    plane = int(re.sub(r"[^0-9]", "", kv.get("plane", "0")) or 0)
                    xs, zs = [p[0] for p in pts], [p[1] for p in pts]
                    mtype = kv.get("mtype", "pin").lower() or "pin"
                    if mtype == "line":
                        continue
                    if mtype in ("pin", ""):
                        # Every pin is a spawn (the Wiki lists all stand tiles of the NPC), none wanders.
                        for px, pz in dict.fromkeys(pts):
                            out.append((ids[0], px, pz, plane, 0))
                        break
                    x, z = (min(xs) + max(xs)) // 2, (min(zs) + max(zs)) // 2
                    if mtype == "rectangle":
                        rx = int(re.sub(r"[^0-9]", "", kv.get("rectx", "0")) or 0)
                        rz = int(re.sub(r"[^0-9]", "", kv.get("recty", "0")) or 0)
                        wander = min(5, min(rx, rz) // 2)
                    elif mtype == "polygon":
                        wander = min(5, min(max(xs) - min(xs), max(zs) - min(zs)) // 2)
                    else:
                        wander = 0
                    out.append((ids[0], x, z, plane, wander))
                    break
    return out


def levels_of(v):
    v = clean(v or "")
    nums = [int(n) for n in re.findall(r"\d+", v)]
    return nums


# Callisto, Venenatis, Vet'ion (both forms), Artio, Calvar'ion (both forms), Spindel: content/pvp Lairs spawns them.
# Misthalin Mystery's visible forms of Mandy (kitchen, outside) and the shady figure: content/quests QuestSpawnsB spawns their
# multi-NPCs (7433, 7434, 7435) so the quest variable shows and hides them.
CONTENT_SPAWNED = {6609, 6610, 6611, 6612, 11992, 11993, 11994, 11998, 7629, 7630, 7638, 10570, 10571, 10576, 10612, 10613, 10572, 10574, 10575}  # Tempoross plugin: the pool, the crates, the retired sailor, Gita, and the boss forms (instanced)


# Banker (cache name "Banker", ops Talk-to / Bank / Collect): the male and the female model, alternating with the tile parity.
# Grand Exchange centre (Wiki "Grand Exchange": 4 bankers, 4 clerks; "Grand Exchange Clerk" map polygon 3163..3167 x
# 3488..3492): bankers stand behind the "Bank" booths (loc 10060, west / east sides), clerks behind the "Exchange" booths
# (loc 10061, south / north sides). The polygon gives no per-NPC tiles, so these are placed here.
MANUAL_SPAWNS = [
    (1633, 3163, 3489), (1634, 3163, 3490), (1633, 3166, 3489), (1634, 3166, 3490),
    (2148, 3164, 3488), (2149, 3165, 3488), (2150, 3164, 3491), (2151, 3165, 3491),
]
# Replaced by MANUAL_SPAWNS: the clerk polygon's centre pin.
MANUAL_REPLACES = {(2148, 3165, 3490)}

# Pages about other game modes (Deadman Mode and Deadman: All Stars, Leagues, Grid Master, the RuneScape 2 beta, beta
# tests) describe NPCs that never stand in the main game: e.g. "Guard (Deadman Mode)" (ids 3361, 6574..6583, combat level
# 1337 and no animations in the cache) put Deadman guards in Lumbridge, Falador and the Gnome Stronghold.
OTHER_MODE = re.compile(r"\((Deadman[^)]*|[^)]*League|Grid Master|RuneScape 2 Beta|[^)]*beta)\)$|^Leagues? |Tournament Guide$", re.I)

BANKER_IDS = (1613, 1618)

# Banker offset from its booth, named as the `open` digits (W, N, E, S) and the `evidence` letter of cache-dump's
# banker-spawns file.
BANKER_SIDES = {"W": (-1, 0), "N": (0, 1), "E": (1, 0), "S": (0, -1)}


def banker_rows(path, wiki):
    """
    Bankers behind the bank booths that the Wiki's own banker tiles (`wiki`, a set of (x, z, level)) don't cover.
    `path` is cache-dump's `banker-spawns` file: `level x z loc angle open evidence` per booth. Which side of a booth the
    banker stands on depends on the booth model (loc id) and is turned clockwise by the loc angle, so it is learned from
    the booths that have a Wiki banker next to them. The side of a booth is, in order: the one all learned booths of its
    loc id and angle share; the one most learned booths of its loc id give once turned back to angle 0, if one side
    leads; else the collision map's evidence (which also overrules a side learned from one booth only; a side learned from
    several booths that the evidence contradicts gives no banker). Booths with
    none of these get no banker (Wiki-covered booths neither). Leave-one-out over the 37 learned booths: 28 right, 5 wrong, 4 without a prediction.
    """
    booths = []
    for line in list(open(path))[1:]:
        level, x, z, loc, angle, free, evidence = line.split()
        booths.append((int(level), int(x), int(z), int(loc), int(angle), free, evidence))
    cw = "NESW"  # banker offsets in clockwise order: one loc angle step turns the side one place on

    def next_to(b):
        return [k for k, (dx, dz) in BANKER_SIDES.items() if (b[1] + dx, b[2] + dz, b[0]) in wiki]

    exact, turned = {}, {}  # (loc, angle) -> {side: booths}; loc -> {side at angle 0: booths}
    for b in booths:
        if len(next_to(b)) == 1:
            side = next_to(b)[0]
            votes = exact.setdefault((b[3], b[4]), {})
            votes[side] = votes.get(side, 0) + 1
            base = cw[(cw.index(side) - b[4]) % 4]
            votes = turned.setdefault(b[3], {})
            votes[base] = votes.get(base, 0) + 1
    rows = set()
    for level, x, z, loc, angle, free, evidence in booths:
        if next_to((level, x, z, loc, angle)):
            continue
        side, support = None, 0
        if len(exact.get((loc, angle), ())) == 1:
            side, support = next(iter(exact[(loc, angle)].items()))
        elif loc in turned:
            ranked = sorted(turned[loc].items(), key=lambda kv: -kv[1])
            if len(ranked) == 1 or ranked[0][1] > ranked[1][1]:
                side, support = cw[(cw.index(ranked[0][0]) + angle) % 4], ranked[0][1]
        if evidence != "-" and side != evidence:
            if side is not None and support > 1:
                continue  # a side learned from several booths and the collision map disagree
            side = evidence  # no learned side, or one booth's only
        if side is None or free["WNES".index(side)] != "1":
            continue
        dx, dz = BANKER_SIDES[side]
        rows.add((BANKER_IDS[(x + dx + z + dz) % 2], x + dx, z + dz, level, 0))
    return rows


def main():
    cache = load_cache_npcs(sys.argv[1])
    dest = sys.argv[2]
    by_name = {}
    for i, (n, lvl) in cache.items():
        by_name.setdefault(n.lower(), []).append(i)
    loc_titles = set(pages_using("Template:LocLine"))
    titles = sorted(t for t in loc_titles | set(pages_using("Template:Infobox NPC")) if not OTHER_MODE.search(t))
    print(f"{len(loc_titles)} pages use LocLine, {len(titles)} pages in total", file=sys.stderr)
    texts = wikitexts(titles)
    rows, unmatched = set(), 0
    for title in sorted(texts):
        text = texts[title]
        if title not in loc_titles:
            for npc, x, z, plane, wander in infobox_maps(text):
                if npc in cache:
                    rows.add((npc, x, z, plane, wander))
            continue
        versions = infobox_versions(text)
        for body in templates(text, "LocLine"):
            params, coords = {}, []
            for p in split_params(body):
                for x, y in re.findall(r"x:(-?\d+),y:(-?\d+)", p):
                    coords.append((int(x), int(y)))
                if "=" in p and not p.startswith("x:"):
                    k, v = p.split("=", 1)
                    params[k.strip().lower()] = v.strip()
            mtype = clean(params.get("mtype", "pin")).lower()
            if mtype not in ("pin", "") or not coords:
                continue
            # Disambiguated names and titles ("Scorpia's offspring (monster)") drop the parenthesised part to match the
            # cache name.
            name = re.sub(r"\s*\([^)]*\)$", "", clean(params.get("name", title))).lower()
            plane = int(re.sub(r"[^0-9]", "", params.get("plane", "0")) or 0)
            levels = levels_of(params.get("levels"))

            def ok(i):
                n, lvl = cache.get(i, ("", -2))
                if n.lower() != name:
                    return False
                return lvl in levels if levels else lvl <= 0

            choices = [ids[0] for ids in ([[i for i in v if ok(i)] for v in versions]) if ids]
            if not choices:
                choices = sorted(i for i in by_name.get(name, []) if ok(i))[:1]
            if not choices:
                unmatched += 1
                continue
            for n, (x, z) in enumerate(coords):
                rows.add((choices[n % len(choices)], x, z, plane, -1))
    # NPCs content spawns itself (Wilderness lair bosses: their LocLines are the surface entrance pins).
    rows = {r for r in rows if r[0] not in CONTENT_SPAWNED}
    if len(sys.argv) > 3:
        wiki = {(r[1], r[2], r[3]) for r in rows if cache.get(r[0], ("",))[0] == "Banker"}
        rows |= banker_rows(sys.argv[3], wiki)
    # Plane 0..3 and coordinates inside the 16384-tile world; anything else is a typo on the Wiki.
    rows = {r for r in rows if (r[0], r[1], r[2]) not in MANUAL_REPLACES}
    rows |= {(npc, x, z, 0, 0) for npc, x, z in MANUAL_SPAWNS}
    rows = {r for r in rows if 0 <= r[3] <= 3 and 0 <= r[1] < 16384 and 0 <= r[2] < 16384}
    with open(dest, "w") as f:
        f.write("npc\tx\tz\tlevel\twander\n")
        for r in sorted(rows, key=lambda r: (r[3], r[1], r[2], r[0])):
            f.write("\t".join(str(c) for c in r) + "\n")
    print(f"wrote {len(rows)} spawns ({unmatched} LocLines without a cache match) to {dest}", file=sys.stderr)


if __name__ == "__main__":
    main()
