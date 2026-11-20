import '@xyflow/react/dist/style.css'

import {
  Background,
  BaseEdge,
  Controls,
  Handle,
  MarkerType,
  MiniMap,
  Position,
  ReactFlow,
  getSmoothStepPath,
  useStore,
  type Edge,
  type EdgeProps,
  type Node,
  type NodeHandle,
  type NodeMouseHandler,
  type NodeProps,
  type ReactFlowState,
} from '@xyflow/react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { elkEdgePath } from '../elkEdgePath'
import {
  DOMAIN_LABELS,
  KNOWLEDGE_DOMAINS,
  buildChainModel,
  buildGraphModel,
  linkedKids,
  type KnowledgeGraphModel,
} from '../graphModel'
import {
  NODE_HEIGHT,
  NODE_WIDTH,
  layoutKnowledgeGraph,
  type GraphLayout,
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
/** Below this zoom a node shows only its KID in large type, which stays legible from afar. */
const COMPACT_ZOOM = 0.6
const isCompactZoom = (state: ReactFlowState) => state.transform[2] < COMPACT_ZOOM

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
  active: boolean
  dimmed: boolean
  focus: boolean
  node: KnowledgeNode
  noQuestions: boolean
  orphan: boolean
  selected: boolean
}

type KidFlowNode = Node<KidNodeData, 'kid'>

