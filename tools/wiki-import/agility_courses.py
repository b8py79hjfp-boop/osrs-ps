#!/usr/bin/env python3
"""
Agility course obstacles from the OSRS Wiki (docs/mechanics/agility.md).

Each course page lists its obstacles in order (the "Experience per obstacle" table); every obstacle links to its own
page whose {{Infobox Scenery}} carries the loc id(s) and the option text, and whose {{Agility info}} carries the level
and experience. Locs that appear more than once on a course ("Tightrope 1" / "Tightrope 2", "Gap #3") are picked by the
version number in the table's link. The loc's placements (x, z, level, shape, angle) come from `cache-dump map` squares.

Output (tab-separated, header first), one row per obstacle in course order:
  course, order, name, loc (one row per id and placement), op (the option text), level, xp, x, z, plane, shape, angle (-1 when the cache has no placement)
The completion bonus of the Gnome Stronghold course (its table has an "N/A" row) is written as a row named
"Course Completion Bonus" with loc -1.

Usage (needs network access to oldschool.runescape.wiki; the map squares from cache-dump):
  ./gradlew -q :tools:cache-dump:run --args="map 38 53" > map_38_53.txt   (and the other squares of the courses)
  python3 -I tools/wiki-import/agility_courses.py out.tsv map_*.txt

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
COURSES = [
    "Gnome Stronghold Agility Course",
    "Draynor Village Rooftop Course",
    "Al Kharid Rooftop Course",
    "Varrock Rooftop Course",
    "Canifis Rooftop Course",
    "Falador Rooftop Course",
    "Seers' Village Rooftop Course",
    "Pollnivneach Rooftop Course",
    "Rellekka Rooftop Course",
    "Ardougne Rooftop Course",
]


def wikitext(page):
    url = API + "?" + urllib.parse.urlencode({"action": "parse", "page": page, "prop": "wikitext", "redirects": 1, "format": "json"})
    for attempt in range(6):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=60) as r:
                return json.load(r)["parse"]["wikitext"]["*"]
        except Exception:  # noqa: BLE001 - retry transient network errors
            time.sleep(2 ** attempt)
    raise RuntimeError("wiki request failed: " + page)


def placements(paths):
    out = {}
    for path in paths:
        for line in open(path, encoding="utf-8"):
            m = re.match(r'\s+(\d+) ".*?" (\d+) (\d+) (\d+) (\d+) (\d+)$', line)
            if m:
                out.setdefault(int(m.group(1)), []).append(tuple(int(g) for g in m.groups()[1:]))
    return out


def field(text, key):
    m = re.search(r"^\|\s*" + re.escape(key) + r"\s*=\s*(.*?)\s*$", text, re.M)
    return m.group(1) if m else None


def main():
    placed = placements(sys.argv[2:])
    rows = ["course\torder\tname\tloc\top\tlevel\txp\tx\tz\tplane\tshape\tangle"]
    for course in COURSES:
        text = wikitext(course)
        # the obstacle table follows "==Obstacles==" (or "==Experience==" on Ardougne, the first table captioned "Course
        # Breakdown" on Seers' Village)
        start = max(text.find("==Obstacles=="), text.find("==Experience=="), text.find("|+Course Breakdown"))
        table = text[start:]
        table = table[:table.find("|}")]
        order = 0
        seen = {}
        for line in table.split("\n|-"):
            link = None
            for m in re.finditer(r"\[\[([^\]|]+?)(?:#([^\]|]*))?\|([^\]]+)\]\]", line):
                if not m.group(1).startswith(("File:", "X:")):
                    link = m
                    break
            xp = re.search(r"\{\{\+=\|xp\|([\d.]+)", line)
            if link is None and "Completion Bonus" in line:
                bonus = re.search(r"\{\{\+=\|xp\|([\d.]+)", line)
                rows.append("\t".join([course, str(order + 1), "Course Completion Bonus", "-1", "-", "1", bonus.group(1), "-1", "-1", "-1", "-1", "-1"]))
                continue
            if link is None or xp is None:
                continue
            order += 1
            page = link.group(1).replace("_", " ")
            name = link.group(3).strip()
            anchor = link.group(2) or ""
            version = re.search(r"(\d+)\s*$", name) or re.fullmatch(r"(\d+)", anchor) or re.search(r"(\d+)$", anchor)
            if page not in seen:
                seen[page] = wikitext(page)
                time.sleep(0.2)
            obstacle = seen[page]
            infobox = obstacle[obstacle.find("{{Infobox Scenery"):obstacle.find("\n}}", obstacle.find("{{Infobox Scenery"))]
            idx = version.group(1) if version else ""
            loc = field(infobox, "id" + idx) or field(infobox, "id") or field(infobox, "id1") or "-1"
            op = field(infobox, "options") or "-"
            info = obstacle[obstacle.find("{{Agility info"):]
            info = info[:info.find("\n}}")]
            level = field(info, "level") or "1"
            for single in [part.strip() for part in loc.split(",")]:
                where = placed.get(int(single), [(-1, -1, -1, -1, -1)]) if single.isdigit() else [(-1, -1, -1, -1, -1)]
                for x, z, plane, shape, angle in where:
                    rows.append("\t".join([course, str(order), name, single, op.split(",")[0].strip(), level, xp.group(1), str(x), str(z), str(plane), str(shape), str(angle)]))
    with open(sys.argv[1], "w", encoding="utf-8") as fh:
        fh.write("\n".join(rows) + "\n")


if __name__ == "__main__":
    main()
