#!/usr/bin/env python3
"""
Music area geometry from the OSRS Wiki (docs/mechanics/music.md OPEN-MU1).

The cache's music table names the tracks and groups them into numbered areas, but it doesn't contain tile geometry.
The Wiki has it. Each track page's Infobox Music `map` field holds inline {{Map|x,y|...|plane=|mtype=polygon}} shapes
and/or a shared {{Map:<Area> music}} page, whose {{Music track map|[[[x,y],...]]|plane=}} lists that area's polygons.

Output (PolygonAreas JSON, smallest area first so specific places win over the region around them):
  - name = cache area number (as text) for tracks that share an area in the cache;
  - name = "track:<display name>" for every other track.

Usage (needs network access to oldschool.runescape.wiki; the cache rows come from cache-dump):
  ./gradlew :tools:cache-dump:run --args="dbrows 44" -q > music-rows.txt
  python3 -I tools/wiki-import/music_areas.py music-rows.txt content/gameframe/src/main/resources/tp/content/gameframe/music-areas.json

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


def api(params):
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json"})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except Exception:  # noqa: BLE001 - retry transient network errors
            time.sleep(2 ** attempt)
    raise RuntimeError("wiki request failed: " + url)


def wikitexts(titles):
    """title -> wikitext (follows redirects), 50 per request."""
    out = {}
    titles = list(titles)
    for i in range(0, len(titles), 50):
        d = api({"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main", "redirects": 1,
                 "titles": "|".join(titles[i:i + 50])})
        q = d["query"]
        alias = {}
        for n in q.get("normalized", []) + q.get("redirects", []):
            alias[n["to"]] = alias.get(n["from"], n["from"])
        for page in q["pages"].values():
            revs = page.get("revisions")
            if not revs:
                continue
            text = revs[0]["slots"]["main"]["*"]
            out[page["title"]] = text
            src = page["title"]
            while src in alias:
                src = alias[src]
                out[src] = text
    return out


def category(name):
    titles, cont = [], {}
    while True:
        d = api({"action": "query", "list": "categorymembers", "cmtitle": name, "cmlimit": 500, "cmnamespace": 0, **cont})
        titles += [m["title"] for m in d["query"]["categorymembers"]]
        if "continue" not in d:
            return titles
        cont = d["continue"]


def infobox_field(text, field):
    """Value of `|field = ...` in the first Infobox Music, with nested {{ }} kept intact."""
    m = re.search(r"\|\s*" + field + r"\s*=", text)
    if not m:
        return None
    i, depth, start = m.end(), 0, m.end()
    while i < len(text):
        if text.startswith("{{", i):
            depth += 1
            i += 2
            continue
        if text.startswith("}}", i):
            if depth == 0:
                break
            depth -= 1
            i += 2
            continue
        if text[i] == "|" and depth == 0 or (text[i] == "\n" and depth == 0 and text.startswith("\n|", i)):
            break
        i += 1
    return text[start:i].strip()


def templates(text, name_re):
    """Top-level {{...}} templates whose name matches name_re: list of (name, [params])."""
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
        body = text[i + 2:j - 2]
        parts = split_params(body)
        if parts and re.fullmatch(name_re, parts[0].strip(), re.I):
            out.append((parts[0].strip(), parts[1:]))
        i = j


def split_params(body):
    parts, depth, cur = [], 0, ""
    k = 0
    while k < len(body):
        two = body[k:k + 2]
        if two in ("{{", "[["):
            depth += 1
            cur += two
            k += 2
            continue
        if two in ("}}", "]]") and depth > 0:
            depth -= 1
            cur += two
            k += 2
            continue
        if body[k] == "|" and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += body[k]
        k += 1
    parts.append(cur)
    return parts


def named(params):
    kv = {}
    for p in params:
        if "=" in p and not re.match(r"^\s*-?\d+\s*,", p):
            k, v = p.split("=", 1)
            kv[k.strip()] = v.strip()
    return kv


def planes(kv):
    p = kv.get("plane", "0")
    try:
        return [int(x) for x in re.split(r"[,\s]+", p) if x != ""]
    except ValueError:
        return [0]


def inline_shapes(params):
    """{{Map|x,y|x,y|...|mtype=polygon|plane=}}: polygons (rectangle for 2 points, square for x,y + r)."""
    kv = named(params)
    pts = []
    for p in params:
        m = re.fullmatch(r"\s*(-?\d+)\s*,\s*(-?\d+)\s*", p)
        if m:
            pts.append((int(m.group(1)), int(m.group(2))))
    mtype = kv.get("mtype", "pin").lower()
    if mtype == "polygon" and len(pts) >= 3:
        return [(pts, planes(kv))]
    if mtype in ("rectangle", "polygon") and len(pts) == 2:
        (x1, y1), (x2, y2) = pts
        return [([(x1, y1), (x1, y2), (x2, y2), (x2, y1)], planes(kv))]
    if mtype == "square" and len(pts) == 1 and "r" in kv:
        (x, y), r = pts[0], int(kv["r"])
        return [([(x - r, y - r), (x - r, y + r + 1), (x + r + 1, y + r + 1), (x + r + 1, y - r)], planes(kv))]
    return []


_EXPANDED = {}


def expand(template):
    if template not in _EXPANDED:
        d = api({"action": "expandtemplates", "text": template, "prop": "wikitext"})
        _EXPANDED[template] = d["expandtemplates"]["wikitext"].strip()
    return _EXPANDED[template]


def shared_shapes(text):
    """{{Music track map|...|[[[x,y],...],...]|plane=}} on a Map:<page> (shared area or one track's own map)."""
    out = []
    for _, params in templates(text, r"Music track map"):
        kv = named(params)
        # The polygon list is the positional parameter that starts with "[" (its position varies between pages).
        raw = next((p.strip() for p in params if p.strip().startswith("[")), "")
        # Editors annotate borders with HTML comments and leave trailing commas: strip both before parsing.
        raw = re.sub(r"<!--.*?-->", "", raw, flags=re.S)
        # Named shapes ({{Map polygon|Pharaoh's Tomb}}) expand to a polygon list through the Wiki.
        raw = re.sub(r"\{\{[^{}]*\}\}", lambda t: expand(t.group(0)), raw)
        raw = re.sub(r",\s*\]", "]", raw)
        try:
            data = json.loads(raw)
        except ValueError:
            continue
        # Either one polygon [[x,y],...] or several [[[x,y],...],...].
        polys = data if data and isinstance(data[0][0], list) else [data]
        for poly in polys:
            if len(poly) >= 3:
                out.append(([(int(x), int(y)) for x, y in poly], planes(kv)))
    return out


def area(poly):
    s = 0
    for (x1, y1), (x2, y2) in zip(poly, poly[1:] + poly[:1]):
        s += x1 * y2 - x2 * y1
    return abs(s) / 2


def cache_tracks(path):
    """Cache music rows: display name -> area number (0 = none), hidden flag."""
    tracks = {}
    for line in open(path, encoding="utf-8"):
        disp = re.search(r"\b1=types=\[36\] values=\[\(\"(.*?)\"\)\]", line)
        if not disp:
            continue
        area_m = re.search(r"\b7=types=\[0\] values=\[\((\d+)\)\]", line)
        hidden = re.search(r"\b9=types=\[1\] values=\[\(1\)\]", line) is not None
        holiday = re.search(r"\b10=types=\[0\] values=\[\(\d+\)\]", line) is not None  # holiday id (1..4)
        # Hidden and holiday-event tracks never play as area music (their wiki maps mark event locations).
        tracks[disp.group(1)] = (int(area_m.group(1)) if area_m else 0, hidden or holiday)
    return tracks


def main(rows_path, out_path):
    cache = cache_tracks(rows_path)
    pages = category("Category:Music tracks")
    texts = wikitexts(pages)
    shared_needed, per_track = set(), {}
    for title, text in texts.items():
        if "Infobox Music" not in text:
            continue
        name = (infobox_field(text, "name") or title).strip()
        # Page names disambiguate with " (music track)"; the cache's display name doesn't.
        name = re.sub(r"\s*\((music track)\)$", "", name, flags=re.I)
        field = infobox_field(text, "map") or ""
        refs = [n for n, _ in templates(field, r"Map:.+")]
        shapes = [s for _, params in templates(field, r"Map") for s in inline_shapes(params)]
        shared_needed.update(refs)
        per_track[name] = (refs, shapes)
    shared = {k: shared_shapes(v) for k, v in wikitexts(sorted(shared_needed)).items()}

    by_name, unmatched, empty = {}, [], []
    for name, (refs, shapes) in per_track.items():
        if name not in cache:
            unmatched.append(name)
            continue
        area_no, excluded = cache[name]
        if excluded:
            continue
        polys = list(shapes) + [s for r in refs for s in shared.get(r, [])]
        if not polys:
            empty.append(name)
            continue
        key = str(area_no) if area_no else "track:" + name
        by_name.setdefault(key, []).extend(polys)

    out = []
    for key, polys in by_name.items():
        # One entry per plane set, so the PolygonAreas "levels" stay exact.
        groups = {}
        for poly, pl in polys:
            groups.setdefault(tuple(sorted(set(pl))), []).append(poly)
        for levels, ps in groups.items():
            uniq = {tuple(p) for p in ps}
            out.append({
                "name": key,
                "levels": list(levels),
                "polygons": [{"vertices": [{"x": x, "z": y} for x, y in p]} for p in sorted(uniq)],
                "_size": sum(area(list(p)) for p in uniq),
            })
    out.sort(key=lambda a: a["_size"])
    for a in out:
        del a["_size"]
    json.dump(out, open(out_path, "w"), separators=(",", ":"))
    print(f"areas={len(out)} tracks_with_geometry={sum(1 for k in by_name)} wiki_tracks={len(per_track)} "
          f"cache_tracks={len(cache)} unmatched={len(unmatched)} no_geometry={len(empty)}")
    print("unmatched sample:", unmatched[:15])
    print("no geometry sample:", empty[:15])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
