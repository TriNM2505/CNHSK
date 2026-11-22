import { describe, expect, it } from 'vitest'

import ben from 'hanzi-writer-data/本.json'
import kou from 'hanzi-writer-data/口.json'
import mu from 'hanzi-writer-data/木.json'
import ren from 'hanzi-writer-data/人.json'
import ri from 'hanzi-writer-data/日.json'
import shi from 'hanzi-writer-data/士.json'
import yi from 'hanzi-writer-data/一.json'

import { readCharacter } from './freeformMatch'
import type { Medians } from './freeformMatch'
import type { Point } from './path'

const medians = (data: { medians: number[][][] }) => data.medians as unknown as Medians

/** What the learner would draw: the reference centre lines in canvas coordinates (y down). */
function drawn(data: { medians: number[][][] }, transform = (p: Point) => p): Point[][] {
  return data.medians.map((median) => median.map(([x = 0, y = 0]) => transform({ x, y: 900 - y })))
}

describe('free writing check', () => {
  it.each([['木', mu], ['本', ben], ['口', kou], ['人', ren], ['日', ri], ['一', yi]] as const)(
    'accepts %s in any stroke order and direction', (_, data) => {
      const strokes = drawn(data).reverse().map((stroke, i) => (i % 2 ? stroke : [...stroke].reverse()))
      expect(readCharacter(strokes, medians(data))).toBe('complete')
    })

  it('accepts writing that is smaller, off-centre and a little slanted', () => {
    const small = drawn(mu, (p) => ({ x: p.x * 0.5 + p.y * 0.04 + 300, y: p.y * 0.55 + 120 }))
    expect(readCharacter(small, medians(mu))).toBe('complete')
  })

  it('accepts two strokes joined into one', () => {
    const [first = [], second = [], ...rest] = drawn(kou)
    expect(readCharacter([[...first].reverse().concat(second), ...rest], medians(kou))).toBe('joined')
  })

  it('skips a slip of the pen and reads past a failed attempt left on the pad', () => {
    const slip = [{ x: 400, y: 400 }, { x: 404, y: 403 }]
    expect(readCharacter([slip, ...drawn(mu)], medians(mu))).toBe('complete')
    expect(readCharacter([...drawn(kou), ...drawn(kou), ...drawn(mu)], medians(mu))).toBe('complete')
  })

  it('accepts strokes of the wrong length that end in a hook, as people really write them', () => {
    // Traced from a learner's 士: short top line, short stem, a hook at the end of both lines.
    const written = [
      [[129, 121], [169, 120], [209, 120], [249, 123], [278, 123], [271, 115], [276, 106]],
      [[171, 83], [171, 121], [173, 146], [173, 181], [171, 197]],
      [[103, 198], [149, 198], [199, 205], [249, 211], [285, 213], [280, 203], [284, 191]],
    ].map((stroke) => stroke.map(([x = 0, y = 0]) => ({ x, y })))
    expect(readCharacter(written, medians(shi))).toBe('complete')
    expect(readCharacter(written, medians(kou))).toBe('none')
    expect(readCharacter(written, medians(ren))).toBe('none')
  })

  it('rejects a scribble', () => {
    const scribble = Array.from({ length: 60 }, (_, i) => ({ x: 500 + 400 * Math.sin(i * 1.7), y: 500 + 400 * Math.cos(i * 2.3) }))
    for (const data of [mu, ben, kou, ri]) expect(readCharacter([scribble, scribble, scribble, scribble], medians(data))).toBe('none')
  })

  it('rejects a character that lacks a whole stroke', () => {
    expect(readCharacter(drawn(mu), medians(ben))).toBe('none')
    expect(readCharacter(drawn(ren), medians(mu))).toBe('none')
  })

  it('rejects two strokes too few, a different character and an empty pad', () => {
    expect(readCharacter(drawn(mu).slice(0, -2), medians(mu))).toBe('none')
    expect(readCharacter(drawn(kou), medians(mu))).toBe('none')
    expect(readCharacter([], medians(mu))).toBe('none')
    expect(readCharacter([[{ x: 1, y: 1 }]], medians(mu))).toBe('none')
  })
})
