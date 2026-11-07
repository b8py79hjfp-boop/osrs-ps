#!/usr/bin/env python3
"""Generate content/combat CombatSpellTable.kt from docs/research/combat-spells.tsv.

Usage: python3 -I tools/wiki-import/combat_spells_kt.py <symbols dir> <tsv> <out.kt>

The TSV is the Wiki import of combat_spells.py (CC BY-NC-SA 3.0). Every symbol name is checked against the generated
symbols, so a renamed gameval fails here instead of at compile time. Effects are mapped per spell from the TSV `effect`
column and the rules in docs/mechanics/combat-magic.md; spells handled elsewhere (Tele Block, Vengeance: content/pvp;
the corruption buffs: CombatBuffs.kt) are left out.
"""
import csv
import os
import re
import sys

SKIP = {"TELEPORT_BLOCK", "VENGEANCE", "VENGEANCE_OTHER", "LESSER_CORRUPTION", "GREATER_CORRUPTION"}

# Strike..Surge ladders (Wiki, 29 May 2024): (unlock level, max) of the four spells of each class.
LADDERS = {
    "STRIKE": [(1, 2), (5, 4), (9, 6), (13, 8)],
    "BOLT": [(17, 9), (23, 10), (29, 11), (35, 12)],
    "BLAST": [(41, 13), (47, 14), (53, 15), (59, 16)],
    "WAVE": [(62, 17), (65, 18), (70, 19), (75, 20)],
    "SURGE": [(81, 21), (85, 22), (90, 23), (95, 24)],
}
# Splash xp where the Wiki gives one different from the hit xp (Bind, Snare, Entangle pages).
SPLASH_XP = {"BIND": 30.0, "SNARE": 60.0, "ENTANGLE": 70.0}
ELEMENTS = {"WIND": "AIR", "WATER": "WATER", "EARTH": "EARTH", "FIRE": "FIRE"}

# Player stat ids (RuneLite Skill order) and NPC level indices (NpcType stats order).
P_STAT = {"attack": 0, "defence": 1, "strength": 2, "prayer": 5, "magic": 6}
N_STAT = {"attack": 0, "defence": 1, "strength": 2, "magic": 5, "prayer": -1}


def symbols(sym_dir, cls):
    names = set()
    for f in os.listdir(sym_dir):
        if f == f"{cls}.java" or f.startswith(f"{cls}$"):
            names |= set(re.findall(r"public static final int (\w+) =", open(os.path.join(sym_dir, f)).read()))
    return names


def effect(comp, text):
    t = text.lower()
    if comp in ("CONFUSE", "WEAKEN", "CURSE", "VULNERABILITY", "ENFEEBLE", "STUN"):
        stat = re.search(r"drain (\w+) (\d+)%", t)
        return f'SpellEffect.Debuff({P_STAT[stat.group(1)]}, {N_STAT[stat.group(1)]}, {stat.group(2)})'
    if comp in ("BIND", "SNARE", "ENTANGLE"):
        return f"SpellEffect.Bind({re.search(r'freeze (\d+)', t).group(1)})"
    if comp.startswith("ICE_"):
        return f"SpellEffect.Freeze({re.search(r'freeze (\d+)', t).group(1)})"
    if comp.startswith("SMOKE_"):
        return f"SpellEffect.Poison({re.search(r'severity (\d+)', t).group(1)})"
    if comp.startswith("SHADOW_"):
        return f"SpellEffect.Drain(0, 0, {re.search(r'(\d+)%', t).group(1)}, 0)"
    if comp.startswith("BLOOD_"):
        return "SpellEffect.Heal(25)"
    if comp == "SARADOMIN_STRIKE":
        return "SpellEffect.Drain(5, -1, 0, 1)"
    if comp == "CLAWS_OF_GUTHIX":
        return "SpellEffect.Drain(1, 1, 5, 1)"
    if comp == "FLAMES_OF_ZAMORAK":
        return "SpellEffect.Drain(6, 5, 5, 1)"
    if comp.endswith("_GRASP"):
        m = re.search(r"freeze (\d+) ticks \((\d+)% chance", t)
        return f"SpellEffect.Grasp({m.group(2)}, {m.group(1)})"
    return "SpellEffect.None"


