#!/usr/bin/env python3
"""
Thievable chests from the OSRS Wiki (docs/mechanics/thieving.md TH11).

A chest page carries {{Infobox Scenery |id1 (closed) |id2 (open)}}, {{Thieving info |level |xp |time}} (`time` is how long the
open chest stays open), two {{Skilling success chart}} lines under "Success chance" (1 = normal, 2 = with a lockpick), one under
"Teleport chance" (the chance of being teleported out on a failure), and {{DropsLineSkill}} rows under "Loot". The page's
{{UncommonSeedDropLines|P}} template is expanded from Template:UncommonSeedDropLines: each seed has weight W out of 1048 and
a rarity P * W / 1048. Clue-scroll pre-rolls are skipped (OPEN-TH5).

Output (tab-separated, header first) is written to the file given as the second argument, one row per loot line:
  chest, closed (loc id), open (loc id), level, xp, ticks (open time), low, high, plow, phigh (lockpick), tlow, thigh (teleport),
  item (cache obj id), name, qty (min-max), num, den (exact fraction)

Usage (needs network access to oldschool.runescape.wiki; objs.txt from cache-dump):
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  python3 -I tools/wiki-import/thieving_chests.py objs.txt content/skills/src/main/resources/tp/content/skills/thieving-chests.tsv

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import math
import re
import sys
from fractions import Fraction

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from thieving_pickpocket import fields, load_objs, quantity, rarity, templates, wikitext  # noqa: E402

PAGES = ["Chest (Aldarin Villas)"]
TICK_SECONDS = 0.6
SEED_BASE = 1048


def seed_rows(text, objs):
    """Expands {{UncommonSeedDropLines|P}} into (name, fraction) pairs."""
    out = []
    for _, body in templates(text, "UncommonSeedDropLines"):
        total = rarity(body.split("|")[1].strip())
        if total is None:
            continue
        template = wikitext("Template:UncommonSeedDropLines")
        for name, weight in re.findall(r"name=([^|]+)\|[^\n]*?rarity=1/\{\{#expr:1/\((\d+)\*", template):
            out.append((name.strip(), total * Fraction(int(weight), SEED_BASE)))
    return out


def main():
    objs = load_objs(sys.argv[1])
    out = ["chest\tclosed\topen\tlevel\txp\tticks\tlow\thigh\tplow\tphigh\ttlow\tthigh\titem\tname\tqty\tnum\tden"]
    for page in PAGES:
        text = wikitext(page)
        infobox = text[text.find("{{Infobox Scenery"):]
        infobox = infobox[:infobox.find("\n}}")]
        closed = re.search(r"^\|\s*id1\s*=\s*(\d+)", infobox, re.M).group(1)
        opened = re.search(r"^\|\s*id2\s*=\s*(\d+)", infobox, re.M).group(1)
        info = fields(templates(text, "Thieving info")[0][1])
        charts = [fields(b) for _, b in templates(text, "Skilling success chart")]
        success, teleport = charts[0], charts[1]
        ticks = round(float(info["time"].split()[0]) / TICK_SECONDS)
        head = [page, closed, opened, info["level"], info["xp"], str(ticks), success["low1"], success["high1"],
                success["low2"], success["high2"], teleport["low1"], teleport["high1"]]
        loot = text[text.find("==Loot=="):]
        rows = []
        for _, body in templates(loot, "DropsLineSkill"):
            f = fields(body)
            frac = rarity(f.get("rarity", ""))
            if frac is not None:
                rows.append((f["name"], quantity(f.get("quantity", "1")) or "1-1", frac))
        rows += [(name, "1-1", frac) for name, frac in seed_rows(loot, objs)]
        total = Fraction(0)
        for name, qty, frac in rows:
            item = objs.get(name.lower())
            if item is None:
                print("skipped row %r on %s" % (name, page), file=sys.stderr)
                continue
            total += frac
            out.append("\t".join(head + [str(item), name, qty, str(frac.numerator), str(frac.denominator)]))
        print("%s: rarities add up to %s" % (page, total), file=sys.stderr)
    with open(sys.argv[2], "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
