#!/usr/bin/env python3
"""
Cooking burn rolls from the OSRS Wiki (docs/mechanics/cooking.md CK3).

Each cooked food's page carries a {{Skilling success chart}} with the `low` / `high` of the cooking success roll per
heat source and modifier ("Fire or Range", "Fire", "Range", "Gauntlets", "Lumbridge range", "Hosidius +5%", ...).
This prints one tab-separated row per chart line: page, label, low, high, req. The rows are copied into
content/skills/.../CookingData.kt.

Usage (needs network access to oldschool.runescape.wiki):
  python3 -I tools/wiki-import/cooking_rates.py

Data licence: OSRS Wiki content is CC BY-NC-SA 3.0 (data/PROVENANCE.md).
"""
import json
import re
import time
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
UA = "tickperfect-wiki-import/1.0 (private server dev tool)"
PAGES = [
    "Cooked meat", "Cooked chicken", "Shrimps", "Anchovies", "Sardine", "Herring", "Mackerel", "Trout", "Cod", "Pike",
    "Salmon", "Tuna", "Cave eel", "Lobster", "Bass", "Swordfish", "Monkfish", "Shark", "Sea turtle", "Anglerfish",
    "Dark crab", "Manta ray", "Bread", "Pitta bread", "Redberry pie", "Meat pie", "Apple pie", "Plain pizza", "Cake",
    # Added with the karambwan, eel and multi-step food work (docs/mechanics/cooking.md CK9-CK13).
    "Cooked karambwan", "Poison karambwan", "Stew", "Curry", "Scrambled egg", "Fried onions", "Fried mushrooms", "Cooked slimy eel",
    "Infernal eel", "Sacred eel", "Jug of wine", "Garden pie", "Fish pie", "Admiral pie", "Wild pie", "Summer pie", "Mud pie",
    "Meat pizza", "Anchovy pizza", "Pineapple pizza", "Chocolate cake",
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


def main():
    for page in PAGES:
        text = wikitext(page)
        for chart in re.findall(r"\{\{Skilling success chart(.*?)\n\}\}", text, re.S):
            if "cooking" not in chart.lower():
                continue
            fields = dict(re.findall(r"\|\s*(\w+?)\s*=\s*([^|\n]*)", chart))
            n = 1
            while f"label{n}" in fields:
                print("\t".join([page, fields[f"label{n}"].strip(), fields.get(f"low{n}", ""), fields.get(f"high{n}", ""), fields.get(f"req{n}", "")]))
                n += 1
        time.sleep(0.2)


if __name__ == "__main__":
    main()
