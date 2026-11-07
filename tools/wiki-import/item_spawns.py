#!/usr/bin/env python3
"""
Static item spawns from the OSRS Wiki (docs/mechanics/ground-items.md G6).

Item pages list where an item lies on the ground in {{ItemSpawnLine}} rows (Template:ItemSpawnLine, written between
{{ItemSpawnTableHead}} and {{ItemSpawnTableBottom}}):

  {{ItemSpawnLine|name=Egg|location=...|members=No|3226,3301,qty:1|3229,3299,qty:1|plane=0|mapID=-1|leagueRegion=...}}

`name` is the item (matched to a cache obj by name, tradeable first, then the lowest id; "(item)" suffixes dropped),
`members` is the members flag, each positional value `x,z[,qty:n][,qty:a-b][,respawn:n][,notes:...]` is one spawn on the
row's `plane` (default 0). Positional text that is not a coordinate (the Wiki puts notes there) is ignored.

Kept: spawns on the main map's coordinates. `mapID` of the row is absent, 0, -1 (underground and special areas that
share the main coordinate space) or below 10000. Map ids from 10000 are instanced / minigame maps whose coordinates are
not world tiles: skipped. A quantity range takes its lower bound. `respawn:n` is not used (its unit is not published,
OPEN-G6); the plugin applies one default respawn time to every spawn.

Output: tab-separated `obj count x z level members` rows (header first), sorted, duplicates removed.

Usage (needs network access to oldschool.runescape.wiki; cache obj list from cache-dump):
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  python3 -I tools/wiki-import/item_spawns.py objs.txt content/world/src/main/resources/tp/content/world/item-spawns.tsv

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import collections
import json
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
UA = "tickperfect-wiki-import/1.0 (private server dev tool)"
TEMPLATE = "Template:ItemSpawnLine"

# Wiki map ids from here on are instanced or minigame maps (Template:ItemSpawnLine mapID), not world coordinates.
FIRST_INSTANCE_MAP = 10000
MAX_COORD = 16384
MAX_LEVEL = 3


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


def template_bodies(text, name):
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


def load_objs(path):
    """lower-case name -> candidate obj ids, best first (tradeable, then lowest id); not noted, not a placeholder."""
    by_name = {}
    for line in open(path):
        m = re.match(r'(\d+) name="(.*?)" cost=(-?\d+) stackable=\d+ members=\w+ tradeable=(\w+) certTemplate=(-?\d+) '
                     r'placeholderTemplate=(-?\d+)', line)
        if not m or int(m.group(5)) != -1 or int(m.group(6)) != -1:
            continue
        by_name.setdefault(m.group(2).lower(), []).append((m.group(4) != "true", int(m.group(1))))
    return {k: [o for _, o in sorted(v)] for k, v in by_name.items()}


COORD = re.compile(r"^(\d+)\s*,\s*(\d+)\s*(?:,(.*))?$", re.S)
QTY = re.compile(r"qty:\s*(\d+)(?:\s*-\s*\d+)?")


def spawns_of(body):
    """(name, members, plane, mapid, [(x, z, qty)]) of one {{ItemSpawnLine}} body."""
    named, coords = {}, []
    for part in split_params(body):
        m = re.match(r"^([A-Za-z]+)\s*=(.*)$", part, re.S)
        if m:
            named[m.group(1).lower()] = m.group(2).strip()
            continue
        c = COORD.match(part)
        if c:
            q = QTY.search(c.group(3) or "")
            coords.append((int(c.group(1)), int(c.group(2)), int(q.group(1)) if q else 1))
    return named, coords


def main():
    objs_path, out_path = sys.argv[1], sys.argv[2]
    objs = load_objs(objs_path)
    titles = pages_using(TEMPLATE)
    print(f"{len(titles)} pages use {TEMPLATE}", file=sys.stderr)
    texts = wikitexts(titles)
    rows, stats, unmatched = set(), collections.Counter(), collections.Counter()
    for title in sorted(texts):
        for body in template_bodies(texts[title], "ItemSpawnLine"):
            named, coords = spawns_of(body)
            name = re.sub(r"\s*\(item\)$", "", re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", named.get("name", title)))
            ids = objs.get(name.lower())
            if not ids:
                unmatched[name] += len(coords)
                continue
            map_id = int(named["mapid"]) if re.fullmatch(r"-?\d+", named.get("mapid", "")) else 0
            if map_id >= FIRST_INSTANCE_MAP:
                stats["skipped instanced map"] += len(coords)
                continue
            level = int(named["plane"]) if re.fullmatch(r"\d+", named.get("plane", "")) else 0
            members = 1 if named.get("members", "no").strip().lower() in ("yes", "true", "1") else 0
            for x, z, qty in coords:
                if level > MAX_LEVEL or x >= MAX_COORD or z >= MAX_COORD or qty < 1:
                    stats["skipped invalid"] += 1
                    continue
                rows.add((ids[0], qty, x, z, level, members))
    with open(out_path, "w") as f:
        f.write("obj\tcount\tx\tz\tlevel\tmembers\n")
        for r in sorted(rows):
            f.write("\t".join(map(str, r)) + "\n")
    print(f"{len(rows)} spawns; {sum(r[5] == 0 for r in rows)} free-to-play", file=sys.stderr)
    for k, v in stats.items():
        print(f"  {k}: {v}", file=sys.stderr)
    print(f"  unmatched item names: {sum(unmatched.values())} spawns, {len(unmatched)} names", file=sys.stderr)
    for k, v in unmatched.most_common(25):
        print(f"    {k}: {v}", file=sys.stderr)


if __name__ == "__main__":
    main()
