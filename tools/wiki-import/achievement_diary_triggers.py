"""
Trigger table of the achievement diary tasks (docs/mechanics/achievement-diaries.md, "Triggers"): which plugin-api event completes
which task, written by achievement_diaries.py to `diary-triggers.tsv`. Hand-maintained: a row is added only when the content that
produces the event exists in the repository (OPEN-AD4 lists what is missing). Every row names a word of the Wiki task text
(`label`), which the importer and the server check, so a renumbered Wiki page cannot silently move a trigger.

A part is `kind;key=value;...` (see DiaryTriggerData.kt). Boxes are `x1,z1,x2,z2[,level]` world tiles; places the Wiki only
names are given as generous boxes around the place (OPEN-AD14: the Wiki gives no tile geometry for them).
"""

MAGIC = "Magic"

# ---- places (world tiles). Each is a box around the named place; the source of each is its comment.
BOX = {
    # Spell destinations are the tiles content/magic teleports to (SpellTeleports.kt), with a 2-tile margin.
    # Cache loc positions: the Magic guild doors (loc 1732 / 1733) are at x 2584 and 2597, z 3087-3088.
    "magic_guild": "2585,3078,2596,3098",
    # NPC spawns (npc-spawns.tsv): Ranging Guild guards stand at x 2655-2666, z 3415-3438.
    "ranging_guild": "2655,3416,2678,3446",
    # Warriors' Guild guards at (2845,3538), (2840,3550); the guild lies between x 2834 and 2880.
    "warriors_guild": "2833,3534,2880,3558",
    "champions_guild": "3188,3352,3202,3370",
    # NPC spawns: Master Farmers at (1236,3727), (1264,3725), (1247,3750) are the Farming Guild's.
    "farming_guild": "1230,3695,1275,3755",
    # NPC spawns: bronze / iron / steel dragons at x 2632-2663, z 9417-9459 (Brimhaven Dungeon).
    "brimhaven_dungeon": "2560,9400,2720,9530",
    "falador_park": "2980,3360,3030,3400",
    "heroes_guild_basement": "2890,9860,2960,9930",
    "fremennik_mainland": "2590,3550,2800,3800",
    "waterfall_dungeon": "2500,9850,2650,9950",
    "slayer_tower": "3400,3520,3460,3590",
    "shayzien_crypts": "1380,9850,1520,10150",
    "ardougne_north": "2600,3300,2700,3400",
    "falador": "2930,3330,3080,3430",
    "gnome_stronghold": "2380,3395,2520,3530",
    "lumbridge": "3190,3190,3280,3290",
    "catherby_shop": "2790,3425,2815,3450",
    "ardougne_market": "2640,3290,2680,3325",
    "varrock_tea_stall": "3260,3405,3275,3416",
    "al_kharid_mine": "3270,3270,3330,3335",
    "varrock_se_mine": "3270,3350,3305,3380",
    "draynor": "3070,3220,3100,3260",
    "lumber_yard": "3285,3478,3320,3515",
    "falador_east_bank": "3005,3350,3022,3362",
    "fremennik": "2590,3590,2780,3730|2300,3770,2430,3830",
    "seers_catherby": "2680,3450,2745,3510|2780,3410,2860,3470",
    "seers_bank": "2718,3486,2734,3497",
    "lumbridge_cow_pen": "3240,3255,3265,3298",
    "canifis_bank": "3500,3470,3522,3490",
    "ardougne_patch": "2650,3355,2685,3390",
    "catherby_patch": "2790,3450,2830,3480",
    "al_kharid_palace": "3280,3158,3300,3178",
    # Cache enum / map: Ape Atoll is the island x 2690-2830, z 2690-2830; Mos Le'Harmless x 3640-3720, z 2960-3010.
    "ape_atoll": "2690,2690,2830,2830",
    "mos_leharmless": "3640,2960,3720,3010",
    "barbarian_village": "3070,3400,3115,3450",
    "lumbridge_kitchen": "3205,3210,3216,3219,0",
}