def main():
    sym_dir, tsv, out = sys.argv[1], sys.argv[2], sys.argv[3]
    objs, seqs, spots = symbols(sym_dir, "Objs"), symbols(sym_dir, "Seqs"), symbols(sym_dir, "SpotAnims")
    comps = set(re.findall(r"public static final int (\w+) =",
                           open(os.path.join(sym_dir, "Components.java")).read().split("class Magic_Spellbook")[1]
                           .split("public static final class")[0]))
    missing = []

    def ref(kind, names, name):
        if not name:
            return "-1"
        if name not in names:
            missing.append(f"{kind}.{name}")
        return f"{kind}.{name}"

    rows = []
    for r in csv.DictReader(open(tsv), delimiter="\t"):
        comp = r["component"]
        if comp in SKIP:
            continue
        if comp not in comps:
            missing.append(f"Components.Magic_Spellbook.{comp}")
        runes = ", ".join(f"{ref('Objs', objs, n)} to {c}" for n, c in (p.split(":") for p in r["runes"].split(";") if p))
        staves = ", ".join(ref("Objs", objs, s) for s in r["staff_required"].split(";") if s)
        anim = r["cast_anim"]
        plain = anim[: -len("_STAFF")] if anim.endswith("_STAFF") and anim[: -len("_STAFF")] in seqs else anim
        parts = comp.split("_")
        ladder = LADDERS.get(parts[-1]) if parts[0] in ELEMENTS and len(parts) == 2 else None
        element = f"Element.{ELEMENTS[parts[0]]}" if ladder else "null"
        only = "SpellTarget.UNDEAD" if comp == "CRUMBLE_UNDEAD" else "SpellTarget.DEMON" if comp.endswith("_DEMONBANE") else "SpellTarget.ANY"
        book = {"standard": "STANDARD", "ancient": "ANCIENT", "arceuus": "ARCEUUS"}[r["book"]]
        ladder_kt = "null" if not ladder else "intArrayOf(" + ", ".join(f"{lv}, {mx}" for lv, mx in ladder) + ")"
        rows.append(
            f"""            CombatSpell(
                Components.Magic_Spellbook.{comp},
                "{r['name']}",
                SpellBook.{book},
                level = {r['level']},
                baseMax = {r['base_max']},
                xp = {float(r['xp'])},
                splashXp = {SPLASH_XP.get(comp, float(r['xp']))},
                runes = listOf({runes}),
                staves = intArrayOf({staves}),
                effect = {effect(comp, r['effect'])},
                multi = {'true' if r['multi'] == 'yes' else 'false'},
                staffAnim = {ref('Seqs', seqs, anim)},
                plainAnim = {ref('Seqs', seqs, plain)},
                castSpot = {ref('SpotAnims', spots, r['cast_spotanim'])},
                projectile = {ref('SpotAnims', spots, r['projectile'])},
                impact = {ref('SpotAnims', spots, r['impact_spotanim'])},
                autocast = {'true' if r['autocastable'] == 'yes' else 'false'},
                ladder = {ladder_kt},
                element = {element},
                only = {only},
            ),"""
        )
    if missing:
        sys.exit("missing symbols: " + ", ".join(missing))
    with open(out, "w") as f:
        f.write(
            """package tp.content.combat

import tp.symbols.Components
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

// GENERATED by tools/wiki-import/combat_spells_kt.py from docs/research/combat-spells.tsv (OSRS Wiki spell pages,
// CC BY-NC-SA 3.0). Do not edit by hand: change the TSV or the generator and run it again.

/** Every combat spell cast at a target (docs/mechanics/combat-magic.md CM1). */
@Suppress("LargeClass")
internal object CombatSpellTable {
    @Suppress("MagicNumber")
    val ALL: List<CombatSpell> =
        listOf(
"""
            + "\n".join(rows)
            + """
        )
}
"""
        )
    print(f"wrote {len(rows)} spells to {out}", file=sys.stderr)


if __name__ == "__main__":
    main()
