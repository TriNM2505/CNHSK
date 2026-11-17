import { useApiQuery } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { Button, Card, EmptyState, ErrorState, Skeleton } from '@/shared/ui'
import { Medal, Trophy } from 'lucide-react'
import { getQuizTopTen } from '../api/leaderboards'
import { quizDuration } from '../types'

export function QuizTopTen({ topicId }: { topicId: number }) {
  const query = useApiQuery((signal) => getQuizTopTen(topicId, signal), [topicId], {
    isEmpty: (data) => data.entries.length === 0,
  })
  return (
    <section aria-label="Top 10 chủ đề" className="min-w-0 space-y-4">
      <div className="flex items-center gap-2">
        <Trophy aria-hidden="true" className="size-5 shrink-0 text-accent-700" />
        <h2 className="text-h2 text-ink">Top 10 chủ đề</h2>
      </div>
      {query.state === 'loading' ? (
        <div aria-label="Đang tải bảng xếp hạng" aria-busy="true">
          <Skeleton variant="card" />
        </div>
      ) : null}
      {query.state === 'error' ? (
        <ErrorState
          title="Bảng xếp hạng chưa khả dụng"
          message="Kết quả quiz của bạn vẫn được giữ. Hãy thử tải lại bảng xếp hạng sau."
          onRetry={query.reload}
        />
      ) : null}
      {query.state === 'empty' ? (
        <EmptyState
          title="Chưa có lượt tính hạng"
          description="Bảng xếp hạng sẽ hiển thị khi có lượt hợp lệ."
        />
      ) : null}
      {query.state === 'ready' ? (
        <ol className="space-y-3">
          {query.data?.entries.slice(0, 10).map((entry) => {
            const isTopThree = entry.rank <= 3
            return (
              <li key={entry.userId}>
                <Card
                  className={cn(
                    'flex min-w-0 flex-wrap items-center justify-between gap-3 break-words p-4 [overflow-wrap:anywhere]',
                    isTopThree && 'border-brand-100 bg-surface-subtle',
                  )}
                >
                  <div className="flex min-w-0 flex-1 items-center gap-2.5">
                    {isTopThree ? (
                      <Medal aria-hidden="true" className="size-4 shrink-0 text-accent-700" />
                    ) : null}
                    <p className="min-w-0 flex-1 font-medium text-ink">
                      #{entry.rank} · {entry.displayName}
                    </p>
                  </div>
                  <p className="text-body-sm font-semibold tabular-nums text-brand-700">
                    {entry.score} điểm
                    {entry.durationMs === null ? '' : ` · ${quizDuration(entry.durationMs)}`}
                  </p>
                </Card>
              </li>
            )
          })}
        </ol>
      ) : null}
      {query.state === 'ready' ? (
        <Button variant="outline" onClick={query.reload}>
          Tải lại bảng xếp hạng
        </Button>
      ) : null}
    </section>
  )
}
