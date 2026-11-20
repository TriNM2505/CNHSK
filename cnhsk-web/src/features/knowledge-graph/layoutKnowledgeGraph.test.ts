import { describe, expect, it } from 'vitest'

import { SAMPLE_TREE } from './fixtures'
import { buildGraphModel } from './graphModel'
import { NODE_HEIGHT, NODE_WIDTH, layoutKnowledgeGraph } from './layoutKnowledgeGraph'

describe('layoutKnowledgeGraph', () => {
  it('places every node and keeps lower HSK levels to the left', async () => {
    const model = buildGraphModel(SAMPLE_TREE)
    const { positions } = await layoutKnowledgeGraph(model)

    expect(positions.size).toBe(SAMPLE_TREE.length)
    const maxX = (level: number) =>
      Math.max(
        ...SAMPLE_TREE.filter((n) => n.hskLevel === level).map((n) => positions.get(n.kid)!.x),
      )
    const minX = (level: number) =>
      Math.min(
        ...SAMPLE_TREE.filter((n) => n.hskLevel === level).map((n) => positions.get(n.kid)!.x),
      )
    expect(maxX(1)).toBeLessThan(minX(2))
    expect(maxX(2)).toBeLessThan(minX(3))
  })

  it('returns the routed points of every edge, from the right side of the source to the left side of the target', async () => {
    const model = buildGraphModel(SAMPLE_TREE)
    const { edgePoints, positions } = await layoutKnowledgeGraph(model)

    expect(edgePoints.size).toBe(model.edges.length)
    for (const edge of model.edges) {
      const points = edgePoints.get(edge.id)!
      const source = positions.get(edge.source)!
      const target = positions.get(edge.target)!
      expect(points.length).toBeGreaterThanOrEqual(2)
      expect(points[0]!.x).toBeCloseTo(source.x + NODE_WIDTH, 0)
      expect(points[0]!.y).toBeGreaterThanOrEqual(source.y)
      expect(points[0]!.y).toBeLessThanOrEqual(source.y + NODE_HEIGHT)
      expect(points.at(-1)!.x).toBeCloseTo(target.x, 0)
    }
  })

  it('lays each domain out as its own block, side by side in domain order', async () => {
    const model = buildGraphModel(SAMPLE_TREE)
    const { edgePoints, groups, positions } = await layoutKnowledgeGraph(model, {
      groupByDomain: true,
    })

    expect(groups.map((group) => group.domain)).toEqual(['TV', 'NP', 'NL'])
    for (let index = 1; index < groups.length; index++) {
      const previous = groups[index - 1]!
      expect(groups[index]!.x).toBeGreaterThan(previous.x + previous.width)
    }
    for (const node of SAMPLE_TREE) {
      const group = groups.find((candidate) => candidate.domain === node.domain)!
      const position = positions.get(node.kid)!
      expect(position.x).toBeGreaterThanOrEqual(group.x)
      expect(position.x + NODE_WIDTH).toBeLessThanOrEqual(group.x + group.width)
      expect(position.y + NODE_HEIGHT).toBeLessThanOrEqual(group.y + group.height)
    }
    // Links inside a domain keep their routed path; links between domains have none and fall back.
    expect(edgePoints.get('prereq:NP2-913->NP3-904')![0]!.x).toBeCloseTo(
      positions.get('NP2-913')!.x + NODE_WIDTH,
      0,
    )
    expect(edgePoints.has('support:NP3-904->NL3-906')).toBe(false)
  })

  it('keeps a single layout when grouping is not asked for or only one domain is shown', async () => {
    expect((await layoutKnowledgeGraph(buildGraphModel(SAMPLE_TREE))).groups).toEqual([])
    const npOnly = buildGraphModel(SAMPLE_TREE, { domain: 'NP' })
    expect((await layoutKnowledgeGraph(npOnly, { groupByDomain: true })).groups).toEqual([])
  })

  it('returns an empty layout for an empty graph', async () => {
    const layout = await layoutKnowledgeGraph({ edges: [], nodes: [] })
    expect(layout.positions.size).toBe(0)
    expect(layout.edgePoints.size).toBe(0)
  })
})
