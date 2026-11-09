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
  ./gradlew -q :tools:cache-dump:run --args="worldmap-sections $PWD/worldmap.tsv"
  python3 -I tools/wiki-import/npc_spawns.py npcs.txt content/world/src/main/resources/tp/content/world/npc-spawns.tsv docs/research/banker-spawns.tsv --worldmap worldmap.tsv
  ./gradlew -q :tools:cache-dump:run --args="spawn-check $PWD/content/world/src/main/resources/tp/content/world/npc-spawns.tsv blocked.tsv"
  python3 -I tools/wiki-import/npc_spawns.py npcs.txt content/world/src/main/resources/tp/content/world/npc-spawns.tsv docs/research/banker-spawns.tsv --worldmap worldmap.tsv --blocked blocked.tsv
(the second run drops or moves the spawns the first run put on tiles an NPC can't stand on, SP10)

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import glob
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
    """id -> (name, combat level) from cache-dump `config npc --all`; also fills [MULTI] (id -> multiNpcs list)."""
    out = {}
    for line in open(path):
        m = re.match(r'(\d+) name="(.*?)" size=\d+ combatLevel=(-?\d+)', line)
        if m:
            out[int(m.group(1))] = (m.group(2), int(m.group(3)))
            mn = re.search(r"multiNpcs=\[([-\d,]+)\]", line)
            if mn:
                MULTI[int(m.group(1))] = [int(x) for x in mn.group(1).split(",")]
    return out


MULTI = {}
PICK = {"rank": lambda i: 0}

# Gameval prefixes of NPC copies made for another context than the world spot the Wiki pins: player-owned houses, random
# events, Nightmare Zone, Deadman, Soul Wars, Barbarian Assault, Leagues (SP14).
OTHER_CONTEXT = re.compile(r"(POH_|MACRO_|NZONE_|DEADMAN|SOUL_WARS|BARBASSAULT|BA_|LEAGUE)")


def pick(ids, usable):
    """The first usable id of [ids], other-context copies last (SP14); None when none is usable."""
    good = [i for i in ids if usable(i)]
    return min(good, key=lambda i: (PICK["rank"](i), good.index(i))) if good else None

# Cache 2757 was captured on 3 November 2026 (docs/BRIEF.md): content the Wiki marks as removed on or before it is gone.
CACHE_DATE = (2026, 11, 3)
MONTHS = ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november",
          "december"]


def removed(value):
    """True when an infobox `removal` value is a date on or before [CACHE_DATE] (an unreadable non-empty one counts too)."""
    v = clean(value or "").lower()
    if not v or v in ("no", "n/a", "none"):
        return False
    y = re.search(r"\b(\d{4})\b", v)
    if not y:
        return True
    month = next((i + 1 for i, name in enumerate(MONTHS) if name in v), 1)
    d = re.search(r"\b(\d{1,2})\b", v.replace(y.group(1), ""))
    return (int(y.group(1)), month, int(d.group(1)) if d else 1) <= CACHE_DATE


WORLD_MAP = {}  # (map, display square x, z, zone x or -1, zone z or -1) -> (dx, dz, level offset)


def load_world_map(path):
    """SP15: `cache-dump worldmap-sections` rows. The Wiki's pins are world-map (display) coordinates of their `mapID`; the
    world map draws game squares and zones elsewhere (dungeons patched together), so a pin is moved by its piece's shift and
    its plane raised by the piece's lowest level."""
    for line in open(path).read().splitlines()[1:]:
        c = line.split("\t")
        m, kind = int(c[0]), c[1]
        dsx, dsz, ssx, ssz, min_level = int(c[2]), int(c[3]), int(c[6]), int(c[7]), int(c[10])
        if kind == "square":
            WORLD_MAP[(m, dsx, dsz, -1, -1)] = ((ssx - dsx) * 64, (ssz - dsz) * 64, min_level)
        else:
            dzx, dzz, szx, szz = int(c[4]), int(c[5]), int(c[8]), int(c[9])
            WORLD_MAP[(m, dsx, dsz, dzx, dzz)] = ((ssx - dsx) * 64 + (szx - dzx) * 8, (ssz - dsz) * 64 + (szz - dzz) * 8, min_level)


