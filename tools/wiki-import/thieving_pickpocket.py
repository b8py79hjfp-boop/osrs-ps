#!/usr/bin/env python3
"""
Pickpocketing data from the OSRS Wiki (docs/mechanics/thieving.md).

Every thievable NPC's page carries three templates:
  {{Thieving info |level |xp |time |damage}}   level, xp, the stun length shown and the stun damage
  {{Skilling success chart |low1 |high1 ...}}  the pickpocket roll (line 1 = "Normal"; line 2 gloves of silence;
                                               line 3 Ardougne diary; line 4 thieving cape + diary)
  {{DropsLineSkill |name |quantity |rarity}}   the loot, under the page's pickpocket loot headings

Rows are classified by the heading they sit under: "Pre-roll" (clue / nothing, rolled first), "Tertiary" (pet, skipped)
and everything else is the main table, whose rows are mutually exclusive (rarities add up to at most 1; "Always" rows
are given every time). Clue-scroll lines (DropsLineClue) are written as `clue-easy` etc. so content can skip them.

Coins: every pickpocket gives a coin pouch (Wiki "Coin pouch"), not coins. A page's "Coins" row (or "Coin pouch" row,
when the page has one) becomes a pouch row: item = the NPC's pouch obj, qty 1, `pouch` = the coins inside (min-max,
Wiki "Coin pouch" quantity table, hard-coded in POUCHES below). Pages without a Skilling success chart (Cave goblin,
Fremennik citizen, Pollnivneach bandits) are skipped: no sourced success roll (OPEN-TH1).

Output (tab-separated, header first) is written to the file given as the second argument, one row per loot line:
  npc      the Wiki page
  level, xp, damage (min-max), stun (Wiki text), low, high (success roll "Normal"), glow, ghigh (with gloves of silence),
  kind (always|pre|main; pre rows are the clue-scroll pre-roll), item (cache obj id, -2 = clue scroll, - = nothing),
  name (Wiki item name), qty (min-max), num, den (exact fraction), pouch (coins in the pouch, or -)

Usage (needs network access to oldschool.runescape.wiki; objs.txt from cache-dump):
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  python3 -I tools/wiki-import/thieving_pickpocket.py objs.txt content/skills/src/main/resources/tp/content/skills/pickpocket.tsv

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from fractions import Fraction

API = "https://oldschool.runescape.wiki/api.php"
UA = "tickperfect-wiki-import/1.0 (private server dev tool)"

# Wiki page -> label written to the output (the NPC name content registers by).
PAGES = [
    "Man", "Woman", "Farmer", "H.A.M. Member", "Warrior (Thieving)", "Al Kharid warrior", "Rogue",
    "Cave goblin (Dorgesh-Kaan)", "Master Farmer", "Guard", "Fremennik citizen", "Bandit (Bandit Camp)",
    "Bandit (Pollnivneach)", "Knight of Ardougne", "Watchman", "Paladin", "Gnome", "Hero", "Vyre (Thieving)",
    "Elf (Thieving)", "TzHaar-Hur",
]


# Coin pouch obj and contents (min-max coins) per page: Wiki "Coin pouch" (item ids and "Coin pouch quantities").
POUCHES = {
    "Man": (22521, "3-3"), "Woman": (22521, "3-3"), "Farmer": (22522, "9-9"), "H.A.M. Member": (22523, "1-21"),
    "Warrior (Thieving)": (22524, "18-18"), "Al Kharid warrior": (22524, "18-18"), "Rogue": (22525, "25-40"),
    "Guard": (22527, "30-30"), "Bandit (Bandit Camp)": (22530, "30-30"), "Knight of Ardougne": (22531, "50-50"),
    "Watchman": (22533, "60-60"), "Paladin": (22535, "80-80"), "Gnome": (22536, "300-300"), "Hero": (22537, "200-300"),
    "Vyre (Thieving)": (24703, "230-315"), "Elf (Thieving)": (22538, "280-350"),
}


def wikitext(page):
    url = API + "?" + urllib.parse.urlencode({"action": "parse", "page": page, "prop": "wikitext", "redirects": 1, "format": "json"})
    for attempt in range(6):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=60) as r:
                return json.load(r)["parse"]["wikitext"]["*"]
        except Exception:  # noqa: BLE001 - retry transient network errors
            time.sleep(2 ** attempt)
    raise RuntimeError("wiki request failed: " + page)


def load_objs(path):
    names = {}
    for line in open(path, encoding="utf-8"):
        m = re.match(r'(\d+) name="(.*?)" ', line)
        if m and 'certTemplate=-1' in line and 'placeholderTemplate=-1' in line:
            names.setdefault(m.group(2).lower(), int(m.group(1)))
    return names


def fields(template):
    """Top-level |key = value pairs of one template body (values without nested templates are enough here)."""
    body = template
    while True:  # drop nested templates (citations, notes) innermost first
        stripped = re.sub(r"\{\{[^{}]*\}\}", "", body)
        if stripped == body:
            break
        body = stripped
    return {k.strip(): v.strip() for k, v in re.findall(r"\|\s*(\w+?)\s*=\s*([^|\n]*)", body)}


def templates(text, name):
    """Bodies of every {{name ...}} with balanced braces."""
    out = []
    start = 0
    while True:
        i = text.find("{{" + name, start)
        if i < 0:
            return out
        depth = 0
        j = i
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
        out.append((i, text[i + 2 + len(name):j - 2]))
        start = j


def rarity(text):
    text = text.replace(",", "").strip()
    if text.lower() == "always":
        return Fraction(1)
    m = re.fullmatch(r"([\d.]+)\s*/\s*([\d.]+)", text)
    if not m:
        return None
    return Fraction(m.group(1)) / Fraction(m.group(2))


def quantity(text):
    text = text.replace(",", "").strip()
    m = re.fullmatch(r"(\d+)(?:\s*[-–]\s*(\d+))?", text)
    if not m:
        return None
    return m.group(1) + "-" + (m.group(2) or m.group(1))


def heading_at(text, pos):
    heads = [(m.start(), m.group(1).strip()) for m in re.finditer(r"^=+\s*(.*?)\s*=+\s*$", text, re.M) if m.start() < pos]
    return heads[-1][1].lower() if heads else ""


def main():
    objs = load_objs(sys.argv[1])
    out = ["npc\tlevel\txp\tdamage\tstun\tlow\thigh\tglow\tghigh\tkind\titem\tname\tqty\tnum\tden\tpouch"]
    for page in PAGES:
        text = wikitext(page)
        # The pickpocket section only (pages of fighters have a long monster body above it).
        start = max(text.find("==Pickpocketing=="), text.find("{{Thieving info"))
        start = text.find("==Pickpocket") if text.find("==Pickpocket") >= 0 else start
        end = text.find("{{Subject changes", start)
        section = text[start:end if end > 0 else len(text)]
        info = {}
        for _, body in templates(section, "Thieving info"):
            info = fields(body)
            break
        chart = {}
        for _, body in templates(section, "Skilling success chart"):
            chart = fields(body)
            break
        if not info or not chart:
            print("skipped (no info/chart): " + page, file=sys.stderr)
            continue
        dmg = re.sub(r"[^\d\-]", "", info.get("damage", "1").replace("–", "-"))
        dmg = dmg if "-" in dmg else dmg + "-" + dmg
        head = [page, info["level"], info["xp"], dmg, info.get("time", ""), chart["low1"], chart["high1"],
                chart.get("low2", chart["low1"]), chart.get("high2", chart["high1"])]
        lines = templates(section, "DropsLineSkill") + templates(section, "DropsLineClue")
        has_pouch_row = any(fields(b).get("name", "").startswith("Coin pouch") for _, b in lines)
        pouch_done = False
        for pos, body in lines:
            f = fields(body)
            where = heading_at(section, pos)
            if "tertiary" in where:
                continue
            kind = "pre" if "pre-roll" in where or "pre roll" in where else "main"
            frac = rarity(f.get("rarity", ""))
            if frac is None:
                print("skipped row (rarity %r) on %s" % (f.get("rarity"), page), file=sys.stderr)
                continue
            if f.get("rarity", "").lower() == "always":
                kind = "always"
            name = f.get("name") or ("clue-" + f.get("type", "easy"))
            name = re.sub(r" \(item\)$", "", name)
            pouch = "-"
            if name.startswith("clue-"):
                kind = "pre"
            elif page in POUCHES and (name.startswith("Coin pouch") or (name == "Coins" and not has_pouch_row)):
                if pouch_done:
                    continue
                pouch_done = True
                item_id, pouch = POUCHES[page]
                out.append("\t".join(head + [kind, str(item_id), "Coin pouch", "1-1", str(frac.numerator), str(frac.denominator), pouch]))
                continue
            elif name == "Coins" and page in POUCHES:
                continue  # rogue-equipment-only coins beside a pouch row
            if name.lower() == "nothing":
                item = "-"
            elif name.startswith("clue-"):
                item = "-2"
            else:
                item = objs.get(name.lower())
                if item is None:
                    print("skipped row (item %r not in cache) on %s" % (name, page), file=sys.stderr)
                    continue
            qty = quantity(f.get("quantity", "1")) or "1-1"
            out.append("\t".join(head + [kind, str(item), name, qty, str(frac.numerator), str(frac.denominator), pouch]))
        time.sleep(0.2)
    with open(sys.argv[2], "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
