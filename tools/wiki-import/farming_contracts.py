#!/usr/bin/env python3
"""
Farming contracts and seed packs from the OSRS Wiki (docs/mechanics/farming.md FC1-FC5).

"Farming contract": the three task tables (Easy / Medium / Hard "contract tasks"): task unlock level, seed, patch type, seed pack tier.
"Seed pack": the roll counts per tier (Rewards table) and the Low / Medium / High seed reward tables ({{DropsLineReward}} rows:
seed, quantity range, weight as "n/d").

Seeds are matched to cache objs by name (not noted, not a placeholder; tradeable first, then the lowest id).

Output (tab-separated, header first, in the output directory):
  farming-contracts.tsv  difficulty unlock_level seed patch tier
  farming-seedpacks.tsv  kind tier|table seed|low|medium|high min max weight   (see the file's own comment lines)

Usage (needs network access to oldschool.runescape.wiki and `cache-dump config obj --all`):
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  python3 -I tools/wiki-import/farming_contracts.py objs.txt content/skills/src/main/resources/tp/content/skills

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


def wikitext(title):
    url = API + "?" + urllib.parse.urlencode({"action": "parse", "format": "json", "prop": "wikitext", "page": title, "redirects": 1})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r).get("parse", {}).get("wikitext", {}).get("*", "")
        except Exception:  # noqa: BLE001 - retry transient network errors
            time.sleep(2 ** attempt)
    raise RuntimeError("wiki request failed: " + title)


def load_objs(path):
    by_name = {}
    for line in open(path):
        m = re.match(r'(\d+) name="(.*?)" cost=(-?\d+) stackable=\d+ members=\w+ tradeable=(\w+) certTemplate=(-?\d+) '
                     r'placeholderTemplate=(-?\d+)', line)
        if not m or int(m.group(5)) != -1 or int(m.group(6)) != -1:
            continue
        by_name.setdefault(m.group(2).lower(), []).append((m.group(4) != "true", int(m.group(1))))
    return {k: [o for _, o in sorted(v)] for k, v in by_name.items()}


def obj_id(objs, name):
    ids = objs.get(name.lower())
    return ids[0] if ids else None


def contracts(text, objs):
    rows = []
    for difficulty in ("Easy", "Medium", "Hard"):
        start = text.index("====%s contract tasks" % difficulty)
        end = text.find("\n====", start + 5)
        end = len(text) if end < 0 else end
        section = text[start:end]
        for row in section.split("\n|-")[1:]:
            cells = [c.strip() for c in re.split(r"\n\|(?!\|)", "\n" + row.strip()) if c.strip()]
            if len(cells) < 6 or not cells[0].isdigit():
                continue
            seed = re.search(r"\{\{plinkp\|([^}|]+)", cells[1])
            seed_id = obj_id(objs, seed.group(1)) if seed else None
            if seed_id is None:
                print("no seed obj:", cells[1], file=sys.stderr)
                continue
            rows.append((difficulty.upper(), int(cells[0]), seed_id, cells[4].upper().replace(" ", "_"), int(cells[5])))
    return rows


def seed_pack(text, objs):
    rolls = []
    table = re.search(r"!Contract\n!Low rewards\n!Medium rewards\n!High rewards\n!Total\n(.*?)\n\|\}", text, re.S)
    for row in table.group(1).split("|-"):
        cells = [c.strip().lstrip("|") for c in row.strip().split("\n") if c.strip()]
        if not cells or not cells[0].startswith("!Tier"):
            continue
        tier = int(cells[0].split()[1])
        rolls.append((tier, cells[1:5]))
    drops = []
    for table_name, version in (("LOW", "Low seed"), ("MEDIUM", "Medium seed"), ("HIGH", "High seed")):
        start = text.index("dropversion=%s" % version)
        end = text.index("{{DropsTableBottom}}", start)
        for m in re.finditer(r"\{\{DropsLineReward\|name=([^|]+)\|quantity=(\d+)(?:-(\d+))?\|rarity=(\d+)/(\d+)", text[start:end]):
            if m.group(1) == "White lily seed":
                continue  # Wiki: only in the seed packs bought with Tithe Farm points (a tier-3 pack with this extra drop)
            seed_id = obj_id(objs, m.group(1))
            if seed_id is None:
                print("no seed obj:", m.group(1), file=sys.stderr)
                continue
            lo = int(m.group(2))
            hi = int(m.group(3)) if m.group(3) else lo
            drops.append((table_name, seed_id, lo, hi, int(m.group(4)), int(m.group(5))))
    return rolls, drops


def count_range(text):
    """'3-5 rolls' -> (3, 5); '1/11 chance of 1 roll' -> (0, 1, 11); '0 rolls' -> (0, 0)."""
    m = re.match(r"(\d+)-(\d+) rolls", text)
    if m:
        return int(m.group(1)), int(m.group(2)), 1
    m = re.match(r"(\d+) rolls?", text)
    if m:
        return int(m.group(1)), int(m.group(1)), 1
    m = re.match(r"1/(\d+) chance of 1 roll", text)
    if m:
        return 0, 1, int(m.group(1))
    raise ValueError(text)


def main():
    objs = load_objs(sys.argv[1])
    out_dir = sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    rows = contracts(wikitext("Farming contract"), objs)
    with open(os.path.join(out_dir, "farming-contracts.tsv"), "w") as f:
        f.write("difficulty\tunlock_level\tseed\tpatch\ttier\n")
        for row in rows:
            f.write("\t".join(str(c) for c in row) + "\n")
    rolls, drops = seed_pack(wikitext("Seed pack"), objs)
    with open(os.path.join(out_dir, "farming-seedpacks.tsv"), "w") as f:
        f.write("# Wiki \"Seed pack\". ROLLS rows: per tier, the low / medium / high roll counts as min max one_in "
                "(one_in > 1: that many rolls, min..max, only with chance 1/one_in), then the total number of rolls.\n")
        f.write("# DROP rows: a seed of one reward table with its quantity range and weight / total (Low out of 60, Medium out of 30, "
                "High out of 44).\n")
        f.write("kind\ttier_or_table\ta\tb\tc\td\te\tf\tg\th\n")
        for tier, cells in rolls:
            low, medium, high, total = (count_range(c) for c in cells)
            f.write("ROLLS\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n" % ((tier,) + low[:2] + medium[:2] + high[:2] + (high[2], total[0])))
        for table_name, seed_id, lo, hi, num, den in drops:
            f.write("DROP\t%s\t%d\t%d\t%d\t%d\t%d\t0\t0\t0\n" % (table_name, seed_id, lo, hi, num, den))
    print("%d contracts, %d drop rows" % (len(rows), len(drops)))


if __name__ == "__main__":
    main()
