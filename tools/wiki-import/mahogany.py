#!/usr/bin/env python3
"""
Mahogany Homes contract layouts from the OSRS Wiki (docs/mechanics/mahogany-homes.md).

Reads the wikitext of "Mahogany Homes/Mechanics" (API action=parse, prop=wikitext) and writes one row per layout. The
Wiki table lists, for the eight client varbits 10554..10561, the furniture of that slot and what it costs; each row is
checked against the table's own plank, steel bar and experience columns (formula: docs/mechanics/mahogany-homes.md MH6)
and a row that does not agree is reported on stderr.

Output (tab-separated, no header): tier home region layout slot1..slot8 planks bars xp
  slot cell: state:count:material:name  (state 1 = repair, 3 = replace; material P plank, B steel bar) or empty
  One fix: the Wiki lists Sarah's shelves as 3 planks; her totals and the other pages say 2.

Usage:
  python3 -I tools/wiki-import/mahogany.py Mahogany_Homes_Mechanics.wikitext content/skills/src/main/resources/tp/content/skills/mahogany-layouts.tsv

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import re
import sys

BUILD = {"Beginner": 22.5, "Novice": 48, "Adept": 72, "Expert": 112}
REPAIR = {"Beginner": 127.5, "Novice": 160, "Adept": 190, "Expert": 240}
DONE = {"Beginner": 500, "Novice": 1250, "Adept": 2250, "Expert": 2750}
BAR_REPAIR = 120
SLOTS = 8


def main():
    text = open(sys.argv[1]).read()
    tier = None
    rows = []
    seen = {}
    bad = 0
    for line in text.splitlines():
        m = re.match(r"^===\s*(Beginner|Novice|Adept|Expert)\s*===", line.strip())
        if m:
            tier = m.group(1)
            continue
        if tier is None or not line.startswith("| [["):
            continue
        cells = [c.strip() for c in line[1:].split("||")]
        region = re.sub(r"\[\[|\]\]", "", cells[0])
        home = re.sub(r"\[\[([^\]|]*\|)?([^\]]*)\]\]", r"\2", cells[1]).replace(" (Mahogany Homes)", "")
        tail = cells[2:]
        planks, bars, xp = tail[2 * SLOTS], tail[2 * SLOTS + 1], tail[2 * SLOTS + 2]
        out = []
        total = DONE[tier]
        sum_planks = sum_bars = 0
        for i in range(SLOTS):
            name, cost = tail[2 * i], tail[2 * i + 1]
            if not name:
                out.append("")
                continue
            n = int(re.search(r"<sup>(\d+)</sup>", cost).group(1))
            if home == "Sarah" and name.strip() == "Shelves":
                n = 2
            steel = "Steel bar" in cost
            repair = "Repair" in cost
            if steel:
                sum_bars += n
                total += BAR_REPAIR * n
            else:
                sum_planks += n
                total += REPAIR[tier] if repair else BUILD[tier] * n
            out.append("%d:%d:%s:%s" % (1 if repair else 3, n, "B" if steel else "P", name.strip()))
        key = (tier, home)
        seen[key] = seen.get(key, 0) + 1
        wiki_xp = float(xp.replace(",", ""))
        if sum_planks != int(planks) or sum_bars != int(bars) or abs(total - wiki_xp) > 0.01:
            bad += 1
            sys.stderr.write("check %s %s #%d: planks %d/%s bars %d/%s xp %s/%s\n" % (tier, home, seen[key], sum_planks, planks, sum_bars, bars, total, wiki_xp))
        rows.append([tier, home, region, str(seen[key])] + out + [str(sum_planks), str(sum_bars), str(wiki_xp)])
    with open(sys.argv[2], "w") as f:
        for r in rows:
            f.write("\t".join(r) + "\n")
    sys.stderr.write("%d layouts, %d disagree with the table totals\n" % (len(rows), bad))


main()
