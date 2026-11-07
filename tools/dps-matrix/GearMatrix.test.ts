import { test } from '@jest/globals';
import * as fs from 'fs';
import {
  calculatePlayerVsNpc, findEquipment, findEquipmentById, getTestMonster, getTestPlayer, findSpell,
} from '@/tests/utils/TestUtils';
import PlayerVsNPCCalc from '@/lib/PlayerVsNPCCalc';
import { getCombatStylesForCategory } from '@/utils';

// Gear-effect rows for content/combat DpsParityTest (docs/mechanics/gear-effects.md): pre-roll [min, max] and max
// attack roll from the Wiki DPS calculator for passives and set effects, at 99s with no prayers.
// An item by name (and version), or by id where the calculator's name lookup lands on a "(broken)" variant.
type Item = [string, string?] | number;
type Case = {
  id: string; monster: [string, string]; gear: Item[]; style: 'stab' | 'slash' | 'crush' | 'ranged' | 'magic';
  stance?: string; spell?: string; buffs?: Record<string, unknown>;
};

const VOID: Item[] = [8839, 8840, 8842];
const ELITE: Item[] = [13072, 13073, 8842];
const CRYSTAL: Item[] = [['Crystal helm', 'Active'], ['Crystal body', 'Active'], ['Crystal legs', 'Active']];

const cases: Case[] = [
  { id: 'void-melee', monster: ['Abyssal demon', 'Standard'], gear: [['Abyssal whip'], 11665, ...VOID], style: 'slash' },
  { id: 'obsidian', monster: ['Abyssal demon', 'Standard'], gear: [['Tzhaar-ket-om'], ['Obsidian helmet'], ['Obsidian platebody'], ['Obsidian platelegs']], style: 'crush' },
  { id: 'inquisitor', monster: ['Abyssal demon', 'Standard'], gear: [["Inquisitor's mace"], ["Inquisitor's great helm"], ["Inquisitor's hauberk"], ["Inquisitor's plateskirt"]], style: 'crush' },
  { id: 'arclight', monster: ['Abyssal demon', 'Standard'], gear: [['Arclight', 'Charged']], style: 'slash' },
  { id: 'emberlight', monster: ['Greater demon', 'Level 100'], gear: [['Emberlight']], style: 'slash' },
  { id: 'darklight', monster: ['Abyssal demon', 'Standard'], gear: [['Darklight']], style: 'slash' },
  { id: 'dhl', monster: ['Black dragon', 'Level 227'], gear: [['Dragon hunter lance']], style: 'stab' },
  { id: 'keris-breaching', monster: ['Kalphite Soldier', 'Kalphite Lair'], gear: [['Keris partisan of breaching']], style: 'stab' },
  { id: 'salve-e', monster: ['Skeleton', 'Level 21, 1'], gear: [['Abyssal whip'], ['Salve amulet (e)']], style: 'slash' },
  { id: 'mask-melee', monster: ['Abyssal demon', 'Standard'], gear: [['Abyssal whip'], ['Slayer helmet (i)']], style: 'slash', buffs: { onSlayerTask: true } },
  { id: 'fang', monster: ['Abyssal demon', 'Standard'], gear: [["Osmumten's fang"]], style: 'stab' },
  { id: 'colossal', monster: ['Black dragon', 'Level 227'], gear: [['Colossal blade']], style: 'slash' },
  { id: 'soulreaper-3', monster: ['Abyssal demon', 'Standard'], gear: [['Soulreaper axe']], style: 'slash', buffs: { soulreaperStacks: 3 } },
  { id: 'leaf-axe', monster: ['Kurask', 'Normal'], gear: [['Leaf-bladed battleaxe']], style: 'slash' },
  { id: 'chainmace-wild', monster: ['Abyssal demon', 'Wilderness Slayer Cave'], gear: [["Viggora's chainmace", 'Charged']], style: 'crush', buffs: { inWilderness: true } },
  { id: 'tbow-1', monster: ['Abyssal demon', 'Standard'], gear: [['Twisted bow'], ['Dragon arrow', 'Unpoisoned']], style: 'ranged' },
  { id: 'tbow-200', monster: ["Kree'arra", ''], gear: [['Twisted bow'], ['Dragon arrow', 'Unpoisoned']], style: 'ranged' },
  { id: 'tbow-300', monster: ['Zulrah', 'Serpentine'], gear: [['Twisted bow'], ['Dragon arrow', 'Unpoisoned']], style: 'ranged' },
  { id: 'bofa-crystal', monster: ['Abyssal demon', 'Standard'], gear: [['Bow of Faerdhinen', 'Charged'], ...CRYSTAL], style: 'ranged' },
  { id: 'dhcb', monster: ['Black dragon', 'Level 227'], gear: [['Dragon hunter crossbow'], ['Dragon bolts', 'Unpoisoned']], style: 'ranged' },
  { id: 'dhcb-mask', monster: ['Black dragon', 'Level 227'], gear: [['Dragon hunter crossbow'], ['Dragon bolts', 'Unpoisoned'], ['Slayer helmet (i)']], style: 'ranged', buffs: { onSlayerTask: true } },
  { id: 'void-ranged', monster: ['Abyssal demon', 'Standard'], gear: [['Rune crossbow'], ['Runite bolts', 'Unpoisoned'], 11664, ...VOID], style: 'ranged' },
  { id: 'elite-void-ranged', monster: ['Abyssal demon', 'Standard'], gear: [['Rune crossbow'], ['Runite bolts', 'Unpoisoned'], 11664, ...ELITE], style: 'ranged' },
  { id: 'scorching', monster: ['Abyssal demon', 'Standard'], gear: [['Scorching bow'], ['Dragon arrow', 'Unpoisoned']], style: 'ranged' },
  { id: 'atlatl', monster: ['Abyssal demon', 'Standard'], gear: [['Eclipse atlatl'], ['Atlatl dart']], style: 'ranged' },
  { id: 'smoke-bolt', monster: ['Abyssal demon', 'Standard'], gear: [['Smoke battlestaff']], style: 'magic', spell: 'Fire Bolt' },
  { id: 'void-mage', monster: ['Abyssal demon', 'Standard'], gear: [['Staff of fire'], 11663, ...VOID], style: 'magic', spell: 'Fire Surge' },
  { id: 'elite-void-mage', monster: ['Abyssal demon', 'Standard'], gear: [['Staff of fire'], 11663, ...ELITE], style: 'magic', spell: 'Fire Surge' },
  { id: 'tome-fire', monster: ['Abyssal demon', 'Standard'], gear: [['Staff of fire'], ['Tome of Fire', 'Charged']], style: 'magic', spell: 'Fire Surge' },
  { id: 'virtus', monster: ['Abyssal demon', 'Standard'], gear: [['Ancient staff'], ['Virtus mask'], ['Virtus robe top'], ['Virtus robe bottom']], style: 'magic', spell: 'Ice Barrage' },
  { id: 'dhw', monster: ['Black dragon', 'Level 227'], gear: [['Dragon hunter wand']], style: 'magic', spell: 'Fire Surge' },
  { id: 'mask-magic', monster: ['Abyssal demon', 'Standard'], gear: [['Staff of fire'], ['Slayer helmet (i)']], style: 'magic', spell: 'Fire Surge', buffs: { onSlayerTask: true } },
  { id: 'salve-ei-magic', monster: ['Skeleton', 'Level 21, 1'], gear: [['Staff of fire'], ['Salve amulet(ei)']], style: 'magic', spell: 'Fire Surge' },
];

