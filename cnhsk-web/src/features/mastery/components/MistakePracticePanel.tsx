import type { ApiQueryState } from '@/shared/api'
import { MISTAKE_SECTIONS } from '@/features/exam/mistakeSections'
import type { MistakeSummary } from '@/features/exam/types'
import { Button, ErrorState, Skeleton } from '@/shared/ui'

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
    <section
      aria-labelledby="mistake-practice-title"
      className="learn-rise flex min-w-0 flex-col gap-4 rounded-lg border border-line bg-surface p-5 sm:p-6"
      style={{ animationDelay: '40ms' }}
    >
      <div className="flex min-w-0 flex-wrap items-start justify-between gap-x-6 gap-y-3">
        <div className="min-w-0 space-y-1">
          <h2
            className="text-label uppercase tracking-wider text-brand-700"
            id="mistake-practice-title"
          >
            Luyện lỗi của tôi
          </h2>
          {state === 'loading' ? <Skeleton variant="text" /> : null}
          {state === 'error' ? (
            <ErrorState message="Không tải được các lỗi gần đây." onRetry={onRetry} />
          ) : null}
          {state === 'ready' && total === 0 ? (
            <p className="text-body text-ink-secondary">Bạn chưa có lỗi nào trong 7 ngày qua</p>
          ) : null}
          {state === 'ready' && total > 0 ? (
            <p className="text-h2 tabular-nums text-ink">{total} phần cần luyện lại</p>
          ) : null}
        </div>
        {state === 'ready' && sections.length > 1 ? (
          <Button size="sm" to={PRACTICE} variant="secondary">
            Luyện tất cả
          </Button>
        ) : null}
      </div>
      {state === 'ready' && sections.length > 0 ? (
        <ul className="min-w-0 space-y-2">
          {sections.map((section) => {
            const names = section.nodes
              .slice(0, 3)
              .map((node) => node.nameVi)
              .join(', ')
            return (
              <li
                className="flex min-w-0 items-center justify-between gap-3 rounded-md bg-surface-subtle py-2.5 pl-3 pr-2"
                key={section.key}
              >
                <div className="flex min-w-0 items-center gap-3">
                  <span className="flex size-8 shrink-0 items-center justify-center rounded-sm bg-brand-100 text-label tabular-nums text-brand-800">
                    {section.nodes.length}
                  </span>
                  <div className="min-w-0">
                    <p className="text-body-sm font-semibold text-ink">
                      {section.label}
                      <span className="sr-only"> {section.nodes.length} phần</span>
                    </p>
                    <p className="truncate text-caption text-ink-secondary">
                      {names}
                      {section.nodes.length > 3 ? '…' : ''}
                    </p>
                  </div>
                </div>
                <Button
                  aria-label={'Luyện lỗi ' + section.label}
                  size="sm"
                  to={PRACTICE + '&section=' + section.key}
                  variant="outline"
                >
                  Luyện
                </Button>
              </li>
            )
          })}
        </ul>
      ) : null}
    </section>
  )
}
