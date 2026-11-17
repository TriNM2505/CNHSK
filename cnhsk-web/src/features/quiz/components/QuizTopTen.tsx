import { useApiQuery } from '@/shared/api'
import { Trophy } from 'lucide-react'
import { Button, EmptyState, ErrorState, Skeleton } from '@/shared/ui'
import { getQuizTopTen } from '../api/leaderboards'
import { QuizRankingList } from './QuizResultParts'
import './QuizResultView.css'

export function QuizTopTen({ topicId }: { topicId: number }) {
  const query = useApiQuery((signal) => getQuizTopTen(topicId, signal), [topicId], {
    isEmpty: (data) => data.entries.length === 0,
  })
  return (
    <section aria-label="Top 10 chủ đề" className="quiz-top-ten min-w-0 space-y-4 pb-24">
      <h2 className="flex items-center gap-2 text-h2">
        <Trophy aria-hidden="true" className="size-5 text-brand-600" />
        Top 10 chủ đề
      </h2>
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
      {query.state === 'ready' ? <QuizRankingList entries={query.data?.entries ?? []} /> : null}
      {query.state === 'ready' ? (
        <Button variant="outline" onClick={query.reload}>
          Tải lại bảng xếp hạng
        </Button>
      ) : null}
    </section>
  )
}
