import { useMemo } from 'react'

import type { ApiError } from '@/shared/api'
import { KnowledgeGraphLazy } from '@/features/knowledge-graph'
import { ErrorState, PageState, Skeleton, type PageStateValue } from '@/shared/ui'

import type { KnowledgeNode, KnowledgeQuestionCount } from '../types'

export type KnowledgeTreeReviewPanelProps = {
  counts?: readonly KnowledgeQuestionCount[]
  countsError?: ApiError
  nodes: readonly KnowledgeNode[]
  nodesError?: ApiError
  nodesState: PageStateValue
  onNodesRetry: () => void
  onSelectKid: (kid: string) => void
  /** Node currently used as the list filter, highlighted in the graph. */
  selectedKid?: string
}

/**
 * Content editor's tree review: the whole competency tree with orphan nodes and nodes without
 * approved questions flagged. If counts fail to load, the tree still shows without that flag.
 */
export function KnowledgeTreeReviewPanel({
  counts,
  countsError,
  nodes,
  nodesError,
  nodesState,
  onNodesRetry,
  onSelectKid,
  selectedKid,
}: KnowledgeTreeReviewPanelProps) {
  const approvedByKid = useMemo(
    () => (counts ? new Map(counts.map((count) => [count.kid, count.approved])) : undefined),
    [counts],
  )

  return (
    <div className="space-y-3">
      <p className="text-body-sm text-ink-secondary">
        Bấm một nút để chọn, rồi xem câu hỏi gắn nút đó hoặc chuỗi tiên quyết của nó. Nút viền vàng
        cần xem lại: mồ côi (không có tiên quyết) hoặc chưa có câu hỏi đã duyệt.
      </p>
      {countsError ? (
        <p className="text-body-sm text-warning" role="status">
          Chưa tải được số câu theo nút; sơ đồ vẫn hiện nhưng không đánh dấu nút chưa có câu.
        </p>
      ) : null}
      <PageState
        empty={null}
        error={
          <ErrorState
            message={nodesError?.message ?? 'Không tải được cây năng lực.'}
            onRetry={onNodesRetry}
            requestId={nodesError?.requestId}
          />
        }
        loading={
          <Skeleton aria-label="Đang tải cây năng lực" className="h-[560px]" variant="card" />
        }
        state={nodesState === 'empty' ? 'ready' : nodesState}
      >
        <KnowledgeGraphLazy
          nodes={nodes}
          onSelectKid={onSelectKid}
          questionCounts={approvedByKid}
          selectedKid={selectedKid}
        />
      </PageState>
    </div>
  )
}
