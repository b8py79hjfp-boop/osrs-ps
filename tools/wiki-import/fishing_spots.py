#!/usr/bin/env python3
"""
Fishing spot NPC ids by spot type from the OSRS Wiki (docs/mechanics/fishing.md FS1).

Each spot page's {{Infobox NPC}} lists the NPC ids of that spot type (`|id = 1510,1519,...`). The type decides which
fish the two ops of the spot give (op 1 = Net / Lure / Cage / Big Net, op 3 = Bait / Harpoon):

  NET_BAIT        Fishing spot (small net, bait)               small net: shrimps, anchovies; bait: sardine, herring
  LURE_BAIT       Rod Fishing spot (lure, bait)                lure: trout, salmon, rainbow fish; bait: pike
  CAGE_HARPOON    Fishing spot (cage, harpoon)                 cage: lobster; harpoon: tuna, swordfish
  BIGNET_HARPOON  Fishing spot (big net, harpoon)              big net: mackerel, cod, bass, junk; harpoon: shark
  NET_HARPOON     Fishing spot (Piscatoris Fishing Colony)     net: monkfish; harpoon: tuna, swordfish
  TUTORIAL        Fishing spot (Tutorial Island)               net: shrimps

Ids are kept only if the cache has an NPC of that id named "Fishing spot" / "Rod Fishing spot". Spots the Wiki pages do not
list but whose cache ops are unambiguous ("Small Net"/"Net" + "Bait", "Lure" + "Bait", "Cage" + "Harpoon", "Big Net" +
"Harpoon") are added with source `cache-ops`; "Net" + "Harpoon" is ambiguous (big net or Piscatoris) and is not guessed.

Output (tab-separated, header first): kind, npc, source (wiki | cache-ops).

Usage (needs network access to oldschool.runescape.wiki):
  ./gradlew -q :tools:cache-dump:run --args="config npc --all" > npcs.txt
  python3 -I tools/wiki-import/fishing_spots.py npcs.txt content/skills/src/main/resources/tp/content/skills/fishing-spots.tsv

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

PAGES = [
    ("NET_BAIT", "Fishing spot (small net, bait)"),
    ("LURE_BAIT", "Rod Fishing spot (lure, bait)"),
    ("CAGE_HARPOON", "Fishing spot (cage, harpoon)"),
    ("BIGNET_HARPOON", "Fishing spot (big net, harpoon)"),
    ("NET_HARPOON", "Fishing spot (Piscatoris Fishing Colony)"),
    ("TUTORIAL", "Fishing spot (Tutorial Island)"),
]

# (op1, op3) texts of a cache NPC -> spot type, for spots no Wiki page lists.
OPS_KIND = {
    ("small net", "bait"): "NET_BAIT",
    ("net", "bait"): "NET_BAIT",
    ("lure", "bait"): "LURE_BAIT",
    ("cage", "harpoon"): "CAGE_HARPOON",
    ("big net", "harpoon"): "BIGNET_HARPOON",
}

NPC_LINE = re.compile(r'^(\d+) name="([^"]*)" .*?ops=\[(.*)\]\s*$')
OP = re.compile(r'op(\d)="([^"]*)"')


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


def page_ids(text):
    ids = set()
    for m in re.finditer(r"^\|id\s*=\s*([0-9, ]+)$", text, re.MULTILINE):
        ids.update(int(x) for x in m.group(1).replace(" ", "").split(",") if x)
    return ids


def main():
    npcs_path, out_path = sys.argv[1], sys.argv[2]
    spots = {}  # npc id -> ops by slot
    for line in open(npcs_path, encoding="utf-8"):
        m = NPC_LINE.match(line)
        if not m or m.group(2) not in ("Fishing spot", "Rod Fishing spot"):
            continue
        spots[int(m.group(1))] = {int(s): t.lower() for s, t in OP.findall(m.group(3))}
    rows = {}
    for kind, title in PAGES:
        for npc in sorted(page_ids(wikitext(title))):
            if npc in spots and npc not in rows:
                rows[npc] = (kind, "wiki")
        time.sleep(0.5)
    for npc, ops in sorted(spots.items()):
        kind = OPS_KIND.get((ops.get(1, ""), ops.get(3, "")))
        if npc not in rows and kind:
            rows[npc] = (kind, "cache-ops")
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("kind\tnpc\tsource\n")
        for npc, (kind, source) in sorted(rows.items(), key=lambda kv: (kv[1][0], kv[0])):
            f.write(f"{kind}\t{npc}\t{source}\n")
    print(f"{len(rows)} fishing spot ids written to {out_path}")


if __name__ == "__main__":
    main()
