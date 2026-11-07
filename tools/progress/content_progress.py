#!/usr/bin/env python3
"""
Content progress report: writes PROGRESS.md at the repo root. Generated, do not edit by hand.

What exists in OSRS : the OSRS Wiki (skills, bosses, raids, minigames, quests, achievement diaries).
What we have        : content/ and engine/. A feature is "added" when a code file for it exists (matched by file-name
                      prefix, or by explicit alias below), or when its generated symbols (tp.symbols.Npcs / Interfaces
                      constant names) are referenced by a file that registers behaviour. Added means "there is
                      code for it", not "it matches the Wiki": completeness is what the linked docs/mechanics/*.md and
                      their OPEN counts are for.

Approach and layout of the report follow OpenRune's tools/progress/content-progress.mjs (ISC licence,
Copyright (c) OpenRune contributors, https://github.com/OpenRune); this script is an independent Python rewrite
that shares only the idea (Wiki lists as the denominator, code scan as the numerator, green/red markers).

Usage (run from anywhere; no third-party packages):
  python3 -I tools/progress/content_progress.py [--refresh] [--symbols DIR] [--out FILE]

  --refresh   re-download the Wiki pages (otherwise .data/progress/wiki-cache.json is used when present)
  --symbols   directory with the generated Npcs.java / Locs*.java / Interfaces.java
              (default symbols/build/generated/symbols/tp/symbols; produced by ./gradlew build)
  --out       output file (default PROGRESS.md at the repo root)

Wiki access follows tools/wiki-import: https://oldschool.runescape.wiki/api.php with the tickperfect-wiki-import
User-Agent. Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md); the cache lives in the gitignored .data/.
"""
import html
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
API = "https://oldschool.runescape.wiki/api.php"
UA = "tickperfect-wiki-import/1.0 (private server dev tool)"
WIKI_DELAY_SECONDS = 0.5
IMAGE_BATCH = 50  # MediaWiki pageimages/imageinfo accept at most 50 titles per request
THUMB_PX = 48
WIKI_BASE = "https://oldschool.runescape.wiki/w/"
CACHE_FILE = os.path.join(ROOT, ".data", "progress", "wiki-cache.json")
DEFAULT_SYMBOLS = os.path.join(ROOT, "symbols", "build", "generated", "symbols", "tp", "symbols")
GREEN, RED = "\U0001F7E2", "\U0001F534"
MIN_PREFIX_LEN = 6  # shortest normalised name that may match a file-name prefix ("Mining" yes, "Fire" no)

# The 24 skills. The Wiki's Category:Skills also holds non-skill pages, so it is intersected with this list.
SKILLS = [
    "Attack", "Strength", "Defence", "Ranged", "Prayer", "Magic", "Runecraft", "Construction", "Hitpoints",
    "Agility", "Herblore", "Thieving", "Crafting", "Fletching", "Slayer", "Hunter", "Mining", "Smithing",
    "Fishing", "Cooking", "Firemaking", "Woodcutting", "Farming", "Sailing",
]

# Modules that only hold data about NPCs, loot or spells: referencing a boss there is not an implementation.
NOT_IMPLEMENTATION_MODULES = {"drops", "collectionlog", "dev"}
# Files that list NPCs for other reasons (task tables, assignment) rather than implementing them.
NOT_IMPLEMENTATION_FILE = re.compile(r"/Slayer(Data|Assign|Cache|Rewards|State|Gate)\.kt$")
# A file that registers behaviour (handlers, bosses, spawns), not one that merely names a symbol.
BEHAVIOUR = re.compile(r"\bBoss\(|\bon[A-Z][A-Za-z]*\(|\bspawnNpc\(|\bnpcAs\(|\bregister[A-Z][A-Za-z]*\(")

# --- manual mappings: only where the Wiki name cannot be matched mechanically ---------------------------------------
# files: (directory, stem prefix) -> every main .kt file under the directory whose stem starts so ("X=" = exact stem).
# Attack, Strength, Defence and Ranged have no file of their own: the combat core awards their XP.
COMBAT_CORE = [("content/combat", "Combat="), ("content/combat", "Formulas="), ("content/combat", "Attacks="),
               ("content/combat", "Equipment="), ("content/combat", "CombatState=")]
