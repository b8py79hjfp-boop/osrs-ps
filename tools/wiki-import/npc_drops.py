#!/usr/bin/env python3
"""
NPC drop tables from the OSRS Wiki (docs/mechanics/drops.md).

The cache has no drop data. Every page that uses {{Infobox Monster}} is read; its drop section is the page's
{{DropsLine}} rows (plus the table templates HerbDropLines, GemDropTable, RareDropTable, ...). The Wiki's own parser
turns those templates into rows (action=expandtemplates: it evaluates {{#expr}} rarities, {{Brimstone rarity}}, the
Lua modules), so values are the ones the Wiki shows. Each row is classified by the heading it sits under:

  always    "100%" tables and "Always" rows outside tertiary tables
  pre       "Pre-roll" tables: rolled in order before the main table; a hit replaces the main roll
  main      every other table: one roll, rows are mutually exclusive (rarities add up to at most 1)
  tertiary  "Tertiary" tables: each row is rolled on its own
  secondary "Secondary" tables: one more independent roll of its own

A row that the monster reaches through a shared table ({{HerbDropLines|7/128}}, {{GemDropTable|3/128}},
{{RareDropTable|...}}) is kept as a reference "@table" with the chance of rolling the table; the table itself is
written once to drop-tables.tsv. Shared tables are expanded with a tiny outer chance (rounding of the Wiki's
"1/73.1" style rarities vanishes) and rationalised; the gem, rare and mega-rare tables are read from their own pages
(exact x/128 rarities).

Versions: a row applies to every infobox version unless its table or row names |dropversion=A,B, in which case only to
infobox versions whose |dropversion= shares a name (Template:DropsLine/doc).

Output 1, npc-drops.tsv (tab-separated, header first), one row per drop line shared by a list of npc ids:
  npcs      comma-separated cache npc ids
  kind      always | pre | main | tertiary | secondary
  item      cache obj id, "@table" (shared table reference) or "-" (explicit nothing)
  qty       "min-max,min-max,..." (a single number n is "n"); every listed integer is equally likely
  noted     1 if the drop is the noted form
  num/den   chance as an exact fraction (kept as written by the Wiki, e.g. 5/128, 10/731 for "1/73.1")
  rolls     number of rolls per kill (Wiki "rolls" parameter, default 1)
Output 2, drop-tables.tsv: table, item, qty, noted, num, den (chances inside the table, summing to 1).

Usage (needs network access to oldschool.runescape.wiki; cache obj list from cache-dump):
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  python3 -I tools/wiki-import/npc_drops.py objs.txt content/drops/src/main/resources/tp/content/drops [cache-dir]
An optional cache-dir keeps the Wiki responses so reruns are fast.

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import collections
import hashlib
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal, InvalidOperation
from fractions import Fraction

API = "https://oldschool.runescape.wiki/api.php"
UA = "tickperfect-wiki-import/1.0 (private server dev tool)"
TEMPLATE = "Template:Infobox Monster"
CACHE_DIR = None
WORKERS = 4

# Worded rarities (Template:DropsLine/doc lists ranges, no point values): geometric middle of the documented range,
# "very rare" is "1/1000 or rarer". OPEN-D5 in docs/mechanics/drops.md.
WORDED = {"common": Fraction(1, 7), "uncommon": Fraction(1, 50), "rare": Fraction(1, 316), "very rare": Fraction(1, 1000)}

# Templates whose rows depend on something we cannot evaluate (slayer task, instance) or that are not monster drops.
SKIP_ROW_TEMPLATES = {"wildernessslayerdroptable", "wildernessslayercavedroptable", "superiordroptable",
                      "superiordroptabletbl2", "dropslineskill", "dropslinereward", "revenants/drops"}
SKIP_HEADINGS = ("pickpocket", "thieving", "mechanics", "average value")
# Shared tables a monster rolls with some chance: {{Name|chance|...}} (rare and gem tables come from their own pages).
SUB_TABLE_TEMPLATES = {"herbdroplines", "rareseeddroplines", "uncommonseeddroplines", "generalseeddroplines",
                       "allotmentseeddroplines", "treeherbseeddroplines", "usefulherbdroplines", "combatherbdroplines",
                       "talismandroplines", "birdnestdroptable", "raredroptable", "gemdroptable"}
# Templates that expand to rows with absolute rarities.
INLINE_TABLE_TEMPLATES = {"catacombsdroptable", "human drop table", "human drop table members", "fossildroplines"}
# Arguments of table templates that only change notes or bookkeeping, not the rows.
IGNORED_ARGS = {"f2p", "f2ponly", "dropversion", "rolls", "bucket", "leagueregion", "approx", "override", "reward", "skill",
                "multiplier", "salvage", "unnoted"}
# Pages that define the shared tables exactly: (page, level-2 heading) with plain {{DropsLine}} rows.
TABLE_PAGES = {"rare": ("Rare drop table", "Rare drop table"), "gem": ("Gem drop table", "Gem Drop Table"),
               "megarare": ("Gem drop table", "Mega-rare drop table")}
DROP_TABLE_NAMES = {"rare drop table": "rare", "gem drop table": "gem", "mega-rare drop table": "megarare"}
# Names whose lowest cache id is not the item the Wiki means: "Coins" 617 is a members-only unstackable variant.
NAME_OVERRIDES = {"coins": 995}
TINY = Fraction(1, 100000)
# Main-table rarities of a Wiki page add up to 1 give or take rounding and a few "overall" unique rows (abyssal demon:
# 1.002); above this the page lists several tables or version variants for one id and the rows roll on their own.
MAIN_SUM_LIMIT = Fraction(21, 20)

FRACTION = re.compile(r"^\s*~?\s*(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)\s*$")
HEADING = re.compile(r"^(={2,6})\s*(.*?)\s*\1\s*$", re.M)
MARK = re.compile(r"@@([LTX])(\d+)@@")
ROW = re.compile(r"<tr[^>]*>.*?</tr>", re.S)


def api(params, post=False):
    url = API
    data = None
    if post:
        data = urllib.parse.urlencode({**params, "format": "json", "formatversion": 2}).encode()
    else:
        url += "?" + urllib.parse.urlencode({**params, "format": "json"})
    req = urllib.request.Request(url, data=data, headers={"User-Agent": UA})
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
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


def expand(title, text):
    """The Wiki's expansion of [text] as if on page [title] (cached on disk when a cache dir is given)."""
    key = None
    if CACHE_DIR:
        key = os.path.join(CACHE_DIR, hashlib.sha1((title + "\0" + text).encode()).hexdigest())
        if os.path.exists(key):
            with open(key) as f:
                return f.read()
    d = api({"action": "expandtemplates", "prop": "wikitext", "title": title, "text": text}, post=True)
    out = d["expandtemplates"]["wikitext"]
    if key:
        with open(key, "w") as f:
            f.write(out)
    time.sleep(0.1)
    return out


