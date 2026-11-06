#!/usr/bin/env python3
"""
NPC conversations from the OSRS Wiki `Transcript:` namespace (docs/mechanics/npc-dialogue.md).

Every `Transcript:<name>` page that starts with `{{Transcript|npc}}` is parsed: the "Standard dialogue" section (else the
first plain section) becomes a graph of nodes (npc line, player line, message, choice, end). Page structure follows the
Wiki's transcript templates:

  * '''Hans:''' text            an NPC line ('''Player:''' is the player)
  * {{tselect|title}} + {{topt|option}}   an option menu; the option's reply is the nested `**` block
  * {{tcond|condition}}         a conditional branch (conditions are not modelled, see below)
  * {{tbox|[pic=..|]text}}, {{mes|text}}  a message box
  * {{tact|end}} / {{tact|opens=..}} / {{tact|receives=..}} / {{qact|..}}   actions
  * {{trandom}} + {{topt|Dialogue n}}     a random pick between replies

Conditions are NOT modelled. The branch chosen is the one whose condition reads negative ("not", "no", "hasn't",
"before", ...: the player who has not started the related content), else the first. A lone positive condition is
dropped. Options with `cond=` are dropped. Actions the server cannot execute (shop opens, item gifts, free-text
actions, text input) end the conversation at that point. Menus keep at most five options.

Each transcript's NPC page (`Infobox NPC` / `Infobox Monster` `id`, `idN`; redirects followed) gives its NPC ids.

Output: `content/world/.../npc-dialogue.tsv`. A `tree <n> <ids>` row starts a tree; its node rows follow, indexed from 0
in order (node 0 is the entry). Rows: `N <next> <text>` (NPC line), `P <next> <text>` (player line), `M <next> <text>`
(message box), `C <title> <count> <text> <target> ...` (menu), `E` (end). `next` / `target` is a node index, -1 = end.

Usage (needs network access to oldschool.runescape.wiki):
  python3 -I tools/wiki-import/npc_dialogue.py content/world/src/main/resources/tp/content/world/npc-dialogue.tsv

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
TRANSCRIPT_NS = 120
MAX_OPTIONS = 5
INFOBOXES = ("Infobox NPC", "Infobox Monster")
NEGATIVE = re.compile(
    r"\b(not|no|hasn'?t|isn'?t|doesn'?t|haven'?t|didn'?t|without|before|never|else|otherwise|unstarted|none)\b", re.I)
JUMP = re.compile(r"^(above|below|initial|previous\d*|continues?)\b")
SKIP_SECTIONS = re.compile(r"historical|overhead|treasure|clue|pre-|pickpocket|journal|random event", re.I)

stats = {}


def count(key, n=1):
    stats[key] = stats.get(key, 0) + n


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


def transcript_titles():
    titles, cont = [], {}
    while True:
        d = api({"action": "query", "list": "allpages", "apnamespace": TRANSCRIPT_NS, "aplimit": 500,
                 "apfilterredir": "nonredirects", **cont})
        titles += [p["title"] for p in d["query"]["allpages"]]
        if "continue" not in d:
            return titles
        cont = d["continue"]


def wikitexts(titles, redirects=False):
    """title -> wikitext; with `redirects`, also returns requested title -> resolved title."""
    out, resolved = {}, {}
    for i in range(0, len(titles), 50):
        params = {"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
                  "titles": "|".join(titles[i:i + 50])}
        if redirects:
            params["redirects"] = 1
        d = api(params)
        for r in d["query"].get("redirects", []):
            resolved[r["from"]] = r["to"]
        for page in d["query"]["pages"].values():
            revs = page.get("revisions")
            if revs:
                out[page["title"]] = revs[0]["slots"]["main"]["*"]
        time.sleep(0.2)
    return out, resolved


def split_args(s):
    """Split on top-level `|` (nested templates and links kept intact)."""
    parts, depth, cur, i = [], 0, "", 0
    while i < len(s):
        two = s[i:i + 2]
        if two in ("{{", "[["):
            depth += 1
            cur += two
            i += 2
        elif two in ("}}", "]]"):
            depth -= 1
            cur += two
            i += 2
        elif s[i] == "|" and depth == 0:
            parts.append(cur)
            cur = ""
            i += 1
        else:
            cur += s[i]
            i += 1
    parts.append(cur)
    return parts


KEEP_FIRST = {"overhead", "sic", "nowrap", "small", "scp", "plink", "plinkt", "gp", "coins", "item"}


def clean(v):
    """Plain chat text from wikitext: links, formatting and inline templates flattened."""
    v = re.sub(r"<!--.*?-->", "", v, flags=re.S)
    v = re.sub(r"<ref[^>]*>.*?</ref>|<ref[^>]*/>", "", v, flags=re.S)
    while True:
        m = re.search(r"\{\{([^{}]*)\}\}", v)
        if not m:
            break
        a = [x.strip() for x in m.group(1).split("|")]
        name = a[0].lower()
        if name in ("colour", "color") and len(a) > 2:
            rep = a[-1]
        elif name in KEEP_FIRST and len(a) > 1:
            rep = a[1]
        else:
            rep = ""
        v = v[:m.start()] + rep + v[m.end():]
    v = re.sub(r"\[\[(?:File|Image):[^\]]*\]\]", "", v, flags=re.I)
    v = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", v)
    v = re.sub(r"'''?", "", v)
    v = re.sub(r"<br\s*/?>", " ", v, flags=re.I)
    v = re.sub(r"<[^>]+>", "", v)
    v = v.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", '"').replace("&#39;", "'")
    return re.sub(r"\s+", " ", v).strip()


def is_transcript(text):
    return re.search(r"\{\{\s*transcript\s*\|\s*npc", text, re.I) is not None


def sections(text):
    """(level, heading, [lines]) in page order; level 0 is the lead."""
    out = [(0, "", [])]
    for line in text.split("\n"):
        m = re.match(r"^(={2,6})\s*(.*?)\s*=+\s*$", line)
        if m:
            out.append((len(m.group(1)), m.group(2), []))
        else:
            out[-1][2].append(line)
    return out


def has_dialogue(lines):
    return any(re.match(r"^\*+\s*('''|\{\{\s*(topt|tselect|tbox|mes)\b)", l) for l in lines)


def pick_branch(headings):
    """Index of the default heading among alternatives: negative wording first, then initial/first, else 0."""
    for i, h in enumerate(headings):
        if NEGATIVE.search(h):
            return i
    for i, h in enumerate(headings):
        if re.search(r"initial|first|standard|default", h, re.I):
            return i
    return 0


def select_lines(text):
    """The dialogue lines of the default section, or None."""
    secs = sections(text)
    tops = [i for i, s in enumerate(secs) if s[0] == 2]
    if not tops:
        return secs[0][2] if has_dialogue(secs[0][2]) else None
    standard = [i for i in tops if secs[i][1].lower().startswith("standard dialogue") and "pre-" not in secs[i][1].lower()]
    plain = [i for i in tops if not SKIP_SECTIONS.search(secs[i][1])]
    if standard:
        start = standard[0]
    elif plain:
        start = plain[0]
    else:
        return None
    count("sections_dropped", len(tops) - 1)
    return descend(secs, start)


def descend(secs, i):
    """Lines of section i; if it has none, choose among its direct subsections."""
    level = secs[i][0]
    j = i + 1
    while j < len(secs) and secs[j][0] > level:
        j += 1
    children = [k for k in range(i + 1, j) if secs[k][0] == level + 1]
    if has_dialogue(secs[i][2]):
        count("sections_dropped", len(children))
        return secs[i][2]
    if not children:
        return None
    pick = pick_branch([secs[k][1] for k in children])
    count("sections_dropped", len(children) - 1)
    return descend(secs, children[pick])


def classify(line):
    """(depth, kind, args, text) for a `*` line, or None."""
    m = re.match(r"^(\*+)\s*(.*)$", line)
    if not m:
        return None
    depth, body = len(m.group(1)), m.group(2).strip()
    t = re.match(r"^\{\{\s*([A-Za-z ]+?)\s*(?:\|(.*))?\}\}\s*$", body, re.S)
    if t and not body.startswith("{{overhead"):
        name = t.group(1).lower()
        args = split_args(t.group(2)) if t.group(2) is not None else []
        if name in ("tselect", "topt", "top", "tcond", "tbox", "mes", "tact", "qact", "trandom", "tmissing", "tinput",
                    "transcript missing"):
            return depth, "topt" if name == "top" else name.replace(" ", "_"), args, ""
        return depth, "other", [], ""
    s = re.match(r"^'''(.+?)'''\s*:?\s*(.*)$", body, re.S)
    if s:
        who = s.group(1).strip().rstrip(":").strip()
        text = clean(s.group(2))
        return depth, ("player" if who.lower() == "player" else "npc"), [who], text
    return depth, "other", [], ""


def fill(text):
    """`[a/b]` takes a, `[player name]` becomes {name}; any other `[placeholder]` makes the line unusable (None)."""
    text = re.sub(r"\[(?:player'?s? ?name|name|username)\]", "{name}", text, flags=re.I)
    text = re.sub(r"\[([^\]/\[]+)/[^\]\[]*\]", r"\1", text)
    return None if re.search(r"\[[^\]]+\]", text) else text


def arg_text(args):
    """Positional text of a template's args (named `cond=`, `pic=` ... skipped)."""
    pos = [a for a in args if not re.match(r"^\s*(cond|pic|title|id|link)\s*=", a)]
    return clean(pos[-1]) if pos else ""


