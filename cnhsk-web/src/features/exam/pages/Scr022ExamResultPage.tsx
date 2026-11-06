import { useState } from 'react'
import { useApiQuery } from '@/shared/api'
import { Button } from '@/shared/ui/Button'
import { getExamResult, getExamWeakPoints } from '../api/exams'
import { ExamResultView, type ResultFilter } from '../components/ExamResultView'
import type { ExamResult } from '../types'

export default function Scr022ExamResultPage({ sessionId }: { sessionId: number }) {
  const [filter, setFilter] = useState<ResultFilter>('ALL')
  const query = useApiQuery((signal) => getExamResult(sessionId, signal), [sessionId])
  if (query.state === 'loading') return <p role="status">Đang tải kết quả đề thi…</p>
  if (query.state === 'error')
    return (
      <section role="alert">
        <p>{query.error?.message}</p>
        <Button onClick={query.reload}>Thử lại</Button>
      </section>
    )
  if (!query.data) return <p role="alert">Không tìm thấy kết quả đề thi.</p>
  return (
    <ExamResultWithWeakPoints
      key={sessionId}
      result={query.data}
      sessionId={sessionId}
      filter={filter}
      onFilter={setFilter}
    />
  )
}

function ExamResultWithWeakPoints({
  result,
  sessionId,
  filter,
  onFilter,
}: {
  result: ExamResult
  sessionId: number
  filter: ResultFilter
  onFilter: (value: ResultFilter) => void
}) {
  const query = useApiQuery((signal) => getExamWeakPoints(sessionId, signal), [sessionId])
  const weakPoints = query.state === 'loading'
    ? { state: 'loading' as const }
    : query.state === 'error'
      ? { state: 'error' as const, onRetry: query.reload }
      : { state: 'ready' as const, points: query.data ?? [] }
  return <ExamResultView result={result} filter={filter} onFilter={onFilter} weakPoints={weakPoints} />
}
