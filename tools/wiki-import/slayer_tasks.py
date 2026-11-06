#!/usr/bin/env python3
"""
Slayer data from the OSRS Wiki (docs/mechanics/slayer.md).

Three tables are written next to each other, all tab-separated with a header row:

  slayer-tasks.tsv     one row per task a Slayer master assigns, from the "Tasks" table of the master's page
                       (or its transcluded `<Master>/Slayer assignments` page):
                         master, group (key shared with slayer-monsters.tsv), name (as the Wiki prints it), min, max
                         (amount range), emin, emax (extended amount range with the "Extend" unlock, - if none), weight,
                         slayer, combat (levels; 0 = none), skills (other skill levels, `Skill:level;...`), other (any
                         requirement that is not a level: a quest or unlock, free text, - if none).
                       Krystilia's table has no requirement column: her `slayer` is the lowest level above 1 among
                       the group's monsters for the six groups her page names ("only bloodveld, jellies, dust devils,
                       nechryael, abyssal demons and spiritual creatures require a Slayer level"), else 0.
  slayer-monsters.tsv  one row per cache npc id of a Slayer monster, from `slaylvl`, `slayxp` and `cat` of its
                       {{Infobox Monster}}: id, group, slayer (level needed to harm it), xp (Slayer xp per kill), name.
  slayer-masters.tsv   the masters that are placed in the world: name, npc (cache id with the "Assignment" op), x, z, level
                       (the position of the Wiki infobox map).

Usage (needs network access to oldschool.runescape.wiki):
  python3 -I tools/wiki-import/slayer_tasks.py content/skills/src/main/resources/tp/content/skills

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
TEMPLATE = "Template:Infobox Monster"

# Master -> (Wiki page holding the task table, cache npc id with the "Assignment" op). Konar and Spria etc. have their table
# on their own page; Turael, Mazchna, Nieve and Duradel transclude `<Master>/Slayer assignments`.
MASTERS = {
    "Turael": ("Turael/Slayer assignments", "Turael", 13618),
    "Spria": ("Spria", "Spria", 10432),
    "Mazchna": ("Mazchna/Slayer assignments", "Mazchna", 13620),
    "Vannaka": ("Vannaka", "Vannaka", 403),
    "Chaeldar": ("Chaeldar", "Chaeldar", 404),
    "Konar quo Maten": ("Konar quo Maten", "Konar quo Maten", 8623),
    "Nieve": ("Nieve/Slayer assignments", "Nieve", 6797),
    "Duradel": ("Duradel/Slayer assignments", "Duradel", 13622),
    "Krystilia": ("Krystilia", "Krystilia", 7663),
}

# Row names whose `cat` differs from the table's wording (matched after normalising; see group_key).
ALIASES = {
    "kalphite": "kalphite",
    "bloodvelds": "bloodveld",
    "greaterdemons": "greaterdemon",
}


# Krystilia's Wiki page: "only bloodvelds, jellies, dust devils, nechryael, abyssal demons, and spiritual creatures require a
# Slayer level" (the other groups need none, whatever the hardest monster of the group needs).
KRYSTILIA_SLAYER_GROUPS = {"bloodveld", "jelly", "jellie", "dustdevil", "nechryael", "abyssaldemon", "spiritualcreature"}


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


def wikitext(page):
    return api({"action": "parse", "page": page, "prop": "wikitext", "redirects": 1})["parse"]["wikitext"]["*"]


def pages_using_template():
    titles, cont = [], {}
    while True:
        d = api({"action": "query", "list": "embeddedin", "eititle": TEMPLATE, "einamespace": 0, "eilimit": 500, **cont})
        titles += [p["title"] for p in d["query"]["embeddedin"]]
        if "continue" not in d:
            return titles
        cont = d["continue"]


def wikitexts(titles):
    out = {}
    for i in range(0, len(titles), 50):
        d = api({"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
                 "titles": "|".join(titles[i:i + 50])})
        for page in d["query"]["pages"].values():
            revs = page.get("revisions")
            if revs:
                out[page["title"]] = revs[0]["slots"]["main"]["*"]
        time.sleep(0.2)
    return out


def infoboxes(text, name="Infobox Monster"):
    """Bodies of every {{<name> ...}} (brace-balanced)."""
    out, start = [], 0
    while True:
        i = text.find("{{" + name, start)
        if i < 0:
            return out
        depth, j = 0, i
        while j < len(text):
            if text.startswith("{{", j):
                depth += 1
                j += 2
            elif text.startswith("}}", j):
                depth -= 1
                j += 2
                if depth == 0:
                    break
            else:
                j += 1
        out.append(text[i + len("{{" + name):j - 2])
        start = j


def params(body):
    """Top-level |key = value pairs (ignores | inside {{ }} and [[ ]])."""
    parts, depth, cur = [], 0, []
    i = 0
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
    out = {}
    for p in parts:
        if "=" in p:
            k, v = p.split("=", 1)
            out[k.strip().lower()] = v.strip()
    return out


def clean(v):
    v = re.sub(r"<ref[^>]*/>", "", v)
    v = re.sub(r"<ref[^>]*>.*?</ref>", "", v, flags=re.S)
    v = re.sub(r"<!--.*?-->", "", v, flags=re.S)
    v = re.sub(r"\{\{Cite[^{}]*\}\}", "", v)
    v = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", v)
    v = re.sub(r"\{\{[^{}]*\}\}", "", v)
    v = re.sub(r"<[^>]+>", " ", v)
    return re.sub(r"\s+", " ", v).strip()


def group_key(name):
    """Normalised group name: lower case letters and digits, trailing plural s dropped ("Aberrant Spectres" = "aberrant spectre")."""
    k = re.sub(r"[^a-z0-9]", "", name.lower())
    k = ALIASES.get(k, k)
    return k[:-1] if k.endswith("s") else k


def expand(p):
    """One (ids, getter) per version of a parsed infobox."""
    versions = sorted({int(m.group(1)) for k in p for m in [re.fullmatch(r"version(\d+)", k)] if m})
    if not versions:
        versions = sorted({int(m.group(1)) for k in p for m in [re.fullmatch(r"id(\d+)", k)] if m})
    out = []
    if not versions:
        out.append((p.get("id", ""), lambda k: p.get(k)))
    for n in versions:
        out.append((p.get(f"id{n}", p.get("id", "")), (lambda n: lambda k: p.get(f"{k}{n}", p.get(k)))(n)))
    return out


def int_or(v, default=0):
    m = re.search(r"-?\d+", (v or "").replace(",", ""))
    return int(m.group()) if m else default


def float_or(v, default=0.0):
    m = re.search(r"-?\d+(\.\d+)?", (v or "").replace(",", ""))
    return float(m.group()) if m else default


def monsters(texts):
    """{npc id: (group key, slayer level, xp, name)} for every Infobox Monster version with a Slayer category."""
    data = {}
    for title in sorted(texts):
        for body in infoboxes(texts[title]):
            for ids, get in expand(params(body)):
                cats = clean(get("cat") or "")
                xp = get("slayxp")
                if not cats or xp is None:
                    continue
                for i in re.findall(r"\d+", clean(ids or "")):
                    if i in data:
                        continue
                    for cat in re.split(r"\s*[,/]\s*", cats):
                        data.setdefault(i, [])
                        data[i].append((group_key(cat), int_or(get("slaylvl"), 1), float_or(xp), clean(get("name") or title)))
    return data


def cells(row):
    """Cell texts of one table row (one cell per `|` line; an `attr|value` prefix is dropped)."""
    out = []
    for line in row.split("\n"):
        if line.startswith("|") and not line.startswith(("|-", "|}", "|+")):
            c = line[1:]
            if re.match(r'^[\w-]+="[^"]*"\s*\|(?!\|)', c):
                c = c.split("|", 1)[1]
            out.append(c.strip())
    return out


def requirements(cell):
    """(slayer, combat, other skills 'Skill:n;..', other requirement text) of an 'Unlock requirement' cell."""
    skills = {}
    for skill, level in re.findall(r"\{\{SCP\|([A-Za-z]+)\|(\d+)", cell):
        skills[skill] = max(skills.get(skill, 0), int(level))
    other = clean(re.sub(r"\{\{SCP\|[^{}]*\}\}", "", cell))
    other = re.sub(r"^(and|,|;|\s)+|(and|,|;|\s)+$", "", other).strip(" ,.;")
    if other.lower() in ("", "none", "n/a", "and"):
        other = "-"
    slayer = skills.pop("Slayer", 0)
    combat = skills.pop("Combat", 0)
    return slayer, combat, ";".join(f"{k}:{v}" for k, v in sorted(skills.items())) or "-", other


def parse_table(text, master, mon_by_group):
    """Task rows of the master's table: the first wikitable whose header has Monster and Weight (or 'Task weight')."""
    out = []
    for table in re.findall(r"\{\|.*?\n\|\}", text, re.S):
        header = [clean(h[1:]).lower() for h in re.findall(r"^![^\n]*", table, re.M)]
        if not any("monster" in h for h in header) or not any("weight" in h for h in header):
            continue
        cols = [h for h in header if "total" not in h and "{{#var" not in h]
        def col(*names, exclude=()):
            for i, h in enumerate(cols):
                if any(n in h for n in names) and not any(x in h for x in exclude):
                    return i
            return -1
        c_mon, c_amt, c_ext = col("monster"), col("amount", exclude=("extended",)), col("extended")
        c_req, c_wt = col("unlock"), col("weight")
        for row in table.split("\n|-")[1:]:
            c = cells(row)
            if len(c) <= max(c_mon, c_amt, c_wt):
                continue
            wm = re.search(r"\+=\|weight\|(\d+)", c[c_wt])
            if not wm:
                continue
            display = clean(c[c_mon])
            amt = re.search(r"(\d+)\s*-\s*(\d+)", c[c_amt])
            ext = re.search(r"(\d+)\s*-\s*(\d+)", c[c_ext]) if c_ext >= 0 and c_ext < len(c) else None
            if c_req >= 0 and c_req < len(c):
                slayer, combat, skills, other = requirements(c[c_req])
            else:
                key = group_key(display)
                levels = [m[1] for m in mon_by_group.get(key, []) if m[1] > 1]
                slayer = min(levels) if levels and key in KRYSTILIA_SLAYER_GROUPS else 0
                combat, skills, other = 0, "-", "-"
            out.append([master, group_key(display), display, amt.group(1) if amt else "0", amt.group(2) if amt else "0",
                        ext.group(1) if ext else "-", ext.group(2) if ext else "-", wm.group(1), slayer, combat, skills, other])
        break
    return out