def arg_named(args, key):
    for a in args:
        m = re.match(r"^\s*" + key + r"\s*=(.*)$", a, re.S)
        if m:
            return clean(m.group(1))
    return ""


class Parser:
    def __init__(self, items):
        self.items = items

    def children(self, i, depth):
        return self.seq(i, depth + 1)

    def skip_deeper(self, i, depth):
        while i < len(self.items) and self.items[i][0] > depth:
            i += 1
        return i

    def seq(self, i, depth):
        """Statements at `depth` from item i; returns (stmts, next index). Stops after End or a menu."""
        items, stmts, done = self.items, [], False
        while i < len(items) and items[i][0] >= depth:
            d, kind, args, text = items[i]
            if d > depth:
                i += 1
                continue
            if done:
                i = self.skip_deeper(i + 1, depth)
                continue
            if kind in ("npc", "player"):
                text = fill(text)
                if text is None:
                    count("lines_dropped_placeholder")
                    stmts.append(("end",))
                    done = True
                elif text:
                    stmts.append((kind, text))
                i = self.skip_deeper(i + 1, depth)
            elif kind in ("mes", "tbox"):
                t = arg_text(args)
                if t:
                    stmts.append(("msg", t))
                i = self.skip_deeper(i + 1, depth)
            elif kind == "tact":
                a = " ".join(args).strip().lower()
                if a == "end":
                    count("actions_end")
                elif re.match(r"^(initial|previous\d*|above)\b", a):
                    # Back to the first menu (initial) or the menu this branch came from (previous, above).
                    count("actions_jump")
                    stmts.append(("jump", "initial" if a.startswith("initial") else "previous"))
                    done = True
                    i = self.skip_deeper(i + 1, depth)
                    continue
                elif JUMP.match(a):
                    count("actions_dropped_jump")
                elif a.startswith("opens"):
                    count("actions_dropped_opens")
                elif a.startswith(("receives", "gives")):
                    count("actions_dropped_item")
                else:
                    count("actions_dropped_other")
                stmts.append(("end",))
                done = True
                i = self.skip_deeper(i + 1, depth)
            elif kind == "qact":
                count("qact_skipped")
                i = self.skip_deeper(i + 1, depth)
            elif kind in ("tmissing", "tinput", "transcript_missing"):
                count("actions_dropped_" + kind)
                stmts.append(("end",))
                done = True
                i = self.skip_deeper(i + 1, depth)
            elif kind in ("tselect", "topt"):
                i, menu = self.menu(i, depth)
                if menu:
                    stmts.extend(menu)
                    done = True
            elif kind == "tcond":
                i, branch = self.cond(i, depth)
                stmts.extend(branch)
                if branch and branch[-1][0] in ("end", "menu"):
                    done = True
            elif kind == "trandom":
                i, branch = self.rand(i, depth)
                stmts.extend(branch)
                if branch and branch[-1][0] in ("end", "menu"):
                    done = True
            else:
                i = self.skip_deeper(i + 1, depth)
        return stmts, i

    def menu(self, i, depth):
        items = self.items
        title = "Select an Option"
        if items[i][1] == "tselect":
            title = arg_text(items[i][2]) or title
            i += 1
        options = []
        while i < len(items) and items[i][0] == depth and items[i][1] == "topt":
            args = items[i][2]
            text = arg_text(args)
            body, i = self.seq(i + 1, depth + 1)
            if arg_named(args, "cond"):
                count("options_dropped_cond")
                continue
            if text:
                options.append((text, body))
        if len(options) > MAX_OPTIONS:
            count("options_dropped_over5", len(options) - MAX_OPTIONS)
            options = options[:MAX_OPTIONS]
        if not options:
            return i, [("end",)]
        if len(options) == 1:
            return i, options[0][1] or [("end",)]
        return i, [("menu", title, options)]

    def cond(self, i, depth):
        items, group = self.items, []
        while i < len(items) and items[i][0] == depth and items[i][1] == "tcond":
            text = arg_text(items[i][2])
            body, i = self.seq(i + 1, depth + 1)
            group.append((text, body))
        count("branches_seen", len(group))
        pick = pick_branch([g[0] for g in group])
        chosen = group[pick]
        if len(group) == 1 and not NEGATIVE.search(chosen[0]):
            count("branches_dropped")
            return i, []
        count("branches_dropped", len(group) - 1)
        return i, chosen[1]

    def rand(self, i, depth):
        items = self.items
        i += 1
        first, n = None, 0
        while i < len(items) and items[i][0] == depth and items[i][1] == "topt":
            body, i = self.seq(i + 1, depth + 1)
            n += 1
            if first is None:
                first = body
        count("random_dropped", max(n - 1, 0))
        return i, first or []


