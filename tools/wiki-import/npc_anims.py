#!/usr/bin/env python3
"""Derive NPC combat animations (attack per style, block, death) from the cache.

Usage: python3 -I tools/wiki-import/npc_anims.py <anim-groups dir> <symbols dir> <npc-combat.tsv> <out.tsv>

The cache has no attack / defend / death anim on an NPC config (they are server data). An NPC's combat seqs animate the
same skeleton as its stand seq, so they share its frame archive (or keyframe set). Among the seqs of that skeleton the
gameval names (generated `Seqs` symbols) say which is which: *_ATTACK / *_DEFEND / *_DEATH and their variants. When
several match, the one whose name shares the stand seq's prefix and is nearest to it by id wins. Input dumps come from
`cache-dump anim-groups <dir>`. NPCs without a match are left blank (docs/mechanics/npc-combat.md N10, OPEN-N11).
"""
import csv
import re
import sys
from collections import defaultdict

DEATH = re.compile(r"DEATH|_DIE\b|_DIE_|DYING|_DEAD\b")
BLOCK = re.compile(r"BLOCK|DEFEND|DEFENCE|DEFENSE|_DEF\b|_DEF_|PARRY")
MAGIC = re.compile(r"MAGIC|CAST|SPELL|MAGE")
RANGED = re.compile(r"RANGE|SHOOT|THROW|_BOW|ARROW|SPIT|BREATH|FIRE_?BALL")
ATTACK = re.compile(r"ATTACK|ATK|STRIKE|PUNCH|SLASH|STAB|BITE|CLAW|SWIPE|KICK|HEADBUTT|SMASH|BASH|SWING|LUNGE|CHOMP|PECK"
                    r"|STING|WHIP|HIT\b")
NOT_COMBAT = re.compile(r"WALK|READY|STAND|IDLE|TURN|RUN\b|_RUN_|SPAWN|EMOTE|TALK")


def seq_names(sym_dir):
    names = {}
    for name, value in re.findall(r"public static final int (\w+) = (\d+);", open(f"{sym_dir}/Seqs.java").read()):
        names.setdefault(int(value), name)
    return names


def prefix(name):
    return name.split("_")[0] if name else ""


def main():
    groups_dir, sym_dir, combat_tsv, out = sys.argv[1:5]
    names = seq_names(sym_dir)
    group_of, members = {}, defaultdict(list)
    for r in csv.DictReader(open(f"{groups_dir}/seq-groups.tsv"), delimiter="\t"):
        g = int(r["group"])
        if g < 0:
            continue
        group_of[int(r["seq"])] = g
        members[g].append(int(r["seq"]))
    fighters = {r["id"] for r in csv.DictReader(open(combat_tsv), delimiter="\t")}

    def kind(n):
        if not n or NOT_COMBAT.search(n):
            return None
        if DEATH.search(n):
            return "death"
        if BLOCK.search(n):
            return "block"
        if MAGIC.search(n):
            return "magic"
        if RANGED.search(n):
            return "ranged"
        if ATTACK.search(n):
            return "melee"
        return None

    # Each seq is classified once; per skeleton group the candidates of each kind.
    by_kind = defaultdict(lambda: defaultdict(list))
    for g, seqs in members.items():
        for sq in seqs:
            k = kind(names.get(sq, ""))
            if k:
                by_kind[g][k].append(sq)
    rows, found = [], 0
    for r in csv.DictReader(open(f"{groups_dir}/npc-seqs.tsv"), delimiter="\t"):
        if r["npc"] not in fighters:
            continue
        stand = int(r["stand"])
        g = group_of.get(stand)
        base = prefix(names.get(stand, ""))

        def best(k):
            cands = [(0 if prefix(names[sq]) == base else 1, abs(sq - stand), sq) for sq in by_kind[g][k]] if g is not None else []
            return str(min(cands)[2]) if cands else ""

        picked = {k: best(k) for k in ("melee", "ranged", "magic", "block", "death")}
        if picked["melee"] or picked["ranged"] or picked["magic"]:
            found += 1
        rows.append([r["npc"], r["name"], picked["melee"], picked["ranged"], picked["magic"], picked["block"], picked["death"]])
    with open(out, "w") as f:
        f.write("npc\tname\tmelee\tranged\tmagic\tblock\tdeath\n")
        for row in rows:
            f.write("\t".join(row) + "\n")
    print(f"wrote {len(rows)} npcs, {found} with an attack anim, to {out}", file=sys.stderr)


if __name__ == "__main__":
    main()
