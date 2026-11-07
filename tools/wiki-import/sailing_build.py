#!/usr/bin/env python3
"""
Sailing shipbuilding costs from the OSRS Wiki (docs/mechanics/sailing-building.md, docs/plans/sailing.md 2.2, 2.3).

The cache keeps the material lists and levels of every boat part and facility but strips the Construction XP and the shipwright
fee (db 177-186 `CONSTRUCTION_COINS_ALTERNATIVE` is a bare 1). This script joins them from two Wiki sources:

  - the long-format tables already imported by sailing.py (`part-stats.tsv`: Hull, Mast and sails, Helm, Keel and Facilities
    pages): Construction XP of every core part and facility, shipwright fee of hulls, masts, helms and keels;
  - the Cargo hold, Salvaging hook and Cannon (facility) pages: the shipwright fee of those facilities (`{{Coins|N}}` cell);
  - the Eternal brazier, Flags and Sail patterns pages: Construction XP of the cosmetic parts.

Output (tab-separated, header first) `shipbuilding.tsv` in the output directory:
  kind   hull | sail | helm | keel | flag | brazier | pattern | facility
  boat   0 raft, 1 skiff, 2 sloop (the Wiki lists them separately); -1 when the value is the same on every boat
  index  tier 0..6 (wooden .. rosewood) for hull, sail, helm and keel; row index of the cosmetic; 0 for a facility
  name   cache name of a facility (blank for the other kinds)
  xp     Construction XP
  fee    shipwright fee in coins, -1 when the shipwright cannot build it

Usage (needs network access to oldschool.runescape.wiki):
  python3 -I tools/wiki-import/sailing_build.py content/sailing/src/main/resources/tp/content/sailing

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
PAGE_DELAY_SECONDS = 1.0

# Wiki facility names that differ from the cache names (db 176 NAME).
ALIASES = {"Basic cargo hold": "Cargo hold", "Innoculation station": "Inoculation station"}

CORE_SECTIONS = [
    ("hull", "Hull", ["Raft bases", "Skiff hulls", "Sloop hulls"], [0, 1, 2]),
    ("sail", "Mast and sails", ["Rafts", "Skiffs", "Sloops"], [0, 1, 2]),
    ("helm", "Helm", ["Raft helms", "Skiff helms", "Sloop helms"], [0, 1, 2]),
    ("keel", "Keel", ["Skiff keels", "Sloop keels"], [1, 2]),
]
FEE_PAGES = ["Cargo hold", "Salvaging hook", "Cannon (facility)"]


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


def wikitext(page):
    data = api({"action": "parse", "page": page, "prop": "wikitext", "redirects": 1})
    time.sleep(PAGE_DELAY_SECONDS)
    return data["parse"]["wikitext"]["*"]


def number(text):
    """First integer or decimal in [text] (thousands commas removed), or None."""
    m = re.search(r"\d[\d,]*(?:\.\d+)?", text or "")
    return float(m.group(0).replace(",", "")) if m else None


def fee_of(text):
    n = number(text)
    return int(n) if n is not None else -1


def load_part_stats(path):
    """{(page, section): {row: {header: value}}} from the long-format part-stats.tsv."""
    out = {}
    with open(path, encoding="utf-8") as f:
        next(f)
        for line in f:
            cells = line.rstrip("\n").split("\t")
            if len(cells) < 5:
                continue
            page, section, row, header, value = cells[:5]
            out.setdefault((page, section), {}).setdefault(int(row), {})[header] = value
    return out


def facility_fees():
    """{wiki facility name: fee} from the first pure-number {{Coins|N}} cell of every table row of the fee pages."""
    fees = {}
    for page in FEE_PAGES:
        text = wikitext(page)
        for chunk in re.split(r"\n\|-", text):
            name = re.search(r"(?:ilinkt\||\[\[)((?:[A-Z][a-z]+ )?[A-Za-z' ]*?(?:cargo hold|salvaging hook|cannon))\b", chunk)
            coins = re.search(r"\{\{Coins\|(\d[\d,]*)\}\}", chunk)
            if name and coins:
                fees[name.group(1).strip()] = int(coins.group(1).replace(",", ""))
    return fees


def cosmetics():
    """Construction XP of the brazier, the flags and the sail patterns (text of their Wiki pages)."""
    rows = []
    brazier = re.search(r"granting ([\d,]+) Construction experience", wikitext("Eternal brazier"))
    if brazier:
        rows.append(("brazier", -1, 0, "", float(brazier.group(1).replace(",", "")), -1))
    flag = re.search(r"\{\{SCP\|Construction\}\}(\d+)", wikitext("Flags"))
    if flag:
        rows.append(("flag", -1, 0, "", float(flag.group(1)), -1))
    patterns = wikitext("Sail patterns")
    for i, chunk in enumerate(re.split(r"\n\|-", patterns.split("==Sail patterns==")[-1])[1:]):
        cells = [c.strip() for c in chunk.strip().split("\n|")]
        if len(cells) >= 6:
            xp = number(cells[4])
            if xp is not None:
                rows.append(("pattern", -1, i, "", xp, -1))
    return rows


def main():
    out_dir = sys.argv[1]
    stats = load_part_stats(os.path.join(out_dir, "part-stats.tsv"))
    rows = []
    for kind, page, sections, boats in CORE_SECTIONS:
        for section, boat in zip(sections, boats):
            for index, cols in sorted(stats[(page, section)].items()):
                xp = number(cols.get("Construction Experience"))
                rows.append((kind, boat, index, "", xp if xp is not None else 0.0, fee_of(cols.get("Shipwright fee"))))
    fees = facility_fees()
    for (page, section), table in sorted(stats.items()):
        if page != "Facilities":
            continue
        for index, cols in sorted(table.items()):
            wiki_name = cols.get("Type")
            xp = number(cols.get("Construction XP"))
            if not wiki_name or xp is None:
                continue
            name = ALIASES.get(wiki_name, wiki_name)
            rows.append(("facility", -1, 0, name, xp, fees.get(wiki_name.replace("Basic ", ""), fees.get(wiki_name, -1))))
    rows += cosmetics()
    path = os.path.join(out_dir, "shipbuilding.tsv")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("kind\tboat\tindex\tname\txp\tfee\n")
        for kind, boat, index, name, xp, fee in rows:
            f.write("%s\t%d\t%d\t%s\t%s\t%d\n" % (kind, boat, index, name, ("%g" % xp), fee))
    print("wrote %s (%d rows)" % (path, len(rows)))


if __name__ == "__main__":
    main()
