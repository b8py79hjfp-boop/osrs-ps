#!/usr/bin/env python3
"""
Sailing server-only data from the OSRS Wiki (docs/plans/sailing.md 4.3, 6.5; docs/mechanics/sailing-*.md).

The rev-242 cache is a client cache: the Sailing db tables lost their server-only columns (port task XP, dock
coordinates, charting locations ...). Everything here comes from machine-readable Wiki templates and tables:

  Courier tasks      {{CourierTaskLine|level|xp|noticeBoard|cargoLocation|destination|item|qty|transcript|taskId}} (439) plus the
                     two quest tasks of the first table (ids 599 and 600, with ledger-table map pins)
  Bounty tasks       {{BountyTaskLine|level|xp|noticeBoard|monster|item|qty|rarity|taskId|guaranteed}} (163)
  Sea charting       {{SeaChartRow|hint|id|level|type|hazard|sea|ocean|location|location2}} (358)
  Level up table     {{Level up table|members<level> = bullet list}}
  Mooring points     the wikitable of "Mooring point" (name, level, requirement, facilities Y/N, map pin x,z)
  Part stats         every wikitable of Hull, Mast and sails, Helm, Keel and Facilities, as long-format rows, to cross-check
                     the cache stats rows (db 164, 176-186; not loaded by the server)
  Buoy coordinates   the cache-derived list in docs/plans/sailing.md section 4.1 (not the Wiki)

Output (tab-separated, header first, in the output directory):
  courier-tasks.tsv   task level xp board cargo_port destination item qty ledger_cargo ledger_deposit
  bounty-tasks.tsv    task level xp board monster item qty rarity guaranteed
  charting-tasks.tsv  task level type hazard sea ocean x z x2 z2 hint
  level-ups.tsv       level construction text
  docks.tsv           name level requirement shipwright notice_board salvaging_station ledger_table crew_registrar deposit_box x z
  dock-buoys.tsv      name x z
  part-stats.tsv      page section row header value

Usage (needs network access to oldschool.runescape.wiki; the plan path defaults to the repo's):
  python3 -I tools/wiki-import/sailing.py content/sailing/src/main/resources/tp/content/sailing [docs/plans/sailing.md]

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
PAGE_DELAY_SECONDS = 1.0
PART_PAGES = ["Hull", "Mast and sails", "Helm", "Keel", "Facilities"]


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


def wikitext(page):
    data = api({"action": "parse", "page": page, "prop": "wikitext", "redirects": 1})
    time.sleep(PAGE_DELAY_SECONDS)
    return data["parse"]["wikitext"]["*"]


# ---------------------------------------------------------------------------------------------------------------- parsing


def split_top(text, sep="|"):
    """Splits on `sep` outside {{ }}, [[ ]] and <!-- -->."""
    parts, depth_b, depth_s, cur, i = [], 0, 0, [], 0
    while i < len(text):
        two = text[i:i + 2]
        if two == "{{":
            depth_b += 1
            cur.append(two)
            i += 2
        elif two == "}}":
            depth_b -= 1
            cur.append(two)
            i += 2
        elif two == "[[":
            depth_s += 1
            cur.append(two)
            i += 2
        elif two == "]]":
            depth_s -= 1
            cur.append(two)
            i += 2
        elif text[i] == sep and depth_b == 0 and depth_s == 0:
            parts.append("".join(cur))
            cur = []
            i += 1
        else:
            cur.append(text[i])
            i += 1
    parts.append("".join(cur))
    return parts


def find_templates(text, name):
    """Inner text (after the name and its first `|`) of every {{name|...}} in text, nested braces handled."""
    out = []
    pattern = re.compile(r"\{\{\s*" + re.escape(name) + r"\s*\|", re.I)
    for m in pattern.finditer(text):
        depth, i = 1, m.end()
        while i < len(text) and depth:
            two = text[i:i + 2]
            if two == "{{":
                depth += 1
                i += 2
            elif two == "}}":
                depth -= 1
                i += 2
            else:
                i += 1
        out.append(text[m.end():i - 2])
    return out


def template_args(inner):
    """(positional list, named dict) of a template body."""
    pos, named = [], {}
    for part in split_top(inner):
        m = re.match(r"\s*([A-Za-z0-9_ ]+?)\s*=(.*)$", part, re.S)
        if m and not part.lstrip().startswith("["):
            named[m.group(1).strip()] = m.group(2).strip()
        else:
            pos.append(part.strip())
    return pos, named


def clean(s):
    """Wikitext cell/bullet -> plain text."""
    s = re.sub(r"<!--.*?-->", "", s, flags=re.S)
    s = re.sub(r"<ref[^>]*/>|<ref[^>]*>.*?</ref>", "", s, flags=re.S)
    s = re.sub(r"\{\{\s*refn\s*\|.*?\}\}", "", s, flags=re.S)
    s = re.sub(r"<br\s*/?>", "; ", s)
    for _ in range(4):  # innermost templates first
        s = re.sub(r"\{\{\s*(?:SCP|scp)\s*\|\s*([^|}]+)\|\s*([^|}]+)\}\}", r"\1 \2", s)
        s = re.sub(r"\{\{\s*SCP\s*\|\s*([^|}]+)\}\}", r"\1", s)
        s = re.sub(r"\{\{\s*(?:plinkt?|ilinkt?|ilink)\s*\|([^{}]*)\}\}", lambda m: _link_text(m.group(1)), s)
        s = re.sub(r"\{\{\s*Coins\s*\|([^{}|]*)\}\}", r"\1", s)
        s = re.sub(r"\{\{\s*(?:Okay|Yes)\s*\}\}", "Y", s)
        s = re.sub(r"\{\{\s*(?:Not okay|No)\s*\}\}", "N", s)
        s = re.sub(r"\{\{\s*(?:NA|N/A)\s*\}\}", "-", s)
        s = re.sub(r"\{\{[^{}]*\}\}", "", s)
    s = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = s.replace("'''", "").replace("''", "").replace("&nbsp;", " ")
    s = re.sub(r"\s+", " ", s).strip(" ;")
    return s


def _link_text(body):
    pos, named = template_args(body)
    return named.get("txt") or (pos[0] if pos else "")


def tsv(path, header, rows):
    with open(path, "w", encoding="utf-8") as f:
        f.write("\t".join(header) + "\n")
        for r in rows:
            f.write("\t".join(str(c).replace("\t", " ").replace("\n", " ") for c in r) + "\n")
    print(f"{os.path.basename(path)}: {len(rows)} rows")


def parse_tables(text):
    """[(section, [header cells], [[cells]])] of every wikitable; multi-line cells are joined."""
    tables, section = [], ""
    lines = text.split("\n")
    i = 0
    while i < len(lines):
        line = lines[i]
        m = re.match(r"^=+\s*(.*?)\s*=+\s*$", line)
        if m:
            section = clean(m.group(1))
        if line.startswith("{|"):
            headers, rows, cur, in_header = [], [], None, True
            i += 1
            while i < len(lines) and not lines[i].startswith("|}"):
                ln = lines[i]
                if ln.startswith("|-"):
                    if cur is not None:
                        rows.append(cur)
                    cur, in_header = [], False
                elif ln.startswith("!"):
                    for cell in ln[1:].split("!!"):
                        headers.append(clean(_strip_attrs(cell)))
                elif ln.startswith("|") and not ln.startswith("|+"):
                    if cur is None:
                        cur = []
                    for cell in _split_cells(ln[1:]):
                        cur.append(_strip_attrs(cell))
                elif cur:
                    cur[-1] += " " + ln
                i += 1
            if cur:
                rows.append(cur)
            tables.append((section, headers, rows))
        i += 1
    return tables


def _split_cells(s):
    """Splits a `|a||b` row line into cells (`||` separator, outside templates and links)."""
    out, cur, depth, i = [], [], 0, 0
    while i < len(s):
        two = s[i:i + 2]
        if two in ("{{", "[["):
            depth += 1
            cur.append(two)
            i += 2
        elif two in ("}}", "]]"):
            depth -= 1
            cur.append(two)
            i += 2
        elif two == "||" and depth == 0:
            out.append("".join(cur))
            cur = []
            i += 2
        else:
            cur.append(s[i])
            i += 1
    out.append("".join(cur))
    return out


def _strip_attrs(cell):
    """Drops a leading `attr=value|` (colspan, style ...) from a table cell."""
    parts = split_top(cell)
    if len(parts) > 1 and "=" in parts[0] and "[[" not in parts[0] and "{{" not in parts[0]:
        return "|".join(parts[1:]).strip()
    return cell.strip()


# ------------------------------------------------------------------------------------------------------------- datasets


def courier_tasks(text):
    rows = []
    for inner in find_templates(text, "CourierTaskLine"):
        _, n = template_args(inner)
        rows.append([n.get("taskId", ""), n.get("level", ""), n.get("xp", ""), n.get("noticeBoard", ""), n.get("cargoLocation", ""),
                     n.get("destination", ""), n.get("item", ""), n.get("qty", ""), "", ""])
    # The first table holds the two quest tasks (level, xp, quest, cargo port, destination, item, qty, map pins).
    for section, headers, table in parse_tables(text):
        for cells in table:
            if len(cells) < 8 or "task id:" not in cells[-1]:
                continue
            task = re.search(r"task id:\s*(\d+)", cells[-1]).group(1)
            pins = re.findall(r"icon:\w+,x:(\d+),y:(\d+),title:(Withdraw|Deposit) cargo", cells[-1])
            cargo = next((f"{x},{y}" for x, y, t in pins if t == "Withdraw"), "")
            deposit = next((f"{x},{y}" for x, y, t in pins if t == "Deposit"), "")
            rows.append([task, clean(cells[0]), clean(cells[1]), clean(cells[2]), clean(cells[3]), clean(cells[4]), clean(cells[5]),
                         clean(cells[6]), cargo, deposit])
    return rows


def bounty_tasks(text):
    rows = []
    for inner in find_templates(text, "BountyTaskLine"):
        _, n = template_args(inner)
        rows.append([n.get("taskId", ""), n.get("level", ""), n.get("xp", ""), n.get("noticeBoard", ""), n.get("monster", ""),
                     n.get("item", ""), n.get("qty", ""), n.get("rarity", ""),
                     "yes" if n.get("guaranteed", "").strip().lower() == "yes" else ""])
    return rows


def charting_tasks(text):
    rows = []
    for inner in find_templates(text, "SeaChartRow"):
        pos, n = template_args(inner)
        # The Wiki keeps editor notes in HTML comments inside some cells (the level of three tasks).
        n = {k: re.sub(r"<!--.*?-->", "", v, flags=re.S).strip() for k, v in n.items()}
        x, z = (n.get("location", ",").split(",") + [""])[:2]
        x2, z2 = (n.get("location2", ",").split(",") + [""])[:2]
        rows.append([n.get("id", ""), n.get("level", ""), n.get("type", ""), n.get("hazard", ""), n.get("sea", ""), n.get("ocean", ""),
                     x, z, x2, z2, clean(pos[0]) if pos else ""])
    return rows


def level_ups(text):
    body = find_templates(text, "Level up table")[0]
    rows = []
    for m in re.finditer(r"\|\s*members(\d+)\s*=(.*?)(?=\n\|\s*(?:members|free)\w*\s*=|\Z)", body, re.S):
        level = int(m.group(1))
        for bullet in re.findall(r"^\*\s*(.+)$", m.group(2), re.M):
            con = re.search(r"SCP\|Construction\|(\d+)", bullet)
            rows.append([level, con.group(1) if con else "", clean(bullet)])
    return rows


def docks(text):
    rows = []
    for _, headers, table in parse_tables(text):
        if not any("Docking point" in h for h in headers):
            continue
        for cells in table:
            if len(cells) < 10:
                continue
            pin = re.search(r"\|(\d+),(\d+)\s*\}\}", cells[-1])
            rows.append([clean(cells[0]), clean(cells[1]), clean(cells[2])] + [clean(c) for c in cells[3:9]] +
                        [pin.group(1) if pin else "", pin.group(2) if pin else ""])
    return rows


def part_stats(page, text):
    rows = []
    for section, headers, table in parse_tables(text):
        for index, cells in enumerate(table):
            for col, cell in enumerate(cells):
                header = headers[col] if col < len(headers) else f"col{col}"
                rows.append([page, section, index, header, clean(cell)])
    return rows


def buoys(plan_path):
    with open(plan_path, encoding="utf-8") as f:
        plan = f.read()
    m = re.search(r"\*\*Buoy \(sea-side dock\) coordinates:\*\*\s*(.+)", plan)
    if not m:
        return []
    rows = []
    for name, x, z in re.findall(r"\s*([^;(]+?) \((\d+),(\d+)\)", m.group(1)):
        rows.append([name.strip(), x, z])
    return rows


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "content/sailing/src/main/resources/tp/content/sailing"
    plan = sys.argv[2] if len(sys.argv) > 2 else "docs/plans/sailing.md"
    os.makedirs(out, exist_ok=True)
    tsv(f"{out}/courier-tasks.tsv", ["task", "level", "xp", "board", "cargo_port", "destination", "item", "qty", "ledger_cargo",
                                     "ledger_deposit"], courier_tasks(wikitext("Courier tasks")))
    tsv(f"{out}/bounty-tasks.tsv", ["task", "level", "xp", "board", "monster", "item", "qty", "rarity", "guaranteed"],
        bounty_tasks(wikitext("Bounty tasks")))
    tsv(f"{out}/charting-tasks.tsv", ["task", "level", "type", "hazard", "sea", "ocean", "x", "z", "x2", "z2", "hint"],
        charting_tasks(wikitext("Sea charting")))
    tsv(f"{out}/level-ups.tsv", ["level", "construction", "text"], level_ups(wikitext("Sailing/Level up table")))
    tsv(f"{out}/docks.tsv", ["name", "level", "requirement", "shipwright", "notice_board", "salvaging_station", "ledger_table",
                              "crew_registrar", "deposit_box", "x", "z"], docks(wikitext("Mooring point")))
    stats = []
    for page in PART_PAGES:
        stats += part_stats(page, wikitext(page))
    tsv(f"{out}/part-stats.tsv", ["page", "section", "row", "header", "value"], stats)
    if os.path.exists(plan):
        tsv(f"{out}/dock-buoys.tsv", ["name", "x", "z"], buoys(plan))


if __name__ == "__main__":
    main()
