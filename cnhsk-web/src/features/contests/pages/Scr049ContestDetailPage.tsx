import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { getContest, getContestResults } from '../api/contests'
import { ContestDetailView } from '../components/ContestDetailView'

function Detail({ id }: { id: number }) {
  const { user, status } = useCurrentUser()
  const navigate = useNavigate()
  const [now, setNow] = useState(Date.now())
  const [confirming, setConfirming] = useState(false)
  const refreshedBoundary = useRef('')
  const detail = useApiQuery((signal) => getContest(id, signal), [id])
  const learner = Boolean(user?.roles.includes('LEARNER'))
  const mine = useApiQuery(
    (signal) => (learner ? getContestResults(id, signal) : Promise.resolve(null)),
    [id, user?.id, learner],
  )
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])
  const contest = detail.data
  useEffect(() => {
    if (!contest || contest.state === 'ENDED') return
    const boundary = contest.state === 'UPCOMING' ? contest.startsAt : contest.endsAt
    if (now >= Date.parse(boundary) && refreshedBoundary.current !== boundary) {
      refreshedBoundary.current = boundary
      detail.reload()
    }
  }, [contest, now, detail])
  const eligibility =
    status === 'loading'
      ? 'loading'
      : !user
        ? 'guest'
        : !learner
          ? 'other'
          : !user.emailVerified
            ? 'unverified'
            : 'learner'
  return (
    <ContestDetailView
      state={detail.state}
      contest={contest}
      mine={mine.data?.mine}
      eligibility={eligibility}
      now={now}
      error={detail.error?.message}
      confirming={confirming}
      onConfirmChange={setConfirming}
      onEnter={() => navigate('/contests/' + id + '/attempt')}
      onRetry={detail.reload}
    />
  )
}

export default function Scr049ContestDetailPage() {
  const id = Number(useParams().id)
  return Number.isSafeInteger(id) && id > 0 ? (
    <Detail key={id} id={id} />
  ) : (
    <p role="alert">Cuộc thi không khả dụng.</p>
  )
}
