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
  farming-crops.tsv     key patch level seeds_per seed produce clean plant_xp harvest_xp check_xp cycle_min first_min cycles cts_low cts_high name
  farming-saplings.tsv  key tree_seed seedling seedling_watered sapling   (Wiki "Seedling" / "Sapling": seed in a filled plant pot, watered, grown)
  farming-payments.tsv  key item count                                    (Wiki "Seeds": what a farmer takes to protect a patch)

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
    ("REDBERRIES", "BUSH", ["Redberry bush"], "Redberry seed", "Redberries", ""),
    ("CADAVABERRIES", "BUSH", ["Cadavaberry bush"], "Cadavaberry seed", "Cadava berries", ""),
    ("DWELLBERRIES", "BUSH", ["Dwellberry bush"], "Dwellberry seed", "Dwellberries", ""),
    ("JANGERBERRIES", "BUSH", ["Jangerberry bush"], "Jangerberry seed", "Jangerberries", ""),
    ("WHITEBERRIES", "BUSH", ["Whiteberry bush"], "Whiteberry seed", "White berries", ""),
    ("POISON_IVY", "BUSH", ["Poison ivy bush"], "Poison ivy seed", "Poison ivy berries", ""),
    ("BARLEY", "HOPS", ["Barley (plant)"], "Barley seed", "Barley", ""),
    ("HAMMERSTONE", "HOPS", ["Hammerstone Hops (plant)"], "Hammerstone seed", "Hammerstone hops", ""),
    ("ASGARNIAN", "HOPS", ["Asgarnian Hops (plant)"], "Asgarnian seed", "Asgarnian hops", ""),
    ("JUTE", "HOPS", ["Jute (plant)", "Jute plant"], "Jute seed", "Jute fibre", ""),
    ("YANILLIAN", "HOPS", ["Yanillian Hops (plant)"], "Yanillian seed", "Yanillian hops", ""),
    ("FLAX", "HOPS", ["Flax (Farming)"], "Flax seed", "Flax", ""),
    ("KRANDORIAN", "HOPS", ["Krandorian Hops (plant)"], "Krandorian seed", "Krandorian hops", ""),
    ("WILDBLOOD", "HOPS", ["Wildblood Hops (plant)"], "Wildblood seed", "Wildblood hops", ""),
    ("HEMP", "HOPS", ["Hemp (Farming)", "Hemp plant"], "Hemp seed", "Hemp", ""),
    ("COTTON", "HOPS", ["Cotton (Farming)", "Cotton plant"], "Cotton seed", "Cotton boll", ""),
    ("CACTUS", "CACTUS", ["Cactus (Farming)"], "Cactus seed", "Cactus spine", ""),
    ("POTATO_CACTUS", "CACTUS", ["Potato cactus (plant)"], "Potato cactus seed", "Potato cactus", ""),
    ("MUSHROOM", "MUSHROOM", ["Bittercap Mushrooms"], "Mushroom spore", "Mushroom", ""),
    ("BELLADONNA", "BELLADONNA", ["Belladonna"], "Belladonna seed", "Nightshade", ""),
    ("CALQUAT", "CALQUAT", ["Calquat tree"], "Calquat sapling", "Calquat fruit", ""),
    ("SPIRIT_TREE", "SPIRIT_TREE", ["Spirit Tree (Farming)"], "Spirit sapling", "", ""),
    ("SEAWEED", "SEAWEED", ["Seaweed (Farming)"], "Seaweed spore", "Giant seaweed", ""),
    ("CELASTRUS", "CELASTRUS", ["Celastrus tree"], "Celastrus sapling", "Celastrus bark", ""),
    ("REDWOOD", "REDWOOD", ["Redwood tree (Farming)"], "Redwood sapling", "Redwood logs", ""),
    ("HESPORI", "HESPORI", ["Hespori"], "Hespori seed", "", ""),
    ("CRYSTAL_TREE", "CRYSTAL_TREE", ["Crystal tree"], "Crystal sapling", "Crystal shard", ""),
    ("TEAK", "HARDWOOD_TREE", ["Teak tree (Farming)"], "Teak sapling", "Teak logs", ""),
    ("MAHOGANY", "HARDWOOD_TREE", ["Mahogany tree (Farming)"], "Mahogany sapling", "Mahogany logs", ""),
    ("CAMPHOR", "HARDWOOD_TREE", ["Camphor tree (Farming)"], "Camphor sapling", "Camphor logs", ""),
    ("IRONWOOD", "HARDWOOD_TREE", ["Ironwood tree (Farming)"], "Ironwood sapling", "Ironwood logs", ""),
    ("ROSEWOOD", "HARDWOOD_TREE", ["Rosewood tree (Farming)"], "Rosewood sapling", "Rosewood logs", ""),
    ("ATTAS", "ANIMA", ["Attas plant"], "Attas seed", "", ""),
    ("IASOR", "ANIMA", ["Iasor plant"], "Iasor seed", "", ""),
    ("KRONOS", "ANIMA", ["Kronos plant"], "Kronos seed", "", ""),
]


