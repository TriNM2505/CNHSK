import type { KnowledgeDomain, KnowledgeNode } from '@/features/question-bank/types'

/** Six domains of the Hán ngữ competency tree (spec v2.0). Skills are node attributes, not domains. */
export const KNOWLEDGE_DOMAINS: readonly KnowledgeDomain[] = ['NA', 'CH', 'TV', 'NP', 'NL', 'VB']

export const DOMAIN_LABELS: Record<KnowledgeDomain, string> = {
  NA: 'Ngữ âm',
  CH: 'Chữ Hán',
  TV: 'Từ vựng',
  NP: 'Ngữ pháp',
  NL: 'Năng lực giao tiếp',
  VB: 'Văn bản',
}

export type GraphEdgeKind = 'prereq' | 'support'

/** Edge from the required node (`source`) to the node that builds on it (`target`). */
export type GraphEdge = {
  id: string
  kind: GraphEdgeKind
  source: string
  target: string
}

export type GraphFilter = {
  domain?: KnowledgeDomain
  hskLevel?: number
}

export type KnowledgeGraphModel = {
  edges: GraphEdge[]
  nodes: KnowledgeNode[]
  /** Nodes above level 1 with no prerequisite link in either direction, computed on the whole tree. */
  orphanKids: ReadonlySet<string>
  stats: { nodes: number; prereqEdges: number; supportEdges: number }
}

function collectEdges(nodes: readonly KnowledgeNode[]): GraphEdge[] {
  const known = new Set(nodes.map((node) => node.kid))
  const seen = new Set<string>()
  const edges: GraphEdge[] = []

  const add = (kind: GraphEdgeKind, source: string, target: string) => {
    // Links to unknown or same nodes come from partial imports; skip them instead of failing the view.
    if (source === target || !known.has(source)) return
    const id = `${kind}:${source}->${target}`
    if (seen.has(id)) return
    seen.add(id)
    edges.push({ id, kind, source, target })
  }

  for (const node of nodes) {
    for (const kid of node.prereqKids) add('prereq', kid, node.kid)
    for (const kid of node.supportKids) add('support', kid, node.kid)
  }
  return edges
}

function findOrphans(nodes: readonly KnowledgeNode[], edges: readonly GraphEdge[]): Set<string> {
  const linked = new Set<string>()
  for (const edge of edges) {
    if (edge.kind !== 'prereq') continue
    linked.add(edge.source)
    linked.add(edge.target)
  }
  return new Set(
    nodes.filter((node) => node.hskLevel > 1 && !linked.has(node.kid)).map((node) => node.kid),
  )
}

export function buildGraphModel(
  allNodes: readonly KnowledgeNode[],
  filter: GraphFilter = {},
): KnowledgeGraphModel {
  const allEdges = collectEdges(allNodes)
  const orphanKids = findOrphans(allNodes, allEdges)

  const nodes = allNodes.filter(
    (node) =>
      (filter.domain === undefined || node.domain === filter.domain) &&
      (filter.hskLevel === undefined || node.hskLevel === filter.hskLevel),
  )
  const visible = new Set(nodes.map((node) => node.kid))
  const edges = allEdges.filter((edge) => visible.has(edge.source) && visible.has(edge.target))

  return {
    edges,
    nodes,
    orphanKids,
    stats: {
      nodes: nodes.length,
      prereqEdges: edges.filter((edge) => edge.kind === 'prereq').length,
      supportEdges: edges.filter((edge) => edge.kind === 'support').length,
    },
  }
}
