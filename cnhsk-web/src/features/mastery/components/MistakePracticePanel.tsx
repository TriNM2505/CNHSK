import type { ApiQueryState } from '@/shared/api'
import type { MistakeSummary } from '@/features/exam/types'
import { Button, Card, ErrorState, Skeleton } from '@/shared/ui'

export interface MistakePracticePanelProps {
  summary: MistakeSummary | undefined
  state: ApiQueryState
  onRetry: () => void
}

/** Entry to the learner's own mistakes from the last seven days. */
export function MistakePracticePanel({ summary, state, onRetry }: MistakePracticePanelProps) {
  const kids = summary?.kids ?? []
  const names = kids
    .slice(0, 3)
    .map((node) => node.nameVi)
    .join(', ')

  return (
    <Card
      aria-labelledby="mistake-practice-title"
      className="flex min-w-0 flex-wrap items-end justify-between gap-x-6 gap-y-4 p-5 sm:px-6"
      role="region"
    >
      <div className="min-w-0 space-y-1">
        <h2 className="text-label text-ink-secondary" id="mistake-practice-title">
          Luyện lỗi của tôi
        </h2>
        {state === 'loading' ? <Skeleton variant="text" /> : null}
        {state === 'error' ? (
          <ErrorState message="Không tải được các lỗi gần đây." onRetry={onRetry} />
        ) : null}
        {state === 'ready' && kids.length === 0 ? (
          <p className="text-body-sm text-ink-secondary">Bạn chưa có lỗi nào trong 7 ngày qua</p>
        ) : null}
        {state === 'ready' && kids.length > 0 ? (
          <>
            <p className="text-h2 tabular-nums text-ink">{kids.length} phần cần luyện lại</p>
            <p className="break-words text-body-sm text-ink-secondary">
              {names}
              {kids.length > 3 ? '…' : ''}
            </p>
          </>
        ) : null}
      </div>
      {state === 'ready' && kids.length > 0 ? (
        <Button to="/learn/practice/targeted?mode=mistakes">Luyện ngay</Button>
      ) : null}
    </Card>
  )
}
