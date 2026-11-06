import { test } from '@jest/globals';
import * as fs from 'fs';
import {
  calculatePlayerVsNpc, findEquipmentById, getTestMonster, getTestPlayer, findSpell,
} from '@/tests/utils/TestUtils';
import { Prayer } from '@/enums/Prayer';
import { getCombatStylesForCategory } from '@/utils';

type Row = { id: string; maxHit: number; maxAttackRoll: number };

test('tickperfect parity matrix', () => {
  const monster = getTestMonster('Abyssal demon', 'Standard');
  const rows: Row[] = [];
  const melee = {
    unarmed: {},
    whip: { weapon: 4151 },
    dscim: { weapon: 4587 },
    ags: { weapon: 11802 },
    gmaul: { weapon: 4153 },
    claws: { weapon: 13652 },
    dds: { weapon: 1215 },
    whipRune: { weapon: 4151, head: 1163, body: 1127, legs: 1079, shield: 1201 },
    whipMax: { weapon: 4151, head: 10828, cape: 6570, neck: 6585, body: 10551, legs: 4087, hands: 7462, feet: 11840, ring: 6737, shield: 12954 },
  } as Record<string, Record<string, number>>;
  const ranged = {
    rcb: { weapon: 9185, ammo: 9144 },
    msb: { weapon: 861, ammo: 892 },
    rcbMax: { weapon: 9185, ammo: 9144, head: 12931, cape: 10499, neck: 6585, body: 2503, legs: 2497, hands: 7462, feet: 6328 },
  } as Record<string, Record<string, number>>;
  const prayersMelee: Record<string, Prayer[]> = { none: [], piety: [Prayer.PIETY], us_ir: [Prayer.ULTIMATE_STRENGTH, Prayer.INCREDIBLE_REFLEXES], burst: [Prayer.BURST_OF_STRENGTH, Prayer.CLARITY_OF_THOUGHT] };
  const prayersRanged: Record<string, Prayer[]> = { none: [], rigour: [Prayer.RIGOUR], eagle: [Prayer.EAGLE_EYE], sharp: [Prayer.SHARP_EYE] };
  const levels = [1, 10, 70, 99];

  const gear = (g: Record<string, number>) => Object.fromEntries(Object.entries(g).map(([k, v]) => [k, findEquipmentById(v)]));

  for (const [gname, g] of Object.entries(melee)) {
    const eq = gear(g);
    const styles = getCombatStylesForCategory((eq.weapon?.category) ?? (getTestPlayer(monster, {}).equipment.weapon?.category as any) ?? ('' as any));
    for (const [pname, prayers] of Object.entries(prayersMelee)) {
      for (const lvl of levels) {
        styles.forEach((style, si) => {
          if (style.type !== 'stab' && style.type !== 'slash' && style.type !== 'crush') return;
          const p = getTestPlayer(monster, { skills: { atk: lvl, str: lvl, def: 99, hp: 99 }, prayers, equipment: eq as any, style });
          const r = calculatePlayerVsNpc(monster, p);
          rows.push({ id: `melee|${gname}|${pname}|${lvl}|${si}|${style.type}|${style.stance}`, maxHit: r.maxHit, maxAttackRoll: r.maxAttackRoll });
        });
      }
    }
  }
  for (const [gname, g] of Object.entries(ranged)) {
    const eq = gear(g);
    const styles = getCombatStylesForCategory(eq.weapon!.category);
    for (const [pname, prayers] of Object.entries(prayersRanged)) {
      for (const lvl of levels) {
        styles.forEach((style, si) => {
          if (style.type !== 'ranged') return;
          const p = getTestPlayer(monster, { skills: { ranged: lvl, def: 99, hp: 99 }, prayers, equipment: eq as any, style });
          const r = calculatePlayerVsNpc(monster, p);
          rows.push({ id: `ranged|${gname}|${pname}|${lvl}|${si}|${style.type}|${style.stance}`, maxHit: r.maxHit, maxAttackRoll: r.maxAttackRoll });
        });
      }
    }
  }
  const spells = ['Ice Barrage', 'Ice Blitz', 'Ice Burst', 'Ice Rush'];
  const magicPrayers: Record<string, Prayer[]> = { none: [], augury: [Prayer.AUGURY], might: [Prayer.MYSTIC_MIGHT] };
  const magicGear = { staff: { weapon: 4675 }, staffOccult: { weapon: 4675, neck: 12002 } } as Record<string, Record<string, number>>;
  for (const [gname, g] of Object.entries(magicGear)) {
    const eq = gear(g);
    for (const spellName of spells) {
      for (const [pname, prayers] of Object.entries(magicPrayers)) {
        for (const lvl of [94, 99]) {
          const p = getTestPlayer(monster, {
            skills: { magic: lvl, def: 99, hp: 99 }, prayers, equipment: eq as any, spell: findSpell(spellName),
            style: { name: 'Spell', type: 'magic', stance: 'Manual Cast' },
          } as any);
          const r = calculatePlayerVsNpc(monster, p);
          rows.push({ id: `magic|${gname}|${spellName}|${pname}|${lvl}`, maxHit: r.maxHit, maxAttackRoll: r.maxAttackRoll });
        }
      }
    }
  }
  fs.writeFileSync('/tmp/claude-0/ref/dps-matrix.json', JSON.stringify(rows, null, 1));
});
