import { describe, expect, it } from 'vitest'

import { SAMPLE_TREE } from './fixtures'
import { buildGraphModel } from './graphModel'
import { layoutKnowledgeGraph } from './layoutKnowledgeGraph'

describe('layoutKnowledgeGraph', () => {
  it('places every node and keeps lower HSK levels to the left', async () => {
    const model = buildGraphModel(SAMPLE_TREE)
    const positions = await layoutKnowledgeGraph(model)

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

  it('returns an empty layout for an empty graph', async () => {
    expect((await layoutKnowledgeGraph({ edges: [], nodes: [] })).size).toBe(0)
  })
})