def part(kind, **kw):
    """`kind;key=value;...` with keys in a fixed order (stable diffs)."""
    out = [kind]
    for key in ("names", "ids", "op", "stat", "xp", "in", "dest", "reason", "count", "min", "worn", "target", "wild", "seq"):
        if key in kw and kw[key] is not None:
            out.append("%s=%s" % (key, kw[key]))
    return ";".join(out)


def box(*names):
    return "|".join(BOX[n] for n in names)


def cast(xp, x, z):
    """A teleport spell: its Wiki experience, then arrival within 2 tiles of the tile content/magic teleports to."""
    return part("cast", stat=MAGIC, xp=xp, dest="%d,%d,%d,%d" % (x - 2, z - 2, x + 2, z + 2))


def item(names, reason=None, place=None, **kw):
    return part("item", names=names, reason=reason, **({"in": box(*place)} if place else {}), **kw)


def kill(names, place=None, **kw):
    return part("kill", names=names, **({"in": box(*place)} if place else {}), **kw)


def burn(xp, place):
    """Burning a log: the Firemaking experience of that log (content/skills BurnableLog.kt) in the named place."""
    return part("xp", stat="Firemaking", xp=xp, **{"in": box(*place)})


def area(*place):
    return part("area", **{"in": box(*place)})


def master(name):
    return part("npc", names=name, op="Assignment")


def pickpocket(target, place=None):
    return part("item", names="*", reason="pickpocket", target=target, **({"in": box(*place)} if place else {}))


