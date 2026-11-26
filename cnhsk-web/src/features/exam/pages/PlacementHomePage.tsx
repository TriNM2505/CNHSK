import { useNavigate } from 'react-router-dom'

import { useApiMutation, useApiQuery } from '@/shared/api'
import { Button, PageHeader, Skeleton } from '@/shared/ui'

import { getPlacementSummary, startPlacementRun } from '../api/placement'
import { PlacementCard } from '../components/PlacementCard'

/**
 * The entrance test on its own page, apart from the mock exams: one mixed sheet that tells the learner which
 * HSK level (1–3) to start from.
 */
export default function PlacementHomePage() {
  const navigate = useNavigate()
  const summary = useApiQuery((signal) => getPlacementSummary(signal), [])
  const start = useApiMutation(async () => {
    const step = await startPlacementRun()
    if (step.session) navigate(`/learn/exams/sessions/${step.session.sessionId}`)
    else navigate(`/learn/placement/${step.runId}`)
  })
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6">
      <div className="rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
        <PageHeader
          className="mb-0"
          title={
            <span className="inline-flex items-center gap-3">
              <span aria-hidden="true" className="h-7 w-1.5 rounded-pill bg-brand-600" />
              <span>Kiểm tra đầu vào</span>
            </span>
          }
          subtitle="Biết mình đang ở HSK 1, 2 hay 3 để học đúng cấp. Khác với đề thi thử: đây là một bài trộn ba cấp, chấm ngay."
        />
      </div>
      {start.error ? (
        <p role="alert" className="text-danger">
          {start.error.message}
        </p>
      ) : null}
      {summary.state === 'loading' ? (
        <div role="status" aria-label="Đang tải">
          <Skeleton variant="card" />
        </div>
      ) : summary.state === 'error' ? (
        <section role="alert" className="space-y-3">
          <p>{summary.error?.message ?? 'Không thể tải bài kiểm tra trình độ.'}</p>
          <Button onClick={summary.reload}>Thử lại</Button>
        </section>
      ) : (
        <div className="max-w-xl">
          <PlacementCard
            placement={summary.data}
            pending={start.pending}
            onStart={() => void start.run(undefined).catch(() => undefined)}
          />
        </div>
      )}
    </main>
  )
}