function KidNodeView({ data }: NodeProps<KidFlowNode>) {
  const { active, dimmed, focus, node, noQuestions, orphan, selected } = data
  // Each node re-renders only when the zoom crosses the threshold, not on every zoom step.
  const compact = useStore(isCompactZoom)
  const warnings = [orphan ? 'Mồ côi' : null, noQuestions ? 'Chưa có câu' : null].filter(Boolean)
  const label = [`${node.nameVi} (${node.kid})`, `HSK ${node.hskLevel}`, ...warnings].join(', ')

  return (
    <>
      <Handle className="!opacity-0" isConnectable={false} position={Position.Left} type="target" />
      <button
        aria-label={label}
        aria-current={selected ? 'true' : undefined}
        aria-pressed={active}
        className={cn(
          'flex flex-col justify-center gap-0.5 rounded-sm border border-l-4 border-line bg-surface px-3 text-left transition-opacity focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
          // Warning colours only the top/right/bottom edges so the left edge keeps the domain colour.
          warnings.length > 0 && 'border-b-warning border-r-warning border-t-warning bg-warning-bg',
          DOMAIN_BORDER[node.domain],
          compact && 'items-center',
          selected && 'ring-2 ring-brand-600',
          focus && 'bg-brand-50',
          active && 'ring-4 ring-brand-600',
          dimmed && 'opacity-30',
        )}
        // Clicks (and Enter/Space on the focused button) bubble to the node wrapper's onNodeClick.
        style={{ height: NODE_HEIGHT, width: NODE_WIDTH }}
        type="button"
      >
        {compact ? (
          <span className="text-[28px] font-semibold leading-none text-ink">{node.kid}</span>
        ) : (
          <>
            <span className="text-caption text-ink-secondary">
              {node.kid} · HSK {node.hskLevel}
              {warnings.length > 0 ? (
                <span className="text-warning"> · {warnings.join(' · ')}</span>
              ) : null}
            </span>
            <span className="line-clamp-2 text-body-sm text-ink">{node.nameVi}</span>
          </>
        )}
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

type ElkFlowEdge = Edge<{ points?: NodePosition[] }, 'elk'>

/** Draws the route ELK computed, so edges go around nodes instead of through them. */
function ElkEdgeView({
  data,
  markerEnd,
  sourcePosition,
  sourceX,
  sourceY,
  style,
  targetPosition,
  targetX,
  targetY,
}: EdgeProps<ElkFlowEdge>) {
  const path =
    elkEdgePath(data?.points) ??
    getSmoothStepPath({ sourcePosition, sourceX, sourceY, targetPosition, targetX, targetY })[0]
  return <BaseEdge markerEnd={markerEnd} path={path} style={style} />
}

const nodeTypes = { kid: KidNodeView }
const NODE_HANDLES: NodeHandle[] = [
  { height: 1, position: Position.Left, type: 'target', width: 1, x: 0, y: NODE_HEIGHT / 2 },
  {
    height: 1,
    position: Position.Right,
    type: 'source',
    width: 1,
    x: NODE_WIDTH,
    y: NODE_HEIGHT / 2,
  },
]
const edgeTypes = { elk: ElkEdgeView }

/** Simple column-per-level fallback used when ELK cannot lay the graph out. */
function gridLayout(model: Pick<KnowledgeGraphModel, 'nodes'>): GraphLayout {
  const rowByLevel = new Map<number, number>()
  return {
    edgePoints: new Map(),
    positions: new Map(
      model.nodes.map((node) => {
        const row = rowByLevel.get(node.hskLevel) ?? 0
        rowByLevel.set(node.hskLevel, row + 1)
        return [
          node.kid,
          { x: (node.hskLevel - 1) * (NODE_WIDTH + 72), y: row * (NODE_HEIGHT + 24) },
        ]
      }),
    ),
  }
}

const selectClass =
  'min-h-touch rounded-sm border border-line bg-surface px-3 text-body-sm text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus disabled:opacity-60'
const chainButtonClass =
  'min-h-touch rounded-sm border border-brand-600 bg-surface px-3 text-body-sm font-semibold text-brand-600 hover:bg-brand-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus'

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
  /** KID whose prerequisite chain replaces the filtered view; the filters keep their values meanwhile. */
  const [chainKid, setChainKid] = useState<string | null>(null)
  const [hoveredKid, setHoveredKid] = useState<string | null>(null)
  /** Node picked on the graph; its actions (questions, chain) show above the canvas. */
  const [activeKid, setActiveKid] = useState<string | undefined>(selectedKid)

  const model = useMemo(
    () =>
      chainKid
        ? buildChainModel(nodes, chainKid)
        : buildGraphModel(nodes, {
            domain: domain === 'ALL' ? undefined : domain,
            hskLevel: level === 'ALL' ? undefined : level,
          }),
    [chainKid, domain, level, nodes],
  )

  // A chain whose node disappeared (the tree reloaded without it) falls back to the full view.
  useEffect(() => {
    if (chainKid && model.nodes.length === 0) {
      chainToggled.current = true
      setChainKid(null)
    }
  }, [chainKid, model.nodes.length])

  // Entering or leaving the chain swaps the action buttons; keep keyboard focus on a live control.
  const domainSelectRef = useRef<HTMLSelectElement>(null)
  const backButtonRef = useRef<HTMLButtonElement>(null)
  const chainButtonRef = useRef<HTMLButtonElement>(null)
  const chainToggled = useRef(false)
  useEffect(() => {
    if (!chainToggled.current) return
    chainToggled.current = false
    const target = chainKid ? backButtonRef.current : chainButtonRef.current
    ;(target ?? domainSelectRef.current)?.focus()
  }, [chainKid])
  const toggleChain = (kid: string | null) => {
    chainToggled.current = true
    setChainKid(kid)
  }

  // The layout is stored with the model it was built for, so a new model never renders with a
  // stale layout for one frame before the effect below resets it.
  const [built, setBuilt] = useState<{ layout: GraphLayout; model: KnowledgeGraphModel } | null>(
    null,
  )
  const layout = built?.model === model ? built.layout : null
  useEffect(() => {
    let cancelled = false
    layoutKnowledgeGraph(model)
      .then((result) => {
        if (!cancelled) setBuilt({ layout: result, model })
      })
      .catch(() => {
        if (!cancelled) setBuilt({ layout: gridLayout(model), model })
      })
    return () => {
      cancelled = true
    }
  }, [model])

  // A hover left over from a node that unmounted (reload, chain switch) never dims the new view.
  const hovered =
    hoveredKid && model.nodes.some((node) => node.kid === hoveredKid) ? hoveredKid : null
  useEffect(() => setHoveredKid(null), [model])
  const highlighted = useMemo(
    () => (hovered ? linkedKids(model.edges, hovered) : null),
    [hovered, model.edges],
  )

  const flowNodes = useMemo<KidFlowNode[]>(
    () =>
      model.nodes.map((node) => ({
        connectable: false,
        data: {
          active: node.kid === activeKid,
          dimmed: highlighted !== null && !highlighted.has(node.kid),
          focus: node.kid === chainKid,
          node,
          noQuestions: questionCounts?.get(node.kid) === 0,
          orphan: model.orphanKids.has(node.kid),
          selected: node.kid === selectedKid,
        },
        draggable: false,
        // Fixed size and handles: React Flow never has to measure a node again when its data
        // changes (hover, selection), so edges keep their anchors without a resize pass.
        handles: NODE_HANDLES,
        height: NODE_HEIGHT,
        id: node.kid,
        measured: { height: NODE_HEIGHT, width: NODE_WIDTH },
        position: layout?.positions.get(node.kid) ?? { x: 0, y: 0 },
        type: 'kid',
        width: NODE_WIDTH,
      })),
    [activeKid, chainKid, highlighted, layout, model, questionCounts, selectedKid],
  )

  const flowEdges = useMemo<ElkFlowEdge[]>(
    () =>
      model.edges.map((edge) => {
        const stroke = edge.kind === 'prereq' ? PREREQ_STROKE : SUPPORT_STROKE
        const faded = hovered !== null && edge.source !== hovered && edge.target !== hovered
        return {
          data: { points: layout?.edgePoints.get(edge.id) },
          id: edge.id,
          markerEnd: { color: stroke, type: MarkerType.ArrowClosed },
          source: edge.source,
          style: {
            opacity: faded ? 0.15 : 1,
            stroke,
            strokeDasharray: edge.kind === 'support' ? '6 4' : undefined,
          },
          target: edge.target,
          type: 'elk',
        }
      }),
    [hovered, layout, model],
  )

  const handleNodeClick = useCallback<NodeMouseHandler>((_, node) => setActiveKid(node.id), [])
  const handleNodeEnter = useCallback<NodeMouseHandler>((_, node) => setHoveredKid(node.id), [])
  const handleNodeLeave = useCallback<NodeMouseHandler>(() => setHoveredKid(null), [])

  const active = activeKid ? nodes.find((node) => node.kid === activeKid) : undefined

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
            disabled={chainKid !== null}
            onChange={(event) => setDomain(event.target.value as KnowledgeDomain | 'ALL')}
            ref={domainSelectRef}
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
            disabled={chainKid !== null}
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
        {chainKid ? (
          <button
            className={chainButtonClass}
            onClick={() => toggleChain(null)}
            ref={backButtonRef}
            type="button"
          >
            Quay lại toàn sơ đồ
          </button>
        ) : null}
      </div>

      {active ? (
        <div
          aria-label="Nút đang chọn"
          className="flex flex-wrap items-center gap-3 rounded-sm border border-line bg-surface p-3"
          role="group"
        >
          <p className="min-w-0 text-body-sm text-ink">
            <span className="font-semibold">{active.kid}</span> · {active.nameVi}
          </p>
          {onSelectKid ? (
            <button
              className={chainButtonClass}
              onClick={() => onSelectKid(active.kid)}
              type="button"
            >
              Xem câu hỏi của {active.kid}
            </button>
          ) : null}
          {active.kid !== chainKid ? (
            <button
              className={chainButtonClass}
              onClick={() => toggleChain(active.kid)}
              ref={chainButtonRef}
              type="button"
            >
              Xem chuỗi của {active.kid}
            </button>
          ) : null}
        </div>
      ) : null}

      {chainKid ? (
        <p className="text-body-sm font-semibold text-ink">
          Đang xem chuỗi {chainKid} · {model.stats.nodes} nút (mọi nút phải học trước và các nút học
          sau trong 2 bậc)
        </p>
      ) : null}

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
      ) : layout === null ? (
        <Skeleton aria-label="Đang dựng sơ đồ" className="h-[560px]" variant="card" />
      ) : (
        <div className="h-[560px] min-w-0 overflow-hidden rounded-md border border-line bg-canvas">
          <ReactFlow
            edgeTypes={edgeTypes}
            edges={flowEdges}
            edgesFocusable={false}
            elementsSelectable={false}
            fitView
            key={`${chainKid ?? ''}-${domain}-${level}`}
            maxZoom={2.5}
            minZoom={0.1}
            nodeTypes={nodeTypes}
            nodes={flowNodes}
            nodesConnectable={false}
            nodesDraggable={false}
            // The inner button is the only tab stop; the wrapper's own focus would add an unnamed stop.
            nodesFocusable={false}
            // Without a click handler React Flow sets pointer-events: none on non-selectable nodes.
            onNodeClick={handleNodeClick}
            onNodeMouseEnter={handleNodeEnter}
            onNodeMouseLeave={handleNodeLeave}
            // No viewport culling: it drops edges whose ELK route crosses the screen while both ends
            // are off it. Lightweight nodes keep the full ~190-node tree smooth without it.
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
