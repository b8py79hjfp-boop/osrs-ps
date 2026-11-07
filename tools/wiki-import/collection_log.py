#!/usr/bin/env python3
"""
Collection log layout from our own cache and its client scripts (docs/mechanics/collection-log.md).

The log is data driven. Tab structs 471..475 (param 682 name, 683 enum of page structs); a page struct has param
689 (name) and 690 (enum of the item objs shown). Client script 4103 maps a page struct to the varbit that colours it
"completed", and script 2735 builds the counter lines above a page (`<label>: <col>` + `get_varp N` + proc 2734).

Output collection-log.tsv (tab-separated, header first), one row per page:
  tab       0 bosses, 1 raids, 2 clues, 3 minigames, 4 other (param 684 of the tab struct)
  page      page struct id (what script 2735 / 4103 switch on)
  index     row index of the page in its tab's list (the list-click slot)
  name      page title (struct param 689)
  complete  varbit script 4103 gives the page, or -1
  counters  `label=varp` pairs joined by `;` (script 2735); empty if the page has none
  items     comma-separated obj ids of the page's enum, in display order (item slots; -1 filtered)

Usage:
  ./gradlew :tools:cache-dump:installDist
  cache-dump config struct --all > structs.txt
  python3 -I tools/wiki-import/collection_log.py tools/cache-dump/build/install/cache-dump/bin/cache-dump structs.txt \
      <dir with 4103.rs2asm and 2735.rs2asm> content/collectionlog/src/main/resources/tp/content/collectionlog
The scripts are the cache's client scripts exported as rs2asm (RuneLite cache tool); the cache is rev 242 / 2757.
"""

import re
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor

TAB_STRUCTS = [471, 472, 473, 474, 475]


def dump(binary, kind, ident):
    out = subprocess.run([binary, "config", kind, str(ident)], capture_output=True, text=True, check=True).stdout
    return out


def enum_values(binary, ident):
    for line in dump(binary, "enum", ident).splitlines():
        if line.startswith("intValues = "):
            return {int(k): int(v) for k, v in re.findall(r"(-?\d+)=(-?\d+)", line)}
    return {}


def struct_params(path):
    out = {}
    for line in open(path):
        m = re.match(r"(\d+) params=\{(.*)\}$", line.strip())
        if m:
            out[int(m.group(1))] = m.group(2)
    return out


def param(raw, key):
    m = re.search(r"(?:^|, )%d=(\"[^\"]*\"|-?\d+)" % key, raw)
    if not m:
        return None
    v = m.group(1)
    return v[1:-1] if v.startswith('"') else int(v)


def complete_varbits(path):
    lines = open(path).read().splitlines()
    switch = {}
    i = lines.index(next(l for l in lines if l.strip() == "switch"))
    i += 1
    while ":" in lines[i]:
        k, v = lines[i].strip().split(": ")
        switch[v] = int(k)
        i += 1
    out = {}
    current = None
    for line in lines[i:]:
        s = line.strip()
        if s.endswith(":") and s.startswith("LABEL"):
            current = s[:-1]
        elif s.startswith("get_varbit") and current in switch:
            out[switch[current]] = int(s.split()[1])
            current = None
    return out


def counters(path):
    lines = open(path).read().splitlines()
    switch = {}
    i = next(n for n, l in enumerate(lines) if l.strip() == "switch") + 1
    while ":" in lines[i] and "LABEL" in lines[i]:
        k, v = lines[i].strip().split(": ")
        switch[v] = int(k)
        i += 1
    out = {}
    current = None
    label = None
    for line in lines[i:]:
        s = line.strip()
        if s.endswith(":") and s.startswith("LABEL"):
            current = switch.get(s[:-1])
            label = None
            if current is not None:
                out.setdefault(current, [])
            continue
        if current is None:
            continue
        m = re.match(r'sconst\s+"(.*?)(?::)?(?: <col=ffffff>)?"$', s)
        if m and "</col>" not in s and s != 'sconst ""':
            label = m.group(1).strip()
            continue
        if s.startswith("get_varp") and label is not None:
            out[current].append("%s=%s" % (label, s.split()[1]))
            label = None
    return out


def main():
    binary, structs_path, scripts, out_dir = sys.argv[1:5]
    structs = struct_params(structs_path)
    varbits = complete_varbits("%s/4103.rs2asm" % scripts)
    counts = counters("%s/2735.rs2asm" % scripts)
    pages = []
    enum_ids = set()
    tab_lists = {}
    for ts in TAB_STRUCTS:
        raw = structs[ts]
        tab = param(raw, 684)
        enum_id = param(raw, 683)
        tab_lists[tab] = enum_values(binary, enum_id)
    for tab, lst in tab_lists.items():
        for index, page in sorted(lst.items()):
            raw = structs[page]
            enum_ids.add(param(raw, 690))
            pages.append((tab, page, index, param(raw, 689), param(raw, 690)))
    with ThreadPoolExecutor(2) as pool:
        items = dict(zip(sorted(enum_ids), pool.map(lambda e: enum_values(binary, e), sorted(enum_ids))))
    with open("%s/collection-log.tsv" % out_dir, "w") as f:
        f.write("tab\tpage\tindex\tname\tcomplete\tcounters\titems\n")
        for tab, page, index, name, enum_id in sorted(pages):
            objs = [str(v) for _, v in sorted(items[enum_id].items()) if v >= 0]
            f.write(
                "%d\t%d\t%d\t%s\t%d\t%s\t%s\n"
                % (tab, page, index, name, varbits.get(page, -1), ";".join(counts.get(page, [])), ",".join(objs))
            )


if __name__ == "__main__":
    main()
