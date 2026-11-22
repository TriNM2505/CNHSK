import { describe, expect, it } from 'vitest'

import ben from 'hanzi-writer-data/本.json'
import kou from 'hanzi-writer-data/口.json'
import mu from 'hanzi-writer-data/木.json'
import ren from 'hanzi-writer-data/人.json'
import ri from 'hanzi-writer-data/日.json'
import yi from 'hanzi-writer-data/一.json'

import { matchesCharacter } from './freeformMatch'
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
      expect(matchesCharacter(strokes, medians(data))).toBe(true)
    })

  it('accepts writing that is smaller, off-centre and a little slanted', () => {
    const small = drawn(mu, (p) => ({ x: p.x * 0.5 + p.y * 0.04 + 300, y: p.y * 0.55 + 120 }))
    expect(matchesCharacter(small, medians(mu))).toBe(true)
  })

  it('accepts two strokes joined into one', () => {
    const [first = [], second = [], ...rest] = drawn(kou)
    expect(matchesCharacter([[...first].reverse().concat(second), ...rest], medians(kou))).toBe(true)
  })

  it('rejects a character that lacks a whole stroke', () => {
    expect(matchesCharacter(drawn(mu), medians(ben))).toBe(false)
    expect(matchesCharacter(drawn(kou), medians(ri))).toBe(false)
  })

  it('rejects two strokes too few, a different character and an empty pad', () => {
    expect(matchesCharacter(drawn(mu).slice(0, -2), medians(mu))).toBe(false)
    expect(matchesCharacter(drawn(kou), medians(mu))).toBe(false)
    expect(matchesCharacter([], medians(mu))).toBe(false)
    expect(matchesCharacter([[{ x: 1, y: 1 }]], medians(mu))).toBe(false)
  })
})