def game_coord(map_id, x, z, plane):
    """The game tile of a pin at display (x, z, plane) on world map [map_id] (unchanged when the map has no piece there)."""
    key = (map_id, x >> 6, z >> 6)
    shift = WORLD_MAP.get(key + (((x & 63) >> 3), ((z & 63) >> 3))) or WORLD_MAP.get(key + (-1, -1))
    if shift is None:
        return x, z, plane
    return x + shift[0], z + shift[1], plane + shift[2]


def map_id(value):
    """A LocLine / {{Map}} `mapID`; missing, empty or -1 means the main map (0)."""
    m = re.fullmatch(r"\s*(-?\d+)\s*", clean(value or ""))
    return max(0, int(m.group(1))) if m else 0


def strip_comments(text):
    """Wikitext without HTML comments: pages park unverified pins there (`<!--TODO - Get locations ...`)."""
    return re.sub(r"<!--.*?(?:-->|\Z)", "", text, flags=re.S)


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
                sfx = k[2:]
                if removed(params.get("removal" + sfx, params.get("removal"))):
                    continue  # SP11: a version removed from the game before the cache
                ids = [int(x) for x in re.findall(r"\d+", params[k])]
                if ids:
                    versions.append(ids)
    return versions


def has_infobox(text):
    return bool(templates(text, "Infobox Monster") or templates(text, "Infobox NPC"))


def infobox_maps(text):
    """(ids, x, z, plane, wander) per infobox version with a {{Map}} in its map field (the version's ids, in order)."""
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
                if removed(params.get("removal" + sfx, params.get("removal"))):
                    continue  # SP11
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
                    mid = map_id(kv.get("mapid"))
                    pts = [game_coord(mid, px, pz, plane) for px, pz in pts]
                    plane = pts[0][2]
                    pts = [(px, pz) for px, pz, _ in pts]
                    xs, zs = [p[0] for p in pts], [p[1] for p in pts]
                    mtype = kv.get("mtype", "pin").lower() or "pin"
                    if mtype == "line":
                        continue
                    if mtype in ("pin", ""):
                        # Every pin is a spawn (the Wiki lists all stand tiles of the NPC), none wanders.
                        for px, pz in dict.fromkeys(pts):
                            out.append((ids, px, pz, plane, 0))
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
                    out.append((ids, x, z, plane, wander))
                    break
    return out


def levels_of(v):
    v = clean(v or "")
    nums = [int(n) for n in re.findall(r"\d+", v)]
    return nums


# Callisto, Venenatis, Vet'ion (both forms), Artio, Calvar'ion (both forms), Spindel: content/pvp Lairs spawns them.
# Misthalin Mystery's visible forms of Mandy (kitchen, outside) and the shady figure: content/quests QuestSpawnsB spawns their
# multi-NPCs (7433, 7434, 7435) so the quest variable shows and hides them.
# Quest NPCs whose Wiki pin names a multi-NPC child: content/quests QuestSpawns spawns the multi-NPC instead (Head Wizard
# 5034 shows Archmage Sedridor 11432, Wizard Mizgog 5005 shows 7746) or the quest's own id (Sir Amik Varze 4771, not the
# Recipe for Disaster form 1867), so the quest's varp drives them and they don't stand twice on one tile. The same for
# content/quests QuestSpawnsB's multi-NPCs: the Port Sarim ferry crew (14978, 14980, 14982, 14984), Lady Keli 11578, Joe
# 11577, Prince Ali 11580, Captain Tock 7956, Ithoi 7961, Misthalin Mystery's Abigale, Hewey, Sid, Tayten, Lacey (7623,
# 7624, 7625, 7627, 7628); and two op-less look-alikes on a quest NPC's tile: Veos 2850 and King Roald 1399.
QUEST_CHILDREN = {11432, 7746, 1867, 14978, 14980, 14982, 14984, 11578, 11577, 11580, 7956, 7961, 7623, 7624, 7625, 7627, 7628, 2850, 1399}
CONTENT_SPAWNED = {6609, 6610, 6611, 6612, 11992, 11993, 11994, 11998, 7629, 7630, 7638, 10570, 10571, 10576, 10612, 10613, 10572, 10574, 10575}  # Tempoross plugin: the pool, the crates, the retired sailor, Gita, and the boss forms (instanced)
# Also spawned by their own plugins (SP3d): Sea Charting's weather trolls 15154, Tempoross' harpoonfish spot 10565 (instanced),
# Wintertodt's pyromancers 7371, Pyramid Plunder's guardian mummy 1779, Otto 2914, the Ourania banker 8132, the Abyss dark mage
# 2583, Volcanic Mine's Pete 7776, Temple of the Eye's tea seller 8687, Distentor 3248; and the Restless Ghost 922, which
# its quest spawns only while the quest is under way.
CONTENT_SPAWNED |= {15154, 10565, 7371, 1779, 2914, 8132, 2583, 7776, 8687, 3248, 922}


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

