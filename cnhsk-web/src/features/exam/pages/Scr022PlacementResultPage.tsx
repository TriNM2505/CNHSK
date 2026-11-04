import { useNavigate } from 'react-router-dom'
import { useApiMutation, useApiQuery } from '@/shared/api'
import { Button } from '@/shared/ui/Button'
import { getPlacementResult, startPlacementRun } from '../api/placement'
import { PlacementResultView } from '../components/PlacementResultView'

export default function Scr022PlacementResultPage({ runId }: { runId: number }) {
  const navigate = useNavigate()
  const query = useApiQuery((signal) => getPlacementResult(runId, signal), [runId])
  const retake = useApiMutation(async () => {
    const step = await startPlacementRun()
    if (step.session) navigate('/learn/exams/sessions/' + step.session.sessionId)
  })
  if (query.state === 'loading') return <p role="status">Đang tải kết quả xếp trình độ…</p>
  if (query.state === 'error')
    return (
      <section role="alert">
        <p>{query.error?.message}</p>
        <Button onClick={query.reload}>Thử lại</Button>
      </section>
    )
  if (!query.data) return <p role="alert">Không tìm thấy kết quả xếp trình độ.</p>
  return (
    <PlacementResultView
      result={query.data}
      pending={retake.pending}
      error={retake.error?.message}
      onRetake={() => {
        void retake.run(undefined).catch(() => undefined)
      }}
    />
  )
}
