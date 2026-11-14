import { useEffect, useRef, useState } from 'react'
import { useBlocker, useLocation, useNavigate, useParams } from 'react-router-dom'
import { usePracticeSession } from '@/features/practice'
import type { AnswerRequest } from '@/features/practice/types'
import { useCurrentUser } from '@/shared/auth'
import { Button, EmptyState, ErrorState, Skeleton } from '@/shared/ui'
import { QuizAttemptView } from '../components/QuizAttemptView'
import type { QuizSession } from '../types'
import { useQuizAttempt } from '../useQuizAttempt'

function Attempt({ started }: { started: QuizSession }) {
  const practice = usePracticeSession({ sessionId: started.sessionId })
  const session = practice.session
  const navigate = useNavigate()
  const location = useLocation()
  const [currentNo, setCurrentNo] = useState(started.items[0]?.itemId ?? 1)
  const [edits, setEdits] = useState<Record<number, AnswerRequest>>({})
  const draft = useRef(edits)
  const [saved, setSaved] = useState<Record<number, 'saving' | 'saved' | 'failed'>>({})
  const [confirmation, setConfirmation] = useState<'submit' | 'exit'>()
  const [now, setNow] = useState(Date.now)
  const enteredAt = useRef(performance.now())
  const lock = useRef(false)
  const leaving = useRef(false)
  const live = useRef(true)
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      !leaving.current &&
      session?.status === 'IN_PROGRESS' &&
      session.refId === started.topicId &&
      (session.source === 'QUIZ' || session.source === 'QUIZ_RETRY') &&
      session.feedback === 'DEFERRED' &&
      practice.error?.errorCode !== 'SESSION_EXPIRED' &&
      practice.error?.errorCode !== 'SESSION_ALREADY_SUBMITTED' &&
      (currentLocation.pathname !== nextLocation.pathname ||
        currentLocation.search !== nextLocation.search),
  )
  useEffect(() => {
    live.current = true
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => {
      live.current = false
      window.clearInterval(timer)
    }
  }, [])
  useEffect(() => {
    if (practice.error?.status === 401 || practice.error?.status === 403) {
      leaving.current = true
      navigate(
        practice.error.status === 401
          ? `/login?redirect=${encodeURIComponent(location.pathname + location.search)}`
          : '/403',
        { replace: true },
      )
    }
  }, [practice.error, navigate, location.pathname, location.search])
  useEffect(() => {
    if (
      session?.status === 'SUBMITTED' &&
      session.refId === started.topicId &&
      (session.source === 'QUIZ' || session.source === 'QUIZ_RETRY')
    ) {
      leaving.current = true
      navigate(`/community/quizzes/attempts/${session.sessionId}`, { replace: true })
    }
  }, [session, started.topicId, navigate])

  const busy = practice.pending !== null
  const choices = Object.fromEntries(
    (session?.items ?? []).map(({ no }) => [
      no,
      edits[no]?.answer ?? practice.drafts[no]?.answer ?? null,
    ]),
  )
  function updateEdits(next: Record<number, AnswerRequest>) {
    draft.current = next
    setEdits(next)
  }
  async function choose(key: string) {
    if (lock.current || !session || session.status !== 'IN_PROGRESS') return
    const item = session.items.find(({ no }) => no === currentNo)
    if (!item?.options?.some((option) => option.key === key)) return
    lock.current = true
    const no = currentNo
    const request: AnswerRequest = {
      no,
      answer: key,
      durationMs: Math.min(3600000, Math.max(0, Math.round(performance.now() - enteredAt.current))),
    }
    updateEdits({ ...draft.current, [no]: request })
    setSaved((value) => ({ ...value, [no]: 'saving' }))
    const response = await practice.answer(no, request)
    if (live.current) {
      setSaved((value) => ({ ...value, [no]: response ? 'saved' : 'failed' }))
      if (response && draft.current[no] === request) {
        const next = { ...draft.current }
        delete next[no]
        updateEdits(next)
      }
    }
    lock.current = false
  }
  async function submit() {
    if (lock.current || busy || session?.status !== 'IN_PROGRESS') return
    lock.current = true
    const response = await practice.finish(Object.values(draft.current))
    if (live.current && response?.status === 'SUBMITTED') {
      leaving.current = true
      navigate(`/community/quizzes/attempts/${response.sessionId}`, { replace: true })
    }
    lock.current = false
  }
  async function exit() {
    if (lock.current || busy) return
    lock.current = true
    const abandoned = await practice.abandon()
    if (live.current && abandoned) {
      leaving.current = true
      if (blocker.state === 'blocked') blocker.proceed()
      else navigate('/community/quizzes')
    }
    lock.current = false
  }
  if (practice.state === 'loading')
    return (
      <div className="p-4" role="status" aria-label="Đang tải lượt quiz">
        <Skeleton variant="card" />
      </div>
    )
  if (practice.state === 'error')
    return (
      <div className="space-y-4 p-4">
        <ErrorState
          title="Không tải được lượt quiz"
          message={practice.error?.message}
          onRetry={practice.reload}
        />
        <Button to="/community/quizzes" variant="outline">
          Quiz khác
        </Button>
      </div>
    )
  if (
    !session ||
    session.refId !== started.topicId ||
    (session.source !== 'QUIZ' && session.source !== 'QUIZ_RETRY') ||
    session.feedback !== 'DEFERRED'
  )
    return (
      <div className="p-4">
        <ErrorState message="Lượt quiz không khả dụng cho chủ đề này." />
        <Button to="/community/quizzes">Quiz khác</Button>
      </div>
    )
  if (session.status === 'ABANDONED' || session.status === 'EXPIRED')
    return (
      <div className="p-4">
        <EmptyState
          title={session.status === 'EXPIRED' ? 'Lượt quiz đã hết hạn' : 'Lượt quiz đã bỏ dở'}
          description="Quay lại danh sách để bắt đầu một lượt mới."
          action={<Button to="/community/quizzes">Quiz khác</Button>}
        />
      </div>
    )
  if (session.status === 'SUBMITTED') return <p role="status">Đang mở kết quả quiz…</p>
  const terminal =
    practice.error?.errorCode === 'SESSION_EXPIRED' ||
    practice.error?.errorCode === 'SESSION_ALREADY_SUBMITTED'
  if (terminal)
    return (
      <div className="space-y-4 p-4">
        <ErrorState message={practice.error?.message} />
        <Button to={`/community/quizzes/attempts/${session.sessionId}`}>Xem kết quả</Button>
        <Button
          variant="outline"
          onClick={() => {
            leaving.current = true
            navigate('/community/quizzes')
          }}
        >
          Quiz khác
        </Button>
      </div>
    )
  const confirming = blocker.state === 'blocked' ? 'exit' : confirmation
  return (
    <QuizAttemptView
      topicId={started.topicId}
      rankingEligible={session.source === 'QUIZ'}
      items={session.items}
      currentNo={currentNo}
      choices={choices}
      elapsedMs={Math.max(0, now - Date.parse(session.servedAt))}
      pending={busy}
      saveState={saved[currentNo]}
      error={practice.error?.message}
      confirming={confirming}
      onNavigate={(no) => {
        setCurrentNo(no)
        enteredAt.current = performance.now()
      }}
      onChoose={(key) => {
        void choose(key)
      }}
      onRetryAnswer={() => {
        const key = draft.current[currentNo]?.answer
        if (key) void choose(key)
      }}
      onSubmit={() => {
        if (session.items.some(({ no }) => choices[no] === null)) setConfirmation('submit')
        else void submit()
      }}
      onExit={() => setConfirmation('exit')}
      onCancel={() => {
        setConfirmation(undefined)
        if (blocker.state === 'blocked') blocker.reset()
      }}
      onConfirm={() => {
        if (confirming === 'exit') void exit()
        else void submit()
      }}
    />
  )
}

