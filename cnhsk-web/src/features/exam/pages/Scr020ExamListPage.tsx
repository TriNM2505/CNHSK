import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApiMutation, useApiQuery } from '@/shared/api'
import { SUPPORTED_HSK_LEVELS, type HskLevel } from '@/shared/ui/LevelBadge'
import { getExams, startExamAttempt } from '../api/exams'
import { ExamListView } from '../components/ExamListView'

/** Mock exams only; the entrance (placement) test has its own page at /learn/placement. */
export default function Scr020ExamListPage() {
  const navigate = useNavigate()
  const [level, setLevel] = useState<HskLevel>(SUPPORTED_HSK_LEVELS[0] ?? 1)
  const query = useApiQuery((signal) => getExams(level, signal), [level])
  const action = useApiMutation(
    async (examId: number) => (await startExamAttempt(examId)).sessionId,
  )
  const start = async (examId: number) => {
    try {
      navigate(`/learn/exams/sessions/${await action.run(examId)}`)
    } catch {
      // The mutation exposes the request error to the view for retry.
    }
  }
  return (
    <ExamListView
      exams={query.data ?? []}
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
    />
  )
}
