import '@xyflow/react/dist/style.css'

import {
  Background,
  Controls,
  Handle,
  MarkerType,
  MiniMap,
  Position,
  ReactFlow,
  type Edge,
  type Node,
  type NodeProps,
} from '@xyflow/react'
import { useEffect, useMemo, useState } from 'react'

import {
  DOMAIN_LABELS,
  KNOWLEDGE_DOMAINS,
  buildGraphModel,
  type KnowledgeGraphModel,
} from '../graphModel'
import {
  NODE_HEIGHT,
  NODE_WIDTH,
  layoutKnowledgeGraph,
  type NodePosition,
} from '../layoutKnowledgeGraph'
import type { KnowledgeDomain, KnowledgeNode } from '@/features/question-bank/types'
import { cn } from '@/shared/lib/cn'
import { EmptyState, Skeleton } from '@/shared/ui'

export type KnowledgeGraphProps = {
  nodes: readonly KnowledgeNode[]
  onSelectKid?: (kid: string) => void
  /** Approved question count per KID; nodes missing from the map are treated as unknown, not zero. */
  questionCounts?: ReadonlyMap<string, number>
  selectedKid?: string
}

/** Above this size the graph opens on a single domain so it stays readable. */
const DEFAULT_FILTER_THRESHOLD = 60
/** Small graphs fit on screen; the minimap would only cover nodes. */
const MINIMAP_THRESHOLD = 30

// Raw values of the brand-600 and ink-muted tokens: SVG markers cannot read Tailwind classes.
const PREREQ_STROKE = '#AF0000'
const SUPPORT_STROKE = '#858890'

const DOMAIN_BORDER: Record<KnowledgeDomain, string> = {
  NA: 'border-l-info',
  CH: 'border-l-brand-600',
  TV: 'border-l-success',
  // Not warning-coloured: that colour is reserved for the "needs review" flag.
  NP: 'border-l-ink',
  NL: 'border-l-accent-500',
  VB: 'border-l-ink-muted',
}

type KidNodeData = {
  node: KnowledgeNode
  noQuestions: boolean
  orphan: boolean
  selected: boolean
}

type KidFlowNode = Node<KidNodeData, 'kid'>

function KidNodeView({ data }: NodeProps<KidFlowNode>) {
  const { node, noQuestions, orphan, selected } = data
  const warnings = [orphan ? 'Mồ côi' : null, noQuestions ? 'Chưa có câu' : null].filter(Boolean)
  const label = [`${node.nameVi} (${node.kid})`, `HSK ${node.hskLevel}`, ...warnings].join(', ')

  return (
    <>
      <Handle className="!opacity-0" isConnectable={false} position={Position.Left} type="target" />
      <button
        aria-label={label}
        aria-current={selected ? 'true' : undefined}
        className={cn(
          'flex flex-col justify-center gap-0.5 rounded-sm border border-l-4 border-line bg-surface px-3 text-left shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
          // Warning colours only the top/right/bottom edges so the left edge keeps the domain colour.
          warnings.length > 0 && 'border-b-warning border-r-warning border-t-warning bg-warning-bg',
          DOMAIN_BORDER[node.domain],
          selected && 'ring-2 ring-brand-600',
        )}
        // Clicks (and Enter/Space on the focused button) bubble to the node wrapper's onNodeClick.
        style={{ height: NODE_HEIGHT, width: NODE_WIDTH }}
        type="button"
      >
        <span className="text-caption text-ink-secondary">
          {node.kid} · HSK {node.hskLevel}
          {warnings.length > 0 ? (
            <span className="text-warning"> · {warnings.join(' · ')}</span>
          ) : null}
        </span>
        <span className="line-clamp-2 text-body-sm text-ink">{node.nameVi}</span>
      </button>
      <Handle
        className="!opacity-0"
        isConnectable={false}
        position={Position.Right}
        type="source"
      />
    </>
  )
}

const nodeTypes = { kid: KidNodeView }

/** Simple column-per-level fallback used when ELK cannot lay the graph out. */
function gridPositions(model: Pick<KnowledgeGraphModel, 'nodes'>): Map<string, NodePosition> {
  const rowByLevel = new Map<number, number>()
  return new Map(
    model.nodes.map((node) => {
      const row = rowByLevel.get(node.hskLevel) ?? 0
      rowByLevel.set(node.hskLevel, row + 1)
      return [node.kid, { x: (node.hskLevel - 1) * (NODE_WIDTH + 72), y: row * (NODE_HEIGHT + 24) }]
    }),
  )
}

const selectClass =
  'min-h-touch rounded-sm border border-line bg-surface px-3 text-body-sm text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'

