#!/usr/bin/env python3
"""
Buryable bones and scatterable ashes from the OSRS Wiki (docs/mechanics/prayer-training.md).

Each page named in BONES / ASHES carries a `{{Prayer info}}` template:

  level   the Prayer level needed to bury or scatter it (1 unless stated)
  xp      the Prayer experience of burying the bone / scattering the ashes
  type    "bone" or "ashes" (the template's own classification; a page whose type disagrees is reported and dropped)

Names are matched to cache objs the way recipes.py does (not noted, not a placeholder; tradeable first, then the lowest
id). A page with no `{{Prayer info}}` or no matching obj is reported and dropped.

Output (tab-separated, header first, in the output directory):
  remains.tsv   obj kind level xp name

Usage (needs network access to oldschool.runescape.wiki and `./gradlew -q :tools:cache-dump:run --args="config obj --all"`):
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  python3 -I tools/wiki-import/prayer_remains.py objs.txt content/skills/src/main/resources/tp/content/skills

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

# The bones of the Wiki's "Prayer training" comparison table (Calculator:Prayer/Bones) and the ashes of its Offering spells table.
BONES = [
    "Bones", "Wolf bones", "Burnt bones", "Monkey bones", "Bat bones", "Big bones", "Jogre bones", "Wyrmling bones", "Zogre bones",
    "Shaikahan bones", "Babydragon bones", "Wyrm bones", "Strykewyrm bones", "Wyvern bones", "Dragon bones", "Drake bones",
    "Fayrg bones", "Lava dragon bones", "Raurg bones", "Frost dragon bones", "Hydra bones", "Dagannoth bones", "Ourg bones",
    "Superior dragon bones",
]
ASHES = ["Fiendish ashes", "Vile ashes", "Malicious ashes", "Abyssal ashes", "Infernal ashes"]


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


def prayer_info(text):
    """Parameters of the first {{Prayer info}} template, lower-cased keys."""
    m = re.search(r"\{\{Prayer info(.*?)\}\}", text, re.S | re.I)
    if not m:
        return None
    params = {}
    for part in m.group(1).split("|"):
        if "=" in part:
            k, v = part.split("=", 1)
            params[k.strip().lower()] = v.strip()
    return params


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


def main():
    objs_path, out_dir = sys.argv[1], sys.argv[2]
    objs = load_objs(objs_path)
    rows = []
    for kind, names in (("bone", BONES), ("ash", ASHES)):
        for name in names:
            info = prayer_info(wikitext(name))
            time.sleep(0.2)
            if info is None or not info.get("type", "").lower().startswith(kind):
                print("no matching {{Prayer info}}:", name, info, file=sys.stderr)
                continue
            ids = objs.get(name.lower())
            if not ids:
                print("no obj:", name, file=sys.stderr)
                continue
            level = int(info.get("level", "1") or 1)
            rows.append((ids[0], kind, level, float(info["xp"].replace(",", "")), name))
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "remains.tsv"), "w") as f:
        f.write("obj\tkind\tlevel\txp\tname\n")
        for obj, kind, level, xp, name in rows:
            f.write(f"{obj}\t{kind}\t{level}\t{xp:g}\t{name}\n")
    print(f"{len(rows)} rows")


if __name__ == "__main__":
    main()
