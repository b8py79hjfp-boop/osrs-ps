#!/usr/bin/env python3
"""
Fishing spot positions from the OSRS Wiki (docs/mechanics/fishing.md FS8).

The spot pages' {{ObjectLocLine}} blocks list the tiles a spot of that type has been seen on (`|x:3267,y:3148|x:3275,y:3140`),
one block per area; a spot moves between the tiles of its area (Wiki "Fishing spot": "they move to a nearby location after a
hidden timer expires"). This writes one row per block tile:

  kind   spot type (fishing-spots.tsv kinds plus KARAMBWAN, BARBARIAN, DARK_CRAB, ANGLER, EEL, SWAMP, INFERNAL_EEL, SACRED_EEL)
  npc    the cache NPC id spawned there (the first id of the kind in fishing-spots.tsv, or the id the page names)
  x z    plane   the tile
  group  area index within the page (the tiles of one group are the ones a spot may move between)

Blocks on another map than the surface (mapID not 0 / -1 / 23, i.e. instanced or sailing maps) and tiles outside the world are
dropped.

Usage (needs network access to oldschool.runescape.wiki):
  python3 -I tools/wiki-import/fishing_spawns.py content/skills/src/main/resources/tp/content/skills/fishing-spots.tsv \
      content/skills/src/main/resources/tp/content/skills/fishing-spawns.tsv

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

# kind, Wiki page, NPC id (0 = the kind's first id in fishing-spots.tsv)
PAGES = [
    ("NET_BAIT", "Fishing spot (small net, bait)", 0),
    ("LURE_BAIT", "Rod Fishing spot (lure, bait)", 0),
    ("CAGE_HARPOON", "Fishing spot (cage, harpoon)", 0),
    ("BIGNET_HARPOON", "Fishing spot (big net, harpoon)", 0),
    ("NET_HARPOON", "Fishing spot (Piscatoris Fishing Colony)", 0),
    ("TUTORIAL", "Fishing spot (Tutorial Island)", 0),
    ("KARAMBWAN", "Fishing spot (karambwan)", 4712),
    ("BARBARIAN", "Fishing spot (barbarian)", 1542),
    ("DARK_CRAB", "Fishing spot (dark crab)", 1535),
    ("ANGLER", "Rod Fishing spot (anglerfish)", 6825),
    ("EEL", "Fishing spot (frog spawn, eels)", 1497),
    ("SWAMP", "Fishing spot (swamp)", 2653),
    ("INFERNAL_EEL", "Fishing spot (infernal eel)", 7676),
    ("SACRED_EEL", "Fishing spot (sacred eel)", 6488),
]
MAPS = {"0", "-1", "23"}
WORLD = 16384


def wikitext(title):
    q = urllib.parse.urlencode({"action": "parse", "page": title, "prop": "wikitext", "format": "json", "redirects": 1})
    req = urllib.request.Request(API + "?" + q, headers={"User-Agent": UA})
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)["parse"]["wikitext"]["*"]
        except Exception:  # noqa: BLE001 - retry transient network errors
            time.sleep(2 ** attempt)
    raise RuntimeError("wiki request failed: " + title)


def first_ids(path):
    ids = {}
    for line in open(path).read().splitlines()[1:]:
        cols = line.split("\t")
        if len(cols) >= 2:
            ids.setdefault(cols[0], int(cols[1]))
    return ids


def main():
    spots_path, out_path = sys.argv[1], sys.argv[2]
    ids = first_ids(spots_path)
    rows = []
    for kind, page, npc in PAGES:
        text = wikitext(page)
        group = 0
        for block in re.findall(r"\{\{ObjectLocLine(.*?)\n\}\}", text, re.S):
            map_id = (re.search(r"\|\s*mapID\s*=\s*(-?\d+)", block) or [None, "0"])[1]
            plane = int((re.search(r"\|\s*plane\s*=\s*(\d+)", block) or [None, "0"])[1])
            pins = re.findall(r"x:(\d+),y:(\d+)", block)
            if map_id not in MAPS or not pins:
                continue
            kept = [(int(x), int(z)) for x, z in pins if int(x) < WORLD and int(z) < WORLD and 0 <= plane <= 3]
            for x, z in kept:
                rows.append((kind, npc or ids[kind], x, z, plane, group))
            group += 1
        time.sleep(0.2)
    with open(out_path, "w") as f:
        f.write("kind\tnpc\tx\tz\tplane\tgroup\n")
        for r in rows:
            f.write("\t".join(str(c) for c in r) + "\n")
    print(f"{len(rows)} tiles", file=sys.stderr)


if __name__ == "__main__":
    main()