export default function KnowledgeGraph({
  nodes,
  onSelectKid,
  questionCounts,
  selectedKid,
}: KnowledgeGraphProps) {
  const domains = useMemo(
    () => KNOWLEDGE_DOMAINS.filter((domain) => nodes.some((node) => node.domain === domain)),
    [nodes],
  )
  const levels = useMemo(
    () => [...new Set(nodes.map((node) => node.hskLevel))].sort((a, b) => a - b),
    [nodes],
  )
  const [domain, setDomain] = useState<KnowledgeDomain | 'ALL'>(() =>
    nodes.length > DEFAULT_FILTER_THRESHOLD && domains[0] ? domains[0] : 'ALL',
  )
  const [level, setLevel] = useState<number | 'ALL'>('ALL')

  const model = useMemo(
    () =>
      buildGraphModel(nodes, {
        domain: domain === 'ALL' ? undefined : domain,
        hskLevel: level === 'ALL' ? undefined : level,
      }),
    [domain, level, nodes],
  )

  const [positions, setPositions] = useState<Map<string, NodePosition> | null>(null)
  useEffect(() => {
    let cancelled = false
    setPositions(null)
    layoutKnowledgeGraph(model)
      .then((result) => {
        if (!cancelled) setPositions(result)
      })
      .catch(() => {
        if (!cancelled) setPositions(gridPositions(model))
      })
    return () => {
      cancelled = true
    }
  }, [model])

  const flowNodes = useMemo<KidFlowNode[]>(
    () =>
      model.nodes.map((node) => ({
        connectable: false,
        data: {
          node,
          noQuestions: questionCounts?.get(node.kid) === 0,
          orphan: model.orphanKids.has(node.kid),
          selected: node.kid === selectedKid,
        },
        draggable: false,
        height: NODE_HEIGHT,
        id: node.kid,
        position: positions?.get(node.kid) ?? { x: 0, y: 0 },
        type: 'kid',
        width: NODE_WIDTH,
      })),
    [model, positions, questionCounts, selectedKid],
  )

  const flowEdges = useMemo<Edge[]>(
    () =>
      model.edges.map((edge) => {
        const stroke = edge.kind === 'prereq' ? PREREQ_STROKE : SUPPORT_STROKE
        return {
          id: edge.id,
          markerEnd: { color: stroke, type: MarkerType.ArrowClosed },
          source: edge.source,
          style: { stroke, strokeDasharray: edge.kind === 'support' ? '6 4' : undefined },
          target: edge.target,
          type: 'smoothstep',
        }
      }),
    [model],
  )

  if (nodes.length === 0) {
    return (
      <EmptyState
        description="Nhập nút và cạnh tiên quyết ở màn Nhập dữ liệu để xem sơ đồ."
        title="Cây năng lực chưa có nút nào"
      />
    )
  }

  const orphanCount = model.nodes.filter((node) => model.orphanKids.has(node.kid)).length
  const noQuestionCount = questionCounts
    ? model.nodes.filter((node) => questionCounts.get(node.kid) === 0).length
    : undefined

  return (
    <section aria-label="Sơ đồ cây năng lực" className="min-w-0 space-y-3">
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-label text-ink">
          Miền
          <select
            className={selectClass}
            onChange={(event) => setDomain(event.target.value as KnowledgeDomain | 'ALL')}
            value={domain}
          >
            <option value="ALL">Tất cả miền</option>
            {domains.map((value) => (
              <option key={value} value={value}>
                {value} · {DOMAIN_LABELS[value]}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-label text-ink">
          Cấp HSK
          <select
            className={selectClass}
            onChange={(event) =>
              setLevel(event.target.value === 'ALL' ? 'ALL' : Number(event.target.value))
            }
            value={level}
          >
            <option value="ALL">Tất cả cấp</option>
            {levels.map((value) => (
              <option key={value} value={value}>
                HSK {value}
              </option>
            ))}
          </select>
        </label>
      </div>

      <p aria-live="polite" className="text-body-sm text-ink-secondary">
        {model.stats.nodes} nút · {model.stats.prereqEdges} cạnh tiên quyết ·{' '}
        {model.stats.supportEdges} cạnh hỗ trợ · {orphanCount} nút mồ côi
        {noQuestionCount !== undefined ? ` · ${noQuestionCount} nút chưa có câu` : ''}
      </p>

      <ul className="flex flex-wrap gap-x-4 gap-y-1 text-caption text-ink-secondary">
        <li className="flex items-center gap-2">
          <span aria-hidden="true" className="h-0.5 w-6 bg-brand-600" /> Tiên quyết
        </li>
        <li className="flex items-center gap-2">
          <span aria-hidden="true" className="w-6 border-t-2 border-dashed border-ink-muted" /> Hỗ
          trợ
        </li>
        <li className="flex items-center gap-2">
          <span
            aria-hidden="true"
            className="size-3 rounded-sm border border-warning bg-warning-bg"
          />{' '}
          Cần xem lại
        </li>
        {domains.map((value) => (
          <li className="flex items-center gap-2" key={value}>
            <span
              aria-hidden="true"
              className={cn('h-3 w-1.5 rounded-sm border-l-4', DOMAIN_BORDER[value])}
            />
            {value} · {DOMAIN_LABELS[value]}
          </li>
        ))}
      </ul>

      {model.nodes.length === 0 ? (
        <EmptyState
          description="Đổi miền hoặc cấp để xem nút khác."
          title="Không có nút phù hợp bộ lọc"
        />
      ) : positions === null ? (
        <Skeleton aria-label="Đang dựng sơ đồ" className="h-[560px]" variant="card" />
      ) : (
        <div className="h-[560px] min-w-0 overflow-hidden rounded-md border border-line bg-canvas">
          <ReactFlow
            edges={flowEdges}
            edgesFocusable={false}
            elementsSelectable={false}
            fitView
            key={`${domain}-${level}`}
            maxZoom={1.5}
            minZoom={0.1}
            nodeTypes={nodeTypes}
            nodes={flowNodes}
            nodesConnectable={false}
            nodesDraggable={false}
            // The inner button is the only tab stop; the wrapper's own focus would add an unnamed stop.
            nodesFocusable={false}
            // Without a click handler React Flow sets pointer-events: none on non-selectable nodes.
            onNodeClick={(_, node) => onSelectKid?.(node.id)}
          >
            <Background gap={24} />
            <Controls showInteractive={false} />
            {model.nodes.length > MINIMAP_THRESHOLD ? <MiniMap pannable zoomable /> : null}
          </ReactFlow>
        </div>
      )}
    </section>
  )
}
