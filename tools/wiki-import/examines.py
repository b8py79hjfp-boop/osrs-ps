#!/usr/bin/env python3
"""
Examine texts from the OSRS Wiki (docs/mechanics/examine.md).

OSRS keeps examine texts server-side: the cache has none for items, NPCs or scenery. The Wiki's {{Infobox Item}},
{{Infobox Monster}}, {{Infobox NPC}} and {{Infobox Scenery}} carry them as `examine` (or `examineN` per version) with the
config ids as `id` (`idN`). Every page using one of those templates is read; each version's ids get that version's
examine (falling back to the unversioned field).

Output: tab-separated `kind id text` rows (kind obj / npc / loc), sorted; `{{...}}`, links and markup are flattened.

Usage (needs network access to oldschool.runescape.wiki):
  python3 -I tools/wiki-import/examines.py content/gameframe/src/main/resources/tp/content/gameframe/examines.tsv

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
TEMPLATES = {
    "Template:Infobox Item": "obj",
    "Template:Infobox Monster": "npc",
    "Template:Infobox NPC": "npc",
    "Template:Infobox Scenery": "loc",
}


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


def pages_using(template):
    titles, cont = [], {}
    while True:
        d = api({"action": "query", "list": "embeddedin", "eititle": template, "einamespace": 0, "eilimit": 500, **cont})
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


def template_bodies(text, name):
    """Bodies of every {{<name> ...}} (brace-balanced)."""
    out, start = [], 0
    while True:
        i = text.find("{{" + name, start)
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


def fields(body):
    """Top-level `|key = value` pairs (nested templates and links kept intact)."""
    out, depth, cur = {}, 0, ""
    parts = []
    i = 0
    while i < len(body):
        two = body[i:i + 2]
        if two in ("{{", "[["):
            depth += 1
            cur += two
            i += 2
            continue
        if two in ("}}", "]]"):
            depth -= 1
            cur += two
            i += 2
            continue
        if body[i] == "|" and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += body[i]
        i += 1
    parts.append(cur)
    for p in parts:
        if "=" in p:
            k, v = p.split("=", 1)
            out[k.strip().lower()] = v.strip()
    return out


def clean(v):
    v = re.sub(r"<!--.*?-->", "", v, flags=re.S)
    v = re.sub(r"<ref[^>]*>.*?</ref>|<ref[^>]*/>", "", v, flags=re.S)
    v = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", v)
    v = re.sub(r"\{\{(?:sic|nowrap)\|([^}]*)\}\}", r"\1", v, flags=re.I)
    v = re.sub(r"\{\{[^}]*\}\}", "", v)
    v = re.sub(r"'''?", "", v)
    v = re.sub(r"<br\s*/?>", " ", v)
    v = re.sub(r"<[^>]+>", "", v)
    return re.sub(r"\s+", " ", v).strip()


def ids(v):
    return [int(x) for x in re.findall(r"\d+", re.sub(r"<!--.*?-->", "", v or "", flags=re.S))]


def main():
    dest = sys.argv[1]
    rows = {}
    for template, kind in TEMPLATES.items():
        name = template.split(":", 1)[1]
        titles = pages_using(template)
        print(f"{template}: {len(titles)} pages", file=sys.stderr)
        texts = wikitexts(titles)
        for text in texts.values():
            for body in template_bodies(text, name):
                f = fields(body)
                versions = {k[2:] for k in f if re.fullmatch(r"id\d+", k)}
                for v in sorted(versions) or [""]:
                    examine = clean(f.get("examine" + v) or f.get("examine", ""))
                    if not examine:
                        continue
                    for i in ids(f.get("id" + v)):
                        rows.setdefault((kind, i), examine)
    with open(dest, "w") as out:
        out.write("kind\tid\ttext\n")
        for (kind, i), text in sorted(rows.items()):
            out.write(f"{kind}\t{i}\t{text}\n")
    counts = {k: sum(1 for (kk, _) in rows if kk == k) for k in ("obj", "npc", "loc")}
    print(f"wrote {len(rows)} examines {counts} to {dest}", file=sys.stderr)


if __name__ == "__main__":
    main()
