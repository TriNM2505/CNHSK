import { describe, expect, it } from 'vitest'

import { SUPPORTED_HSK_LEVELS } from '@/shared/ui/LevelBadge'

// eslint-disable-next-line no-restricted-imports -- Only this test consumes the original preview fixtures.
import { knowledgeTreeSample, questionPageSample, questionSamples } from './catalog.preview'

describe('question-bank original fixtures (T-01)', () => {
  const nodes = knowledgeTreeSample.nodes
  const kids = new Set(nodes.map(({ kid }) => kid))

  it('covers six domains with valid original KIDs and consistent prerequisite and support links', () => {
    expect([...new Set(nodes.map(({ domain }) => domain))]).toEqual([
      'NA',
      'CH',
      'TV',
      'NP',
      'NL',
      'VB',
    ])
    expect(kids.size).toBe(nodes.length)
    expect(nodes.some(({ prereqKids }) => prereqKids.length > 0)).toBe(true)
    expect(nodes.some(({ supportKids }) => supportKids.length > 0)).toBe(true)
    for (const node of nodes) {
      expect(node.kid).toMatch(/^(NA|CH|TV|NP|NL|VB)[1-7]-[0-9]{3}$/)
      expect(node.kid.slice(0, 2)).toBe(node.domain)
      expect(Number(node.kid[2])).toBe(node.hskLevel)
      expect(SUPPORTED_HSK_LEVELS).toContain(node.hskLevel)
      expect(node.prereqKids.length).toBeLessThanOrEqual(4)
      for (const kid of [...node.prereqKids, ...node.supportKids]) {
        expect(kids.has(kid)).toBe(true)
        expect(kid).not.toBe(node.kid)
      }
      for (const kid of node.prereqKids) {
        expect(nodes.find((entry) => entry.kid === kid)!.hskLevel).toBeLessThanOrEqual(
          node.hskLevel,
        )
      }
    }
  })

  it('provides twelve questions, two per supported type, and covers later screen states', () => {
    expect(questionSamples).toHaveLength(12)
    expect(new Set(questionSamples.map(({ id }) => id)).size).toBe(12)
    const counts = new Map<string, number>()
    for (const { qtype } of questionSamples) counts.set(qtype, (counts.get(qtype) ?? 0) + 1)
    expect(Object.fromEntries(counts)).toEqual({
      MULTIPLE_CHOICE: 2,
      TRUE_FALSE: 2,
      IMAGE_MATCH: 2,
      SENTENCE_MATCH: 2,
      FILL_BLANK: 2,
      SENTENCE_ORDER: 2,
    })
    expect(new Set(questionSamples.map(({ status }) => status))).toEqual(
      new Set(['APPROVED', 'PENDING_REVIEW', 'ARCHIVED', 'REJECTED']),
    )
    expect(questionSamples.some(({ used }) => used)).toBe(true)
    expect(questionSamples.some(({ used }) => !used)).toBe(true)
    expect(questionSamples.some(({ code }) => code === null)).toBe(true)
  })

  it.each(questionSamples)(
    'keeps question $id valid for $qtype and its knowledge labels',
    (question) => {
      expect(SUPPORTED_HSK_LEVELS).toContain(question.hskLevel)
      expect(question.difficulty).toBeGreaterThanOrEqual(1)
      expect(question.difficulty).toBeLessThanOrEqual(3)
      expect(question.explanation).toBeTruthy()
      expect(question.labels.filter(({ role }) => role === 'PRIMARY')).toHaveLength(1)
      expect(question.labels.filter(({ role }) => role === 'SECONDARY').length).toBeLessThanOrEqual(
        2,
      )
      expect(new Set(question.labels.map(({ kid }) => kid)).size).toBe(question.labels.length)
      for (const { kid, nameVi } of question.labels) {
        expect(kids.has(kid)).toBe(true)
        expect(nodes.find((node) => node.kid === kid)!.nameVi).toBe(nameVi)
      }
      if (question.source === 'IMPORT') expect(question.code).toMatch(/^[A-Z0-9_-]{3,50}$/)
      const keys = question.options.map(({ key }) => key)
      expect(keys).toEqual(['A', 'B', 'C', 'D', 'E', 'F'].slice(0, keys.length))
      expect(new Set(question.options.map(({ text }) => text.trim())).size).toBe(keys.length)
      for (const option of question.options) {
        expect(option.text.length).toBeGreaterThan(0)
        expect(option.text.length).toBeLessThanOrEqual(300)
        if (option.errorKid) {
          expect(option.isCorrect).toBe(false)
          expect(kids.has(option.errorKid)).toBe(true)
          expect(['MULTIPLE_CHOICE', 'IMAGE_MATCH', 'SENTENCE_MATCH']).toContain(question.qtype)
        }
      }
      if (question.qtype === 'FILL_BLANK') {
        expect(question.stem).toContain('___')
        expect(question.options).toEqual([])
        expect(question.correctKey).toBeTruthy()
        expect(question.correctKey!.length).toBeLessThanOrEqual(50)
      } else if (question.qtype === 'SENTENCE_ORDER') {
        expect(keys.length).toBeGreaterThanOrEqual(3)
        expect(keys.length).toBeLessThanOrEqual(6)
        expect(question.correctKey!.split('').sort()).toEqual([...keys].sort())
        expect(question.options.every(({ isCorrect }) => !isCorrect)).toBe(true)
      } else {
        expect(keys.length).toBeGreaterThanOrEqual(2)
        expect(keys.length).toBeLessThanOrEqual(6)
        expect(question.options.filter(({ isCorrect }) => isCorrect)).toHaveLength(1)
        if (question.qtype === 'IMAGE_MATCH') expect(question.imageUrl).toMatch(/^https:\/\//)
        if (question.qtype === 'TRUE_FALSE') {
          expect(question.options.map(({ text }) => text)).toEqual(['Đúng', 'Sai'])
        }
      }
    },
  )

  it('provides a ten-item first page with summaries and the full totalElements', () => {
    expect(questionPageSample).toMatchObject({ page: 0, size: 10, totalElements: 12 })
    expect(questionPageSample.items).toHaveLength(10)
    for (const summary of questionPageSample.items) {
      expect(summary.stemSummary.length).toBeLessThanOrEqual(120)
      expect(summary.primaryKid).toBeTruthy()
      expect(summary).not.toHaveProperty('options')
      expect(summary).not.toHaveProperty('correctKey')
    }
  })
})
