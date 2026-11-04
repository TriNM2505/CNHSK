import { describe, expect, it } from 'vitest'

import { SUPPORTED_HSK_LEVELS } from '@/shared/ui/LevelBadge'

describe('mastery preview foundation (T-01 data for AT-24–26)', () => {
  it('provides 30 original nodes across six domains and HSK 1–3 with valid prerequisites', async () => {
    const { knowledgeTreePreview } = await import('./catalog.preview')
    const nodes = knowledgeTreePreview.nodes
    const kids = new Set(nodes.map(({ kid }) => kid))
    expect(nodes).toHaveLength(30)
    expect(kids.size).toBe(30)
    expect([...new Set(nodes.map(({ domain }) => domain))]).toEqual([
      'NA',
      'CH',
      'TV',
      'NP',
      'NL',
      'VB',
    ])
    expect([...new Set(nodes.map(({ hskLevel }) => hskLevel))]).toEqual([...SUPPORTED_HSK_LEVELS])
    for (const node of nodes) {
      expect(node.kid).toMatch(new RegExp(`^${node.domain}${node.hskLevel}-9\\d{2}$`))
      expect(node.nameVi.trim()).not.toBe('')
      expect(node.nameZh.trim()).not.toBe('')
      for (const kid of node.prereqKids) {
        expect(kids.has(kid)).toBe(true)
        expect(kid).not.toBe(node.kid)
      }
    }
  })

  it('covers all six mastery states with coherent frontier and evidence details', async () => {
    const { knowledgeTreePreview, masteryTreePreview, masteryNodeDetailsPreview } =
      await import('./catalog.preview')
    const nodes = masteryTreePreview.nodes
    expect(nodes.map(({ kid }) => kid)).toEqual(knowledgeTreePreview.nodes.map(({ kid }) => kid))
    expect(new Set(nodes.map(({ state }) => state))).toEqual(
      new Set(['notyet', 'insufficient', 'review', 'consolidate', 'extend', 'inferred']),
    )
    expect(new Set(nodes.map(({ frontier }) => frontier))).toEqual(new Set([true, false]))
    expect(masteryNodeDetailsPreview).toHaveLength(30)
    for (const [index, node] of nodes.entries()) {
      expect(node.confidence).toBeGreaterThanOrEqual(0)
      expect(node.evidenceCount).toBeGreaterThanOrEqual(0)
      expect(node.evidenceCount).toBeLessThanOrEqual(8)
      if (node.value !== null) {
        expect(node.value).toBeGreaterThanOrEqual(0)
        expect(node.value).toBeLessThanOrEqual(100)
      }
      const detail = masteryNodeDetailsPreview[index]!
      expect(detail).toMatchObject(node)
      expect(detail.prereqs.map(({ kid }) => kid)).toEqual(
        knowledgeTreePreview.nodes[index]!.prereqKids,
      )
      expect(detail.evidence).toHaveLength(node.evidenceCount)
      expect(detail.evidence.map(({ answeredAt }) => answeredAt)).toEqual(
        detail.evidence
          .map(({ answeredAt }) => answeredAt)
          .sort()
          .reverse(),
      )
      expect(detail.evidence.reduce((sum, { weight }) => sum + weight, 0)).toBeCloseTo(
        node.confidence,
        3,
      )
      const prereqsReady = detail.prereqs.every(({ state }) =>
        ['consolidate', 'extend', 'inferred'].includes(state),
      )
      expect(node.frontier).toBe(!['extend', 'inferred'].includes(node.state) && prereqsReady)
    }
    const inferred = nodes.find(({ state }) => state === 'inferred')!
    expect(
      knowledgeTreePreview.nodes.some(({ kid, prereqKids }) => {
        const dependent = nodes.find((node) => node.kid === kid)!
        return (
          prereqKids.includes(inferred.kid) &&
          dependent.value !== null &&
          dependent.value >= 80 &&
          dependent.confidence >= 2
        )
      }),
    ).toBe(true)
  })

  it('provides five distinct recommendations with reasons and usable nodes', async () => {
    const { recommendationsPreview, masteryNodeDetailsPreview, knowledgeTreePreview } =
      await import('./catalog.preview')
    expect(recommendationsPreview).toHaveLength(5)
    expect(new Set(recommendationsPreview.map(({ kid }) => kid)).size).toBe(5)
    expect(recommendationsPreview.map(({ kind }) => kind)).toEqual([
      'PREREQ',
      'REVIEW',
      'REVIEW',
      'CONSOLIDATE',
      'NEW',
    ])
    for (const recommendation of recommendationsPreview) {
      const detail = masteryNodeDetailsPreview.find(({ kid }) => kid === recommendation.kid)!
      expect(detail.approvedQuestionCount).toBeGreaterThan(0)
      expect(recommendation.value).toBe(detail.value)
      expect(recommendation.nameVi).toBe(
        knowledgeTreePreview.nodes.find(({ kid }) => kid === detail.kid)!.nameVi,
      )
      expect(recommendation.reason.trim()).not.toBe('')
    }
  })

  it('keeps new learners notyet with only NEW recommendations from the frontier', async () => {
    const { newLearnerMasteryPreview, newLearnerRecommendationsPreview } =
      await import('./catalog.preview')
    expect(newLearnerMasteryPreview.nodes).toHaveLength(30)
    for (const node of newLearnerMasteryPreview.nodes) {
      expect(node).toMatchObject({ state: 'notyet', value: null, confidence: 0, evidenceCount: 0 })
    }
    expect(newLearnerRecommendationsPreview).toHaveLength(5)
    for (const recommendation of newLearnerRecommendationsPreview) {
      expect(recommendation.kind).toBe('NEW')
      expect(recommendation.value).toBeNull()
      expect(
        newLearnerMasteryPreview.nodes.find(({ kid }) => kid === recommendation.kid)?.frontier,
      ).toBe(true)
    }
  })

  it('provides 12 ungraded review items with choices and self-rating cards', async () => {
    const { dueReviewSessionPreview: session, reviewSummaryPreview } =
      await import('./catalog.preview')
    expect(session).toMatchObject({
      source: 'DUE_REVIEW',
      feedback: 'IMMEDIATE',
      status: 'IN_PROGRESS',
      answered: [],
    })
    expect(Date.parse(session.expiresAt) - Date.parse(session.servedAt)).toBe(2 * 60 * 60 * 1000)
    expect(session.items).toHaveLength(12)
    expect(reviewSummaryPreview.dueCount).toBe(session.items.length)
    expect(session.items.map(({ no }) => no)).toEqual(
      Array.from({ length: 12 }, (_, index) => index + 1),
    )
    const choices = session.items.filter(({ type }) => type === 'MEANING_CHOICE')
    const cards = session.items.filter((item) => item.type === 'FLASHCARD_RATING')
    expect(choices).toHaveLength(8)
    expect(cards).toHaveLength(4)
    for (const item of choices) {
      expect(item.options).toHaveLength(4)
      expect(new Set(item.options!.map(({ key }) => key)).size).toBe(4)
      expect(new Set(item.options!.map(({ text }) => text)).size).toBe(4)
      expect(item.prompt).not.toHaveProperty('meaning')
    }
    for (const item of cards) {
      expect(item.skill).toBe('GRAMMAR')
      expect(item.options).toBeUndefined()
    }
    expect(JSON.stringify(session)).not.toMatch(
      /"(?:correct|correctAnswer|correctKey|isCorrect|explanation|score)"\s*:/,
    )
  })

  it('provides empty and ahead states and an empty preview entry list pending pages', async () => {
    const { emptyReviewSummaryPreview, reviewAheadSummaryPreview, nothingDuePreview, previews } =
      await import('./catalog.preview')
    expect(emptyReviewSummaryPreview).toEqual({ dueCount: 0, nextDueAt: null })
    expect(reviewAheadSummaryPreview.dueCount).toBe(0)
    expect(reviewAheadSummaryPreview.nextDueAt).not.toBeNull()
    expect(nothingDuePreview).toEqual({ session: null, reason: 'NOTHING_DUE' })
    expect(previews).toEqual([])
  })
})
