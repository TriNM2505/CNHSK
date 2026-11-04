import { useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { answerItem, finishSession } from '@/features/practice/api'
import type { SessionView } from '@/features/practice/types'
import { useApiMutation, useApiQuery } from '@/shared/api'

import { getTargetedNodeName, startTargetedPractice } from '../api/targetedPractice'
import { TargetedPracticeView, type TargetedFeedback } from '../components/TargetedPracticeView'
import type { TargetedPracticeRequest } from '../types'

function Practice({ kid, name, session }: { kid: string; name?: string; session: SessionView }) {
  const [index, setIndex] = useState(0)
  const [feedback, setFeedback] = useState<TargetedFeedback>()
  const [finished, setFinished] = useState(false)
  const enteredAt = useRef(performance.now())
  const answer = useApiMutation(({ no, key }: { no: number; key: string }) =>
    answerItem(session.sessionId, {
      no,
      answer: key,
      durationMs: Math.min(3_600_000, Math.round(performance.now() - enteredAt.current)),
    }),
  )
  const finish = useApiMutation(() => finishSession(session.sessionId))
  const item = session.items[index]
  const repeatedNos = session.meta?.repeatedNos

  async function submit(key: string) {
    if (!item || feedback || answer.pending) return
    try {
      const { correct, correctAnswer, explanation } = await answer.run({ no: item.no, key })
      setFeedback({ correct, correctAnswer, explanation })
    } catch {
      // The mutation exposes the error and keeps the current question available for retry.
    }
  }

  async function advance() {
    if (index + 1 < session.items.length) {
      setIndex(index + 1)
      setFeedback(undefined)
      answer.reset()
      enteredAt.current = performance.now()
      return
    }
    try {
      await finish.run(undefined)
      setFinished(true)
    } catch {
      // Keep feedback visible and let the learner retry finishing the session.
    }
  }

  if (finished) return <TargetedPracticeView kid={kid} name={name} state="finished" />
  if (!item) return <TargetedPracticeView kid={kid} name={name} state="empty" />
  return (
    <TargetedPracticeView
      kid={kid}
      name={name}
      state="ready"
      question={{
        text: item.prompt.text ?? '',
        options: item.options?.map(({ key, text }) => ({ key, text })) ?? [],
        repeated: Array.isArray(repeatedNos) && repeatedNos.includes(item.no),
      }}
      position={index + 1}
      total={session.items.length}
      feedback={feedback}
      pending={answer.pending || finish.pending}
      error={answer.error?.message ?? finish.error?.message}
      onAnswer={submit}
      onContinue={advance}
    />
  )
}

function TargetedPractice({ request }: { request: TargetedPracticeRequest }) {
  const { kid } = request
  const inFlight = useRef<ReturnType<typeof startTargetedPractice>>()
  const name = useApiQuery((signal) => getTargetedNodeName(kid, signal), [kid])
  const session = useApiQuery(() => {
    // StrictMode replays effects. Share the in-flight POST for this mounted route.
    inFlight.current ??= startTargetedPractice(request).finally(() => {
      inFlight.current = undefined
    })
    return inFlight.current
  }, [])

  if (session.state === 'loading')
    return <TargetedPracticeView kid={kid} name={name.data} state="loading" />
  if (session.state === 'error') {
    return (
      <TargetedPracticeView
        kid={kid}
        name={name.data}
        state="error"
        error={session.error?.message}
        onRetry={session.reload}
      />
    )
  }
  const data = session.data
  if (!data || 'reason' in data)
    return <TargetedPracticeView kid={kid} name={name.data} state="empty" />
  return <Practice key={data.sessionId} kid={kid} name={name.data} session={data} />
}

export default function Scr023TargetedPracticePage() {
  const [params] = useSearchParams()
  const kid = params.get('kid')?.trim()
  if (!kid) return <TargetedPracticeView kid="" state="error" error="Thiếu mã nút năng lực." />
  const qtype = params.get('qtype')
  const exclude = params.get('exclude')
  const id = exclude && /^\d+$/.test(exclude) ? Number(exclude) : 0
  const request: TargetedPracticeRequest = { kid }
  if (qtype === 'MULTIPLE_CHOICE' || qtype === 'TRUE_FALSE') request.qtype = qtype
  if (Number.isSafeInteger(id) && id > 0) request.excludeQuestionId = id
  return <TargetedPractice key={JSON.stringify(request)} request={request} />
}
