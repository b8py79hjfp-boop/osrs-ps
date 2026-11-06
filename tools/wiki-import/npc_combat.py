#!/usr/bin/env python3
"""
NPC combat data from the OSRS Wiki (docs/mechanics/combat-data.md OPEN-N1/N2, now docs/mechanics/npc-combat.md).

The cache has NPC levels and hitpoints but no attack speed, max hit, attack style, bonuses, respawn time or poison
data. The Wiki's {{Infobox Monster}} has them. Every page that uses the template is read; switch infoboxes (several
per page) and versioned fields (|id1 = .., |max hit2 = ..) are expanded so each cache NPC id gets one record.

Output: tab-separated, one row per cache npc id, header row first (content modules parse it without a JSON library):
  id, name, combat (level), size, speed (ticks), max (largest max hit in the field), maxes (style:max,... when the
  field names styles), styles (stab|slash|crush|melee|ranged|magic|dragonfire|typeless|special, comma-separated),
  aggressive (0/1), respawn (ticks), hp/att/str/def/mage/range (Wiki levels, to check against the cache),
  ab/sb (melee attack/strength bonus), am/mb (magic attack/damage bonus), ar/rb (ranged attack/strength bonus),
  dstab/dslash/dcrush/dmagic/dlight/dstandard/dheavy (defence bonuses), poison (damage of a poisonous hit; 1 if only
  "Yes"), venom (0/1), pimm/vimm (immune to poison/venom, 0/1).
When several versions share one id (raid scaling, phases, Deadman), the first (sorted by page title, then version)
is kept; usually the base version.

Usage (needs network access to oldschool.runescape.wiki):
  python3 -I tools/wiki-import/npc_combat.py content/combat/src/main/resources/tp/content/combat/npc-combat.tsv

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import json
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
UA = "tickperfect-wiki-import/1.0 (private server dev tool)"
TEMPLATE = "Template:Infobox Monster"


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


def infoboxes(text):
    """Bodies of every {{Infobox Monster ...}} (brace-balanced)."""
    out, start = [], 0
    while True:
        i = text.find("{{Infobox Monster", start)
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
        out.append(text[i + len("{{Infobox Monster"):j - 2])
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
    v = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", v)
    v = re.sub(r"\{\{[^{}]*\}\}", "", v)
    v = re.sub(r"<[^>]+>", " ", v)
    return v.strip()


def int_or(v, default=0):
    m = re.search(r"-?\d+", clean(v or "").replace(",", ""))
    return int(m.group()) if m else default


STYLE_WORDS = ["stab", "slash", "crush", "melee", "ranged", "magic", "dragonfire", "typeless", "special"]


def styles(v):
    t = clean(v or "").lower()
    return [s for s in STYLE_WORDS if re.search(r"\b" + s + r"\b", t)]


def max_hits(v):
    """(largest number, {style: max}) from e.g. '55 (Crush), 31 (Ranged), 50 (Special)'."""
    t = clean(v or "").replace(",", ", ")
    nums = [int(n) for n in re.findall(r"\d+", t.replace(", ", " "))]
    per = {}
    for n, label in re.findall(r"(\d+)\s*\(([^)]*)\)", t):
        for s in styles(label):
            per[s] = max(per.get(s, 0), int(n))
    return (max(nums) if nums else 0), per


def yes(v):
    return clean(v or "").lower().startswith("yes")


def immune(params_get, text_key, resist_key):
    r = params_get(resist_key)
    if r is not None and clean(r) != "":
        return int_or(r) >= 100
    t = clean(params_get(text_key) or "").lower()
    return t.startswith("immune") or t == "yes"


def record(get):
    mx, per = max_hits(get("max hit"))
    poison_text = clean(get("poisonous") or "").lower()
    rec = {
        "name": clean(get("name") or ""),
        "combat": int_or(get("combat")),
        "size": int_or(get("size"), 1),
        "speed": int_or(get("attack speed")),
        "max": mx,
        "maxes": per,
        "styles": styles(get("attack style")),
        "aggressive": yes(get("aggressive")),
        "respawn": int_or(get("respawn")),
        "hp": int_or(get("hitpoints")),
        "att": int_or(get("att")),
        "str": int_or(get("str")),
        "def_lvl": int_or(get("def")),
        "mage": int_or(get("mage")),
        "range": int_or(get("range")),
        "ab": int_or(get("attbns")),
        "sb": int_or(get("strbns")),
        "am": int_or(get("amagic")),
        "mb": int_or(get("mbns")),
        "ar": int_or(get("arange")),
        "rb": int_or(get("rngbns")),
        "def": [int_or(get(k)) for k in ("dstab", "dslash", "dcrush", "dmagic")]
        + [int_or(get(k) if get(k) is not None else get("drange")) for k in ("dlight", "dstandard", "dheavy")],
        "poison": 0 if not poison_text.startswith("yes") else int_or(poison_text, 1),
        "venom": "venom" in poison_text,
        "pimm": immune(get, "immunepoison", "poisonresistance"),
        "vimm": immune(get, "immunevenom", "venomresistance"),
    }
    return rec


COLUMNS = ["id", "name", "combat", "size", "speed", "max", "maxes", "styles", "aggressive", "respawn", "hp", "att", "str",
           "def", "mage", "range", "ab", "sb", "am", "mb", "ar", "rb", "dstab", "dslash", "dcrush", "dmagic", "dlight",
           "dstandard", "dheavy", "poison", "venom", "pimm", "vimm"]


def row(npc_id, r):
    def b(v):
        return "1" if v else "0"
    cells = [npc_id, r["name"].replace("\t", " "), r["combat"], r["size"], r["speed"], r["max"],
             ",".join(f"{k}:{v}" for k, v in r["maxes"].items()), ",".join(r["styles"]), b(r["aggressive"]), r["respawn"],
             r["hp"], r["att"], r["str"], r["def_lvl"], r["mage"], r["range"], r["ab"], r["sb"], r["am"], r["mb"],
             r["ar"], r["rb"], *r["def"], r["poison"], b(r["venom"]), b(r["pimm"]), b(r["vimm"])]
    return "\t".join(str(c) for c in cells)


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


def main():
    dest = sys.argv[1]
    titles = pages_using_template()
    print(f"{len(titles)} pages use {TEMPLATE}", file=sys.stderr)
    texts = wikitexts(titles)
    data, conflicts = {}, 0
    for title in sorted(texts):
        for body in infoboxes(texts[title]):
            for ids, get in expand(params(body)):
                rec = record(get)
                for i in re.findall(r"\d+", clean(ids or "")):
                    if i in data and data[i] != rec:
                        conflicts += 1
                        continue  # first page wins (sorted by title); conflicts are reported
                    data[i] = rec
    with open(dest, "w") as f:
        f.write("\t".join(COLUMNS) + "\n")
        for npc_id, r in sorted(data.items(), key=lambda kv: int(kv[0])):
            f.write(row(npc_id, r) + "\n")
    print(f"wrote {len(data)} npc ids ({conflicts} duplicate ids with different data skipped) to {dest}", file=sys.stderr)


if __name__ == "__main__":
    main()