def lower(stmts, nodes):
    """Emit nodes for a statement list; returns the entry index (-1 = nothing). Jumps become `J` placeholders."""
    entry = -1
    for st in reversed(stmts):
        if st[0] == "end":
            entry = add(nodes, ("E",))
        elif st[0] == "jump":
            entry = add(nodes, ("J", st[1]))
        elif st[0] == "menu":
            start = len(nodes)
            opts = [(text, lower(body, nodes)) for text, body in st[2]]
            entry = add(nodes, ("C", st[1], opts))
            # "previous" / "above" inside this menu's options return to this menu (the innermost one).
            for n in range(start, entry):
                if nodes[n] == ("J", "previous"):
                    nodes[n] = ("R", entry)
        else:
            entry = add(nodes, ({"npc": "N", "player": "P", "msg": "M"}[st[0]], entry, st[1]))
    return entry


def resolve_jumps(raw, entry):
    """Points every remaining jump at the conversation's first menu (or ends it when there is none)."""
    first, n, seen = -1, entry, set()
    while 0 <= n < len(raw) and n not in seen:
        seen.add(n)
        if raw[n][0] == "C":
            first = n
            break
        n = raw[n][1] if raw[n][0] in "NPM" else -1
    for i, node in enumerate(raw):
        if node[0] == "J":
            raw[i] = ("R", first)