SKILL_FILES = {
    "Attack": COMBAT_CORE, "Strength": COMBAT_CORE, "Defence": COMBAT_CORE, "Ranged": COMBAT_CORE,
    "Hitpoints": [("content/combat", "Combat="), ("content/combat", "Death="), ("content/combat", "Consumables=")],
    "Magic": [("content/magic", ""), ("content/combat", "Magic"), ("content/combat", "Spell"),
              ("content/combat", "Autocast"), ("content/combat", "CombatSpell")],
    "Prayer": [("content/skills", "Prayer"), ("content/combat", "Prayer"), ("content/skills", "Remains"),
               ("content/skills", "Ectofuntus")],
    "Runecraft": [("content/skills", "Runecraft")],
    "Construction": [("content/skills", "Construction"), ("content/skills", "Poh"), ("content/skills", "House"),
                     ("content/skills", "PlayerOwnedHouse")],
    "Agility": [("content/skills", "Agility"), ("content/skills", "Shortcut")],
    "Thieving": [("content/skills", "Thieving"), ("content/skills", "StallThieving"),
                 ("content/skills", "Pickpocketing"), ("content/skills", "Blackjacking")],
    "Smithing": [("content/skills", "Smithing"), ("content/skills", "BlastFurnace")],
    "Firemaking": [("content/skills", "Firemaking"), ("content/skills", "BurnableLog")],
    "Cooking": [("content/skills", "Cooking")],
    "Sailing": [("content/sailing", "")],
}
SKILL_DOCS = {
    "Attack": ["combat-formulas.md", "combat-timeline.md"], "Strength": ["combat-formulas.md", "combat-timeline.md"],
    "Defence": ["combat-formulas.md", "combat-timeline.md"], "Ranged": ["combat-formulas.md", "combat-timeline.md"],
    "Hitpoints": ["combat-formulas.md", "combat-timeline.md"],
    "Magic": ["combat-magic.md", "magic-utility.md", "teleports.md"],
    "Prayer": ["prayers.md", "prayer-training.md"],
    "Slayer": ["slayer.md", "slayer-equipment.md"],
    "Sailing": "sailing-*.md",
}
# Wiki name -> overrides for bosses, raids, minigames and quests.
ALIASES = {
    # quests: the file stem differs from the Wiki title in more than "The" / "&"
    "Dragon Slayer I": {"files": [("content/quests", "DragonSlayer"), ("content/quests", "Crandor")]},
    # minigames
    "Brimhaven Agility Arena": {"files": [("content/skills", "BrimhavenArena")]},
    "Guardians of the Rift": {"files": [("content/skills", "RunecraftGotr"), ("content/skills", "GotrRewards")],
                              "docs": ["guardians-of-the-rift.md"]},
    "Motherlode Mine": {"files": [("content/skills", "Motherlode")], "docs": ["motherlode.md"]},
    "Giants' Foundry": {"files": [("content/skills", "GiantsFoundry")], "docs": ["giants-foundry.md"]},
    "Duel Arena": {"files": [("content/pvp", "DuelArena")], "docs": ["duel-arena.md"]},
    "Pyramid Plunder": {"files": [("content/skills", "PyramidPlunder")], "docs": ["pyramid-plunder.md"]},
    "Barbarian Assault": {},
    # bosses whose NPC symbols do not carry the Wiki name
    "King Black Dragon": {"syms": ["KING_DRAGON"]},
    "Crazy Archaeologist": {"syms": ["CRAZY_ARCHAEOLOGIST"]},
    "Chaos Fanatic": {"syms": ["CHAOS_FANATIC"]},
    "Ahrim the Blighted": {"syms": ["BARROWS_AHRIM"]},
    "Karil the Tainted": {"syms": ["BARROWS_KARIL"]},
    "Dharok the Wretched": {"syms": ["BARROWS_DHAROK"]},
    "Guthan the Infested": {"syms": ["BARROWS_GUTHAN"]},
    "Torag the Corrupted": {"syms": ["BARROWS_TORAG"]},
    "Verac the Defiled": {"syms": ["BARROWS_VERAC"]},
    "Giant Mole": {"syms": ["MOLE_GIANT"]},
}
# Raids and minigames also match NPC and interface symbols inside longer names. Locs are left out on purpose: a loc
# such as Locs.FORESTRY_FIRE is cosmetic and would mark the whole Forestry minigame as added.
SOFT_KINDS = ["Npcs", "Interfaces"]
BOSS_DOCS = ["bosses.md", "boss-lairs.md"]  # linked for bosses implemented in content/combat or content/pvp
SYMBOL_VARIANT_TOKENS = {"single", "singles"}  # trailing symbol tokens that only mark a variant of the same NPC


