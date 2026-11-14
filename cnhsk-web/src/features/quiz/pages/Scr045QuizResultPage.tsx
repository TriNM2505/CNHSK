import { useEffect, useRef } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { appRouteDefinitions } from '@/app/routeAccess'
import { useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { Button, ErrorState, Skeleton } from '@/shared/ui'
import { getQuizResult, startQuizAttempt } from '../api/quizzes'
import { QuizResultView } from '../components/QuizResultView'
import { QuizTopTen } from '../components/QuizTopTen'

function Result({ sessionId }: { sessionId: number }) {
  const { user } = useCurrentUser()
  const query = useApiQuery((signal) => getQuizResult(sessionId, signal), [sessionId, user?.id])
  const retry = useApiMutation(startQuizAttempt)
  const navigate = useNavigate()
  const location = useLocation()
  const lock = useRef(false)
  const live = useRef(true)
  useEffect(() => {
    live.current = true
    return () => {
      live.current = false
    }
  }, [])
  const authError = query.error ?? retry.error
  useEffect(() => {
    if (authError?.status === 401)
      navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`, {
        replace: true,
      })
    else if (authError?.status === 403) navigate('/403', { replace: true })
  }, [authError, location.pathname, location.search, navigate])
  async function startRetry() {
    if (lock.current || !query.data) return
    lock.current = true
    try {
      const session = await retry.run(query.data.topicId)
      if (live.current)
        navigate(`/community/quizzes/${session.topicId}/play?sessionId=${session.sessionId}`, {
          state: { quizSession: session },
        })
    } catch {
      // Keep the result visible; the mutation exposes a retryable action error.
    } finally {
      lock.current = false
    }
  }
  if (query.state === 'loading')
    return (
      <main className="p-4" role="status" aria-label="Đang tải kết quả quiz">
        <Skeleton variant="card" />
      </main>
    )
  if (query.state === 'error' || !query.data)
    return (
      <main className="space-y-4 p-4">
        <ErrorState
          title="Không tải được kết quả quiz"
          message={
            query.error?.errorCode === 'ATTEMPT_NOT_SUBMITTED'
              ? 'Lượt quiz này chưa được nộp.'
              : query.error?.errorCode === 'QUIZ_ATTEMPT_NOT_FOUND'
                ? 'Không tìm thấy kết quả quiz này.'
                : query.error?.message
          }
          onRetry={query.reload}
        />
        <Button to="/community/quizzes" variant="outline">
          Quiz khác
        </Button>
      </main>
    )
  const leaderboardAvailable = appRouteDefinitions.some(
    (route: { path: string }) => route.path === '/leaderboards/topics',
  )
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 break-words p-4 [overflow-wrap:anywhere] sm:p-6">
      <QuizResultView
        result={query.data}
        pending={retry.pending}
        actionError={
          retry.error?.errorCode === 'RATE_LIMITED'
            ? 'Bạn đã đạt giới hạn 30 lượt bắt đầu quiz trong một giờ. Vui lòng thử lại sau.'
            : retry.error?.errorCode === 'QUIZ_NOT_AVAILABLE'
              ? 'Chủ đề này chưa có đủ câu hỏi để làm lại.'
              : retry.error?.message
        }
        leaderboardAvailable={leaderboardAvailable}
        onRetry={() => {
          void startRetry()
        }}
      />
      <QuizTopTen key={query.data.topicId} topicId={query.data.topicId} />
    </main>
  )
}

export default function Scr045QuizResultPage() {
  const sessionId = Number(useParams().sessionId)
  const { user } = useCurrentUser()
  if (!Number.isSafeInteger(sessionId) || sessionId <= 0)
    return <ErrorState message="Mã lượt quiz không hợp lệ." />
  return <Result key={`${user?.id}:${sessionId}`} sessionId={sessionId} />
}