test('tickperfect gear-effect matrix', () => {
  const rows: unknown[] = [];
  for (const c of cases) {
    const monster = getTestMonster(c.monster[0], c.monster[1]);
    const equipment: Record<string, unknown> = {};
    for (const item of c.gear) {
      const piece = typeof item === 'number' ? findEquipmentById(item) : findEquipment(item[0], item[1] ?? '');
      equipment[piece.slot] = piece;
    }
    const weapon = equipment.weapon as { category: never } | undefined;
    const styles = getCombatStylesForCategory(weapon?.category ?? ('' as never));
    const style = c.style === 'magic'
      ? { name: 'Spell', type: 'magic', stance: 'Manual Cast' }
      : styles.find((s) => s.type === c.style && s.stance !== 'Defensive');
    if (!style) throw new Error(`${c.id}: no ${c.style} style`);
    const p = getTestPlayer(monster, {
      skills: {
        atk: 99, str: 99, def: 99, hp: 99, ranged: 99, magic: 99,
      },
      equipment: equipment as never,
      style: style as never,
      spell: c.spell ? findSpell(c.spell) : undefined,
      buffs: c.buffs as never,
    } as never);
    const r = calculatePlayerVsNpc(monster, p);
    const calc = new PlayerVsNPCCalc(p, monster, { loadoutName: 'gear' });
    const [min, max] = calc.getMinAndMax();
    rows.push({
      id: c.id,
      style: c.style,
      stance: style.stance,
      gear: Object.values(equipment).map((e) => (e as { id: number }).id),
      spell: c.spell ? { name: c.spell, ...findSpell(c.spell) } : null,
      buffs: c.buffs ?? {},
      monster: {
        name: monster.name,
        attributes: monster.attributes,
        size: monster.size,
        magic: monster.skills.magic,
        magicAttack: monster.offensive.magic,
      },
      min,
      max,
      maxAttackRoll: r.maxAttackRoll,
    });
  }
  fs.writeFileSync('/tmp/claude-0/ref/gear-matrix.json', JSON.stringify(rows, null, 1));
});
