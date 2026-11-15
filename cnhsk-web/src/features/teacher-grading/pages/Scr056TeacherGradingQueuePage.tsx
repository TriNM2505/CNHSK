import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { claimGrading, getGradingQueue, getMyAssignedGrading } from '../api/requests'
import { TeacherGradingQueueView } from '../components/TeacherGradingQueueView'

function Queue() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const query = useApiQuery((signal) => getGradingQueue(page, 20, signal), [page], {
    isEmpty: (data) => data.items.length === 0,
  })
  const mine = useApiQuery(getMyAssignedGrading, [], { isEmpty: (data) => data.length === 0 })
  const mutation = useApiMutation(claimGrading)
  const alive = useRef(true),
    busy = useRef(false)
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])
  function claim(id: string) {
    if (busy.current) return
    busy.current = true
    void mutation
      .run(id)
      .then((row) => {
        if (alive.current) navigate(`/teacher/grading/${row.id}`)
      })
      .catch((error: unknown) => {
        if (
          alive.current &&
          error instanceof ApiError &&
          error.errorCode === 'GRADING_ALREADY_CLAIMED'
        ) {
          query.reload()
          mine.reload()
        }
      })
      .finally(() => {
        busy.current = false
      })
  }
  return (
    <TeacherGradingQueueView
      state={query.state}
      queue={query.data}
      error={query.error}
      mineState={mine.state}
      mine={mine.data}
      mineError={mine.error}
      claimError={mutation.error}
      pending={mutation.pending}
      page={page}
      onPage={setPage}
      onClaim={claim}
      onRetry={query.reload}
      onRetryMine={mine.reload}
    />
  )
}
export default function Scr056TeacherGradingQueuePage() {
  const auth = useCurrentUser()
  return auth.status === 'authenticated' && auth.user?.roles.includes('TEACHER') ? (
    <Queue key={auth.user.id} />
  ) : (
    <p role="status">Đang kiểm tra quyền truy cập…</p>
  )
}
