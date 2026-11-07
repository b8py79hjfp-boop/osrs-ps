#!/usr/bin/env python3
"""Sea charting completion bonuses (docs/mechanics/sailing-charting.md C7). Run with `python3 -I`, stdlib only.

  charting-seas.tsv  ocean sea level tasks bonus
      One row per sea from the "=== <Ocean> ===" tables of the Wiki page "Sea charting/By oceans" (columns Level, Sea, #, Bonus
      XP); `ocean` is the heading ("Bonus charts" for the Miscellaneous groups, as in charting-tasks.tsv), `tasks` the sea's
      task count and `bonus` the XP for charting every task of the sea.
  charting-oceans.tsv  ocean level bonus
      The ocean completion bonuses, claimed from Chartin' Charles McAtless, from the "Sea completion bonuses" table; the row
      `*` is the 100% completion bonus.

    python3 -I tools/wiki-import/sailing_charting.py <out-dir>
"""

import json
import re
import sys
import urllib.parse
import urllib.request

UA = "tickperfect-wiki-import/1.0 (private server dev tool)"
API = "https://oldschool.runescape.wiki/api.php?action=parse&format=json&prop=wikitext&page="

# The Wiki's heading for the miscellaneous groups is not the TSV's name for them.
OCEAN_ALIASES = {"Miscellaneous": "Bonus charts"}


def wiki(page):
    req = urllib.request.Request(API + urllib.parse.quote(page), headers={"User-Agent": UA})
    return json.load(urllib.request.urlopen(req, timeout=60))["parse"]["wikitext"]["*"]


def number(text):
    return int(re.sub(r"[^\d]", "", text))


def sea_rows(text):
    rows = []
    ocean = None
    cells = []
    for line in text.splitlines():
        h = re.match(r"===\s*([^=]+?)\s*===\s*$", line)
        if h:
            ocean = OCEAN_ALIASES.get(h.group(1), h.group(1))
            cells = []
            continue
        if ocean is None:
            continue
        if line.startswith("|-") or line.startswith("|}"):
            if len(cells) == 4:
                level = int(re.search(r"SCP\|Sailing\|(\d+)", cells[0]).group(1))
                sea = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", cells[1]).strip()
                rows.append((ocean, sea, level, number(cells[2]), number(cells[3])))
            cells = []
        elif line.startswith("|") and not line.startswith("|+"):
            cells.append(line[1:].strip())
    return rows


def ocean_rows(text):
    rows = []
    in_table = False
    block = []
    for line in text.splitlines():
        if "Sea completion bonuses" in line:
            in_table = True
        if in_table and line.startswith("|-"):
            block.append([])
        elif in_table and block and line.startswith("|") and not line.startswith("|}"):
            block[-1].extend(c.strip() for c in line.lstrip("|").split("||"))
        elif in_table and line.startswith("|}"):
            break
    for cells in block:
        level = re.search(r"SCP\|Sailing\|(\d+)", cells[0]) if cells else None
        nums = [number(c) for c in cells[2:] if re.fullmatch(r"[\d,]+", c)]
        if level and len(cells) >= 2 and len(nums) >= 3:
            rows.append((OCEAN_ALIASES.get(cells[1], cells[1]), int(level.group(1)), nums[1]))
    return rows


def main(out):
    text = wiki("Sea charting/By oceans")
    with open(out + "/charting-seas.tsv", "w") as f:
        f.write("ocean\tsea\tlevel\ttasks\tbonus\n")
        for r in sea_rows(text):
            f.write("\t".join(str(c) for c in r) + "\n")
    with open(out + "/charting-oceans.tsv", "w") as f:
        f.write("ocean\tlevel\tbonus\n")
        for r in ocean_rows(text):
            f.write("\t".join(str(c) for c in r) + "\n")
        f.write("*\t78\t100000\n")


if __name__ == "__main__":
    main(sys.argv[1])
