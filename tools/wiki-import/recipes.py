#!/usr/bin/env python3
"""
Smithing (furnace), Crafting, Fletching and Herblore production recipes from the OSRS Wiki (docs/mechanics/smithing.md,
crafting.md, fletching.md, herblore.md).

Each product page named in PAGES is read and every top-level {{Recipe}} on it whose first output is that product and
whose first skill is Smithing or Crafting is kept (the Wiki's Recipe template, Module:Recipe). The pages in
ANY_OUTPUT_PAGES (Fletching and Herblore; a potion page's output is the dose-suffixed item, "Attack potion(3)") keep every
recipe of those skills on the page with at most MAX_ANY_MATERIALS consumed materials (three-material variants such as the
coconut milk antidotes are not modelled). Recipes of other skills,
Blast Furnace and Superheat Item variants, costume-needle variants (no thread) and sailing/quest-only variants are
dropped by the filters below. From the template parameters:

  skill1 / skill1lvl / skill1exp   the skill, its level requirement and the xp per action
  ticks / ticksnote                ticks per action; the note may give the first action ("Make-X is 4, then 5") and a
                                   single action ("A single bar is 6"), which become first_ticks / single_ticks
  tools / facilities               comma lists of tool names (kept in the backpack) and the facility (Furnace, ...)
  matN / matNquantity              consumed items and counts
  output1 / output1quantity        the product and its count

Names are matched to cache objs the way shops.py does (not noted, not a placeholder; tradeable first, then the lowest
id). A recipe with a name that matches no obj is reported and dropped.

Output (tab-separated, header first, in the output directory):
  recipes.tsv   output count skill level xp ticks first_ticks single_ticks members tools facilities materials name

`ticks`, `first_ticks` and `single_ticks` are always filled (first/single default to ticks).

Usage (needs network access to oldschool.runescape.wiki and `./gradlew -q :tools:cache-dump:run --args="config obj --all"`):
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  python3 -I tools/wiki-import/recipes.py objs.txt content/skills/src/main/resources/tp/content/skills

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

GEMS = ["Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone", "Onyx"]
PAGES = (
    ["Bronze bar", "Iron bar", "Silver bar", "Steel bar", "Gold bar", "Mithril bar", "Adamantite bar", "Runite bar", "Molten glass"]
    + ["Leather gloves", "Leather boots", "Leather cowl", "Leather vambraces", "Leather body", "Leather chaps", "Hardleather body", "Coif"]
    + [f"{c} d'hide {i}" for c in ("Green", "Blue", "Red", "Black") for i in ("vambraces", "chaps", "body")]
    + ["Snakeskin boots", "Snakeskin vambraces", "Snakeskin bandana", "Snakeskin chaps", "Snakeskin body"]
    + ["Opal", "Jade", "Red topaz", "Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone", "Onyx"]
    + ["Ball of wool", "Bow string", "Crossbow string", "Magic string"]
    + ["Unfired pot", "Unfired pie dish", "Unfired bowl", "Unfired plant pot", "Unfired pot lid"]
    + ["Pot", "Pie dish", "Bowl", "Empty plant pot", "Pot lid"]
    + ["Beer glass", "Empty candle lantern", "Empty oil lamp", "Vial", "Fishbowl", "Unpowered orb", "Lantern lens", "Empty light orb"]
    + ["Gold ring", "Gold necklace", "Gold bracelet", "Gold amulet (u)"]
    + [f"{g} {i}" for g in GEMS if g != "Dragonstone" for i in ("ring", "necklace", "bracelet", "amulet (u)")]
    + ["Dragonstone ring", "Dragon necklace", "Dragon bracelet=Dragonstone bracelet", "Dragonstone amulet (u)"]
    + ["Unstrung symbol", "Silver sickle", "Tiara"]
    + ["Gold amulet", "Sapphire amulet", "Emerald amulet", "Ruby amulet", "Diamond amulet", "Dragonstone amulet", "Onyx amulet"]
    + ["Unblessed symbol"]
)
WOODS = ["Oak", "Willow", "Maple", "Yew", "Magic"]
METALS = ["Bronze", "Iron", "Steel", "Mithril", "Adamant", "Rune"]
GEM_TIPS = ["Opal", "Jade", "Pearl", "Topaz", "Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone", "Onyx"]
HERBS = ["Guam leaf", "Marrentill", "Tarromin", "Harralander", "Ranarr weed", "Toadflax", "Irit leaf", "Avantoe", "Kwuarm",
         "Snapdragon", "Cadantine", "Lantadyme", "Dwarf weed", "Torstol"]
UNFINISHED = ["Guam", "Marrentill", "Tarromin", "Harralander", "Ranarr", "Toadflax", "Irit", "Avantoe", "Kwuarm", "Snapdragon",
              "Cadantine", "Lantadyme", "Dwarf weed", "Torstol"]
POTIONS = ["Attack potion", "Antipoison", "Strength potion", "Restore potion", "Energy potion", "Defence potion",
           "Agility potion", "Combat potion", "Prayer potion", "Super attack", "Superantipoison", "Fishing potion",
           "Super energy", "Hunter potion", "Super strength", "Weapon poison", "Super restore", "Super defence",
           "Antidote+", "Antifire potion", "Ranging potion", "Magic potion", "Zamorak brew", "Saradomin brew",
           "Antidote++", "Bastion potion", "Battlemage potion", "Extended antifire", "Super antifire potion",
           "Extended super antifire", "Stamina potion", "Weapon poison+", "Weapon poison++",
           "Anti-venom", "Anti-venom+", "Prayer regeneration potion", "Goading potion",
           "Divine super attack potion", "Divine super strength potion", "Divine super defence potion",
           "Divine ranging potion", "Divine magic potion", "Divine bastion potion", "Divine battlemage potion",
           "Menaphite remedy"]
ANY_OUTPUT_PAGES = (
    ["Arrow shaft", "Javelin shaft", "Headless arrow", "Shortbow (u)", "Longbow (u)", "Shortbow", "Longbow"]
    + [f"{w} {b}" for w in WOODS for b in ("shortbow (u)", "longbow (u)", "shortbow", "longbow")]
    + [f"{w} stock" for w in ["Wooden", "Oak", "Willow", "Teak", "Maple", "Mahogany", "Yew", "Magic"]]
    + [f"{m} crossbow (u)" for m in ["Bronze", "Blurite", "Iron", "Steel", "Mithril", "Adamant", "Runite", "Dragon"]]
    + [f"{m} crossbow" for m in ["Bronze", "Blurite", "Iron", "Steel", "Mithril", "Adamant", "Rune", "Dragon"]]
    + [f"{m} arrow" for m in METALS + ["Amethyst", "Dragon"]]
    + [f"{m} dart" for m in ["Bronze", "Iron", "Steel", "Black", "Mithril", "Adamant", "Rune", "Amethyst", "Dragon"]]
    + [f"{m} javelin" for m in METALS + ["Amethyst", "Dragon"]]
    + [f"{m} bolts" for m in ["Bronze", "Blurite", "Iron", "Silver", "Steel", "Mithril", "Adamant", "Rune", "Dragon"]]
    + [f"{g} bolt tips" for g in GEM_TIPS]
    + [f"{g} bolts" for g in GEM_TIPS]
    + [f"{g} dragon bolts" for g in GEM_TIPS]
    + HERBS
    + [f"{h} potion (unf)" for h in UNFINISHED]
    + POTIONS
)
# Secondaries made by crushing with the pestle and mortar (Herblore, no level or xp).
GRIND_PAGES = ["Unicorn horn dust", "Chocolate dust", "Dragon scale dust", "Crushed nest", "Kebbit teeth dust", "Goat horn dust"]
# Wiki item names that the cache calls by another name (the empty bird's nest is the tradeable "Bird nest").
NAME_ALIASES = {"bird nest (empty)": "bird nest"}
# Recipes of these skills on ANY_OUTPUT_PAGES are kept with at most this many consumed materials.
MAX_ANY_MATERIALS = 2
SKILLS = {"smithing", "crafting", "fletching", "herblore"}
# Facilities and tools of variants this server does not model as ordinary recipes.
DROP_FACILITIES = {"blast furnace", "ancient furnace", "lovakite furnace"}
DROP_TOOLS = {"costume needle", "jeweller's chisel"}
DROP_SUBTEXT = r"catalys|blast|superheat|ancient|lovakite"


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


def wikitexts(titles):
    """title as asked -> wikitext, following normalisation and redirects."""
    out = {}
    for i in range(0, len(titles), 50):
        batch = titles[i:i + 50]
        d = api({"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main", "redirects": 1,
                 "titles": "|".join(batch)})
        q = d["query"]
        alias = {}
        for key in ("normalized", "redirects"):
            for e in q.get(key, []):
                alias[e["from"]] = e["to"]
        texts = {}
        for page in q["pages"].values():
            revs = page.get("revisions")
            if revs:
                texts[page["title"]] = revs[0]["slots"]["main"]["*"]
        for t in batch:
            cur, seen = t, set()
            while cur in alias and cur not in seen:
                seen.add(cur)
                cur = alias[cur]
            if cur in texts:
                out[t] = texts[cur]
        time.sleep(0.2)
    return out


def template_end(text, i):
    """Index just after the }} that closes the {{ at i."""
    depth, j = 0, i
    while j < len(text):
        if text.startswith("{{", j):
            depth += 1
            j += 2
        elif text.startswith("}}", j):
            depth -= 1
            j += 2
            if depth == 0:
                return j
        else:
            j += 1
    return len(text)


def recipes_in(text):
    """Bodies of every {{Recipe ...}} (brace-balanced, also inside tab templates)."""
    out, start, opener = [], 0, "{{Recipe"
    while True:
        i = text.find(opener, start)
        if i < 0:
            return out
        after = text[i + len(opener):i + len(opener) + 1]
        if after not in ("|", "\n", " ", "}"):
            start = i + len(opener)
            continue
        end = template_end(text, i)
        out.append(text[i + len(opener):end - 2])
        start = end


def split_params(body):
    parts, depth, cur, i = [], 0, [], 0
    while i < len(body):
        two = body[i:i + 2]
        if two in ("{{", "[["):
            depth += 1
            cur.append(two)
            i += 2
            continue
        if two in ("}}", "]]"):
            depth -= 1
            cur.append(two)
            i += 2
            continue
        if body[i] == "|" and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(body[i])
        i += 1
    parts.append("".join(cur))
    return [p.strip() for p in parts]


def clean(v):
    v = re.sub(r"<ref[^>]*/>", "", v)
    v = re.sub(r"<ref[^>]*>.*?</ref>", "", v, flags=re.S)
    v = re.sub(r"<!--.*?-->", "", v, flags=re.S)
    v = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", v)
    v = re.sub(r"\{\{[^}]*\}\}", "", v)
    return v.strip()


def params_of(body):
    params = {}
    for p in split_params(body):
        if "=" in p:
            k, v = p.split("=", 1)
            params[k.strip().lower()] = clean(v)
    return params


def num(v, default=None):
    v = (v or "").replace(",", "").strip()
    return int(v) if re.fullmatch(r"-?\d+", v) else default


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


def ticks_of(p):
    """(ticks, first_ticks, single_ticks) from ticks and ticksnote."""
    ticks = num(p.get("ticks"), 0)
    note = p.get("ticksnote", "")
    first = single = ticks
    m = re.search(r"make-(?:x|all)(?: and make-(?:x|all))? (?:is|takes)? ?(\d+)(?: ticks?)?,? then (\d+)", note, re.I)
    if m:
        first, ticks = int(m.group(1)), int(m.group(2))
    m = re.search(r"single \w+ (?:is|takes) (\d+)", note, re.I)
    if m:
        single = int(m.group(1))
    elif m is None and not re.search(r"make-", note, re.I):
        single = first
    return ticks, first, single


def main():
    objs_path, out_dir = sys.argv[1], sys.argv[2]
    objs = load_objs(objs_path)

    def oid(name):
        ids = objs.get(NAME_ALIASES.get(name.lower(), name.lower()))
        return ids[0] if ids else None

    pages = [e.split("=") for e in PAGES] + [[t, "*"] for t in ANY_OUTPUT_PAGES] + [[t, "grind"] for t in GRIND_PAGES]
    texts = wikitexts([e[0] for e in pages])
    rows, seen = [], set()
    for entry in pages:
        title = entry[0]
        product = entry[-1]
        text = texts.get(title)
        if text is None:
            print("no page:", title, file=sys.stderr)
            continue
        kept = 0
        for body in recipes_in(text):
            p = params_of(body)
            tools = [t.strip() for t in p.get("tools", "").split(",") if t.strip()]
            grind = product == "grind"
            if grind:
                # Grinding a secondary with the pestle and mortar has no skill in the template: no level, no xp.
                if p.get("skill1") or [t.lower() for t in tools] != ["pestle and mortar"] or p.get("output1", "").lower() != title.lower():
                    continue
                p["skill1"], p["skill1lvl"], p["skill1exp"] = "Herblore", "1", "0"
            elif p.get("skill1", "").lower() not in SKILLS or p.get("skill2"):
                continue
            facilities = [f.strip() for f in p.get("facilities", "").split(",") if f.strip()]
            if any(f.lower() in DROP_FACILITIES for f in facilities) or any(t.lower() in DROP_TOOLS for t in tools):
                continue
            any_output = product == "*"
            if any_output and p["skill1"].lower() not in ("fletching", "herblore"):
                continue
            if not any_output and not grind and p.get("output1", "").lower() != product.lower():
                continue
            if re.search(DROP_SUBTEXT, p.get("output1subtxt", ""), re.I):
                continue
            mats = []
            n = 1
            while f"mat{n}" in p:
                mats.append((p[f"mat{n}"], num(p.get(f"mat{n}quantity"), 1)))
                n += 1
            if any_output and len(mats) > MAX_ANY_MATERIALS:
                continue
            ticks, first, single = ticks_of(p)
            out_id = oid(p["output1"])
            ids = [oid(m) for m, _ in mats]
            tool_ids = [oid(t) for t in tools]
            if out_id is None or None in ids or None in tool_ids:
                print("unmatched in", title, ":", p["output1"], mats, tools, file=sys.stderr)
                continue
            key = (out_id, tuple(ids), tuple(tool_ids), tuple(facilities))
            if key in seen:
                continue
            seen.add(key)
            rows.append([
                out_id, num(p.get("output1quantity"), 1), p["skill1"].lower(), num(p.get("skill1lvl"), 1),
                p.get("skill1exp", "0").replace(",", ""), ticks, first, single, p.get("members", "").lower(),
                ",".join(str(t) for t in tool_ids), ",".join(f.lower() for f in facilities),
                ",".join(f"{i}:{q}" for i, (_, q) in zip(ids, mats)), p["output1"] + (" [" + p.get("output1subtxt", "") + "]" if p.get("output1subtxt") else ""),
            ])
            kept += 1
        if kept == 0:
            print("no recipe kept:", title, file=sys.stderr)
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "recipes.tsv"), "w") as f:
        f.write("output\tcount\tskill\tlevel\txp\tticks\tfirst_ticks\tsingle_ticks\tmembers\ttools\tfacilities\tmaterials\tname\n")
        for r in rows:
            f.write("\t".join(str(c) for c in r) + "\n")
    print(f"{len(rows)} recipes", file=sys.stderr)


if __name__ == "__main__":
    main()
