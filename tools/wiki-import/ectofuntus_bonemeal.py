#!/usr/bin/env python3
"""
Bone -> pot of bonemeal objs for the Ectofuntus (docs/mechanics/prayer-training.md PT6).

The Wiki page "Bonemeal" lists one row per bone type: {{plinkp|<bonemeal item>}} {{plinkt|<bone item>}} bury xp, worship xp.
Both names are resolved to cache objs (the first non-noted, non-placeholder obj of that name). Rows whose bone is not in
remains.tsv (Alan's bones, long bone, curved bone, burnt jogre bones) are left out.

Output (tab-separated, header first) is written to the file given as the third argument:
  bone (obj id), bonemeal (obj id), name (bone name), bury (xp), worship (xp)

Usage (needs network access to oldschool.runescape.wiki; objs.txt from cache-dump):
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  python3 -I tools/wiki-import/ectofuntus_bonemeal.py objs.txt content/skills/src/main/resources/tp/content/skills/remains.tsv \
      content/skills/src/main/resources/tp/content/skills/bonemeal.tsv

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import re
import sys

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from thieving_pickpocket import load_objs, wikitext  # noqa: E402


def main():
    objs = load_objs(sys.argv[1])
    bones = {int(line.split("\t")[0]) for line in open(sys.argv[2], encoding="utf-8").read().splitlines()[1:]
             if line.split("\t")[1] == "bone"}
    text = wikitext("Bonemeal")
    rows = re.findall(
        r"\{\{plinkp\|([^}|]+)\}\}\s*\|\{\{plinkt\|([^}|]+)\}\}\s*\|([\d.]+)\s*\|([\d.]+)", text)
    out = ["bone\tbonemeal\tname\tbury\tworship"]
    seen = set()
    for meal, bone, bury, worship in rows:
        meal = re.sub(r" \(bones\)$", "", meal)
        bone_id = objs.get(bone.lower())
        meal_id = objs.get(meal.lower())
        if bone_id not in bones or bone_id in seen:
            continue
        if meal_id is None:
            print("no bonemeal obj for %r (%r)" % (meal, bone), file=sys.stderr)
            continue
        seen.add(bone_id)
        out.append("\t".join([str(bone_id), str(meal_id), bone, bury, worship]))
    # The "Bonemeal" table has no strykewyrm row; the "Ectofuntus" experience table gives 200 xp per strykewyrm bone (bury 60)
    # and the cache has "Strykewyrm bonemeal" (31333).
    if 31726 in bones and 31726 not in seen:
        seen.add(31726)
        out.append("\t".join(["31726", str(objs["strykewyrm bonemeal"]), "Strykewyrm bones", "60", "200"]))
    for missing in sorted(bones - seen):
        print("bone %d has no bonemeal row" % missing, file=sys.stderr)
    with open(sys.argv[3], "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
