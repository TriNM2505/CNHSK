import type { ElkExtendedEdge } from 'elkjs/lib/elk-api'
import ELK from 'elkjs/lib/elk.bundled.js'

import type { KnowledgeGraphModel } from './graphModel'

export const NODE_WIDTH = 208
export const NODE_HEIGHT = 72

const elk = new ELK()

/** Above this size the layout trades a little ordering quality for speed. */
const LARGE_GRAPH_NODES = 60

export type NodePosition = { x: number; y: number }

export type GraphLayout = {
  /** Routed path of each edge (start, bend points, end), in the same coordinates as the nodes. */
  edgePoints: Map<string, NodePosition[]>
  positions: Map<string, NodePosition>
}

/**
 * Layered left-to-right layout. HSK level is used as the ELK partition so lower levels
 * always sit to the left, and prerequisite edges then point rightwards.
 */
export async function layoutKnowledgeGraph(
  model: Pick<KnowledgeGraphModel, 'edges' | 'nodes'>,
): Promise<GraphLayout> {
  if (model.nodes.length === 0) return { edgePoints: new Map(), positions: new Map() }

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
      // The default effort takes ~1.3 s for the full 186-node tree and freezes the page; 2 keeps it
      // under 0.5 s with slightly more crossings. Small trees keep the default, better ordering.
      ...(model.nodes.length > LARGE_GRAPH_NODES ? { 'elk.layered.thoroughness': '2' } : {}),
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

  // Edges are declared on the root graph, so their sections share the root coordinates of the nodes.
  const edgePoints = new Map<string, NodePosition[]>()
  // elk.layout echoes the input type, which lacks the routed sections ELK adds.
  for (const edge of (result.edges ?? []) as ElkExtendedEdge[]) {
    const section = edge.sections?.[0]
    if (!section) continue
    edgePoints.set(edge.id, [section.startPoint, ...(section.bendPoints ?? []), section.endPoint])
  }

  return {
    edgePoints,
    positions: new Map(
      (result.children ?? []).map((child) => [child.id, { x: child.x ?? 0, y: child.y ?? 0 }]),
    ),
  }
}
