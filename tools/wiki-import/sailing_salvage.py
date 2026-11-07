#!/usr/bin/env python3
"""Sailing salvaging data (docs/mechanics/sailing-salvaging.md). Run with `python3 -I`, stdlib only.

  salvage-wrecks.tsv  type x z level angle cluster
      Every shipwreck site, read from the cache map: the stump locs `SAILING_*_SHIPWRECK_STUMP` (60465, 60467, ... 60479)
      stand on each site, so their tiles are the sites and their angles are the wreck's (shape 10 on all). Sites are grouped
      into clusters (hotspots) by single-linkage with SPOT_GAP tiles [D]: 80 gives clusters of 6 (8 for small wrecks); the
      Barracuda wrecks merge into clusters of 10 and 14 and one stray site (OPEN-SV1). The Wiki map pins list the same tiles.
  salvage-loot.tsv    salvage section item qty_min qty_max num den
      The `DropsLineSkill` rows of the Wiki page of each salvage item ("Small salvage" ...), one row per drop. `section` is
      the Wiki heading (Pre-roll, Shipbuilding, Runes and ammunition, Other, Tertiary, ...); rows with a clue `type` are
      `item` = "clue:<tier>".

Needs the cache-dump distribution (`./gradlew :tools:cache-dump:installDist`) and network access to the OSRS Wiki.

    python3 -I tools/wiki-import/sailing_salvage.py <out-dir> <cache-dump-binary>
"""

import json
import re
import subprocess
import sys
import urllib.parse
import urllib.request

UA = "tickperfect-wiki-import/1.0 (private server dev tool)"
API = "https://oldschool.runescape.wiki/api.php?action=parse&format=json&prop=wikitext&page="

# (loc name in the cache, wreck type name, salvage page)
WRECKS = [
    ("Small shipwreck", "small", "Small salvage"),
    ("Fisherman's shipwreck", "fisherman", "Fishy salvage"),
    ("Barracuda shipwreck", "barracuda", "Barracuda salvage"),
    ("Large shipwreck", "large", "Large salvage"),
    ("Pirate shipwreck", "pirate", "Plundered salvage"),
    ("Mercenary shipwreck", "mercenary", "Martial salvage"),
    ("Fremennik shipwreck", "fremennik", "Fremennik salvage"),
    ("Merchant shipwreck", "merchant", "Opulent salvage"),
]
STUMP_IDS = {60465, 60467, 60469, 60471, 60473, 60475, 60477, 60479}
SPOT_GAP = 80


def cache(binary, *args):
    out = subprocess.run([binary, *args], capture_output=True, text=True, check=True).stdout
    return [line for line in out.splitlines() if not line.startswith("Picked up")]


def wiki(page):
    req = urllib.request.Request(API + urllib.parse.quote(page), headers={"User-Agent": UA})
    return json.load(urllib.request.urlopen(req, timeout=60))["parse"]["wikitext"]["*"]


def sites(binary):
    """(type, x, z, level, angle) of every stump."""
    found = {}
    for name, kind, _ in WRECKS:
        for line in cache(binary, "find-locs", name):
            parts = line.split()
            if len(parts) == 4 and parts[3].isdigit() and int(parts[3]) in STUMP_IDS:
                found[(int(parts[0]), int(parts[1]), int(parts[2]))] = kind
    squares = {(x >> 6, z >> 6) for (x, z, _) in found}
    angles = {}
    for sx, sz in sorted(squares):
        for line in cache(binary, "map", str(sx), str(sz)):
            m = re.match(r'\s+(\d+) ".*" (\d+) (\d+) (\d+) (\d+) (\d+)$', line)
            if m and int(m.group(1)) in STUMP_IDS:
                angles[(int(m.group(2)), int(m.group(3)), int(m.group(4)))] = int(m.group(6))
    return [(kind, x, z, lv, angles.get((x, z, lv), 0)) for (x, z, lv), kind in sorted(found.items())]


def clusters(rows):
    """Single-linkage groups of the sites of one type: a site within SPOT_GAP tiles joins the cluster."""
    parent = list(range(len(rows)))

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for i, a in enumerate(rows):
        for j in range(i):
            b = rows[j]
            if a[0] == b[0] and max(abs(a[1] - b[1]), abs(a[2] - b[2])) <= SPOT_GAP:
                parent[find(i)] = find(j)
    ids = {}
    out = []
    for i, r in enumerate(rows):
        out.append((*r, ids.setdefault(find(i), len(ids))))
    return out


def loot(page):
    text = wiki(page)
    rows = []
    section = ""
    for line in text.splitlines():
        h = re.match(r"=+\s*([^=]+?)\s*=+\s*$", line)
        if h:
            section = h.group(1)
            continue
        m = re.match(r"\{\{DropsLine(Skill|Clue)\|(.*)\}\}\s*$", line)
        if not m:
            continue
        args = {}
        for part in re.split(r"\|(?![^\[\{]*[\]\}])", m.group(2)):
            if "=" in part:
                k, v = part.split("=", 1)
                args[k.strip()] = v.strip()
        item = args.get("name") or ("clue:" + args.get("type", ""))
        qty = args.get("quantity", "1")
        lo, _, hi = qty.replace(",", "").partition("-")
        rarity = re.sub(r"<.*", "", args.get("rarity", "")).strip().replace(",", "")
        num, _, den = rarity.partition("/")
        if not num.replace(".", "").isdigit() or not den.isdigit():
            continue
        rows.append((page, section, item, lo or "1", hi or lo or "1", num, den))
    return rows


def main(out, binary):
    wrecks = []
    allsites = sites(binary)
    for _, kind, _ in WRECKS:
        wrecks.extend(clusters([s for s in allsites if s[0] == kind]))
    with open(out + "/salvage-wrecks.tsv", "w") as f:
        f.write("type\tx\tz\tlevel\tangle\tcluster\n")
        for r in wrecks:
            f.write("\t".join(str(c) for c in r) + "\n")
    with open(out + "/salvage-loot.tsv", "w") as f:
        f.write("salvage\tsection\titem\tqty_min\tqty_max\tnum\tden\n")
        for _, _, page in WRECKS:
            for r in loot(page):
                f.write("\t".join(r) + "\n")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