# (area, tier, index, label, parts...). Area / tier numbers: cache enum 595 and 0 easy .. 3 elite.
TRIGGERS = [
    # Teleport spells: the spell's experience, then the player arriving at its destination (SpellTeleports.kt).
    (1, 1, 4, "Ardougne Teleport", cast(61.0, 2662, 3305)),
    (1, 2, 5, "Watchtower", cast(68.0, 2546, 3114)),
    (2, 1, 14, "Falador", cast(48.0, 2964, 3378)),
    (3, 2, 1, "Trollheim", cast(68.0, 2890, 3679)),
    (3, 2, 8, "Waterbirth", cast(71.0, 2546, 3755)),
    (4, 1, 6, "Camelot", cast(55.5, 2757, 3479)),
    (4, 3, 7, "Catherby", cast(92.0, 2801, 3450)),
    (6, 1, 5, "Lumbridge", cast(41.0, 3222, 3218)),
    (8, 1, 8, "Varrock", cast(35.0, 3213, 3424)),
    (8, 2, 4, "Paddewwa", cast(64.0, 3097, 9881)),
    (9, 3, 2, "Ghorrock", cast(106.0, 2976, 3872)),
    (10, 2, 12, "Ape Atoll", cast(74.0, 2798, 2799)),
    # Runecrafting: the runes a craft creates (one transaction), `min` for "simultaneously".
    (0, 2, 4, "nature runes", item("Nature rune", "runecraft")),
    (0, 3, 1, "56 Nature runes", item("Nature rune", "runecraft", min=56)),
    (1, 2, 12, "Death runes", item("Death rune", "runecraft")),
    (2, 2, 1, "140 Mind runes", item("Mind rune", "runecraft", min=140)),
    (2, 3, 1, "252 Air Runes", item("Air rune", "runecraft", min=252)),
    (6, 0, 4, "water runes", item("Water rune", "runecraft")),
    (6, 2, 3, "56 Cosmic runes", item("Cosmic rune", "runecraft", min=56)),
    (6, 3, 5, "140 or more Water runes", item("Water rune", "runecraft", min=140)),
    (8, 0, 12, "Earth runes", item("Earth rune", "runecraft")),
    (8, 3, 5, "100 or more earth runes", item("Earth rune", "runecraft", min=100)),
    # Slayer masters (content/skills Slayer.kt: the "Assignment" op).
    (0, 2, 9, "Slayer Master in Shilo Village", master("Duradel")),
    (6, 1, 10, "Chaeldar", master("Chaeldar")),
    (7, 0, 3, "Slayer Master in Canifis", master("Mazchna")),
    (8, 1, 9, "Vannaka", master("Vannaka")),
    # Kills where the Wiki names a place (the kill is placed by the NPC's tile).
    (0, 2, 10, "metal dragon", kill("Bronze dragon|Iron dragon|Steel dragon", ["brimhaven_dungeon"])),
    (2, 0, 6, "duck", kill("Duck", ["falador_park"])),
    (2, 2, 7, "Blue Dragon", kill("Blue dragon", ["heroes_guild_basement"])),
    (3, 0, 3, "Rock crabs", kill("Rock Crab", ["fremennik_mainland"], count=5)),
    (4, 1, 10, "Fire Giant", kill("Fire giant", ["waterfall_dungeon"])),
    (7, 3, 5, "Abyssal demon", kill("Abyssal demon", ["slayer_tower"])),
    (11, 2, 6, "Zombie", kill("Zombie", ["shayzien_crypts"])),
    # Pickpocketing: the loot of a success, right after the interaction with the named NPC.
    (1, 3, 3, "Pickpocket a Hero", pickpocket("Hero")),
    (1, 1, 8, "master farmer", pickpocket("Master Farmer", ["ardougne_north"])),
    (2, 1, 8, "Falador guard", pickpocket("Guard", ["falador"])),
    (10, 2, 13, "Pickpocket a Gnome", pickpocket("Gnome", ["gnome_stronghold"])),
    (6, 0, 6, "man or woman", pickpocket("Man|Woman", ["lumbridge"])),
    # Shops and stalls.
    (8, 0, 1, "Thessalia", part("npc", ids="10477,10478", op="Trade")),
    (11, 0, 5, "Warrens General Store", part("npc", ids="7913", op="Trade")),
    (4, 0, 2, "candle", item("Candle", "shop buy", ["catherby_shop"])),
    (1, 0, 2, "cake", item("Cake", "stall", ["ardougne_market"])),
    (8, 0, 14, "Tea stall", item("Cup of tea", "stall", ["varrock_tea_stall"])),
    # Agility laps: the course's obstacles in order (content/skills AgilityData.kt; `/` joins a step's alternative locs).
    (6, 0, 1, "Draynor", part("seq", seq="11404,11405,11406,11430,11630,11631,11632")),
    (6, 1, 1, "Al Kharid", part("seq", seq="11633,14398,14402,14403,14404,11634,14409,14399")),
    (8, 1, 13, "Varrock Agility", part("seq", seq="14412,14413,14414,14832,14833,14834,14835,14836,14841")),
    (10, 0, 4, "Gnome agility", part("seq", seq="23145,23134,23559,23557/23558,23560,23135,23138/23139")),
    # Mining, woodcutting.
    (9, 0, 10, "Iron ore in the Wilderness", item("Iron ore", "mining", wild=1)),
    (9, 1, 1, "Mithril ore in the wilderness", item("Mithril ore", "mining", wild=1)),
    (6, 0, 11, "Iron ore at the Al Kharid mine", item("Iron ore", "mining", ["al_kharid_mine"])),
    (8, 0, 3, "south-east mining patch", item("Iron ore", "mining", ["varrock_se_mine"])),
    (11, 3, 2, "Redwood logs", item("Redwood logs", "woodcutting")),
    (7, 1, 3, "Bark", item("Bark", "woodcutting")),
    (6, 1, 8, "willow logs in Draynor", item("Willow logs", "woodcutting", ["draynor"])),
    (8, 0, 7, "dying tree", item("Logs", "woodcutting", ["lumber_yard"])),
    # Herblore, crafting, fletching, sawmill, farming harvests (the product, made in the named place).
    (2, 3, 6, "Saradomin brew", item("Saradomin brew(3)", "herblore", ["falador_east_bank"])),
    (3, 2, 3, "super defence", item("Super defence(3)", "herblore", ["fremennik"])),
    (4, 1, 2, "Super Antipoison", item("Superantipoison(3)", "herblore", ["seers_catherby"])),
    (4, 1, 7, "Maple shortbow", item("Maple shortbow", "fletching", ["seers_bank"])),
    (10, 0, 10, "Oak Shortbow", item("Oak shortbow", "fletching", ["gnome_stronghold"])),
    (6, 1, 7, "coif", item("Coif", "crafting", ["lumbridge_cow_pen"])),
    (7, 3, 4, "Black dragonhide body", item("Black d'hide body", "crafting", ["canifis_bank"])),
    (8, 0, 4, "normal plank", item("Plank", "sawmill", ["lumber_yard"])),
    (8, 1, 10, "20 Mahogany Planks", item("Mahogany plank", "sawmill", ["lumber_yard"], min=20)),
    (8, 3, 2, "Lunar magic to make 20 mahogany planks", item("Mahogany plank", "lunar batch", ["lumber_yard"], min=20)),
    (1, 1, 3, "strawberries", item("Strawberry", "farming-harvest", ["ardougne_patch"])),
    (1, 3, 6, "Torstol", item("Grimy torstol", "farming-harvest", ["ardougne_patch"])),
    (4, 1, 8, "Limpwurt root", item("Limpwurt root", "farming-harvest", ["catherby_patch"])),
    (4, 3, 2, "Dwarf weed", item("Grimy dwarf weed", "farming-harvest", ["catherby_patch"])),
    # Spells that make an item.
    (9, 1, 7, "Earth Orb", item("Earth orb", "charge orb", wild=1)),
    (9, 2, 2, "Air Orb", item("Air orb", "charge orb", wild=1)),
    (6, 2, 1, "Bones to Peaches", item("Peach", "bones to fruit", ["al_kharid_palace"])),
    # Chop and burn: the logs cut and a fire lit with that log's experience, both in the place.
    (3, 0, 10, "oak logs", item("Oak logs", "woodcutting", ["fremennik"]), burn(60.0, ["fremennik"])),
    (6, 0, 7, "oak logs", item("Oak logs", "woodcutting", ["lumbridge"]), burn(60.0, ["lumbridge"])),
    (10, 1, 5, "teak logs", item("Teak logs", "woodcutting", ["ape_atoll"]), burn(105.0, ["ape_atoll"])),
    (10, 2, 6, "Mahogany logs", item("Mahogany logs", "woodcutting", ["ape_atoll"]), burn(157.5, ["ape_atoll"])),
    (7, 2, 4, "Mos Le'Harmless", item("Mahogany logs", "woodcutting", ["mos_leharmless"]), burn(157.5, ["mos_leharmless"])),
    # Cooking, crafting, runecrafting products and the feathers of hunted birds (the bird's own loot).
    (6, 0, 10, "Bread", item("Bread", "cooking", ["lumbridge_kitchen"])),
    (8, 0, 10, "pottery wheel", item("Unfired bowl", "crafting", ["barbarian_village"]), item("Bowl", "crafting", ["barbarian_village"])),
    (2, 0, 7, "mind tiara", item("Mind tiara", "tiara")),
    (3, 0, 1, "Cerulean twitch", item("Blue feather", "hunter")),
    (5, 0, 1, "Golden Warbler", item("Yellow feather", "hunter")),
    (10, 0, 1, "Copper Longtail", item("Orange feather", "hunter")),
    # Entering a place (polled).
    (1, 2, 2, "Magic Guild", area("magic_guild")),
    (2, 2, 10, "Warriors' Guild", area("warriors_guild")),
    (4, 1, 3, "Ranging guild", area("ranging_guild")),
    (8, 1, 2, "Champions' Guild", area("champions_guild")),
    (11, 1, 5, "Farming Guild", area("farming_guild")),
]
