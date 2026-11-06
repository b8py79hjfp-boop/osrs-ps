#!/usr/bin/env python3
"""
Construction data from the OSRS Wiki (docs/mechanics/construction.md): the furniture table and the room table.

Furniture: every page that embeds {{Infobox Construction}} (API list=embeddedin) with a plain `level` and `experience`.
From the infobox: name, level, experience (xp per build), room, hotspot, id (the built loc ids), itemid (the flat-pack
item). From the first {{Recipe}} on the page whose skill is Construction: ticks, tools, materials (matN / matNquantity).
Names are matched to cache objs the way recipes.py does; a furniture with a name that matches no obj is reported and
dropped. Pages with a versioned infobox (level1, level2, ...) are reported and dropped.

Rooms: the "Rooms" tables of the Player-owned house page (level, room, cost).

Output (tab-separated, header first, in the output directory):
  construction-furniture.tsv   name level xp room hotspot locs flatpack ticks tools materials
  construction-rooms.tsv       name level cost

Usage (needs network access to oldschool.runescape.wiki and `./gradlew -q :tools:cache-dump:run --args="config obj --all"`):
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  python3 -I tools/wiki-import/construction.py objs.txt content/skills/src/main/resources/tp/content/skills

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
# The Wiki names the 1,000-coin first-house deposit and non-room tables in the same page; only rows with a room link are kept.
ROOM_ROW = re.compile(r"\|\s*(\d+)\s*\n\|\s*\{\{plinkt\|([^|}]+)[^\n]*\n\|\s*\{\{Coins\|([\d,]+)\}\}")


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


def embedded_titles(template):
    titles, cont = [], {}
    while True:
        d = api({"action": "query", "list": "embeddedin", "eititle": template, "eilimit": 500, "einamespace": 0, **cont})
        titles += [e["title"] for e in d["query"]["embeddedin"]]
        if "continue" not in d:
            return titles
        cont = d["continue"]


def wikitexts(titles):
    out = {}
    for i in range(0, len(titles), 50):
        batch = titles[i:i + 50]
        d = api({"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main", "titles": "|".join(batch)})
        for page in d["query"]["pages"].values():
            revs = page.get("revisions")
            if revs:
                out[page["title"]] = revs[0]["slots"]["main"]["*"]
        time.sleep(0.2)
    return out


def template_end(text, i):
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


def templates(text, name):
    """Bodies of every {{name ...}} (brace-balanced)."""
    out, start, opener = [], 0, "{{" + name
    while True:
        i = text.find(opener, start)
        if i < 0:
            return out
        after = text[i + len(opener):i + len(opener) + 1]
        end = template_end(text, i)
        if after in ("|", "\n", " ", "}"):
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
        if not m or int(m.group(5)) != -1 or int(m.group(6)) != -1:
            continue
        by_name.setdefault(m.group(2).lower(), []).append((m.group(4) != "true", int(m.group(1))))
    return {k: [o for _, o in sorted(v)] for k, v in by_name.items()}


def furniture(objs, titles):
    texts = wikitexts(titles)
    rows = []
    for title in sorted(texts):
        text = texts[title]
        info = next(iter(templates(text, "Infobox Construction")), None)
        if info is None:
            continue
        p = params_of(info)
        level, xp = num(p.get("level")), p.get("experience", "").replace(",", "")
        if level is None or not re.fullmatch(r"\d+(\.\d+)?", xp):
            print("versioned or incomplete infobox:", title, file=sys.stderr)
            continue
        recipe = next((params_of(b) for b in templates(text, "Recipe") if params_of(b).get("skill1", "").lower() == "construction"), None)
        if recipe is None:
            print("no Construction recipe:", title, file=sys.stderr)
            continue
        mats, n = [], 1
        while f"mat{n}" in recipe:
            ids = objs.get(recipe[f"mat{n}"].lower())
            mats.append((ids[0] if ids else None, num(recipe.get(f"mat{n}quantity"), 1), recipe[f"mat{n}"]))
            n += 1
        tools = [t.strip() for t in recipe.get("tools", "").split(",") if t.strip()]
        tool_ids = [(objs.get(t.lower()) or [None])[0] for t in tools]
        if any(m[0] is None for m in mats) or None in tool_ids:
            print("unmatched materials in", title, ":", [m[2] for m in mats if m[0] is None], tools, file=sys.stderr)
            continue
        locs = [str(int(x)) for x in re.findall(r"\d+", p.get("id", ""))]
        flat = (objs.get(p.get("flatpack name", p["name"] + " (flatpack)").lower()) or [-1])[0] if p.get("flatpack", "").lower() == "yes" else -1
        rows.append([
            p.get("name", title), level, xp, p.get("room", ""), p.get("hotspot", ""), ",".join(locs), flat,
            num(recipe.get("ticks"), 5), ",".join(str(t) for t in tool_ids), ",".join(f"{i}:{q}" for i, q, _ in mats),
        ])
    return rows


def rooms():
    text = wikitexts(["Player-owned house"])["Player-owned house"]
    start, end = text.index("==Rooms=="), text.index("==Building a house==")
    return [[m.group(2).strip(), int(m.group(1)), int(m.group(3).replace(",", ""))] for m in ROOM_ROW.finditer(text[start:end])]


def main():
    objs_path, out_dir = sys.argv[1], sys.argv[2]
    objs = load_objs(objs_path)
    os.makedirs(out_dir, exist_ok=True)
    rows = furniture(objs, embedded_titles("Template:Infobox Construction"))
    with open(os.path.join(out_dir, "construction-furniture.tsv"), "w") as f:
        f.write("name\tlevel\txp\troom\thotspot\tlocs\tflatpack\tticks\ttools\tmaterials\n")
        for r in rows:
            f.write("\t".join(str(c) for c in r) + "\n")
    room_rows = rooms()
    with open(os.path.join(out_dir, "construction-rooms.tsv"), "w") as f:
        f.write("name\tlevel\tcost\n")
        for r in room_rows:
            f.write("\t".join(str(c) for c in r) + "\n")
    print(f"{len(rows)} furniture, {len(room_rows)} rooms", file=sys.stderr)


if __name__ == "__main__":
    main()