# ---------------------------------------------------------------- wikitext scanning

def template_calls(text):
    """(start, end) of every top-level {{...}} in [text] (brace-balanced)."""
    out, i = [], 0
    while True:
        i = text.find("{{", i)
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
        out.append((i, j))
        i = j


def split_args(body):
    """Top-level |-separated pieces of a template body (ignores | inside {{ }} and [[ ]])."""
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
    return parts


def parse_call(call):
    """(normalised name, positional args, named args {lower key: value}) of '{{Name|a|k=v}}'."""
    parts = split_args(call[2:-2])
    name = re.sub(r"[_\s]+", " ", parts[0]).strip().lower()
    pos, named = [], {}
    for p in parts[1:]:
        m = re.match(r"\s*([^={}\[\]|]+?)\s*=(.*)$", p, re.S)
        if m:
            named[m.group(1).strip().lower()] = m.group(2).strip()
        else:
            pos.append(p.strip())
    return name, pos, named


def clean(v):
    v = re.sub(r"<ref[^>]*/>", "", v)
    v = re.sub(r"<ref[^>]*>.*?</ref>", "", v, flags=re.S)
    v = re.sub(r"<!--.*?-->", "", v, flags=re.S)
    v = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", v)
    v = re.sub(r"\{\{[^{}]*\}\}", "", v)
    v = re.sub(r"<[^>]+>", " ", v)
    return v.strip()