# ------------------------------------------------------------------------------------------------ small utilities


def norm(text):
    """Lower-case alphanumerics only, "&" read as "and" ("Romeo & Juliet" == RomeoAndJuliet)."""
    return re.sub(r"[^a-z0-9]", "", text.lower().replace("&", "and"))


def name_tokens(name):
    """Word tokens of a Wiki title without articles, parentheses and apostrophes."""
    name = re.sub(r"\(.*?\)", " ", name).replace("'", "").replace("’", "")
    return [t for t in re.split(r"[^a-z0-9]+", name.lower().replace("&", " and ")) if t and t not in ("the", "of", "and", "a")]


def symbol_tokens(symbol):
    """Tokens of a symbol constant without its trailing numeric / single(s) variant markers."""
    toks = symbol.lower().split("_")
    while len(toks) > 1 and (toks[-1].isdigit() or toks[-1] in SYMBOL_VARIANT_TOKENS):
        toks.pop()
    return toks


def slug(name):
    return re.sub(r"[^a-z0-9]+", "-", name.lower().replace("'", "").replace("’", "").replace("&", "and")).strip("-")


def rel(path):
    return os.path.relpath(path, ROOT).replace(os.sep, "/")


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def count_lines(text):
    return text.count("\n") + (0 if text.endswith("\n") or not text else 1)


def wiki_url(title):
    return WIKI_BASE + urllib.parse.quote(title.replace(" ", "_"), safe="_(),:'!")


# ------------------------------------------------------------------------------------------------------ Wiki access


class Wiki:
    """Minimal cached client. Responses are stored under .data/ (gitignored) so reruns are offline."""

    def __init__(self, refresh):
        self.refresh = refresh
        self.cache = {}
        self.dirty = False
        if not refresh and os.path.exists(CACHE_FILE):
            try:
                self.cache = json.loads(read(CACHE_FILE))
            except (OSError, ValueError):
                self.cache = {}

    def save(self):
        if not self.dirty:
            return
        os.makedirs(os.path.dirname(CACHE_FILE), exist_ok=True)
        with open(CACHE_FILE, "w", encoding="utf-8") as f:
            json.dump(self.cache, f, indent=1, sort_keys=True)

    def _api(self, params):
        url = API + "?" + urllib.parse.urlencode({**params, "format": "json"})
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        for attempt in range(6):
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    data = json.load(r)
                time.sleep(WIKI_DELAY_SECONDS)
                return data
            except Exception:  # noqa: BLE001 - retry transient network errors
                time.sleep(2 ** attempt)
        raise RuntimeError("wiki request failed: " + url)

    def _memo(self, key, fetch):
        if key not in self.cache:
            self.cache[key] = fetch()
            self.dirty = True
        return self.cache[key]

    def wikitext(self, page):
        return self._memo("wikitext:" + page, lambda: self._api(
            {"action": "parse", "page": page, "prop": "wikitext", "redirects": 1})["parse"]["wikitext"]["*"])

    def rendered(self, page):
        return self._memo("html:" + page, lambda: self._api(
            {"action": "parse", "page": page, "prop": "text", "redirects": 1})["parse"]["text"]["*"])

    def category(self, cat):
        return self._memo("category:" + cat, lambda: [m["title"] for m in self._api(
            {"action": "query", "list": "categorymembers", "cmtitle": cat, "cmlimit": "500"})["query"]["categorymembers"]])

    def thumbnails(self, titles):
        """Article thumbnails, {title: url}. Titles without an image are simply absent."""
        missing = [t for t in titles if "thumb:" + t not in self.cache]
        for i in range(0, len(missing), IMAGE_BATCH):
            batch = missing[i:i + IMAGE_BATCH]
            data = self._api({"action": "query", "titles": "|".join(batch), "prop": "pageimages", "piprop": "thumbnail",
                              "pithumbsize": str(THUMB_PX), "redirects": 1})["query"]
            alias = {}
            for key in ("normalized", "redirects"):
                for m in data.get(key, []):
                    alias[m["from"]] = m["to"]
            found = {p.get("title"): p["thumbnail"]["source"] for p in data.get("pages", {}).values() if "thumbnail" in p}
            for t in batch:
                target = t
                while target in alias:
                    target = alias[target]
                self.cache["thumb:" + t] = found.get(target, "")
            self.dirty = True
        return {t: self.cache["thumb:" + t] for t in titles if self.cache.get("thumb:" + t)}

    def skill_icons(self, skills):
        missing = [s for s in skills if "icon:" + s not in self.cache]
        for i in range(0, len(missing), IMAGE_BATCH):
            batch = missing[i:i + IMAGE_BATCH]
            data = self._api({"action": "query", "titles": "|".join("File:%s icon.png" % s for s in batch),
                              "prop": "imageinfo", "iiprop": "url"})["query"]
            urls = {}
            for p in data.get("pages", {}).values():
                name = re.sub(r" icon\.png$", "", re.sub(r"^File:", "", p.get("title", "")))
                if p.get("imageinfo"):
                    urls[name] = p["imageinfo"][0]["url"]
            for s in batch:
                self.cache["icon:" + s] = urls.get(s, "")
            self.dirty = True
        return {s: self.cache["icon:" + s] for s in skills if self.cache.get("icon:" + s)}