# Pet NPCs (cache op "Pick-up", they follow their owner) that some Wiki LocLine / map put in the world: seen in the client
# (a Rock Golem in Lumbridge castle's courtyard). Rock Golem, Great blue heron, Tiny Tempor, Tumeken's and Elidinis' Guardian.
PET_IDS = {2182, 6817, 10562, 11652, 11653}

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


def one_per_tile(rows, cache):
    """SP11: one NPC of a name per tile. Sibling pages ("Citizen (Kastori)" / "(Tal Teklan)", "Monkey (monster)" / "Monkey
    (Monkey Madness I)") list the same pins with their own ids; the page titled exactly as the NPC wins, else the first
    title. Rows are `row -> page title`."""
    best = {}
    for row, title in rows.items():
        key = (cache[row[0]][0].lower(), row[1], row[2], row[3])
        main = re.sub(r"\s*\([^)]*\)$", "", title).lower() == title.lower() == key[0]
        rank = (0 if main else 1, title, row[0])
        if key not in best or rank < best[key][0]:
            best[key] = (rank, row)
    return {row: rows[row] for _, row in best.values()}


def to_multi_parents(rows, cache):
    """SP12: the Wiki pins the type a multi-NPC shows (its child); the game spawns the multi-NPC itself, and the client picks the
    child from a varbit. A child is spawned as its parent when exactly one multi-NPC can show it, every state of that parent
    is this NPC (same name) or hidden, and either the parent's default state (varbit 0) is this child or the parent only shows
    or hides this child (a quest's visibility switch: Ardougne's knights, Varlamore's recruits); otherwise the child stays
    (the Tool Leprechaun 0 has a My Arm's Big Adventure parent that hides it in most states)."""
    parents = {}
    for p, states in MULTI.items():
        for c in set(states):
            if c >= 0:
                parents.setdefault(c, []).append(p)

    def parent(c):
        name = cache[c][0]
        fit = [p for p in parents.get(c, []) if PICK["rank"](p) == 0 and
               all(cache.get(k, ("",))[0] == name for k in MULTI[p] if k >= 0) and
               (MULTI[p][0] == c or set(MULTI[p]) - {-1} == {c})]
        return fit[0] if len(fit) == 1 else c

    for npc, x, z, plane, wander in rows:
        yield parent(npc), x, z, plane, wander


def gamevals():
    """npc id -> Jagex gameval name, from the generated `tp.symbols.Npcs` (cache archive 24; `./gradlew build` writes it)."""
    out = {}
    for path in glob.glob("symbols/build/**/Npcs.java", recursive=True):
        for m in re.finditer(r"(\w+) = (\d+);", open(path).read()):
            out[int(m.group(2))] = m.group(1)
    return out


def apply_blocked(rows, path):
    """SP10: `cache-dump spawn-check` rows. `nomap` (no map square in this cache) drops the spawn, `loc` (inside solid
    scenery: a pin a tile off) moves it to the nearest standable tile; `floor` stays (water creatures, staff behind counters,
    fliers over gaps stand on floor players can't walk)."""
    fix = {}
    for line in open(path).read().splitlines()[1:]:
        npc, x, z, level, reason, fx, fz = line.split("\t")
        fix[(int(npc), int(x), int(z), int(level))] = (reason, fx, fz)
    out = set()
    for r in rows:
        f = fix.get(r[:4])
        if f is None or f[0] == "floor":
            out.add(r)
        elif f[0] == "loc" and f[1] != "-":
            out.add((r[0], int(f[1]), int(f[2]), r[3], r[4]))
    return out