def infoboxes(text):
    out, start = [], 0
    while True:
        i = text.find("{{Infobox Monster", start)
        if i < 0:
            return out
        for s, e in template_calls(text[i:]):
            out.append(text[i + s + len("{{Infobox Monster"):i + e - 2])
            start = i + e
            break
        else:
            return out


def params(body):
    out = {}
    for p in split_args(body):
        if "=" in p:
            k, v = p.split("=", 1)
            out[k.strip().lower()] = v.strip()
    return out


def expand_versions(p):
    """One (ids, getter) per version of a parsed infobox (same as npc_combat.py)."""
    versions = sorted({int(m.group(1)) for k in p for m in [re.fullmatch(r"version(\d+)", k)] if m})
    if not versions:
        versions = sorted({int(m.group(1)) for k in p for m in [re.fullmatch(r"id(\d+)", k)] if m})
    out = []
    if not versions:
        out.append((p.get("id", ""), lambda k: p.get(k)))
    for n in versions:
        out.append((p.get(f"id{n}", p.get("id", "")), (lambda n: lambda k: p.get(f"{k}{n}", p.get(k)))(n)))
    return out


def version_names(value):
    return frozenset(s.strip().lower() for s in clean(value or "").split(",") if s.strip())


def primary_version(value):
    """The first drop version an infobox lists (an npc id serving several uses the first: OPEN-D4)."""
    names = [s.strip().lower() for s in clean(value or "").split(",") if s.strip()]
    return frozenset(names[:1])


# ---------------------------------------------------------------- numbers

def to_fraction(s):
    """'5/128', '1/73.1', '1/40,000' -> Fraction; None when it is not a fraction."""
    m = FRACTION.match(s.replace(",", ""))
    if not m:
        return None
    try:
        return Fraction(Decimal(m.group(1))) / Fraction(Decimal(m.group(2)))
    except (InvalidOperation, ZeroDivisionError):
        return None


def parse_quantity(text):
    """(ranges, noted) from '5-10,12', '5–10; 12', '100 (noted)'; ranges is [(lo, hi)] or None when unparsable."""
    t = text.replace("&nbsp;", " ").replace("–", "-").replace("—", "-")
    noted = "noted" in t.lower()
    # Rendered cells list alternatives with ';' and use ',' as the thousands separator ("5-10; 12", "3,000").
    t = re.sub(r"\(.*?\)", "", t).replace(" ", "").replace(",", "")
    ranges = []
    for part in [p for p in t.split(";") if p]:
        m = re.fullmatch(r"(\d+)(?:-(\d+))?", part)
        if not m:
            return None, noted
        lo = int(m.group(1))
        hi = int(m.group(2)) if m.group(2) else lo
        if hi < lo:
            lo, hi = hi, lo
        ranges.append((lo, hi))
    return (ranges or None), noted


def qty_text(ranges):
    return ",".join(str(lo) if lo == hi else f"{lo}-{hi}" for lo, hi in ranges)


def tags(s):
    return re.sub(r"<[^>]+>", "", s)


