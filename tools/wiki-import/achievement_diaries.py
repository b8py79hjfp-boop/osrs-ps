#!/usr/bin/env python3
"""
Achievement diary tasks, requirements and rewards from the OSRS Wiki (docs/mechanics/achievement-diaries.md).

Each of the 12 pages "<Area> Diary" has, per tier, a table `data-diary-name="<Area>" data-diary-tier="<Tier>"` whose rows
are `N. <task text>` and a requirements cell (`{{SCP|<Skill>|<level>}}` for skills, `{{SCP|Quest}} Completion of /
Started / Partial completion of [[Quest]]` for quests, plain bullets for items), followed by a Rewards list (the tier
item, the Antique lamp's experience and minimum level, the other perks). The Wiki infobox names the taskmaster.

The number of tasks per tier is checked against the client: client script 4073 (tasks in a tier per area) of cache 2757,
whose area order is cache enum 595 (0 Karamja, 1 Ardougne, 2 Falador, 3 Fremennik, 4 Kandarin, 5 Desert, 6 Lumbridge &
Draynor, 7 Morytania, 8 Varrock, 9 Wilderness, 10 Western Provinces, 11 Kourend & Kebos). Reward item ids are matched
by name from the cache (`cache-dump config obj --all`).

Output, in the given directory (tab-separated, header first):
  diary-areas.tsv    area, name, taskmaster (NPC name as the cache has it), page
  diary-tasks.tsv    area, tier (0 easy .. 3 elite), index (1-based, the wiki number), text,
                     skills (`Skill:level:boostable` joined by `;`; Skill may be Quest = quest points, Combat),
                     quests (`Quest name:complete|started|partial` joined by `;`), other (item and misc requirements)
  diary-rewards.tsv  area, tier, obj (tier item id), item (name), lamp_xp, lamp_min_level, perks (joined by ` | `)

Usage (network access to oldschool.runescape.wiki; the objs come from cache-dump):
  ./gradlew :tools:cache-dump:installDist
  cache-dump config obj --all > objs.txt
  python3 -I tools/wiki-import/achievement_diaries.py objs.txt content/diaries/src/main/resources/tp/content/diaries
Pass a third argument (a directory) to read/write the raw wikitext there instead of fetching every time.

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

# (index, wiki name, tasks per tier from client script 4073)
AREAS = [
    (0, "Karamja", [10, 19, 10, 5]),
    (1, "Ardougne", [10, 12, 12, 8]),
    (2, "Falador", [11, 14, 11, 6]),
    (3, "Fremennik", [10, 9, 9, 6]),
    (4, "Kandarin", [11, 14, 11, 7]),
    (5, "Desert", [11, 12, 10, 6]),
    (6, "Lumbridge & Draynor", [12, 12, 11, 6]),
    (7, "Morytania", [11, 11, 10, 6]),
    (8, "Varrock", [14, 13, 10, 5]),
    (9, "Wilderness", [12, 11, 10, 7]),
    (10, "Western Provinces", [11, 13, 13, 7]),
    (11, "Kourend & Kebos", [12, 13, 10, 8]),
]
TIERS = ["Easy", "Medium", "Hard", "Elite"]


def fetch(page):
    url = API + "?" + urllib.parse.urlencode(
        {"action": "parse", "format": "json", "prop": "wikitext", "page": page, "redirects": 1})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)["parse"]["wikitext"]["*"]
        except Exception:  # noqa: BLE001 - retry transient network errors
            time.sleep(2 ** attempt)
    raise RuntimeError("wiki request failed: " + page)


def page_text(name, raw_dir):
    path = os.path.join(raw_dir, name.replace(" ", "_").replace("&", "and") + ".txt") if raw_dir else None
    if path and os.path.exists(path):
        return open(path).read()
    text = fetch(name + " Diary")
    time.sleep(1.5)
    if path:
        os.makedirs(raw_dir, exist_ok=True)
        open(path, "w").write(text)
    return text


# ---- wikitext cleanup

def strip_refs(s):
    s = re.sub(r"<ref[^>]*/>", "", s)
    s = re.sub(r"<ref[^>]*>.*?</ref>", "", s, flags=re.S)
    s = re.sub(r"\{\{efn\|(?:[^{}]|\{\{[^{}]*\}\})*\}\}", "", s)
    return s


def clean(s):
    """Wikitext to plain text."""
    s = strip_refs(s)
    s = re.sub(r"\{\{Fairycode\|([^}|]*)\}\}", lambda m: m.group(1).upper(), s, flags=re.I)
    s = re.sub(r"\{\{sic\}\}", "", s, flags=re.I)
    s = re.sub(r"\{\{!\}\}", "|", s)
    s = re.sub(r"\{\{(?:Boostable|boostable)\|[^}]*\}\}", "", s)
    s = re.sub(r"\{\{NA\|[^}]*\}\}", "", s, flags=re.I)
    s = re.sub(r"\{\{RuneReq\|([^}]*)\}\}", lambda m: " + ".join(
        p.split("=")[1] + " " + p.split("=")[0].lower() for p in m.group(1).split("|") if "=" in p), s)
    s = re.sub(r"\{\{SCP\|([A-Za-z ]+)\|(\d+)[^}]*\}\}", r"\2 \1", s)
    s = re.sub(r"\{\{SCP\|[^}]*\}\}", "", s)
    s = re.sub(r"\[\[(?:File|Image):[^\]]*\]\]", "", s)
    s = re.sub(r"\[\[[^\]|]*\|([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"\[\[([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"\{\{[^{}]*\}\}", "", s)
    s = s.replace("'''", "").replace("''", "")
    s = re.sub(r"<[^>]+>", "", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


# ---- tasks

def tier_tables(text):
    """(area name, tier index) -> table lines."""
    lines = text.splitlines()
    out = {}
    for i, line in enumerate(lines):
        m = re.search(r'data-diary-name="([^"]*)" data-diary-tier="([^"]*)"', line)
        if not m:
            continue
        j = i
        while lines[j].strip() != "|}":
            j += 1
        out[(m.group(1), TIERS.index(m.group(2)))] = lines[i + 1:j]
    return out


def split_rows(lines):
    rows, cur = [], []
    for line in lines:
        if line.startswith("|-"):
            if cur:
                rows.append(cur)
            cur = []
        elif line.startswith("!"):
            continue
        else:
            cur.append(line)
    if cur:
        rows.append(cur)
    return rows


def parse_requirements(cell):
    skills, quests, other = [], [], []
    for raw in cell:
        line = strip_refs(raw).strip()
        if not line or line == "|":
            continue
        line = line.lstrip("*").strip()
        handled = False
        # skills: {{SCP|Skill|lvl|..}} optionally followed by {{Boostable|no}}
        for m in re.finditer(r"\{\{SCP\|([A-Za-z ]+)\|(\d+)[^}]*\}\}(\s*\{\{[Bb]oostable\|(no|n)\}\})?", line):
            skills.append((m.group(1).strip(), int(m.group(2)), 0 if m.group(3) else 1))
            handled = True
        if re.search(r"\{\{SCP\|Quest\}\}", line):
            handled = True
            mode = "complete"
            low = line.lower()
            if "partial" in low:
                mode = "partial"
            elif "started" in low:
                mode = "started"
            for q in re.findall(r"\[\[([^\]|#]+)(?:#[^\]|]*)?(?:\|[^\]]*)?\]\]", line):
                if (q.strip(), mode) not in quests:
                    quests.append((q.strip(), mode))
        if re.search(r"\{\{SCP\|combatachievement\}\}", line):
            handled = False
        rest = re.sub(r"\{\{SCP\|[^}]*\}\}(\s*\{\{[Bb]oostable\|[^}]*\}\})?", "", line)
        if handled:
            # leftover text of a skill/quest line is only connective ("and", "Completion of ..."): keep nothing
            continue
        text = clean(rest)
        if text and text.lower() not in ("none",):
            other.append(text)
    return skills, quests, other


def parse_task(row):
    first = row[0]
    assert first.startswith("|"), row
    head = first[1:]
    m = re.match(r"\s*(\d+)\.\s*(.*)", head)
    assert m, head
    number = int(m.group(1))
    text = clean(m.group(2))
    cell2 = []
    in_cell2 = False
    for line in row[1:]:
        if not in_cell2:
            if line.startswith("|"):
                in_cell2 = True
                rest = line[1:]
                if rest.strip():
                    cell2.append(rest)
            continue
        cell2.append(line)
    return number, text, parse_requirements(cell2)


# ---- rewards

LAMP = re.compile(r"worth ([\d,]+)\s*(?:\[\[)?experience(?:\]\])? in any skill (?:level )?(?:of any level|of at least level (\d+))")


def parse_rewards(text, area):
    """Four (item name, lamp xp, lamp min level, perks) tuples in tier order."""
    # the reward block of tier t follows its table: from the "Rewards" heading to the next tier heading
    chunks = re.split(r"\n==(?:Easy|Medium|Hard|Elite)==", "\n" + text)
    result = []
    for t, chunk in enumerate(chunks[1:5]):
        body = re.split(r"\n==[A-Za-z]", chunk)[0]
        m = re.search(r"\n\*\s*\[\[([^\]|]+)(?:\|[^\]]*)?\]\]:?\s*\n(.*)", body, re.S)
        # the first top-level bullet that is the tier item (ends with ':')
        item = None
        perks = []
        lamp_xp, lamp_min = 0, 0
        start = body.find("Rewards")
        for line in body[start:].splitlines()[1:]:
            line = strip_refs(line).rstrip()
            if not line.strip():
                continue
            if line.startswith("{|") or line.startswith("|") or line.startswith("=="):
                continue
            lm = LAMP.search(line)
            im = re.match(r"\*\s*\[\[([^\]|]+)(?:\|[^\]]*)?\]\]:\s*$", line)
            if im and item is None:
                item = im.group(1).strip()
            elif lm and line.startswith("*") and not line.startswith("**"):
                lamp_xp = int(lm.group(1).replace(",", ""))
                lamp_min = int(lm.group(2) or 1)
            elif line.startswith("*"):
                perks.append(clean(line.lstrip("*")))
        assert item and lamp_xp, (area, t, body[:300])
        result.append((item, lamp_xp, lamp_min, perks))
    return result


def taskmaster(text):
    m = re.search(r"\|taskmasters\s*=\s*\[\[([^\]|]+)(?:\|[^\]]*)?\]\]", text)
    return clean(m.group(1)) if m else ""


def load_objs(path):
    ids = {}
    for line in open(path):
        m = re.match(r'(\d+) name="(.*?)" cost=.*? members=\w+ tradeable=\w+ certTemplate=(-?\d+) placeholderTemplate=(-?\d+)', line)
        if m and m.group(3) == "-1" and m.group(4) == "-1":
            ids.setdefault(m.group(2).lower(), int(m.group(1)))
    return ids


def main():
    objs = load_objs(sys.argv[1])
    out_dir = sys.argv[2]
    raw_dir = sys.argv[3] if len(sys.argv) > 3 else None
    os.makedirs(out_dir, exist_ok=True)
    areas, tasks, rewards = [], [], []
    for index, name, counts in AREAS:
        text = page_text(name, raw_dir)
        tables = tier_tables(text)
        areas.append((index, name, taskmaster(text), name + " Diary"))
        for tier in range(4):
            rows = [parse_task(r) for r in split_rows(tables[(name, tier)]) if r and r[0].startswith("|")]
            numbers = [r[0] for r in rows]
            assert numbers == list(range(1, counts[tier] + 1)), (name, tier, numbers, counts[tier])
            for number, ttext, (skills, quests, other) in rows:
                tasks.append((index, tier, number, ttext,
                              ";".join("%s:%d:%d" % s for s in skills),
                              ";".join("%s:%s" % q for q in quests),
                              " ; ".join(other)))
        for tier, (item, xp, minlvl, perks) in enumerate(parse_rewards(text, name)):
            obj = objs.get(item.lower())
            assert obj is not None, (name, item)
            rewards.append((index, tier, obj, item, xp, minlvl, " | ".join(perks)))
    with open(os.path.join(out_dir, "diary-areas.tsv"), "w") as f:
        f.write("area\tname\ttaskmaster\tpage\n")
        for r in areas:
            f.write("\t".join(map(str, r)) + "\n")
    with open(os.path.join(out_dir, "diary-tasks.tsv"), "w") as f:
        f.write("area\ttier\tindex\ttext\tskills\tquests\tother\n")
        for r in tasks:
            f.write("\t".join(map(str, r)) + "\n")
    with open(os.path.join(out_dir, "diary-rewards.tsv"), "w") as f:
        f.write("area\ttier\tobj\titem\tlamp_xp\tlamp_min_level\tperks\n")
        for r in rewards:
            f.write("\t".join(map(str, r)) + "\n")
    print("%d areas, %d tasks, %d rewards" % (len(areas), len(tasks), len(rewards)))


if __name__ == "__main__":
    main()