function ResolveAttempt({ topicId, state }: { topicId: number; state: unknown }) {
  const started = useQuizAttempt(topicId, state)
  const navigate = useNavigate()
  const location = useLocation()
  useEffect(() => {
    if (started.error?.status === 401)
      navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`, {
        replace: true,
      })
    else if (started.error?.status === 403) navigate('/403', { replace: true })
  }, [started.error, navigate, location.pathname, location.search])
  if (started.loading)
    return (
      <div className="p-4" role="status" aria-label="Đang tiếp tục quiz">
        <Skeleton variant="card" />
      </div>
    )
  if (started.error)
    return (
      <div className="space-y-4 p-4">
        <ErrorState
          title="Chưa thể mở quiz"
          message={
            started.error.errorCode === 'RATE_LIMITED'
              ? 'Bạn đã đạt giới hạn 30 lượt bắt đầu quiz trong một giờ. Vui lòng thử lại sau.'
              : started.error.errorCode === 'QUIZ_NOT_AVAILABLE'
                ? 'Chủ đề này chưa có đủ câu hỏi quiz được hỗ trợ.'
                : started.error.message
          }
          onRetry={started.error.status === 429 ? undefined : started.reload}
        />
        <Button to="/community/quizzes">Quiz khác</Button>
      </div>
    )
  return started.session ? (
    <Attempt key={started.session.sessionId} started={started.session} />
  ) : (
    <EmptyState
      title="Không có lượt quiz"
      description="Quay lại danh sách để chọn một chủ đề khác."
    />
  )
}

export default function Scr044QuizAttemptPage() {
  const topicId = Number(useParams().topicId)
  const location = useLocation()
  const { user } = useCurrentUser()
  if (!Number.isSafeInteger(topicId) || topicId <= 0)
    return <ErrorState message="Mã chủ đề không hợp lệ." />
  return <ResolveAttempt key={`${user?.id}:${topicId}`} topicId={topicId} state={location.state} />
}