# ------------------------------------------------------------------------------------------------- Wiki feature lists


def table_rows(wikitext):
    """Row chunks of every {| ... |} table in the text."""
    rows = []
    for table in re.findall(r"\{\|.*?\n\|\}", wikitext, re.S):
        rows.extend(table.split("\n|-")[1:])
    return rows


def first_links(row):
    """Every non-file link in the first `|` cell line that has one, as (target, label). One cell may name several
    bosses ("[[Callisto]] &<br/>[[Artio]]")."""
    for line in row.split("\n"):
        if not line.startswith("|") or line.startswith(("|}", "|-", "|+")):
            continue
        found = []
        for m in re.finditer(r"\[\[([^\]|#]+)(?:\|([^\]]*))?\]\]", line):
            target = m.group(1).strip()
            if not target.lower().startswith(("file:", "category:", "image:")):
                found.append((target, (m.group(2) or target).strip()))
        if found:
            return found
    return []


def first_link(row):
    links = first_links(row)
    return links[0] if links else None


def section(text, heading):
    m = re.search(r"^==\s*%s\s*==\s*$" % re.escape(heading), text, re.M)
    if not m:
        return ""
    nxt = re.search(r"^==[^=].*?==\s*$", text[m.end():], re.M)
    return text[m.end():m.end() + nxt.start()] if nxt else text[m.end():]


def uniq(seq):
    seen, out = set(), []
    for x in seq:
        if x not in seen:
            seen.add(x)
            out.append(x)
    return out


def wiki_skills(wiki):
    members = set(wiki.category("Category:Skills"))
    for s in SKILLS:
        if s not in members:
            print("warning: %s is not in Category:Skills, kept from the built-in list" % s, file=sys.stderr)
    return list(SKILLS)


def wiki_bosses(wiki):
    text = section(wiki.wikitext("Boss"), "List of bosses")
    # sub-sections are ===level 3===; `section` stops at the next level 2 heading only
    return uniq(t for r in table_rows(text) for t, _ in first_links(r))


def wiki_raids(wiki):
    return [t for t in wiki.category("Category:Raids") if not t.startswith("Category:") and t != "Raids"]


def wiki_minigames(wiki):
    return uniq(t for r in table_rows(wiki.wikitext("Minigames")) for t, _ in first_links(r))


