#!/usr/bin/env python3
"""
Cooking preparation steps from the OSRS Wiki (docs/mechanics/cooking.md CK13): dough, pie shells, uncooked pies, pizza bases and
toppings, cakes, stews, curries and unfermented wine. These are item-on-item recipes without a facility; the steps that need a
range (the cooked pies, stew, curry) are rows of CookingData.kt.

Each product page named in PAGES is read and every top-level {{Recipe}} on it whose first output is that product and whose skill
is Cooking (or has no skill: the plain dough pages) and has no facility is kept. Names are matched to cache objs like recipes.py.
`output2` / `output3` (the pot, bucket, bowl, jug and tin a step gives back) become the `returns` column.

Output `cooking-prep.tsv` (tab-separated, header first), the columns of recipes.tsv plus `returns` (`obj:count,...`).

Usage (needs network access to oldschool.runescape.wiki and `./gradlew -q :tools:cache-dump:run --args="config obj --all"`):
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  python3 -I tools/wiki-import/cooking_prep.py objs.txt content/skills/src/main/resources/tp/content/skills

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import recipes as R  # noqa: E402 - the shared Wiki helpers (wikitexts, recipes_in, params_of, load_objs, ticks_of)

PAGES = [
    "Bread dough", "Pastry dough", "Pitta dough", "Pizza base", "Incomplete pizza", "Uncooked pizza", "Meat pizza", "Anchovy pizza",
    "Pineapple pizza", "Pie shell", "Uncooked berry pie", "Uncooked meat pie", "Uncooked apple pie", "Uncooked cake",
    "Chocolate cake", "Incomplete stew", "Uncooked stew", "Uncooked curry", "Unfermented wine",
]
# The plain dough pages have no skill in the template: level 1 and no xp.
NO_SKILL = {"bread dough", "pastry dough"}
# The two incomplete stews are named "Incomplete stew#Meat" / "#Potato" in the templates (Wiki "Incomplete stew": ids 1999 and 1997).
ALIASES = {"incomplete stew#meat": 1999, "incomplete stew#potato": 1997}
# An emptied container a step gives back when the Wiki template does not list it (Wiki "Pastry dough" lists the same pairs).
EMPTIES = {1933: 1931, 1929: 1925, 1921: 1923, 1937: 1935, 1927: 1925}


def numbered(p, prefix):
    out = []
    for n in range(1, 8):
        if f"{prefix}{n}" in p:
            out.append((p[f"{prefix}{n}"], R.num(p.get(f"{prefix}{n}quantity"), 1)))
    return out


def main():
    objs = R.load_objs(sys.argv[1])
    out_dir = sys.argv[2]

    def oid(name):
        if name.lower() in ALIASES:
            return ALIASES[name.lower()]
        ids = objs.get(name.lower())
        return ids[0] if ids else None

    texts = R.wikitexts(PAGES)
    rows, seen = [], set()
    for title in PAGES:
        text = texts.get(title)
        if text is None:
            print("no page:", title, file=sys.stderr)
            continue
        kept = 0
        for body in R.recipes_in(text):
            p = R.params_of(body)
            skill = p.get("skill1", "").lower()
            if p.get("output1", "").lower().split("#")[0] != title.lower() or p.get("facilities") or p.get("skill2"):
                continue
            if skill != "cooking" and not (skill == "" and title.lower() in NO_SKILL):
                continue
            mats = numbered(p, "mat")
            tools = [t.strip() for t in p.get("tools", "").split(",") if t.strip()]
            returns = [r for r in numbered(p, "output")[1:]]
            ids = [oid(m) for m, _ in mats]
            tool_ids = [oid(t) for t in tools]
            ret_ids = [oid(r) for r, _ in returns]
            out_id = oid(p["output1"])
            for i, _ in zip(ids, mats):
                if title.lower() in NO_SKILL and i in EMPTIES and EMPTIES[i] not in ret_ids:
                    ret_ids.append(EMPTIES[i])
                    returns.append(("", 1))
            if out_id is None or None in ids or None in tool_ids or None in ret_ids:
                print("unmatched in", title, ":", p["output1"], mats, tools, returns, file=sys.stderr)
                continue
            key = (out_id, tuple(ids), tuple(tool_ids))
            if key in seen:
                continue
            seen.add(key)
            ticks, first, single = R.ticks_of(p)
            rows.append([
                out_id, R.num(p.get("output1quantity"), 1), "cooking", R.num(p.get("skill1lvl"), 1) if skill else 1,
                p.get("skill1exp", "0").replace(",", "") if skill else "0", ticks, first, single, p.get("members", "").lower(),
                ",".join(str(t) for t in tool_ids), "", ",".join(f"{i}:{q}" for i, (_, q) in zip(ids, mats)), p["output1"],
                ",".join(f"{i}:{q}" for i, (_, q) in zip(ret_ids, returns)),
            ])
            kept += 1
        if kept == 0:
            print("no recipe kept:", title, file=sys.stderr)
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "cooking-prep.tsv"), "w") as f:
        f.write("output\tcount\tskill\tlevel\txp\tticks\tfirst_ticks\tsingle_ticks\tmembers\ttools\tfacilities\tmaterials\tname\treturns\n")
        for r in rows:
            f.write("\t".join(str(c) for c in r) + "\n")
    print(f"{len(rows)} cooking steps", file=sys.stderr)


if __name__ == "__main__":
    main()