def master_position(master, text):
    for body in infoboxes(text, "Infobox NPC"):
        for _, get in expand(params(body)):
            map_text = get("map") or ""
            m = re.search(r"\{\{Map\|[^}]*?x=(\d+)\|y=(\d+)", map_text)
            if m:
                plane = re.search(r"plane=(\d+)", map_text)
                return int(m.group(1)), int(m.group(2)), int(plane.group(1)) if plane else 0
    return None


def main():
    dest = sys.argv[1]
    titles = pages_using_template()
    print(f"{len(titles)} pages use {TEMPLATE}", file=sys.stderr)
    mons = monsters(wikitexts(titles))
    by_group = {}
    for npc, rows in mons.items():
        for g, lvl, xp, name in rows:
            by_group.setdefault(g, []).append((npc, lvl, xp, name))
    tasks, positions, unmatched = [], [], []
    for master, (page, home, npc) in MASTERS.items():
        text = wikitext(page)
        rows = parse_table(text, master, by_group)
        if not rows:
            print("no task table for " + master, file=sys.stderr)
        unmatched += [(master, r[2]) for r in rows if r[1] not in by_group]
        tasks += [r for r in rows if r[1] in by_group]  # "Boss" rows list several monsters and have no group
        pos = master_position(master, wikitext(home))
        if pos:
            positions.append([master, npc, *pos])
        time.sleep(0.2)
    used = {r[1] for r in tasks}
    with open(os.path.join(dest, "slayer-tasks.tsv"), "w") as f:
        f.write("master\tgroup\tname\tmin\tmax\temin\temax\tweight\tslayer\tcombat\tskills\tother\n")
        for r in tasks:
            f.write("\t".join(str(c) for c in r) + "\n")
    n = 0
    with open(os.path.join(dest, "slayer-monsters.tsv"), "w") as f:
        f.write("id\tgroup\tslayer\txp\tname\n")
        for npc in sorted(mons, key=int):
            for g, lvl, xp, name in mons[npc]:
                if g in used:
                    f.write(f"{npc}\t{g}\t{lvl}\t{xp:g}\t{name}\n")
                    n += 1
    with open(os.path.join(dest, "slayer-masters.tsv"), "w") as f:
        f.write("name\tnpc\tx\tz\tlevel\n")
        for r in positions:
            f.write("\t".join(str(c) for c in r) + "\n")
    print(f"{len(tasks)} task rows, {n} monster rows, {len(positions)} masters; unmatched groups: {unmatched}", file=sys.stderr)


if __name__ == "__main__":
    main()
