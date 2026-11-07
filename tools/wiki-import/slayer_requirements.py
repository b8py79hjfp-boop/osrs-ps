#!/usr/bin/env python3
"""
Slayer task requirements (docs/mechanics/slayer.md SL3, SL10): turns the free-text `other` column of slayer-tasks.tsv (written
by slayer_tasks.py from the OSRS Wiki master pages) into requirement expressions the server can check.

Writes next to slayer-tasks.tsv, both tab-separated with a header row:

  slayer-requirements.tsv  key, expr. `key` is a text of the `other` column (lower case) or `Master|group` for a requirement the
                           master tables do not print (Krystilia's four "I Wildy More Slayer" tasks, Wiki "Slayer Rewards").
                           `expr` is `&`-joined clauses, each clause `|`-joined atoms:
                             quest:<Quest>:complete | quest:<Quest>:started   (the quest's state variable, slayer-quests.tsv)
                             unlock:<Unlock name>                             (cache db table 117 name, case-insensitive)
                             skill:<Skill>:<level>                            (base level)
  slayer-quests.tsv        quest, kind (varbit / varp), var, unstarted, end: the state variable of every quest named in an expr, copied
                           from content/quests quest-states.tsv (modules cannot depend on each other; same approach as
                           content/diaries QuestGate).

"partial completion of X" and "started X" are `started`: past the unstarted state (the Wiki gives no stage; OPEN-SL8).

Usage:
  python3 -I tools/wiki-import/slayer_requirements.py content/skills/src/main/resources/tp/content/skills \
      content/quests/src/main/resources/tp/content/quests/quest-states.tsv

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import csv
import os
import re
import sys


def q(name, mode="complete"):
    return f"quest:{name}:{mode}"


PIP = q("Priest in Peril")
DS1_STARTED = q("Dragon Slayer I", "started")
STR_OR_AGI = "skill:Strength:60|skill:Agility:60"

# Lower-cased `other` text -> expression. Every distinct text of slayer-tasks.tsv must be listed (the script stops otherwise).
TEXTS = {
    "completion of priest in peril": PIP,
    "completion of a porcine of interest": q("A Porcine of Interest"),
    "completion of ernest the chicken": q("Ernest the Chicken"),
    "completion of skippy and the mogres": q("Skippy and the Mogres"),
    "completion of priest in peril or fairytale ii - cure a queen": PIP + "|" + q("Fairytale II - Cure a Queen"),
    "partial completion of dragon slayer i": DS1_STARTED,
    "completion of olaf's quest": q("Olaf's Quest"),
    "completion of horror from the deep": q("Horror from the Deep"),
    "partial completion of desert treasure i": q("Desert Treasure I", "started"),
    "completion of regicide": q("Regicide"),
    "partial completion of rum deal": q("Rum Deal", "started"),
    "completion of troubled tortugans": q("Troubled Tortugans"),
    "completion of cabin fever": q("Cabin Fever"),
    "completion of death to the dorgeshuun": q("Death to the Dorgeshuun"),
    "completion of lost city": q("Lost City"),
    "completion of royal trouble": q("Royal Trouble"),
    "completion of legends' quest": q("Legends' Quest"),
    # The importer cut this text at "Agility or Strength" (links); the cache lists "60 Strength or 60 Agility" for the group.
    "completion of death plateau, level 60 in or": q("Death Plateau") + "&" + STR_OR_AGI,
    "completion of haunted mine": q("Haunted Mine"),
    "completion of shadows of custodia": q("Shadows of Custodia"),
    "completion of bone voyage and elemental workshop i": q("Bone Voyage") + "&" + q("Elemental Workshop I"),
    "completion of bone voyage, completion of elemental workshop i": q("Bone Voyage") + "&" + q("Elemental Workshop I"),
    "completion of perilous moons": q("Perilous Moons"),
    "completion of elemental workshop i": q("Elemental Workshop I"),
    "completion of song of the elves for access to iorwerth dungeon": q("Song of the Elves"),
    "partial completion of mourning's end part ii": q("Mourning's End Part II", "started"),
    "started mourning's end part ii": q("Mourning's End Part II", "started"),
    "partial completion of dragon slayer i, completion of watchtower for access to ogre enclave": DS1_STARTED + "&" + q("Watchtower"),
    "partial completion of contact!": q("Contact!", "started"),
    "partial completion of lunar diplomacy": q("Lunar Diplomacy", "started"),
    "progressed the firemaking tasks of the barbarian training miniquest up to crafting pyre ships": q("Barbarian Training", "started"),
    "progressed the barbarian training miniquest up to the pyre ships section": q("Barbarian Training", "started"),
    "unlocked the like a boss ability via spending 200 slayer reward points, as well as various requirements for specific bosses": "unlock:Like a Boss",
    "partial completion of dragon slayer i, unlocked the ''seeing red'' ability via spending 50 slayer reward points": DS1_STARTED + "&unlock:Seeing Red",
    "completion of troubled tortugans, unlocked the ''wings spread'' ability via spending 80 slayer reward points": q("Troubled Tortugans") + "&unlock:Wings Spread",
}

# "Unlocked the ''X'' ability via spending N Slayer reward points" -> unlock:X (title case as in db table 117).
UNLOCK_RE = re.compile(r"^unlocked the ''(.+)'' ability via spending \d+ slayer reward points$")
UNLOCK_NAMES = {
    "watch the birdie": "Watch the Birdie",
    "reptile got ripped": "Reptile Got Ripped",
    "hot stuff": "Hot Stuff",
    "actual vampyre slayer": "Actual Vampyre Slayer",
    "warped reality": "Warped Reality",
    "basilocked": "Basilocked",
    "lured in": "Lured In",
}

# Krystilia's table has no requirement column; the footnotes of her Wiki page give them. Wiki "Slayer Rewards": "I Wildy More Slayer ...
# Krystilia will be able to assign Jellies, Dust Devils, Nechryaels, and Abyssal Demons" (the footnotes call it a free switch).
WILDY = "unlock:I Wildy More Slayer"
KEYS = {
    "Krystilia|jellie": WILDY,
    "Krystilia|abyssaldemon": PIP + "&" + WILDY,
    "Krystilia|nechryael": PIP + "&" + WILDY,
    "Krystilia|dustdevil": q("Desert Treasure I") + "&" + WILDY,
    "Krystilia|bloodveld": PIP,
    "Krystilia|spiritualcreature": q("Death Plateau") + "&" + STR_OR_AGI,
    "Krystilia|aviansie": "unlock:Watch the Birdie&" + STR_OR_AGI,
}

# What buying an unlock needs: Wiki "Slayer Rewards", Watch the Birdie: "Requires level 60 Agility or 60 Strength to unlock."
KEYS["unlock:watch the birdie"] = "skill:Agility:60|skill:Strength:60"


def expr_of(text):
    low = text.lower()
    if low in TEXTS:
        return TEXTS[low]
    m = UNLOCK_RE.match(low)
    if m and m.group(1) in UNLOCK_NAMES:
        return "unlock:" + UNLOCK_NAMES[m.group(1)]
    return None


def main():
    out_dir, quest_states = sys.argv[1], sys.argv[2]
    texts = set()
    with open(os.path.join(out_dir, "slayer-tasks.tsv")) as f:
        for r in csv.DictReader(f, delimiter="\t"):
            if r["other"] != "-":
                texts.add(r["other"])
    rows = {}
    for t in sorted(texts):
        e = expr_of(t)
        if e is None:
            sys.exit(f"no expression for requirement text: {t!r}")
        rows[t.lower()] = e
    rows.update(KEYS)
    quests = set()
    for e in rows.values():
        quests.update(re.findall(r"quest:([^:&|]+):", e))
    # The quests of the bosses a master can assign (slayer-bosses.tsv, written by hand from the Wiki "Boss Slayer" table).
    with open(os.path.join(out_dir, "slayer-bosses.tsv")) as f:
        for r in csv.DictReader(f, delimiter="\t"):
            quests.update(re.findall(r"quest:([^:&|]+):", r["expr"]))
    with open(os.path.join(out_dir, "slayer-requirements.tsv"), "w") as f:
        f.write("key\texpr\n")
        for k in sorted(rows):
            f.write(f"{k}\t{rows[k]}\n")
    found = {}
    with open(quest_states) as f:
        for r in csv.DictReader(f, delimiter="\t"):
            if r["name"] in quests:
                found[r["name"]] = r
    missing = quests - set(found)
    if missing:
        sys.exit(f"quests not in quest-states.tsv: {sorted(missing)}")
    with open(os.path.join(out_dir, "slayer-quests.tsv"), "w") as f:
        f.write("quest\tkind\tvar\tunstarted\tend\n")
        for n in sorted(found):
            r = found[n]
            f.write(f"{n}\t{r['kind']}\t{r['var']}\t{r['unstarted']}\t{r['end']}\n")
    print(f"{len(rows)} requirement rows, {len(found)} quests")


if __name__ == "__main__":
    main()
