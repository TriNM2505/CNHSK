import { useEffect, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useApiQuery } from '@/shared/api'
import { getContest } from '../api/contests'
import { ContestAttemptView } from '../components/ContestAttemptView'
import { useContestAttempt } from '../useContestAttempt'

function Attempt({ id }: { id: number }) {
  const navigate = useNavigate()
  const navigated = useRef(false)
  const detail = useApiQuery((signal) => getContest(id, signal), [id])
  const attempt = useContestAttempt(id)
  useEffect(() => {
    if (attempt.submitted && !navigated.current) {
      navigated.current = true
      navigate('/contests/' + id + '/results', { replace: true })
    }
  }, [attempt.submitted, id, navigate])
  return (
    <ContestAttemptView
      name={detail.data?.name ?? 'Làm bài cuộc thi'}
      state={attempt.load === 'ready' && !attempt.session?.items.length ? 'empty' : attempt.load}
      session={attempt.session}
      remainingMs={attempt.remainingMs}
      choices={attempt.choices}
      saveStates={attempt.saveStates}
      locked={attempt.locked}
      submitting={attempt.submitting}
      submitted={attempt.submitted}
      error={attempt.loadError?.message}
      submitError={attempt.submitError?.message}
      onRetry={attempt.reload}
      onSelectQuestion={attempt.selectQuestion}
      onChoose={attempt.choose}
      onSubmit={() => void attempt.submit()}
      onRetrySubmit={() => void attempt.retrySubmit()}
    />
  )
}

export default function Scr050ContestAttemptPage() {
  const id = Number(useParams().id)
  return Number.isSafeInteger(id) && id > 0 ? (
    <Attempt key={id} id={id} />
  ) : (
    <p role="alert">Cuộc thi không khả dụng.</p>
  )
}