def wiki_quests(wiki):
    out = []
    for page, kind in (("Quests/Free-to-play", "F2P"), ("Quests/Members", "Members")):
        names = [html.unescape(n) for n in re.findall(r'<tr data-rowid="([^"]+)"', wiki.rendered(page))]
        out.extend((n, kind) for n in uniq(names))
    return out


def wiki_diaries(wiki):
    text = section(wiki.wikitext("Achievement Diary"), "List of diaries")
    out = []
    for row in table_rows(text):
        link = first_link(row)
        if link:
            out.append(link)  # (page, label)
    return uniq(out)


# --------------------------------------------------------------------------------------------------- code scanning


class Code:
    """Every main .kt file of content/ and engine/, plus the symbol references of the behaviour-registering ones."""

    def __init__(self, symbols_dir):
        self.files = []  # dicts: path, module, stem, lines
        self.symbols = {"Npcs": set(), "Locs": set(), "Interfaces": set()}
        self.refs = {"Npcs": {}, "Locs": {}, "Interfaces": {}}  # symbol -> [file dicts]
        self.modules = []
        self._load_symbols(symbols_dir)
        self._scan()

    def _load_symbols(self, d):
        if not os.path.isdir(d):
            sys.exit("symbols not found in %s (run ./gradlew build once, or pass --symbols DIR)" % d)
        for fn in sorted(os.listdir(d)):
            kind = fn.split("$")[0].split(".")[0]
            if kind in self.symbols and fn.endswith(".java"):
                self.symbols[kind].update(re.findall(r"public static final int ([A-Z0-9_]+) =", read(os.path.join(d, fn))))
        if not self.symbols["Npcs"]:
            sys.exit("no Npcs symbols in %s" % d)

    def _scan(self):
        pattern = re.compile(r"\b(Npcs|Locs|Interfaces)\.([A-Z0-9_]+)")
        for top in ("content", "engine"):
            base = os.path.join(ROOT, top)
            for dirpath, dirnames, filenames in os.walk(base):
                dirnames[:] = sorted(d for d in dirnames if d not in ("build", ".git", "test", "integration"))
                if "build.gradle.kts" in filenames:
                    self.modules.append(dirpath)
                for fn in sorted(filenames):
                    path = os.path.join(dirpath, fn)
                    r = rel(path)
                    if not fn.endswith(".kt") or "/src/main/" not in r:
                        continue
                    text = read(path)
                    parts = r.split("/")
                    entry = {"path": r, "module": "/".join(parts[:2]) if top == "content" else parts[0],
                             "stem": fn[:-3], "lines": count_lines(text)}
                    self.files.append(entry)
                    if top == "content" and parts[1] in NOT_IMPLEMENTATION_MODULES:
                        continue
                    if NOT_IMPLEMENTATION_FILE.search(r) or not BEHAVIOUR.search(text):
                        continue
                    for kind, sym in set(pattern.findall(text)):
                        if sym in self.symbols[kind]:
                            self.refs[kind].setdefault(sym, []).append(entry)

    def by_prefix(self, directory, prefix):
        """Files under directory whose stem starts with prefix; a prefix ending in "=" must equal the stem."""
        if prefix.endswith("="):
            return [f for f in self.files if f["path"].startswith(directory + "/") and f["stem"] == prefix[:-1]]
        return [f for f in self.files if f["path"].startswith(directory + "/") and f["stem"].startswith(prefix)]

    def by_name_prefix(self, name, under=None):
        """Files whose normalised stem starts with the normalised name (never the other way round)."""
        n = norm(re.sub(r"^The ", "", name))
        if len(n) < MIN_PREFIX_LEN:
            return []
        return [f for f in self.files if (under is None or f["path"].startswith(under + "/")) and norm(f["stem"]).startswith(n)]

    def symbols_for_name(self, name, kinds, strict):
        """Referenced symbols of the given kinds whose tokens equal the name's tokens.
        strict (bosses): ordered tokens, concatenated tokens, or the same token set. Otherwise the name's tokens may
        also appear as a contiguous run inside a longer symbol (Interfaces.FOSSIL_VOLCANIC_MINE for Volcanic Mine)."""
        want = name_tokens(name)
        if not want or len(norm(name)) < MIN_PREFIX_LEN:
            return []
        joined, hits = "".join(want), []
        for kind in kinds:
            for sym, files in self.refs[kind].items():
                toks = symbol_tokens(sym)
                ok = toks == want or "".join(toks) == joined or sorted(toks) == sorted(want)
                if not ok and not strict:
                    ok = any(toks[i:i + len(want)] == want for i in range(len(toks) - len(want) + 1))
                if ok:
                    hits.append((kind, sym, files))
        return hits

    def symbols_exact(self, names):
        hits = []
        for sym in names:
            for kind in self.refs:
                if sym in self.refs[kind]:
                    hits.append((kind, sym, self.refs[kind][sym]))
        return hits


