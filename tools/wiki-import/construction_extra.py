#!/usr/bin/env python3
"""
Built loc ids the main Construction import drops (docs/mechanics/construction.md CO13): pages whose {{Infobox Construction}} has
versions (altars by god, burners lit and unlit, ...) or no `id` in the first version. The rows of construction-furniture.tsv
with an empty locs column are looked up again here; for each page every `id`, `id1`, `id2`, ... of the infobox is kept in
order, and `lit` ids (the second of a lit / unlit pair) are not guessed.

Output (tab-separated, header first): name  locs   (comma separated, version order of the Wiki page)

Usage (needs network access to oldschool.runescape.wiki):
  python3 -I tools/wiki-import/construction_extra.py content/skills/src/main/resources/tp/content/skills

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
UA = "tickperfect-wiki-import/1.0 (private server dev tool)"
# Rooms whose furniture is not built in a player's house (Mahogany Homes clients) are skipped.
SKIP_ROOMS = {"mahogany homes clients"}


def wikitext(page):
    url = API + "?" + urllib.parse.urlencode({"action": "parse", "format": "json", "prop": "wikitext", "page": page})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(5):
        try:
            data = json.load(urllib.request.urlopen(req))
            return data["parse"]["wikitext"]["*"] if "parse" in data else None
        except Exception:
            time.sleep(1 + attempt)
    return None


def main():
    out = sys.argv[1]
    rows = [l.rstrip("\n").split("\t") for l in open(os.path.join(out, "construction-furniture.tsv"))][1:]
    result = []
    for r in rows:
        name, room, locs = r[0], r[3], r[5]
        if locs or room.strip().lower() in SKIP_ROOMS or not room.strip():
            continue
        text = wikitext(re.sub(r" \(Construction\)$", "", name)) or wikitext(name)
        if not text:
            sys.stderr.write("no page for %s\n" % name)
            continue
        ids = []
        for m in re.finditer(r"^\|\s*id(\d*)\s*=\s*([\d, ]+)", text, re.M):
            ids += re.findall(r"\d+", m.group(2))
        if ids:
            result.append((name, ",".join(ids)))
        else:
            sys.stderr.write("no ids for %s\n" % name)
        time.sleep(0.2)
    with open(os.path.join(out, "construction-extra-locs.tsv"), "w") as f:
        f.write("name\tlocs\n")
        for name, ids in result:
            f.write("%s\t%s\n" % (name, ids))
    sys.stderr.write("%d pieces with ids\n" % len(result))


main()
