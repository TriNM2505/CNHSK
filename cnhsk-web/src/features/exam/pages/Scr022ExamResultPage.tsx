import { useState } from 'react'
import { useApiQuery } from '@/shared/api'
import { Button } from '@/shared/ui/Button'
import { getExamResult } from '../api/exams'
import { ExamResultView, type ResultFilter } from '../components/ExamResultView'

const SHOW_EXAM_WEAK_POINTS = false

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
    <ExamResultView
      result={query.data}
      filter={filter}
      onFilter={setFilter}
      showWeakPoints={SHOW_EXAM_WEAK_POINTS}
    />
  )
}