def uniq_files(files):
    seen, out = set(), []
    for f in files:
        if f["path"] not in seen:
            seen.add(f["path"])
            out.append(f)
    return out


# --------------------------------------------------------------------------------------------- mechanics documents


class Mechanics:
    def __init__(self):
        self.dir = os.path.join(ROOT, "docs", "mechanics")
        self.names = sorted(os.listdir(self.dir)) if os.path.isdir(self.dir) else []

    def find(self, name, explicit=None, prefixes=("", "quest-")):
        if isinstance(explicit, str):
            return [n for n in self.names if re.fullmatch(explicit.replace("*", ".*"), n)]
        if explicit:
            return [n for n in explicit if n in self.names]
        s = slug(re.sub(r"^The ", "", name)) or slug(name)
        for cand in (slug(name), s):
            for p in prefixes:
                for c in (p + cand + ".md", p + "the-" + cand + ".md"):
                    if c in self.names:
                        return [c]
        return []

    def open_count(self, doc):
        text = read(os.path.join(self.dir, doc))
        ids = set(re.findall(r"OPEN-[A-Za-z0-9]+", text))
        bare = sum(1 for line in text.split("\n") if re.search(r"\bOPEN\b(?!-)", line) and not line.startswith("#"))
        return len(ids) + bare


# ------------------------------------------------------------------------------------------------------- features


def make_row(name, files, mechanics, docs, icon, wiki_title, extra=""):
    # the file named after the feature first, then the largest: it is the one the report links to
    files = sorted(uniq_files(files), key=lambda f: (norm(f["stem"]) != norm(name), -f["lines"], f["path"]))
    return {"name": name, "files": files, "added": bool(files), "docs": docs, "icon": icon, "wiki": wiki_title,
            "extra": extra, "opens": sum(mechanics.open_count(d) for d in docs)}


def locate(code, name, kinds, strict, under=None, use_name_files=True):
    """(files, how) for a boss / raid / minigame."""
    alias = ALIASES.get(name, {})
    files = []
    for directory, prefix in alias.get("files", []):
        files.extend(code.by_prefix(directory, prefix))
    if use_name_files and not alias.get("files"):
        files.extend(code.by_name_prefix(name, under))
    hits = code.symbols_exact(alias["syms"]) if alias.get("syms") else code.symbols_for_name(name, kinds, strict)
    for _, _, fs in hits:
        files.extend(fs)
    return uniq_files(files)


