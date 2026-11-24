import { FilePenLine, PenLine, Send } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { cn } from '@/shared/lib/cn'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  HskLevelPicker,
  PageBanner,
  Skeleton,
  Textarea,
  useHskLevel,
} from '@/shared/ui'

import { getLearningTasks, type LearningTask } from '../api/learning-tasks'
import { createGradingRequest, getGradingPolicy } from '../api/requests'
import { countHanzi, validSubmission } from '../essayRules'

/**
 * Viết: writing tasks of the competency tree for the chosen HSK level. The teacher grades a task with
 * R-VIẾT, and the grade feeds the task's grammar, vocabulary and character nodes.
 */
export default function WritingPracticePage() {
  const navigate = useNavigate()
  const { user } = useCurrentUser()
  const [level, setLevel] = useHskLevel(user?.hskLevel)
  const tasks = useApiQuery((signal) => getLearningTasks('WRITING', level, signal), [level], {
    isEmpty: (rows) => rows.length === 0,
  })
  const policy = useApiQuery((signal) => getGradingPolicy(signal), [])
  const mutation = useApiMutation(createGradingRequest)
  const [selected, setSelected] = useState<LearningTask>()
  const [essay, setEssay] = useState('')
  const busy = useRef(false)

  useEffect(() => {
    setSelected(undefined)
    setEssay('')
  }, [level])

  const hanzi = countHanzi(essay)
  const minimum = Math.max(selected?.minChars ?? 0, policy.data?.minHanzi ?? 0)
  const ready = Boolean(selected) && hanzi >= minimum && validSubmission(essay, '', policy.data)

  function send() {
    if (!selected || !ready || busy.current) return
    busy.current = true
    void mutation
      .run({ essay, prompt: null, hskLevel: selected.hskLevel, taskId: selected.id })
      .then((row) => navigate(`/grading/${row.id}`))
      .catch(() => undefined)
      .finally(() => {
        busy.current = false
      })
  }

  return (
    <div className="mx-auto w-full min-w-0 max-w-content space-y-6 pb-16">
      <PageBanner
        subtitle="Chọn một nhiệm vụ viết của cấp, viết bài rồi gửi giáo viên chấm theo R-VIẾT."
        title="Viết"
      />
      <div className="flex flex-wrap items-center gap-3">
        <HskLevelPicker level={level} onChange={setLevel} />
        <Button size="sm" to="/grading/new" variant="outline">
          <PenLine aria-hidden="true" className="size-4" />
          Viết tự do
        </Button>
      </div>

      <div className="grid min-w-0 items-start gap-6 lg:grid-cols-12">
        <section aria-label="Nhiệm vụ viết" className="min-w-0 space-y-3 lg:col-span-5">
          {tasks.state === 'loading' ? <Skeleton className="h-64" /> : null}
          {tasks.state === 'error' ? (
            <ErrorState
              message={tasks.error?.message ?? 'Vui lòng thử lại.'}
              onRetry={tasks.reload}
              title="Không tải được nhiệm vụ"
            />
          ) : null}
          {tasks.state === 'empty' ? (
            <EmptyState
              action={
                <Button size="sm" to="/grading/new" variant="outline">
                  Viết tự do
                </Button>
              }
              description="Cấp này chưa có nhiệm vụ viết. Bạn vẫn có thể viết tự do và gửi giáo viên chấm."
              icon={<FilePenLine aria-hidden="true" className="size-5" />}
              title="Chưa có nhiệm vụ viết"
            />
          ) : null}
          {tasks.state === 'ready'
            ? (tasks.data ?? []).map((task) => (
                <button
                  aria-pressed={selected?.id === task.id}
                  className={cn(
                    'w-full min-w-0 rounded-md border p-4 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
                    selected?.id === task.id
                      ? 'border-brand-600 bg-brand-50'
                      : 'border-line bg-surface hover:border-brand-600',
                  )}
                  key={task.id}
                  onClick={() => {
                    setSelected(task)
                    mutation.reset()
                  }}
                  type="button"
                >
                  <p className="text-label text-ink">{task.titleVi}</p>
                  <p className="mt-1 text-body-sm text-ink-secondary">{task.promptVi}</p>
                  {task.minChars ? (
                    <p className="mt-2 text-caption text-ink-secondary">
                      Tối thiểu {task.minChars} chữ Hán
                    </p>
                  ) : null}
                </button>
              ))
            : null}
        </section>

        <div className="min-w-0 lg:col-span-7">
          {selected ? (
            <Card className="min-w-0 space-y-4 p-5 sm:p-6">
              <div className="space-y-1">
                <h2 className="text-h3 text-ink">{selected.titleVi}</h2>
                <p className="text-body text-ink-secondary">{selected.promptVi}</p>
                {selected.promptZh ? (
                  <p className="font-hanzi text-body text-ink" lang="zh">
                    {selected.promptZh}
                  </p>
                ) : null}
              </div>
              <FormField
                hint={`${hanzi} chữ Hán${minimum ? ` · cần ít nhất ${minimum}` : ''}`}
                htmlFor="writing-essay"
                label="Bài viết"
                required
              >
                <Textarea
                  className="font-hanzi"
                  lang="zh"
                  onChange={(event) => {
                    setEssay(event.target.value)
                    mutation.reset()
                  }}
                  rows={10}
                  value={essay}
                />
              </FormField>
              {mutation.error ? (
                <p
                  className="rounded-md border border-danger bg-danger-bg p-3 text-body-sm text-danger"
                  role="alert"
                >
                  {mutation.error.message}
                </p>
              ) : null}
              <div className="flex flex-wrap items-center gap-3">
                <Button disabled={!ready} loading={mutation.pending} onClick={send}>
                  <Send aria-hidden="true" className="size-4" />
                  Gửi giáo viên chấm
                </Button>
                <p className="text-body-sm text-ink-secondary">
                  Mỗi bài dùng 1 lượt chấm bài của tháng.
                </p>
              </div>
            </Card>
          ) : (
            <Card className="min-w-0 space-y-2 border-brand-100 bg-brand-50 p-5">
              <h2 className="text-h3 text-ink">Cách luyện viết</h2>
              <ol className="list-decimal space-y-2 pl-5 text-body-sm text-ink-secondary">
                <li>Chọn một nhiệm vụ ở cột bên.</li>
                <li>Viết bằng chữ Hán, đủ độ dài và đúng trình tự ý.</li>
                <li>
                  Giáo viên chấm 4 tiêu chí R-VIẾT và gắn mã lỗi; kết quả cập nhật nút kiến thức của
                  bạn.
                </li>
              </ol>
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
