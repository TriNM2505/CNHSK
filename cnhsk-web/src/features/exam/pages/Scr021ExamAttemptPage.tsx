import { useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { usePracticeSession } from '@/features/practice'
import type { AnswerRequest, SessionView } from '@/features/practice/types'
import { useApiMutation } from '@/shared/api'
import { Button } from '@/shared/ui/Button'

import { nextPlacementBlock } from '../api/placement'
import { ExamAttemptView, type AttemptSection } from '../components/ExamAttemptView'

type Draft = { pending: Record<number, AnswerRequest>; marks: number[] }
type Practice = ReturnType<typeof usePracticeSession>

function object(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
}

function readDraft(key: string): Draft {
  try {
    const raw = sessionStorage.getItem(key)
    if (raw) {
      const parsed = object(JSON.parse(raw))
      const pending = Object.fromEntries(
        Object.values(object(parsed.pending)).flatMap((value) => {
          const entry = object(value)
          return typeof entry.no === 'number' &&
            typeof entry.answer === 'string' &&
            typeof entry.durationMs === 'number'
            ? [[entry.no, { no: entry.no, answer: entry.answer, durationMs: entry.durationMs }]]
            : []
        }),
      )
      return {
        pending,
        marks: Array.isArray(parsed.marks)
          ? parsed.marks.filter((no) => typeof no === 'number')
          : [],
      }
    }
  } catch {
    // Storage may be unavailable or contain an invalid draft.
  }
  return { pending: {}, marks: [] }
}

function Attempt({ session, practice }: { session: SessionView; practice: Practice }) {
  const navigate = useNavigate()
  const placement = session.source === 'PLACEMENT'
  const storageKey = 'cnhsk:mock-exam:' + session.sessionId
  const [draft, setDraft] = useState<Draft>(() =>
    placement ? { pending: {}, marks: [] } : readDraft(storageKey),
  )
  const draftRef = useRef(draft)
  const [currentNo, setCurrentNo] = useState(session.items[0]?.no ?? 1)
  const [confirming, setConfirming] = useState(false)
  const enteredAt = useRef(performance.now())
  const [saveStates, setSaveStates] = useState<Record<number, 'saving' | 'saved' | 'failed'>>({})
  const exam = object(session.meta?.exam)
  const block = object(session.meta?.placement)
  const runId = typeof block.runId === 'number' ? block.runId : (session.refId ?? session.sessionId)
  const advance = useApiMutation(() => nextPlacementBlock(runId))
  const busy = practice.pending !== null || advance.pending
  const readOnly = session.status === 'SUBMITTED'
  const questions = session.items.map(({ no, prompt, options }) => ({
    no,
    text: prompt.text ?? '',
    options: options ?? [],
  }))
  const sectionOf = object(exam.sectionOf)
  const sections: AttemptSection[] =
    Array.isArray(exam.sections) && !placement
      ? exam.sections.flatMap((value: unknown) => {
          const { index, name } = object(value)
          return typeof index === 'number' && typeof name === 'string'
            ? [{ index, name, questions: questions.filter(({ no }) => sectionOf[no] === index) }]
            : []
        })
      : []
  const assigned = new Set(sections.flatMap((section) => section.questions.map(({ no }) => no)))
  const unassigned = questions.filter(({ no }) => !assigned.has(no))
  if (unassigned.length) sections.push({ index: 0, name: 'Câu hỏi', questions: unassigned })
  const choices = Object.fromEntries(
    questions.map(({ no }) => [
      no,
      draft.pending[no]
        ? (draft.pending[no].answer ?? null)
        : (practice.drafts[no]?.answer ?? null),
    ]),
  )

  function updateDraft(next: Draft) {
    draftRef.current = next
    setDraft(next)
    if (!placement) {
      try {
        sessionStorage.setItem(storageKey, JSON.stringify(next))
      } catch {
        /* Keep the in-memory draft. */
      }
    }
  }
  function selectQuestion(no: number) {
    enteredAt.current = performance.now()
    setCurrentNo(no)
  }
  async function choose(key: string) {
    if (busy || readOnly) return
    const no = currentNo
    const request = {
      no,
      answer: key,
      durationMs: Math.round(performance.now() - enteredAt.current),
    }
    updateDraft({ ...draftRef.current, pending: { ...draftRef.current.pending, [no]: request } })
    if (placement) return
    setSaveStates((current) => ({ ...current, [no]: 'saving' }))
    const saved = await practice.answer(no, request)
    if (draftRef.current.pending[no] !== request) return
    setSaveStates((current) => ({ ...current, [no]: saved ? 'saved' : 'failed' }))
    if (saved) {
      const pending = { ...draftRef.current.pending }
      delete pending[no]
      updateDraft({ ...draftRef.current, pending })
    }
  }
  function skip() {
    if (busy || readOnly) return
    updateDraft({
      ...draftRef.current,
      pending: {
        ...draftRef.current.pending,
        [currentNo]: {
          no: currentNo,
          answer: null,
          skipped: true,
          durationMs: Math.round(performance.now() - enteredAt.current),
        },
      },
    })
    const next = questions[questions.findIndex(({ no }) => no === currentNo) + 1]
    if (next) selectQuestion(next.no)
  }
  async function continueAfterFinish() {
    if (!placement) {
      navigate('/learn/exams/results/' + session.sessionId, { replace: true })
      return
    }
    try {
      const step = await advance.run(undefined)
      if (step.session)
        navigate('/learn/exams/sessions/' + step.session.sessionId, { replace: true })
      else if (step.concluded) navigate('/learn/placement/' + step.runId, { replace: true })
    } catch {
      // The submitted block stays read-only and can retry next.
    }
  }
  async function submit() {
    if (busy || readOnly) return
    const allowed = new Map(questions.map(({ no, options }) => [no, options]))
    const answers = Object.values(draftRef.current.pending).filter(
      ({ no, answer, skipped }) =>
        allowed.has(no) && (skipped || allowed.get(no)?.some(({ key }) => key === answer)),
    )
    const result = await practice.finish(answers)
    if (result?.status !== 'SUBMITTED') return
    if (!placement) {
      try {
        sessionStorage.removeItem(storageKey)
      } catch {
        /* Storage may be unavailable. */
      }
    }
    await continueAfterFinish()
  }
  return (
    <ExamAttemptView
      title={
        placement
          ? 'Khối ' + (typeof block.block === 'number' ? block.block : 1)
          : typeof exam.title === 'string'
            ? exam.title
            : 'Đề thi thử'
      }
      sections={sections}
      currentNo={currentNo}
      choices={choices}
      marks={draft.marks}
      placement={placement}
      pending={busy}
      readOnly={readOnly}
      confirming={confirming}
      saveState={
        saveStates[currentNo] ??
        (draft.pending[currentNo]
          ? 'failed'
          : practice.drafts[currentNo]?.answer
            ? 'saved'
            : undefined)
      }
      error={advance.error?.message ?? practice.error?.message}
      onNavigate={selectQuestion}
      onChoose={(key) => {
        void choose(key)
      }}
      onSkip={skip}
      onMark={() =>
        updateDraft({
          ...draftRef.current,
          marks: draftRef.current.marks.includes(currentNo)
            ? draftRef.current.marks.filter((no) => no !== currentNo)
            : [...draftRef.current.marks, currentNo],
        })
      }
      onConfirmChange={setConfirming}
      onSubmit={() => {
        void submit()
      }}
      onContinue={() => {
        void continueAfterFinish()
      }}
    />
  )
}

function LoadedAttempt({ sessionId }: { sessionId: number }) {
  const practice = usePracticeSession({ sessionId })
  if (practice.state === 'loading') return <p role="status">Đang tải lượt làm bài…</p>
  if (practice.state === 'error')
    return (
      <section role="alert">
        <p>{practice.error?.message}</p>
        <Button onClick={practice.reload}>Thử lại</Button>
      </section>
    )
  const session = practice.session
  if (
    !session ||
    practice.state === 'abandoned' ||
    (session.source !== 'MOCK_EXAM' && session.source !== 'PLACEMENT')
  ) {
    return <p role="alert">Lượt làm bài không khả dụng.</p>
  }
  return <Attempt session={session} practice={practice} />
}

export default function Scr021ExamAttemptPage() {
  const { sessionId } = useParams()
  const id = Number(sessionId)
  return Number.isSafeInteger(id) && id > 0 ? (
    <LoadedAttempt key={id} sessionId={id} />
  ) : (
    <p role="alert">Lượt làm bài không khả dụng.</p>
  )
}
