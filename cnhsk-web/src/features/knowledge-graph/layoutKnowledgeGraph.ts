import type { ElkExtendedEdge, ElkNode } from 'elkjs/lib/elk-api'
import ELK from 'elkjs/lib/elk.bundled.js'

import { KNOWLEDGE_DOMAINS, type KnowledgeGraphModel } from './graphModel'
import type { KnowledgeDomain } from '@/features/question-bank/types'

export const NODE_WIDTH = 208
export const NODE_HEIGHT = 72

const elk = new ELK()

/** Above this size the layout trades a little ordering quality for speed. */
const LARGE_GRAPH_NODES = 60
/** Space between domain blocks, the inner margin of a block and the room left for its title. */
const DOMAIN_GAP = 160
const GROUP_PADDING = 24
const GROUP_HEADER = 64

export type NodePosition = { x: number; y: number }

/** Frame of one domain block when every domain is shown; nodes of that domain sit inside it. */
export type DomainGroup = {
  domain: KnowledgeDomain
  height: number
  width: number
  x: number
  y: number
}

export type GraphLayout = {
  /** Routed path of each edge (start, bend points, end), in the same coordinates as the nodes. */
  edgePoints: Map<string, NodePosition[]>
  /** Empty unless the layout was grouped by domain. */
  groups: DomainGroup[]
  positions: Map<string, NodePosition>
}

type GraphSlice = Pick<KnowledgeGraphModel, 'edges' | 'nodes'>

/**
 * Layered left-to-right layout of one slice. HSK level is used as the ELK partition so lower
 * levels always sit to the left, and prerequisite edges then point rightwards.
 */
async function layoutSlice(slice: GraphSlice) {
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
      ...(slice.nodes.length > LARGE_GRAPH_NODES ? { 'elk.layered.thoroughness': '2' } : {}),
    },
    children: slice.nodes.map((node) => ({
      height: NODE_HEIGHT,
      id: node.kid,
      layoutOptions: { 'elk.partitioning.partition': String(node.hskLevel) },
      width: NODE_WIDTH,
    })),
    edges: slice.edges.map((edge) => ({
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
    height: (result as ElkNode).height ?? 0,
    positions: new Map(
      (result.children ?? []).map((child) => [child.id, { x: child.x ?? 0, y: child.y ?? 0 }]),
    ),
    width: (result as ElkNode).width ?? 0,
  }
}

/**
 * Lays the graph out in one pass, or with `groupByDomain` as one block per domain placed side by
 * side. One pass over the whole tree stacks every node of a level in a single tall column (about
 * 3000 × 8200 for the real tree); blocks give a wide shape (about 8500 × 4000) that fits a screen.
 * Links between domains get no route in that case and are drawn by the caller's fallback.
 */
export async function layoutKnowledgeGraph(
  model: GraphSlice,
  options: { groupByDomain?: boolean } = {},
): Promise<GraphLayout> {
  if (model.nodes.length === 0) return { edgePoints: new Map(), groups: [], positions: new Map() }

  const domains = KNOWLEDGE_DOMAINS.filter((domain) =>
    model.nodes.some((node) => node.domain === domain),
  )
  if (!options.groupByDomain || domains.length < 2) {
    const { edgePoints, positions } = await layoutSlice(model)
    return { edgePoints, groups: [], positions }
  }

  const slices = domains.map((domain) => {
    const nodes = model.nodes.filter((node) => node.domain === domain)
    const kids = new Set(nodes.map((node) => node.kid))
    const edges = model.edges.filter((edge) => kids.has(edge.source) && kids.has(edge.target))
    return { domain, edges, nodes }
  })
  const results = await Promise.all(slices.map(layoutSlice))

  const edgePoints = new Map<string, NodePosition[]>()
  const positions = new Map<string, NodePosition>()
  const groups: DomainGroup[] = []
  let left = 0
  slices.forEach(({ domain }, index) => {
    const result = results[index]!
    const shift = ({ x, y }: NodePosition) => ({
      x: x + left + GROUP_PADDING,
      y: y + GROUP_HEADER,
    })
    result.positions.forEach((position, kid) => positions.set(kid, shift(position)))
    result.edgePoints.forEach((points, id) => edgePoints.set(id, points.map(shift)))
    const width = result.width + GROUP_PADDING * 2
    groups.push({
      domain,
      height: result.height + GROUP_HEADER + GROUP_PADDING,
      width,
      x: left,
      y: 0,
    })
    left += width + DOMAIN_GAP
  })

  return { edgePoints, groups, positions }
}
