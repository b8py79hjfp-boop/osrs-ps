#!/usr/bin/env python3
"""
Farming crops from the OSRS Wiki (docs/mechanics/farming.md F2-F8).

Each crop's plant page carries a `{{Farming info}}` template:

  level      Farming level to plant
  seedsper   seeds (or saplings) used per patch
  time       "N min (CxM min)": C growth cycles of M minutes each
  plantxp    xp for planting (a sapling for trees)
  checkxp    xp for checking the health of a fully grown tree / fruit tree ("No" otherwise)
  harvestxp  xp per harvested item ("No" for trees)

and, for crops that use harvest lives, `{{Farming yield calculator|...|low=L|high=H}}`: the chance to save a life out of 256
at Farming level 1 and 99 (Wiki "Farming#Variable crop yield").

Seeds, products and clean herbs are matched to cache objs by name (not noted, not a placeholder; tradeable first, then the
lowest id). A crop with no `{{Farming info}}` or no matching obj is reported and dropped. `key` is the name of the crop in
farming-varbits.tsv (RuneLite `Produce`).

Output (tab-separated, header first, in the output directory):
  farming-crops.tsv   key patch level seeds_per seed produce clean plant_xp harvest_xp check_xp cycle_min first_min cycles cts_low cts_high name

Usage (needs network access to oldschool.runescape.wiki and `cache-dump config obj --all`):
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  python3 -I tools/wiki-import/farming_crops.py objs.txt content/skills/src/main/resources/tp/content/skills

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

# key, patch, plant page titles to try, seed item, product item, clean herb item ("" = none)
CROPS = [
    ("POTATO", "ALLOTMENT", ["Potato plant"], "Potato seed", "Potato", ""),
    ("ONION", "ALLOTMENT", ["Onion plant"], "Onion seed", "Onion", ""),
    ("CABBAGE", "ALLOTMENT", ["Cabbage plant"], "Cabbage seed", "Cabbage", ""),
    ("TOMATO", "ALLOTMENT", ["Tomato plant"], "Tomato seed", "Tomato", ""),
    ("SWEETCORN", "ALLOTMENT", ["Sweetcorn plant"], "Sweetcorn seed", "Sweetcorn", ""),
    ("STRAWBERRY", "ALLOTMENT", ["Strawberry plant"], "Strawberry seed", "Strawberry", ""),
    ("WATERMELON", "ALLOTMENT", ["Watermelon plant"], "Watermelon seed", "Watermelon", ""),
    ("SNAPE_GRASS", "ALLOTMENT", ["Snape grass plant"], "Snape grass seed", "Snape grass", ""),
    ("GUAM", "HERB", ["Guam plant"], "Guam seed", "Grimy guam leaf", "Guam leaf"),
    ("MARRENTILL", "HERB", ["Marrentill plant"], "Marrentill seed", "Grimy marrentill", "Marrentill"),
    ("TARROMIN", "HERB", ["Tarromin plant"], "Tarromin seed", "Grimy tarromin", "Tarromin"),
    ("HARRALANDER", "HERB", ["Harralander plant"], "Harralander seed", "Grimy harralander", "Harralander"),
    ("RANARR", "HERB", ["Ranarr weed (plant)"], "Ranarr seed", "Grimy ranarr weed", "Ranarr weed"),
    ("TOADFLAX", "HERB", ["Toadflax plant"], "Toadflax seed", "Grimy toadflax", "Toadflax"),
    ("IRIT", "HERB", ["Irit plant"], "Irit seed", "Grimy irit leaf", "Irit leaf"),
    ("AVANTOE", "HERB", ["Avantoe plant"], "Avantoe seed", "Grimy avantoe", "Avantoe"),
    ("KWUARM", "HERB", ["Kwuarm plant"], "Kwuarm seed", "Grimy kwuarm", "Kwuarm"),
    ("HUASCA", "HERB", ["Huasca plant", "Huasca"], "Huasca seed", "Grimy huasca", "Huasca"),
    ("SNAPDRAGON", "HERB", ["Snapdragon plant"], "Snapdragon seed", "Grimy snapdragon", "Snapdragon"),
    ("CADANTINE", "HERB", ["Cadantine plant"], "Cadantine seed", "Grimy cadantine", "Cadantine"),
    ("LANTADYME", "HERB", ["Lantadyme plant"], "Lantadyme seed", "Grimy lantadyme", "Lantadyme"),
    ("DWARF_WEED", "HERB", ["Dwarf weed plant"], "Dwarf weed seed", "Grimy dwarf weed", "Dwarf weed"),
    ("TORSTOL", "HERB", ["Torstol plant"], "Torstol seed", "Grimy torstol", "Torstol"),
    ("MARIGOLD", "FLOWER", ["Marigold"], "Marigold seed", "Marigolds", ""),
    ("ROSEMARY", "FLOWER", ["Rosemary (plant)", "Rosemary plant"], "Rosemary seed", "Rosemary", ""),
    ("NASTURTIUM", "FLOWER", ["Nasturtium"], "Nasturtium seed", "Nasturtiums", ""),
    ("WOAD", "FLOWER", ["Woad plant"], "Woad seed", "Woad leaf", ""),
    ("LIMPWURT", "FLOWER", ["Limpwurt plant"], "Limpwurt seed", "Limpwurt root", ""),
    ("WHITE_LILY", "FLOWER", ["White lily (plant)"], "White lily seed", "White lily", ""),
    ("OAK", "TREE", ["Oak tree (Farming)"], "Oak sapling", "Oak logs", ""),
    ("WILLOW", "TREE", ["Willow tree (Farming)"], "Willow sapling", "Willow logs", ""),
    ("MAPLE", "TREE", ["Maple tree (Farming)"], "Maple sapling", "Maple logs", ""),
    ("YEW", "TREE", ["Yew tree (Farming)"], "Yew sapling", "Yew logs", ""),
    ("MAGIC", "TREE", ["Magic tree (Farming)"], "Magic sapling", "Magic logs", ""),
    ("APPLE", "FRUIT_TREE", ["Apple tree"], "Apple sapling", "Cooking apple", ""),
    ("BANANA", "FRUIT_TREE", ["Banana tree (Farming)"], "Banana sapling", "Banana", ""),
    ("ORANGE", "FRUIT_TREE", ["Orange tree"], "Orange sapling", "Orange", ""),
    ("CURRY", "FRUIT_TREE", ["Curry tree"], "Curry sapling", "Curry leaf", ""),
    ("PINEAPPLE", "FRUIT_TREE", ["Pineapple plant"], "Pineapple sapling", "Pineapple", ""),
    ("PAPAYA", "FRUIT_TREE", ["Papaya tree"], "Papaya sapling", "Papaya fruit", ""),
    ("PALM", "FRUIT_TREE", ["Palm tree (Farming)"], "Palm sapling", "Coconut", ""),
    ("DRAGONFRUIT", "FRUIT_TREE", ["Dragonfruit tree"], "Dragonfruit sapling", "Dragonfruit", ""),
]


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


def wikitext(title):
    d = api({"action": "parse", "page": title, "prop": "wikitext", "redirects": 1})
    return d.get("parse", {}).get("wikitext", {}).get("*", "")


def template(text, name):
    """Parameters of the first {{name ...}} template, lower-cased keys."""
    m = re.search(r"\{\{" + re.escape(name) + r"\s*\n?(.*?)\n\}\}", text, re.S | re.I)
    if not m:
        m = re.search(r"\{\{" + re.escape(name) + r"(.*?)\}\}", text, re.S | re.I)
    if not m:
        return None
    params = {}
    for part in re.split(r"\n\s*\||\|(?=\w+\s*=)", m.group(1)):
        if "=" in part:
            k, v = part.split("=", 1)
            params[k.strip().lower()] = v.strip()
    return params


def number(value):
    """A xp / level value, or None for 'No'."""
    m = re.match(r"[\d,]*\.?\d+", (value or "").replace(",", ""))
    return float(m.group(0)) if m else None


def load_objs(path):
    """lower-case name -> candidate ids, best first (tradeable, then lowest id)."""
    by_name = {}
    for line in open(path):
        m = re.match(r'(\d+) name="(.*?)" cost=(-?\d+) stackable=\d+ members=\w+ tradeable=(\w+) certTemplate=(-?\d+) '
                     r'placeholderTemplate=(-?\d+)', line)
        if not m:
            continue
        if int(m.group(5)) != -1 or int(m.group(6)) != -1:
            continue
        by_name.setdefault(m.group(2).lower(), []).append((m.group(4) != "true", int(m.group(1))))
    return {k: [o for _, o in sorted(v)] for k, v in by_name.items()}


def obj_id(objs, name):
    ids = objs.get(name.lower())
    return ids[0] if ids else None


def fmt(x):
    return "" if x is None else ("%g" % x)


def main():
    objs_path, out_dir = sys.argv[1], sys.argv[2]
    objs = load_objs(objs_path)
    rows = []
    for key, patch, pages, seed, product, clean in CROPS:
        text = ""
        for title in pages:
            text = wikitext(title)
            time.sleep(0.2)
            if "{{Farming info" in text:
                break
        info = template(text, "Farming info")
        if info is None:
            print("no {{Farming info}}:", key, pages, file=sys.stderr)
            continue
        t = re.search(r"\((?:(\d+)\s*\+\s*)?(\d+)\s*x\s*(\d+)\s*min", info.get("time", ""))
        if not t:
            print("no growth time:", key, info.get("time"), file=sys.stderr)
            continue
        # "5 + 9x40 min": the first stage is shorter than the rest (Wiki "Farming#Growth timing", yew trees).
        cycles, cycle_min = int(t.group(2)), int(t.group(3))
        first_min = int(t.group(1)) if t.group(1) else cycle_min
        if t.group(1):
            cycles += 1
        yc = re.search(r"\{\{Farming yield calculator[^}]*?low\s*=\s*(\d+)[^}]*?high\s*=\s*(\d+)", text, re.S)
        seed_id, product_id = obj_id(objs, seed), obj_id(objs, product)
        clean_id = obj_id(objs, clean) if clean else -1
        if seed_id is None or product_id is None or clean_id is None:
            print("no obj:", key, seed, product, clean, file=sys.stderr)
            continue
        rows.append((
            key, patch, int(number(info.get("level")) or 1), int(number(info.get("seedsper")) or 1), seed_id, product_id, clean_id,
            fmt(number(info.get("plantxp"))), fmt(number(info.get("harvestxp"))), fmt(number(info.get("checkxp"))),
            cycle_min, first_min, cycles, yc.group(1) if yc else "", yc.group(2) if yc else "", info.get("name", key),
        ))
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "farming-crops.tsv"), "w") as f:
        f.write("key\tpatch\tlevel\tseeds_per\tseed\tproduce\tclean\tplant_xp\tharvest_xp\tcheck_xp\tcycle_min\tfirst_min\tcycles\tcts_low\tcts_high\tname\n")
        for row in rows:
            f.write("\t".join(str(c) for c in row) + "\n")
    print(f"{len(rows)} rows")


if __name__ == "__main__":
    main()
