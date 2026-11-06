#!/usr/bin/env python3
"""
Hunter creature data from the OSRS Wiki (docs/mechanics/hunter.md).

Every creature page carries
  {{Hunter info |name |level |xp |wildxp |trap |...}}   level, xp (xp is the Puro-Puro value for implings and `wildxp` the
                                                         value outside it; the other creatures have only `xp`)
  {{Skilling success chart |label1 |low1 |high1 ...}}    the catch roll (line 1 = normal; for nets line 2 = barehanded or
                                                         magic butterfly net); the three chinchompas share one chart
  {{DropsLineSkill |name |quantity |rarity}}              the loot; only the "Always" rows are imported (the Hunters' Rumours
                                                         tufts and the pet rows are skipped, hunter.md OPEN-HU8)
  |id = ...                                               the cache NPC ids of the creature

Net creatures (butterflies and implings) have no drop lines: the product is the jar named in `JARS`. Creatures whose page has
no Skilling success chart (ruby harvest, snowy knight, sapphire glacialis) are skipped (hunter.md OPEN-HU7).

Output (tab-separated, header first), one row per creature, written to the file given as the second argument:
  creature, trap (bird|box|net), level, xp, low, high (catch roll), low2, high2 (net line 2, - if none), npcs (comma separated
  cache ids), loot (`obj:min-max;obj:min-max`, cache obj ids; for nets the jar obj)

Usage (needs network access to oldschool.runescape.wiki; objs.txt from cache-dump):
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  python3 -I tools/wiki-import/hunter_creatures.py objs.txt content/skills/src/main/resources/tp/content/skills/hunter.tsv

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

# Wiki page -> (trap kind, chart label word for the shared chinchompa chart or "")
PAGES = {
    "Crimson swift": ("bird", ""), "Golden warbler": ("bird", ""), "Copper longtail": ("bird", ""),
    "Cerulean twitch": ("bird", ""), "Tropical wagtail": ("bird", ""),
    "Ferret (Hunter)": ("box", ""), "Chinchompa (Hunter)": ("box", "Grey"), "Carnivorous chinchompa": ("box", "Red"),
    "Black chinchompa (Hunter)": ("box", "Black"),
    "Black warlock": ("net", ""),
    "Baby impling": ("net", ""), "Young impling": ("net", ""), "Gourmet impling": ("net", ""), "Earth impling": ("net", ""),
    "Essence impling": ("net", ""), "Eclectic impling": ("net", ""), "Nature impling": ("net", ""),
    "Magpie impling": ("net", ""), "Ninja impling": ("net", ""), "Dragon impling": ("net", ""),
    "Crystal impling": ("net", ""), "Lucky impling": ("net", ""),
}

# Net creature -> the jar item it is caught in (cache name of the filled jar).
JARS = {"Black warlock": "Black warlock"}
for _imp in ["Baby", "Young", "Gourmet", "Earth", "Essence", "Eclectic", "Nature", "Magpie", "Ninja", "Dragon", "Crystal",
             "Lucky"]:
    JARS[_imp + " impling"] = _imp + " impling jar"


def wikitext(page):
    url = API + "?" + urllib.parse.urlencode({"action": "parse", "page": page, "prop": "wikitext", "redirects": 1, "format": "json"})
    for attempt in range(6):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=60) as r:
                return json.load(r)["parse"]["wikitext"]["*"]
        except Exception:  # noqa: BLE001 - retry transient network errors
            time.sleep(2 ** attempt)
    raise RuntimeError("wiki request failed: " + page)


def obj_ids(path):
    """Cache obj name (lower case) -> id of the first tradeable, un-noted, non-placeholder obj (else the first)."""
    best = {}
    for line in open(path):
        m = re.match(r'(\d+) name="(.*?)" cost=\S+ stackable=(\d+) members=\S+ tradeable=(\w+) certTemplate=(-?\d+) '
                     r'placeholderTemplate=(-?\d+)', line)
        if not m:
            continue
        i, name, stack, trade, cert, ph = int(m.group(1)), m.group(2).lower(), int(m.group(3)), m.group(4), int(m.group(5)), int(m.group(6))
        if cert != -1 or ph != -1 or stack == 2:
            continue
        rank = 0 if trade == "true" else 1
        if name not in best or rank < best[name][0]:
            best[name] = (rank, i)
    return {k: v[1] for k, v in best.items()}


def template(text, name):
    m = re.search(r"\{\{" + re.escape(name) + r"\b(.*?)\n?\}\}", text, re.S)
    return m.group(1) if m else None


def fields(body):
    return {k.strip().lower(): v.strip() for k, v in re.findall(r"\|\s*([\w ]+?)\s*=\s*([^|\n]*)", body)}


def number(v, default=0.0):
    m = re.search(r"-?\d+(\.\d+)?", (v or "").replace(",", ""))
    return float(m.group()) if m else default


def chart_rows(text):
    """[(label, low, high)] of the page's Skilling success chart (only charts about catching)."""
    body = template(text, "Skilling success chart")
    if body is None:
        return []
    f = fields(body)
    rows, n = [], 1
    while f"low{n}" in f:
        rows.append((f.get(f"label{n}", ""), int(number(f[f"low{n}"])), int(number(f[f"high{n}"]))))
        n += 1
    return rows


def main():
    objs, dest = obj_ids(sys.argv[1]), sys.argv[2]
    out = []
    for page, (trap, word) in PAGES.items():
        text = wikitext(page)
        info = fields(template(text, "Hunter info") or "")
        rows = chart_rows(text)
        if not info or not rows:
            print(f"skipped {page}: no Hunter info / Skilling success chart", file=sys.stderr)
            continue
        if word:
            rows = [r for r in rows if word.lower() in r[0].lower()]
        low, high = rows[0][1], rows[0][2]
        low2, high2 = (rows[1][1], rows[1][2]) if trap == "net" and len(rows) > 1 else ("-", "-")
        xp = number(info.get("wildxp") or info.get("xp"))
        ids = []
        for m in re.finditer(r"\|\s*id\d*\s*=\s*([\d,\s]+)", text):
            ids += [i for i in re.findall(r"\d+", m.group(1)) if i not in ids]
        loot = []
        if trap == "net":
            jar = objs.get(JARS[page].lower())
            if jar is not None:
                loot.append(f"{jar}:1-1")
        else:
            for name, qty, rarity in re.findall(r"\{\{DropsLineSkill\|name=([^|]*)\|(?:[^|}]*\|)*?quantity=([^|}]*)\|rarity=([^|}]*)", text):
                if rarity.strip().lower() != "always":
                    continue
                obj = objs.get(name.strip().lower())
                lo, _, hi = qty.strip().partition("-")
                if obj is None:
                    print(f"{page}: no cache obj named {name!r}", file=sys.stderr)
                    continue
                loot.append(f"{obj}:{lo}-{hi or lo}")
        if not loot:
            print(f"skipped {page}: no loot / jar", file=sys.stderr)
            continue
        name = (info.get("name") or page).replace("\t", " ")
        out.append([page, trap, int(number(info.get("level"))), f"{xp:g}", low, high, low2, high2, ",".join(ids), ";".join(loot)])
        time.sleep(0.2)
    with open(dest, "w") as f:
        f.write("creature\ttrap\tlevel\txp\tlow\thigh\tlow2\thigh2\tnpcs\tloot\n")
        for r in out:
            f.write("\t".join(str(c) for c in r) + "\n")
    print(f"wrote {len(out)} creatures to {dest}", file=sys.stderr)


if __name__ == "__main__":
    main()
