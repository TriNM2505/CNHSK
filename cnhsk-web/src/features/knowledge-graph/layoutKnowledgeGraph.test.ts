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

  it('returns an empty layout for an empty graph', async () => {
    const layout = await layoutKnowledgeGraph({ edges: [], nodes: [] })
    expect(layout.positions.size).toBe(0)
    expect(layout.edgePoints.size).toBe(0)
  })
})
