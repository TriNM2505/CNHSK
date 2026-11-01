import type { FinishResult, SessionView } from './types'

/** Rebuild the generic result available from a submitted session after a race/reload. */
export function restoredFinishResult(session: SessionView | null): FinishResult | undefined {
  if (session?.status !== 'SUBMITTED') return undefined

  const score = session.answered.filter((answer) => answer.correct === true).length
  const maxScore = session.answered.filter((answer) => answer.counted === true).length

  return {
    items: session.answered.map((answer) => ({
      correct: answer.correct,
      correctAnswer: answer.correctAnswer,
      no: answer.no,
      yourAnswer: answer.answer,
    })),
    maxScore,
    percent: maxScore === 0 ? null : Math.floor((score * 100) / maxScore),
    score,
    sessionId: session.sessionId,
    status: 'SUBMITTED',
    suspicious: false,
  }
}
