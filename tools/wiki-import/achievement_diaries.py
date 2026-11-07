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
  diary-triggers.tsv area, tier, index, label (a word of the task text), parts: the plugin-api event that completes the task
                     (the table is achievement_diary_triggers.py; DiaryTriggerData.kt describes the format)
  diary-quests.tsv   quest (Wiki name), kind (varbit / varp), var, unstarted, end: the state variable of each quest whose content
                     exists (QUESTS_WITH_CONTENT), read from content/quests quest-states.tsv, for the quest requirements
  diary-dialogue.tsv the taskmasters' Wiki transcripts (Transcript:<NPC>) in the format of npc-dialogue.tsv, `tree <area> <kind>`
                     with kind `standard` or a tier name (the "<Tier> tasks completed" claim)

Usage (network access to oldschool.runescape.wiki; the objs come from cache-dump):
  ./gradlew :tools:cache-dump:installDist
  cache-dump config obj --all > objs.txt
  python3 -I tools/wiki-import/achievement_diaries.py objs.txt content/diaries/src/main/resources/tp/content/diaries
Pass a third argument (a directory) to read/write the raw wikitext there instead of fetching every time. `--offline` keeps
diary-areas / -tasks / -rewards as they are (the tasks are read for the trigger labels) and writes the other three files.

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import achievement_diary_triggers as triggers  # noqa: E402
import npc_dialogue as dialogue  # noqa: E402

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
        m = re.match(r'(\d+) name="(.*?)" cost=.*? members=\w+(?: tradeable=\w+ certTemplate=(-?\d+) placeholderTemplate=(-?\d+))?', line)
        # older cache-dump builds print no note / placeholder templates: every row is then taken as a plain item
        if m and (m.group(3) or "-1") == "-1" and (m.group(4) or "-1") == "-1":
            ids.setdefault(m.group(2).lower(), int(m.group(1)))
    return ids


# ---- triggers, quest variables, taskmaster dialogue

# Quests whose content is in content/quests (the quest's state variable can change); the others cannot be started, so a
# requirement for them is not checked (OPEN-AD2).
QUESTS_WITH_CONTENT = [
    "Below Ice Mountain", "Black Knights' Fortress", "Cook's Assistant", "The Corsair Curse", "Demon Slayer", "Doric's Quest",
    "Dragon Slayer I", "Ernest the Chicken", "Goblin Diplomacy", "Imp Catcher", "The Knight's Sword", "Misthalin Mystery",
    "Pirate's Treasure", "Prince Ali Rescue", "The Restless Ghost", "Romeo & Juliet", "Rune Mysteries", "Sheep Shearer",
    "Shield of Arrav", "Vampyre Slayer", "Witch's Potion", "X Marks the Spot",
]

# The Wiki page names of the taskmasters' transcripts, in area order.
TASKMASTER_PAGES = ["Pirate Jackie the Fruit", "Two-pints", "Sir Rebral", "Thorodin", "The 'Wedge'", "Jarr", "Mikasi", "Le-sabr\u00e8",
                    "Toby", "Lesser Fanatic", "Elder Gnome child", "Elise"]
REPLACE_OPTION = re.compile(r"Can I have (another|some more)")
CLAIM_MARK = "@@CLAIM@@"
REPLACE_MARK = "@@REPLACE@@"


def write_quests(out_dir, states_path):
    rows = {}
    for line in open(states_path).read().splitlines()[1:]:
        c = line.split("\t")
        rows[c[2]] = (c[3], c[4], c[5], c[6])
    with open(os.path.join(out_dir, "diary-quests.tsv"), "w") as f:
        f.write("quest\tkind\tvar\tunstarted\tend\n")
        for name in QUESTS_WITH_CONTENT:
            kind, var, unstarted, end = rows[name]
            f.write("\t".join([name, kind, var, unstarted, end]) + "\n")


def write_triggers(out_dir, tasks, objs):
    """`tasks`: (area, tier, index, text, ...) rows; objs: lower case obj name -> id (names are checked against it)."""
    text = {(t[0], t[1], t[2]): t[3] for t in tasks}
    seen = set()
    with open(os.path.join(out_dir, "diary-triggers.tsv"), "w") as f:
        f.write("area\ttier\tindex\tlabel\tparts\n")
        for area, tier, index, label, *parts in triggers.TRIGGERS:
            key = (area, tier, index)
            assert key in text, key
            assert key not in seen, key
            seen.add(key)
            assert label.lower() in text[key].lower(), (key, label, text[key])
            for p in parts:
                for m in re.finditer(r"names=([^;]*)", p):
                    if m.group(1) != "*" and p.startswith("item"):
                        for n in m.group(1).split("|"):
                            assert n.lower() in objs, ("unknown obj", n)
            f.write("%d\t%d\t%d\t%s\t%s\n" % (area, tier, index, label, " && ".join(parts)))
    print("%d triggers" % len(seen))


def tree_rows(lines):
    """Node rows of a transcript section's dialogue lines (the parser of npc_dialogue.py), or None."""
    lines = [re.sub(r"\{\{sic\|[^}]*\}\}", "", l) for l in lines]
    items = [c for c in (dialogue.classify(l) for l in lines if l.startswith("*")) if c]
    if not items:
        return None
    base = min(i[0] for i in items)
    stmts, _ = dialogue.Parser(items).seq(0, base)
    rows = dialogue.serialize(stmts)
    # the box after "Can I have another ..." is where the tier item is given again
    for i in range(len(rows) - 1):
        c = rows[i].split("\t")
        if c[0] == "P" and len(c) > 2 and REPLACE_OPTION.match(c[2]) and rows[i + 1].startswith("M\t"):
            m = rows[i + 1].split("\t")
            rows[i + 1] = "\t".join([m[0], m[1], REPLACE_MARK + m[2]])
    return rows