def parse_row(row):
    """(item name, ranges|None, noted, rarity Fraction|'always'|None, rarity text, rolls) of an expanded <tr>."""
    item = re.search(r'class="item-col"[^>]*>(.*?)</td>', row, re.S)
    if not item or "Free-to-play-only" in item.group(1):
        return None  # f2p-only rows (the server is a members world, OPEN-D6)
    link = re.search(r"\[\[([^\]|]+)(?:\|[^\]]*)?\]\]", item.group(1))
    name = (link.group(1) if link else tags(item.group(1))).strip().split("#")[0].strip()
    tds = re.findall(r"<td[^>]*>(.*?)</td>", row, re.S)
    qcell = re.search(r'class="item-col".*?</td>\s*<td[^>]*>(.*?)</td>', row, re.S)
    qtext = tags(qcell.group(1)) if qcell else ""
    ranges, noted = parse_quantity(qtext) if re.search(r"\d", qtext) else (None, False)
    fr = re.search(r'data-drop-fraction="([^"]*)"', row)
    rarity_cell = tds[3] if len(tds) > 3 else ""
    rtext = tags(re.sub(r"<ref.*?(?:</ref>|/>)", "", rarity_cell, flags=re.S)).strip()
    first = rtext.split(";")[0].strip().lstrip("~").strip()
    rolls = 1
    if fr:
        # "3 × 1/128": the table is rolled 3 times, each with this chance (Template:DropsLine |rolls=).
        m = re.match(r"(\d+)\s*×\s*(.+)$", fr.group(1).strip())
        if m:
            rolls, text = int(m.group(1)), m.group(2)
        else:
            text = fr.group(1)
        return name, ranges, noted, to_fraction(text), first, rolls
    if first.lower() == "always":
        return name, ranges, noted, "always", first, rolls
    return name, ranges, noted, None, first, rolls


# ---------------------------------------------------------------- cache objs

def load_objs(path):
    """name (lower) -> lowest obj id; noted and placeholder objs share the name but come later."""
    by_name = {}
    ambiguous = 0
    with open(path) as f:
        for line in f:
            m = re.match(r'(\d+) name="(.*?)" cost', line)
            if not m:
                continue
            n = m.group(2).lower()
            if n in by_name:
                ambiguous += 1
                continue
            by_name[n] = int(m.group(1))
    by_name.update(NAME_OVERRIDES)
    return by_name


# ---------------------------------------------------------------- page processing

class Entry:
    """One expanded drop row of a page, before it is assigned to npc ids."""
    __slots__ = ("kind", "item", "ranges", "noted", "chance", "rolls", "versions")

    def __init__(self, kind, item, ranges, noted, chance, rolls, versions):
        self.kind, self.item, self.ranges, self.noted = kind, item, ranges, noted
        self.chance, self.rolls, self.versions = chance, rolls, versions


def classify(text, parent):
    t = text.lower()
    if "tertiar" in t:
        return "tertiary"
    if "secondary" in t:
        return "secondary"
    if t.startswith("100") or t == "always":
        return "always"
    if "pre-roll" in t or "preroll" in t or "pre roll" in t:
        return "pre"
    if parent in ("tertiary", "secondary"):
        return parent
    return "main"


def is_drop_call(call):
    name = parse_call(call)[0]
    return (name.startswith("dropsline") or name == "dropstablehead" or name in SUB_TABLE_TEMPLATES
            or name in SKIP_ROW_TEMPLATES or name in INLINE_TABLE_TEMPLATES)


def drop_region(text):
    """The part of the page holding drop calls: from the level-2 heading before the first one to the last one."""
    calls = [(s, e) for s, e in template_calls(text) if is_drop_call(text[s:e])]
    if not calls:
        return None
    first, last = calls[0][0], calls[-1][1]
    start = first
    for m in re.finditer(r"^==[^=].*?[^=]==\s*$", text[:first], re.M):
        start = m.start()
    return text[start:last]


