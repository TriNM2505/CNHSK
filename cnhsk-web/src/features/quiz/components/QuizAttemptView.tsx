import {
  ArrowLeft,
  ArrowRight,
  Check,
  ChevronDown,
  Clock3,
  Flag,
  ListChecks,
  LogOut,
  Send,
  Trophy,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { ChoiceQuestion } from '@/features/practice/components/ChoiceQuestion'
import { NewQuestionBadge } from '@/features/practice/components/NewQuestionBadge'
import type { ServedItemView } from '@/features/practice/types'
import { Button, Card, EmptyState, PageBanner, Progress } from '@/shared/ui'
import { cn } from '@/shared/lib/cn'
import { quizDuration } from '../types'
import './QuizAttemptView.css'

export type QuizAttemptViewProps = {
  topicId: number
  rankingEligible: boolean
  items: ServedItemView[]
  /** Numbers of newly added bank questions, marked "Câu mới". */
  newItems?: readonly number[]
  currentNo: number
  choices: Readonly<Record<number, string | null>>
  elapsedMs: number
  pending: boolean
  saveState?: 'saving' | 'saved' | 'failed'
  error?: string
  confirming?: 'submit' | 'exit'
  onNavigate: (no: number) => void
  onChoose: (key: string) => void
  onSubmit: () => void
  onExit: () => void
  onConfirm: () => void
  onCancel: () => void
  onRetryAnswer: () => void
}

export function QuizAttemptView(props: QuizAttemptViewProps) {
  const [navigationOpen, setNavigationOpen] = useState(true)
  const panel = useRef<HTMLElement>(null)
  const trigger = useRef<HTMLElement | null>(null)
  useEffect(() => {
    if (!props.confirming) return
    trigger.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    panel.current?.querySelector<HTMLButtonElement>('button')?.focus()
    return () => trigger.current?.focus()
  }, [props.confirming])
  const current = props.items.find((item) => item.no === props.currentNo)
  const index = props.items.findIndex((item) => item.no === props.currentNo)
  const answered = props.items.filter(({ no }) => props.choices[no] != null).length
  const disabled = props.pending || Boolean(props.confirming)
  return (
    <main className="quiz-attempt mx-auto w-full min-w-0 max-w-content space-y-4 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <PageBanner
        title="Làm quiz theo chủ đề"
        subtitle={`Chủ đề #${props.topicId} · Tập trung từng câu, tự tin thử sức.`}
      />
      <div className="quiz-attempt-content mx-auto w-full min-w-0 max-w-reading space-y-4">
        <Card className="space-y-4 border-brand-100 p-4 sm:p-5" aria-label="Thông tin phiên quiz">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="flex items-center gap-2 text-label text-brand-700">
              <Trophy aria-hidden="true" className="size-4 shrink-0" />
              {props.rankingEligible ? 'Lượt tính hạng' : 'Làm lại — không tính hạng'}
            </p>
            <Button variant="ghost" disabled={disabled} onClick={props.onExit}>
              <LogOut aria-hidden="true" className="size-4" />
              Thoát
            </Button>
          </div>
          <div className="grid gap-3 sm:grid-cols-2 sm:gap-6">
            <div className="space-y-2">
              <p className="flex items-center gap-2 text-body-sm text-ink-secondary">
                <ListChecks aria-hidden="true" className="size-4 text-brand-600" />
                Đã trả lời {answered}/{props.items.length} câu
              </p>
              <Progress value={answered} max={props.items.length} aria-label="Tiến độ trả lời" />
            </div>
            <div className="flex items-center gap-3 rounded-sm bg-canvas px-3 py-2">
              <Clock3 aria-hidden="true" className="size-5 shrink-0 text-brand-600" />
              <div>
                <p className="text-caption text-ink-secondary">Thời gian đã làm:</p>
                <p className="text-label tabular-nums text-ink">{quizDuration(props.elapsedMs)}</p>
              </div>
            </div>
          </div>
        </Card>
        {props.error ? (
          <p role="alert" className="rounded-md bg-danger-bg p-4 text-danger">
            {props.error}
          </p>
        ) : null}
        {current ? (
          <Card
            className={cn(
              'quiz-question-card relative isolate min-w-0 overflow-hidden border-brand-100 p-5 shadow-sm sm:p-6',
              current.options?.some(({ text }) => /\p{Script=Han}/u.test(text)) &&
                'quiz-hanzi-options',
              /^\p{Script=Han}{1,6}$/u.test(current.prompt.text ?? '') && 'quiz-short-prompt',
            )}
          >
            <div className="relative space-y-5">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="inline-flex items-center gap-2 rounded-pill bg-brand-50 px-3 py-2 text-label text-brand-700">
                  <Flag aria-hidden="true" className="size-4" />
                  Câu {current.no}
                </p>
                {props.newItems?.includes(current.no) ? <NewQuestionBadge /> : null}
                <p className="text-caption text-ink-secondary">Chọn một đáp án phù hợp</p>
              </div>
              <div key={current.no} className="quiz-question-enter">
                <ChoiceQuestion
                  key={current.no}
                  item={current}
                  feedbackMode="DEFERRED"
                  selectedAnswer={props.choices[current.no]}
                  pending={disabled}
                  onAnswer={props.onChoose}
                />
              </div>
              {props.saveState ? (
                <p
                  role="status"
                  className={cn(
                    'rounded-sm px-3 py-2 text-body-sm',
                    props.saveState === 'failed'
                      ? 'bg-warning-bg text-warning'
                      : 'bg-canvas text-ink-secondary',
                  )}
                >
                  {props.saveState === 'saving'
                    ? 'Đang lưu lựa chọn…'
                    : props.saveState === 'saved'
                      ? 'Đã lưu lựa chọn.'
                      : 'Chưa lưu được lựa chọn. Bạn có thể thử lại hoặc nộp bài.'}
                </p>
              ) : null}
              {props.saveState === 'failed' ? (
                <Button variant="outline" disabled={disabled} onClick={props.onRetryAnswer}>
                  Lưu lại lựa chọn
                </Button>
              ) : null}
            </div>
          </Card>
        ) : (
          <EmptyState
            title="Quiz chưa có câu hỏi được duyệt"
            description="Quay lại danh sách để chọn quiz khác."
          />
        )}
        <div className="quiz-attempt-actions grid grid-cols-2 gap-3 rounded-md border border-brand-100 bg-surface p-4">
          <Button
            variant="outline"
            disabled={disabled || index <= 0}
            onClick={() => {
              const previous = props.items[index - 1]
              if (previous) props.onNavigate(previous.no)
            }}
          >
            <ArrowLeft aria-hidden="true" className="size-4 shrink-0" />
            Câu trước
          </Button>
          <Button
            variant="outline"
            disabled={disabled || index < 0 || index >= props.items.length - 1}
            onClick={() => {
              const next = props.items[index + 1]
              if (next) props.onNavigate(next.no)
            }}
          >
            Câu tiếp theo
            <ArrowRight aria-hidden="true" className="size-4 shrink-0" />
          </Button>
          <Button className="col-span-2" disabled={disabled || !current} onClick={props.onSubmit}>
            <Send aria-hidden="true" className="size-4" />
            Nộp bài
          </Button>
        </div>
        {props.confirming ? (
          <section
            ref={panel}
            aria-label={props.confirming === 'submit' ? 'Xác nhận nộp bài' : 'Xác nhận thoát quiz'}
            className="space-y-3 rounded-md border border-brand-100 bg-brand-50 p-4 sm:p-5"
            onKeyDown={(event) => {
              if (event.key === 'Escape' && !props.pending) props.onCancel()
            }}
          >
            <h2 className="text-h3">
              {props.confirming === 'submit' ? 'Nộp quiz?' : 'Bỏ dở quiz?'}
            </h2>
            <p>
              {props.confirming === 'submit'
                ? `Còn ${props.items.length - answered} câu chưa trả lời. Câu bỏ trống được tính sai. Bạn muốn nộp bài?`
                : 'Lượt bỏ dở không được tính hạng. Bạn muốn thoát quiz?'}
            </p>
            <div className="flex flex-wrap gap-3">
              <Button variant="outline" disabled={props.pending} onClick={props.onCancel}>
                Tiếp tục làm bài
              </Button>
              <Button loading={props.pending} onClick={props.onConfirm}>
                {props.confirming === 'submit' ? 'Xác nhận nộp bài' : 'Xác nhận thoát'}
              </Button>
            </div>
          </section>
        ) : null}
        <Card className="space-y-3 border-brand-100 p-4">
          <Button
            className="w-full justify-between px-0 hover:bg-transparent"
            variant="ghost"
            aria-expanded={navigationOpen}
            aria-controls="quiz-question-navigation"
            onClick={() => setNavigationOpen((open) => !open)}
          >
            <span className="inline-flex items-center gap-2 text-label text-ink">
              <ListChecks aria-hidden="true" className="size-4 text-brand-600" />
              Bảng câu hỏi
            </span>
            <ChevronDown
              aria-hidden="true"
              className={cn(
                'size-4 transition-transform motion-reduce:transition-none',
                navigationOpen && 'rotate-180',
              )}
            />
          </Button>
          <div id="quiz-question-navigation" hidden={!navigationOpen} className="space-y-3">
            <nav aria-label="Điều hướng câu hỏi" className="flex flex-wrap gap-2">
              {props.items.map(({ no }) => (
                <Button
                  key={no}
                  size="sm"
                  variant={no === props.currentNo ? 'primary' : 'outline'}
                  disabled={disabled}
                  aria-label={`Câu ${no} — ${props.choices[no] != null ? 'đã trả lời' : 'chưa trả lời'}`}
                  aria-current={no === props.currentNo ? 'step' : undefined}
                  className={cn(
                    'min-w-12',
                    no !== props.currentNo &&
                      props.choices[no] != null &&
                      'border-success bg-success-bg text-success',
                  )}
                  onClick={() => props.onNavigate(no)}
                >
                  {no}
                  {props.choices[no] != null ? (
                    <Check aria-hidden="true" className="size-3" />
                  ) : null}
                </Button>
              ))}
            </nav>
            <p className="text-caption text-ink-secondary">
              Dấu ✓: đã chọn đáp án. Bạn có thể quay lại đổi lựa chọn trước khi nộp.
            </p>
          </div>
        </Card>
        <p className="px-1 text-center text-caption text-ink-secondary">
          Kết quả được hiển thị sau khi nộp bài. Quiz không ảnh hưởng tiến độ học.
        </p>
      </div>
    </main>
  )
}
