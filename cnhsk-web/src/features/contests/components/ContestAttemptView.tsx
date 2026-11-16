import { Clock3 } from 'lucide-react'
import { useState } from 'react'

import { ChoiceQuestion } from '@/features/practice/components/ChoiceQuestion'
import { Button, Card, PageHeader, Progress, Skeleton } from '@/shared/ui'

import { formatCountdown } from '../countdown'
import type { ContestSession } from '../types'
import type { SaveState } from '../useContestAttempt'

type Props = {
  name: string
  state: 'loading' | 'empty' | 'error' | 'ready'
  session?: ContestSession
  remainingMs: number
  choices: Readonly<Record<number, string>>
  saveStates: Readonly<Record<number, SaveState>>
  locked: boolean
  submitting: boolean
  submitted: boolean
  error?: string
  submitError?: string
  onRetry: () => void
  onSelectQuestion: () => void
  onChoose: (no: number, answer: string) => Promise<unknown> | unknown
  onSubmit: () => void
  onRetrySubmit: () => void
}

export function ContestAttemptView(props: Props) {
  const [currentNo, setCurrentNo] = useState(props.session?.items[0]?.no ?? 1)
  const [confirming, setConfirming] = useState(false)
  const [leaving, setLeaving] = useState(false)
  const item = props.session?.items.find(({ no }) => no === currentNo) ?? props.session?.items[0]
  const items = props.session?.items ?? []
  const selected = items.filter(({ no }) => props.choices[no] !== undefined).length
  const position = items.findIndex(({ no }) => no === item?.no)
  function select(no: number) {
    props.onSelectQuestion()
    setCurrentNo(no)
  }
  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-5 px-4 py-6 pb-16 sm:px-6">
      <PageHeader title={props.name} subtitle="Làm bài cuộc thi" />
      {props.state === 'loading' ? (
        <div>
          <p role="status">Đang mở lượt thi…</p>
          <Skeleton variant="card" />
        </div>
      ) : null}
      {props.state === 'error' ? (
        <Card role="alert" className="space-y-3">
          <p>{props.error ?? 'Không mở được lượt thi.'}</p>
          <Button onClick={props.onRetry} variant="outline">
            Thử lại
          </Button>
        </Card>
      ) : null}
      {props.state === 'empty' ? (
        <Card role="status">Cuộc thi chưa có câu hỏi khả dụng.</Card>
      ) : null}
      {props.state === 'ready' && props.session && item ? (
        <>
          <Card className="flex flex-wrap items-center justify-between gap-3 border-brand-100 bg-brand-50">
            <div>
              <p className="text-body-sm text-ink-secondary">Thời gian còn lại</p>
              <p
                role="timer"
                aria-label="Thời gian làm bài còn lại"
                className={
                  'flex items-center gap-2 text-h1-sm tabular-nums ' +
                  (props.remainingMs <= 60_000 ? 'text-danger' : 'text-brand-700')
                }
              >
                <Clock3 aria-hidden="true" className="size-5" />
                {formatCountdown(props.remainingMs)}
              </p>
            </div>
            <p className="text-body-sm text-ink-secondary">
              Đã chọn {selected}/{items.length} câu
            </p>
            <Button variant="ghost" disabled={props.submitting} onClick={() => setLeaving(true)}>
              Thoát
            </Button>
            <Progress
              className="w-full"
              value={selected}
              max={items.length}
              label={`${selected}/${items.length}`}
            />
          </Card>
          <details open className="rounded-md border border-line bg-surface p-4">
            <summary className="min-h-touch cursor-pointer text-label focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">
              Điều hướng câu hỏi
            </summary>
            <nav aria-label="Các câu hỏi" className="flex flex-wrap gap-2">
              {items.map(({ no }) => (
                <Button
                  key={no}
                  size="sm"
                  variant={item.no === no ? 'secondary' : 'outline'}
                  aria-current={item.no === no ? 'step' : undefined}
                  onClick={() => select(no)}
                >
                  Câu {no}
                  {props.choices[no] !== undefined ? ' ✓' : ''}
                </Button>
              ))}
            </nav>
          </details>
          <Card className="min-w-0 space-y-5 break-words p-6 sm:p-8">
            <h2 className="text-h3">Câu {item.no}</h2>
            <div className="[&_.text-hanzi-lg]:text-h3 [&_.text-hanzi-lg]:leading-relaxed">
              <ChoiceQuestion
                item={item}
                feedbackMode="DEFERRED"
                selectedAnswer={props.choices[item.no]}
                pending={props.locked}
                onAnswer={async (answer) => {
                  const accepted = await props.onChoose(item.no, answer)
                  if (accepted === false) throw new Error('The answer was not accepted.')
                }}
              />
            </div>
            {props.saveStates[item.no] ? (
              <p role="status" className="text-body-sm text-ink-secondary">
                {props.saveStates[item.no] === 'saving'
                  ? 'Đang lưu…'
                  : props.saveStates[item.no] === 'saved'
                    ? 'Đã lưu đáp án'
                    : 'Chưa lưu được đáp án'}
              </p>
            ) : null}
            {props.saveStates[item.no] === 'failed' && !props.locked ? (
              <Button
                variant="outline"
                onClick={() => void props.onChoose(item.no, props.choices[item.no]!)}
              >
                Lưu lại đáp án
              </Button>
            ) : null}
          </Card>
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              disabled={position <= 0}
              onClick={() => select(items[position - 1]!.no)}
            >
              Câu trước
            </Button>
            <Button
              variant="outline"
              disabled={position >= items.length - 1}
              onClick={() => select(items[position + 1]!.no)}
            >
              Câu tiếp theo
            </Button>
            <Button disabled={props.locked} onClick={() => setConfirming(true)}>
              Nộp bài
            </Button>
          </div>
          {confirming && !props.locked ? (
            <Card role="region" aria-label="Xác nhận nộp bài" className="space-y-3">
              <p>Còn {items.length - selected} câu chưa trả lời. Bạn muốn nộp bài?</p>
              <div className="flex flex-wrap gap-2">
                <Button
                  onClick={() => {
                    setConfirming(false)
                    props.onSubmit()
                  }}
                >
                  Xác nhận nộp bài
                </Button>
                <Button variant="outline" onClick={() => setConfirming(false)}>
                  Tiếp tục làm bài
                </Button>
              </div>
            </Card>
          ) : null}
          {props.submitting ? <p role="status">Đang lưu các đáp án và nộp bài…</p> : null}
          {props.submitError ? (
            <Card role="alert" className="space-y-3">
              <p className="font-semibold">Bài thi chưa nộp</p>
              <p>{props.submitError}</p>
              <Button onClick={props.onRetrySubmit} disabled={props.submitting}>
                Thử nộp lại
              </Button>
            </Card>
          ) : null}
          {props.submitted ? (
            <Card role="status">Đã nộp bài. Kết quả sẽ hiển thị sau khi ban tổ chức công bố.</Card>
          ) : null}
          {leaving ? (
            <Card role="region" aria-label="Xác nhận rời cuộc thi" className="space-y-3">
              <p>
                Đồng hồ vẫn tiếp tục khi bạn rời trang. Đáp án đã lưu được giữ lại; mỗi người chỉ có
                một lượt thi.
              </p>
              <div className="flex flex-wrap gap-2">
                <Button variant="outline" onClick={() => setLeaving(false)}>
                  Ở lại làm bài
                </Button>
                <Button to={'/contests/' + props.session.contestId} variant="ghost">
                  Về thông tin cuộc thi
                </Button>
              </div>
            </Card>
          ) : null}
        </>
      ) : null}
      {props.state === 'error' || props.state === 'empty' ? (
        <Button to="/contests" variant="outline">
          Danh sách cuộc thi
        </Button>
      ) : null}
    </main>
  )
}
