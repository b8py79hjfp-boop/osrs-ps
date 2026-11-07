#!/usr/bin/env python3
"""Convert RS Mod's obj enrichment data (ISC) into content/combat weapons.tsv.

Usage: python3 -I tools/wiki-import/rsmod_weapons.py <rsmod objs.toml> <symbols dir> <out.tsv>

RS Mod (https://github.com/rsmod/rsmod, ISC licence) keeps the server-only weapon data the client cache lacks: the
weapon category the combat tab shows, attack / block / movement animations, attack sounds, speed and range overrides,
and ammo projectiles. Obj names are gameval names; ids are resolved through our generated `Objs` symbols (same names,
upper case). Rows whose name is not a symbol are reported and skipped.
"""
import os
import re
import sys
import tomllib

CATEGORIES = {
    "Unarmed": 0, "Axe": 1, "Blunt": 2, "Bow": 3, "Claw": 4, "Crossbow": 5, "Salamander": 6, "Chinchompas": 7,
    "Gun": 8, "SlashSword": 9, "TwoHandedSword": 10, "Pickaxe": 11, "Polearm": 12, "Polestaff": 13, "Scythe": 14,
    "Spear": 15, "Spiked": 16, "StabSword": 17, "Staff": 18, "Thrown": 19, "Whip": 20, "BladedStaff": 21, "Banner": 22,
    "GodSword": 23, "PoweredStaff": 24, "Bludgeon": 27, "Bulwark": 28,
}
COLUMNS = ["obj", "name", "category", "anim1", "anim2", "anim3", "anim4", "block", "ready", "turn", "walk", "walk_back",
           "walk_left", "walk_right", "run", "speed", "range", "proj_anim", "proj_travel", "proj_launch",
           "proj_launch_double", "sound1", "sound2", "sound3", "sound4", "ammo_recovery"]
KEYS = {"anim1": "anim_stance1", "anim2": "anim_stance2", "anim3": "anim_stance3", "anim4": "anim_stance4",
        "block": "block_anim", "ready": "ready_anim", "turn": "turn_anim", "walk": "walk_anim",
        "walk_back": "walk_anim_back", "walk_left": "walk_anim_left", "walk_right": "walk_anim_right",
        "run": "run_anim", "speed": "speed", "range": "range", "proj_anim": "proj_anim",
        "proj_travel": "projectile_travel", "proj_launch": "projectile_launch",
        "proj_launch_double": "projectile_launch_double", "sound1": "sound_stance1", "sound2": "sound_stance2",
        "sound3": "sound_stance3", "sound4": "sound_stance4", "ammo_recovery": "ammo_recovery_rate"}


def objs(sym_dir):
    ids = {}
    for f in os.listdir(sym_dir):
        if f == "Objs.java" or f.startswith("Objs$"):
            for name, value in re.findall(r"public static final int (\w+) = (\d+);", open(os.path.join(sym_dir, f)).read()):
                ids[name] = int(value)
    return ids


def main():
    src, sym_dir, out = sys.argv[1], sys.argv[2], sys.argv[3]
    ids = objs(sym_dir)
    rows, missing = [], []
    for c in tomllib.load(open(src, "rb"))["config"]:
        keys = set(c) - {"obj", "untradable", "equipment_sound", "destroy_note", "destroy_note_alt", "destroy_header",
                         "dummyitem", "respawn_timer", "metallic_interference", "req_stat1", "req_level1"}
        if not keys:
            continue
        name = c["obj"].upper()
        if name not in ids:
            # Gameval names with "+" (poisoned weapons) are symbols with "_" ("dragon_dagger_p+" -> DRAGON_DAGGER_P_).
            name = re.sub(r"[^A-Z0-9_]", "_", name)
        if name not in ids:
            missing.append(c["obj"])
            continue
        cat = CATEGORIES[c["weapon_category"]] if "weapon_category" in c else ""
        row = [str(ids[name]), c["obj"], str(cat)] + [str(c.get(KEYS[k], "")) for k in COLUMNS[3:]]
        rows.append(row)
    rows.sort(key=lambda r: int(r[0]))
    with open(out, "w") as f:
        f.write("\t".join(COLUMNS) + "\n")
        for r in rows:
            f.write("\t".join(r) + "\n")
    print(f"wrote {len(rows)} objs to {out}; {len(missing)} names not in symbols: {missing[:20]}", file=sys.stderr)


if __name__ == "__main__":
    main()
