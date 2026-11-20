import { describe, expect, it } from 'vitest'

import { SAMPLE_TREE } from './fixtures'
import { buildGraphModel } from './graphModel'

describe('buildGraphModel', () => {
  it('turns prerequisite and support links into edges pointing from the required node', () => {
    const model = buildGraphModel(SAMPLE_TREE)

    expect(model.edges).toContainEqual({
      id: 'prereq:NP2-913->NP3-904',
      kind: 'prereq',
      source: 'NP2-913',
      target: 'NP3-904',
    })
    expect(model.edges).toContainEqual({
      id: 'support:NP3-904->NL3-906',
      kind: 'support',
      source: 'NP3-904',
      target: 'NL3-906',
    })
    expect(model.stats).toEqual({ nodes: 7, prereqEdges: 5, supportEdges: 2 })
  })

  it('skips links to unknown nodes and self links instead of failing', () => {
    const ids = buildGraphModel(SAMPLE_TREE).edges.map((edge) => edge.id)

    expect(ids.some((id) => id.includes('XX9-999'))).toBe(false)
    expect(ids).not.toContain('prereq:NP3-904->NP3-904')
  })

  it('marks nodes above level 1 without any prerequisite link as orphans', () => {
    const { orphanKids } = buildGraphModel(SAMPLE_TREE)

    expect([...orphanKids]).toEqual(['NL3-906', 'TV2-907'])
  })

  it('filters by domain and level and keeps only edges inside the visible set', () => {
    const byDomain = buildGraphModel(SAMPLE_TREE, { domain: 'NP' })
    expect(byDomain.nodes.map((node) => node.kid)).toEqual([
      'NP1-914',
      'NP1-918',
      'NP2-906',
      'NP2-913',
      'NP3-904',
    ])
    expect(byDomain.stats).toEqual({ nodes: 5, prereqEdges: 5, supportEdges: 0 })

    const byLevel = buildGraphModel(SAMPLE_TREE, { hskLevel: 2 })
    expect(byLevel.nodes.map((node) => node.kid)).toEqual(['NP2-906', 'NP2-913', 'TV2-907'])
    expect(byLevel.edges.map((edge) => edge.id)).toEqual(['prereq:NP2-906->NP2-913'])
  })

  it('keeps orphan detection on the whole tree when a filter hides the linked neighbours', () => {
    const { orphanKids } = buildGraphModel(SAMPLE_TREE, { hskLevel: 3 })

    expect(orphanKids.has('NP3-904')).toBe(false)
  })
})
