import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'

import { useApiMutation, useApiQuery, type ApiError } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { SUPPORTED_HSK_LEVELS } from '@/shared/ui'

import { listQuizzes, startQuizAttempt } from '../api/quizzes'
import { QuizListView } from '../components/QuizListView'

export default function Scr043QuizListPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const location = useLocation()
  const { user } = useCurrentUser()
  const level = Number(params.get('hsk'))
  const hsk = SUPPORTED_HSK_LEVELS.find((value) => value === level)
  const rawPage = params.get('page') ?? '0'
  const page = /^\d+$/.test(rawPage) && Number.isSafeInteger(Number(rawPage)) ? Number(rawPage) : 0
  const query = useApiQuery((signal) => listQuizzes({ hsk, page }, signal), [hsk, page, user?.id], {
    isEmpty: (data) => data.items.length === 0,
  })
  const mutation = useApiMutation(startQuizAttempt)
  const [pendingTopic, setPendingTopic] = useState<number>()
  const inFlight = useRef(false)
  const mounted = useRef(true)
  const identity = useRef(user?.id)
  identity.current = user?.id

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  function authFailure(error: ApiError | undefined) {
    if (error?.status === 401) {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`, {
        replace: true,
      })
    } else if (error?.status === 403) navigate('/403', { replace: true })
  }

  useEffect(() => {
    if (query.error?.status === 401) {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`, {
        replace: true,
      })
    } else if (query.error?.status === 403) navigate('/403', { replace: true })
  }, [query.error, navigate, location.pathname, location.search])

  function changeFilter(value: string) {
    mutation.reset()
    setParams(value ? { hsk: value } : {})
  }

  async function start(topicId: number) {
    if (inFlight.current) return
    inFlight.current = true
    setPendingTopic(topicId)
    const startedBy = identity.current
    try {
      const session = await mutation.run(topicId)
      if (mounted.current && identity.current === startedBy) {
        navigate(`/community/quizzes/${session.topicId}/play?sessionId=${session.sessionId}`, {
          state: { quizSession: session },
        })
      }
    } catch (error) {
      if (mounted.current && identity.current === startedBy) authFailure(error as ApiError)
    } finally {
      inFlight.current = false
      if (mounted.current) setPendingTopic(undefined)
    }
  }

  return (
    <QuizListView
      state={query.state}
      result={query.data}
      hsk={hsk === undefined ? '' : String(hsk)}
      error={query.error}
      actionError={mutation.error}
      pendingTopic={pendingTopic}
      onHskChange={changeFilter}
      onPageChange={(next) => {
        const updated = new URLSearchParams(params)
        updated.set('page', String(next))
        mutation.reset()
        setParams(updated)
      }}
      onRetry={() => {
        mutation.reset()
        query.reload()
      }}
      onStart={(id) => {
        void start(id)
      }}
    />
  )
}