def build(wiki, code, mechanics):
    cats = []

    skills = wiki_skills(wiki)
    icons = wiki.skill_icons(skills)
    rows = []
    for s in skills:
        files = []
        for directory, prefix in SKILL_FILES.get(s, [("content/skills", s)]):
            files.extend(code.by_prefix(directory, prefix))
        # "Mining", "Woodcutting" ... are also module-level names elsewhere (engine/ systems)
        if s not in SKILL_FILES:
            files.extend(code.by_name_prefix(s, "engine"))
        docs = mechanics.find(s, SKILL_DOCS.get(s))
        rows.append(make_row(s, files, mechanics, docs, icons.get(s), s))
    cats.append(("Skills", rows))

    bosses = wiki_bosses(wiki)
    thumbs = wiki.thumbnails(bosses)
    rows = []
    for b in bosses:
        files = locate(code, b, ["Npcs"], True, "content", use_name_files=False)
        docs = mechanics.find(b, ALIASES.get(b, {}).get("docs"), prefixes=("",))
        if not docs and any(f["module"] in ("content/combat", "content/pvp") for f in files):
            docs = [d for d in BOSS_DOCS if d in mechanics.names]
        rows.append(make_row(b, files, mechanics, docs, thumbs.get(b), b))
    cats.append(("Bosses", rows))

    raids = wiki_raids(wiki)
    thumbs = wiki.thumbnails(raids)
    rows = [make_row(r, locate(code, r, SOFT_KINDS, False), mechanics,
                     mechanics.find(r, ALIASES.get(r, {}).get("docs")), thumbs.get(r), r) for r in raids]
    cats.append(("Raids", rows))

    minis = wiki_minigames(wiki)
    thumbs = wiki.thumbnails(minis)
    rows = [make_row(m, locate(code, m, SOFT_KINDS, False), mechanics,
                     mechanics.find(m, ALIASES.get(m, {}).get("docs")), thumbs.get(m), m) for m in minis]
    cats.append(("Minigames", rows))

    quests = wiki_quests(wiki)
    thumbs = wiki.thumbnails([q for q, _ in quests])
    rows = []
    for q, kind in quests:
        alias = ALIASES.get(q, {})
        files = []
        for directory, prefix in alias.get("files", []):
            files.extend(code.by_prefix(directory, prefix))
        if not alias.get("files"):
            files.extend(code.by_name_prefix(q, "content/quests"))
        r = make_row(q, files, mechanics, mechanics.find(q, alias.get("docs")), thumbs.get(q), q)
        r["kind"] = kind
        r["extra"] = kind
        rows.append(r)
    cats.append(("Quests", rows))

    diaries = wiki_diaries(wiki)
    thumbs = wiki.thumbnails([p for p, _ in diaries])
    areas, tasks, triggers = diary_data()
    rows = []
    for page, label in diaries:
        area = areas.get(page)
        mod = [f for f in code.files if f["module"] == "content/diaries"]
        files = mod if area is not None and tasks.get(area, 0) else []
        extra = "%d tasks listed, %d with a trigger" % (tasks.get(area, 0), triggers.get(area, 0)) if files else ""
        r = make_row(label, files, mechanics, mechanics.find("achievement-diaries", ["achievement-diaries.md"]),
                     thumbs.get(page), page, extra)
        rows.append(r)
    cats.append(("Achievement diaries", rows))
    return cats


def diary_data():
    base = os.path.join(ROOT, "content", "diaries", "src", "main", "resources", "tp", "content", "diaries")

    def tsv(name):
        path = os.path.join(base, name)
        if not os.path.exists(path):
            return []
        return [line.split("\t") for line in read(path).split("\n")[1:] if line.strip()]

    areas = {cols[3]: int(cols[0]) for cols in tsv("diary-areas.tsv") if len(cols) >= 4}
    tasks, triggers = {}, {}
    for cols in tsv("diary-tasks.tsv"):
        tasks[int(cols[0])] = tasks.get(int(cols[0]), 0) + 1
    for cols in tsv("diary-triggers.tsv"):
        triggers[int(cols[0])] = triggers.get(int(cols[0]), 0) + 1
    return areas, tasks, triggers


# --------------------------------------------------------------------------------------------------------- render


def where(files):
    mods = []
    for f in files:
        if f["module"] not in mods:
            mods.append(f["module"])
    return ", ".join("`%s`" % m for m in mods[:3]) + (" +%d" % (len(mods) - 3) if len(mods) > 3 else "")


def code_note(row):
    files = row["files"]
    if not files:
        return "nothing yet"
    lines = sum(f["lines"] for f in files)
    note = "%s lines in %d file%s, %s" % (format(lines, ","), len(files), "" if len(files) == 1 else "s", where(files))
    return note + (" · " + row["extra"] if row["extra"] else "")


def mech_cell(row):
    if not row["docs"]:
        return ""
    parts = ["[%s](docs/mechanics/%s)" % (d[:-3], d) for d in row["docs"][:3]]
    more = " +%d" % (len(row["docs"]) - 3) if len(row["docs"]) > 3 else ""
    return ", ".join(parts) + more + (" · %d OPEN" % row["opens"] if row["opens"] else " · 0 OPEN")


