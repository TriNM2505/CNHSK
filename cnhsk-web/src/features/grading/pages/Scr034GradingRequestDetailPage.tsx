import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError, useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { cancelGradingRequest, getMyGradingRequest } from '../api/requests'
import { GradingRequestDetailView } from '../components/GradingRequestDetailView'
import type { GradingRequest } from '../types'

function Detail({ id }: { id: string }) {
  const query = useApiQuery((signal) => getMyGradingRequest(id, signal), [id])
  const mutation = useApiMutation(() => cancelGradingRequest(id))
  const [updated, setUpdated] = useState<GradingRequest>()
  const [confirming, setConfirming] = useState(false)
  const alive = useRef(true),
    busy = useRef(false)
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])
  function cancel() {
    if (busy.current || !confirming || (updated ?? query.data)?.status !== 'PENDING') return
    busy.current = true
    void mutation
      .run(undefined)
      .then((row) => {
        if (alive.current) {
          setUpdated(row)
          setConfirming(false)
        }
      })
      .catch((error: unknown) => {
        if (!alive.current) return
        setConfirming(false)
        if (error instanceof ApiError && error.errorCode === 'GRADING_NOT_CANCELLABLE') {
          setUpdated(undefined)
          query.reload()
        }
      })
      .finally(() => {
        busy.current = false
      })
  }
  return (
    <GradingRequestDetailView
      state={query.state}
      request={updated ?? query.data}
      error={query.error}
      cancelError={mutation.error}
      pending={mutation.pending}
      confirming={confirming}
      onRetry={() => {
        setUpdated(undefined)
        query.reload()
      }}
      onReviewCancel={() => {
        mutation.reset()
        setConfirming(true)
      }}
      onCloseCancel={() => setConfirming(false)}
      onCancel={cancel}
    />
  )
}

export default function Scr034GradingRequestDetailPage() {
  const { id = '' } = useParams()
  const auth = useCurrentUser()
  return auth.status === 'authenticated' && auth.user?.roles.includes('LEARNER') ? (
    <Detail key={`${id}-${auth.user.id}`} id={id} />
  ) : (
    <p role="status">Đang kiểm tra quyền truy cập…</p>
  )
}