def prepare(region):
    """Rewrite [region] for expansion: a marker before every drop call (so rows can be attributed to it) and shared
    tables replaced by their marker alone. Returns (text, metas) where metas[marker id] describes the call."""
    out, metas, pos, n = [], {}, 0, 0
    table_versions, table_region = frozenset(), False
    for s, e in template_calls(region):
        call = region[s:e]
        name, pos_args, named = parse_call(call)
        raw_name = split_args(call[2:-2])[0].strip()
        out.append(region[pos:s])
        pos = e
        if name == "dropstablehead":
            table_versions, table_region = version_names(named.get("dropversion")), "leagueregion" in named
            out.append(call)
            continue
        if name == "dropstablebottom":
            table_versions, table_region = frozenset(), False
            out.append(call)
            continue
        versions = version_names(named.get("dropversion")) or table_versions
        meta = {"name": name, "versions": versions, "rolls": parse_rolls(named.get("rolls")),
                "region": "leagueregion" in named, "raw": call, "pos": pos_args, "named": named}
        if name.startswith("dropsline") and name not in SKIP_ROW_TEMPLATES:
            tag, text = "L", call
        elif name in SKIP_ROW_TEMPLATES:
            tag, text = "X", call
            meta["skip"] = True
        elif name in SUB_TABLE_TEMPLATES:
            tag, text = "T", ""
            if name not in ("raredroptable", "gemdroptable"):
                kept = [f"{k}={v}" for k, v in sorted(named.items()) if k not in IGNORED_ARGS]
                meta["chance"] = to_fraction(pos_args[0]) if pos_args else None
                meta["table"] = "|".join([name] + pos_args[1:] + kept)
                meta["probe"] = "{{" + "|".join([raw_name, "1/100000"] + pos_args[1:] + kept) + "}}"
        elif name in INLINE_TABLE_TEMPLATES:
            tag, text = "X", call
        else:
            out.append(call)
            continue
        n += 1
        meta["tag"] = tag
        metas[n] = meta
        out.append(f"\n@@{tag}{n}@@\n{text}\n")
    out.append(region[pos:])
    return "".join(out), metas


def process_page(title, text, sub_tables, stats):
    """All Entry objects of one page, or [] when it has no drop section."""
    region = drop_region(text)
    if region is None:
        return []
    prepared, metas = prepare(region)
    expanded = expand(title, prepared)
    entries, kinds, skip_level, cur = [], {}, None, None
    tokens = sorted([(m.start(), "H", m) for m in HEADING.finditer(expanded)]
                    + [(m.start(), "M", m) for m in MARK.finditer(expanded)]
                    + [(m.start(), "R", m) for m in ROW.finditer(expanded)], key=lambda t: t[0])
    for _, typ, m in tokens:
        if typ == "H":
            level = len(m.group(1))
            lower = [k for k in kinds if k < level]
            parent = kinds[max(lower)] if lower else "main"
            for k in [k for k in kinds if k >= level]:
                del kinds[k]
            kinds[level] = classify(m.group(2), parent)
            if skip_level is not None and level <= skip_level:
                skip_level = None
            if skip_level is None and any(w in m.group(2).lower() for w in SKIP_HEADINGS):
                skip_level = level
        elif typ == "M":
            cur = metas[int(m.group(2))]
            if cur["tag"] == "T":
                if skip_level is None:
                    sub_ref(cur, kinds[max(kinds)] if kinds else "main", entries, sub_tables, stats)
                cur = None
        elif "<td" in m.group() and skip_level is None:
            row_entry(m.group(), kinds[max(kinds)] if kinds else "main", cur, entries, stats)
            if cur is not None and cur["tag"] == "L":
                cur = None
    return entries


def talisman_flags(named):
    return tuple(k for k in ("chaostalisman", "naturetalisman") if k in named)


def gem_ref(named):
    flags = talisman_flags(named)
    if len(flags) == 1:
        return "@gem-" + flags[0][:-len("talisman")]
    return "@gem"


