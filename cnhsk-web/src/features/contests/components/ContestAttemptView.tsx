import {
  ArrowLeft,
  ArrowRight,
  Bookmark,
  CheckCircle2,
  Circle,
  Clock3,
  FileText,
  LogOut,
  Send,
} from 'lucide-react'
import { useState } from 'react'

import { cn } from '@/shared/lib/cn'
import { Button, Card, HanziText, PageHeader, Progress, Skeleton } from '@/shared/ui'

import { formatCountdown } from '../countdown'
import type { ContestSession } from '../types'
import type { SaveState } from '../useContestAttempt'
import { ContestAttemptMountains } from './ContestAttemptArt'
import './ContestAttemptView.css'

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

function ChineseLandscapeBannerArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-y-0 right-0 hidden h-full w-3/5 text-brand-600 sm:block"
      fill="none"
      preserveAspectRatio="xMaxYMid slice"
      viewBox="0 0 680 132"
    >
      <defs>
        <linearGradient id="cnhsk-contatt-mtn-far" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id="cnhsk-contatt-mtn-mid" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
        </linearGradient>
        <linearGradient id="cnhsk-contatt-hill-front" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.34" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
      </defs>

      <circle cx="518" cy="34" fill="currentColor" opacity="0.2" r="24" />
      <rect fill="currentColor" height="5" opacity="0.16" rx="2.5" width="54" x="466" y="42" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="42" x="524" y="24" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="38" x="380" y="34" />

      <path
        d="M10 132 L110 66 L158 94 L236 22 L318 88 L382 36 L455 92 L520 58 L600 104 L680 132 Z"
        fill="url(#cnhsk-contatt-mtn-far)"
      />
      <path d="M236 22 L252 88 L195 112 Z" fill="currentColor" opacity="0.08" />
      <path d="M382 36 L396 86 L348 102 Z" fill="currentColor" opacity="0.08" />

      <path
        d="M95 132 L176 74 L248 106 L312 68 L392 108 L468 74 L556 106 L680 132 Z"
        fill="url(#cnhsk-contatt-mtn-mid)"
      />

      <path
        d="M280 132 C345 92, 415 74, 480 88 C538 100, 588 92, 680 112 L680 132 Z"
        fill="url(#cnhsk-contatt-hill-front)"
      />
      <path
        d="M390 132 C456 104, 520 98, 585 112 C622 104, 654 106, 680 118 L680 132 Z"
        fill="currentColor"
        opacity="0.22"
      />

      <path
        d="M284 132 C328 96, 376 70, 428 72 C474 74, 514 104, 562 102 C598 100, 632 88, 676 94 L676 106 C632 100, 598 112, 562 114 C512 116, 472 86, 428 84 C378 82, 334 106, 298 132 Z"
        fill="currentColor"
        opacity="0.32"
      />
      <path
        d="M282 132 C326 94, 376 68, 428 70 C474 72, 514 102, 562 100 C598 98, 632 86, 676 92"
        opacity="0.58"
        stroke="currentColor"
        strokeDasharray="5 3.5"
        strokeWidth="4.5"
      />
      <path
        d="M294 132 C336 102, 380 78, 428 80 C474 82, 514 112, 562 110 C598 108, 632 96, 676 102"
        opacity="0.45"
        stroke="currentColor"
        strokeWidth="2"
      />

      <g opacity="0.55" transform="translate(506, 70) scale(0.58)">
        <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
        <path d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z" fill="currentColor" />
        <path d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z" fill="currentColor" />
        <rect fill="currentColor" height="11" width="3" x="11" y="24" />
        <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
        <rect fill="currentColor" height="11" width="3" x="30" y="24" />
        <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
      </g>

      <g opacity="0.5" transform="translate(594, 78) scale(0.62)">
        <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
        <path d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z" fill="currentColor" />
        <path d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z" fill="currentColor" />
        <rect fill="currentColor" height="11" width="3" x="11" y="24" />
        <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
        <rect fill="currentColor" height="11" width="3" x="30" y="24" />
        <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
      </g>

      <g className="text-brand-700" opacity="0.95" transform="translate(426, 26)">
        <path d="M28 0 L31 8 L25 8 Z" fill="currentColor" />
        <path
          d="M6 19 C15 17, 22 12, 28 6 C34 12, 41 17, 50 19 C42 21, 35 18, 28 15 C21 18, 14 21, 6 19 Z"
          fill="currentColor"
        />
        <rect fill="currentColor" height="6" width="22" x="17" y="17" />
        <path
          d="M0 31 C12 28, 21 23, 28 18 C35 23, 44 28, 56 31 C45 33, 36 30, 28 27 C20 30, 11 33, 0 31 Z"
          fill="currentColor"
        />
        <rect fill="currentColor" height="15" width="3" x="12" y="29" />
        <rect fill="currentColor" height="15" width="3" x="20" y="29" />
        <rect fill="currentColor" height="15" width="3" x="33" y="29" />
        <rect fill="currentColor" height="15" width="3" x="41" y="29" />
        <rect fill="currentColor" height="2.5" width="34" x="11" y="39" />
        <path d="M7 44 L49 44 L52 49 L4 49 Z" fill="currentColor" />
      </g>

      <g className="text-brand-700" opacity="0.92">
        <path d="M634 134 L600 -8" stroke="currentColor" strokeWidth="6" />
        <line stroke="white" strokeWidth="2" x1="622" x2="630" y1="96" y2="94" />
        <line stroke="white" strokeWidth="2" x1="613" x2="621" y1="58" y2="56" />
        <line stroke="white" strokeWidth="2" x1="604" x2="612" y1="20" y2="18" />

        <path d="M654 134 L634 -8" stroke="currentColor" strokeWidth="8.5" />
        <line stroke="white" strokeWidth="2.2" x1="644" x2="654" y1="102" y2="100" />
        <line stroke="white" strokeWidth="2.2" x1="639" x2="649" y1="64" y2="62" />
        <line stroke="white" strokeWidth="2.2" x1="634" x2="644" y1="26" y2="24" />

        <path d="M672 134 L662 -8" stroke="currentColor" strokeWidth="6.5" />
        <line stroke="white" strokeWidth="2" x1="664" x2="673" y1="86" y2="85" />
        <line stroke="white" strokeWidth="2" x1="661" x2="670" y1="46" y2="45" />

        <path
          d="M636 10 Q585 6 542 26"
          stroke="currentColor"
          strokeLinecap="round"
          strokeWidth="2"
        />
        <path
          d="M612 36 Q576 42 548 62"
          stroke="currentColor"
          strokeLinecap="round"
          strokeWidth="1.8"
        />

        <path d="M558 18 Q540 32 528 50 Q548 38 558 18 Z" fill="currentColor" />
        <path d="M570 14 Q558 36 550 58 Q568 40 570 14 Z" fill="currentColor" />
        <path d="M584 12 Q578 34 574 54 Q588 36 584 12 Z" fill="currentColor" />
        <path d="M554 20 Q534 20 518 28 Q540 28 554 20 Z" fill="currentColor" opacity="0.85" />

        <path d="M562 52 Q546 66 536 82 Q554 70 562 52 Z" fill="currentColor" />
        <path d="M576 46 Q566 66 560 86 Q576 68 576 46 Z" fill="currentColor" />
        <path d="M590 42 Q586 60 584 78 Q596 62 590 42 Z" fill="currentColor" />
        <path d="M556 54 Q538 56 524 64 Q544 64 556 54 Z" fill="currentColor" opacity="0.8" />

        <path d="M596 8 Q586 24 582 40 Q596 28 596 8 Z" fill="currentColor" />
        <path d="M618 62 Q606 76 600 92 Q614 80 618 62 Z" fill="currentColor" />
        <path d="M628 58 Q622 76 620 92 Q632 76 628 58 Z" fill="currentColor" />
      </g>
    </svg>
  )
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
      <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
        <PageHeader
          className="relative z-nav mb-0"
          title={
            <span className="inline-flex min-w-0 max-w-full items-center gap-3">
              <span aria-hidden="true" className="h-7 w-1.5 shrink-0 rounded-pill bg-brand-600" />
              <span className="min-w-0 break-words">{props.name}</span>
            </span>
          }
          subtitle="Làm bài cuộc thi"
        />
        <ChineseLandscapeBannerArt />
      </div>
      {props.state === 'loading' ? (
        <div>
          <p role="status">Đang mở lượt thi…</p>
          <Skeleton variant="card" />
        </div>
      ) : null}
      {props.state === 'error' ? (
        <Card role="alert" className="space-y-3 border-danger bg-danger-bg">
          <p>{props.error ?? 'Không mở được lượt thi.'}</p>
          <Button onClick={props.onRetry} variant="outline">
            Thử lại
          </Button>
        </Card>
      ) : null}
      {props.state === 'empty' ? (
        <Card role="status" className="border-brand-100 bg-brand-50 p-6">
          Cuộc thi chưa có câu hỏi khả dụng.
        </Card>
      ) : null}
      {props.state === 'ready' && props.session && item ? (
        <>
          <Card className="space-y-3 border-brand-100 bg-brand-50/80 p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="min-w-0">
                <p className="text-caption text-ink-secondary">Thời gian còn lại</p>
                <p
                  role="timer"
                  aria-label="Thời gian làm bài còn lại"
                  className={
                    'flex items-center gap-2 text-h2 font-bold tabular-nums ' +
                    (props.remainingMs <= 60_000 ? 'text-danger' : 'text-brand-700')
                  }
                >
                  <Clock3 aria-hidden="true" className="size-5" />
                  {formatCountdown(props.remainingMs)}
                </p>
              </div>
              <p className="border-l border-brand-100 pl-4 text-caption text-ink-secondary sm:pl-6">
                Đã chọn {selected}/{items.length} câu
              </p>
              <Button
                size="sm"
                variant="outline"
                className="shadow-sm"
                disabled={props.submitting}
                onClick={() => setLeaving(true)}
              >
                <span className="inline-flex items-center gap-2">
                  <LogOut aria-hidden="true" className="size-4 text-brand-600" />
                  Thoát
                </span>
              </Button>
            </div>
            <Progress
              className="w-full"
              value={selected}
              max={items.length}
              label={`${selected}/${items.length}`}
            />
          </Card>
          <details
            open
            className="relative isolate overflow-hidden rounded-md border border-brand-100 bg-brand-50/80 p-4"
          >
            <ContestAttemptMountains position="bottom" />
            <summary className="relative z-nav min-h-touch cursor-pointer text-label font-bold marker:text-brand-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">
              Điều hướng câu hỏi
            </summary>
            <nav aria-label="Các câu hỏi" className="relative z-nav mt-1 flex flex-wrap gap-2">
              {items.map(({ no }) => (
                <Button
                  key={no}
                  size="sm"
                  variant={item.no === no ? 'primary' : 'outline'}
                  className="min-w-16 shadow-sm"
                  aria-current={item.no === no ? 'step' : undefined}
                  onClick={() => select(no)}
                >
                  Câu {no}
                  {props.choices[no] !== undefined ? ' ✓' : ''}
                </Button>
              ))}
            </nav>
          </details>
          <Card className="relative isolate min-w-0 space-y-5 overflow-hidden break-words border-brand-100 px-4 py-5 pb-8 sm:p-6 sm:pb-10">
            <ContestAttemptMountains position="top" />
            <ContestAttemptMountains position="bottom" />
            <h2 className="relative z-nav flex items-center gap-2.5 text-h3 font-bold">
              <span className="flex size-8 items-center justify-center rounded-pill bg-brand-50 text-brand-700">
                <FileText aria-hidden="true" className="size-6" />
              </span>
              Câu {item.no}
            </h2>
            <section
              aria-labelledby={`contest-question-${item.no}`}
              className="relative z-nav min-w-0 space-y-5"
            >
              <div className="text-center">
                {item.prompt.text ? (
                  <HanziText
                    className="contest-question-prompt block break-words text-h3 leading-relaxed"
                    id={`contest-question-${item.no}`}
                    size="sm"
                  >
                    {item.prompt.text}
                  </HanziText>
                ) : (
                  <h3 className="text-h3 text-ink" id={`contest-question-${item.no}`}>
                    Chọn đáp án phù hợp
                  </h3>
                )}
                {item.prompt.sentence ? (
                  <p className="mt-3 break-words font-hanzi text-h2 text-ink" lang="zh-CN">
                    {item.prompt.sentence}
                  </p>
                ) : null}
                {item.prompt.hint ? (
                  <p className="mt-2 text-body-sm text-ink-secondary">Gợi ý: {item.prompt.hint}</p>
                ) : null}
              </div>
              <div
                aria-label="Các phương án"
                className="contest-answer-grid grid gap-3"
                role="group"
              >
                {(item.options ?? []).map((option) => {
                  const selected = props.choices[item.no] === option.key
                  return (
                    <Button
                      aria-pressed={selected}
                      className={cn(
                        'grid min-w-0 grid-cols-1 justify-stretch rounded-md p-3 text-left font-normal',
                        selected && 'border-brand-600 bg-brand-50',
                      )}
                      disabled={props.locked}
                      key={option.key}
                      onClick={() => void props.onChoose(item.no, option.key)}
                      variant="outline"
                    >
                      <span className="flex w-full items-center gap-2">
                        <span className="flex size-7 shrink-0 items-center justify-center rounded-pill border border-line text-label text-ink-secondary">
                          {option.key}
                        </span>
                        <HanziText className="contest-answer-text min-w-0 flex-1 break-words text-body-sm font-semibold leading-relaxed">
                          {option.text}
                        </HanziText>
                        {selected ? (
                          <CheckCircle2
                            aria-hidden="true"
                            className="size-5 shrink-0 text-brand-700"
                          />
                        ) : (
                          <Circle aria-hidden="true" className="size-5 shrink-0 text-ink-muted" />
                        )}
                      </span>
                    </Button>
                  )
                })}
              </div>
            </section>
            {props.saveStates[item.no] ? (
              <p
                role="status"
                className="relative z-nav flex items-center gap-2 text-caption text-ink-secondary"
              >
                <Bookmark aria-hidden="true" className="size-4 shrink-0 text-brand-600" />
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
                className="relative z-nav"
                onClick={() => void props.onChoose(item.no, props.choices[item.no]!)}
              >
                Lưu lại đáp án
              </Button>
            ) : null}
          </Card>
          <div className="flex flex-wrap gap-3">
            <Button
              variant="outline"
              className="flex-1 shadow-sm sm:flex-none"
              size="sm"
              disabled={position <= 0}
              onClick={() => select(items[position - 1]!.no)}
            >
              <span className="inline-flex items-center gap-2 whitespace-nowrap">
                <ArrowLeft aria-hidden="true" className="size-4" />
                Câu trước
              </span>
            </Button>
            <Button
              variant="outline"
              className="flex-1 border-brand-100 text-brand-700 shadow-sm sm:flex-none"
              size="sm"
              disabled={position >= items.length - 1}
              onClick={() => select(items[position + 1]!.no)}
            >
              <span className="inline-flex items-center gap-2 whitespace-nowrap">
                Câu tiếp theo
                <ArrowRight aria-hidden="true" className="size-4" />
              </span>
            </Button>
            <Button
              className="flex-1 bg-gradient-to-b from-brand-600 to-brand-800 shadow-sm sm:flex-none"
              size="sm"
              disabled={props.locked}
              onClick={() => setConfirming(true)}
            >
              <span className="inline-flex items-center gap-2 whitespace-nowrap">
                <Send aria-hidden="true" className="size-5" />
                Nộp bài
              </span>
            </Button>
          </div>
          {confirming && !props.locked ? (
            <Card
              role="region"
              aria-label="Xác nhận nộp bài"
              className="space-y-3 border-brand-100 bg-brand-50 p-5"
            >
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
            <Card role="alert" className="space-y-3 border-danger bg-danger-bg">
              <p className="font-semibold">Bài thi chưa nộp</p>
              <p>{props.submitError}</p>
              <Button onClick={props.onRetrySubmit} disabled={props.submitting}>
                Thử nộp lại
              </Button>
            </Card>
          ) : null}
          {props.submitted ? (
            <Card role="status" className="border-brand-100 bg-brand-50 p-5">
              Đã nộp bài. Kết quả sẽ hiển thị sau khi ban tổ chức công bố.
            </Card>
          ) : null}
          {leaving ? (
            <Card
              role="region"
              aria-label="Xác nhận rời cuộc thi"
              className="space-y-3 border-brand-100 bg-brand-50 p-5"
            >
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
