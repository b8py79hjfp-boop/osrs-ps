#!/usr/bin/env python3
"""
Thievable stalls from the OSRS Wiki (docs/mechanics/thieving.md).

Each stall page carries {{Infobox Scenery |id / id1.. |options}} (the loc ids of every placement), {{Thieving info
|level |xp |time}} (`time` is the respawn: the stall stays empty that long) and {{DropsLineSkill}} rows (what a theft
yields; rows are mutually exclusive, rarities add up to 1). Stalls of the Port Roberts / Mor Ul Rek / Ape Atoll
variants that need quests or are not in this cache revision are left out.

Output (tab-separated, header first) is written to the file given as the second argument, one row per loot line:
  stall    the Wiki page
  locs     comma-separated loc ids of the stall (cache ids)
  level, xp, respawn (Wiki text, e.g. "2.4 seconds")
  item (cache obj id), name (Wiki item name), qty (min-max), num, den (exact fraction)

Usage (needs network access to oldschool.runescape.wiki; objs.txt from cache-dump):
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  python3 -I tools/wiki-import/thieving_stalls.py objs.txt content/skills/src/main/resources/tp/content/skills/stalls.tsv

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import re
import sys
import time

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from thieving_pickpocket import fields, load_objs, quantity, rarity, templates, wikitext  # noqa: E402

PAGES = [
    "Bakery stall", "Tea stall", "Crafting stall", "Silk stall", "Fruit Stall", "Seed stall", "Fur stall", "Fish stall",
    "Crossbow stall", "Silver stall", "Spice stall", "Gem stall",
    # Added 2026-11-07 (Wiki "Stall/Thievable"): vegetable / wine / Ape Atoll / Gu'Tanoth stalls. The Port Roberts variants are
    # left out (the market guards behave uniquely, the pages give no respawn: OPEN-TH14 in docs/mechanics/thieving.md).
    "Vegetable stall", "Wine stall", "Monkey general stall", "Food Stall", "Magic stall", "Scimitar stall", "Counter (Gu'Tanoth)",
]


def main():
    objs = load_objs(sys.argv[1])
    out = ["stall\tlocs\tlevel\txp\trespawn\titem\tname\tqty\tnum\tden"]
    for page in PAGES:
        text = wikitext(page)
        infobox = text[text.find("{{Infobox Scenery"):]
        infobox = infobox[:infobox.find("\n}}")]
        ids = []
        for key, value in re.findall(r"^\|\s*(id\d*)\s*=\s*(.*?)\s*$", infobox, re.M):
            ids += [part.strip() for part in value.split(",") if part.strip().isdigit()]
        info = {}
        for _, body in templates(text, "Thieving info"):
            info = fields(body)
            break
        if not info or not ids:
            print("skipped (no info/ids): " + page, file=sys.stderr)
            continue
        start = text.find("{{Thieving info")
        end = text.find("===Tertiary", start)
        for pos, body in templates(text[start:end if end > 0 else len(text)], "DropsLineSkill"):
            f = fields(body)
            frac = rarity(f.get("rarity", ""))
            name = f.get("name", "")
            item = objs.get(name.lower())
            if frac is None or item is None:
                print("skipped row %r on %s" % (name, page), file=sys.stderr)
                continue
            out.append("\t".join([page, ",".join(dict.fromkeys(ids)), info["level"], info["xp"], info["time"], str(item), name,
                                  quantity(f.get("quantity", "1")) or "1-1", str(frac.numerator), str(frac.denominator)]))
        time.sleep(0.2)
    with open(sys.argv[2], "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
