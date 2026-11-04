import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApiMutation, useApiQuery } from '@/shared/api'
import { SUPPORTED_HSK_LEVELS, type HskLevel } from '@/shared/ui/LevelBadge'
import { getExams, startExamAttempt } from '../api/exams'
import { getPlacementSummary, startPlacementRun } from '../api/placement'
import { ExamListView } from '../components/ExamListView'

export default function Scr020ExamListPage() {
  const navigate = useNavigate()
  const [level, setLevel] = useState<HskLevel>(SUPPORTED_HSK_LEVELS[0] ?? 1)
  const query = useApiQuery(
    async (signal) => {
      const [exams, placement] = await Promise.all([
        getExams(level, signal),
        getPlacementSummary(signal),
      ])
      return { exams, placement }
    },
    [level],
  )
  const action = useApiMutation(async (examId: number | null) => {
    if (examId !== null) return (await startExamAttempt(examId)).sessionId
    const step = await startPlacementRun()
    if (step.session) return step.session.sessionId
    navigate(`/learn/placement/${step.runId}`)
    return null
  })
  const start = async (examId: number | null) => {
    try {
      const sessionId = await action.run(examId)
      if (sessionId !== null) navigate(`/learn/exams/sessions/${sessionId}`)
    } catch {
      // The mutation exposes the request error to the view for retry.
    }
  }
  return (
    <ExamListView
      exams={query.data?.exams ?? []}
      placement={query.data?.placement}
      level={level}
      state={query.state}
      error={query.error?.message}
      actionError={action.error?.message}
      pending={action.pending}
      onLevelChange={setLevel}
      onRetry={query.reload}
      onStartExam={(id) => {
        void start(id)
      }}
      onStartPlacement={() => {
        void start(null)
      }}
    />
  )
}
