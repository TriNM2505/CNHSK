import type { NodePosition } from './layoutKnowledgeGraph'

/** SVG path through the points ELK routed for an edge; undefined when there is no usable route. */
export function elkEdgePath(points: readonly NodePosition[] | undefined): string | undefined {
  if (!points || points.length < 2) return undefined
  return points.map(({ x, y }, index) => `${index === 0 ? 'M' : 'L'} ${x} ${y}`).join(' ')
}
