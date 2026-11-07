#!/usr/bin/env python3
"""
Combat spell table from the OSRS Wiki (docs/research/combat-spells.md).

Per spell page the `{{Infobox Spell}}` template gives the values that come straight from the Wiki:

  level      the Magic level needed
  exp        base Magic experience
  damage     base maximum hit (tiered spells are overridden by the Wiki's "unlocked spell" ladder, see LADDER)
  cost       `{{RuneReq|Air=1|Mind=1}}` rune costs, mapped to `Objs` symbol names (RUNES)
  spellbook  Normal / Ancient / Arceuus / Lunar

Everything else (effect, staff, multi, animations) is curated in SPELLS below from the spell pages' prose and the
gameval symbols. The Wiki rarely states animation or graphic ids, so they are matched by symbol name against
`Seqs.java` / `SpotAnims.java`; every such match is "symbol-matched", not verified in a client.

Every symbol that is written to the table must exist in the generated symbols (Components.Magic_Spellbook, Objs, Seqs,
SpotAnims); a missing one is reported and the run fails.

Output (tab-separated, header first, in the output directory):
  combat-spells.tsv

Usage (needs network access to oldschool.runescape.wiki and `./gradlew :symbols:build` once):
  python3 -I tools/wiki-import/combat_spells.py symbols/build/generated/symbols/tp/symbols docs/research

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

COLUMNS = [
    "book", "name", "component", "level", "base_max", "xp", "runes", "staff_required", "effect", "multi",
    "cast_anim", "cast_spotanim", "projectile", "impact_spotanim", "sound", "autocastable", "notes",
]

# `{{RuneReq|<Name>=n}}` key -> `Objs` symbol (cache gameval names).
RUNES = {
    "Air": "AIRRUNE", "Water": "WATERRUNE", "Earth": "EARTHRUNE", "Fire": "FIRERUNE", "Mind": "MINDRUNE",
    "Body": "BODYRUNE", "Chaos": "CHAOSRUNE", "Death": "DEATHRUNE", "Blood": "BLOODRUNE", "Soul": "SOULRUNE",
    "Nature": "NATURERUNE", "Law": "LAWRUNE", "Wrath": "WRATHRUNE", "Astral": "ASTRALRUNE", "Cosmic": "COSMICRUNE",
}

BOOK = {"Normal": "standard", "Ancient": "ancient", "Arceuus": "arceuus", "Lunar": "lunar"}

# Wiki "Strike/Bolt/Blast/Wave/Surge spells": the base max hit of a spell is the value of the highest spell of its class
# the player has unlocked (changed 29 May 2024, Project Rebalance). Value per spell = the one of its own tier when no
# higher-tier spell is unlocked yet, i.e. the lowest figure the Wiki shows for that spell.
LADDER = {
    "Strike": {"Wind": 2, "Water": 4, "Earth": 6, "Fire": 8},
    "Bolt": {"Wind": 9, "Water": 10, "Earth": 11, "Fire": 12},
    "Blast": {"Wind": 13, "Water": 14, "Earth": 15, "Fire": 16},
    "Wave": {"Wind": 17, "Water": 18, "Earth": 19, "Fire": 20},
    "Surge": {"Wind": 21, "Water": 22, "Earth": 23, "Fire": 24},
}
CLASS_ANIM = {
    "Strike": "HUMAN_CASTSTRIKE_STAFF", "Bolt": "HUMAN_CASTSTRIKE_STAFF", "Blast": "HUMAN_CASTSTRIKE_STAFF",
    "Wave": "HUMAN_CASTWAVE_STAFF", "Surge": "HUMAN_CAST_SURGE",
}
ELEMENTS = ["Wind", "Water", "Earth", "Fire"]


def row(book, name, **kw):
    r = {"book": book, "name": name}
    r.update(kw)
    return r


def build_spells():
    s = []
    for cls in ["Strike", "Bolt", "Blast", "Wave", "Surge"]:
        for el in ELEMENTS:
            pre = (el + cls).upper()
            rule = {
                "Strike": "base 2/4/6/8 by highest strike spell unlocked (levels 1/5/9/13)",
                "Bolt": "base 9..12 by highest bolt spell unlocked (levels 17/23/29/35); chaos gauntlets +3 on bolts",
                "Blast": "base 13..16 by highest blast spell unlocked (levels 41/47/53/59)",
                "Wave": "base 17..20 by highest wave spell unlocked (levels 62/65/70/75)",
                "Surge": "base 21..24 by highest surge spell unlocked (levels 81/85/90/95)",
            }[cls]
            extra = "; OPEN-S1: unstaffed variant HUMAN_CASTSTRIKE / HUMAN_CASTWAVE exists, which one a staffless cast uses is unverified" if cls != "Surge" else ""
            s.append(row(
                "standard", f"{el} {cls}", base_max=str(LADDER[cls][el]), effect="none", multi="no",
                cast_anim=CLASS_ANIM[cls], cast_spotanim=pre + "_CASTING", projectile=pre + "_TRAVEL",
                impact_spotanim=pre + "_IMPACT", autocastable="yes",
                notes=f"{rule}; +max vs elemental weakness ({el.lower()}); any staff autocasts{extra}"))
    # debuffs
    s.append(row("standard", "Confuse", base_max="0", effect="drain attack 5%", multi="no",
                 cast_anim="HUMAN_CASTCONFUSE_STAFF", cast_spotanim="CONFUSE_CASTING", projectile="CONFUSE_TRAVEL",
                 impact_spotanim="CONFUSE_IMPACT", autocastable="no",
                 notes="only castable if target stats not already lowered; xp on hit or splash; unstaffed variant HUMAN_CASTCONFUSE"))
    s.append(row("standard", "Weaken", base_max="0", effect="drain strength 5%", multi="no",
                 cast_anim="HUMAN_CASTWEAKEN_STAFF", cast_spotanim="WEAKEN_CASTING", projectile="WEAKEN_TRAVEL",
                 impact_spotanim="WEAKEN_IMPACT", autocastable="no",
                 notes="only castable if target stats not already lowered; xp on hit or splash; unstaffed variant HUMAN_CASTWEAKEN"))
    s.append(row("standard", "Curse", base_max="0", effect="drain defence 5%", multi="no",
                 cast_anim="HUMAN_CASTCURSE_STAFF", cast_spotanim="CURSE_CASTING", projectile="CURSE_TRAVEL",
                 impact_spotanim="CURSE_IMPACT", autocastable="no",
                 notes="only castable if target stats not already lowered; xp on hit or splash; unstaffed variant HUMAN_CASTCURSE"))
    s.append(row("standard", "Vulnerability", base_max="0", effect="drain defence 10% (15% with tome of water)", multi="no",
                 cast_anim="", cast_spotanim="VULNERABILITY_CASTING", projectile="VULNERABILITY_TRAVEL",
                 impact_spotanim="VULNERABILITY_IMPACT", autocastable="no",
                 notes="only castable if target stats not already lowered; OPEN-S2: no player-anim symbol (Seqs has VULNERABILITY_CASTING = the graphic's own seq 694, no HUMAN_CASTVULNERABILITY)"))
    s.append(row("standard", "Enfeeble", base_max="0", effect="drain strength 10%", multi="no",
                 cast_anim="HUMAN_CASTENFEEBLE_STAFF", cast_spotanim="ENFEEBLE_CASTING", projectile="ENFEEBLE_TRAVEL",
                 impact_spotanim="ENFEEBLE_IMPACT", autocastable="no",
                 notes="only castable if target stats not already lowered; unstaffed variant HUMAN_CASTENFEEBLE"))
    s.append(row("standard", "Stun", base_max="0", effect="drain attack 10%", multi="no",
                 cast_anim="HUMAN_CASTSTUN_STAFF", cast_spotanim="STUN_CASTING", projectile="STUN_TRAVEL",
                 impact_spotanim="STUN_IMPACT", autocastable="no",
                 notes="only castable if target stats not already lowered; unstaffed variant HUMAN_CASTSTUN"))
    # binds
    bind_note = ("freeze only on a successful hit; +2 ticks per swampbark piece; 'The holding effect is beginning to fade.' near the end; "
                 "cast/travel graphic shared with Entangle (symbol-matched, OPEN-S3); unstaffed variant HUMAN_CASTENTANGLE")
    s.append(row("standard", "Bind", base_max="0", effect="freeze 8 ticks", multi="no",
                 cast_anim="HUMAN_CASTENTANGLE_STAFF", cast_spotanim="ENTANGLE_CASTING", projectile="ENTANGLE_TRAVEL",
                 impact_spotanim="BIND_IMPACT", autocastable="no",
                 notes=bind_note + "; swampbark max 14 ticks; splash xp 30 (hit 31)"))
    s.append(row("standard", "Snare", base_max="2", effect="freeze 16 ticks", multi="no",
                 cast_anim="HUMAN_CASTENTANGLE_STAFF", cast_spotanim="ENTANGLE_CASTING", projectile="ENTANGLE_TRAVEL",
                 impact_spotanim="SNARE_IMPACT", autocastable="no",
                 notes=bind_note + "; swampbark max 22 ticks; splash xp 60 (hit 61)"))
    s.append(row("standard", "Entangle", base_max="5", effect="freeze 24 ticks", multi="no",
                 cast_anim="HUMAN_CASTENTANGLE_STAFF", cast_spotanim="ENTANGLE_CASTING", projectile="ENTANGLE_TRAVEL",
                 impact_spotanim="ENTANGLE_IMPACT", autocastable="no",
                 notes=bind_note + "; swampbark max 30 ticks; 5 ticks freeze immunity after it ends; PvP: cancelled if caster moves >10 tiles away; splash xp 70 (hit 89)"))
    s.append(row("standard", "Crumble Undead", base_max="15", effect="none", multi="no",
                 cast_anim="HUMAN_CASTCRUMBLEUNDEAD_STAFF", cast_spotanim="CRUMBLEUNDEAD_CASTING",
                 projectile="CRUMBLEUNDEAD_TRAVEL", impact_spotanim="CRUMBLEUNDEAD_IMPACT", autocastable="yes",
                 notes=("undead only ('This spell only affects skeletons, zombies, ghosts and shades'); max 18 with salve amulet (ei); "
                        "50% damage (round down) vs zogres/skogres; autocast only with skull sceptre (i), slayer's staff (e), SLAYER_STAFF, SOTD, "
                        "TOXIC_SOTD_CHARGED, STAFF_OF_LIGHT, STAFF_OF_BALANCE, PEST_VOID_KNIGHT_MACE; unstaffed variant HUMAN_CASTCRUMBLEUNDEAD")))
    s.append(row("standard", "Iban Blast", base_max="25", effect="none", multi="no", staff_required="IBANSTAFF;IBANSTAFF_UPGRADED",
                 cast_anim="HUMAN_CASTIBANBLAST", cast_spotanim="IBANBLAST_CASTING", projectile="IBANBLAST_TRAVEL",
                 impact_spotanim="IBANBLAST_IMPACT", autocastable="yes",
                 notes=("quest Underground Pass; Attack 50 to wield; 1 charge per cast on an NPC, 2 on a player; IBANSTAFF holds 120 charges, "
                        "upgraded 2500; autocast only with Iban's staff")))
    s.append(row("standard", "Magic Dart", base_max="10", effect="none", multi="no",
                 staff_required="SLAYER_STAFF;SLAYER_STAFF_ENCHANTED;SOTD;TOXIC_SOTD_CHARGED;STAFF_OF_LIGHT;STAFF_OF_BALANCE",
                 cast_anim="SLAYER_MAGICDART_CAST", cast_spotanim="SLAYER_SALT_SPRINKLE_SPOTANIM", projectile="SLAYER_MAGICDART_TRAVEL",
                 impact_spotanim="SLAYER_MAGICDART_IMPACT", autocastable="yes",
                 notes=("max = floor(Magic/10) + 10; with slayer's staff (e) on task floor(Magic/6) + 13 (1 charge per hit); Slayer 55 needed; "
                        "slayer's staff (e) projectile is SLAYER_MAGICDART_ENCHANTED_TRAVEL / _IMPACT (white); OPEN-S4: cast graphic 327 is named "
                        "SLAYER_SALT_SPRINKLE_SPOTANIM, matched only by adjacency to 328/329")))
    god_note = ("quest Mage Arena I; usable only in the Mage Arena until cast 100 times (OPEN-S5: where the counter lives); "
                "max 30 while CHARGE is active and the god cape is worn; no projectile; wiki gives a cast anim only; tome of fire gives infinite fire runes")
    s.append(row("standard", "Saradomin Strike", base_max="20", effect="drain prayer 1 point", multi="no",
                 staff_required="SARADOMIN_STAFF;STAFF_OF_LIGHT", cast_anim="HUMAN_CASTING", cast_spotanim="", projectile="",
                 impact_spotanim="SARADOMIN_LIGHTNING", autocastable="yes", notes=god_note + "; autocast only with STAFF_OF_LIGHT"))
    s.append(row("standard", "Claws of Guthix", base_max="20", effect="drain defence 1 + 5%", multi="no",
                 staff_required="GUTHIX_STAFF;PEST_VOID_KNIGHT_MACE;STAFF_OF_BALANCE", cast_anim="HUMAN_CASTING", cast_spotanim="",
                 projectile="", impact_spotanim="GUNTHIX_CLAW", autocastable="yes",
                 notes=god_note + "; drain does not stack, not reapplied until Defence is back at base; autocast only with PEST_VOID_KNIGHT_MACE, STAFF_OF_BALANCE"))
    s.append(row("standard", "Flames of Zamorak", base_max="20", effect="drain magic 1 + 5%", multi="no",
                 staff_required="ZAMORAK_STAFF;SOTD;TOXIC_SOTD_CHARGED", cast_anim="HUMAN_CASTING", cast_spotanim="", projectile="",
                 impact_spotanim="ZAMORAK_FLAME", autocastable="yes",
                 notes=(god_note + "; drain does not stack, not reapplied until Magic is back at base; also Thammaron's sceptre (a) / accursed sceptre (a) "
                        "(OPEN-S6: obj symbols not resolved); autocast with every listed staff except ZAMORAK_STAFF")))
    s.append(row("standard", "Tele Block", base_max="0", effect="teleblock 500 ticks (250 if Protect from Magic was up)", multi="no",
                 cast_anim="HUMAN_CASTING_TELE_BLOCK_STAFF", cast_spotanim="", projectile="TELE_BLOCK_TRAVEL_FORFAIL",
                 impact_spotanim="TELE_BLOCK_IMPACT", autocastable="no",
                 notes=("players only, Wilderness / PvP world / Deadman; target immune while blocked and briefly after; removed by killing the caster, leaving the Wilderness, dying; "
                        "OPEN-S7: only the failed-cast travel graphic (1300) and impact (345) have symbols, cast/travel graphics unresolved; "
                        "unstaffed variant HUMAN_CASTING_TELE_BLOCK, also *_STAFF_PRIORITY and *_STAFF_WALKMERGE seqs exist")))
    # ancient
    ancient = {
        "Smoke": dict(rush=("13", "poison severity 10 (1/8 on hit)"), burst=("17", "poison severity 10 (1/8 on hit)"),
                      blitz=("23", "poison severity 20 (1/8 on hit)"), barrage=("27", "poison severity 20 (1/8 on hit)")),
        "Shadow": dict(rush=("14", "drain attack 10%"), burst=("18", "drain attack 10%"),
                       blitz=("24", "drain attack 15%"), barrage=("28", "drain attack 15%")),
        "Blood": dict(rush=("15", "heal 25% of damage dealt"), burst=("21", "heal 25% of damage dealt"),
                      blitz=("25", "heal 25% of damage dealt"), barrage=("29", "heal 25% of damage dealt")),
        "Ice": dict(rush=("16", "freeze 8 ticks"), burst=("22", "freeze 16 ticks"),
                    blitz=("26", "freeze 24 ticks"), barrage=("30", "freeze 32 ticks")),
    }
    spot = {  # (cast, travel, impact) symbol names, "" where the cache has none
        ("Ice", "rush"): ("", "ICE_RUSH_TRAVEL", "ICE_RUSH_IMPACT"),
        ("Ice", "blitz"): ("", "ICE_BLITZ_TRAVEL", "ICE_BLITZ_IMPACT"),
        ("Ice", "burst"): ("", "ICE_BURST_TRAVEL", "ICE_BURST_IMPACT"),
        ("Ice", "barrage"): ("", "ICE_BARRAGE_TRAVEL", "ICE_BARRAGE_IMPACT"),
        ("Blood", "rush"): ("", "BLOOD_RUSH_TRAVEL", "BLOOD_RUSH_IMPACT"),
        ("Blood", "blitz"): ("", "BLOOD_BLITZ_TRAVEL", "BLOOD_BLITZ_IMPACT"),
        ("Blood", "burst"): ("", "", "SPELL_BLOOD_BURST_IMPACT"),
        ("Blood", "barrage"): ("", "", "SPELL_BLOOD_BARRAGE_IMPACT"),
        ("Shadow", "rush"): ("", "SHADOW_RUSH_TRAVEL", "SHADOW_RUSH_IMPACT"),
        ("Shadow", "blitz"): ("", "SHADOW_BLITZ_TRAVEL", "SHADOW_BLITZ_IMPACT"),
        ("Shadow", "burst"): ("", "", "SHADOW_BURST_IMPACT"),
        ("Shadow", "barrage"): ("", "", "SHADOW_BARRAGE_IMPACT"),
        ("Smoke", "rush"): ("", "SMOKE_RUSH_TRAVEL", "SMOKE_RUSH_IMPACT"),
        ("Smoke", "blitz"): ("", "SMOKE_BLITZ_TRAVEL", "SMOKE_BLITZ_IMPACT"),
        ("Smoke", "burst"): ("", "SMOKE_BURST_TRAVEL", "SMOKE_BURST_IMPACT"),
        ("Smoke", "barrage"): ("", "SMOKE_BARRAGE_TRAVEL", "SMOKE_BARRAGE_IMPACT"),
    }
    el_note = {
        "Smoke": ("poison chance 1/8 on hit, initial poison damage = floor((severity + 4) / 5) every 30 ticks, severity falls once per hit; "
                  "ancient sceptres +10% severity; poison skipped on poison-immune targets"),
        "Shadow": "drain applies only if the target is not already drained, never stacks to zero; shadow ancient sceptre also drains Strength and Defence",
        "Blood": "heal is credited even when the target has little HP left; +2% per bloodbark piece up to 35%; ancient sceptre x1.1 (38.5% with full bloodbark)",
        "Ice": "freeze only on a successful hit; ancient sceptre +10% rounded down (Rush none, Burst 17, Blitz 26, Barrage 35); 5 ticks immunity afterwards (docs/mechanics/freeze.md)",
    }
    for el, d in ancient.items():
        for kind, (dmg, eff) in d.items():
            multi = "yes" if kind in ("burst", "barrage") else "no"
            anim = "ZAROS_VERTICAL_CASTING" if multi == "yes" else "ZAROS_CASTING"
            c, t, i = spot[(el, kind)]
            n = f"{el} {kind.capitalize()}"
            note = f"Desert Treasure I; {el_note[el]}"
            if multi == "yes":
                note += "; up to 9 targets in a 3x3 around the target in multi-combat, accuracy rolled per target; barrage distance measured to the defender's south-west tile"
            if (el, kind) in (("Blood", "burst"), ("Blood", "barrage"), ("Shadow", "burst"), ("Shadow", "barrage")):
                note += "; the cache has no travel graphic symbol for this spell (impact only)"
            if (el, kind) == ("Shadow", "barrage"):
                note += "; variants SHADOW_BARRAGE_IMPACT_FAST / _MED exist (OPEN-S8: use unclear)"
            if (el, kind) == ("Ice", "barrage"):
                note += "; ICE_BARRAGE_IMPACT2 / IMPACT3 exist (OPEN-S8: likely secondary-target variants)"
            if (el, kind) == ("Ice", "blitz"):
                note += "; ICE_BLITZ_IMPACT2 / IMPACT3 exist (OPEN-S8)"
            s.append(row("ancient", n, base_max=dmg, effect=eff, multi=multi, cast_anim=anim, cast_spotanim=c, projectile=t,
                         impact_spotanim=i, autocastable="yes",
                         notes=note + "; autocast needs an ancient-capable staff (see md)"))
    # arceuus
    s.append(row("arceuus", "Ghostly Grasp", base_max="12", effect="freeze 2 ticks (10% chance on hit, 20% with Mark of Darkness)", multi="no",
                 cast_anim="HUMAN_SPELLCAST_GRASP", cast_spotanim="GHOSTLY_GRASP_CAST_SPOTANIM", projectile="",
                 impact_spotanim="GHOSTLY_GRASP_HIT_SPOTANIM", autocastable="yes",
                 notes="no quest needed; miss graphic GHOSTLY_GRASP_MISS_SPOTANIM; 2 ticks freeze immunity afterwards; swampbark does not extend; if target has Ward of Arceuus the bind lasts 1 tick"))
    s.append(row("arceuus", "Skeletal Grasp", base_max="17", effect="freeze 3 ticks (25% chance on hit, 50% with Mark of Darkness)", multi="no",
                 cast_anim="HUMAN_SPELLCAST_GRASP", cast_spotanim="SKELETAL_GRASP_CAST_SPOTANIM", projectile="",
                 impact_spotanim="SKELETAL_GRASP_HIT_SPOTANIM", autocastable="yes",
                 notes="A Kingdom Divided; miss graphic SKELETAL_GRASP_MISS_SPOTANIM; same immunity / Ward rules as Ghostly Grasp"))
    s.append(row("arceuus", "Undead Grasp", base_max="24", effect="freeze 4 ticks (50% chance on hit, 100% with Mark of Darkness)", multi="no",
                 cast_anim="HUMAN_SPELLCAST_GRASP", cast_spotanim="UNDEAD_GRASP_CAST_SPOTANIM", projectile="",
                 impact_spotanim="UNDEAD_GRASP_HIT_SPOTANIM", autocastable="yes",
                 notes="A Kingdom Divided; miss graphic UNDEAD_GRASP_MISS_SPOTANIM; same immunity / Ward rules as Ghostly Grasp"))
    dem = ("demonic targets only; accuracy bonus +20% (+40% with Mark of Darkness, +80% with PURGING_STAFF too); with Mark of Darkness +25% damage "
           "(+50% with PURGING_STAFF), both applied after equipment magic damage; no projectile")
    s.append(row("arceuus", "Inferior Demonbane", base_max="16", effect="none", multi="no", cast_anim="HUMAN_SPELLCAST_DEMONBANE",
                 cast_spotanim="INFERIOR_DEMONBANE_CAST_SPOTANIM", projectile="", impact_spotanim="INFERIOR_DEMONBANE_HIT_SPOTANIM",
                 autocastable="yes", notes="no quest needed; " + dem))
    s.append(row("arceuus", "Superior Demonbane", base_max="23", effect="none", multi="no", cast_anim="HUMAN_SPELLCAST_DEMONBANE",
                 cast_spotanim="SUPERIOR_DEMONBANE_CAST_SPOTANIM", projectile="", impact_spotanim="SUPERIOR_DEMONBANE_HIT_SPOTANIM",
                 autocastable="yes", notes="A Kingdom Divided; " + dem))
    s.append(row("arceuus", "Dark Demonbane", base_max="30", effect="none", multi="no", cast_anim="HUMAN_SPELLCAST_DEMONBANE",
                 cast_spotanim="DARK_DEMONBANE_CAST_SPOTANIM", projectile="", impact_spotanim="DARK_DEMONBANE_HIT_SPOTANIM",
                 autocastable="yes", notes="A Kingdom Divided; " + dem))
    cor = ("A Kingdom Divided; no damage; next successful hit has 50% chance (100% with Mark of Darkness) to corrupt: prayer drain 1, 2, 3 points on the "
           "1st/2nd/3rd activation, activations every 10 ticks (5 with Mark of Darkness); cooldown 50 ticks; cannot corrupt an already corrupted target "
           "or one under Ward of Arceuus; OPEN-S9: player cast anim has no symbol (FX_SPELLCAST_CORRUPTION01 is the graphic's seq; HUMAN_CAST_VICIOUS_STRIKE 8974 is an unverified candidate)")
    s.append(row("arceuus", "Lesser Corruption", base_max="0", effect="corruption: drain prayer 1+2+3 over 30 ticks, 50% chance", multi="no",
                 cast_anim="", cast_spotanim="LESSER_CORRUPTION_CAST_SPOTANIM", projectile="", impact_spotanim="LESSER_CORRUPTION_HIT_SPOTANIM",
                 autocastable="no", notes=cor))
    s.append(row("arceuus", "Greater Corruption", base_max="0", effect="corruption: drain prayer 1+2+3 over 30 ticks, 50% chance (per Lesser Corruption; greater-specific figures not on the page)", multi="no",
                 cast_anim="", cast_spotanim="GREATER_CORRUPTION_CAST_SPOTANIM", projectile="", impact_spotanim="GREATER_CORRUPTION_HIT_SPOTANIM",
                 autocastable="no", notes=cor + "; OPEN-S10: the Greater Corruption page text states no effect numbers (copied from Lesser)"))
    # lunar
    s.append(row("lunar", "Vengeance", base_max="0", effect="reflect 75% of the next hit taken back to the attacker; cooldown 50 ticks; 'Taste vengeance!'", multi="no",
                 cast_anim="VENGEANCE_SPELL_ANIM_NOSTALLING", cast_spotanim="QUEST_LUNAR_SPELLBOOK_VENGEANCE_SPOT_ANIM", projectile="",
                 impact_spotanim="", autocastable="no",
                 notes=("effect only (no damage roll); wears off on logout or teleporting into a POH; OPEN-S11: VENGEANCE_SPELL_ANIM_STALLING is the alternative anim, "
                        "cast graphic names carry 'QUEST_LUNAR_SPELLBOOK' and the 725/726 assignment vs Vengeance Other is unverified")))
    s.append(row("lunar", "Vengeance Other", base_max="0", effect="lets the target reflect 75% of the next hit it takes; cooldown 50 ticks", multi="no",
                 cast_anim="", cast_spotanim="QUEST_LUNAR_SPELLBOOK_VENGEANCE_OTHER_SPOT_ANIM", projectile="", impact_spotanim="",
                 autocastable="no",
                 notes="effect only; target needs Accept Aid on; message 'You have the power of vengeance!'; OPEN-S11: caster anim has no symbol"))
    return s


def api(title):
    q = urllib.parse.urlencode({"action": "parse", "page": title, "prop": "wikitext", "format": "json", "redirects": 1})
    req = urllib.request.Request(API + "?" + q, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)["parse"]["wikitext"]["*"]


def infobox(text):
    m = re.search(r"\{\{Infobox Spell(.*?)\n\}\}", text, re.S)
    out = {}
    if not m:
        return out
    for line in m.group(1).split("\n"):
        mm = re.match(r"\|\s*(\w+)\s*=\s*(.*)", line)
        if mm:
            out[mm.group(1)] = mm.group(2).strip()
    return out


def runes(cost):
    m = re.search(r"\{\{RuneReq\|([^}]*)\}\}", cost)
    parts = []
    for kv in m.group(1).split("|") if m else []:
        k, _, v = kv.partition("=")
        if k not in RUNES:
            raise SystemExit(f"unknown rune {k!r}")
        parts.append(f"{RUNES[k]}:{int(v)}")
    return ";".join(parts)


def load_symbols(sym_dir):
    def names(files, inner=None):
        found = set()
        for f in files:
            with open(os.path.join(sym_dir, f), encoding="utf-8") as fh:
                text = fh.read()
            if inner:
                a = text.index(f"class {inner} ")
                text = text[a:text.index("\n    }", a)]
            found.update(re.findall(r"public static final int (\w+) =", text))
        return found

    return {
        "component": names(["Components.java"], "Magic_Spellbook"),
        "obj": names(["Objs.java", "Objs$1.java"]),
        "seq": names(["Seqs.java"]),
        "spot": names(["SpotAnims.java"]),
    }


def main():
    sym_dir, out_dir = sys.argv[1], sys.argv[2]
    syms = load_symbols(sym_dir)
    bad = []

    def check(kind, value, where):
        for v in filter(None, value.split(";")):
            if v.split(":")[0] not in syms[kind]:
                bad.append(f"{where}: {kind} symbol {v!r} missing")

    rows = []
    for sp in build_spells():
        name = sp["name"]
        wiki = infobox(api(name))
        time.sleep(0.2)
        if not wiki:
            bad.append(f"{name}: no Infobox Spell")
            continue
        sp["component"] = "TELEPORT_BLOCK" if name == "Tele Block" else name.upper().replace(" ", "_")
        sp["level"] = wiki["level"]
        sp["xp"] = wiki["exp"]
        sp["runes"] = runes(wiki["cost"])
        if BOOK.get(wiki.get("spellbook", "")) != sp["book"]:
            bad.append(f"{name}: spellbook {wiki.get('spellbook')} != {sp['book']}")
        sp.setdefault("staff_required", "")
        sp["sound"] = ""  # the Wiki infobox gives only an .ogg file name, never a sound id
        sp["notes"] = "symbol-matched anims; " + sp["notes"]
        check("component", sp["component"], name)
        check("obj", sp["runes"], name)
        check("obj", sp["staff_required"], name)
        check("seq", sp["cast_anim"], name)
        for col in ("cast_spotanim", "projectile", "impact_spotanim"):
            check("spot", sp[col], name)
        rows.append(sp)
    if bad:
        print("\n".join(bad), file=sys.stderr)
        raise SystemExit(1)
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "combat-spells.tsv"), "w", encoding="utf-8") as f:
        f.write("\t".join(COLUMNS) + "\n")
        for sp in rows:
            f.write("\t".join(sp.get(c, "") for c in COLUMNS) + "\n")
    print(f"{len(rows)} spells")


if __name__ == "__main__":
    main()