def sub_ref(meta, kind, entries, sub_tables, stats):
    name, rolls, versions = meta["name"], meta["rolls"], meta["versions"]
    if name in ("raredroptable", "gemdroptable"):
        chances = [to_fraction(a) for a in meta["pos"][:2]]
        if not chances or chances[0] is None:
            stats["unparsed_sub"] += 1
            return
        if name == "raredroptable":
            entries.append(Entry(kind, "@rare", [(1, 1)], False, chances[0], rolls, versions))
            if len(chances) > 1 and chances[1] is not None:
                entries.append(Entry(kind, gem_ref(meta["named"]), [(1, 1)], False, chances[1], rolls, versions))
        else:
            entries.append(Entry(kind, gem_ref(meta["named"]), [(1, 1)], False, chances[0], rolls, versions))
        stats["sub_refs"] += 1
        return
    if meta["chance"] is None:
        stats["unparsed_sub"] += 1
        return
    sub_tables.setdefault(meta["table"], meta["probe"])
    entries.append(Entry(kind, "@" + meta["table"], [(1, 1)], False, meta["chance"], rolls, versions))
    stats["sub_refs"] += 1


def parse_rolls(v):
    m = re.search(r"\d+", clean(v or ""))
    return max(1, int(m.group())) if m else 1


def row_entry(row, kind, meta, entries, stats):
    parsed = parse_row(row)
    if parsed is None:
        return
    if meta is not None and meta.get("skip"):
        stats["skipped_template_rows"] += 1
        return
    name, ranges, noted, chance, rtext, row_rolls = parsed
    stats["rows"] += 1
    versions = meta["versions"] if meta else frozenset()
    rolls = max(meta["rolls"] if meta else 1, row_rolls)
    if meta and meta["region"]:
        stats["region_only"] += 1
        return
    if meta and meta["tag"] == "L" and "brimstone" in meta["raw"].lower():
        stats["task_only"] += 1
        return
    if chance is None:
        chance = WORDED.get(rtext.lower())
        if chance is None:
            stats["unknown_rarity"] += 1
            stats["unknown_rarity_names"][rtext or "(blank)"] += 1
            return
        stats["worded"] += 1
    key = name.lower()
    item = "-" if key == "nothing" else "@" + DROP_TABLE_NAMES[key] if key in DROP_TABLE_NAMES else name
    if chance == "always":
        if kind in ("tertiary", "secondary"):
            stats["tertiary_always"] += 1
            return
        kind, chance = "always", Fraction(1)
    elif kind == "always":
        kind = "main"
    if ranges is None and item != "-" and not item.startswith("@"):
        stats["no_quantity"] += 1
        return
    entries.append(Entry(kind, item, ranges or [(1, 1)], noted, chance, rolls, versions))


# ---------------------------------------------------------------- shared tables

def table_rows(expanded):
    """[(item name, ranges, noted, Fraction)] of the fraction rows in [expanded] wikitext."""
    out = []
    for r in ROW.findall(expanded):
        if "<td" not in r:
            continue
        parsed = parse_row(r)
        if parsed is None:
            continue
        name, ranges, noted, chance, _, _ = parsed
        if isinstance(chance, Fraction):
            out.append((name, ranges or [(1, 1)], noted, chance))
    return out


def canonical_table(texts, key):
    """Rows of the rare/gem/mega-rare table from its own page (rarities relative to the table)."""
    page, heading = TABLE_PAGES[key]
    text = texts[page]
    m = re.search(r"^==\s*" + re.escape(heading) + r"\s*==\s*$", text, re.M | re.I)
    rest = text[m.end():]
    n = re.search(r"^==[^=].*?[^=]==\s*$", rest, re.M)
    return table_rows(expand(page, rest[:n.start()] if n else rest))


def gem_variants(rows):
    """The gem table in three forms: both talismans (location decides), chaos only, nature only."""
    plain = [r for r in rows if r[0] not in ("Chaos talisman", "Nature talisman")]
    chaos = next(r for r in rows if r[0] == "Chaos talisman")
    nature = next(r for r in rows if r[0] == "Nature talisman")
    return {"gem": plain + [("Nature talisman/Chaos talisman", nature[1], False, nature[3])],
            "gem-chaos": plain + [chaos], "gem-nature": plain + [nature]}


def rationalise(rows):
    """Chances of a probe expansion (outer chance TINY) -> exact fractions of the table, and their sum."""
    scaled = [(n, r, nt, (c / TINY).limit_denominator(10000)) for n, r, nt, c in rows]
    return scaled, sum(c for *_, c in scaled)