def _depth(line):
    return len(line) - len(line.lstrip("*"))


def _flatten(lines):
    """The default path of a claim section: the branch for a player whose earlier tiers are done, and no inventory-space
    branch (the server checks the room); other conditions are left for the dialogue parser."""
    out, i = [], 0
    while i < len(lines):
        line = lines[i]
        m = re.match(r"^(\*+)\s*\{\{tcond\|([^}]*)\}\}", line)
        if not m:
            out.append(line)
            i += 1
            continue
        depth = len(m.group(1))
        j = i + 1
        while j < len(lines) and lines[j].startswith("*") and _depth(lines[j]) > depth:
            j += 1
        body = lines[i + 1:j]
        cond = m.group(2).lower()
        if "inventory" in cond or "previous tasks" in cond or "previous tiers" in cond:
            if "n't" in cond or " not " in cond or "full" in cond:
                pass
            else:
                out.extend(_flatten([l[1:] for l in body]))
        else:
            out.append(line)
            out.extend(body)
        i = j
    return out


def claim_lines(lines):
    """A "<Tier> tasks completed" section made playable: the hand-over (the Wiki's `receives` action, else the line after the
    player's "Yes please") becomes the CLAIM_MARK box; the jumps back to the start are dropped."""
    flat = _flatten(lines)
    has_receives = any("tact|receives=" in l for l in flat)
    out, marked = [], False
    if not has_receives and not any(re.match(r"^\*\s*\'\'\'Player:\'\'\'\s*Yes", l) for l in flat):
        # elite sections have no "Yes please": the hand-over comes with the first line of the taskmaster's reply
        for k, line in enumerate(flat):
            if re.match(r"^\*\s*\'\'\'(?!Player:)(.+?):\'\'\'", line):
                flat = flat[:k + 1] + ["* {{tbox|%s}}" % CLAIM_MARK] + flat[k + 1:]
                marked = True
                break
    for line in flat:
        if re.match(r"^\*\s*\{\{tact\|(initial|above|below|previous\d*|continues?)\}\}", line):
            continue
        if re.search(r"\{\{tact\|receives=", line):
            out.append("* {{tbox|%s}}" % CLAIM_MARK)
            marked = True
            continue
        out.append(line)
        if not has_receives and not marked and re.match(r"^\*\s*\'\'\'Player:\'\'\'\s*Yes", line):
            out.append("* {{tbox|%s}}" % CLAIM_MARK)
            marked = True
    return out


def standard_lines(lines):
    """The standard conversation with the branch for a player who has room, and the Wiki's "gives you" actions as message boxes."""
    return [re.sub(r"\{\{tact\|([^}|]* gives you [^}]*)\}\}", r"{{tbox|\1}}", l) for l in _flatten(lines)]


def write_dialogue(out_dir, raw_dir):
    out = []
    for area, page in enumerate(TASKMASTER_PAGES):
        path = os.path.join(raw_dir, "Transcript_" + page.replace(" ", "_").replace("'", "") + ".txt") if raw_dir else None
        if path and os.path.exists(path):
            text = open(path).read()
        else:
            text = fetch("Transcript:" + page)
            time.sleep(1.5)
            if path:
                os.makedirs(raw_dir, exist_ok=True)
                open(path, "w").write(text)
        standard = dialogue.select_lines(text)
        rows = tree_rows(standard_lines(standard)) if standard else None
        if rows:
            out.append(("tree\t%d\tstandard" % area, rows))
        for tier, name in enumerate(["Easy", "Medium", "Hard", "Elite"]):
            for _level, heading, lines in dialogue.sections(text):
                if re.match(name + r" tasks completed", heading, re.I):
                    rows = tree_rows(claim_lines(lines))
                    if rows and any(r.endswith(CLAIM_MARK) for r in rows):
                        out.append(("tree\t%d\t%s" % (area, name.lower()), rows))
    with open(os.path.join(out_dir, "diary-dialogue.tsv"), "w") as f:
        f.write("# tree <area> <standard|tier>, then nodes as in npc-dialogue.tsv. Wiki Transcript: pages, CC BY-NC-SA 3.0; "
                "tools/wiki-import/achievement_diaries.py\n")
        for head, rows in out:
            f.write(head + "\n" + "\n".join(rows) + "\n")
    print("%d dialogue trees" % len(out))


def main():
    args = [a for a in sys.argv[1:] if a != "--offline"]
    offline = "--offline" in sys.argv[1:]
    objs = load_objs(args[0])
    out_dir = args[1]
    raw_dir = args[2] if len(args) > 2 else None
    os.makedirs(out_dir, exist_ok=True)
    if offline:
        tasks = []
        for line in open(os.path.join(out_dir, "diary-tasks.tsv")).read().splitlines()[1:]:
            c = line.split("\t")
            tasks.append((int(c[0]), int(c[1]), int(c[2]), c[3]))
        repo = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        write_quests(out_dir, os.path.join(repo, "content/quests/src/main/resources/tp/content/quests/quest-states.tsv"))
        write_triggers(out_dir, tasks, objs)
        write_dialogue(out_dir, raw_dir)
        return
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
    repo = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    write_quests(out_dir, os.path.join(repo, "content/quests/src/main/resources/tp/content/quests/quest-states.tsv"))
    write_triggers(out_dir, tasks, objs)
    write_dialogue(out_dir, raw_dir)


if __name__ == "__main__":
    main()
