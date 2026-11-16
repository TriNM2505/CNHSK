import { Clock3, ListOrdered } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { ChoiceQuestion } from '@/features/practice/components/ChoiceQuestion'
import type { ServedItemView } from '@/features/practice/types'
import { Button, Card, EmptyState, PageHeader, Progress, StatusBadge } from '@/shared/ui'
import { cn } from '@/shared/lib/cn'
import { quizDuration } from '../types'

export type QuizAttemptViewProps = {
  topicId: number
  rankingEligible: boolean
  items: ServedItemView[]
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
  const panel = useRef<HTMLElement>(null)
  const trigger = useRef<HTMLElement | null>(null)
  const [navOpen, setNavOpen] = useState(true)
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
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-6 px-4 py-6 pb-20 sm:px-6">
      <PageHeader
        className="mb-0"
        title="Làm quiz theo chủ đề"
        subtitle={`Chủ đề #${props.topicId}`}
      />

      <section
        aria-label="Thông tin phiên quiz"
        className="space-y-4 rounded-md border border-line bg-surface p-4 sm:p-5"
      >
        <div className="flex flex-wrap items-center justify-between gap-3 text-body-sm text-ink">
          <StatusBadge tone={props.rankingEligible ? 'info' : 'neutral'}>
            {props.rankingEligible ? 'Lượt tính hạng' : 'Làm lại — không tính hạng'}
          </StatusBadge>
          <p className="inline-flex items-center gap-1.5 font-medium text-ink-secondary">
            <Clock3 aria-hidden="true" className="size-4 shrink-0" />
            Thời gian đã làm:{' '}
            <span className="font-semibold tabular-nums text-ink">
              {quizDuration(props.elapsedMs)}
            </span>
          </p>
        </div>

        <Progress
          aria-label="Tiến độ trả lời"
          label={`Đã trả lời ${answered}/${props.items.length} câu`}
          max={Math.max(props.items.length, 1)}
          tone="brand"
          value={answered}
        />

        {props.items.length > 0 ? (
          <div className="space-y-3 border-t border-line pt-3">
            <div className="flex items-center justify-between gap-2">
              <span className="inline-flex items-center gap-1.5 text-label text-ink">
                <ListOrdered aria-hidden="true" className="size-4 shrink-0 text-ink-secondary" />
                Danh sách câu hỏi
              </span>
              <Button
                aria-expanded={navOpen}
                onClick={() => setNavOpen((value) => !value)}
                size="sm"
                variant="ghost"
              >
                {navOpen ? 'Thu gọn' : 'Mở danh sách'}
              </Button>
            </div>
            {navOpen ? (
              <nav aria-label="Điều hướng câu hỏi" className="flex flex-wrap gap-2">
                {props.items.map(({ no }) => (
                  <Button
                    key={no}
                    size="sm"
                    variant="outline"
                    disabled={disabled}
                    aria-label={`Câu ${no} — ${props.choices[no] != null ? 'đã trả lời' : 'chưa trả lời'}`}
                    aria-current={no === props.currentNo ? 'step' : undefined}
                    className={cn(
                      props.choices[no] != null && 'border-brand-600 bg-brand-50 text-brand-700',
                      no === props.currentNo && 'ring-2 ring-brand-600 ring-offset-1',
                    )}
                    onClick={() => props.onNavigate(no)}
                  >
                    {no}
                  </Button>
                ))}
              </nav>
            ) : null}
          </div>
        ) : null}
      </section>

      {props.error ? (
        <p role="alert" className="rounded-md border border-danger bg-danger-bg p-4 text-danger">
          {props.error}
        </p>
      ) : null}

      {current ? (
        <Card className="min-w-0 space-y-5 break-words p-5 [overflow-wrap:anywhere] sm:p-6">
          <div className="flex items-center justify-between gap-2 border-b border-line pb-3">
            <p className="text-label text-brand-700">Câu {current.no}</p>
            {props.saveState ? (
              <p
                role="status"
                className={cn(
                  'text-body-sm',
                  props.saveState === 'failed' ? 'text-danger' : 'text-ink-secondary',
                )}
              >
                {props.saveState === 'saving'
                  ? 'Đang lưu lựa chọn…'
                  : props.saveState === 'saved'
                    ? 'Đã lưu lựa chọn.'
                    : 'Chưa lưu được lựa chọn. Bạn có thể thử lại hoặc nộp bài.'}
              </p>
            ) : null}
          </div>
          <ChoiceQuestion
            key={current.no}
            item={current}
            feedbackMode="DEFERRED"
            selectedAnswer={props.choices[current.no]}
            pending={disabled}
            onAnswer={props.onChoose}
          />
          {props.saveState === 'failed' ? (
            <Button variant="outline" disabled={disabled} onClick={props.onRetryAnswer}>
              Lưu lại lựa chọn
            </Button>
          ) : null}
        </Card>
      ) : (
        <EmptyState
          title="Quiz chưa có câu hỏi được duyệt"
          description="Quay lại danh sách để chọn quiz khác."
        />
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-3">
          <Button
            variant="outline"
            disabled={disabled || index <= 0}
            onClick={() => {
              const previous = props.items[index - 1]
              if (previous) props.onNavigate(previous.no)
            }}
          >
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
          </Button>
        </div>
        <div className="flex flex-wrap gap-3">
          <Button variant="outline" disabled={disabled} onClick={props.onExit}>
            Thoát
          </Button>
          <Button disabled={disabled || !current} onClick={props.onSubmit}>
            Nộp bài
          </Button>
        </div>
      </div>

      {props.confirming ? (
        <section
          ref={panel}
          aria-label={props.confirming === 'submit' ? 'Xác nhận nộp bài' : 'Xác nhận thoát quiz'}
          className="space-y-4 rounded-md border border-brand-100 bg-brand-50 p-5"
          onKeyDown={(event) => {
            if (event.key === 'Escape' && !props.pending) props.onCancel()
          }}
        >
          <h2 className="text-h3 text-ink">
            {props.confirming === 'submit' ? 'Nộp quiz?' : 'Bỏ dở quiz?'}
          </h2>
          <p className="text-body text-ink-secondary">
            {props.confirming === 'submit'
              ? `Còn ${props.items.length - answered} câu chưa trả lời. Câu bỏ trống được tính sai. Bạn muốn nộp bài?`
              : 'Lượt bỏ dở không được tính hạng. Bạn muốn thoát quiz?'}
          </p>
          <div className="flex flex-wrap gap-3">
            <Button variant="outline" disabled={props.pending} onClick={props.onCancel}>
              Tiếp tục làm bài
            </Button>
            <Button
              loading={props.pending}
              variant={props.confirming === 'exit' ? 'danger' : 'primary'}
              onClick={props.onConfirm}
            >
              {props.confirming === 'submit' ? 'Xác nhận nộp bài' : 'Xác nhận thoát'}
            </Button>
          </div>
        </section>
      ) : null}
    </main>
  )
}
