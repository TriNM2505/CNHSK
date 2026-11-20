import ELK from 'elkjs/lib/elk.bundled.js'

import type { KnowledgeGraphModel } from './graphModel'

export const NODE_WIDTH = 208
export const NODE_HEIGHT = 72

const elk = new ELK()

export type NodePosition = { x: number; y: number }

/**
 * Layered left-to-right layout. HSK level is used as the ELK partition so lower levels
 * always sit to the left, and prerequisite edges then point rightwards.
 */
export async function layoutKnowledgeGraph(
  model: Pick<KnowledgeGraphModel, 'edges' | 'nodes'>,
): Promise<Map<string, NodePosition>> {
  if (model.nodes.length === 0) return new Map()

  const result = await elk.layout({
    id: 'knowledge-tree',
    layoutOptions: {
      'elk.algorithm': 'layered',
      'elk.direction': 'RIGHT',
      'elk.edgeRouting': 'ORTHOGONAL',
      'elk.layered.spacing.nodeNodeBetweenLayers': '72',
      'elk.partitioning.activate': 'true',
      // Lay out disconnected nodes (orphans) in the same pass so the level columns stay global.
      'elk.separateConnectedComponents': 'false',
      'elk.spacing.nodeNode': '24',
    },
    children: model.nodes.map((node) => ({
      height: NODE_HEIGHT,
      id: node.kid,
      layoutOptions: { 'elk.partitioning.partition': String(node.hskLevel) },
      width: NODE_WIDTH,
    })),
    edges: model.edges.map((edge) => ({
      id: edge.id,
      sources: [edge.source],
      targets: [edge.target],
    })),
  })

  return new Map(
    (result.children ?? []).map((child) => [child.id, { x: child.x ?? 0, y: child.y ?? 0 }]),
  )
}