def feature_cell(row):
    label = row["name"]
    if row["files"]:
        label = "[%s](%s)" % (label, row["files"][0]["path"])
    return ('<img src="%s" height="20" alt=""> ' % row["icon"] if row["icon"] else "") + label


def render_category(title, rows, many):
    done = sum(1 for r in rows if r["added"])
    out = ["### %s <sup>%d/%d</sup>" % (title, done, len(rows)), ""]
    shown = [r for r in rows if r["added"]] if many else rows
    if shown:
        out += ["| | Feature | Code | Mechanics docs | |", "|---|---|---|---|---|"]
        for r in shown:
            out.append("| %s | %s | %s | %s | [wiki](%s) |" % (
                GREEN if r["added"] else RED, feature_cell(r), code_note(r), mech_cell(r), wiki_url(r["wiki"])))
        out.append("")
    missing = [r for r in rows if not r["added"]] if many else []
    if missing:
        out += ["<details>", "<summary>%s <b>%d not added</b></summary>" % (RED, len(missing)), "",
                " · ".join("[%s](%s)" % (r["name"], wiki_url(r["wiki"])) for r in missing), "", "</details>", ""]
    return out


def render_modules(code):
    out = ["### Content modules", "",
           "Every directory under `content/` with a `build.gradle.kts` (main sources only, tests excluded).", "",
           "| Module | Files | Lines |", "|---|---:|---:|"]
    total_f = total_l = 0
    for d in sorted(code.modules):
        r = rel(d)
        if not r.startswith("content/") or r == "content":
            continue
        fs = [f for f in code.files if f["module"] == r]
        total_f += len(fs)
        total_l += sum(f["lines"] for f in fs)
        out.append("| [`%s`](%s) | %d | %s |" % (r, r, len(fs), format(sum(f["lines"] for f in fs), ",")))
    out.append("| **total** | **%d** | **%s** |" % (total_f, format(total_l, ",")))
    out.append("")
    return out


def render(cats, code):
    summary = " · ".join("%s **%d/%d**" % (t, sum(1 for r in rows if r["added"]), len(rows)) for t, rows in cats)
    quests = dict(cats)["Quests"]
    split = ", ".join("%s %d/%d" % (k, sum(1 for r in quests if r["kind"] == k and r["added"]),
                                    sum(1 for r in quests if r["kind"] == k)) for k in ("F2P", "Members"))
    summary += "\n\nQuests split: " + split + "."
    out = [
        "# Content progress", "",
        "_Generated by `python3 -I tools/progress/content_progress.py`; do not edit by hand. The session log is "
        "[docs/PROGRESS.md](docs/PROGRESS.md)._", "",
        "The feature lists come from the OSRS Wiki. A feature counts as added when `content/` or `engine/` holds a file "
        "for it (matched by file name), or when its generated symbols (`tp.symbols.Npcs` / `Interfaces`) are "
        "referenced by a file that registers behaviour for them. Added means there is code, not that it matches the Wiki: "
        "the Mechanics column links the rule document and counts its open questions (`OPEN` items), which tells how "
        "settled the implementation is. Lines are whole files, so a file shared by several features counts for each.", "",
        "%s added · %s not added" % (GREEN, RED), "", summary, "",
        "Only these six lists are scored. Dialogue, shops, drops, items, clue scrolls and similar content have no "
        "category here, so check the module table at the bottom before reading a red as \"nothing exists\".", "",
    ]
    for title, rows in cats:
        out += render_category(title, rows, len(rows) > 30)
    out += render_modules(code)
    return "\n".join(out)


def main(argv):
    refresh = "--refresh" in argv
    opt = lambda flag, default: argv[argv.index(flag) + 1] if flag in argv else default  # noqa: E731
    symbols_dir = opt("--symbols", DEFAULT_SYMBOLS)
    out_path = opt("--out", os.path.join(ROOT, "PROGRESS.md"))
    wiki = Wiki(refresh)
    code = Code(symbols_dir)
    mechanics = Mechanics()
    cats = build(wiki, code, mechanics)
    wiki.save()
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(render(cats, code))
    print(" · ".join("%s %d/%d" % (t, sum(1 for r in rows if r["added"]), len(rows)) for t, rows in cats))


if __name__ == "__main__":
    main(sys.argv[1:])