def follow(raw, t):
    """A node index with jump links (`R`) followed."""
    hops = 0
    while 0 <= t < len(raw) and raw[t][0] == "R" and hops < len(raw):
        t = raw[t][1]
        hops += 1
    return t if 0 <= t < len(raw) and raw[t][0] != "R" else -1


def add(nodes, node):
    nodes.append(node)
    return len(nodes) - 1


def serialize(stmts):
    """Rows of one tree, nodes renumbered so the entry is index 0."""
    raw = []
    entry = lower(stmts, raw)
    if entry < 0:
        return None
    resolve_jumps(raw, entry)
    entry = follow(raw, entry)
    if entry < 0:
        return None
    order, seen = [], {}
    stack = [entry]
    while stack:
        n = stack.pop()
        if n < 0 or n in seen:
            continue
        seen[n] = len(order)
        order.append(n)
        node = raw[n]
        if node[0] == "C":
            stack.extend(follow(raw, t) for _, t in reversed(node[2]))
        elif node[0] in "NPM":
            stack.append(follow(raw, node[1]))
    rows = []
    for n in order:
        node = raw[n]
        remap = lambda t: seen.get(follow(raw, t), -1)  # noqa: E731
        if node[0] == "E":
            rows.append("E")
        elif node[0] == "C":
            cells = "\t".join(f"{text}\t{remap(t)}" for text, t in node[2])
            rows.append(f"C\t{node[1]}\t{len(node[2])}\t{cells}")
        else:
            rows.append(f"{node[0]}\t{remap(node[1])}\t{node[2]}")
    return rows