def main():
    args = sys.argv[1:]
    flags = {}
    for flag in ("--worldmap", "--blocked"):
        if flag in args:
            i = args.index(flag)
            flags[flag] = args[i + 1]
            del args[i:i + 2]
    sys.argv = [sys.argv[0]] + args
    if "--worldmap" not in flags:
        sys.exit("--worldmap <file> is required (cache-dump worldmap-sections): Wiki pins are world-map coordinates")
    load_world_map(flags["--worldmap"])
    cache = load_cache_npcs(sys.argv[1])
    # SP13: cutscene copies (gameval `..._CUTSCENE...`: Frost Nagua, Song of the Elves rebels, the Elvarg of Dragon Slayer I's
    # boat scene) exist only in a quest's scene; they never match, so a page picks its next version (the fightable Elvarg).
    names = gamevals()
    if not names:
        sys.exit("no generated symbols: run ./gradlew :symbols:build first")
    for i in [i for i in cache if "CUTSCENE" in names.get(i, "")]:
        del cache[i]
    # SP14: a version's id list and the by-name fallback often start with a copy made for another context (Ogre 136 is the
    # house guard POH_OGRE, Swarm 330 the random event's, Elvarg 6349 Nightmare Zone's): those ids come last.
    def rank(i):
        return 1 if OTHER_CONTEXT.match(names.get(i, "")) else 0

    PICK["rank"] = rank
    dest = sys.argv[2]
    by_name = {}
    for i, (n, lvl) in cache.items():
        by_name.setdefault(n.lower(), []).append(i)
    loc_titles = set(pages_using("Template:LocLine"))
    titles = sorted(t for t in loc_titles | set(pages_using("Template:Infobox NPC")) if not OTHER_MODE.search(t))
    print(f"{len(loc_titles)} pages use LocLine, {len(titles)} pages in total", file=sys.stderr)
    texts = {t: strip_comments(x) for t, x in wikitexts(titles).items()}
    rows, unmatched = {}, 0  # row -> the page that gave it
    for title in sorted(texts):
        text = texts[title]
        if title not in loc_titles:
            for ids, x, z, plane, wander in infobox_maps(text):
                npc = pick(ids, lambda i: i in cache)
                if npc is not None:
                    rows.setdefault((npc, x, z, plane, wander), title)
            continue
        if not has_infobox(text):
            continue  # SP11: guides ("Splashing/Guide") repeat other pages' LocLines; the NPC's own page has them
        versions = infobox_versions(text)
        if not versions:
            continue  # every version removed
        # SP11: one page often has a LocLine per combat level listing the same pins (the levels mix): each pin is one NPC,
        # its id taken in turn from the LocLines that list it.
        pins = {}
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

            choices = [c for c in (pick(v, ok) for v in versions) if c is not None]
            if not choices:
                fallback = pick(sorted(by_name.get(name, [])), ok)
                choices = [fallback] if fallback is not None else []
            if not choices:
                unmatched += 1
                continue
            mid = map_id(params.get("mapid"))
            for n, (x, z) in enumerate(coords):
                gx, gz, gplane = game_coord(mid, x, z, plane)
                pins.setdefault((name, gx, gz, gplane), []).append(choices[n % len(choices)])
        for n, ((_, x, z, plane), ids) in enumerate(pins.items()):
            rows.setdefault((ids[n % len(ids)], x, z, plane, -1), title)
    rows = one_per_tile(rows, cache)
    rows = set(to_multi_parents(rows, cache))
    # NPCs content spawns itself (Wilderness lair bosses: their LocLines are the surface entrance pins).
    rows = {r for r in rows if r[0] not in CONTENT_SPAWNED | QUEST_CHILDREN | PET_IDS}

    if len(sys.argv) > 3:
        wiki = {(r[1], r[2], r[3]) for r in rows if cache.get(r[0], ("",))[0] == "Banker"}
        rows |= banker_rows(sys.argv[3], wiki)
    # Plane 0..3 and coordinates inside the 16384-tile world; anything else is a typo on the Wiki.
    rows = {r for r in rows if (r[0], r[1], r[2]) not in MANUAL_REPLACES}
    rows |= {(npc, x, z, 0, 0) for npc, x, z in MANUAL_SPAWNS}
    rows = {r for r in rows if 0 <= r[3] <= 3 and 0 <= r[1] < 16384 and 0 <= r[2] < 16384}
    if "--blocked" in flags:
        rows = apply_blocked(rows, flags["--blocked"])
    with open(dest, "w") as f:
        f.write("npc\tx\tz\tlevel\twander\n")
        for r in sorted(rows, key=lambda r: (r[3], r[1], r[2], r[0])):
            f.write("\t".join(str(c) for c in r) + "\n")
    print(f"wrote {len(rows)} spawns ({unmatched} LocLines without a cache match) to {dest}", file=sys.stderr)


if __name__ == "__main__":
    main()