# Wiki "Seedling" / "Sapling": crop key -> the seed that goes into a filled plant pot (the sapling is the crop's own seed).
SAPLINGS = [
    ("OAK", "Acorn", "Oak"), ("WILLOW", "Willow seed", "Willow"), ("MAPLE", "Maple seed", "Maple"), ("YEW", "Yew seed", "Yew"),
    ("MAGIC", "Magic seed", "Magic"), ("APPLE", "Apple tree seed", "Apple"), ("BANANA", "Banana tree seed", "Banana"),
    ("ORANGE", "Orange tree seed", "Orange"), ("CURRY", "Curry tree seed", "Curry"), ("PINEAPPLE", "Pineapple seed", "Pineapple"),
    ("PAPAYA", "Papaya tree seed", "Papaya"), ("PALM", "Palm tree seed", "Palm"), ("DRAGONFRUIT", "Dragonfruit tree seed", "Dragonfruit"),
    ("TEAK", "Teak seed", "Teak"), ("MAHOGANY", "Mahogany seed", "Mahogany"), ("CAMPHOR", "Camphor seed", "Camphor"),
    ("IRONWOOD", "Ironwood seed", "Ironwood"), ("ROSEWOOD", "Rosewood seed", "Rosewood"), ("CALQUAT", "Calquat tree seed", "Calquat"),
    ("SPIRIT_TREE", "Spirit seed", "Spirit"), ("CELASTRUS", "Celastrus seed", "Celastrus"), ("REDWOOD", "Redwood tree seed", "Redwood"),
    ("CRYSTAL_TREE", "Crystal acorn", "Crystal"),
]

# Wiki "Seeds" (Allotment / Hops / Bush / Tree / Fruit tree / Special patches, "Protection"): crop key -> [(object name, count)].
# Every name is the cache name of the item the farmer asks for (sacks and baskets are the full "Potatoes(10)" / "Tomatoes(5)" items).
PAYMENTS = {
    "POTATO": [("Compost", 2)], "ONION": [("Potatoes(10)", 1)], "CABBAGE": [("Onions(10)", 1)], "TOMATO": [("Cabbages(10)", 2)],
    "SWEETCORN": [("Jute fibre", 10)], "STRAWBERRY": [("Apples(5)", 1)], "WATERMELON": [("Curry leaf", 10)],
    "SNAPE_GRASS": [("Jangerberries", 5)],
    "BARLEY": [("Compost", 3)], "HAMMERSTONE": [("Marigolds", 1)], "ASGARNIAN": [("Onions(10)", 1)], "JUTE": [("Barley malt", 6)],
    "YANILLIAN": [("Tomatoes(5)", 1)], "FLAX": [("Grain", 6)], "KRANDORIAN": [("Cabbages(10)", 3)], "WILDBLOOD": [("Nasturtiums", 1)],
    "HEMP": [("Flax", 6)], "COTTON": [("Hemp", 6)],
    "REDBERRIES": [("Cabbages(10)", 4)], "CADAVABERRIES": [("Tomatoes(5)", 3)], "DWELLBERRIES": [("Strawberries(5)", 3)],
    "JANGERBERRIES": [("Watermelon", 6)], "WHITEBERRIES": [("Mushroom", 8)],
    "OAK": [("Tomatoes(5)", 1)], "WILLOW": [("Apples(5)", 1)], "MAPLE": [("Oranges(5)", 1)], "YEW": [("Cactus spine", 10)],
    "MAGIC": [("Coconut", 25)],
    "APPLE": [("Sweetcorn", 9)], "BANANA": [("Apples(5)", 4)], "ORANGE": [("Strawberries(5)", 3)], "CURRY": [("Bananas(5)", 5)],
    "PINEAPPLE": [("Watermelon", 10)], "PAPAYA": [("Pineapple", 10)], "PALM": [("Papaya fruit", 15)], "DRAGONFRUIT": [("Coconut", 15)],
    "SEAWEED": [("Numulite", 200)], "CACTUS": [("Cadava berries", 6)], "POTATO_CACTUS": [("Snape grass", 8)],
    "TEAK": [("Limpwurt root", 15)], "MAHOGANY": [("Yanillian hops", 25)], "CAMPHOR": [("White berries", 10)],
    "IRONWOOD": [("Curry leaf", 10)], "ROSEWOOD": [("Dragonfruit", 8)], "CALQUAT": [("Poison ivy berries", 8)],
    "CELASTRUS": [("Potato cactus", 8)], "REDWOOD": [("Dragonfruit", 6)],
    "SPIRIT_TREE": [("Monkey nuts", 5), ("Monkey bar", 1), ("Ground tooth", 1)],
}


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
        seed_id = obj_id(objs, seed)
        product_id = obj_id(objs, product) if product else -1
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
    with open(os.path.join(out_dir, "farming-saplings.tsv"), "w") as f:
        f.write("key\ttree_seed\tseedling\tseedling_watered\tsapling\n")
        crops = {r[0]: r for r in rows}
        for key, seed, name in SAPLINGS:
            seed_id, seedling = obj_id(objs, seed), obj_id(objs, name + " seedling")
            wet, sapling = obj_id(objs, name + " seedling (w)"), crops[key][4] if key in crops else None
            if None in (seed_id, seedling, wet, sapling):
                print("no sapling chain:", key, seed_id, seedling, wet, sapling, file=sys.stderr)
                continue
            f.write(f"{key}\t{seed_id}\t{seedling}\t{wet}\t{sapling}\n")
    with open(os.path.join(out_dir, "farming-payments.tsv"), "w") as f:
        f.write("key\titem\tcount\n")
        for key, items in PAYMENTS.items():
            for name, count in items:
                item = obj_id(objs, name)
                if item is None:
                    print("no payment obj:", key, name, file=sys.stderr)
                    continue
                f.write(f"{key}\t{item}\t{count}\n")
    print(f"{len(rows)} rows")


if __name__ == "__main__":
    main()