def template_bodies(text, name):
    out, start = [], 0
    while True:
        i = text.lower().find("{{" + name.lower(), start)
        if i < 0:
            return out
        depth, j = 0, i
        while j < len(text) - 1:
            if text[j:j + 2] == "{{":
                depth += 1
                j += 2
            elif text[j:j + 2] == "}}":
                depth -= 1
                j += 2
                if depth == 0:
                    break
            else:
                j += 1
        out.append(text[i + 2 + len(name):j - 2])
        start = j


def infobox_ids(text):
    ids = set()
    for name in INFOBOXES:
        for body in template_bodies(text, name):
            for p in split_args(body):
                if "=" not in p:
                    continue
                k, v = p.split("=", 1)
                if re.fullmatch(r"\s*id\d*\s*", k):
                    ids.update(int(x) for x in re.findall(r"\d+", re.sub(r"<!--.*?-->", "", v, flags=re.S)))
    return ids


def main():
    dest = sys.argv[1]
    titles = transcript_titles()
    print(f"{len(titles)} transcript pages", file=sys.stderr)
    texts, _ = wikitexts(titles)
    npc = {t: x for t, x in texts.items() if is_transcript(x)}
    names = [t.split(":", 1)[1] for t in npc]
    pages, resolved = wikitexts(names, redirects=True)
    print(f"{len(npc)} NPC transcripts, {len(pages)} NPC pages", file=sys.stderr)
    trees, no_ids, no_dialogue = {}, [], []
    for title, text in sorted(npc.items()):
        name = title.split(":", 1)[1]
        ids = infobox_ids(pages.get(resolved.get(name, name), ""))
        if not ids:
            no_ids.append(name)
            continue
        lines = select_lines(text)
        if lines is None:
            no_dialogue.append(name)
            continue
        items = [c for c in (classify(l) for l in lines if l.startswith("*")) if c]
        if not items:
            no_dialogue.append(name)
            continue
        base = min(i[0] for i in items)
        stmts, _ = Parser(items).seq(0, base)
        rows = serialize(stmts)
        if not rows or all(r == "E" for r in rows):
            no_dialogue.append(name)
            continue
        key = "\n".join(rows)
        entry = trees.setdefault(key, set())
        entry.update(ids)
    claimed, out = set(), []
    for key, ids in sorted(trees.items(), key=lambda kv: min(kv[1])):
        ids = sorted(i for i in ids if i not in claimed)
        if not ids:
            continue
        claimed.update(ids)
        out.append((ids, key))
    with open(dest, "w") as f:
        f.write("# tree <n> <npc ids>, then nodes: N/P/M next text; C title count (text target)...; E. tools/wiki-import/npc_dialogue.py\n")
        for n, (ids, key) in enumerate(out):
            f.write(f"tree\t{n}\t{','.join(map(str, ids))}\n{key}\n")
    print(f"transcripts without NPC ids: {len(no_ids)}; without usable dialogue: {len(no_dialogue)}", file=sys.stderr)
    print(f"trees {len(out)}, npc ids covered {len(claimed)}", file=sys.stderr)
    for k in sorted(stats):
        print(f"  {k}: {stats[k]}", file=sys.stderr)
    print("no ids sample:", no_ids[:15], file=sys.stderr)


if __name__ == "__main__":
    main()