# ---------------------------------------------------------------- main

def obj_id(name, objs):
    return objs.get(name.lower())


def item_token(name, objs):
    """TSV item cell for a table row name: obj id, "-" (nothing), "@table" or "a/b" (nature/chaos talisman)."""
    key = name.lower()
    if key == "nothing":
        return "-"
    if key in DROP_TABLE_NAMES:
        return "@" + DROP_TABLE_NAMES[key]
    if "/" in name:
        ids = [obj_id(part, objs) for part in name.split("/")]
        return None if None in ids else "/".join(str(i) for i in ids)
    o = obj_id(name, objs)
    return None if o is None else str(o)


def main():
    global CACHE_DIR
    objs_path, out_dir = sys.argv[1], sys.argv[2]
    if len(sys.argv) > 3:
        CACHE_DIR = sys.argv[3]
        os.makedirs(CACHE_DIR, exist_ok=True)
    objs = load_objs(objs_path)
    titles = pages_using_template()
    print(f"{len(titles)} pages use {TEMPLATE}", file=sys.stderr)
    texts = wikitexts(titles + sorted({p for p, _ in TABLE_PAGES.values()}))
    stats = collections.Counter()
    stats["unknown_rarity_names"] = collections.Counter()
    sub_tables = {}

    def work(title):
        local = collections.Counter()
        local["unknown_rarity_names"] = collections.Counter()
        subs = {}
        try:
            es = process_page(title, texts[title], subs, local)
        except Exception as ex:  # noqa: BLE001 - keep going, report
            print(f"FAILED {title}: {ex!r}", file=sys.stderr)
            es = []
        return title, es, subs, local

    results = {}
    with ThreadPoolExecutor(WORKERS) as pool:
        for i, (title, es, subs, local) in enumerate(pool.map(work, sorted(titles))):
            results[title] = es
            sub_tables.update(subs)
            for k, v in local.items():
                if k == "unknown_rarity_names":
                    stats[k].update(v)
                else:
                    stats[k] += v
            if i % 100 == 0:
                print(f"  {i}/{len(titles)}", file=sys.stderr)

    tables = {"rare": canonical_table(texts, "rare"), "megarare": canonical_table(texts, "megarare")}
    tables.update(gem_variants(canonical_table(texts, "gem")))
    table_ids = {}
    for key, probe in sorted(sub_tables.items()):
        # Short, stable table names: long argument lists (template syntax) are replaced by a hash.
        table_ids[key] = key if len(key) <= 40 else key.split("|")[0] + "|" + hashlib.sha1(key.encode()).hexdigest()[:8]
        scaled, total = rationalise(table_rows(expand("Hill Giant", probe)))
        if abs(total - 1) > Fraction(1, 200):
            print(f"WARNING table {key}: chances add up to {float(total):.4f}", file=sys.stderr)
            stats["tables_off"] += 1
        tables[table_ids[key]] = [(n, r, nt, c / total) for n, r, nt, c in scaled] if total else []

    # entries -> npc ids (first page by title claims an id)
    claimed, groups = {}, []
    for title in sorted(titles):
        es = results.get(title) or []
        if not es:
            continue
        per_version = collections.defaultdict(set)
        for body in infoboxes(texts[title]):
            for ids, get in expand_versions(params(body)):
                dv = primary_version(get("dropversion"))
                for i in re.findall(r"\d+", clean(ids or "")):
                    claimed.setdefault(i, title)
                    if claimed[i] == title:
                        per_version[dv].add(int(i))
        if per_version:
            groups.append((title, es, per_version))
    stats["pages_with_drops"] = len(groups)
    all_ids = {i for body in (b for t in titles for b in infoboxes(texts[t])) for ids, _ in expand_versions(params(body))
               for i in re.findall(r"\d+", clean(ids or ""))}

    item_missing, rows_out, ids_with_drops = collections.Counter(), [], set()
    for title, es, per_version in groups:
        merged = collections.defaultdict(set)
        for dv, ids in per_version.items():
            for idx, e in enumerate(es):
                if not e.versions or (e.versions & dv):
                    merged[idx] |= ids
        for idx, ids in sorted(merged.items()):
            e = es[idx]
            if e.item == "-":
                token = e.item
            elif e.item.startswith("@"):
                token = "@" + table_ids.get(e.item[1:], e.item[1:])
            else:
                o = obj_id(e.item, objs)
                if o is None:
                    item_missing[e.item] += 1
                    stats["lines_unmatched"] += 1
                    continue
                token = str(o)
            ids_with_drops |= ids
            rows_out.append([",".join(str(i) for i in sorted(ids)), e.kind, token, e])
    # a main table whose rarities add up to well over 1 cannot be a single roll: its rows roll on their own
    sums = collections.defaultdict(Fraction)
    for ids, kind, _, e in rows_out:
        if kind == "main":
            sums[ids] += e.chance
    for r in rows_out:
        if r[1] == "main" and sums[r[0]] > MAIN_SUM_LIMIT:
            r[1] = "independent"
    stats["main_tables_over_1"] = sum(1 for v in sums.values() if v > MAIN_SUM_LIMIT)

    os.makedirs(out_dir, exist_ok=True)
    order = {"always": 0, "pre": 1, "main": 2, "independent": 3, "secondary": 4, "tertiary": 5}
    with open(os.path.join(out_dir, "npc-drops.tsv"), "w") as f:
        f.write("npcs\tkind\titem\tqty\tnoted\tnum\tden\trolls\n")
        for ids, kind, token, e in sorted(rows_out, key=lambda r: (int(r[0].split(",")[0]), r[0], order[r[1]])):
            f.write("\t".join([ids, kind, token, qty_text(e.ranges), "1" if e.noted else "0", str(e.chance.numerator),
                               str(e.chance.denominator), str(e.rolls)]) + "\n")
    table_missing, table_lines = collections.Counter(), 0
    with open(os.path.join(out_dir, "drop-tables.tsv"), "w") as f:
        f.write("table\titem\tqty\tnoted\tnum\tden\n")
        for key in sorted(tables):
            for name, ranges, noted, chance in tables[key]:
                token = item_token(name, objs)
                if token == "-" and (key.startswith("gem") or key == "megarare"):
                    token = "-w"  # a ring of wealth removes the empty slot of these tables (docs/mechanics/drops.md)
                if token is None:
                    table_missing[name] += 1
                    continue
                table_lines += 1
                f.write("\t".join([key, token, qty_text(ranges), "1" if noted else "0", str(chance.numerator),
                                   str(chance.denominator)]) + "\n")
    print(f"pages with drops: {stats['pages_with_drops']} of {len(titles)}", file=sys.stderr)
    print(f"npc ids with drops: {len(ids_with_drops)} of {len(all_ids)} infobox ids", file=sys.stderr)
    print(f"rows read {stats['rows']}; npc-drops lines {len(rows_out)} (shared-table refs {stats['sub_refs']}); "
          f"{table_lines} lines in {len(tables)} tables", file=sys.stderr)
    for k in ("region_only", "task_only", "skipped_template_rows", "tertiary_always", "worded", "unknown_rarity",
              "no_quantity", "unparsed_sub", "tables_off", "main_tables_over_1", "lines_unmatched"):
        print(f"  {k}: {stats[k]}", file=sys.stderr)
    print("unmatched item names: rows", sum(item_missing.values()), "distinct", len(item_missing), file=sys.stderr)
    for n, c in item_missing.most_common(40):
        print(f"    {c:5d} {n}", file=sys.stderr)
    print("unknown rarities:", dict(stats["unknown_rarity_names"].most_common(15)), file=sys.stderr)
    print("unmatched table items:", dict(table_missing), file=sys.stderr)


if __name__ == "__main__":
    main()
