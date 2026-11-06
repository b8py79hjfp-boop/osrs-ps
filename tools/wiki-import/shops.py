#!/usr/bin/env python3
"""
NPC shops from the OSRS Wiki (docs/mechanics/shops.md).

Every page that uses {{Infobox Shop}} is read (listed with the API's `embeddedin`). The first {{StoreTableHead}} ...
{{StoreTableBottom}} table of the page is the shop's stock (later tables are variants such as Karamja gloves or
ironman stock: OPEN-SH2). From the template parameters:

  Infobox Shop      name, members, owner (wikilinks to NPC pages), special ("General store" = accepts any item)
  StoreTableHead    sellmultiplier (price the shop sells at, per mille of the item's value, default 1000),
                    buymultiplier (price the shop buys at, per mille, default 1000), delta (price change per stock
                    unit, per mille of the value, default 0), currency (default Coins); Module:StoreTableHead
  StoreLine / row   name, stock (a number or inf), restock (ticks, -1 = unknown), optional sell/buy fixed prices;
                    Module:StoreLine. Wrapper rows such as {{Tzhaar shop row}} pass the same parameters.

Owners: each owner link is an NPC page; its {{Infobox NPC}} ids that exist in the cache and have a "Trade" op become the
shop's owner NPC ids. Items and currencies are matched to cache objs by name (not noted, not a placeholder; tradeable
first, then the lowest id). The client needs an inv id per shop (it binds the shop interface to it, the cache `inv` size
is the slot count): it is the shop-named cache inv whose symbol shares the most words with the shop name and has enough
slots, else the smallest generic one.

Output (tab-separated, header first, in the output directory):
  shops.tsv        id name currency general sell buy delta inv slots members owners
  shop-items.tsv   shop obj stock restock sellprice buyprice      (stock -1 = infinite; prices -1 = by multiplier)

Usage (needs network access to oldschool.runescape.wiki and the generated symbols, `./gradlew :symbols:compileKotlin`):
  ./gradlew -q :tools:cache-dump:run --args="config npc --all" > npcs.txt
  ./gradlew -q :tools:cache-dump:run --args="config obj --all" > objs.txt
  ./gradlew -q :tools:cache-dump:run --args="config inv --all" > invs.txt
  python3 -I tools/wiki-import/shops.py npcs.txt objs.txt invs.txt symbols/build/generated/symbols/tp/symbols/Invs.java \
      content/shops/src/main/resources/tp/content/shops

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
COINS_OBJ = 995  # cache obj "Coins"
GENERAL_INV_SLOTS = 40  # cache inv size of the general stores (GENERALSHOP1)
DEFAULT_RESTOCK = -1


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
    """title as asked -> wikitext, following normalisation and redirects."""
    out = {}
    for i in range(0, len(titles), 50):
        batch = titles[i:i + 50]
        d = api({"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main", "redirects": 1,
                 "titles": "|".join(batch)})
        q = d["query"]
        alias = {}
        for key in ("normalized", "redirects"):
            for e in q.get(key, []):
                alias[e["from"]] = e["to"]
        texts = {}
        for page in q["pages"].values():
            revs = page.get("revisions")
            if revs:
                texts[page["title"]] = revs[0]["slots"]["main"]["*"]
        for t in batch:
            cur, seen = t, set()
            while cur in alias and cur not in seen:
                seen.add(cur)
                cur = alias[cur]
            if cur in texts:
                out[t] = texts[cur]
        time.sleep(0.2)
    return out


def templates(text, name):
    """Bodies of every {{name ...}} (brace-balanced)."""
    out, start, opener = [], 0, "{{" + name
    while True:
        i = text.find(opener, start)
        if i < 0:
            return out
        after = text[i + len(opener):i + len(opener) + 1]
        if after not in ("|", "\n", " ", "}"):
            start = i + len(opener)
            continue
        end = template_end(text, i)
        out.append(text[i + len(opener):end - 2])
        start = end


def template_end(text, i):
    """Index just after the }} that closes the {{ at i."""
    depth, j = 0, i
    while j < len(text):
        if text.startswith("{{", j):
            depth += 1
            j += 2
        elif text.startswith("}}", j):
            depth -= 1
            j += 2
            if depth == 0:
                return j
        else:
            j += 1
    return len(text)


def top_templates(text):
    """(name, body) of the top-level templates of text."""
    out, i = [], 0
    while True:
        i = text.find("{{", i)
        if i < 0:
            return out
        end = template_end(text, i)
        inner = text[i + 2:end - 2]
        name = re.split(r"[|\n]", inner, maxsplit=1)[0].strip()
        out.append((name, inner[len(name):]))
        i = end


def split_params(body):
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
    return [p.strip() for p in parts]


def clean(v):
    v = re.sub(r"<ref[^>]*/>", "", v)
    v = re.sub(r"<ref[^>]*>.*?</ref>", "", v, flags=re.S)
    v = re.sub(r"<!--.*?-->", "", v, flags=re.S)
    v = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", v)
    return v.strip()


def params_of(body):
    params = {}
    for p in split_params(body):
        if "=" in p:
            k, v = p.split("=", 1)
            params[k.strip().lower()] = v.strip()
    return params


def num(v, default=None):
    v = (v or "").replace(",", "").strip()
    return int(v) if re.fullmatch(r"-?\d+", v) else default


def load_npcs(path):
    """id -> (name, set of op texts)."""
    out = {}
    for line in open(path):
        m = re.match(r'(\d+) name="(.*?)" size=\d+ combatLevel=-?\d+ standSeq=-?\d+ ops=\[(.*)\]\s*$', line)
        if m:
            out[int(m.group(1))] = (m.group(2), set(re.findall(r'op\d="([^"]*)"', m.group(3))))
    return out


def load_objs(path):
    """lower-case name -> candidate ids, best first (tradeable, then lowest id); id -> cost."""
    by_name, cost = {}, {}
    for line in open(path):
        m = re.match(r'(\d+) name="(.*?)" cost=(-?\d+) stackable=\d+ members=\w+ tradeable=(\w+) certTemplate=(-?\d+) '
                     r'placeholderTemplate=(-?\d+)', line)
        if not m:
            continue
        oid = int(m.group(1))
        cost[oid] = int(m.group(3))
        if int(m.group(5)) != -1 or int(m.group(6)) != -1:
            continue
        by_name.setdefault(m.group(2).lower(), []).append((m.group(4) != "true", oid))
    return {k: [o for _, o in sorted(v)] for k, v in by_name.items()}, cost


def load_invs(sizes_path, symbols_path):
    """(id, symbol, size) of shop-named invs, without the ironman/league variants."""
    sizes = {}
    for line in open(sizes_path):
        m = re.match(r"(\d+) size=(\d+)", line)
        if m:
            sizes[int(m.group(1))] = int(m.group(2))
    out = []
    for line in open(symbols_path):
        m = re.search(r"int (\w+) = (\d+);", line)
        if not m or not re.search(r"SHOP|STORE", m.group(1)):
            continue
        if re.search(r"_(IRON|UIM|GIM|LEAGUES?\w*|TBLR|EASTER)$|^LEAGUE|^DMM|^DEADMAN|^TRADER_SHOP", m.group(1)):
            continue
        if int(m.group(2)) in sizes:
            out.append((int(m.group(2)), m.group(1), sizes[int(m.group(2))]))
    return out


GENERIC = {"shop", "store", "general", "the", "of", "and", "s", "a", "supplies", "goods", "stall"}


def words(s):
    """Lower-case stems of the words of s (a Wiki name or an Invs symbol), without the generic ones."""
    out = set()
    for w in re.split(r"[^a-z]+", s.lower().replace("'", "")):
        w = re.sub(r"(shop|store)$", "", w).rstrip("s")
        if w and w not in GENERIC:
            out.add(w)
    return out


def pick_inv(name, general, needed, invs):
    """Shop-named inv with the most words in common and at least `needed` slots; else the smallest generic one."""
    want = GENERAL_INV_SLOTS if general else needed
    ws = words(name)
    best = None
    for oid, sym, size in invs:
        if size < want:
            continue
        score = len(ws & words(sym))
        if score and (best is None or (score, -size) > (best[0], -best[1])):
            best = (score, size, oid)
    if best:
        return best[2]
    pattern = r"GENERALSHOP\d" if general else r"[A-Z]+SHOP\d*"
    generic = [(size, oid) for oid, sym, size in invs if size >= want and re.fullmatch(pattern, sym)]
    return min(generic)[1] if generic else max((size, oid) for oid, _, size in invs)[1]


def main():
    verbose = "-v" in sys.argv
    npcs_path, objs_path, invs_path, symbols_path, out_dir = [a for a in sys.argv[1:] if a != "-v"][:5]
    lim = None if verbose else 20
    npcs = load_npcs(npcs_path)
    objs, cost = load_objs(objs_path)
    invs = load_invs(invs_path, symbols_path)
    sizes = {oid: size for oid, _, size in invs}
    trade_by_name = {}
    for nid, (name, ops) in npcs.items():
        if "Trade" in ops:
            trade_by_name.setdefault(name.lower(), []).append(nid)

    titles = sorted(pages_using("Template:Infobox Shop"))
    print("shop pages:", len(titles))
    texts = wikitexts(titles)

    parsed, bad_currency, bad_items, no_stock = [], [], [], []
    multi_table = 0
    owner_titles = set()
    for title in titles:
        text = texts.get(title)
        if not text:
            continue
        boxes = templates(text, "Infobox Shop")
        if not boxes:
            continue
        info = params_of(boxes[0])
        head_i = text.find("{{StoreTableHead")
        if head_i < 0:
            no_stock.append(title)
            continue
        head_end = template_end(text, head_i)
        head = params_of(text[head_i + len("{{StoreTableHead"):head_end - 2])
        bottom = text.find("{{StoreTableBottom", head_end)
        region = text[head_end:bottom if bottom >= 0 else len(text)]
        if text.count("{{StoreTableHead") > 1:
            multi_table += 1
        rows = []
        for name, body in top_templates(region):
            p = params_of(body)
            if "name" not in p:
                continue
            stock_raw = p.get("stock", "").lower()
            stock = -1 if stock_raw in ("inf", "∞") else num(stock_raw)
            if stock is None:
                continue
            rows.append({
                "name": clean(p["name"]), "stock": stock, "restock": num(p.get("restock"), DEFAULT_RESTOCK),
                "sell": num(p.get("sell"), -1), "buy": num(p.get("buy"), -1),
            })
        if not rows:
            no_stock.append(title)
            continue
        owner_raw = info.get("owner", "")
        owner_links = [(m.split("|")[0].split("#")[0].strip(), (m.split("|")[-1]).strip())
                       for m in re.findall(r"\[\[([^\]]+)\]\]", owner_raw)]
        for link, _ in owner_links:
            owner_titles.add(link)
        special = clean(info.get("special", "")).lower()
        parsed.append({
            "page": title, "name": clean(info.get("name", title)) or title, "special": special,
            "members": 1 if clean(info.get("members", "")).lower().startswith("y") else 0,
            "owners": owner_links, "general": 1 if "general store" in special or "{{General stores}}" in text else 0,
            "currency": clean(head.get("currency", "Coins")) or "Coins",
            "sell": num(head.get("sellmultiplier"), 1000), "buy": num(head.get("buymultiplier"), 1000),
            "delta": num(head.get("delta"), 0), "rows": rows,
        })

    print("owner pages:", len(owner_titles))
    owner_texts = wikitexts(sorted(owner_titles))
    owner_ids = {}
    for t in owner_titles:
        ids = []
        for kind in ("Infobox NPC", "Infobox Monster"):
            for body in templates(owner_texts.get(t, ""), kind):
                for k, v in params_of(body).items():
                    if re.fullmatch(r"id\d*", k):
                        ids += [int(x) for x in re.findall(r"\d+", clean(v))]
        owner_ids[t] = [i for i in dict.fromkeys(ids) if i in npcs and "Trade" in npcs[i][1]]

    shops, items, unmatched_owners, seen_npc, shared_npc = [], [], [], {}, 0
    no_inv_fit = []
    for sh in sorted(parsed, key=lambda s: s["name"].lower()):
        currency = COINS_OBJ if sh["currency"].lower() in ("coins", "coin") else (objs.get(sh["currency"].lower()) or [None])[0]
        if currency is None:
            bad_currency.append(f"{sh['name']} ({sh['currency']})")
            continue
        owners = []
        for link, shown in sh["owners"]:
            ids = owner_ids.get(link) or []
            if not ids:
                base = re.sub(r"\s*\(.*\)$", "", link).lower()
                cand = trade_by_name.get(base) or trade_by_name.get(shown.lower()) or []
                ids = cand if len(cand) <= 4 else []
            if not ids:
                unmatched_owners.append(f"{sh['name']} <- {link}")
            owners += ids
        owners = list(dict.fromkeys(owners))
        if not owners:
            unmatched_owners.append(f"{sh['name']} (no Trade NPC)")
        sid = len(shops)
        shop_items = []
        for r in sh["rows"]:
            wiki_name = re.sub(r" \(item\)$", "", r["name"].split("#")[0]).lower()
            cands = objs.get(wiki_name)
            if not cands:
                bad_items.append(f"{sh['name']}: {r['name']}")
                continue
            shop_items.append((cands[0], r))
        if not shop_items:
            continue
        needed = len(shop_items)
        inv = pick_inv(sh["name"] + " " + sh["special"], sh["general"], needed, invs)
        if sizes[inv] < needed:
            no_inv_fit.append(sh["name"])
            shop_items = shop_items[:sizes[inv]]
        for nid in owners:
            if nid in seen_npc:
                shared_npc += 1
            else:
                seen_npc[nid] = sid
        shops.append((sid, sh["name"], currency, sh["general"], sh["sell"], sh["buy"], sh["delta"], inv, sizes[inv],
                      sh["members"], ",".join(map(str, owners))))
        for oid, r in shop_items:
            items.append((sid, oid, r["stock"], r["restock"], r["sell"], r["buy"]))

    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "shops.tsv"), "w") as f:
        f.write("id\tname\tcurrency\tgeneral\tsell\tbuy\tdelta\tinv\tslots\tmembers\towners\n")
        for s in shops:
            f.write("\t".join(map(str, s)) + "\n")
    with open(os.path.join(out_dir, "shop-items.tsv"), "w") as f:
        f.write("shop\tobj\tstock\trestock\tsellprice\tbuyprice\n")
        for i in items:
            f.write("\t".join(map(str, i)) + "\n")

    print(f"shops: {len(shops)} of {len(titles)} pages, items: {len(items)}, owner NPC ids: {len(seen_npc)}")
    print(f"shops without a Trade NPC: {sum(1 for s in shops if not s[-1])}; NPC ids claimed by several shops: {shared_npc}")
    print(f"pages with several stock tables (only the first is used): {multi_table}")
    print(f"pages without a stock table: {len(no_stock)}", no_stock[:lim])
    print(f"shops skipped for a non-item currency: {len(bad_currency)}", bad_currency[:lim])
    print(f"unmatched item names: {len(bad_items)}", bad_items[:lim])
    print(f"unmatched owners: {len(unmatched_owners)}", unmatched_owners[:lim])
    print(f"shops with more items than their inv has slots: {len(no_inv_fit)}", no_inv_fit[:lim])


if __name__ == "__main__":
    main()
