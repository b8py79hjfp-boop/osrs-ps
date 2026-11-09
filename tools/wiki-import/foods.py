#!/usr/bin/env python3
"""
Food heal values from the OSRS Wiki table "Food/All food" (docs/mechanics/consumables.md E12).

Output: tab-separated `name heal_min heal_max bites` rows (header first), one per cache-named food:
  "N"          -> N N 1
  "N × B"      -> N N B      (B bites of N; the item after each bite follows the cache name chain, see Consumables)
  "A – B"      -> A B 1      (a random heal in A..B with the world RNG)
Skipped (handled by hand in Consumables or not food): random or percentage heals, potions and brews with doses
("(4)", "(m4)"), "(A - B) × N" rows, and 0 heals.

Usage (needs network access to oldschool.runescape.wiki):
  python3 -I tools/wiki-import/foods.py content/combat/src/main/resources/tp/content/combat/foods.tsv

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import json
import re
import sys
import urllib.request

API = "https://oldschool.runescape.wiki/api.php?action=parse&page=Food/All_food&prop=wikitext&format=json"


def main():
    req = urllib.request.Request(API, headers={"User-Agent": "tickperfect-wiki-import"})
    text = json.load(urllib.request.urlopen(req, timeout=60))["parse"]["wikitext"]["*"]
    rows = []
    for block in text.split("\n|-"):
        m = re.search(r"\{\{plinkt\|([^}|]+)", block)
        if not m:
            continue
        name = m.group(1).strip()
        cells = block.split("\n|")
        if len(cells) < 3:
            continue
        heal = re.sub(r"data-sort-value=-?\d+[^|]*\|", "", cells[2]).replace("{{fact}}", "").strip()
        if re.search(r"\(m?\d\)$", name):
            continue  # drinks and potions with doses
        if m2 := re.fullmatch(r"(\d+)", heal):
            n = int(m2.group(1))
            if n > 0:
                rows.append((name, n, n, 1))
        elif m2 := re.fullmatch(r"(\d+)\s*[×x]\s*(\d+)", heal):
            rows.append((name, int(m2.group(1)), int(m2.group(1)), int(m2.group(2))))
        elif m2 := re.fullmatch(r"(\d+)\s*[–-]\s*(\d+)", heal):
            rows.append((name, int(m2.group(1)), int(m2.group(2)), 1))
    rows = sorted(set(rows))
    with open(sys.argv[1], "w") as f:
        f.write("name\theal_min\theal_max\tbites\n")
        for r in rows:
            f.write("\t".join(str(x) for x in r) + "\n")
    print(f"wrote {len(rows)} foods to {sys.argv[1]}")


if __name__ == "__main__":
    main()
