import type { ApiQueryState } from '@/shared/api'
import { MISTAKE_SECTIONS } from '@/features/exam/mistakeSections'
import type { MistakeSummary } from '@/features/exam/types'
import { Button, Card, ErrorState, Skeleton } from '@/shared/ui'

export interface MistakePracticePanelProps {
  summary: MistakeSummary | undefined
  state: ApiQueryState
  onRetry: () => void
}

const PRACTICE = '/learn/practice/targeted?mode=mistakes'

/** The learner's mistakes of the last seven days, filed under the menu section where each was made. */
export function MistakePracticePanel({ summary, state, onRetry }: MistakePracticePanelProps) {
  const kids = summary?.kids ?? []
  const total = new Set(kids.map((node) => node.kid)).size
  const sections = MISTAKE_SECTIONS.map((section) => ({
    ...section,
    nodes: kids.filter((node) => node.section === section.key),
  })).filter((section) => section.nodes.length > 0)

  return (
    <Card aria-labelledby="mistake-practice-title" className="min-w-0 space-y-4 p-5 sm:px-6" role="region">
      <div className="flex min-w-0 flex-wrap items-end justify-between gap-x-6 gap-y-3">
        <div className="min-w-0 space-y-1">
          <h2 className="text-label text-ink-secondary" id="mistake-practice-title">
            Luyện lỗi của tôi
          </h2>
          {state === 'loading' ? <Skeleton variant="text" /> : null}
          {state === 'error' ? (
            <ErrorState message="Không tải được các lỗi gần đây." onRetry={onRetry} />
          ) : null}
          {state === 'ready' && total === 0 ? (
            <p className="text-body-sm text-ink-secondary">Bạn chưa có lỗi nào trong 7 ngày qua</p>
          ) : null}
          {state === 'ready' && total > 0 ? (
            <p className="text-h2 tabular-nums text-ink">{total} phần cần luyện lại</p>
          ) : null}
        </div>
        {state === 'ready' && sections.length > 1 ? (
          <Button to={PRACTICE} variant="secondary">
            Luyện tất cả
          </Button>
        ) : null}
      </div>
      {state === 'ready' && sections.length > 0 ? (
        <ul className="divide-y divide-brand-100 border-t border-brand-100">
          {sections.map((section) => {
            const names = section.nodes.slice(0, 3).map((node) => node.nameVi).join(', ')
            return (
              <li className="flex min-w-0 flex-wrap items-center justify-between gap-x-4 gap-y-2 py-3" key={section.key}>
                <div className="min-w-0 space-y-0.5">
                  <p className="text-body font-semibold text-ink">
                    {section.label}
                    <span className="ml-2 text-body-sm font-normal tabular-nums text-ink-secondary">
                      {section.nodes.length} phần
                    </span>
                  </p>
                  <p className="break-words text-body-sm text-ink-secondary">
                    {names}
                    {section.nodes.length > 3 ? '…' : ''}
                  </p>
                </div>
                <Button
                  aria-label={'Luyện lỗi ' + section.label}
                  size="sm"
                  to={PRACTICE + '&section=' + section.key}
                >
                  Luyện
                </Button>
              </li>
            )
          })}
        </ul>
      ) : null}
    </Card>
  )
}
